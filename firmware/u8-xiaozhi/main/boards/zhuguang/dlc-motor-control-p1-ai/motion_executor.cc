#include "motion_executor.h"

#include <cmath>
#include <esp_log.h>

// ponytail: motion_executor.cc 因固件审查修复累积至 420+ 行，超过 300 行目标。
// 上限：单文件略大但仍在同一编译单元内，未拆分。
// 升级触发条件：下次新增非运动执行职责（如路径规划、设备发现）时拆分为
// motion_executor_core.cc / motion_executor_path.cc / motion_executor_capability.cc。

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

namespace {

// 固件审查第二轮 FW-F1：回执诚实——ok=true 仅表示急停实时字符已成功写入 UART
// （signal sent），不代表 U1 已确认停车；真正"已停"确认留待 HIL 观察 U1 状态帧。
cJSON* BuildSignalSentResponse(const char* cmd, const char* message) {
    cJSON* root = cJSON_CreateObject();
    if (root == nullptr) {
        return nullptr;
    }
    cJSON_AddBoolToObject(root, "ok", true);
    cJSON_AddStringToObject(root, "cmd", cmd);
    cJSON_AddStringToObject(root, "state", "signal_sent");
    cJSON_AddStringToObject(root, "message", message);
    return root;
}

cJSON* BuildErrorResponse(const char* message) {
    cJSON* root = cJSON_CreateObject();
    if (root == nullptr) {
        return nullptr;
    }
    cJSON_AddBoolToObject(root, "ok", false);
    cJSON_AddStringToObject(root, "error", message);
    return root;
}

}  // namespace

ReturnValue MotionExecutor::ExecuteStopWithTaskId(
    const std::string& task_id) {
    // 固件审查 P2：STOP 用抢占式写，避免在 PATH_END 长等待期间被 UART 锁阻塞。
    // 固件审查第二轮 FW-F1：STOP 走 Grbl 实时字符 '!'(FeedHold)；回执只承诺"已发送"。
    if (protocol_.SendU1PreemptiveCommand("STOP")) {
        return ReturnValue(CJsonPtr(BuildSignalSentResponse(
            "STOP", "stop signal sent (feed hold); halt not yet confirmed")));
    }
    return ReturnValue(CJsonPtr(BuildErrorResponse("stop command failed to send")));
}

ReturnValue MotionExecutor::ExecuteEstopWithTaskId(
    const std::string& task_id) {
    // 固件审查 P2：ESTOP 用抢占式写，确保夹手/撞机场景下能立即停电机。
    // 固件审查第二轮 FW-F1：ESTOP 走 Grbl 实时字符 0x18(Reset)；回执只承诺"已发送"。
    if (protocol_.SendU1PreemptiveCommand("ESTOP")) {
        return ReturnValue(CJsonPtr(BuildSignalSentResponse(
            "ESTOP", "estop signal sent (reset); halt not yet confirmed")));
    }
    return ReturnValue(CJsonPtr(BuildErrorResponse("estop command failed to send")));
}

ReturnValue MotionExecutor::ExecuteMoveWithTaskIdUnlocked(
    const std::string& task_id, int x, int y, int z, int feed, bool has_z) {
    const uint32_t msg_id = protocol_.NextProtocolMessageId();
    cJSON* extra = cJSON_CreateObject();
    cJSON_AddNumberToObject(extra, "x", x);
    cJSON_AddNumberToObject(extra, "y", y);
    // 固件审查 P1：仅当调用方显式传入 z 时才下发，否则不写 z 字段，让 U1 保持当前 Z。
    // 这避免 2D move_abs 因默认 z=0 而意外落笔/撞机。
    if (has_z) {
        cJSON_AddNumberToObject(extra, "z", z);
    }
    cJSON_AddNumberToObject(extra, "feed", feed);
    auto response =
        protocol_.SendU1ProtocolJson(msg_id, task_id, "MOVE", extra, 200);
    cJSON_Delete(extra);
    return protocol_.ParseCapabilityResponse(response, msg_id, task_id, "MOVE");
}

