#include "wifi_board.h"
#include "audio/codecs/box_audio_codec.h"
#include "application.h"
#include "button.h"
#include "config.h"
#include "esp32_camera.h"
#include "mcp_server.h"
#include "websocket_control_server.h"

#include <driver/gpio.h>
#include <driver/i2c_master.h>
#include <driver/uart.h>
#include <esp_log.h>

#include <cctype>
#include <cstdio>
#include <mutex>
#include <string>
#include <vector>

#define TAG "DlcMotorP1AiBoard"

class DlcMotorControlP1AudioCodec : public BoxAudioCodec {
public:
    explicit DlcMotorControlP1AudioCodec(i2c_master_bus_handle_t i2c_bus)
        : BoxAudioCodec(i2c_bus,
                        AUDIO_INPUT_SAMPLE_RATE,
                        AUDIO_OUTPUT_SAMPLE_RATE,
                        AUDIO_I2S_GPIO_MCLK,
                        AUDIO_I2S_GPIO_BCLK,
                        AUDIO_I2S_GPIO_WS,
                        AUDIO_I2S_GPIO_DOUT,
                        AUDIO_I2S_GPIO_DIN,
                        GPIO_NUM_NC,
                        AUDIO_CODEC_ES8311_ADDR,
                        AUDIO_CODEC_ES7210_ADDR,
                        AUDIO_INPUT_REFERENCE) {
        gpio_config_t io_conf = {};
        io_conf.mode = GPIO_MODE_OUTPUT;
        io_conf.pin_bit_mask = 1ULL << AUDIO_CODEC_PA_PIN;
        gpio_config(&io_conf);
        gpio_set_level(AUDIO_CODEC_PA_PIN, 0);
    }

    void EnableOutput(bool enable) override {
        BoxAudioCodec::EnableOutput(enable);
        gpio_set_level(AUDIO_CODEC_PA_PIN, enable ? 1 : 0);
    }
};

class DlcMotorControlP1AiBoard : public WifiBoard {
private:
    enum class JobState {
        Idle,
        Staged,
        Running,
        Paused,
        Completed,
        Failed,
        Cancelled
    };

    i2c_master_bus_handle_t i2c_bus_ = nullptr;
    Button boot_button_;
    Esp32Camera* camera_ = nullptr;
    WebSocketControlServer* ws_control_server_ = nullptr;
    std::mutex uart_mutex_;
    std::mutex job_mutex_;
    TaskHandle_t job_task_handle_ = nullptr;
    std::vector<std::string> job_lines_;
    size_t job_next_line_ = 0;
    uint32_t job_id_ = 0;
    uint32_t protocol_msg_id_ = 0;
    JobState job_state_ = JobState::Idle;
    bool job_cancel_requested_ = false;
    bool job_pause_requested_ = false;
    std::string job_error_;
    std::string job_last_response_;

    static const char* JobStateName(JobState state) {
        switch (state) {
            case JobState::Idle:
                return "idle";
            case JobState::Staged:
                return "staged";
            case JobState::Running:
                return "running";
            case JobState::Paused:
                return "paused";
            case JobState::Completed:
                return "completed";
            case JobState::Failed:
                return "failed";
            case JobState::Cancelled:
                return "cancelled";
        }
        return "unknown";
    }

    static std::string Trim(const std::string& text) {
        size_t start = 0;
        while (start < text.size() && isspace(static_cast<unsigned char>(text[start]))) {
            ++start;
        }

        size_t end = text.size();
        while (end > start && isspace(static_cast<unsigned char>(text[end - 1]))) {
            --end;
        }

        return text.substr(start, end - start);
    }

    static bool IsIgnorableGcodeLine(const std::string& line) {
        if (line.empty()) {
            return true;
        }
        if (line[0] == ';') {
            return true;
        }
        if (line.front() == '(' && line.back() == ')') {
            return true;
        }
        return false;
    }

