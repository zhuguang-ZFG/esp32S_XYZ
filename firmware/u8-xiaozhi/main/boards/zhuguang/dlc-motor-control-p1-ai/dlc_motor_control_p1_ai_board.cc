#include "wifi_board.h"
#include "audio/codecs/box_audio_codec.h"
#include "application.h"
#include "button.h"
#include "config.h"
#include "esp32_camera.h"
#include "mcp_server.h"
#include "system_info.h"
#include "websocket_control_server.h"

#include <driver/gpio.h>
#include <driver/i2c_master.h>
#include <esp_log.h>
#include <nvs.h>
#include <cJSON.h>

#include <string>

#include "motion_event_emitter.h"
#include "motion_executor.h"
#include "u1_protocol_client.h"

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
    i2c_master_bus_handle_t i2c_bus_ = nullptr;
    Button boot_button_;
    Esp32Camera* camera_ = nullptr;
    WebSocketControlServer* ws_control_server_ = nullptr;

    U1ProtocolClient protocol_;
    MotionEventEmitter emitter_;
    MotionExecutor executor_;

    // --- DLC API helpers (strategy-1: device calls dlc_api to generate path) ---

    // SEC-007: Read per-device token from NVS; never compiled into firmware
        static void MigrateDlcApiToken(const char* token) {
        nvs_handle_t handle;
        if (nvs_open("dlc", NVS_READWRITE, &handle) != ESP_OK) {
            return;
        }
        if (nvs_set_str(handle, "api_token", token) == ESP_OK) {
            nvs_commit(handle);
        }
        nvs_close(handle);
    }

    std::string GetDlcApiToken() {
        char token[128] = {0};
        size_t len = sizeof(token);
        nvs_handle_t handle;
        esp_err_t err = nvs_open("dlc", NVS_READONLY, &handle);
        if (err == ESP_OK) {
            err = nvs_get_str(handle, "api_token", token, &len);
            nvs_close(handle);
            if (err == ESP_OK && token[0] != '\0') {
                if (len > sizeof(token)) {
                    ESP_LOGE(TAG, "dlc api_token too long (%zu bytes), refusing truncated token", len);
                    return std::string();
                }
                return std::string(token);
            }
        } else {
            ESP_LOGW(TAG, "dlc api_token: NVS open failed (%s)", esp_err_to_name(err));
        }

        char secret[128] = {0};
        size_t secret_len = sizeof(secret);
        if (nvs_open("wifi", NVS_READONLY, &handle) != ESP_OK) {
            return std::string();
        }
        err = nvs_get_str(handle, "device_secret", secret, &secret_len);
        nvs_close(handle);
        if (err != ESP_OK || secret[0] == '\0') {
            ESP_LOGE(TAG, "dlc api_token not found in NVS");
            return std::string();
        }
        MigrateDlcApiToken(secret);
        return std::string(secret);
    }

    // SEC-007: Force HTTPS; reject http:// to protect token
    // SEC-005: Validate response size BEFORE ReadAll to prevent OOM
    std::string PostDlcApi(const std::string& path, const std::string& body) {
        const std::string base_url = DLC_API_BASE_URL;
        if (base_url.rfind("https://", 0) != 0) {
            ESP_LOGE(TAG, "DLC_API_BASE_URL must be https://");
            return "";
        }

        const std::string token = GetDlcApiToken();
        if (token.empty()) {
            ESP_LOGE(TAG, "dlc api_token not found in NVS");
            return "";
        }

        auto& board = Board::GetInstance();
        // 固件审查 P1：CreateHttp(timeout_seconds)。0=无限阻塞，改 15s 防半开连接挂死 MCP 线程。
        auto http = board.GetNetwork()->CreateHttp(15);
        http->SetHeader("Content-Type", "application/json");
        http->SetHeader("Accept", "application/json");
        http->SetHeader("Authorization", "Bearer " + token);
        // Http::SetContent takes std::string&&; copy then move (body is const lvalue).
        http->SetContent(std::string(body));

        std::string url = base_url + path;
        if (!http->Open("POST", url)) {
            ESP_LOGE(TAG, "dlc_api POST failed: cannot connect to %s", url.c_str());
            return "";
        }

        // 固件审查 P1：SEC-005 修正——在 ReadAll 前用 Content-Length 预检，超限直接 Close，
        // 避免恶意响应先占满堆内存再丢弃。GetBodyLength() 在 Open 后、ReadAll 前可调。
        size_t body_length = http->GetBodyLength();
        if (body_length > 0 && body_length > DLC_API_MAX_RESPONSE_BYTES) {
            ESP_LOGE(TAG, "dlc_api response too large (declared %zu bytes), aborting",
                     body_length);
            http->Close();
            return "";
        }

        int status = http->GetStatusCode();
        std::string response = http->ReadAll();
        http->Close();

        // SEC-005: 兜底——Content-Length 缺失（chunked）时，读后再校验累计大小。
        if (response.size() > DLC_API_MAX_RESPONSE_BYTES) {
            ESP_LOGE(TAG, "dlc_api response exceeded limit after read: %zu bytes",
                     response.size());
            return "";
        }

        if (status != 200) {
            ESP_LOGE(TAG, "dlc_api returned status %d", status);
            return "";
        }
        return response;
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

    void InitializeWebSocketControlServer() {
        ws_control_server_ = new WebSocketControlServer();
        if (!ws_control_server_->Start(8080)) {
            // 安全修复：未配 control_ws_token 时 Start 拒绝启动。记录错误（非静默），
            // 本地控制功能禁用直至 NVS 配置 token。
            ESP_LOGE(TAG, "Local control WS disabled: control_ws_token not configured");
            delete ws_control_server_;
            ws_control_server_ = nullptr;
        }
    }

    void InitializeTools() {
        auto& mcp_server = McpServer::GetInstance();

        mcp_server.AddTool("self.motor.home",
                           "Send HOME through the private protocol.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue {
                               return executor_.ExecuteHomeCapability();
                           });

        mcp_server.AddTool("self.motor.get_status",
                           "Query U1 status through the private protocol.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue {
                               return executor_.ExecuteGetStatusCapability();
                           });

        mcp_server.AddTool("self.motor.get_device_info",
                           "Query U1 device information through the private protocol.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue {
                               return executor_.ExecuteGetDeviceInfoCapability();
                           });

        mcp_server.AddTool("self.motor.pause",
                           "Send PAUSE through the private protocol.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue {
                               return executor_.ExecutePauseCapability();
                           });

        mcp_server.AddTool("self.motor.resume",
                           "Send RESUME through the private protocol.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue {
                               return executor_.ExecuteResumeCapability();
                           });

        mcp_server.AddTool("self.motor.stop",
                           "Send STOP through the private protocol.",
                           PropertyList(),
                           [this](const PropertyList&) -> ReturnValue {
                               return executor_.ExecuteStopCapability();
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
                                return executor_.ExecuteMoveCapability(x, y, z, feed);
                            });

        mcp_server.AddTool("self.motor.move_rel",
                           "Move by a whitelisted relative step of at most 1 mm per axis.",
                           PropertyList({
                               Property("dx", kPropertyTypeInteger, 0, -1, 1),
                               Property("dy", kPropertyTypeInteger, 0, -1, 1),
                               Property("dz", kPropertyTypeInteger, 0, -1, 1),
                               Property("feed", kPropertyTypeInteger, 800, 1, 20000)
                           }),
                           [this](const PropertyList& properties) -> ReturnValue {
                               int dx = properties["dx"].value<int>();
                               int dy = properties["dy"].value<int>();
                               int dz = properties["dz"].value<int>();
                               int feed = properties["feed"].value<int>();
                               return executor_.ExecuteMoveRelCapability(dx, dy, dz, feed);
                           });

        mcp_server.AddTool("self.motor.run_path",
                           "Run a path capability on U8 through the private protocol.",
                           PropertyList({
                                Property("path_json", kPropertyTypeString),
                                Property("feed", kPropertyTypeInteger, 1200, 1, 20000)
                           }),
                           [this](const PropertyList& properties) -> ReturnValue {
                               return executor_.RunPath(properties["path_json"].value<std::string>(),
                                                        properties["feed"].value<int>());
                           });

        // --- Strategy-1 high-level tools: device calls dlc_api then executes locally ---

        mcp_server.AddTool("self.plotter.write_text",
                           "在绘图机上写字。先通过云端生成路径，再本地执行。",
                           PropertyList({
                               Property("text", kPropertyTypeString),
                               Property("feed", kPropertyTypeInteger, 1200, 1, 20000)
                           }),
                           [this](const PropertyList& properties) -> ReturnValue {
                               std::string text = properties["text"].value<std::string>();
                               int feed = properties["feed"].value<int>();
                               if (text.empty() || text.length() > 40) {
                                   return std::string("invalid text length");
                               }

                               // 1. Call dlc_api to generate path
                               cJSON* req = cJSON_CreateObject();
                               cJSON_AddStringToObject(req, "type", "write_text");
                               cJSON* payload = cJSON_CreateObject();
                               cJSON_AddStringToObject(payload, "text", text.c_str());
                               cJSON_AddItemToObject(req, "payload", payload);
                               cJSON_AddStringToObject(req, "device_id", SystemInfo::GetMacAddress().c_str());
                               char* body = cJSON_PrintUnformatted(req);
                               std::string resp = PostDlcApi("/dlc/tasks/preview", body);
                               cJSON_free(body);
                               cJSON_Delete(req);

                               if (resp.empty()) {
                                   return std::string("路径生成失败：dlc_api 无响应");
                               }

                               // 2. Parse response and extract path_data
                               cJSON* root = cJSON_Parse(resp.c_str());
                               if (root == nullptr) {
                                   return std::string("路径生成失败：dlc_api 返回异常");
                               }
                               cJSON* path_data = cJSON_GetObjectItemCaseSensitive(root, "path_data");
                               if (!cJSON_IsArray(path_data)) {
                                   cJSON_Delete(root);
                                   return std::string("路径生成失败：dlc_api 未返回 path_data");
                               }

                               // 3. Execute path locally
                               char* path_json = cJSON_PrintUnformatted(path_data);
                               std::string task_id = protocol_.NextLocalTaskId("mcp_write");
                               ReturnValue rv = executor_.RunPathWithTaskId(task_id, path_json, feed, true);
                               cJSON_free(path_json);
                               cJSON_Delete(root);
                               return rv;
                           });

        mcp_server.AddTool("self.plotter.draw_generated",
                           "在绘图机上画图。先通过云端 AI 生成图像并矢量化为路径，再本地执行。",
                           PropertyList({
                               Property("prompt", kPropertyTypeString),
                               Property("feed", kPropertyTypeInteger, 1200, 1, 20000)
                           }),
                           [this](const PropertyList& properties) -> ReturnValue {
                               std::string prompt = properties["prompt"].value<std::string>();
                               int feed = properties["feed"].value<int>();
                               if (prompt.empty() || prompt.length() > 80) {
                                   return std::string("invalid prompt length");
                               }

                               // 1. Call dlc_api to generate path
                               cJSON* req = cJSON_CreateObject();
                               cJSON_AddStringToObject(req, "type", "draw_generated");
                               cJSON* payload = cJSON_CreateObject();
                               cJSON_AddStringToObject(payload, "prompt", prompt.c_str());
                               cJSON_AddItemToObject(req, "payload", payload);
                               cJSON_AddStringToObject(req, "device_id", SystemInfo::GetMacAddress().c_str());
                               char* body = cJSON_PrintUnformatted(req);
                               std::string resp = PostDlcApi("/dlc/tasks/preview", body);
                               cJSON_free(body);
                               cJSON_Delete(req);

                               if (resp.empty()) {
                                   return std::string("绘图生成失败：dlc_api 无响应");
                               }

                               // 2. Parse response and extract path_data
                               cJSON* root = cJSON_Parse(resp.c_str());
                               if (root == nullptr) {
                                   return std::string("绘图生成失败：dlc_api 返回异常");
                               }
                               cJSON* path_data = cJSON_GetObjectItemCaseSensitive(root, "path_data");
                               if (!cJSON_IsArray(path_data)) {
                                   cJSON_Delete(root);
                                   return std::string("绘图生成失败：dlc_api 未返回 path_data");
                               }

                               // 3. Execute path locally
                               char* path_json = cJSON_PrintUnformatted(path_data);
                               std::string task_id = protocol_.NextLocalTaskId("mcp_draw");
                               ReturnValue rv = executor_.RunPathWithTaskId(task_id, path_json, feed, true);
                               cJSON_free(path_json);
                               cJSON_Delete(root);
                               return rv;
                           });
    }

    void HandleMotionTaskJson(const cJSON* root) override {
        if (root == nullptr) {
            return;
        }
        cJSON* cap_item = cJSON_GetObjectItemCaseSensitive(root, "capability");
        if (!cJSON_IsString(cap_item) || cap_item->valuestring == nullptr) {
            ESP_LOGW(TAG, "motion_task: missing capability");
            std::string fallback_id;
            cJSON* task_item = cJSON_GetObjectItemCaseSensitive(root, "task_id");
            if (cJSON_IsString(task_item) && task_item->valuestring != nullptr &&
                task_item->valuestring[0] != '\0') {
                fallback_id = task_item->valuestring;
            } else {
                fallback_id = protocol_.NextLocalTaskId("motion");
            }
            emitter_.EmitError(fallback_id, "failed", "E_UNSUPPORTED_CAPABILITY",
                               "capability field is missing");
            return;
        }
        const std::string cap_norm =
            U1ProtocolClient::NormalizeMotionCapabilityName(cap_item->valuestring);

        std::string task_id;
        cJSON* task_item = cJSON_GetObjectItemCaseSensitive(root, "task_id");
        if (cJSON_IsString(task_item) && task_item->valuestring != nullptr &&
            task_item->valuestring[0] != '\0') {
            task_id = task_item->valuestring;
        } else {
            ESP_LOGW(TAG, "motion_task: missing task_id, generating local id");
            task_id = protocol_.NextLocalTaskId("motion");
        }

        emitter_.ClearMotionContext();
        std::string device_id;
        cJSON* dev_item = cJSON_GetObjectItemCaseSensitive(root, "device_id");
        if (cJSON_IsString(dev_item) && dev_item->valuestring != nullptr) {
            device_id = dev_item->valuestring;
        }
        std::string source;
        cJSON* source_item = cJSON_GetObjectItemCaseSensitive(root, "source");
        if (cJSON_IsString(source_item) && source_item->valuestring != nullptr) {
            source = source_item->valuestring;
        }
        emitter_.SetMotionContext(device_id, cap_item->valuestring, source);

        cJSON* params = cJSON_GetObjectItemCaseSensitive(root, "params");
        ESP_LOGI(TAG, "motion_task capability=%s task_id=%s", cap_norm.c_str(),
                 task_id.c_str());

        if (Application::GetInstance().GetDeviceState() == kDeviceStateUpgrading) {
            ESP_LOGW(TAG,
                     "motion_task rejected while firmware upgrade is active task_id=%s",
                     task_id.c_str());
            emitter_.EmitError(task_id, "failed", "E_DEVICE_UPDATING",
                               "device is updating");
            return;
        }

        if (cap_norm == "home" || cap_norm == "motor.home") {
            emitter_.EmitPhase(task_id, "accepted");
            emitter_.EmitPhase(task_id, "running");
            ReturnValue rv = executor_.ExecuteHomeWithTaskId(task_id);
            ReturnValueJsonGuard rv_guard(rv);
            emitter_.EmitDoneOrFailed(rv, task_id);
        } else if (cap_norm == "get_status" || cap_norm == "motor.get_status" ||
                   cap_norm == "getstatus") {
            emitter_.EmitPhase(task_id, "accepted");
            emitter_.EmitPhase(task_id, "running");
            ReturnValue rv = executor_.ExecuteGetStatusWithTaskId(task_id);
            ReturnValueJsonGuard rv_guard(rv);
            emitter_.EmitDoneOrFailed(rv, task_id);
        } else if (cap_norm == "get_device_info" ||
                   cap_norm == "motor.get_device_info" ||
                   cap_norm == "device_info") {
            emitter_.EmitPhase(task_id, "accepted");
            emitter_.EmitPhase(task_id, "running");
            ReturnValue rv = executor_.ExecuteGetDeviceInfoWithTaskId(task_id);
            ReturnValueJsonGuard rv_guard(rv);
            emitter_.EmitDeviceInfoIfOk(rv, task_id);
            emitter_.EmitDoneOrFailed(rv, task_id);
        } else if (cap_norm == "pause" || cap_norm == "motor.pause") {
            emitter_.EmitPhase(task_id, "accepted");
            emitter_.EmitPhase(task_id, "running");
            ReturnValue rv = executor_.ExecuteControlWithTaskId(task_id, "PAUSE");
            ReturnValueJsonGuard rv_guard(rv);
            emitter_.EmitDoneOrFailed(rv, task_id);
        } else if (cap_norm == "resume" || cap_norm == "motor.resume") {
            emitter_.EmitPhase(task_id, "accepted");
            emitter_.EmitPhase(task_id, "running");
            ReturnValue rv = executor_.ExecuteControlWithTaskId(task_id, "RESUME");
            ReturnValueJsonGuard rv_guard(rv);
            emitter_.EmitDoneOrFailed(rv, task_id);
        } else if (cap_norm == "stop" || cap_norm == "motor.stop") {
            emitter_.EmitPhase(task_id, "accepted");
            emitter_.EmitPhase(task_id, "running");
            // 固件审查 P2：STOP 走抢占式 U1 路径，避免在 PATH_END 长等待期间被 UART 锁阻塞。
            ReturnValue rv = executor_.ExecuteStopWithTaskId(task_id);
            ReturnValueJsonGuard rv_guard(rv);
            emitter_.EmitDoneOrFailed(rv, task_id);
        } else if (cap_norm == "estop" || cap_norm == "motor.estop" ||
                   cap_norm == "emergency_stop") {
            // 固件审查 P1/P2：硬件级急停通道。STOP 是暂停级（可恢复），estop 映射 U1 的
            // ESTOP（Grbl Protocol.cpp ESTOP：立即停电机+进 ALARM 态），用于撞机/夹手等
            // 需要立即终止的危险场景。estop 后需 home 复位才能继续运动。
            // 必须使用抢占式路径，否则 PATH_END 的 120s 长等待会让急停命令阻塞，形同虚设。
            emitter_.EmitPhase(task_id, "accepted");
            emitter_.EmitPhase(task_id, "running");
            ReturnValue rv = executor_.ExecuteEstopWithTaskId(task_id);
            ReturnValueJsonGuard rv_guard(rv);
            emitter_.EmitDoneOrFailed(rv, task_id);
        } else if (cap_norm == "move_abs" || cap_norm == "motor.move_abs" ||
                   cap_norm == "move" || cap_norm == "motor.move") {
            emitter_.EmitPhase(task_id, "accepted");
            emitter_.EmitPhase(task_id, "running");
            const int x = U1ProtocolClient::MotionParamsGetInt(params, "x", 0);
            const int y = U1ProtocolClient::MotionParamsGetInt(params, "y", 0);
            // 固件审查 P1：仅当 params 显式含 z 时才下压 Z 轴，避免 2D 移动落笔/撞机。
            const auto z_opt =
                U1ProtocolClient::MotionParamsGetOptionalInt(params, "z");
            const int feed =
                U1ProtocolClient::MotionParamsGetInt(params, "feed", 1000);
            const int z = z_opt.value_or(0);
            ReturnValue rv =
                executor_.ExecuteMoveWithTaskId(task_id, x, y, z, feed,
                                                 /*has_z=*/z_opt.has_value());
            ReturnValueJsonGuard rv_guard(rv);
            emitter_.EmitDoneOrFailed(rv, task_id);
        } else if (cap_norm == "move_rel" || cap_norm == "motor.move_rel") {
            emitter_.EmitPhase(task_id, "accepted");
            emitter_.EmitPhase(task_id, "running");
            const int dx = U1ProtocolClient::MotionParamsGetInt(params, "dx", 0);
            const int dy = U1ProtocolClient::MotionParamsGetInt(params, "dy", 0);
            const int dz = U1ProtocolClient::MotionParamsGetInt(params, "dz", 0);
            const int feed =
                U1ProtocolClient::MotionParamsGetInt(params, "feed", 800);
            ReturnValue rv =
                executor_.ExecuteMoveRelWithTaskId(task_id, dx, dy, dz, feed);
            ReturnValueJsonGuard rv_guard(rv);
            emitter_.EmitDoneOrFailed(rv, task_id);
        } else if (cap_norm == "run_path" || cap_norm == "motor.run_path" ||
                   cap_norm == "path") {
            int feed_rate =
                U1ProtocolClient::MotionParamsGetInt(params, "feed", 1200);
            if (feed_rate < 1 || feed_rate > 20000) {
                feed_rate = 1200;
            }
            std::string path_json;
            if (cJSON_IsObject(params)) {
                cJSON* pj =
                    cJSON_GetObjectItemCaseSensitive(params, "path_json");
                if (cJSON_IsString(pj) && pj->valuestring != nullptr) {
                    path_json = pj->valuestring;
                } else {
                    cJSON* parr =
                        cJSON_GetObjectItemCaseSensitive(params, "path");
                    if (parr != nullptr &&
                        (cJSON_IsArray(parr) || cJSON_IsObject(parr))) {
                        char* printed = cJSON_PrintUnformatted(parr);
                        if (printed != nullptr) {
                            path_json = printed;
                            cJSON_free(printed);
                        }
                    }
                }
            }
            if (path_json.empty()) {
                ESP_LOGW(TAG,
                         "motion_task run_path: missing path_json/path in params "
                         "task_id=%s",
                         task_id.c_str());
                emitter_.EmitError(task_id, "failed", "E_MISSING_PATH",
                                   "path or path_json is missing from params");
                return;
            }
            emitter_.EmitPhase(task_id, "accepted");
            emitter_.EmitPhase(task_id, "running");
            ReturnValue rv = executor_.RunPathWithTaskId(
                task_id, path_json, feed_rate, true);
            emitter_.EmitRunPathOutcome(rv, task_id);
            if (const auto* msg = std::get_if<std::string>(&rv)) {
                if (msg->find("failed") != std::string::npos ||
                    msg->find("invalid") != std::string::npos) {
                    ESP_LOGW(TAG, "motion_task run_path: %s", msg->c_str());
                } else {
                    ESP_LOGI(TAG, "motion_task run_path completed");
                }
            }
        } else {
            ESP_LOGW(TAG, "motion_task: unsupported capability '%s' task_id=%s",
                     cap_item->valuestring, task_id.c_str());
            emitter_.EmitError(
                task_id, "failed", "E_UNSUPPORTED_CAPABILITY",
                ("unsupported capability: " +
                 std::string(cap_item->valuestring))
                    .c_str());
        }
    }

public:
    DlcMotorControlP1AiBoard()
        : boot_button_(BOOT_BUTTON_GPIO),
          executor_(protocol_, emitter_) {
        InitializeI2c();
        InitializeButtons();
        InitializeCamera();
        protocol_.InitializeU1Uart();
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

    bool SupportsMotionTask() override { return true; }

    bool CheckU1Uart(std::string& detail) override {
        ReturnValue rv = executor_.ExecuteGetStatusWithTaskId("self_check_u1");
        ReturnValueJsonGuard rv_guard(rv);
        const bool ok = U1ProtocolClient::ReturnValueU1Ok(rv);
        detail = ok ? "GET_STATUS ok" : "GET_STATUS failed";
        return ok;
    }
};

DECLARE_BOARD(DlcMotorControlP1AiBoard);
