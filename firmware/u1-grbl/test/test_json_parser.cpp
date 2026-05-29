#include <cstdio>
#include <cstring>
#include <cassert>
#include "json_utils.h"

int main() {
    char out[64];
    float fval;
    int ival;

    // String tests
    assert(json_extract_string_field("{\"cmd\":\"MOVE\"}", "cmd", out, sizeof(out)));
    assert(strcmp(out, "MOVE") == 0);

    assert(json_extract_string_field("{\"cmd\" : \"HOME\"}", "cmd", out, sizeof(out)));
    assert(strcmp(out, "HOME") == 0);

    assert(!json_extract_string_field("{\"cmd\":\"MOVE\"}", "missing", out, sizeof(out)));
    assert(!json_extract_string_field(nullptr, "cmd", out, sizeof(out)));

    assert(json_extract_string_field("{\"x\":\"\"}", "x", out, sizeof(out)));
    assert(strcmp(out, "") == 0);

    assert(json_extract_string_field("{\"msg\":\"hello\\\"world\"}", "msg", out, sizeof(out)));

    // Float tests
    assert(json_extract_float_field("{\"feed\":1000}", "feed", &fval));
    assert(fval == 1000.0f);

    assert(json_extract_float_field("{\"x\":-10.5}", "x", &fval));
    assert(fval == -10.5f);

    assert(!json_extract_float_field("{\"feed\":\"1000\"}", "feed", &fval));
    assert(!json_extract_float_field(nullptr, "feed", &fval));

    assert(json_extract_float_field("{\"x\":0}", "x", &fval));
    assert(fval == 0.0f);

    // Int tests
    assert(json_extract_int_field("{\"total_segments\":5}", "total_segments", &ival));
    assert(ival == 5);

    assert(json_extract_int_field("{\"val\":7.9}", "val", &ival));
    assert(ival == 7);

    assert(!json_extract_int_field("{\"val\":\"5\"}", "val", &ival));

    // Realistic protocol message
    const char* json = "{\"msg_id\":\"42\",\"task_id\":\"task-abc\",\"cmd\":\"MOVE\",\"x\":100,\"y\":200,\"z\":50,\"feed\":1500}";
    assert(json_extract_string_field(json, "cmd", out, sizeof(out)));
    assert(strcmp(out, "MOVE") == 0);
    assert(json_extract_float_field(json, "x", &fval));
    assert(fval == 100.0f);
    assert(json_extract_float_field(json, "feed", &fval));
    assert(fval == 1500.0f);

    // @-prefix handling
    const char* input = "@{\"msg_id\":\"1\",\"cmd\":\"GET_STATUS\"}";
    assert(json_extract_string_field(input + 1, "cmd", out, sizeof(out)));
    assert(strcmp(out, "GET_STATUS") == 0);

    printf("All 18 JSON parser tests passed!\n");
    return 0;
}
