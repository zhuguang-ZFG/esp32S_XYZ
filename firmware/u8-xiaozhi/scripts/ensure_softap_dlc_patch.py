#!/usr/bin/env python3
"""Ensure SoftAP /submit persists device_secret (DLC patch on esp-wifi-connect).

Run after idf.py reconfigure / set-target so managed_components exists.
Idempotent: skips when SaveDlcProvisioningFields is already present.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / "managed_components/78__esp-wifi-connect/wifi_configuration_ap.cc"
PATCH = ROOT / "patches/esp-wifi-connect-softap-dlc.patch"
MARKER = "SaveDlcProvisioningFields"


def ensure_softap_dlc_patch(*, require_component: bool = True) -> None:
    if not TARGET.is_file():
        msg = f"missing {TARGET.relative_to(ROOT)} — run idf.py reconfigure/set-target first"
        if require_component:
            print(msg, file=sys.stderr)
            raise SystemExit(1)
        print(f"skip SoftAP DLC patch: {msg}", file=sys.stderr)
        return
    text = TARGET.read_text(encoding="utf-8", errors="replace")
    if MARKER in text:
        print("esp-wifi-connect SoftAP DLC patch already applied.")
        return
    if not PATCH.is_file():
        print(f"missing patch file {PATCH}", file=sys.stderr)
        raise SystemExit(1)
    component_dir = TARGET.parent
    proc = subprocess.run(
        ["git", "apply", str(PATCH)],
        cwd=component_dir,
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        proc = subprocess.run(
            ["patch", "-p1", "--forward", f"--input={PATCH}"],
            cwd=component_dir,
            capture_output=True,
            text=True,
        )
    if proc.returncode != 0:
        print(proc.stdout or "")
        print(proc.stderr or "", file=sys.stderr)
        print("SoftAP DLC patch apply failed", file=sys.stderr)
        raise SystemExit(1)
    if MARKER not in TARGET.read_text(encoding="utf-8", errors="replace"):
        print("patch applied but marker still missing", file=sys.stderr)
        raise SystemExit(1)
    print("Applied esp-wifi-connect SoftAP DLC patch.")


if __name__ == "__main__":
    ensure_softap_dlc_patch(require_component="--optional" not in sys.argv)