    static bool JsonNumberToString(cJSON* item, char* buffer, size_t buffer_size) {
        if (item == nullptr || buffer == nullptr || buffer_size == 0 || !cJSON_IsNumber(item)) {
            return false;
        }
        snprintf(buffer, buffer_size, "%.3f", item->valuedouble);
        return true;
    }

    static bool BuildPathGcode(cJSON* root, int feed_rate, std::string& gcode, std::string& error) {
        if (root == nullptr || !cJSON_IsArray(root)) {
            error = "invalid path json";
            return false;
        }

        gcode = "G90\n";
        cJSON* segment = nullptr;
        cJSON_ArrayForEach(segment, root) {
            if (!cJSON_IsObject(segment)) {
                error = "invalid path segment";
                return false;
            }

            cJSON* cmd_item = cJSON_GetObjectItemCaseSensitive(segment, "cmd");
            cJSON* x_item = cJSON_GetObjectItemCaseSensitive(segment, "x");
            cJSON* y_item = cJSON_GetObjectItemCaseSensitive(segment, "y");
            if (!cJSON_IsString(cmd_item) || cmd_item->valuestring == nullptr || !cJSON_IsNumber(x_item) || !cJSON_IsNumber(y_item)) {
                error = "path segment missing cmd/x/y";
                return false;
            }

            char xbuf[32];
            char ybuf[32];
            JsonNumberToString(x_item, xbuf, sizeof(xbuf));
            JsonNumberToString(y_item, ybuf, sizeof(ybuf));

            std::string cmd = cmd_item->valuestring;
            if (cmd == "M") {
                gcode += "G0 X";
                gcode += xbuf;
                gcode += " Y";
                gcode += ybuf;
                gcode += "\n";
            } else if (cmd == "L") {
                gcode += "G1 X";
                gcode += xbuf;
                gcode += " Y";
                gcode += ybuf;
                gcode += " F";
                gcode += std::to_string(feed_rate);
                gcode += "\n";
            } else {
                error = "unsupported segment cmd";
                return false;
            }
        }

        return true;
    }

    static std::vector<std::string> ParseGcodeLines(const std::string& gcode) {
        std::vector<std::string> lines;
        std::string current;
        current.reserve(gcode.size());

        for (char ch : gcode) {
            if (ch == '\r') {
                continue;
            }
            if (ch == '\n') {
                auto line = Trim(current);
                if (!IsIgnorableGcodeLine(line)) {
                    lines.push_back(line);
                }
                current.clear();
                continue;
            }
            current.push_back(ch);
        }

        auto tail = Trim(current);
        if (!IsIgnorableGcodeLine(tail)) {
            lines.push_back(tail);
        }
        return lines;
    }

    cJSON* BuildJobStatusJsonSnapshot() {
        uint32_t job_id;
        size_t total_lines;
        size_t sent_lines;
        bool cancel_requested;
        bool pause_requested;
        JobState state;
        std::string last_response;
        std::string error;

        {
            std::lock_guard<std::mutex> lock(job_mutex_);
            job_id = job_id_;
            total_lines = job_lines_.size();
            sent_lines = job_next_line_;
            cancel_requested = job_cancel_requested_;
            pause_requested = job_pause_requested_;
            state = job_state_;
            last_response = job_last_response_;
            error = job_error_;
        }

        auto root = cJSON_CreateObject();
        cJSON_AddNumberToObject(root, "job_id", static_cast<double>(job_id));
        cJSON_AddStringToObject(root, "state", JobStateName(state));
        cJSON_AddNumberToObject(root, "total_lines", static_cast<int>(total_lines));
        cJSON_AddNumberToObject(root, "sent_lines", static_cast<int>(sent_lines));
        cJSON_AddNumberToObject(root, "pending_lines", static_cast<int>(total_lines - sent_lines));
        cJSON_AddBoolToObject(root, "cancel_requested", cancel_requested);
        cJSON_AddBoolToObject(root, "pause_requested", pause_requested);
        cJSON_AddStringToObject(root, "last_response", last_response.c_str());
        cJSON_AddStringToObject(root, "error", error.c_str());
        return root;
    }

