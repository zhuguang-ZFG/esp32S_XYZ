# U8 ReturnValue JSON Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce manual `ReturnValue` cJSON cleanup in the U8 motion protocol path by introducing a tiny RAII guard.

**Architecture:** Keep the existing `ReturnValue` variant and `U1ProtocolClient::FreeReturnValueIfJson()` helper. Add `ReturnValueJsonGuard` as a local stack guard around `ReturnValue` variables that may hold `cJSON*`, then replace repeated manual frees in the U8 board and relative-move executor paths.

**Tech Stack:** ESP-IDF C++17, cJSON, Python unittest static CI tests.

---

### Task 1: Add Static Regression Coverage

**Files:**
- Modify: `tests/ci/test_edge_d_firmware_static.py`

- [x] **Step 1: Add guard definition coverage**

```python
    def test_u8_return_value_json_guard_is_defined(self):
        header = U8_BOARD_DIR.joinpath("u1_protocol_client.h").read_text(
            encoding="utf-8", errors="replace"
        )

        self.assertIn("class ReturnValueJsonGuard", header)
        self.assertIn("explicit ReturnValueJsonGuard(ReturnValue& value)", header)
        self.assertIn("~ReturnValueJsonGuard()", header)
        self.assertIn("U1ProtocolClient::FreeReturnValueIfJson(value_)", header)
        self.assertIn("ReturnValueJsonGuard(const ReturnValueJsonGuard&) = delete", header)
```

- [x] **Step 2: Add board usage coverage**

```python
    def test_u8_board_uses_return_value_json_guard_for_motion_results(self):
        board = U8_BOARD.read_text(encoding="utf-8", errors="replace")

        self.assertGreaterEqual(board.count("ReturnValueJsonGuard rv_guard(rv);"), 9)
        self.assertNotIn("U1ProtocolClient::FreeReturnValueIfJson(rv);", board)
```

- [x] **Step 3: Add relative move usage coverage**

```python
    def test_u8_motion_executor_uses_return_value_json_guard_for_relative_move(self):
        text = U8_BOARD_DIR.joinpath("motion_executor.cc").read_text(
            encoding="utf-8", errors="replace"
        )
        relative_move = text[
            text.index("ReturnValue MotionExecutor::ExecuteMoveRelWithTaskId"):
            text.index("// --- Capability wrappers")
        ]

        self.assertIn("ReturnValueJsonGuard status_guard(status_rv);", relative_move)
        self.assertIn("ReturnValueJsonGuard info_guard(info_rv);", relative_move)
        self.assertNotIn("FreeReturnValueIfJson(status_rv)", relative_move)
        self.assertNotIn("FreeReturnValueIfJson(info_rv)", relative_move)
```

- [x] **Step 4: Run the new tests and confirm RED**

Run: `rtk python -m unittest tests.ci.test_edge_d_firmware_static.EdgeDFirmwareStaticTests.test_u8_return_value_json_guard_is_defined tests.ci.test_edge_d_firmware_static.EdgeDFirmwareStaticTests.test_u8_board_uses_return_value_json_guard_for_motion_results tests.ci.test_edge_d_firmware_static.EdgeDFirmwareStaticTests.test_u8_motion_executor_uses_return_value_json_guard_for_relative_move -v`

Expected: tests fail before implementation.

Observed: all three tests failed before implementation.

### Task 2: Add the RAII Guard

**Files:**
- Modify: `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/u1_protocol_client.h`

- [x] **Step 1: Add `ReturnValueJsonGuard` after `U1ProtocolClient`**

```cpp
class ReturnValueJsonGuard {
public:
    explicit ReturnValueJsonGuard(ReturnValue& value) : value_(value) {}
    ~ReturnValueJsonGuard() {
        U1ProtocolClient::FreeReturnValueIfJson(value_);
    }

    ReturnValueJsonGuard(const ReturnValueJsonGuard&) = delete;
    ReturnValueJsonGuard& operator=(const ReturnValueJsonGuard&) = delete;

private:
    ReturnValue& value_;
};
```

### Task 3: Replace Local Manual Frees

**Files:**
- Modify: `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/dlc_motor_control_p1_ai_board.cc`
- Modify: `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/motion_executor.cc`

- [x] **Step 1: Guard board motion results**

```cpp
            ReturnValue rv = executor_.ExecuteHomeWithTaskId(task_id);
            ReturnValueJsonGuard rv_guard(rv);
            emitter_.EmitDoneOrFailed(rv, task_id);
```

- [x] **Step 2: Guard self-check result**

```cpp
        ReturnValue rv = executor_.ExecuteGetStatusWithTaskId("self_check_u1");
        ReturnValueJsonGuard rv_guard(rv);
        const bool ok = U1ProtocolClient::ReturnValueU1Ok(rv);
```

- [x] **Step 3: Guard relative move status and device-info results in scoped blocks**

```cpp
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
```

### Task 4: Validate

**Files:**
- No additional edits.

- [x] **Step 1: Run new target tests**

Run: `rtk python -m unittest tests.ci.test_edge_d_firmware_static.EdgeDFirmwareStaticTests.test_u8_return_value_json_guard_is_defined tests.ci.test_edge_d_firmware_static.EdgeDFirmwareStaticTests.test_u8_board_uses_return_value_json_guard_for_motion_results tests.ci.test_edge_d_firmware_static.EdgeDFirmwareStaticTests.test_u8_motion_executor_uses_return_value_json_guard_for_relative_move -v`

Expected: all three tests pass.

Observed: all three tests passed.

- [x] **Step 2: Run U8 static firmware tests**

Run: `rtk python -m unittest tests.ci.test_edge_d_firmware_static -v`

Expected: all tests pass.

Observed: 37 tests passed.

- [x] **Step 3: Run project static checks**

Run: `rtk python tools/check_gpio.py`

Expected: GPIO check passes.

Observed: GPIO check passed.

Run: `rtk python tools/validate_schemas.py`

Expected: schema validation passes.

Observed: schema validation passed with 62 checks.

- [x] **Step 4: Check ESP-IDF build availability**

Run: `rtk idf.py --version`

Expected: either ESP-IDF version is printed, or the missing `idf.py` toolchain blocker is reported.

Observed: `idf.py` is not available in the current shell, so ESP-IDF build validation remains blocked.
