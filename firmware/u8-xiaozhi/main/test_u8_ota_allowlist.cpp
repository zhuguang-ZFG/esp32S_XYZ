// Native unit tests for U8 OTA pure-logic helpers (P3.4).
// These test URL host allow-listing, SHA-256 hex validation, and base64
// shape checking — the security-critical pure functions extracted from ota.cc.
// They do NOT require ESP-IDF or hardware. Compile with:
//   g++ -std=c++17 test_u8_ota_allowlist.cpp -o test_u8_ota_allowlist
//
// The tested functions live in ota.cc but include ESP-IDF headers, so we
// re-implement the pure logic here for native testing. If ota.cc logic changes,
// update both copies.

#include <algorithm>
#include <cstddef>
#include <cstdio>
#include <string>
#include <vector>

// --- Re-implementations of pure logic for native testing ---

static bool IsHttpsUrl(const std::string& url) {
    return url.rfind("https://", 0) == 0;
}

static bool IsAllowedOtaHost(const std::string& url) {
    static const std::vector<std::string> kAllowedHosts = {
        "chat.donglicao.com",
        "donglicao.com",
        "127.0.0.1",
        "localhost",
    };
    for (const auto& host : kAllowedHosts) {
        std::string prefix = "https://" + host;
        if (url.compare(0, prefix.size(), prefix) == 0) {
            // 确保 host 段精确匹配（避免 "chat.donglicao.com.evil.com" 绕过）
            char next = url.size() > prefix.size() ? url[prefix.size()] : '\0';
            if (next == '\0' || next == '/' || next == ':' || next == '?') {
                return true;
            }
        }
    }
    return false;
}

static bool IsAllowedEndpointUrl(const std::string& url) {
    static const std::vector<std::string> kAllowedHosts = {
        "chat.donglicao.com",
        "donglicao.com",
        "127.0.0.1",
        "localhost",
    };
    std::string host_part;
    const std::string kSchemes[] = {"wss://", "ws://", "mqtts://", "mqtt://"};
    bool has_scheme = false;
    for (const auto& scheme : kSchemes) {
        if (url.rfind(scheme, 0) == 0) {
            host_part = url.substr(scheme.size());
            has_scheme = true;
            break;
        }
    }
    if (!has_scheme) {
        host_part = url;
    }
    size_t port_pos = host_part.find(':');
    if (port_pos != std::string::npos) {
        host_part = host_part.substr(0, port_pos);
    }
    size_t slash_pos = host_part.find('/');
    if (slash_pos != std::string::npos) {
        host_part = host_part.substr(0, slash_pos);
    }
    for (const auto& host : kAllowedHosts) {
        if (host_part == host) {
            return true;
        }
    }
    return false;
}

static bool IsLowerHexSha256(const std::string& value) {
    if (value.size() != 64) {
        return false;
    }
    return std::all_of(value.begin(), value.end(), [](char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f');
    });
}

static bool IsLikelyBase64(const std::string& value) {
    if (value.empty()) {
        return false;
    }
    return std::all_of(value.begin(), value.end(), [](char ch) {
        return (ch >= 'A' && ch <= 'Z')
            || (ch >= 'a' && ch <= 'z')
            || (ch >= '0' && ch <= '9')
            || ch == '+'
            || ch == '/'
            || ch == '=';
    });
}

// --- Test harness (mirrors test_u8_protocol_logic.cpp) ---

static int passed = 0;
static int failed = 0;

#define TEST(name) do { printf("  %-55s", name); } while (0)
#define PASS() do { printf("PASS\n"); ++passed; } while (0)
#define FAIL(msg) do { printf("FAIL: %s\n", msg); ++failed; } while (0)
#define ASSERT_EQ(a, b) do { if ((a) != (b)) { FAIL("expected equal"); return; } } while (0)
#define ASSERT_TRUE(v) do { if (!(v)) { FAIL("expected true"); return; } } while (0)
#define ASSERT_FALSE(v) do { if ((v)) { FAIL("expected false"); return; } } while (0)