    void InitializeI2c() {
        i2c_master_bus_config_t i2c_bus_cfg = {
            .i2c_port = (i2c_port_t)1,
            .sda_io_num = AUDIO_CODEC_I2C_SDA_PIN,
            .scl_io_num = AUDIO_CODEC_I2C_SCL_PIN,
            .clk_source = I2C_CLK_SRC_DEFAULT,
            .glitch_ignore_cnt = 7,
            .intr_priority = 0,
            .trans_queue_depth = 0,
            .flags = {
                .enable_internal_pullup = 1,
            },
        };
        ESP_ERROR_CHECK(i2c_new_master_bus(&i2c_bus_cfg, &i2c_bus_));
    }

    void InitializeButtons() {
        boot_button_.OnClick([this]() {
            auto& app = Application::GetInstance();
            if (app.GetDeviceState() == kDeviceStateStarting) {
                EnterWifiConfigMode();
                return;
            }
            app.ToggleChatState();
        });

        boot_button_.OnLongPress([this]() {
            EnterWifiConfigMode();
        });
    }

    void InitializeCamera() {
        camera_config_t config = {};
        config.ledc_channel = LEDC_CHANNEL_2;
        config.ledc_timer = LEDC_TIMER_2;
        config.pin_d0 = CAMERA_PIN_D0;
        config.pin_d1 = CAMERA_PIN_D1;
        config.pin_d2 = CAMERA_PIN_D2;
        config.pin_d3 = CAMERA_PIN_D3;
        config.pin_d4 = CAMERA_PIN_D4;
        config.pin_d5 = CAMERA_PIN_D5;
        config.pin_d6 = CAMERA_PIN_D6;
        config.pin_d7 = CAMERA_PIN_D7;
        config.pin_xclk = CAMERA_PIN_XCLK;
        config.pin_pclk = CAMERA_PIN_PCLK;
        config.pin_vsync = CAMERA_PIN_VSYNC;
        config.pin_href = CAMERA_PIN_HREF;
        config.pin_sccb_sda = -1;
        config.pin_sccb_scl = CAMERA_PIN_SIOC;
        config.sccb_i2c_port = 1;
        config.pin_pwdn = CAMERA_PIN_PWDN;
        config.pin_reset = CAMERA_PIN_RESET;
        config.xclk_freq_hz = XCLK_FREQ_HZ;
        config.pixel_format = PIXFORMAT_RGB565;
        config.frame_size = FRAMESIZE_VGA;
        config.jpeg_quality = 12;
        config.fb_count = 1;
        config.fb_location = CAMERA_FB_IN_PSRAM;
        config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;

        camera_ = new Esp32Camera(config);
    }

    void InitializeU1Uart() {
        uart_config_t uart_config = {
            .baud_rate = U1_UART_BAUD_RATE,
            .data_bits = UART_DATA_8_BITS,
            .parity = UART_PARITY_DISABLE,
            .stop_bits = UART_STOP_BITS_1,
            .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
            .source_clk = UART_SCLK_DEFAULT,
        };

        ESP_ERROR_CHECK(uart_driver_install(U1_UART_PORT_NUM, U1_UART_BUF_SIZE * 2, U1_UART_BUF_SIZE * 2, 0, NULL, 0));
        ESP_ERROR_CHECK(uart_param_config(U1_UART_PORT_NUM, &uart_config));
        ESP_ERROR_CHECK(uart_set_pin(U1_UART_PORT_NUM, U1_UART_TXD, U1_UART_RXD, U1_UART_RTS, U1_UART_CTS));
    }

    static std::string NormalizeResponse(std::string text) {
        for (char& ch : text) {
            if (ch == '\r' || ch == '\n') {
                ch = ' ';
            }
        }
        while (!text.empty() && text.back() == ' ') {
            text.pop_back();
        }
        return text;
    }

