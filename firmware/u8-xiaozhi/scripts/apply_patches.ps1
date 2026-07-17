# Apply tracked DLC SoftAP patch (delegates to ensure_softap_dlc_patch.py).
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
python (Join-Path $PSScriptRoot "ensure_softap_dlc_patch.py")
exit $LASTEXITCODE