// --- IsHttpsUrl ---

void test_is_https_url_basic() {
    TEST("IsHttpsUrl: https prefix");
    ASSERT_TRUE(IsHttpsUrl("https://chat.donglicao.com/ota"));
    PASS();
}

void test_is_https_url_http_rejected() {
    TEST("IsHttpsUrl: http rejected");
    ASSERT_FALSE(IsHttpsUrl("http://chat.donglicao.com/ota"));
    PASS();
}

void test_is_https_url_empty() {
    TEST("IsHttpsUrl: empty rejected");
    ASSERT_FALSE(IsHttpsUrl(""));
    PASS();
}

// --- IsAllowedOtaHost ---

void test_ota_host_allowed_primary() {
    TEST("IsAllowedOtaHost: chat.donglicao.com allowed");
    ASSERT_TRUE(IsAllowedOtaHost("https://chat.donglicao.com/firmware.bin"));
    PASS();
}

void test_ota_host_allowed_root() {
    TEST("IsAllowedOtaHost: donglicao.com allowed");
    ASSERT_TRUE(IsAllowedOtaHost("https://donglicao.com/firmware.bin"));
    PASS();
}

void test_ota_host_allowed_localhost() {
    TEST("IsAllowedOtaHost: localhost allowed (debug)");
    ASSERT_TRUE(IsAllowedOtaHost("https://localhost:8080/fw.bin"));
    PASS();
}

void test_ota_host_allowed_bare() {
    TEST("IsAllowedOtaHost: bare host no path allowed");
    ASSERT_TRUE(IsAllowedOtaHost("https://chat.donglicao.com"));
    PASS();
}

void test_ota_host_reject_evil_suffix() {
    TEST("IsAllowedOtaHost: evil suffix bypass blocked");
    ASSERT_FALSE(IsAllowedOtaHost("https://chat.donglicao.com.evil.com/fw"));
    PASS();
}

void test_ota_host_reject_unknown() {
    TEST("IsAllowedOtaHost: unknown host rejected");
    ASSERT_FALSE(IsAllowedOtaHost("https://evil.com/firmware.bin"));
    PASS();
}

void test_ota_host_reject_http() {
    TEST("IsAllowedOtaHost: http scheme rejected");
    ASSERT_FALSE(IsAllowedOtaHost("http://chat.donglicao.com/fw"));
    PASS();
}

void test_ota_host_allowed_with_port() {
    TEST("IsAllowedOtaHost: host with port allowed");
    ASSERT_TRUE(IsAllowedOtaHost("https://chat.donglicao.com:443/fw"));
    PASS();
}

void test_ota_host_allowed_with_query() {
    TEST("IsAllowedOtaHost: host with query allowed");
    ASSERT_TRUE(IsAllowedOtaHost("https://chat.donglicao.com/fw?v=2"));
    PASS();
}

// --- IsAllowedEndpointUrl ---

void test_endpoint_wss_allowed() {
    TEST("IsAllowedEndpointUrl: wss chat.donglicao.com allowed");
    ASSERT_TRUE(IsAllowedEndpointUrl("wss://chat.donglicao.com/device/v1/ws"));
    PASS();
}

void test_endpoint_mqtt_allowed() {
    TEST("IsAllowedEndpointUrl: mqtt chat.donglicao.com allowed");
    ASSERT_TRUE(IsAllowedEndpointUrl("mqtts://chat.donglicao.com:8883"));
    PASS();
}

void test_endpoint_ws_localhost_allowed() {
    TEST("IsAllowedEndpointUrl: ws localhost allowed");
    ASSERT_TRUE(IsAllowedEndpointUrl("ws://127.0.0.1:8080/test"));
    PASS();
}

void test_endpoint_reject_evil() {
    TEST("IsAllowedEndpointUrl: evil host rejected");
    ASSERT_FALSE(IsAllowedEndpointUrl("wss://evil.com/ws"));
    PASS();
}

