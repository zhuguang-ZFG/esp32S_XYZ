#include "motion_executor.h"

#include <cmath>
#include <esp_log.h>

#define TAG_EXECUTOR "MotionExecutor"

namespace {

struct BusyGuard {
    std::atomic<bool>& flag;
    ~BusyGuard() { flag.store(false, std::memory_order_release); }
};

}  // namespace

MotionExecutor::MotionExecutor(U1ProtocolClient& protocol,
                               MotionEventEmitter& emitter)
    : protocol_(protocol), emitter_(emitter) {}

bool MotionExecutor::TryAcquireMotionLock() {
    bool expected = false;
    return motion_busy_.compare_exchange_strong(expected, true,
                                                std::memory_order_acq_rel);
}

void MotionExecutor::ReleaseMotionLock() {
    motion_busy_.store(false, std::memory_order_release);
}

ReturnValue MotionExecutor::ExecuteHomeWithTaskId(
    const std::string& task_id) {
    if (!TryAcquireMotionLock()) {
        return std::string("device is busy: a motion task is already running");
    }
    BusyGuard guard{motion_busy_};

    const uint32_t msg_id = protocol_.NextProtocolMessageId();
    return protocol_.ParseCapabilityResponse(
        protocol_.SendU1ProtocolCommand(msg_id, task_id, "HOME", 250), msg_id,
        task_id, "HOME");
}

ReturnValue MotionExecutor::ExecuteGetStatusWithTaskId(
    const std::string& task_id) {
    const uint32_t msg_id = protocol_.NextProtocolMessageId();
    return protocol_.ParseCapabilityResponse(
        protocol_.SendU1ProtocolCommand(msg_id, task_id, "GET_STATUS", 120),
        msg_id, task_id, "GET_STATUS");
}

ReturnValue MotionExecutor::ExecuteGetDeviceInfoWithTaskId(
    const std::string& task_id) {
    const uint32_t msg_id = protocol_.NextProtocolMessageId();
    return protocol_.ParseCapabilityResponse(
        protocol_.SendU1ProtocolCommand(msg_id, task_id, "GET_DEVICE_INFO",
                                        120),
        msg_id, task_id, "GET_DEVICE_INFO");
}

ReturnValue MotionExecutor::ExecuteControlWithTaskId(
    const std::string& task_id, const char* cmd) {
    const uint32_t msg_id = protocol_.NextProtocolMessageId();
    return protocol_.ParseCapabilityResponse(
        protocol_.SendU1ProtocolCommand(msg_id, task_id, cmd, 120), msg_id,
        task_id, cmd);
}

ReturnValue MotionExecutor::ExecuteMoveWithTaskIdUnlocked(
    const std::string& task_id, int x, int y, int z, int feed) {
    const uint32_t msg_id = protocol_.NextProtocolMessageId();
    cJSON* extra = cJSON_CreateObject();
    cJSON_AddNumberToObject(extra, "x", x);
    cJSON_AddNumberToObject(extra, "y", y);
    cJSON_AddNumberToObject(extra, "z", z);
    cJSON_AddNumberToObject(extra, "feed", feed);
    auto response =
        protocol_.SendU1ProtocolJson(msg_id, task_id, "MOVE", extra, 200);
    cJSON_Delete(extra);
    return protocol_.ParseCapabilityResponse(response, msg_id, task_id, "MOVE");
}

ReturnValue MotionExecutor::ExecuteMoveWithTaskId(
    const std::string& task_id, int x, int y, int z, int feed) {
    if (feed < 1 || feed > 20000) {
        return std::string("invalid move params: feed must be within [1, 20000]");
    }

    if (!TryAcquireMotionLock()) {
        return std::string("device is busy: a motion task is already running");
    }
    BusyGuard guard{motion_busy_};

    return ExecuteMoveWithTaskIdUnlocked(task_id, x, y, z, feed);
}

