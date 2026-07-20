#include "u1_protocol_client.h"
#include "config.h"

#include <cstdlib>
#include <esp_err.h>

U1ProtocolClient::U1ProtocolClient() = default;

void U1ProtocolClient::InitializeU1Uart() {
    uart_config_t uart_config = {
        .baud_rate = U1_UART_BAUD_RATE,
        .data_bits = UART_DATA_8_BITS,
        .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1,
        .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
        .source_clk = UART_SCLK_DEFAULT,
    };

    ESP_ERROR_CHECK(uart_driver_install(U1_UART_PORT_NUM, U1_UART_BUF_SIZE * 2,
                                        U1_UART_BUF_SIZE * 2, 0, NULL, 0));
    ESP_ERROR_CHECK(uart_param_config(U1_UART_PORT_NUM, &uart_config));
    ESP_ERROR_CHECK(uart_set_pin(U1_UART_PORT_NUM, U1_UART_TXD, U1_UART_RXD,
                                 U1_UART_RTS, U1_UART_CTS));
}

uint32_t U1ProtocolClient::NextProtocolMessageId() {
    std::lock_guard<std::mutex> lock(job_mutex_);
    return ++protocol_msg_id_;
}

// 给 MCP 调试入口用的本地 task_id 生成器：u8_<prefix>_<msg_id>。
// 真实链路（M2.5 起）task_id 由 BusinessServer 透传，**不要**复用本函数。
std::string U1ProtocolClient::NextLocalTaskId(const char* prefix) {
    return std::string("u8_") + prefix + "_" + std::to_string(NextProtocolMessageId());
}

/// Read one response line from U1 UART.
/// 固件审查第二轮 FW-F4：U1 私有协议全部响应（ack/status/result/error/device_info，
/// 见 U1 Protocol.cpp grbl_sendf 格式串）均以 "\r\n" 结尾，故按行读——读到 '\n'
/// 立即返回。旧实现"2 轮空闲判结束"每次调用尾部多等 2×timeout_ms（PATH_END
/// timeout=120000ms 时白等 240s），是 30s 任务看门狗 panic 的直接根因。
/// 逐字节读取以保证 '\n' 一到即返（uart_read_bytes 会等满请求长度，块读无法早退）；
/// timeout_ms 为等待下一字节的超时，首字节等待即覆盖 U1 开始响应前的耗时。
/// 某轮读到 0 字节视为 U1 无响应/发半行即停，返回已收内容（可能为空串）。
/// Protected by uart_mutex_ (caller must hold it via SendU1Line).
std::string U1ProtocolClient::ReadU1Response(int timeout_ms) {
    std::string response;
    uint8_t byte = 0;

    while (true) {
        // 防止 U1 故障时持续发送无换行数据导致 response 无界增长耗尽堆（OOM）。
        if (response.size() >= kU1MaxResponseBytes) {
            ESP_LOGW(TAG_U1_PROTOCOL,
                     "U1 response exceeded %zu bytes, truncating (possible U1 flood)",
                     kU1MaxResponseBytes);
            break;
        }
        const int len = uart_read_bytes(U1_UART_PORT_NUM, &byte, 1,
                                        pdMS_TO_TICKS(timeout_ms));
        if (len <= 0) {
            // 本轮超时：正常响应必带 '\n' 不会走到这里；说明 U1 无响应或异常中断。
            break;
        }
        response.push_back(static_cast<char>(byte));
        if (byte == '\n') {
            break;
        }
    }

    return NormalizeResponse(response);
}

std::string U1ProtocolClient::SendU1Line(const std::string& line, int timeout_ms) {
    std::lock_guard<std::mutex> lock(uart_mutex_);
    const esp_err_t flush_result = uart_flush_input(U1_UART_PORT_NUM);
    if (flush_result != ESP_OK) {
        ESP_LOGE(TAG_U1_PROTOCOL, "Failed to flush U1 UART input: %s",
                 esp_err_to_name(flush_result));
        return "";
    }

    std::string command = line + "\n";
    const int written = uart_write_bytes(U1_UART_PORT_NUM, command.data(), command.size());
    if (written < 0 || static_cast<size_t>(written) != command.size()) {
        ESP_LOGE(TAG_U1_PROTOCOL, "Failed to write full U1 command: %d/%u",
                 written, static_cast<unsigned>(command.size()));
        return "";
    }
    ESP_LOGI(TAG_U1_PROTOCOL, "U8 -> U1: %s", line.c_str());

    auto response = ReadU1Response(timeout_ms);
    if (!response.empty()) {
        ESP_LOGI(TAG_U1_PROTOCOL, "U1 -> U8: %s", response.c_str());
    }
    return response;
}

