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

#include <cstdlib>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>

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
    struct U1CapabilityResult {
        bool ok = false;
        uint32_t msg_id = 0;
        std::string task_id;
        std::string cmd;
        std::string response_type;
        std::string state;
        std::string error_code;
        std::string error_message;
        std::string raw_response;
    };

    i2c_master_bus_handle_t i2c_bus_ = nullptr;
    Button boot_button_;
    Esp32Camera* camera_ = nullptr;
    WebSocketControlServer* ws_control_server_ = nullptr;
    std::mutex uart_mutex_;
    std::mutex job_mutex_;
    uint32_t protocol_msg_id_ = 0;

    static bool JsonNumberToString(cJSON* item, char* buffer, size_t buffer_size) {
        if (item == nullptr || buffer == nullptr || buffer_size == 0 || !cJSON_IsNumber(item)) {
            return false;
        }
        snprintf(buffer, buffer_size, "%.3f", item->valuedouble);
        return true;
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

    static std::string GetJsonStringValue(cJSON* root, const char* key) {
        cJSON* item = cJSON_GetObjectItemCaseSensitive(root, key);
        if (item != nullptr && cJSON_IsString(item) && item->valuestring != nullptr) {
            return item->valuestring;
        }
        return "";
    }

    static cJSON* BuildCapabilityResponseJson(const U1CapabilityResult& result) {
        cJSON* root = cJSON_CreateObject();
        cJSON_AddBoolToObject(root, "ok", result.ok);
        cJSON_AddNumberToObject(root, "msg_id", static_cast<double>(result.msg_id));
        cJSON_AddStringToObject(root, "task_id", result.task_id.c_str());
        cJSON_AddStringToObject(root, "cmd", result.cmd.c_str());
        cJSON_AddStringToObject(root, "response_type", result.response_type.c_str());
        cJSON_AddStringToObject(root, "raw", result.raw_response.c_str());
        if (!result.state.empty()) {
            cJSON_AddStringToObject(root, "state", result.state.c_str());
        }
        if (!result.error_code.empty()) {
            cJSON_AddStringToObject(root, "error_code", result.error_code.c_str());
        }
        if (!result.error_message.empty()) {
            cJSON_AddStringToObject(root, "error_message", result.error_message.c_str());
        }
        return root;
    }

    ReturnValue ParseCapabilityResponse(const std::string& raw_response,
                                        uint32_t msg_id,
                                        const std::string& task_id,
                                        const std::string& cmd) {
        U1CapabilityResult result;
        result.msg_id = msg_id;
        result.task_id = task_id;
        result.cmd = cmd;
        result.raw_response = raw_response;

        if (raw_response.empty()) {
            result.response_type = "timeout";
            result.error_code = "timeout";
            result.error_message = "empty response from u1";
            return BuildCapabilityResponseJson(result);
        }

        cJSON* root = cJSON_Parse(raw_response.c_str());
        if (root == nullptr || !cJSON_IsObject(root)) {
            if (root != nullptr) {
                cJSON_Delete(root);
            }
            result.response_type = "invalid";
            result.error_code = "invalid_response";
            result.error_message = "u1 response is not valid json";
            return BuildCapabilityResponseJson(result);
        }

        result.response_type = GetJsonStringValue(root, "type");
        result.state = GetJsonStringValue(root, "state");
        result.error_code = GetJsonStringValue(root, "code");
        result.error_message = GetJsonStringValue(root, "message");

        bool parsed_msg_id = false;
        cJSON* msg_id_item = cJSON_GetObjectItemCaseSensitive(root, "msg_id");
        if (msg_id_item != nullptr && cJSON_IsString(msg_id_item) && msg_id_item->valuestring != nullptr) {
            result.msg_id = static_cast<uint32_t>(strtoul(msg_id_item->valuestring, nullptr, 10));
            parsed_msg_id = true;
        }

        auto response_task_id = GetJsonStringValue(root, "task_id");
        if (!response_task_id.empty()) {
            result.task_id = response_task_id;
        }

        result.ok = result.response_type == "ack" || result.response_type == "status" || result.response_type == "result";
        if (parsed_msg_id && result.msg_id != msg_id) {
            result.ok = false;
            result.response_type = "invalid";
            result.error_code = "msg_id_mismatch";
            result.error_message = "u1 response msg_id does not match request";
        } else if (!response_task_id.empty() && response_task_id != task_id) {
            result.ok = false;
            result.response_type = "invalid";
            result.error_code = "task_id_mismatch";
            result.error_message = "u1 response task_id does not match request";
        }
        if (result.response_type == "error" && result.error_message.empty()) {
            result.error_message = "u1 returned error";
        }
        if (result.response_type.empty()) {
            result.response_type = "unknown";
            if (result.error_message.empty()) {
                result.error_message = "u1 response missing type";
            }
        }

        cJSON_Delete(root);
        return BuildCapabilityResponseJson(result);
    }

    uint32_t NextProtocolMessageId() {
        std::lock_guard<std::mutex> lock(job_mutex_);
        return ++protocol_msg_id_;
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

    std::string BuildProtocolCommandJson(uint32_t msg_id,
                                         const std::string& task_id,
                                         const std::string& cmd,
                                         const std::string& extra_fields = "") {
        std::string line = "{\"msg_id\":\"" + std::to_string(msg_id) + "\",\"type\":\"cmd\",\"task_id\":\"" + task_id +
                           "\",\"cmd\":\"" + cmd + "\"";
        if (!extra_fields.empty()) {
            line += "," + extra_fields;
        }
        line += "}";
        return line;
    }

    std::string SendU1ProtocolCommand(uint32_t msg_id, const std::string& task_id, const std::string& cmd, int timeout_ms = 120) {
        std::string line = BuildProtocolCommandJson(msg_id, task_id, cmd);
        return SendU1Line("@" + line, timeout_ms);
    }

    std::string SendU1ProtocolJson(uint32_t msg_id,
                                   const std::string& task_id,
                                   const std::string& cmd,
                                   const std::string& extra_fields,
                                   int timeout_ms = 120) {
        std::string line = BuildProtocolCommandJson(msg_id, task_id, cmd, extra_fields);
        return SendU1Line("@" + line, timeout_ms);
    }

    ReturnValue ExecuteHomeCapability() {
        const std::string task_id = "t_home_local";
        const uint32_t msg_id = NextProtocolMessageId();
        return ParseCapabilityResponse(SendU1ProtocolCommand(msg_id, task_id, "HOME", 250), msg_id, task_id, "HOME");
    }

    ReturnValue ExecuteGetStatusCapability() {
        const std::string task_id = "t_status_local";
        const uint32_t msg_id = NextProtocolMessageId();
        return ParseCapabilityResponse(SendU1ProtocolCommand(msg_id, task_id, "GET_STATUS", 120), msg_id, task_id, "GET_STATUS");
    }

    ReturnValue ExecuteMoveCapability(int x, int y, int z, int feed) {
        if (feed < 1 || feed > 20000) {
            return std::string("invalid move params: feed must be within [1, 20000]");
        }

        const std::string task_id = "t_move_local";
        const uint32_t msg_id = NextProtocolMessageId();
        const std::string extra_fields = "\"x\":" + std::to_string(x) +
                                         ",\"y\":" + std::to_string(y) +
                                         ",\"z\":" + std::to_string(z) +
                                         ",\"feed\":" + std::to_string(feed);
        auto response = SendU1ProtocolJson(msg_id, task_id, "MOVE", extra_fields, 200);
        return ParseCapabilityResponse(response, msg_id, task_id, "MOVE");
    }

    void InitializeWebSocketControlServer() {
        ws_control_server_ = new WebSocketControlServer();
        if (!ws_control_server_->Start(8080)) {
            delete ws_control_server_;
            ws_control_server_ = nullptr;
        }
    }

    ReturnValue RunPath(const std::string& path_json, int feed_rate) {
        cJSON* root = cJSON_Parse(path_json.c_str());
        if (root == nullptr || !cJSON_IsArray(root)) {
            if (root != nullptr) {
                cJSON_Delete(root);
            }
            return std::string("invalid path json");
        }
        int total_segments = cJSON_GetArraySize(root);
        if (total_segments <= 0) {
            cJSON_Delete(root);
            return std::string("path is empty");
        }

        const std::string task_id = "t_path_local";
        auto response = SendU1ProtocolJson(NextProtocolMessageId(), task_id, "PATH_BEGIN",
                                           "\"total_segments\":" + std::to_string(total_segments) +
                                               ",\"feed\":" + std::to_string(feed_rate),
                                           150);
        if (response.empty() || response.find("\"type\":\"error\"") != std::string::npos) {
            cJSON_Delete(root);
            return std::string("path begin failed: ") + (response.empty() ? "timeout" : response);
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

            response = SendU1ProtocolJson(NextProtocolMessageId(), task_id,
                                          "PATH_SEG",
                                          "\"segment_index\":" + std::to_string(segment_index) +
                                              ",\"segment_cmd\":\"" + cmd +
                                              "\",\"x\":" + xbuf +
                                              ",\"y\":" + ybuf +
                                              ",\"feed\":" + std::to_string(feed_rate),
                                          150);
            if (response.empty() || response.find("\"type\":\"error\"") != std::string::npos) {
                cJSON_Delete(root);
                return std::string("path segment failed: ") + (response.empty() ? "timeout" : response);
            }
            ++segment_index;
        }

        cJSON_Delete(root);

        response = SendU1ProtocolJson(NextProtocolMessageId(), task_id, "PATH_END", "", 120000);
        if (response.empty()) {
            return std::string("path end failed: timeout");
        }
        if (response.find("\"type\":\"error\"") != std::string::npos) {
            return std::string("path end failed: ") + response;
        }
        return response;
    }

    void InitializeTools() {
        auto& mcp_server = McpServer::GetInstance();

        mcp_server.AddTool("self.motor.home",
                           "Send HOME through the private protocol.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue {
                               return ExecuteHomeCapability();
                           });

        mcp_server.AddTool("self.motor.get_status",
                           "Query U1 status through the private protocol.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue {
                               return ExecuteGetStatusCapability();
                           });

        mcp_server.AddTool("self.motor.move_abs",
                           "Send MOVE through the private protocol.",
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
                                return ExecuteMoveCapability(x, y, z, feed);
                            });

        mcp_server.AddTool("self.motor.run_path",
                           "Run a path capability on U8 through the private protocol.",
                           PropertyList({
                                Property("path_json", kPropertyTypeString),
                                Property("feed", kPropertyTypeInteger, 1200, 1, 20000)
                           }),
                           [this](const PropertyList& properties) -> ReturnValue {
                               return RunPath(properties["path_json"].value<std::string>(),
                                              properties["feed"].value<int>());
                           });
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
