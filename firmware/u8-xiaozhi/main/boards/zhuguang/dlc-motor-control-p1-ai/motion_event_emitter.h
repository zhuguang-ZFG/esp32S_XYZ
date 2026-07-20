#ifndef MOTION_EVENT_EMITTER_H
#define MOTION_EVENT_EMITTER_H

#include <cJSON.h>
#include <mutex>
#include <string>

#include "u1_protocol_client.h"

class MotionEventEmitter {
public:
    MotionEventEmitter() = default;

    // Set motion context for the current task
    void SetMotionContext(const std::string& device_id,
                          const std::string& capability_raw,
                          const std::string& source);
    void ClearMotionContext();

    // Event emission methods
    void EmitPhase(const std::string& task_id, const char* phase);
    void EmitError(const std::string& task_id, const char* phase,
                   const char* error_code, const char* error_message);
    void EmitProgress(const std::string& task_id, int done_segments,
                      int total_segments);
    void EmitDoneOrFailed(const ReturnValue& rv, const std::string& task_id);
    void EmitDeviceInfoIfOk(const ReturnValue& rv, const std::string& task_id);
    void EmitRunPathOutcome(const ReturnValue& rv, const std::string& task_id);

private:
    cJSON* BuildBaseEvent(const std::string& task_id, const char* phase);

    // 固件审查第二轮 FW-F12：last_motion_* 被协议线程写、主循环读（std::string
    // 非原子），加互斥防数据竞争。锁只保护下面三个成员。
    std::mutex context_mutex_;
    std::string last_motion_device_id_;
    std::string last_motion_capability_raw_;
    std::string last_motion_source_;
};

#endif  // MOTION_EVENT_EMITTER_H