    std::string ReadU1Response(int timeout_ms) {
        std::string response;
        uint8_t buffer[128];
        int idle_rounds = 0;

        while (idle_rounds < 2) {
            int len = uart_read_bytes(U1_UART_PORT_NUM, buffer, sizeof(buffer), pdMS_TO_TICKS(timeout_ms));
            if (len > 0) {
                response.append(reinterpret_cast<char*>(buffer), len);
                idle_rounds = 0;
            } else {
                ++idle_rounds;
            }
        }

        return NormalizeResponse(response);
    }

    std::string SendU1Line(const std::string& line, int timeout_ms = 120) {
        std::lock_guard<std::mutex> lock(uart_mutex_);
        uart_flush_input(U1_UART_PORT_NUM);

        std::string command = line + "\n";
        uart_write_bytes(U1_UART_PORT_NUM, command.data(), command.size());
        ESP_LOGI(TAG, "U8 -> U1: %s", line.c_str());

        auto response = ReadU1Response(timeout_ms);
        if (!response.empty()) {
            ESP_LOGI(TAG, "U1 -> U8: %s", response.c_str());
        }
        return response;
    }

    std::string SendU1ProtocolCommand(const std::string& task_id, const std::string& cmd, int timeout_ms = 120) {
        uint32_t msg_id = 0;
        {
            std::lock_guard<std::mutex> lock(job_mutex_);
            msg_id = ++protocol_msg_id_;
        }

        std::string line = "{\"msg_id\":\"" + std::to_string(msg_id) + "\",\"type\":\"cmd\",\"task_id\":\"" + task_id +
                           "\",\"cmd\":\"" + cmd + "\"}";
        return SendU1Line("@" + line, timeout_ms);
    }

    std::string SendU1ProtocolJson(const std::string& task_id, const std::string& cmd, const std::string& extra_fields, int timeout_ms = 120) {
        uint32_t msg_id = 0;
        {
            std::lock_guard<std::mutex> lock(job_mutex_);
            msg_id = ++protocol_msg_id_;
        }

        std::string line = "{\"msg_id\":\"" + std::to_string(msg_id) + "\",\"type\":\"cmd\",\"task_id\":\"" + task_id +
                           "\",\"cmd\":\"" + cmd + "\"";
        if (!extra_fields.empty()) {
            line += "," + extra_fields;
        }
        line += "}";
        return SendU1Line("@" + line, timeout_ms);
    }

    std::string SendU1MoveAbsCommand(int x, int y, int z, int feed, int timeout_ms = 150) {
        uint32_t msg_id = 0;
        {
            std::lock_guard<std::mutex> lock(job_mutex_);
            msg_id = ++protocol_msg_id_;
        }

        std::string line = "{\"msg_id\":\"" + std::to_string(msg_id) +
                           "\",\"type\":\"cmd\",\"task_id\":\"t_move_local\",\"cmd\":\"MOVE\",\"x\":" +
                           std::to_string(x) + ",\"y\":" + std::to_string(y) + ",\"z\":" + std::to_string(z) +
                           ",\"feed\":" + std::to_string(feed) + "}";
        return SendU1Line("@" + line, timeout_ms);
    }

    std::string SendU1Realtime(char command, int timeout_ms = 80) {
        std::lock_guard<std::mutex> lock(uart_mutex_);
        uart_flush_input(U1_UART_PORT_NUM);
        uart_write_bytes(U1_UART_PORT_NUM, &command, 1);
        ESP_LOGI(TAG, "U8 -> U1 realtime: 0x%02x", static_cast<unsigned char>(command));

        auto response = ReadU1Response(timeout_ms);
        if (!response.empty()) {
            ESP_LOGI(TAG, "U1 -> U8: %s", response.c_str());
        }
        return response;
    }

    void InitializeWebSocketControlServer() {
        ws_control_server_ = new WebSocketControlServer();
        if (!ws_control_server_->Start(8080)) {
            delete ws_control_server_;
            ws_control_server_ = nullptr;
        }
    }

    static void JobTaskThunk(void* arg) {
        static_cast<DlcMotorControlP1AiBoard*>(arg)->RunJobTask();
    }

