#!/usr/bin/env python3
"""Ensure SoftAP /submit persists device_secret (DLC patch on esp-wifi-connect)."""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
COMPONENT = ROOT / "managed_components/78__esp-wifi-connect"
TARGET_CC = COMPONENT / "wifi_configuration_ap.cc"
TARGET_HTML = COMPONENT / "assets/wifi_configuration.html"
PATCH = ROOT / "patches/esp-wifi-connect-softap-dlc.patch"
CC_MARKER = "SaveDlcProvisioningFields"
HTML_MARKER = 'id="device_secret"'

_PASSWORD_ANCHOR = (
    '                    <input type="password" id="password" name="password">\n'
    "                </p>\n"
    '                <p style="text-align: center;">\n'
    '                    <input type="submit" value="Connect" id="button" '
    'data-lang-value="connect">\n'
    "                </p>"
)
_PASSWORD_REPLACEMENT = (
    '                    <input type="password" id="password" name="password">\n'
    "                </p>\n"
    "                <p>\n"
    '                    <label for="device_secret" data-lang="device_secret">'
    "Device secret (optional):</label>\n"
    '                    <input type="password" id="device_secret" name="device_secret" '
    'autocomplete="off" placeholder="DLC device_secret">\n'
    "                </p>\n"
    "                <p>\n"
    '                    <label for="server_host" data-lang="server_host">'
    "Server host (optional):</label>\n"
    '                    <input type="text" id="server_host" name="server_host" '
    'autocomplete="off" placeholder="e.g. chat.donglicao.com">\n'
    "                </p>\n"
    '                <p style="text-align: center;">\n'
    '                    <input type="submit" value="Connect" id="button" '
    'data-lang-value="connect">\n'
    "                </p>"
)
_OLD_PAYLOAD = (
    "            const payload = {\n"
    "                ssid: ssid.value,\n"
    "                password: document.getElementById('password').value\n"
    "            };"
)
_NEW_PAYLOAD = (
    "            const payload = {\n"
    "                ssid: ssid.value,\n"
    "                password: document.getElementById('password').value,\n"
    "                device_secret: document.getElementById('device_secret').value,\n"
    "                server_host: document.getElementById('server_host').value\n"
    "            };"
)


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def _has_cc() -> bool:
    return TARGET_CC.is_file() and CC_MARKER in _read(TARGET_CC)


def _has_html() -> bool:
    if not TARGET_HTML.is_file():
        return False
    html = _read(TARGET_HTML)
    return HTML_MARKER in html and "device_secret:" in html


def _patch_bin() -> str | None:
    found = shutil.which("patch")
    if found:
        return found
    git_patch = Path(r"C:\Program Files\Git\usr\bin\patch.exe")
    return str(git_patch) if git_patch.is_file() else None


def _run_external_patch(patch_body: str) -> None:
    tmp = COMPONENT / ".lima_softap_dlc.patch"
    tmp.write_text(patch_body, encoding="utf-8", newline="\n")
    try:
        git = subprocess.run(
            ["git", "apply", "--whitespace=nowarn", str(tmp)],
            cwd=COMPONENT,
            capture_output=True,
            text=True,
        )
        if git.returncode == 0:
            return
        patch = _patch_bin()
        if patch:
            subprocess.run(
                [patch, "-p1", "--forward", "--batch", f"--input={tmp}"],
                cwd=COMPONENT,
                capture_output=True,
                text=True,
                check=False,
            )
    finally:
        tmp.unlink(missing_ok=True)


def _html_ok(text: str) -> bool:
    return HTML_MARKER in text and "device_secret:" in text


def _inject_html_fields() -> None:
    """Surgical HTML edit when patch cannot apply (partial/corrupt tree)."""
    text = _read(TARGET_HTML)
    text = text.replace('id="device_secret_x"', HTML_MARKER)
    text = text.replace("device_secret_x:", "device_secret:")
    if not _html_ok(text):
        if _PASSWORD_ANCHOR in text:
            text = text.replace(_PASSWORD_ANCHOR, _PASSWORD_REPLACEMENT, 1)
        if _OLD_PAYLOAD in text:
            text = text.replace(_OLD_PAYLOAD, _NEW_PAYLOAD, 1)
    TARGET_HTML.write_text(text, encoding="utf-8", newline="\n")


def _apply_needed() -> None:
    if not PATCH.is_file():
        print(f"missing patch file {PATCH}", file=sys.stderr)
        raise SystemExit(1)
    full = _read(PATCH)
    if "--- a/assets/wifi_configuration.html" not in full:
        print("patch missing HTML hunk", file=sys.stderr)
        raise SystemExit(1)
    cc_part, html_part = full.split("--- a/assets/wifi_configuration.html", 1)
    html_part = "--- a/assets/wifi_configuration.html" + html_part
    if not _has_cc():
        _run_external_patch(cc_part if cc_part.strip() else full)
    if not _has_html():
        _run_external_patch(html_part)
        if not _has_html():
            _inject_html_fields()
    if not (_has_cc() and _has_html()):
        print("SoftAP DLC patch apply failed (CC/HTML markers missing)", file=sys.stderr)
        raise SystemExit(1)


def ensure_softap_dlc_patch(*, require_component: bool = True) -> None:
    if not TARGET_CC.is_file():
        msg = f"missing {TARGET_CC.relative_to(ROOT)} — run idf.py reconfigure/set-target first"
        if require_component:
            print(msg, file=sys.stderr)
            raise SystemExit(1)
        print(f"skip SoftAP DLC patch: {msg}", file=sys.stderr)
        return
    if not TARGET_HTML.is_file():
        msg = f"missing {TARGET_HTML.relative_to(ROOT)}"
        if require_component:
            print(msg, file=sys.stderr)
            raise SystemExit(1)
        print(f"skip SoftAP DLC patch: {msg}", file=sys.stderr)
        return
    if _has_cc() and _has_html():
        print("esp-wifi-connect SoftAP DLC patch already applied (CC+HTML).")
        return
    _apply_needed()
    print("Applied esp-wifi-connect SoftAP DLC patch (CC+HTML).")


if __name__ == "__main__":
    ensure_softap_dlc_patch(require_component="--optional" not in sys.argv)
