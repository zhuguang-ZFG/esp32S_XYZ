# Apply SoftAP DLC patch then build (run from firmware/u8-xiaozhi with IDF env loaded).
$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)
python scripts/ensure_softap_dlc_patch.py
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
idf.py @args
