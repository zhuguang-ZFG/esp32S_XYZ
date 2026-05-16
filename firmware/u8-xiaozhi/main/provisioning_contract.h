#ifndef PROVISIONING_CONTRACT_H
#define PROVISIONING_CONTRACT_H

namespace ProvisioningContract {

inline constexpr const char* kPrimaryChannel = "ble_blufi";
inline constexpr const char* kFallbackChannel = "softap_http";

inline constexpr const char* kBlufiDeviceName = "Xiaozhi-Blufi";
inline constexpr const char* kSoftApSsidPrefix = "Xiaozhi";
inline constexpr const char* kSoftApBaseUrl = "http://192.168.4.1";
inline constexpr const char* kSoftApScanPath = "/scan";
inline constexpr const char* kSoftApSubmitPath = "/submit";
inline constexpr const char* kSoftApExitPath = "/exit";

inline constexpr const char* kNvsNamespace = "wifi";
inline constexpr const char* kNvsSsidKey = "ssid";
inline constexpr const char* kNvsPasswordKey = "password";
inline constexpr const char* kNvsDeviceSecretKey = "device_secret";
inline constexpr const char* kNvsServerHostKey = "server_host";

inline constexpr const char* kSecurityPairing = "ble_just_works";
inline constexpr const char* kCredentialPayloadFields = "ssid,password,server_host,device_secret";

} // namespace ProvisioningContract

#endif // PROVISIONING_CONTRACT_H
