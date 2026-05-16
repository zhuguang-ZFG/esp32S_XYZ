#include "protocol.h"

#include <cJSON.h>
#include <esp_log.h>

#define TAG "Protocol"

void Protocol::OnIncomingJson(std::function<void(const cJSON* root)> callback) {
    on_incoming_json_ = callback;
}

void Protocol::OnIncomingAudio(std::function<void(std::unique_ptr<AudioStreamPacket> packet)> callback) {
    on_incoming_audio_ = callback;
}

void Protocol::OnAudioChannelOpened(std::function<void()> callback) {
    on_audio_channel_opened_ = callback;
}

void Protocol::OnAudioChannelClosed(std::function<void()> callback) {
    on_audio_channel_closed_ = callback;
}

void Protocol::OnNetworkError(std::function<void(const std::string& message)> callback) {
    on_network_error_ = callback;
}

void Protocol::OnConnected(std::function<void()> callback) {
    on_connected_ = callback;
}

void Protocol::OnDisconnected(std::function<void()> callback) {
    on_disconnected_ = callback;
}

void Protocol::SetError(const std::string& message) {
    error_occurred_ = true;
    if (on_network_error_ != nullptr) {
        on_network_error_(message);
    }
}

void Protocol::SendAbortSpeaking(AbortReason reason) {
    std::string message = "{\"session_id\":\"" + session_id_ + "\",\"type\":\"abort\"";
    if (reason == kAbortReasonWakeWordDetected) {
        message += ",\"reason\":\"wake_word_detected\"";
    }
    message += "}";
    SendText(message);
}

void Protocol::SendWakeWordDetected(const std::string& wake_word) {
    std::string json = "{\"session_id\":\"" + session_id_ + 
                      "\",\"type\":\"listen\",\"state\":\"detect\",\"text\":\"" + wake_word + "\"}";
    SendText(json);
}

void Protocol::SendStartListening(ListeningMode mode) {
    std::string message = "{\"session_id\":\"" + session_id_ + "\"";
    message += ",\"type\":\"listen\",\"state\":\"start\"";
    if (mode == kListeningModeRealtime) {
        message += ",\"mode\":\"realtime\"";
    } else if (mode == kListeningModeAutoStop) {
        message += ",\"mode\":\"auto\"";
    } else {
        message += ",\"mode\":\"manual\"";
    }
    message += "}";
    SendText(message);
}

void Protocol::SendStopListening() {
    std::string message = "{\"session_id\":\"" + session_id_ + "\",\"type\":\"listen\",\"state\":\"stop\"}";
    SendText(message);
}

void Protocol::SendMcpMessage(const std::string& payload) {
    std::string message = "{\"session_id\":\"" + session_id_ + "\",\"type\":\"mcp\",\"payload\":" + payload + "}";
    SendText(message);
}

void Protocol::SendMotionEvent(cJSON* fields) {
    if (fields == nullptr) {
        return;
    }
    cJSON* root = cJSON_Duplicate(fields, 1);
    if (root == nullptr) {
        return;
    }
    cJSON_DeleteItemFromObjectCaseSensitive(root, "session_id");
    cJSON_AddStringToObject(root, "session_id", session_id_.c_str());
    cJSON_DeleteItemFromObjectCaseSensitive(root, "type");
    cJSON_AddStringToObject(root, "type", "motion_event");
    char* serialized = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    if (serialized == nullptr) {
        ESP_LOGE(TAG, "SendMotionEvent: cJSON_Print failed");
        return;
    }
    std::string message(serialized);
    cJSON_free(serialized);
    if (!SendText(message)) {
        ESP_LOGW(TAG, "SendMotionEvent failed (channel closed?)");
    }
}

void Protocol::SendDeviceInfo(cJSON* fields) {
    if (fields == nullptr) {
        return;
    }
    cJSON* root = cJSON_Duplicate(fields, 1);
    if (root == nullptr) {
        return;
    }
    cJSON_DeleteItemFromObjectCaseSensitive(root, "session_id");
    cJSON_AddStringToObject(root, "session_id", session_id_.c_str());
    cJSON_DeleteItemFromObjectCaseSensitive(root, "type");
    cJSON_AddStringToObject(root, "type", "device_info");
    char* serialized = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    if (serialized == nullptr) {
        ESP_LOGE(TAG, "SendDeviceInfo: cJSON_Print failed");
        return;
    }
    std::string message(serialized);
    cJSON_free(serialized);
    if (!SendText(message)) {
        ESP_LOGW(TAG, "SendDeviceInfo failed (channel closed?)");
    }
}

void Protocol::SendSelfCheck(cJSON* fields) {
    if (fields == nullptr) {
        return;
    }
    cJSON* root = cJSON_Duplicate(fields, 1);
    if (root == nullptr) {
        return;
    }
    cJSON_DeleteItemFromObjectCaseSensitive(root, "session_id");
    cJSON_AddStringToObject(root, "session_id", session_id_.c_str());
    cJSON_DeleteItemFromObjectCaseSensitive(root, "type");
    cJSON_AddStringToObject(root, "type", "self_check");
    char* serialized = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    if (serialized == nullptr) {
        ESP_LOGE(TAG, "SendSelfCheck: cJSON_Print failed");
        return;
    }
    std::string message(serialized);
    cJSON_free(serialized);
    if (!SendText(message)) {
        ESP_LOGW(TAG, "SendSelfCheck failed (channel closed?)");
    }
}

bool Protocol::IsTimeout() const {
    const int kTimeoutSeconds = 120;
    auto now = std::chrono::steady_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::seconds>(now - last_incoming_time_);
    bool timeout = duration.count() > kTimeoutSeconds;
    if (timeout) {
        ESP_LOGE(TAG, "Channel timeout %ld seconds", (long)duration.count());
    }
    return timeout;
}
