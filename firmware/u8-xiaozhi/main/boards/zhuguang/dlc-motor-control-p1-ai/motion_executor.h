#ifndef MOTION_EXECUTOR_H
#define MOTION_EXECUTOR_H

#include <atomic>
#include <string>

#include "motion_event_emitter.h"
#include "u1_protocol_client.h"

class MotionExecutor {
public:
    MotionExecutor(U1ProtocolClient& protocol, MotionEventEmitter& emitter);

    // Execute motion commands with explicit task_id (from BusinessServer)
    ReturnValue ExecuteHomeWithTaskId(const std::string& task_id);
    ReturnValue ExecuteGetStatusWithTaskId(const std::string& task_id);
    ReturnValue ExecuteGetDeviceInfoWithTaskId(const std::string& task_id);
    ReturnValue ExecuteControlWithTaskId(const std::string& task_id,
                                          const char* cmd);
    // 固件审查 P2：STOP/ESTOP 走抢占式路径，绕过 UART 锁阻塞。
    ReturnValue ExecuteStopWithTaskId(const std::string& task_id);
    ReturnValue ExecuteEstopWithTaskId(const std::string& task_id);
    ReturnValue ExecuteMoveWithTaskId(const std::string& task_id, int x, int y,
                                       int z, int feed);
    // 固件审查 P1：z 缺失时不下压 Z 轴（has_z=false），防 2D 移动落笔/撞机。
    ReturnValue ExecuteMoveWithTaskId(const std::string& task_id, int x, int y,
                                       int z, int feed, bool has_z);
    ReturnValue ExecuteMoveRelWithTaskId(const std::string& task_id, int dx,
                                          int dy, int dz, int feed);

    // Execute motion commands with auto-generated local task_id (MCP debug)
    ReturnValue ExecuteHomeCapability();
    ReturnValue ExecuteGetStatusCapability();
    ReturnValue ExecuteGetDeviceInfoCapability();
    ReturnValue ExecutePauseCapability();
    ReturnValue ExecuteResumeCapability();
    ReturnValue ExecuteStopCapability();
    ReturnValue ExecuteEstopCapability();
    // 固件审查第二轮 FW-F6：MCP move_abs 同样支持 z 缺失不下发（has_z=false），
    // 对齐 motion_task 路径的 MotionParamsGetOptionalInt 语义。
    ReturnValue ExecuteMoveCapability(int x, int y, int z, int feed,
                                       bool has_z = true);
    ReturnValue ExecuteMoveRelCapability(int dx, int dy, int dz, int feed);

    // Run a path (PATH_BEGIN / PATH_SEG / PATH_END sequence)
    ReturnValue RunPathWithTaskId(const std::string& task_id,
                                   const std::string& path_json, int feed_rate,
                                   bool emit_progress = false);
    ReturnValue RunPath(const std::string& path_json, int feed_rate);

private:
    U1ProtocolClient& protocol_;
    MotionEventEmitter& emitter_;
    std::atomic<bool> motion_busy_{false};

    // Cache workspace after first successful fetch (rarely changes at runtime).
    bool has_workspace_cache_ = false;
    double cached_workspace_x_ = 0.0;
    double cached_workspace_y_ = 0.0;
    double cached_workspace_z_ = 0.0;

    bool TryAcquireMotionLock();
    void ReleaseMotionLock();

    // Internal helpers that assume the motion lock is already held.
    ReturnValue ExecuteMoveWithTaskIdUnlocked(const std::string& task_id,
                                               int x, int y, int z, int feed,
                                               bool has_z = true);

    // Fetch workspace_mm from device info and sanity-check dimensions.
    // Returns an empty string on success, or a human-readable error message.
    std::string FetchWorkspaceMm(const std::string& task_id,
                                  double& workspace_x, double& workspace_y,
                                  double& workspace_z);
};

#endif  // MOTION_EXECUTOR_H