std::string U1ProtocolClient::BuildProtocolCommandJson(uint32_t msg_id,
                                                         const std::string& task_id,
                                                         const std::string& cmd,
                                                         cJSON* extra) {
    cJSON* root = cJSON_CreateObject();
    if (root == nullptr) {
        ESP_LOGE(TAG_U1_PROTOCOL, "Failed to allocate U1 command JSON");
        return "";
    }
    // 固件审查第二轮 FW-F3：cmd.schema.json 要求 msg_id 为 string；U1 json_utils
    // 只解析带引号的值，数字 msg_id 会导致所有 capability 响应被判 msg_id_mismatch。
    char msg_id_buf[16];
    snprintf(msg_id_buf, sizeof(msg_id_buf), "%u", static_cast<unsigned>(msg_id));
    if (cJSON_AddStringToObject(root, "msg_id", msg_id_buf) == nullptr ||
        cJSON_AddStringToObject(root, "task_id", task_id.c_str()) == nullptr ||
        cJSON_AddStringToObject(root, "cmd", cmd.c_str()) == nullptr) {
        ESP_LOGE(TAG_U1_PROTOCOL, "Failed to populate U1 command JSON");
        cJSON_Delete(root);
        return "";
    }
    if (extra != nullptr) {
        cJSON* child = extra->child;
        while (child != nullptr) {
            cJSON* duplicated = cJSON_Duplicate(child, 1);
            if (duplicated == nullptr) {
                ESP_LOGE(TAG_U1_PROTOCOL, "Failed to copy U1 command field: %s",
                         child->string != nullptr ? child->string : "<unnamed>");
                cJSON_Delete(root);
                return "";
            }
            if (!cJSON_AddItemToObject(root, child->string, duplicated)) {
                ESP_LOGE(TAG_U1_PROTOCOL, "Failed to add U1 command field: %s",
                         child->string != nullptr ? child->string : "<unnamed>");
                cJSON_Delete(duplicated);
                cJSON_Delete(root);
                return "";
            }
            child = child->next;
        }
    }
    char* json = cJSON_PrintUnformatted(root);
    std::string result;
    if (json != nullptr) {
        result = json;
        cJSON_free(json);
    } else {
        ESP_LOGE(TAG_U1_PROTOCOL, "Failed to print U1 command JSON");
    }
    cJSON_Delete(root);
    return result;
}

std::string U1ProtocolClient::SendU1ProtocolCommand(uint32_t msg_id,
                                                      const std::string& task_id,
                                                      const std::string& cmd,
                                                      int timeout_ms) {
    std::string line = BuildProtocolCommandJson(msg_id, task_id, cmd);
    if (line.empty()) {
        return "";
    }
    return SendU1Line("@" + line, timeout_ms);
}

std::string U1ProtocolClient::SendU1ProtocolJson(uint32_t msg_id,
                                                  const std::string& task_id,
                                                  const std::string& cmd,
                                                  cJSON* extra,
                                                  int timeout_ms) {
    std::string line = BuildProtocolCommandJson(msg_id, task_id, cmd, extra);
    if (line.empty()) {
        return "";
    }
    return SendU1Line("@" + line, timeout_ms);
}

bool U1ProtocolClient::SendU1PreemptiveCommand(const std::string& cmd) {
    // 固件审查 P2：急停命令必须绕过 uart_mutex_，否则 PATH_END 等长等待会阻塞
    // STOP/ESTOP 达 120s，导致夹手/撞机时无法立即停车。
    // 本函数不持锁、不等待响应，直接写 UART。
    // 固件审查第二轮 FW-F1：裸文本 "STOP\n" 在 U1 私有协议（只认 '@' 开头 JSON 帧）
    // 中被当坏 G-code 排队，且 PATH_END 执行期间行命令根本进不了主循环。
    // 改发 Grbl 实时字符：U1 Serial.cpp clientCheckTask 在行缓冲之前拦截
    // （is_realtime_command 路径），即时生效：
    //   STOP  -> '!'  (0x21, Cmd::FeedHold，减速保持，可 RESUME 恢复)
    //   ESTOP -> 0x18 (Ctrl-X, Cmd::Reset，mc_reset 立即停电机，需 HOME 复位)
    std::string line;
    if (cmd == "STOP") {
        line = "!";
    } else if (cmd == "ESTOP") {
        line.push_back('\x18');
    } else {
        line = cmd + "\n";
    }
    const int written =
        uart_write_bytes(U1_UART_PORT_NUM, line.data(), line.size());
    if (written < 0 || static_cast<size_t>(written) != line.size()) {
        ESP_LOGE(TAG_U1_PROTOCOL,
                 "Failed to write preemptive U1 command: %d/%u", written,
                 static_cast<unsigned>(line.size()));
        return false;
    }
    ESP_LOGI(TAG_U1_PROTOCOL, "U8 -> U1 (preemptive): %s", cmd.c_str());
    return true;
}

