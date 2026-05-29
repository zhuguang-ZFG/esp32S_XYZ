#include "json_utils.h"
#include <cstdio>
#include <cstring>
#include <cstdlib>

const char* json_skip_ws(const char* p) {
    while (*p == ' ' || *p == '\t' || *p == '\r' || *p == '\n') p++;
    return p;
}

bool json_extract_string_field(const char* json, const char* field, char* output, size_t output_size) {
    if (json == nullptr || field == nullptr || output == nullptr || output_size == 0) {
        return false;
    }

    char pattern[32];
    snprintf(pattern, sizeof(pattern), "\"%s\"", field);
    const char* key = strstr(json, pattern);
    if (key == nullptr) {
        output[0] = '\0';
        return false;
    }

    const char* p = json_skip_ws(key + strlen(pattern));
    if (*p != ':') {
        output[0] = '\0';
        return false;
    }
    p = json_skip_ws(p + 1);
    if (*p != '"') {
        output[0] = '\0';
        return false;
    }
    p++; // skip opening quote

    const char* end = p;
    while (*end != '\0' && *end != '"') {
        if (*end == '\\') end++; // skip escaped char
        end++;
    }
    if (*end != '"') {
        output[0] = '\0';
        return false;
    }

    size_t len = end - p;
    if (len >= output_size) {
        len = output_size - 1;
    }

    memcpy(output, p, len);
    output[len] = '\0';
    return true;
}

bool json_extract_float_field(const char* json, const char* field, float* output) {
    if (json == nullptr || field == nullptr || output == nullptr) {
        return false;
    }

    char pattern[32];
    snprintf(pattern, sizeof(pattern), "\"%s\"", field);
    const char* key = strstr(json, pattern);
    if (key == nullptr) {
        return false;
    }

    const char* p = json_skip_ws(key + strlen(pattern));
    if (*p != ':') {
        return false;
    }
    p = json_skip_ws(p + 1);

    // Must be a numeric value (not a string or object)
    if (*p == '"' || *p == '{' || *p == '[') {
        return false;
    }

    char* endptr = nullptr;
    float val = strtof(p, &endptr);
    if (endptr == p) {
        return false; // no digits parsed
    }
    *output = val;
    return true;
}

bool json_extract_int_field(const char* json, const char* field, int* output) {
    float value = 0.0f;
    if (!json_extract_float_field(json, field, &value)) {
        return false;
    }
    *output = static_cast<int>(value);
    return true;
}
