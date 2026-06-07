# U8 UART JSON Failure Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the U8 to U1 command send path so cJSON allocation failures and UART write failures do not crash or silently continue as successful sends.

**Architecture:** Keep the existing `U1ProtocolClient` API and Edge-D command shape. Add guard checks inside `BuildProtocolCommandJson()` and `SendU1Line()`; failed local preparation or UART I/O returns an empty response so the existing `ParseCapabilityResponse()` timeout path handles the failure.

**Tech Stack:** ESP-IDF C++17, cJSON, UART driver APIs, Python unittest static CI tests.

---

### Task 1: Add Static Regression Coverage

**Files:**
- Modify: `tests/ci/test_edge_d_firmware_static.py`

- [x] **Step 1: Add builder allocation failure coverage**

```python
    def test_u8_protocol_command_builder_handles_cjson_allocation_failures(self):
        text = U8_BOARD_DIR.joinpath("u1_protocol_client.cc").read_text(
            encoding="utf-8", errors="replace"
        )
        builder = text[
            text.index("std::string U1ProtocolClient::BuildProtocolCommandJson"):
            text.index("std::string U1ProtocolClient::SendU1ProtocolCommand")
        ]

        self.assertIn("if (root == nullptr)", builder)
        self.assertIn("std::string result;", builder)
        self.assertIn("if (json != nullptr)", builder)
        self.assertNotIn("std::string result(json);", builder)
```

- [x] **Step 2: Add UART local I/O failure coverage**

```python
    def test_u8_protocol_send_checks_uart_flush_and_write_results(self):
        text = U8_BOARD_DIR.joinpath("u1_protocol_client.cc").read_text(
            encoding="utf-8", errors="replace"
        )
        send_line = text[
            text.index("std::string U1ProtocolClient::SendU1Line"):
            text.index("std::string U1ProtocolClient::BuildProtocolCommandJson")
        ]

        self.assertIn("const esp_err_t flush_result = uart_flush_input", send_line)
        self.assertIn("flush_result != ESP_OK", send_line)
        self.assertIn("const int written = uart_write_bytes", send_line)
        self.assertIn("static_cast<size_t>(written) != command.size()", send_line)
```

- [x] **Step 3: Run the new tests and confirm RED**

Run: `rtk python -m unittest tests.ci.test_edge_d_firmware_static.EdgeDFirmwareStaticTests.test_u8_protocol_command_builder_handles_cjson_allocation_failures tests.ci.test_edge_d_firmware_static.EdgeDFirmwareStaticTests.test_u8_protocol_send_checks_uart_flush_and_write_results -v`

Expected: both tests fail before implementation.

Observed: both planned tests failed before implementation; an additional empty-builder-send test was added and also failed before implementation.

### Task 2: Harden the Command Builder

**Files:**
- Modify: `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/u1_protocol_client.cc:70`

- [x] **Step 1: Guard root allocation and JSON print**

```cpp
    cJSON* root = cJSON_CreateObject();
    if (root == nullptr) {
        ESP_LOGE(TAG_U1_PROTOCOL, "Failed to allocate U1 command JSON");
        return "";
    }

    ...

    char* json = cJSON_PrintUnformatted(root);
    std::string result;
    if (json != nullptr) {
        result = json;
        cJSON_free(json);
    } else {
        ESP_LOGE(TAG_U1_PROTOCOL, "Failed to print U1 command JSON");
    }
    cJSON_Delete(root);
    return result;
```

- [x] **Step 2: Keep copied extra fields root-owned**

Expected code remains:

```cpp
            cJSON* duplicated = cJSON_Duplicate(child, 1);
            if (duplicated != nullptr) {
                cJSON_AddItemToObject(root, child->string, duplicated);
            }
```

### Task 3: Harden UART Send Failure Handling

**Files:**
- Modify: `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/u1_protocol_client.cc:54`

- [x] **Step 1: Check flush and write return values**

```cpp
    const esp_err_t flush_result = uart_flush_input(U1_UART_PORT_NUM);
    if (flush_result != ESP_OK) {
        ESP_LOGE(TAG_U1_PROTOCOL, "Failed to flush U1 UART input: %s",
                 esp_err_to_name(flush_result));
        return "";
    }

    const int written =
        uart_write_bytes(U1_UART_PORT_NUM, command.data(), command.size());
    if (written < 0 || static_cast<size_t>(written) != command.size()) {
        ESP_LOGE(TAG_U1_PROTOCOL, "Failed to write full U1 command: %d/%u",
                 written, static_cast<unsigned>(command.size()));
        return "";
    }
```

- [x] **Step 2: Add `esp_err.h` include if required**

```cpp
#include <esp_err.h>
```

### Task 4: Validate

**Files:**
- No additional edits.

- [x] **Step 1: Run new target tests**

Run: `rtk python -m unittest tests.ci.test_edge_d_firmware_static.EdgeDFirmwareStaticTests.test_u8_protocol_command_builder_handles_cjson_allocation_failures tests.ci.test_edge_d_firmware_static.EdgeDFirmwareStaticTests.test_u8_protocol_send_checks_uart_flush_and_write_results -v`

Expected: both tests pass.

Observed: the three new target tests passed.

- [x] **Step 2: Run U8 static firmware tests**

Run: `rtk python -m unittest tests.ci.test_edge_d_firmware_static -v`

Expected: all tests pass.

Observed: 34 tests passed.

- [x] **Step 3: Run project static checks that do not require missing dependencies**

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