    void RunJobTask() {
        for (;;) {
            std::string line;
            {
                std::lock_guard<std::mutex> lock(job_mutex_);
                if (job_cancel_requested_) {
                    job_state_ = JobState::Cancelled;
                    break;
                }
                if (job_pause_requested_) {
                    job_state_ = JobState::Paused;
                } else if (job_state_ == JobState::Paused) {
                    job_state_ = JobState::Running;
                }

                if (job_state_ == JobState::Paused) {
                    line.clear();
                } else if (job_next_line_ >= job_lines_.size()) {
                    job_state_ = JobState::Completed;
                    break;
                } else {
                    line = job_lines_[job_next_line_];
                }
            }

            if (line.empty()) {
                vTaskDelay(pdMS_TO_TICKS(20));
                continue;
            }

            auto response = SendU1Line(line, 300);

            std::lock_guard<std::mutex> lock(job_mutex_);
            job_last_response_ = response;

            if (job_cancel_requested_) {
                job_state_ = JobState::Cancelled;
                break;
            }
            if (response.empty()) {
                job_state_ = JobState::Failed;
                job_error_ = "timeout waiting for grbl response";
                break;
            }
            if (response.find("error") != std::string::npos || response.find("ALARM") != std::string::npos) {
                job_state_ = JobState::Failed;
                job_error_ = response;
                break;
            }

            ++job_next_line_;
        }

        {
            std::lock_guard<std::mutex> lock(job_mutex_);
            job_task_handle_ = nullptr;
        }
        vTaskDelete(nullptr);
    }

    ReturnValue LoadJob(const std::string& gcode, bool append) {
        auto lines = ParseGcodeLines(gcode);

        uint32_t job_id;
        size_t total_lines;
        JobState state;
        {
            std::lock_guard<std::mutex> lock(job_mutex_);
            if (job_state_ == JobState::Running || job_state_ == JobState::Paused) {
                return std::string("job busy");
            }
            if (!append) {
                job_lines_.clear();
                job_next_line_ = 0;
                job_error_.clear();
                job_last_response_.clear();
                job_cancel_requested_ = false;
                job_pause_requested_ = false;
                ++job_id_;
            }

            job_lines_.insert(job_lines_.end(), lines.begin(), lines.end());
            job_state_ = job_lines_.empty() ? JobState::Idle : JobState::Staged;
            job_id = job_id_;
            total_lines = job_lines_.size();
            state = job_state_;
        }

        auto root = cJSON_CreateObject();
        cJSON_AddNumberToObject(root, "job_id", static_cast<double>(job_id));
        cJSON_AddNumberToObject(root, "lines_added", static_cast<int>(lines.size()));
        cJSON_AddNumberToObject(root, "total_lines", static_cast<int>(total_lines));
        cJSON_AddStringToObject(root, "state", JobStateName(state));
        return root;
    }

