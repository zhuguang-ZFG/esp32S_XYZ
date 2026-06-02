#ifndef U1_PROTOCOL_CLIENT_H
#define U1_PROTOCOL_CLIENT_H

#include <cJSON.h>
#include <driver/uart.h>
#include <esp_log.h>

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <variant>

#include "mcp_server.h"

// Forward declaration from config.h
#ifndef U1_UART_PORT_NUM
#include "config.h"
#endif

#define TAG_U1_PROTOCOL "U1ProtocolClient"

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
    std::string model;
    std::string hw_rev;
    std::string fw_rev;
    bool has_workspace_mm = false;
    double workspace_x = 0.0;
    double workspace_y = 0.0;
    double workspace_z = 0.0;
    bool has_position = false;
    double position_x = 0.0;
    double position_y = 0.0;
    double position_z = 0.0;
};

class U1ProtocolClient {
public:
    U1ProtocolClient();

    void InitializeU1Uart();

    // Protocol message ID management
    uint32_t NextProtocolMessageId();
    std::string NextLocalTaskId(const char* prefix);

    // Send raw line to U1 and get response
    std::string SendU1Line(const std::string& line, int timeout_ms = 120);

    // Protocol command builders
    std::string BuildProtocolCommandJson(uint32_t msg_id,
                                         const std::string& task_id,
                                         const std::string& cmd,
                                         cJSON* extra = nullptr);

    // High-level protocol send methods
    std::string SendU1ProtocolCommand(uint32_t msg_id, const std::string& task_id,
                                      const std::string& cmd, int timeout_ms = 120);
    std::string SendU1ProtocolJson(uint32_t msg_id, const std::string& task_id,
                                   const std::string& cmd, cJSON* extra,
                                   int timeout_ms = 120);

    // Response parsing
    ReturnValue ParseCapabilityResponse(const std::string& raw_response,
                                        uint32_t msg_id,
                                        const std::string& task_id,
                                        const std::string& cmd);

    // Static JSON / ReturnValue helpers
    static std::string NormalizeResponse(std::string text);
    static std::string GetJsonStringValue(cJSON* root, const char* key);
    static bool GetJsonXyzObject(cJSON* root, const char* key,
                                 double& x_out, double& y_out, double& z_out);
    static bool JsonValueIsOk(cJSON* root);
    static bool JsonValueHasXyz(cJSON* root, const char* key,
                                double& x, double& y, double& z);
    static void FreeReturnValueIfJson(ReturnValue& rv);
    static bool ReturnValueU1Ok(const ReturnValue& rv);
    static std::string ToLowerAscii(std::string s);
    static std::string NormalizeMotionCapabilityName(const char* raw);
    static int MotionParamsGetInt(cJSON* params, const char* key, int default_value);
    static bool JsonNumberToString(cJSON* item, char* buffer, size_t buffer_size);

private:
    std::string ReadU1Response(int timeout_ms);
    static cJSON* BuildCapabilityResponseJson(const U1CapabilityResult& result);

    std::mutex uart_mutex_;
    std::mutex job_mutex_;
    uint32_t protocol_msg_id_ = 0;
};

#endif  // U1_PROTOCOL_CLIENT_H