void test_endpoint_reject_no_scheme_evil() {
    TEST("IsAllowedEndpointUrl: bare evil host rejected");
    ASSERT_FALSE(IsAllowedEndpointUrl("evil.com"));
    PASS();
}

void test_endpoint_bare_allowed_host() {
    TEST("IsAllowedEndpointUrl: bare allowed host accepted");
    ASSERT_TRUE(IsAllowedEndpointUrl("chat.donglicao.com"));
    PASS();
}

void test_endpoint_strips_path() {
    TEST("IsAllowedEndpointUrl: host extracted before path");
    ASSERT_TRUE(IsAllowedEndpointUrl("wss://chat.donglicao.com/path?x=1"));
    PASS();
}

// --- IsLowerHexSha256 ---

void test_sha256_valid() {
    TEST("IsLowerHexSha256: valid 64-char lowercase hex");
    ASSERT_TRUE(IsLowerHexSha256("a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"));
    PASS();
}

void test_sha256_wrong_length() {
    TEST("IsLowerHexSha256: wrong length rejected");
    ASSERT_FALSE(IsLowerHexSha256("abc123"));
    PASS();
}

void test_sha256_uppercase_rejected() {
    TEST("IsLowerHexSha256: uppercase rejected");
    ASSERT_FALSE(IsLowerHexSha256("A1B2C3D4E5F60718293A4B5C6D7E8F90A1B2C3D4E5F60718293A4B5C6D7E8F90"));
    PASS();
}

void test_sha256_non_hex_rejected() {
    TEST("IsLowerHexSha256: non-hex chars rejected");
    ASSERT_FALSE(IsLowerHexSha256("g1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"));
    PASS();
}

void test_sha256_empty() {
    TEST("IsLowerHexSha256: empty rejected");
    ASSERT_FALSE(IsLowerHexSha256(""));
    PASS();
}

// --- IsLikelyBase64 ---

void test_base64_valid() {
    TEST("IsLikelyBase64: valid base64");
    ASSERT_TRUE(IsLikelyBase64("SGVsbG8gV29ybGQ="));
    PASS();
}

void test_base64_empty() {
    TEST("IsLikelyBase64: empty rejected");
    ASSERT_FALSE(IsLikelyBase64(""));
    PASS();
}

void test_base64_invalid_char() {
    TEST("IsLikelyBase64: invalid char rejected");
    ASSERT_FALSE(IsLikelyBase64("SGVsbG8!V29ybGQ="));
    PASS();
}

void test_base64_url_safe_rejected() {
    TEST("IsLikelyBase64: url-safe chars (-_) rejected");
    ASSERT_FALSE(IsLikelyBase64("SGVsbG8-V29ybGQ_"));
    PASS();
}

int main() {
    printf("U8 OTA allowlist native tests\n");
    printf("=============================\n\n");

    test_is_https_url_basic();
    test_is_https_url_http_rejected();
    test_is_https_url_empty();

    test_ota_host_allowed_primary();
    test_ota_host_allowed_root();
    test_ota_host_allowed_localhost();
    test_ota_host_allowed_bare();
    test_ota_host_reject_evil_suffix();
    test_ota_host_reject_unknown();
    test_ota_host_reject_http();
    test_ota_host_allowed_with_port();
    test_ota_host_allowed_with_query();

    test_endpoint_wss_allowed();
    test_endpoint_mqtt_allowed();
    test_endpoint_ws_localhost_allowed();
    test_endpoint_reject_evil();
    test_endpoint_reject_no_scheme_evil();
    test_endpoint_bare_allowed_host();
    test_endpoint_strips_path();

    test_sha256_valid();
    test_sha256_wrong_length();
    test_sha256_uppercase_rejected();
    test_sha256_non_hex_rejected();
    test_sha256_empty();

    test_base64_valid();
    test_base64_empty();
    test_base64_invalid_char();
    test_base64_url_safe_rejected();

    printf("\n=============================\n");
    printf("Results: %d passed, %d failed\n", passed, failed);
    return failed > 0 ? 1 : 0;
}
