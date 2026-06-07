# U8 JSON Command Memory Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove a heap leak risk from the U8 to U1 JSON command builder without changing the Edge-D command contract.

**Architecture:** Keep the existing U8 board modules and cJSON-based protocol builder. Change only ownership semantics for copied cJSON fields so the command root owns every duplicated node and deletes it when the root is deleted.

**Tech Stack:** ESP-IDF C++17, cJSON, Python unittest static CI tests.

---

### Task 1: Add Regression Coverage

**Files:**
- Modify: `tests/ci/test_edge_d_firmware_static.py`

- [x] **Step 1: Add the failing static test**

```python
    def test_u8_protocol_command_builder_owns_extra_json_copies(self):
        text = U8_BOARD_DIR.joinpath("u1_protocol_client.cc").read_text(
            encoding="utf-8", errors="replace"
        )

        builder = text[
            text.index("std::string U1ProtocolClient::BuildProtocolCommandJson"):
            text.index("std::string U1ProtocolClient::SendU1ProtocolCommand")
        ]
        self.assertIn("cJSON_Duplicate(child, 1)", builder)
        self.assertIn("cJSON_AddItemToObject(root, child->string, duplicated)", builder)
        self.assertNotIn("cJSON_AddItemReferenceToObject", builder)
```

- [x] **Step 2: Run the target test and confirm RED**

Run: `rtk python -m unittest tests.ci.test_edge_d_firmware_static.EdgeDFirmwareStaticTests.test_u8_protocol_command_builder_owns_extra_json_copies -v`

Expected: FAIL because `u1_protocol_client.cc` still uses `cJSON_AddItemReferenceToObject`.

Observed: FAIL before implementation with the expected missing `cJSON_AddItemToObject(root, child->string, duplicated)` assertion.

### Task 2: Fix cJSON Ownership

**Files:**
- Modify: `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/u1_protocol_client.cc:78`

- [x] **Step 1: Replace reference ownership with root ownership**

```cpp
        cJSON* child = extra->child;
        while (child != nullptr) {
            cJSON* duplicated = cJSON_Duplicate(child, 1);
            if (duplicated != nullptr) {
                cJSON_AddItemToObject(root, child->string, duplicated);
            }
            child = child->next;
        }
```

- [x] **Step 2: Run the target test and confirm GREEN**

Run: `rtk python -m unittest tests.ci.test_edge_d_firmware_static.EdgeDFirmwareStaticTests.test_u8_protocol_command_builder_owns_extra_json_copies -v`

Expected: PASS.

Observed: PASS after implementation.

### Task 3: Validate Relevant Project Checks

**Files:**
- No additional edits.

- [x] **Step 1: Run U8 static firmware tests**

Run: `rtk python -m unittest tests.ci.test_edge_d_firmware_static -v`

Expected: all tests pass.

Observed: 31 tests passed.

- [x] **Step 2: Run GPIO static check**

Run: `rtk python tools/check_gpio.py`

Expected: exit code 0 and no ERROR issues.

Observed: GPIO check passed with no issues found.

- [x] **Step 3: Run full CI Python tests if local dependencies are available**

Run: `rtk python -m unittest discover -s tests/ci -p test_*.py -v`

Expected: all tests pass; if local optional dependencies are missing, report the exact blocker.

Observed: full discover is blocked in the current Python 3.14 environment by missing `httpx`; `pytest` is also unavailable. A dependency install attempt did not complete because `pytest.exe` was locked by another process.