// --- Static helpers ---

std::string U1ProtocolClient::NormalizeResponse(std::string text) {
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

std::string U1ProtocolClient::GetJsonStringValue(cJSON* root, const char* key) {
    cJSON* item = cJSON_GetObjectItemCaseSensitive(root, key);
    if (item != nullptr && cJSON_IsString(item) && item->valuestring != nullptr) {
        return item->valuestring;
    }
    return "";
}

bool U1ProtocolClient::GetJsonXyzObject(cJSON* root, const char* key,
                                         double& x_out, double& y_out, double& z_out) {
    cJSON* object = cJSON_GetObjectItemCaseSensitive(root, key);
    if (object == nullptr || !cJSON_IsObject(object)) {
        return false;
    }
    cJSON* x = cJSON_GetObjectItemCaseSensitive(object, "x");
    cJSON* y = cJSON_GetObjectItemCaseSensitive(object, "y");
    cJSON* z = cJSON_GetObjectItemCaseSensitive(object, "z");
    if (!cJSON_IsNumber(x) || !cJSON_IsNumber(y) || !cJSON_IsNumber(z)) {
        return false;
    }
    x_out = x->valuedouble;
    y_out = y->valuedouble;
    z_out = z->valuedouble;
    return true;
}

bool U1ProtocolClient::JsonValueIsOk(cJSON* root) {
    if (root == nullptr) {
        return false;
    }
    cJSON* ok = cJSON_GetObjectItemCaseSensitive(root, "ok");
    return cJSON_IsBool(ok) && cJSON_IsTrue(ok);
}

bool U1ProtocolClient::JsonValueHasXyz(cJSON* root, const char* key,
                                        double& x, double& y, double& z) {
    return root != nullptr && GetJsonXyzObject(root, key, x, y, z);
}

void U1ProtocolClient::FreeReturnValueIfJson(ReturnValue& rv) {
    (void)rv;
    // No-op: ReturnValue now uses unique_ptr, ownership managed via RAII.
}

bool U1ProtocolClient::ReturnValueU1Ok(const ReturnValue& rv) {
    const auto* pj = std::get_if<CJsonPtr>(&rv);
    if (pj == nullptr || pj->get() == nullptr) {
        return false;
    }
    cJSON* ok = cJSON_GetObjectItemCaseSensitive(pj->get(), "ok");
    return cJSON_IsBool(ok) && cJSON_IsTrue(ok);
}

std::string U1ProtocolClient::ToLowerAscii(std::string s) {
    for (char& c : s) {
        if (c >= 'A' && c <= 'Z') {
            c = static_cast<char>(c - 'A' + 'a');
        }
    }
    return s;
}

std::string U1ProtocolClient::NormalizeMotionCapabilityName(const char* raw) {
    if (raw == nullptr) {
        return "";
    }
    std::string s = raw;
    if (s.size() > 5 && s.compare(0, 5, "self.") == 0) {
        s = s.substr(5);
    }
    return ToLowerAscii(std::move(s));
}

int U1ProtocolClient::MotionParamsGetInt(cJSON* params, const char* key,
                                          int default_value) {
    if (params == nullptr || !cJSON_IsObject(params)) {
        return default_value;
    }
    cJSON* it = cJSON_GetObjectItemCaseSensitive(params, key);
    if (it == nullptr || !cJSON_IsNumber(it)) {
        return default_value;
    }
    return static_cast<int>(it->valuedouble);
}

std::optional<int> U1ProtocolClient::MotionParamsGetOptionalInt(cJSON* params,
                                                                const char* key) {
    if (params == nullptr || !cJSON_IsObject(params)) {
        return std::nullopt;
    }
    cJSON* it = cJSON_GetObjectItemCaseSensitive(params, key);
    if (it == nullptr || !cJSON_IsNumber(it)) {
        return std::nullopt;
    }
    return static_cast<int>(it->valuedouble);
}

bool U1ProtocolClient::JsonNumberToString(cJSON* item, char* buffer,
                                           size_t buffer_size) {
    if (item == nullptr || buffer == nullptr || buffer_size == 0 ||
        !cJSON_IsNumber(item)) {
        return false;
    }
    snprintf(buffer, buffer_size, "%.3f", item->valuedouble);
    return true;
}

// --- Response parsing ---

cJSON* U1ProtocolClient::BuildCapabilityResponseJson(
    const U1CapabilityResult& result) {
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
        cJSON_AddStringToObject(root, "error_message",
                                result.error_message.c_str());
    }
    if (!result.model.empty()) {
        cJSON_AddStringToObject(root, "model", result.model.c_str());
    }
    if (!result.hw_rev.empty()) {
        cJSON_AddStringToObject(root, "hw_rev", result.hw_rev.c_str());
    }
    if (!result.fw_rev.empty()) {
        cJSON_AddStringToObject(root, "fw_rev", result.fw_rev.c_str());
    }
    if (result.has_workspace_mm) {
        cJSON* workspace = cJSON_AddObjectToObject(root, "workspace_mm");
        if (workspace != nullptr) {
            cJSON_AddNumberToObject(workspace, "x", result.workspace_x);
            cJSON_AddNumberToObject(workspace, "y", result.workspace_y);
            cJSON_AddNumberToObject(workspace, "z", result.workspace_z);
        }
    }
    if (result.has_position) {
        cJSON* position = cJSON_AddObjectToObject(root, "position");
        if (position != nullptr) {
            cJSON_AddNumberToObject(position, "x", result.position_x);
            cJSON_AddNumberToObject(position, "y", result.position_y);
            cJSON_AddNumberToObject(position, "z", result.position_z);
        }
    }
    return root;
}

