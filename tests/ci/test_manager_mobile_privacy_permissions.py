import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MOBILE_ROOT = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-mobile"
MOBILE_SRC = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-mobile" / "src"
PAGES_JSON = MOBILE_SRC / "pages.json"
MANIFEST = MOBILE_SRC / "manifest.json"
MANIFEST_CONFIG = MOBILE_ROOT / "manifest.config.ts"
VITE_CONFIG = MOBILE_ROOT / "vite.config.ts"
SETTINGS_PAGE = MOBILE_SRC / "pages" / "settings" / "index.vue"
PRIVACY_PERMISSIONS_PAGE = MOBILE_SRC / "pages" / "settings" / "privacy-permissions.vue"
DEVICE_CONFIG_PAGE = MOBILE_SRC / "pages" / "device-config" / "index.vue"
UNI_PAGES = MOBILE_SRC / "types" / "uni-pages.d.ts"
PRIVACY_PERMISSIONS_RUNBOOK = ROOT / "ops" / "runbooks" / "m6-privacy-permissions.md"
PRIVACY_PERMISSIONS_STATUS = ROOT / "docs" / "M6.1-privacy-permissions-status.md"


class ManagerMobilePrivacyPermissionsTests(unittest.TestCase):
    def test_privacy_permissions_page_is_registered(self):
        pages = PAGES_JSON.read_text(encoding="utf-8", errors="replace")
        types = UNI_PAGES.read_text(encoding="utf-8", errors="replace")

        self.assertIn('"path": "pages/settings/privacy-permissions"', pages)
        self.assertIn('"navigationBarTitleText": "隐私与权限"', pages)
        self.assertIn('"/pages/settings/privacy-permissions"', types)

    def test_wechat_manifest_declares_local_permission_purposes(self):
        text = MANIFEST.read_text(encoding="utf-8", errors="replace")
        config = MANIFEST_CONFIG.read_text(encoding="utf-8", errors="replace")
        vite = VITE_CONFIG.read_text(encoding="utf-8", errors="replace")

        self.assertIn('"mp-weixin"', text)
        self.assertIn('"permission"', text)
        self.assertIn('"scope.record"', text)
        self.assertIn('"scope.userLocation"', text)
        self.assertIn('"requiredPrivateInfos"', text)
        self.assertIn('"getLocation"', text)
        self.assertIn("'scope.record'", config)
        self.assertIn("'scope.userLocation'", config)
        self.assertIn("requiredPrivateInfos: ['getLocation']", config)
        self.assertIn("patch-mp-weixin-permissions", vite)
        self.assertIn("dist/build/mp-weixin/app.json", vite)
        self.assertIn("'scope.record'", vite)

    def test_settings_links_privacy_permissions_page(self):
        text = SETTINGS_PAGE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("openPrivacyPermissions", text)
        self.assertIn("uni.navigateTo({ url: '/pages/settings/privacy-permissions' })", text)
        self.assertIn("隐私协议与系统授权", text)
        self.assertIn("麦克风、蓝牙、Wi-Fi", text)

    def test_privacy_permissions_page_has_separate_permission_flows_and_fallbacks(self):
        text = PRIVACY_PERMISSIONS_PAGE.read_text(encoding="utf-8", errors="replace")

        for token in (
            "type PermissionId = 'microphone' | 'bluetooth' | 'wifi'",
            "scope: 'scope.record'",
            "uni.openBluetoothAdapter",
            "uni.startWifi",
            "openSystemPermissionSettings",
            "uni.openSetting",
            "fallbackHint",
            "麦克风未授权时",
            "蓝牙未开启或未授权时",
            "Wi-Fi 未开启或未授权时",
            "openPrivacyPolicy",
            "/pages/login/privacy-policy-zh",
        ):
            self.assertIn(token, text)

    def test_device_config_exposes_permission_fallback_entry(self):
        text = DEVICE_CONFIG_PAGE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("openPrivacyPermissions", text)
        self.assertIn("配网需要蓝牙和 Wi-Fi 权限", text)
        self.assertIn("未授权时可进入权限页查看兜底提示", text)
        self.assertIn("权限设置", text)


    def test_privacy_permissions_runbook_covers_wechat_console_and_device_drill(self):
        text = PRIVACY_PERMISSIONS_RUNBOOK.read_text(encoding="utf-8", errors="replace")

        for token in (
            "/pages/settings/privacy-permissions",
            "/pages/login/privacy-policy-zh",
            "mp-weixin.permission.scope.record",
            "requiredPrivateInfos: getLocation",
            "microphone",
            "Bluetooth",
            "Wi-Fi",
            "rtk python -m unittest tests.ci.test_manager_mobile_privacy_permissions -v",
            "rtk corepack pnpm type-check",
            "rtk corepack pnpm run build:mp-weixin",
            "dist/build/mp-weixin/app.json",
            "missing scope.record",
            "missing scope.userLocation",
            "missing getLocation private info",
            "WeChat Mini-Program Console Checklist",
            "Device Acceptance Drill",
            "privacy policy URL",
            "microphone API purpose",
            "Bluetooth API purpose",
            "Wi-Fi API purpose",
            "subscription/notification wording",
            "matches the privacy policy",
            "Re-enable permissions through system settings",
            "build version",
            "phone model and OS version",
            "WeChat version",
            "microphone prompt and fallback screenshots",
            "Bluetooth prompt and fallback screenshots",
            "Wi-Fi prompt and fallback screenshots",
            "reviewer notes or WeChat review result",
            "Privacy Permissions Evidence Gap Record",
            "Use this structure when WeChat console, real permission prompt, or review evidence is missing",
            "missing evidence scope: privacy policy URL, microphone purpose, Bluetooth purpose, Wi-Fi purpose, generated permission artifact, real permission prompt, fallback screenshot, reviewer notes, or WeChat review approval",
            "fallback path: keep the in-app privacy/permission page reachable, keep per-permission fallback prompts, block release if required WeChat declarations or real prompt screenshots are missing",
            "risk acceptance: product owner, compliance owner, and release manager approval reference",
            "rollback trigger: missing WeChat declaration, generated artifact lacks required permission fields, real prompt text mismatches policy wording, fallback prompt is not reachable, or review rejects the privacy declaration",
            "follow-up evidence: console privacy declaration screenshot, generated `app.json`, build version, phone model, OS version, WeChat version, prompt screenshots, fallback screenshots, privacy policy screenshot, reviewer notes, and review approval result",
            "does not prove WeChat console privacy declarations",
            "real permission prompts",
            "review approval",
        ):
            self.assertIn(token, text)

    def test_privacy_permissions_status_covers_source_and_generated_manifest_artifacts(self):
        text = PRIVACY_PERMISSIONS_STATUS.read_text(encoding="utf-8", errors="replace")

        for token in (
            "manager-mobile/manifest.config.ts",
            "manager-mobile/vite.config.ts",
            "manager-mobile/src/manifest.json",
            "dist/build/mp-weixin/app.json",
            "rtk corepack pnpm run build:mp-weixin",
            "mp-weixin.permission.scope.record",
            "mp-weixin.permission.scope.userLocation",
            "requiredPrivateInfos: getLocation",
            "source manifest checks alone are not sufficient",
        ):
            self.assertIn(token, text)


if __name__ == "__main__":
    unittest.main()