std::string MotionExecutor::FetchWorkspaceMm(const std::string& task_id,
                                              double& workspace_x,
                                              double& workspace_y,
                                              double& workspace_z) {
    // 固件审查 P3：缓存 workspace 到成员变量，避免每次绝对移动都发 GET_DEVICE_INFO。
    if (has_workspace_cache_) {
        workspace_x = cached_workspace_x_;
        workspace_y = cached_workspace_y_;
        workspace_z = cached_workspace_z_;
        return {};
    }

    ReturnValue info_rv = ExecuteGetDeviceInfoWithTaskId(task_id);
    ReturnValueJsonGuard info_guard(info_rv);
    cJSON* info = nullptr;
    if (auto* p = std::get_if<CJsonPtr>(&info_rv)) {
        info = p->get();
    }
    if (!U1ProtocolClient::JsonValueIsOk(info) ||
        !U1ProtocolClient::JsonValueHasXyz(info, "workspace_mm", workspace_x,
                                           workspace_y, workspace_z)) {
        return "unable to verify workspace";
    }
    // 固件审查 P2：workspace 无物理上限护栏，后端/配置异常时可能下发极大值。
    // 这里以 1000mm 为 sanity 上限（覆盖现有绘图机/写字机机型），超限拒绝。
    constexpr double kMaxWorkspaceMm = 1000.0;
    if (workspace_x <= 0.0 || workspace_y <= 0.0 || workspace_z < 0.0 ||
        workspace_x > kMaxWorkspaceMm || workspace_y > kMaxWorkspaceMm ||
        workspace_z > kMaxWorkspaceMm) {
        return "workspace dimensions out of sanity bounds";
    }

    cached_workspace_x_ = workspace_x;
    cached_workspace_y_ = workspace_y;
    cached_workspace_z_ = workspace_z;
    has_workspace_cache_ = true;
    return {};
}

ReturnValue MotionExecutor::ExecuteMoveWithTaskId(
    const std::string& task_id, int x, int y, int z, int feed) {
    return ExecuteMoveWithTaskId(task_id, x, y, z, feed, /*has_z=*/true);
}

ReturnValue MotionExecutor::ExecuteMoveWithTaskId(
    const std::string& task_id, int x, int y, int z, int feed, bool has_z) {
    if (feed < 1 || feed > 20000) {
        return std::string("invalid move params: feed must be within [1, 20000]");
    }

    if (!TryAcquireMotionLock()) {
        return std::string("device is busy: a motion task is already running");
    }
    BusyGuard guard{motion_busy_};

    // 固件审查 P1/P2：绝对移动 workspace 边界校验（与 move_rel/run_path 一致，防超行程撞机）。
    // move_abs 是绝对坐标，合法范围是 [0, workspace_mm]。
    double workspace_x = 0.0;
    double workspace_y = 0.0;
    double workspace_z = 0.0;
    if (const std::string ws_err =
            FetchWorkspaceMm(task_id, workspace_x, workspace_y, workspace_z);
        !ws_err.empty()) {
        return std::string("absolute move rejected: ") + ws_err;
    }
    if (x < 0 || y < 0 || (has_z && z < 0) ||
        x > workspace_x || y > workspace_y || (has_z && z > workspace_z)) {
        return std::string("absolute move rejected: target outside workspace");
    }

    return ExecuteMoveWithTaskIdUnlocked(task_id, x, y, z, feed, has_z);
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
        if (auto* p = std::get_if<CJsonPtr>(&status_rv)) {
            status = p->get();
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
    if (const std::string ws_err =
            FetchWorkspaceMm(task_id, workspace_x, workspace_y, workspace_z);
        !ws_err.empty()) {
        return std::string("relative move rejected: ") + ws_err;
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
    return ExecuteStopWithTaskId(protocol_.NextLocalTaskId("stop"));
}

ReturnValue MotionExecutor::ExecuteEstopCapability() {
    return ExecuteEstopWithTaskId(protocol_.NextLocalTaskId("estop"));
}

ReturnValue MotionExecutor::ExecuteMoveCapability(int x, int y, int z,
                                                   int feed, bool has_z) {
    return ExecuteMoveWithTaskId(protocol_.NextLocalTaskId("move"), x, y, z,
                                 feed, has_z);
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
    // 固件审查 P2：feed_rate 与 move 命令保持一致，必须在 [1, 20000]。
    if (feed_rate < 1 || feed_rate > 20000) {
        return std::string("path rejected: feed_rate must be within [1, 20000]");
    }

    if (!TryAcquireMotionLock()) {
        return std::string("device is busy: a motion task is already running");
    }
    BusyGuard guard{motion_busy_};

    double workspace_x = 0.0;
    double workspace_y = 0.0;
    double workspace_z = 0.0;
    if (const std::string ws_err =
            FetchWorkspaceMm(task_id, workspace_x, workspace_y, workspace_z);
        !ws_err.empty()) {
        return std::string("path rejected: ") + ws_err;
    }

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
        // AUDIT-10-V1/F5/P2：固件侧坐标边界双重防线（不依赖后端 path_validator）。
        // NaN/Inf 经 isfinite 拦截；path 是绝对坐标，必须在 [0, workspace_mm] 内。
        {
            double xv = x_item->valuedouble;
            double yv = y_item->valuedouble;
            if (!std::isfinite(xv) || !std::isfinite(yv)) {
                cJSON_Delete(root);
                return std::string("path segment has non-finite x/y");
            }
            if (xv < 0.0 || xv > workspace_x || yv < 0.0 || yv > workspace_y) {
                cJSON_Delete(root);
                return std::string("path segment x/y outside workspace");
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