ReturnValue U1ProtocolClient::ParseCapabilityResponse(
    const std::string& raw_response, uint32_t msg_id,
    const std::string& task_id, const std::string& cmd) {
    U1CapabilityResult result;
    result.msg_id = msg_id;
    result.task_id = task_id;
    result.cmd = cmd;
    result.raw_response = raw_response;

    if (raw_response.empty()) {
        result.response_type = "timeout";
        result.error_code = "timeout";
        result.error_message = "empty response from u1";
        return ReturnValue(CJsonPtr(BuildCapabilityResponseJson(result)));
    }

    cJSON* root = cJSON_Parse(raw_response.c_str());
    if (root == nullptr || !cJSON_IsObject(root)) {
        if (root != nullptr) {
            cJSON_Delete(root);
        }
        result.response_type = "invalid";
        result.error_code = "invalid_response";
        result.error_message = "u1 response is not valid json";
        return ReturnValue(CJsonPtr(BuildCapabilityResponseJson(result)));
    }

    result.response_type = GetJsonStringValue(root, "type");
    result.state = GetJsonStringValue(root, "state");
    result.error_code = GetJsonStringValue(root, "code");
    result.error_message = GetJsonStringValue(root, "message");
    result.model = GetJsonStringValue(root, "model");
    result.hw_rev = GetJsonStringValue(root, "hw_rev");
    result.fw_rev = GetJsonStringValue(root, "fw_rev");
    if (GetJsonXyzObject(root, "workspace_mm", result.workspace_x,
                         result.workspace_y, result.workspace_z)) {
        result.has_workspace_mm = true;
    }
    if (GetJsonXyzObject(root, "position", result.position_x,
                         result.position_y, result.position_z)) {
        result.has_position = true;
    }

    bool parsed_msg_id = false;
    cJSON* msg_id_item = cJSON_GetObjectItemCaseSensitive(root, "msg_id");
    if (msg_id_item != nullptr && cJSON_IsString(msg_id_item) &&
        msg_id_item->valuestring != nullptr) {
        result.msg_id =
            static_cast<uint32_t>(strtoul(msg_id_item->valuestring, nullptr, 10));
        parsed_msg_id = true;
    }

    auto response_task_id = GetJsonStringValue(root, "task_id");
    if (!response_task_id.empty()) {
        result.task_id = response_task_id;
    }

    result.ok = result.response_type == "ack" ||
                result.response_type == "status" ||
                result.response_type == "result";
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
    return ReturnValue(CJsonPtr(BuildCapabilityResponseJson(result)));
}