ReturnValue MotionExecutor::ExecuteMoveRelWithTaskId(
    const std::string& task_id, int dx, int dy, int dz, int feed) {
    if (feed < 1 || feed > 20000) {
        return std::string(
            "relative move rejected: feed must be within [1, 20000]");
    }
    if (dx < -1 || dx > 1 || dy < -1 || dy > 1 || dz < -1 || dz > 1) {
        return std::string(
            "relative move rejected: each axis step must be within [-1, 1] mm");
    }
    if (dx == 0 && dy == 0 && dz == 0) {
        return std::string(
            "relative move rejected: at least one axis step is required");
    }

    if (!TryAcquireMotionLock()) {
        return std::string("device is busy: a motion task is already running");
    }
    BusyGuard guard{motion_busy_};

    double current_x = 0.0;
    double current_y = 0.0;
    double current_z = 0.0;
    {
        ReturnValue status_rv = ExecuteGetStatusWithTaskId(task_id);
        ReturnValueJsonGuard status_guard(status_rv);
        cJSON* status = nullptr;
        if (auto* p = std::get_if<cJSON*>(&status_rv)) {
            status = *p;
        }

        if (!U1ProtocolClient::JsonValueIsOk(status) ||
            !U1ProtocolClient::JsonValueHasXyz(status, "position", current_x,
                                               current_y, current_z)) {
            return std::string(
                "relative move rejected: unable to read current position");
        }
    }

    double workspace_x = 0.0;
    double workspace_y = 0.0;
    double workspace_z = 0.0;
    {
        ReturnValue info_rv = ExecuteGetDeviceInfoWithTaskId(task_id);
        ReturnValueJsonGuard info_guard(info_rv);
        cJSON* info = nullptr;
        if (auto* p = std::get_if<cJSON*>(&info_rv)) {
            info = *p;
        }

        if (!U1ProtocolClient::JsonValueIsOk(info) ||
            !U1ProtocolClient::JsonValueHasXyz(info, "workspace_mm", workspace_x,
                                               workspace_y, workspace_z)) {
            return std::string(
                "relative move rejected: unable to verify workspace");
        }
    }

    const double target_x = current_x + dx;
    const double target_y = current_y + dy;
    const double target_z = current_z + dz;
    if (target_x < 0.0 || target_y < 0.0 || target_z < 0.0 ||
        target_x > workspace_x || target_y > workspace_y ||
        target_z > workspace_z) {
        return std::string("relative move rejected: target outside workspace");
    }

    return ExecuteMoveWithTaskIdUnlocked(
        task_id, static_cast<int>(target_x), static_cast<int>(target_y),
        static_cast<int>(target_z), feed);
}

// --- Capability wrappers (auto-generate local task_id) ---

ReturnValue MotionExecutor::ExecuteHomeCapability() {
    return ExecuteHomeWithTaskId(protocol_.NextLocalTaskId("home"));
}

ReturnValue MotionExecutor::ExecuteGetStatusCapability() {
    return ExecuteGetStatusWithTaskId(protocol_.NextLocalTaskId("status"));
}

ReturnValue MotionExecutor::ExecuteGetDeviceInfoCapability() {
    return ExecuteGetDeviceInfoWithTaskId(
        protocol_.NextLocalTaskId("device_info"));
}

ReturnValue MotionExecutor::ExecutePauseCapability() {
    return ExecuteControlWithTaskId(protocol_.NextLocalTaskId("pause"),
                                    "PAUSE");
}

ReturnValue MotionExecutor::ExecuteResumeCapability() {
    return ExecuteControlWithTaskId(protocol_.NextLocalTaskId("resume"),
                                    "RESUME");
}

ReturnValue MotionExecutor::ExecuteStopCapability() {
    return ExecuteControlWithTaskId(protocol_.NextLocalTaskId("stop"), "STOP");
}

ReturnValue MotionExecutor::ExecuteMoveCapability(int x, int y, int z,
                                                   int feed) {
    return ExecuteMoveWithTaskId(protocol_.NextLocalTaskId("move"), x, y, z,
                                 feed);
}

ReturnValue MotionExecutor::ExecuteMoveRelCapability(int dx, int dy, int dz,
                                                      int feed) {
    return ExecuteMoveRelWithTaskId(protocol_.NextLocalTaskId("move_rel"), dx,
                                    dy, dz, feed);
}

// --- Path execution ---

