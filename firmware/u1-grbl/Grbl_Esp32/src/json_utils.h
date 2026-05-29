#pragma once
#include <cstddef>
#include <cstdint>

// Skip whitespace in JSON string
const char* json_skip_ws(const char* p);

// Extract a string value from JSON by field name
// Returns true on success, false if field not found or invalid
bool json_extract_string_field(const char* json, const char* field, char* output, size_t output_size);

// Extract a float value from JSON by field name
// Returns true on success, false if field not found or not numeric
bool json_extract_float_field(const char* json, const char* field, float* output);

// Extract an int value from JSON by field name (via float conversion)
// Returns true on success, false if field not found or not numeric
bool json_extract_int_field(const char* json, const char* field, int* output);
