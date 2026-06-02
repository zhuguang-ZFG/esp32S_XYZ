#ifndef MOTION_EXECUTOR_H
#define MOTION_EXECUTOR_H

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
    ReturnValue ExecuteMoveWithTaskId(const std::string& task_id, int x, int y,
                                       int z, int feed);
    ReturnValue ExecuteMoveRelWithTaskId(const std::string& task_id, int dx,
                                          int dy, int dz, int feed);

    // Execute motion commands with auto-generated local task_id (MCP debug)
    ReturnValue ExecuteHomeCapability();
    ReturnValue ExecuteGetStatusCapability();
    ReturnValue ExecuteGetDeviceInfoCapability();
    ReturnValue ExecutePauseCapability();
    ReturnValue ExecuteResumeCapability();
    ReturnValue ExecuteStopCapability();
    ReturnValue ExecuteMoveCapability(int x, int y, int z, int feed);
    ReturnValue ExecuteMoveRelCapability(int dx, int dy, int dz, int feed);

    // Run a path (PATH_BEGIN / PATH_SEG / PATH_END sequence)
    ReturnValue RunPathWithTaskId(const std::string& task_id,
                                   const std::string& path_json, int feed_rate,
                                   bool emit_progress = false);
    ReturnValue RunPath(const std::string& path_json, int feed_rate);

private:
    U1ProtocolClient& protocol_;
    MotionEventEmitter& emitter_;
};

#endif  // MOTION_EXECUTOR_H