ReturnValue MotionExecutor::RunPathWithTaskId(const std::string& task_id,
                                               const std::string& path_json,
                                               int feed_rate,
                                               bool emit_progress) {
    if (!TryAcquireMotionLock()) {
        return std::string("device is busy: a motion task is already running");
    }
    BusyGuard guard{motion_busy_};

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

    // PATH_BEGIN/PATH_SEG 都只把段加进 U1 内部 G-code 缓冲区，未真正执行；
    // 但 U1 主循环偶尔会被 feedHold/cycleStart 切换或 UART 抢占，150 ms 余量太紧，
    // 给到 800 ms 仍属"非阻塞 ack"范畴。真实执行的等待由 PATH_END 长超时承担。
    cJSON* path_begin_extra = cJSON_CreateObject();
    cJSON_AddNumberToObject(path_begin_extra, "total_segments", total_segments);
    cJSON_AddNumberToObject(path_begin_extra, "feed", feed_rate);
    auto response = protocol_.SendU1ProtocolJson(
        protocol_.NextProtocolMessageId(), task_id, "PATH_BEGIN",
        path_begin_extra, 800);
    cJSON_Delete(path_begin_extra);
    if (response.empty() ||
        response.find("\"type\":\"error\"") != std::string::npos) {
        cJSON_Delete(root);
        return std::string("path begin failed: ") +
               (response.empty() ? "timeout" : response);
    }

    cJSON* segment = nullptr;
    int segment_index = 0;
    cJSON_ArrayForEach(segment, root) {
        if (!cJSON_IsObject(segment)) {
            cJSON_Delete(root);
            return std::string("invalid path segment");
        }

        cJSON* cmd_item =
            cJSON_GetObjectItemCaseSensitive(segment, "cmd");
        cJSON* x_item = cJSON_GetObjectItemCaseSensitive(segment, "x");
        cJSON* y_item = cJSON_GetObjectItemCaseSensitive(segment, "y");
        if (!cJSON_IsNumber(x_item) || !cJSON_IsNumber(y_item)) {
            cJSON_Delete(root);
            return std::string("path segment missing x/y");
        }
        // AUDIT-10-V1/F5：固件侧坐标边界双重防线（不依赖后端 path_validator）。
        // NaN/Inf 经 isfinite 拦截；超 ±500mm 物理边界拒绝，防撞机。
        {
            double xv = x_item->valuedouble;
            double yv = y_item->valuedouble;
            if (!std::isfinite(xv) || !std::isfinite(yv)) {
                cJSON_Delete(root);
                return std::string("path segment has non-finite x/y");
            }
            constexpr double kMaxCoord = 500.0;
            if (xv < -kMaxCoord || xv > kMaxCoord || yv < -kMaxCoord || yv > kMaxCoord) {
                cJSON_Delete(root);
                return std::string("path segment x/y out of physical bounds");
            }
        }

        std::string cmd;
        if (cJSON_IsString(cmd_item) && cmd_item->valuestring != nullptr) {
            cmd = cmd_item->valuestring;
        }
        // Cloud-generated paths may omit cmd; default to M for the first
        // segment and L for the rest so vectorized raster images still run.
        if (cmd.empty()) {
            cmd = (segment_index == 0) ? "M" : "L";
        }
        if (cmd != "M" && cmd != "L") {
            cJSON_Delete(root);
            return std::string("unsupported segment cmd");
        }

        char xbuf[32];
        char ybuf[32];
        U1ProtocolClient::JsonNumberToString(x_item, xbuf, sizeof(xbuf));
        U1ProtocolClient::JsonNumberToString(y_item, ybuf, sizeof(ybuf));

        cJSON* seg_extra = cJSON_CreateObject();
        cJSON_AddNumberToObject(seg_extra, "segment_index", segment_index);
        cJSON_AddStringToObject(seg_extra, "segment_cmd", cmd.c_str());
        cJSON_AddNumberToObject(seg_extra, "x", x_item->valuedouble);
        cJSON_AddNumberToObject(seg_extra, "y", y_item->valuedouble);
        cJSON_AddNumberToObject(seg_extra, "feed", feed_rate);
        response = protocol_.SendU1ProtocolJson(
            protocol_.NextProtocolMessageId(), task_id, "PATH_SEG", seg_extra,
            800);
        cJSON_Delete(seg_extra);
        if (response.empty() ||
            response.find("\"type\":\"error\"") != std::string::npos) {
            cJSON_Delete(root);
            return std::string("path segment failed: ") +
                   (response.empty() ? "timeout" : response);
        }
        ++segment_index;
        if (emit_progress) {
            emitter_.EmitProgress(task_id, segment_index, total_segments);
        }
    }

    cJSON_Delete(root);

    response = protocol_.SendU1ProtocolJson(
        protocol_.NextProtocolMessageId(), task_id, "PATH_END", nullptr,
        120000);
    if (response.empty()) {
        return std::string("path end failed: timeout");
    }
    if (response.find("\"type\":\"error\"") != std::string::npos) {
        return std::string("path end failed: ") + response;
    }
    return response;
}

ReturnValue MotionExecutor::RunPath(const std::string& path_json,
                                     int feed_rate) {
    return RunPathWithTaskId(protocol_.NextLocalTaskId("path"), path_json,
                             feed_rate);
}
