#include "websocket_control_server.h"
#include "mcp_server.h"
#include "settings.h"  // AUDIT-12-F3：Settings for control_ws_token NVS key
#include <esp_log.h>
#include <esp_http_server.h>
#include <sys/param.h>
#include <cstring>
#include <cstdlib>
#include <map>
#include <string>

static const char* TAG = "WSControl";

WebSocketControlServer* WebSocketControlServer::instance_ = nullptr;

WebSocketControlServer::WebSocketControlServer() : server_handle_(nullptr) {
    instance_ = this;
}

WebSocketControlServer::~WebSocketControlServer() {
    Stop();
    instance_ = nullptr;
}

esp_err_t WebSocketControlServer::ws_handler(httpd_req_t *req) {
    if (instance_ == nullptr) {
        return ESP_FAIL;
    }

    if (req->method == HTTP_GET) {
        // AUDIT-12-F3 + 固件审查 P0：本地控制 WS 强制鉴权——校验 Authorization Bearer
        // token 或 ?token= 参数。token 存储在 NVS（Settings "control_ws_token"）。
        // 安全修复：未配置 token 时【拒绝】握手（不再向后兼容无鉴权），否则同局域网
        // 任意设备可连 :8080/ws 遥控 self.motor.* 物理运动，造成撞机/夹手风险。
        std::string expected_token;
        {
            Settings settings("websocket", true);
            expected_token = settings.GetString("control_ws_token", "");
        }
        if (expected_token.empty()) {
            ESP_LOGE(TAG, "control_ws_token not set; refusing local WS handshake (set NVS 'control_ws_token' to enable)");
            httpd_resp_send_err(req, 401, "Unauthorized");
            return ESP_FAIL;
        }
        // 从 Authorization 头读取
        char auth_header[128] = {0};
        size_t auth_len = httpd_req_get_hdr_value_len(req, "Authorization");
        std::string provided;
        if (auth_len > 0 && auth_len < sizeof(auth_header)) {
            httpd_req_get_hdr_value_str(req, "Authorization", auth_header, sizeof(auth_header));
            std::string auth_str(auth_header);
            const std::string prefix = "Bearer ";
            if (auth_str.compare(0, prefix.size(), prefix) == 0) {
                provided = auth_str.substr(prefix.size());
            }
        }
        // 回退：?token= 查询参数
        if (provided.empty()) {
            char query[128] = {0};
            if (httpd_req_get_url_query_str(req, query, sizeof(query)) == ESP_OK) {
                char token_val[96] = {0};
                if (httpd_query_key_value(query, "token", token_val, sizeof(token_val)) == ESP_OK) {
                    provided = token_val;
                }
            }
        }
        if (provided != expected_token) {
            ESP_LOGW(TAG, "WebSocket auth failed: invalid or missing token");
            httpd_resp_send_err(req, 401, "Unauthorized");
            return ESP_FAIL;
        }
        ESP_LOGI(TAG, "Handshake done, the new connection was opened");
        instance_->AddClient(req);
        return ESP_OK;
    }
    
    httpd_ws_frame_t ws_pkt;
    uint8_t *buf = NULL;
    memset(&ws_pkt, 0, sizeof(httpd_ws_frame_t));
    ws_pkt.type = HTTPD_WS_TYPE_TEXT;
    
    /* Set max_len = 0 to get the frame len */
    esp_err_t ret = httpd_ws_recv_frame(req, &ws_pkt, 0);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "httpd_ws_recv_frame failed to get frame len with %d", ret);
        return ret;
    }
    ESP_LOGI(TAG, "frame len is %d", ws_pkt.len);
    
    if (ws_pkt.len) {
        /* ws_pkt.len + 1 is for NULL termination as we are expecting a string */
        buf = (uint8_t*)calloc(1, ws_pkt.len + 1);
        if (buf == NULL) {
            ESP_LOGE(TAG, "Failed to calloc memory for buf");
            return ESP_ERR_NO_MEM;
        }
        ws_pkt.payload = buf;
        /* Set max_len = ws_pkt.len to get the frame payload */
        ret = httpd_ws_recv_frame(req, &ws_pkt, ws_pkt.len);
        if (ret != ESP_OK) {
            ESP_LOGE(TAG, "httpd_ws_recv_frame failed with %d", ret);
            free(buf);
            return ret;
        }
        // AUDIT-12：日志脱敏——不打印 WS 消息内容（可能含控制指令/敏感数据），只打印长度
        ESP_LOGI(TAG, "Got packet, length: %d", ws_pkt.len);
    }
    
    ESP_LOGI(TAG, "Packet type: %d", ws_pkt.type);
    
    if (ws_pkt.type == HTTPD_WS_TYPE_CLOSE) {
        ESP_LOGI(TAG, "WebSocket close frame received");
        instance_->RemoveClient(req);
        free(buf);
        return ESP_OK;
    }
    
    if (ws_pkt.type == HTTPD_WS_TYPE_TEXT) {
        if (ws_pkt.len > 0 && buf != nullptr) {
            buf[ws_pkt.len] = '\0';
            instance_->HandleMessage(req, (const char*)buf, ws_pkt.len);
        }
    } else {
        ESP_LOGW(TAG, "Unsupported frame type: %d", ws_pkt.type);
    }
    
    free(buf);
    return ESP_OK;
}

