// Native unit tests for U8 board module pure logic functions.
// These test string manipulation and normalization functions that don't
// require ESP-IDF or hardware. Compile with:
//   g++ -std=c++17 -I <board_dir> test_u8_protocol_logic.cpp -o test_u8_protocol_logic
//
// The tested functions are extracted from the U8 board module into
// u1_protocol_client.h/.cc. Since that file includes ESP-IDF headers,
// we re-implement the pure logic here for native testing.

#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <utility>

// --- Re-implementations of pure logic for native testing ---

static std::string NormalizeResponse(std::string text) {
    for (char& ch : text) {
        if (ch == '\r' || ch == '\n') {
            ch = ' ';
        }
    }
    while (!text.empty() && text.back() == ' ') {
        text.pop_back();
    }
    return text;
}

static std::string ToLowerAscii(std::string s) {
    for (char& c : s) {
        if (c >= 'A' && c <= 'Z') {
            c = static_cast<char>(c - 'A' + 'a');
        }
    }
    return s;
}

static std::string NormalizeMotionCapabilityName(const char* raw) {
    if (raw == nullptr) {
        return "";
    }
    std::string s = raw;
    if (s.size() > 5 && ToLowerAscii(s.substr(0, 5)) == "self.") {
        s = s.substr(5);
    }
    return ToLowerAscii(std::move(s));
}

// Mirrors the msg_id serialization in U1ProtocolClient::BuildProtocolCommandJson.
// 固件审查第二轮 FW-F3：cmd.schema.json 要求 msg_id 为 string；发出帧必须形如
// "msg_id":"123"（带引号），否则 U1 json_utils 解析失败、全链路 msg_id_mismatch。
static std::string FormatProtocolMsgId(uint32_t msg_id) {
    char buf[16];
    snprintf(buf, sizeof(buf), "%u", static_cast<unsigned>(msg_id));
    return std::string(buf);
}

static std::string BuildMsgIdWireFragment(uint32_t msg_id) {
    return std::string("\"msg_id\":\"") + FormatProtocolMsgId(msg_id) + "\"";
}

// --- Test cases ---

static int passed = 0;
static int failed = 0;

#define TEST(name) \
    do { \
        printf("  %-50s", name); \
    } while (0)

#define PASS() \
    do { \
        printf("PASS\n"); \
        ++passed; \
    } while (0)

#define FAIL(msg) \
    do { \
        printf("FAIL: %s\n", msg); \
        ++failed; \
    } while (0)

#define ASSERT_EQ(a, b) \
    do { \
        if ((a) != (b)) { \
            FAIL("expected equal"); \
            return; \
        } \
    } while (0)

void test_normalize_response_basic() {
    TEST("NormalizeResponse: basic trim");
    auto result = NormalizeResponse("hello world");
    ASSERT_EQ(result, std::string("hello world"));
    PASS();
}

void test_normalize_response_strips_newlines() {
    TEST("NormalizeResponse: strips \\r\\n");
    auto result = NormalizeResponse("ok\r\n");
    ASSERT_EQ(result, std::string("ok"));
    PASS();
}

void test_normalize_response_replaces_internal_newlines() {
    TEST("NormalizeResponse: replaces internal \\r\\n with space");
    auto result = NormalizeResponse("line1\nline2\rline3");
    ASSERT_EQ(result, std::string("line1 line2 line3"));
    PASS();
}

void test_normalize_response_empty() {
    TEST("NormalizeResponse: empty string");
    auto result = NormalizeResponse("");
    ASSERT_EQ(result, std::string(""));
    PASS();
}

void test_normalize_response_all_whitespace() {
    TEST("NormalizeResponse: all trailing whitespace");
    auto result = NormalizeResponse("  \r\n  \r\n");
    ASSERT_EQ(result, std::string(""));
    PASS();
}

void test_normalize_response_mixed_trailing() {
    TEST("NormalizeResponse: mixed trailing spaces and newlines");
    auto result = NormalizeResponse("data  \n\r ");
    ASSERT_EQ(result, std::string("data"));
    PASS();
}

void test_to_lower_basic() {
    TEST("ToLowerAscii: basic lowercase");
    ASSERT_EQ(ToLowerAscii("HELLO"), std::string("hello"));
    PASS();
}

void test_to_lower_mixed() {
    TEST("ToLowerAscii: mixed case");
    ASSERT_EQ(ToLowerAscii("Motor.Home"), std::string("motor.home"));
    PASS();
}

void test_to_lower_already_lower() {
    TEST("ToLowerAscii: already lowercase");
    ASSERT_EQ(ToLowerAscii("home"), std::string("home"));
    PASS();
}

void test_to_lower_empty() {
    TEST("ToLowerAscii: empty string");
    ASSERT_EQ(ToLowerAscii(""), std::string(""));
    PASS();
}

void test_to_lower_preserves_non_alpha() {
    TEST("ToLowerAscii: preserves non-alpha chars");
    ASSERT_EQ(ToLowerAscii("move_rel.v2"), std::string("move_rel.v2"));
    PASS();
}

void test_normalize_cap_null() {
    TEST("NormalizeMotionCapabilityName: null input");
    ASSERT_EQ(NormalizeMotionCapabilityName(nullptr), std::string(""));
    PASS();
}

void test_normalize_cap_empty() {
    TEST("NormalizeMotionCapabilityName: empty string");
    ASSERT_EQ(NormalizeMotionCapabilityName(""), std::string(""));
    PASS();
}

