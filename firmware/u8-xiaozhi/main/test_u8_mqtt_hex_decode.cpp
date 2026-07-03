// Native unit tests for U8 MQTT protocol hex-decode helper (P3.4).
// Tests DecodeHexString / CharToHex pure logic extracted from mqtt_protocol.cc.
// No ESP-IDF or hardware required. Compile with:
//   g++ -std=c++17 test_u8_mqtt_hex_decode.cpp -o test_u8_mqtt_hex_decode
//
// The tested functions are members of MqttProtocol (which pulls ESP-IDF headers),
// so we re-implement the pure logic here for native testing.

#include <cstdint>
#include <cstdio>
#include <string>

// --- Re-implementations of pure logic for native testing ---

static inline uint8_t CharToHex(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return 0;  // 无效输入返回 0（与固件实现一致）
}

static std::string DecodeHexString(const std::string& hex_string) {
    std::string decoded;
    decoded.reserve(hex_string.size() / 2);
    for (size_t i = 0; i < hex_string.size(); i += 2) {
        char byte = static_cast<char>((CharToHex(hex_string[i]) << 4) | CharToHex(hex_string[i + 1]));
        decoded.push_back(byte);
    }
    return decoded;
}

// --- Test harness ---

static int passed = 0;
static int failed = 0;

#define TEST(name) do { printf("  %-55s", name); } while (0)
#define PASS() do { printf("PASS\n"); ++passed; } while (0)
#define FAIL(msg) do { printf("FAIL: %s\n", msg); ++failed; } while (0)
#define ASSERT_EQ(a, b) do { if ((a) != (b)) { FAIL("expected equal"); return; } } while (0)

// --- CharToHex ---

void test_char_to_hex_digits() {
    TEST("CharToHex: 0-9 map to 0-9");
    ASSERT_EQ(static_cast<int>(CharToHex('0')), 0);
    ASSERT_EQ(static_cast<int>(CharToHex('9')), 9);
    PASS();
}

void test_char_to_hex_upper() {
    TEST("CharToHex: A-F map to 10-15");
    ASSERT_EQ(static_cast<int>(CharToHex('A')), 10);
    ASSERT_EQ(static_cast<int>(CharToHex('F')), 15);
    PASS();
}

void test_char_to_hex_lower() {
    TEST("CharToHex: a-f map to 10-15");
    ASSERT_EQ(static_cast<int>(CharToHex('a')), 10);
    ASSERT_EQ(static_cast<int>(CharToHex('f')), 15);
    PASS();
}

void test_char_to_hex_invalid() {
    TEST("CharToHex: invalid returns 0");
    ASSERT_EQ(static_cast<int>(CharToHex('g')), 0);
    ASSERT_EQ(static_cast<int>(CharToHex(' ')), 0);
    PASS();
}

// --- DecodeHexString ---

void test_decode_empty() {
    TEST("DecodeHexString: empty input -> empty output");
    ASSERT_EQ(DecodeHexString(""), std::string(""));
    PASS();
}

void test_decode_single_byte() {
    TEST("DecodeHexString: '41' -> 'A'");
    ASSERT_EQ(DecodeHexString("41"), std::string("A"));
    PASS();
}

void test_decode_multibyte() {
    TEST("DecodeHexString: '48656c6c6f' -> 'Hello'");
    ASSERT_EQ(DecodeHexString("48656c6c6f"), std::string("Hello"));
    PASS();
}

void test_decode_mixed_case() {
    TEST("DecodeHexString: mixed case '4A6b' -> 'Jk'");
    ASSERT_EQ(DecodeHexString("4A6b"), std::string("Jk"));
    PASS();
}

void test_decode_binary_payload() {
    TEST("DecodeHexString: binary bytes '00ff80'");
    std::string result = DecodeHexString("00ff80");
    ASSERT_EQ(result.size(), static_cast<size_t>(3));
    ASSERT_EQ(static_cast<int>(static_cast<unsigned char>(result[0])), 0x00);
    ASSERT_EQ(static_cast<int>(static_cast<unsigned char>(result[1])), 0xff);
    ASSERT_EQ(static_cast<int>(static_cast<unsigned char>(result[2])), 0x80);
    PASS();
}

void test_decode_audio_hex_prefix() {
    TEST("DecodeHexString: realistic audio hex prefix");
    // 模拟音频帧十六进制前缀（非 ASCII 字节）
    std::string result = DecodeHexString("52494646");  // "RIFF"
    ASSERT_EQ(result, std::string("RIFF"));
    PASS();
}

int main() {
    printf("U8 MQTT hex-decode native tests\n");
    printf("===============================\n\n");

    test_char_to_hex_digits();
    test_char_to_hex_upper();
    test_char_to_hex_lower();
    test_char_to_hex_invalid();

    test_decode_empty();
    test_decode_single_byte();
    test_decode_multibyte();
    test_decode_mixed_case();
    test_decode_binary_payload();
    test_decode_audio_hex_prefix();

    printf("\n===============================\n");
    printf("Results: %d passed, %d failed\n", passed, failed);
    return failed > 0 ? 1 : 0;
}