bool WebSocketControlServer::Start(int port) {
    // 安全修复：启动前预检 token，未配则直接拒绝启动（避免启动一个无人能合法连接、
    // 却又持续占资源/打日志的服务器）。
    std::string expected_token;
    {
        Settings settings("websocket", true);
        expected_token = settings.GetString("control_ws_token", "");
    }
    if (expected_token.empty()) {
        ESP_LOGE(TAG, "Refusing to start local WS: control_ws_token not set in NVS");
        return false;
    }

    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.server_port = port;
    config.max_open_sockets = 7;
    config.ctrl_port = 32769;

    httpd_uri_t ws_uri = {
        .uri = "/ws",
        .method = HTTP_GET,
        .handler = ws_handler,
        .user_ctx = nullptr,
        .is_websocket = true
    };

    if (httpd_start(&server_handle_, &config) == ESP_OK) {
        httpd_register_uri_handler(server_handle_, &ws_uri);
        ESP_LOGI(TAG, "WebSocket server started on port %d", port);
        return true;
    }

    ESP_LOGE(TAG, "Failed to start WebSocket server");
    return false;
}

void WebSocketControlServer::Stop() {
    if (server_handle_) {
        httpd_stop(server_handle_);
        server_handle_ = nullptr;
        clients_.clear();
        ESP_LOGI(TAG, "WebSocket server stopped");
    }
}

void WebSocketControlServer::HandleMessage(httpd_req_t *req, const char* data, size_t len) {
    if (data == nullptr || len == 0) {
        ESP_LOGE(TAG, "Invalid message: data is null or len is 0");
        return;
    }
    
    if (len > 4096) {
        ESP_LOGE(TAG, "Message too long: %zu bytes", len);
        return;
    }
    
    char* temp_buf = (char*)malloc(len + 1);
    if (temp_buf == nullptr) {
        ESP_LOGE(TAG, "Failed to allocate memory");
        return;
    }
    memcpy(temp_buf, data, len);
    temp_buf[len] = '\0';
    
    cJSON* root = cJSON_Parse(temp_buf);
    free(temp_buf);
    
    if (root == nullptr) {
        ESP_LOGE(TAG, "Failed to parse JSON");
        return;
    }

    // 支持两种格式：
    // 1. 完整格式：{"type":"mcp","payload":{...}}
    // 2. 简化格式：直接是MCP payload对象
    
    cJSON* payload = nullptr;
    cJSON* type = cJSON_GetObjectItem(root, "type");
    
    if (type && cJSON_IsString(type) && strcmp(type->valuestring, "mcp") == 0) {
        payload = cJSON_GetObjectItem(root, "payload");
        if (payload != nullptr) {
            cJSON_DetachItemViaPointer(root, payload);
            McpServer::GetInstance().ParseMessage(payload);
            cJSON_Delete(payload); 
        }
    } else {
        payload = cJSON_Duplicate(root, 1);
        if (payload != nullptr) {
            McpServer::GetInstance().ParseMessage(payload);
            cJSON_Delete(payload);
        }
    }
    
    if (payload == nullptr) {
        ESP_LOGE(TAG, "Invalid message format or failed to parse");
    }

    cJSON_Delete(root);
}

void WebSocketControlServer::AddClient(httpd_req_t *req) {
    int sock_fd = httpd_req_to_sockfd(req);
    if (clients_.find(sock_fd) == clients_.end()) {
        clients_[sock_fd] = req;
        ESP_LOGI(TAG, "Client connected: %d (total: %zu)", sock_fd, clients_.size());
    }
}

void WebSocketControlServer::RemoveClient(httpd_req_t *req) {
    int sock_fd = httpd_req_to_sockfd(req);
    clients_.erase(sock_fd);
    ESP_LOGI(TAG, "Client disconnected: %d (total: %zu)", sock_fd, clients_.size());
}

size_t WebSocketControlServer::GetClientCount() const {
    return clients_.size();
}
