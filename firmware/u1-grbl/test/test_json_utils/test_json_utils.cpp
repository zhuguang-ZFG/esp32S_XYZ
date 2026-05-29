#include <unity.h>
#include "json_utils.h"

// ── json_extract_string_field ──────────────────────────

void test_string_basic() {
    char out[32];
    TEST_ASSERT_TRUE(json_extract_string_field("{\"cmd\":\"MOVE\"}", "cmd", out, sizeof(out)));
    TEST_ASSERT_EQUAL_STRING("MOVE", out);
}

void test_string_with_spaces() {
    char out[32];
    TEST_ASSERT_TRUE(json_extract_string_field("{\"cmd\" : \"MOVE\"}", "cmd", out, sizeof(out)));
    TEST_ASSERT_EQUAL_STRING("MOVE", out);
}

void test_string_with_escapes() {
    char out[64];
    TEST_ASSERT_TRUE(json_extract_string_field("{\"msg\":\"hello\\\"world\"}", "msg", out, sizeof(out)));
    TEST_ASSERT_EQUAL_STRING("hello\\\"world", out);
}

void test_string_not_found() {
    char out[32];
    TEST_ASSERT_FALSE(json_extract_string_field("{\"cmd\":\"MOVE\"}", "missing", out, sizeof(out)));
}

void test_string_null_inputs() {
    char out[32];
    TEST_ASSERT_FALSE(json_extract_string_field(nullptr, "cmd", out, sizeof(out)));
    TEST_ASSERT_FALSE(json_extract_string_field("{\"cmd\":\"MOVE\"}", nullptr, out, sizeof(out)));
    TEST_ASSERT_FALSE(json_extract_string_field("{\"cmd\":\"MOVE\"}", "cmd", nullptr, 0));
    TEST_ASSERT_FALSE(json_extract_string_field("{\"cmd\":\"MOVE\"}", "cmd", out, 0));
}

void test_string_truncation() {
    char out[4];
    TEST_ASSERT_TRUE(json_extract_string_field("{\"cmd\":\"MOVE\"}", "cmd", out, sizeof(out)));
    TEST_ASSERT_EQUAL_STRING("MOV", out);
}

void test_string_empty_value() {
    char out[32];
    TEST_ASSERT_TRUE(json_extract_string_field("{\"cmd\":\"\"}", "cmd", out, sizeof(out)));
    TEST_ASSERT_EQUAL_STRING("", out);
}

void test_string_multiple_fields() {
    char out[32];
    TEST_ASSERT_TRUE(json_extract_string_field("{\"msg_id\":\"123\",\"cmd\":\"HOME\",\"task_id\":\"abc\"}", "cmd", out, sizeof(out)));
    TEST_ASSERT_EQUAL_STRING("HOME", out);
}

void test_string_escaped_backslash() {
    char out[32];
    TEST_ASSERT_TRUE(json_extract_string_field("{\"path\":\"C:\\\\Users\"}", "path", out, sizeof(out)));
    TEST_ASSERT_EQUAL_STRING("C:\\\\Users", out);
}

// ── json_extract_float_field ───────────────────────────

void test_float_basic() {
    float out;
    TEST_ASSERT_TRUE(json_extract_float_field("{\"feed\":1000}", "feed", &out));
    TEST_ASSERT_EQUAL_FLOAT(1000.0f, out);
}

void test_float_decimal() {
    float out;
    TEST_ASSERT_TRUE(json_extract_float_field("{\"x\":123.456}", "x", &out));
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 123.456f, out);
}

void test_float_negative() {
    float out;
    TEST_ASSERT_TRUE(json_extract_float_field("{\"z\":-10.5}", "z", &out));
    TEST_ASSERT_FLOAT_WITHIN(0.001f, -10.5f, out);
}

void test_float_with_spaces() {
    float out;
    TEST_ASSERT_TRUE(json_extract_float_field("{\"feed\" : 1000}", "feed", &out));
    TEST_ASSERT_EQUAL_FLOAT(1000.0f, out);
}

void test_float_reject_string() {
    float out;
    TEST_ASSERT_FALSE(json_extract_float_field("{\"feed\":\"1000\"}", "feed", &out));
}

void test_float_reject_object() {
    float out;
    TEST_ASSERT_FALSE(json_extract_float_field("{\"data\":{\"nested\":1}}", "data", &out));
}

void test_float_not_found() {
    float out;
    TEST_ASSERT_FALSE(json_extract_float_field("{\"feed\":1000}", "missing", &out));
}

void test_float_null_inputs() {
    float out;
    TEST_ASSERT_FALSE(json_extract_float_field(nullptr, "feed", &out));
    TEST_ASSERT_FALSE(json_extract_float_field("{\"feed\":1000}", nullptr, &out));
    TEST_ASSERT_FALSE(json_extract_float_field("{\"feed\":1000}", "feed", nullptr));
}

void test_float_zero() {
    float out;
    TEST_ASSERT_TRUE(json_extract_float_field("{\"x\":0}", "x", &out));
    TEST_ASSERT_EQUAL_FLOAT(0.0f, out);
}