void test_normalize_cap_strips_self_prefix() {
    TEST("NormalizeMotionCapabilityName: strips 'self.' prefix");
    ASSERT_EQ(NormalizeMotionCapabilityName("self.motor.home"), std::string("motor.home"));
    PASS();
}

void test_normalize_cap_lowercases() {
    TEST("NormalizeMotionCapabilityName: lowercases result");
    ASSERT_EQ(NormalizeMotionCapabilityName("Motor.HOME"), std::string("motor.home"));
    PASS();
}

void test_normalize_cap_strips_self_and_lowercases() {
    TEST("NormalizeMotionCapabilityName: strips self. and lowercases");
    ASSERT_EQ(NormalizeMotionCapabilityName("Self.Motor.GET_STATUS"), std::string("motor.get_status"));
    PASS();
}

void test_normalize_cap_no_prefix() {
    TEST("NormalizeMotionCapabilityName: no self. prefix");
    ASSERT_EQ(NormalizeMotionCapabilityName("home"), std::string("home"));
    PASS();
}

void test_normalize_cap_short_self() {
    TEST("NormalizeMotionCapabilityName: 'self' without dot (no strip)");
    ASSERT_EQ(NormalizeMotionCapabilityName("self"), std::string("self"));
    PASS();
}

void test_normalize_cap_all_known_capabilities() {
    TEST("NormalizeMotionCapabilityName: all known capability names");
    // Verify all capability names used in HandleMotionTaskJson normalize correctly
    ASSERT_EQ(NormalizeMotionCapabilityName("home"), std::string("home"));
    ASSERT_EQ(NormalizeMotionCapabilityName("motor.home"), std::string("motor.home"));
    ASSERT_EQ(NormalizeMotionCapabilityName("get_status"), std::string("get_status"));
    ASSERT_EQ(NormalizeMotionCapabilityName("motor.get_status"), std::string("motor.get_status"));
    ASSERT_EQ(NormalizeMotionCapabilityName("getstatus"), std::string("getstatus"));
    ASSERT_EQ(NormalizeMotionCapabilityName("self.motor.stop"), std::string("motor.stop"));
    ASSERT_EQ(NormalizeMotionCapabilityName("run_path"), std::string("run_path"));
    ASSERT_EQ(NormalizeMotionCapabilityName("motor.run_path"), std::string("motor.run_path"));
    ASSERT_EQ(NormalizeMotionCapabilityName("path"), std::string("path"));
    ASSERT_EQ(NormalizeMotionCapabilityName("move_abs"), std::string("move_abs"));
    ASSERT_EQ(NormalizeMotionCapabilityName("motor.move_abs"), std::string("motor.move_abs"));
    ASSERT_EQ(NormalizeMotionCapabilityName("move_rel"), std::string("move_rel"));
    ASSERT_EQ(NormalizeMotionCapabilityName("motor.move_rel"), std::string("motor.move_rel"));
    PASS();
}

void test_normalize_response_json_payload() {
    TEST("NormalizeResponse: realistic JSON payload");
    std::string input = "{\"type\":\"ack\",\"ok\":true}\r\n";
    auto result = NormalizeResponse(input);
    ASSERT_EQ(result, std::string("{\"type\":\"ack\",\"ok\":true}"));
    PASS();
}

void test_normalize_response_multiline_json() {
    TEST("NormalizeResponse: multi-line JSON payload");
    std::string input = "{\n  \"type\": \"status\",\n  \"ok\": true\n}\r\n";
    auto result = NormalizeResponse(input);
    // Internal newlines become spaces, trailing trimmed
    ASSERT_EQ(result, std::string("{   \"type\": \"status\",   \"ok\": true }"));
    PASS();
}

void test_msg_id_wire_format_is_string() {
    TEST("msg_id wire format: serialized as JSON string");
    // cmd.schema.json contract: msg_id must be a quoted string on the wire.
    ASSERT_EQ(FormatProtocolMsgId(0), std::string("0"));
    ASSERT_EQ(FormatProtocolMsgId(42), std::string("42"));
    ASSERT_EQ(FormatProtocolMsgId(4294967295u), std::string("4294967295"));
    ASSERT_EQ(BuildMsgIdWireFragment(42), std::string("\"msg_id\":\"42\""));
    PASS();
}

int main() {
    printf("U8 protocol logic native tests\n");
    printf("==============================\n\n");

    // NormalizeResponse tests
    test_normalize_response_basic();
    test_normalize_response_strips_newlines();
    test_normalize_response_replaces_internal_newlines();
    test_normalize_response_empty();
    test_normalize_response_all_whitespace();
    test_normalize_response_mixed_trailing();
    test_normalize_response_json_payload();
    test_normalize_response_multiline_json();

    // ToLowerAscii tests
    test_to_lower_basic();
    test_to_lower_mixed();
    test_to_lower_already_lower();
    test_to_lower_empty();
    test_to_lower_preserves_non_alpha();

    // NormalizeMotionCapabilityName tests
    test_normalize_cap_null();
    test_normalize_cap_empty();
    test_normalize_cap_strips_self_prefix();
    test_normalize_cap_lowercases();
    test_normalize_cap_strips_self_and_lowercases();
    test_normalize_cap_no_prefix();
    test_normalize_cap_short_self();
    test_normalize_cap_all_known_capabilities();

    // msg_id wire contract tests (FW-F3)
    test_msg_id_wire_format_is_string();

    printf("\n==============================\n");
    printf("Results: %d passed, %d failed\n", passed, failed);
    return failed > 0 ? 1 : 0;
}
