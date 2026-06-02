#include "motion_event_emitter.h"

#include <esp_log.h>

#include "application.h"

#define TAG_EMITTER "MotionEventEmitter"

void MotionEventEmitter::SetMotionContext(const std::string& device_id,
                                           const std::string& capability_raw,
                                           const std::string& source) {
    last_motion_device_id_ = device_id;
    last_motion_capability_raw_ = capability_raw;
    last_motion_source_ = source;
}

void MotionEventEmitter::ClearMotionContext() {
    last_motion_device_id_.clear();
    last_motion_capability_raw_.clear();
    last_motion_source_.clear();
}

cJSON* MotionEventEmitter::BuildBaseEvent(const std::string& task_id,
                                           const char* phase) {
    cJSON* o = cJSON_CreateObject();
    if (o == nullptr) {
        return nullptr;
    }
    cJSON_AddStringToObject(o, "task_id", task_id.c_str());
    cJSON_AddStringToObject(o, "phase", phase);
    if (!last_motion_device_id_.empty()) {
        cJSON_AddStringToObject(o, "device_id",
                                last_motion_device_id_.c_str());
    }
    if (!last_motion_capability_raw_.empty()) {
        cJSON_AddStringToObject(o, "capability",
                                last_motion_capability_raw_.c_str());
    }
    if (!last_motion_source_.empty()) {
        cJSON_AddStringToObject(o, "source", last_motion_source_.c_str());
    }
    return o;
}

void MotionEventEmitter::EmitPhase(const std::string& task_id,
                                    const char* phase) {
    cJSON* o = BuildBaseEvent(task_id, phase);
    if (o == nullptr) {
        return;
    }
    Application::GetInstance().SendMotionEvent(o);
    cJSON_Delete(o);
}

void MotionEventEmitter::EmitError(const std::string& task_id,
                                    const char* phase,
                                    const char* error_code,
                                    const char* error_message) {
    cJSON* o = BuildBaseEvent(task_id, phase);
    if (o == nullptr) {
        return;
    }
    cJSON_AddStringToObject(o, "error_code", error_code);
    cJSON_AddStringToObject(o, "error_message", error_message);
    Application::GetInstance().SendMotionEvent(o);
    cJSON_Delete(o);
}

void MotionEventEmitter::EmitProgress(const std::string& task_id,
                                       int done_segments,
                                       int total_segments) {
    if (total_segments <= 0) {
        return;
    }
    cJSON* o = BuildBaseEvent(task_id, "progress");
    if (o == nullptr) {
        return;
    }
    cJSON* progress = cJSON_AddObjectToObject(o, "progress");
    if (progress != nullptr) {
        cJSON_AddNumberToObject(progress, "done_segments", done_segments);
        cJSON_AddNumberToObject(progress, "total_segments", total_segments);
        cJSON_AddNumberToObject(progress, "percent",
                                (done_segments * 100) / total_segments);
    }
    Application::GetInstance().SendMotionEvent(o);
    cJSON_Delete(o);
}

void MotionEventEmitter::EmitDoneOrFailed(const ReturnValue& rv,
                                           const std::string& task_id) {
    EmitPhase(task_id,
              U1ProtocolClient::ReturnValueU1Ok(rv) ? "done" : "failed");
}

void MotionEventEmitter::EmitDeviceInfoIfOk(const ReturnValue& rv,
                                             const std::string& task_id) {
    const auto* pj = std::get_if<cJSON*>(&rv);
    if (pj == nullptr || *pj == nullptr ||
        !U1ProtocolClient::ReturnValueU1Ok(rv)) {
        return;
    }
    if (last_motion_device_id_.empty()) {
        return;
    }
    cJSON* workspace =
        cJSON_GetObjectItemCaseSensitive(*pj, "workspace_mm");
    if (workspace == nullptr || !cJSON_IsObject(workspace)) {
        return;
    }

    cJSON* o = cJSON_CreateObject();
    if (o == nullptr) {
        return;
    }
    cJSON_AddStringToObject(o, "task_id", task_id.c_str());
    cJSON_AddStringToObject(o, "device_id",
                            last_motion_device_id_.c_str());
    if (!last_motion_capability_raw_.empty()) {
        cJSON_AddStringToObject(o, "capability",
                                last_motion_capability_raw_.c_str());
    }

    const char* keys[] = {"model", "hw_rev", "fw_rev"};
    for (const char* key : keys) {
        cJSON* value = cJSON_GetObjectItemCaseSensitive(*pj, key);
        if (!cJSON_IsString(value) || value->valuestring == nullptr ||
            value->valuestring[0] == '\0') {
            cJSON_Delete(o);
            return;
        }
        cJSON_AddStringToObject(o, key, value->valuestring);
    }

    cJSON* workspace_copy = cJSON_Duplicate(workspace, 1);
    if (workspace_copy == nullptr) {
        cJSON_Delete(o);
        return;
    }
    cJSON_AddItemToObject(o, "workspace_mm", workspace_copy);

    Application::GetInstance().SendDeviceInfo(o);
    cJSON_Delete(o);
}

void MotionEventEmitter::EmitRunPathOutcome(const ReturnValue& rv,
                                             const std::string& task_id) {
    if (const auto* msg = std::get_if<std::string>(&rv)) {
        const bool ok = msg->find("failed") == std::string::npos &&
                        msg->find("invalid") == std::string::npos;
        EmitPhase(task_id, ok ? "done" : "failed");
    } else {
        EmitPhase(task_id, "failed");
    }
}