void test_float_scientific() {
    float out;
    TEST_ASSERT_TRUE(json_extract_float_field("{\"val\":1.5e2}", "val", &out));
    TEST_ASSERT_EQUAL_FLOAT(150.0f, out);
}

// ── json_extract_int_field ─────────────────────────────

void test_int_basic() {
    int out;
    TEST_ASSERT_TRUE(json_extract_int_field("{\"total_segments\":5}", "total_segments", &out));
    TEST_ASSERT_EQUAL_INT(5, out);
}

void test_int_truncates_decimal() {
    int out;
    TEST_ASSERT_TRUE(json_extract_int_field("{\"val\":7.9}", "val", &out));
    TEST_ASSERT_EQUAL_INT(7, out);
}

void test_int_negative() {
    int out;
    TEST_ASSERT_TRUE(json_extract_int_field("{\"x\":-3}", "x", &out));
    TEST_ASSERT_EQUAL_INT(-3, out);
}

void test_int_not_found() {
    int out;
    TEST_ASSERT_FALSE(json_extract_int_field("{\"val\":5}", "missing", &out));
}

void test_int_reject_string() {
    int out;
    TEST_ASSERT_FALSE(json_extract_int_field("{\"val\":\"5\"}", "val", &out));
}

// ── Realistic protocol messages ────────────────────────

void test_realistic_move_command() {
    char cmd[32], task_id[64];
    float x, y, z, feed;
    const char* json = "{\"msg_id\":\"42\",\"task_id\":\"task-abc\",\"cmd\":\"MOVE\",\"x\":100,\"y\":200,\"z\":50,\"feed\":1500}";

    TEST_ASSERT_TRUE(json_extract_string_field(json, "cmd", cmd, sizeof(cmd)));
    TEST_ASSERT_EQUAL_STRING("MOVE", cmd);

    TEST_ASSERT_TRUE(json_extract_string_field(json, "task_id", task_id, sizeof(task_id)));
    TEST_ASSERT_EQUAL_STRING("task-abc", task_id);

    TEST_ASSERT_TRUE(json_extract_float_field(json, "x", &x));
    TEST_ASSERT_EQUAL_FLOAT(100.0f, x);

    TEST_ASSERT_TRUE(json_extract_float_field(json, "y", &y));
    TEST_ASSERT_EQUAL_FLOAT(200.0f, y);

    TEST_ASSERT_TRUE(json_extract_float_field(json, "z", &z));
    TEST_ASSERT_EQUAL_FLOAT(50.0f, z);

    TEST_ASSERT_TRUE(json_extract_float_field(json, "feed", &feed));
    TEST_ASSERT_EQUAL_FLOAT(1500.0f, feed);
}

void test_realistic_path_begin() {
    int total_segments;
    float feed;
    const char* json = "{\"msg_id\":\"10\",\"task_id\":\"task-xyz\",\"cmd\":\"PATH_BEGIN\",\"total_segments\":25,\"feed\":1200}";

    TEST_ASSERT_TRUE(json_extract_int_field(json, "total_segments", &total_segments));
    TEST_ASSERT_EQUAL_INT(25, total_segments);

    TEST_ASSERT_TRUE(json_extract_float_field(json, "feed", &feed));
    TEST_ASSERT_EQUAL_FLOAT(1200.0f, feed);
}

void test_at_prefix_handling() {
    // Protocol messages start with @, parser receives json = input + 1
    char cmd[32];
    const char* input = "@{\"msg_id\":\"1\",\"cmd\":\"GET_STATUS\"}";
    const char* json = input + 1; // skip @

    TEST_ASSERT_TRUE(json_extract_string_field(json, "cmd", cmd, sizeof(cmd)));
    TEST_ASSERT_EQUAL_STRING("GET_STATUS", cmd);
}

void setup() {
    UNITY_BEGIN();

    // String extraction
    RUN_TEST(test_string_basic);
    RUN_TEST(test_string_with_spaces);
    RUN_TEST(test_string_with_escapes);
    RUN_TEST(test_string_not_found);
    RUN_TEST(test_string_null_inputs);
    RUN_TEST(test_string_truncation);
    RUN_TEST(test_string_empty_value);
    RUN_TEST(test_string_multiple_fields);
    RUN_TEST(test_string_escaped_backslash);

    // Float extraction
    RUN_TEST(test_float_basic);
    RUN_TEST(test_float_decimal);
    RUN_TEST(test_float_negative);
    RUN_TEST(test_float_with_spaces);
    RUN_TEST(test_float_reject_string);
    RUN_TEST(test_float_reject_object);
    RUN_TEST(test_float_not_found);
    RUN_TEST(test_float_null_inputs);
    RUN_TEST(test_float_zero);
    RUN_TEST(test_float_scientific);

    // Int extraction
    RUN_TEST(test_int_basic);
    RUN_TEST(test_int_truncates_decimal);
    RUN_TEST(test_int_negative);
    RUN_TEST(test_int_not_found);
    RUN_TEST(test_int_reject_string);

    // Realistic protocol messages
    RUN_TEST(test_realistic_move_command);
    RUN_TEST(test_realistic_path_begin);
    RUN_TEST(test_at_prefix_handling);

    UNITY_END();
}

void loop() {}