    ReturnValue RunPath(const std::string& path_json, int feed_rate) {
        cJSON* root = cJSON_Parse(path_json.c_str());
        if (root == nullptr || !cJSON_IsArray(root)) {
            if (root != nullptr) {
                cJSON_Delete(root);
            }
            return std::string("invalid path json");
        }
        std::string fallback_gcode;
        std::string fallback_error;
        if (!BuildPathGcode(root, feed_rate, fallback_gcode, fallback_error)) {
            cJSON_Delete(root);
            return fallback_error;
        }
        int total_segments = cJSON_GetArraySize(root);
        if (total_segments <= 0) {
            cJSON_Delete(root);
            return std::string("path is empty");
        }

        const std::string task_id = "t_path_local";
        auto response = SendU1ProtocolJson(task_id, "PATH_BEGIN",
                                           "\"total_segments\":" + std::to_string(total_segments) +
                                               ",\"feed\":" + std::to_string(feed_rate),
                                           150);
        if (response.empty() || response.find("\"type\":\"error\"") != std::string::npos) {
            cJSON_Delete(root);
            auto staged = LoadJob(fallback_gcode, false);
            if (std::holds_alternative<std::string>(staged)) {
                return std::string("path begin failed: ") + (response.empty() ? "timeout" : response);
            }
            return StartJob();
        }

        cJSON* segment = nullptr;
        int segment_index = 0;
        cJSON_ArrayForEach(segment, root) {
            if (!cJSON_IsObject(segment)) {
                cJSON_Delete(root);
                return std::string("invalid path segment");
            }

            cJSON* cmd_item = cJSON_GetObjectItemCaseSensitive(segment, "cmd");
            cJSON* x_item = cJSON_GetObjectItemCaseSensitive(segment, "x");
            cJSON* y_item = cJSON_GetObjectItemCaseSensitive(segment, "y");
            if (!cJSON_IsString(cmd_item) || cmd_item->valuestring == nullptr || !cJSON_IsNumber(x_item) || !cJSON_IsNumber(y_item)) {
                cJSON_Delete(root);
                return std::string("path segment missing cmd/x/y");
            }

            char xbuf[32];
            char ybuf[32];
            JsonNumberToString(x_item, xbuf, sizeof(xbuf));
            JsonNumberToString(y_item, ybuf, sizeof(ybuf));

            std::string cmd = cmd_item->valuestring;
            if (cmd != "M" && cmd != "L") {
                cJSON_Delete(root);
                return std::string("unsupported segment cmd");
            }

            response = SendU1ProtocolJson(task_id,
                                          "PATH_SEG",
                                          "\"segment_index\":" + std::to_string(segment_index) +
                                              ",\"segment_cmd\":\"" + cmd +
                                              "\",\"x\":" + xbuf +
                                              ",\"y\":" + ybuf +
                                              ",\"feed\":" + std::to_string(feed_rate),
                                          150);
            if (response.empty() || response.find("\"type\":\"error\"") != std::string::npos) {
                cJSON_Delete(root);
                auto staged = LoadJob(fallback_gcode, false);
                if (std::holds_alternative<std::string>(staged)) {
                    return std::string("path segment failed: ") + (response.empty() ? "timeout" : response);
                }
                return StartJob();
            }
            ++segment_index;
        }

        cJSON_Delete(root);

        response = SendU1ProtocolJson(task_id, "PATH_END", "", 120000);
        if (!response.empty()) {
            return response;
        }

        auto staged = LoadJob(fallback_gcode, false);
        if (std::holds_alternative<std::string>(staged)) {
            return std::string("path end sent");
        }
        return StartJob();
    }

    ReturnValue StartJob() {
        bool started = false;
        {
            std::lock_guard<std::mutex> lock(job_mutex_);
            if (job_task_handle_ != nullptr || job_state_ == JobState::Running || job_state_ == JobState::Paused) {
                return std::string("job already running");
            }
            if (job_lines_.empty()) {
                return std::string("no staged gcode");
            }

            job_state_ = JobState::Running;
            job_next_line_ = 0;
            job_error_.clear();
            job_last_response_.clear();
            job_cancel_requested_ = false;
            job_pause_requested_ = false;

            if (xTaskCreate(JobTaskThunk, "u1_gcode_job", 6144, this, 5, &job_task_handle_) == pdPASS) {
                started = true;
            } else {
                job_task_handle_ = nullptr;
                job_state_ = JobState::Failed;
                job_error_ = "failed to start gcode worker";
                return std::string(job_error_);
            }
        }

        if (started) {
            return BuildJobStatusJsonSnapshot();
        }
        return std::string("failed to start job");
    }

    ReturnValue CancelJob() {
        bool send_hold = false;
        bool return_status = false;
        {
            std::lock_guard<std::mutex> lock(job_mutex_);
            if (job_state_ == JobState::Idle) {
                return std::string("no active job");
            }
            if (job_state_ == JobState::Staged) {
                job_lines_.clear();
                job_next_line_ = 0;
                job_state_ = JobState::Cancelled;
                job_cancel_requested_ = false;
                job_pause_requested_ = false;
                return_status = true;
            } else {
                job_cancel_requested_ = true;
                send_hold = true;
            }
        }

        if (return_status) {
            return BuildJobStatusJsonSnapshot();
        }
        if (send_hold) {
            SendU1Realtime('!', 80);
        }
        return BuildJobStatusJsonSnapshot();
    }

    ReturnValue PauseJob() {
        {
            std::lock_guard<std::mutex> lock(job_mutex_);
            if (job_state_ != JobState::Running && job_state_ != JobState::Paused) {
                return std::string("job not running");
            }
            job_pause_requested_ = true;
        }
        SendU1Realtime('!', 80);
        return BuildJobStatusJsonSnapshot();
    }

    ReturnValue ResumeJob() {
        {
            std::lock_guard<std::mutex> lock(job_mutex_);
            if (job_state_ != JobState::Paused) {
                return std::string("job not paused");
            }
            job_pause_requested_ = false;
            job_state_ = JobState::Running;
        }
        SendU1Realtime('~', 80);
        return BuildJobStatusJsonSnapshot();
    }

    void InitializeTools() {
        auto& mcp_server = McpServer::GetInstance();

        mcp_server.AddTool("self.motor.home",
                           "Send HOME through the private protocol, with Grbl $H fallback.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue {
                               auto response = SendU1ProtocolCommand("t_home_local", "HOME", 250);
                               if (!response.empty()) {
                                   return response;
                               }

                               response = SendU1Line("$H", 250);
                               return response.empty() ? std::string("sent: $H") : std::string("sent: $H; response: ") + response;
                           });

        mcp_server.AddTool("self.motor.unlock",
                           "Send Grbl unlock command $X to U1.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue {
                               auto response = SendU1Line("$X", 150);
                               return response.empty() ? std::string("sent: $X") : std::string("sent: $X; response: ") + response;
                           });

        mcp_server.AddTool("self.motor.get_status",
                           "Query U1 status through the private protocol, with Grbl realtime status fallback.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue {
                               auto response = SendU1ProtocolCommand("t_status_local", "GET_STATUS", 120);
                               if (!response.empty()) {
                                   return response;
                               }

                               response = SendU1Realtime('?', 100);
                               return response.empty() ? std::string("status query sent") : response;
                           });

        mcp_server.AddTool("self.motor.send_gcode",
                           "Send a raw Grbl command line to U1.",
                           PropertyList({
                               Property("command", kPropertyTypeString)
                           }),
                           [this](const PropertyList& properties) -> ReturnValue {
                               auto command = properties["command"].value<std::string>();
                               auto response = SendU1Line(command, 150);
                               return response.empty() ? std::string("sent: ") + command : std::string("sent: ") + command + "; response: " + response;
                           });

        mcp_server.AddTool("self.motor.jog",
                           "Send a relative Grbl jog command to U1.",
                           PropertyList({
                               Property("x", kPropertyTypeInteger, 0),
                               Property("y", kPropertyTypeInteger, 0),
                               Property("z", kPropertyTypeInteger, 0),
                               Property("feed", kPropertyTypeInteger, 1000, 1, 20000)
                           }),
                           [this](const PropertyList& properties) -> ReturnValue {
                               int x = properties["x"].value<int>();
                               int y = properties["y"].value<int>();
                               int z = properties["z"].value<int>();
                               int feed = properties["feed"].value<int>();

                               std::string command = "$J=G91";
                               if (x != 0) {
                                   command += " X" + std::to_string(x);
                               }
                               if (y != 0) {
                                   command += " Y" + std::to_string(y);
                               }
                               if (z != 0) {
                                   command += " Z" + std::to_string(z);
                               }
                               command += " F" + std::to_string(feed);

                               auto response = SendU1Line(command, 150);
                               return response.empty() ? std::string("sent: ") + command : std::string("sent: ") + command + "; response: " + response;
                           });

        mcp_server.AddTool("self.motor.move_abs",
                           "Send absolute MOVE through the private protocol.",
                           PropertyList({
                               Property("x", kPropertyTypeInteger, 0),
                               Property("y", kPropertyTypeInteger, 0),
                               Property("z", kPropertyTypeInteger, 0),
                               Property("feed", kPropertyTypeInteger, 1000, 1, 20000)
                           }),
                           [this](const PropertyList& properties) -> ReturnValue {
                               int x = properties["x"].value<int>();
                               int y = properties["y"].value<int>();
                               int z = properties["z"].value<int>();
                               int feed = properties["feed"].value<int>();

                               auto response = SendU1MoveAbsCommand(x, y, z, feed, 200);
                               if (!response.empty()) {
                                   return response;
                               }

                               std::string command = "G90 G1 X" + std::to_string(x) + " Y" + std::to_string(y) +
                                                     " Z" + std::to_string(z) + " F" + std::to_string(feed);
                               response = SendU1Line(command, 150);
                               return response.empty() ? std::string("sent: ") + command : std::string("sent: ") + command + "; response: " + response;
                           });

        mcp_server.AddTool("self.motor.run_path",
                           "Run a path capability on U8 using JSON array segments and the existing async job pipeline.",
                           PropertyList({
                               Property("path_json", kPropertyTypeString),
                               Property("feed", kPropertyTypeInteger, 1200, 1, 20000)
                           }),
                           [this](const PropertyList& properties) -> ReturnValue {
                               return RunPath(properties["path_json"].value<std::string>(),
                                              properties["feed"].value<int>());
                           });

        mcp_server.AddTool("self.motor.set_laser_power",
                           "Set U1 laser power through standard Grbl spindle commands.",
                           PropertyList({
                               Property("power", kPropertyTypeInteger, 0, 0, 1000)
                           }),
                           [this](const PropertyList& properties) -> ReturnValue {
                               int power = properties["power"].value<int>();
                               std::string command = power == 0 ? "M5" : "M3 S" + std::to_string(power);
                               auto response = SendU1Line(command, 150);
                               return response.empty() ? std::string("sent: ") + command : std::string("sent: ") + command + "; response: " + response;
                           });

        mcp_server.AddTool("self.motor.pause",
                           "Send realtime feed hold to U1 and pause staged job dispatch.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue { return PauseJob(); });

        mcp_server.AddTool("self.motor.resume",
                           "Send realtime cycle start to U1 and resume staged job dispatch.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue { return ResumeJob(); });

        mcp_server.AddTool("self.motor.job_load",
                           "Stage G-code lines for asynchronous execution over the U8 to U1 UART bridge.",
                           PropertyList({
                               Property("gcode", kPropertyTypeString),
                               Property("append", kPropertyTypeBoolean, false)
                           }),
                           [this](const PropertyList& properties) -> ReturnValue {
                               return LoadJob(properties["gcode"].value<std::string>(),
                                              properties["append"].value<bool>());
                           });

        mcp_server.AddTool("self.motor.job_start",
                           "Start the staged G-code job.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue { return StartJob(); });

        mcp_server.AddTool("self.motor.job_status",
                           "Get staged or running job status.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue { return BuildJobStatusJsonSnapshot(); });

        mcp_server.AddTool("self.motor.job_cancel",
                           "Cancel the staged or running G-code job.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue { return CancelJob(); });
    }

public:
    DlcMotorControlP1AiBoard()
        : boot_button_(BOOT_BUTTON_GPIO) {
        InitializeI2c();
        InitializeButtons();
        InitializeCamera();
        InitializeU1Uart();
        InitializeTools();
    }

    void StartNetwork() override {
        WifiBoard::StartNetwork();
        vTaskDelay(pdMS_TO_TICKS(1000));
        InitializeWebSocketControlServer();
    }

    AudioCodec* GetAudioCodec() override {
        static DlcMotorControlP1AudioCodec audio_codec(i2c_bus_);
        return &audio_codec;
    }

    Camera* GetCamera() override {
        return camera_;
    }
};

DECLARE_BOARD(DlcMotorControlP1AiBoard);
