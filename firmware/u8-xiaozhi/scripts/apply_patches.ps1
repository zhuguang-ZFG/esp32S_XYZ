# Apply tracked DLC patches to managed_components (after idf.py set-target / reconfigure).
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ComponentDir = Join-Path $Root "managed_components/78__esp-wifi-connect"
$Target = Join-Path $ComponentDir "wifi_configuration_ap.cc"
$Patch = Join-Path $Root "patches/esp-wifi-connect-softap-dlc.patch"

if (-not (Test-Path $Target)) {
    Write-Error "Missing $Target — run idf.py reconfigure first to fetch esp-wifi-connect."
}
if (Select-String -Path $Target -Pattern "SaveDlcProvisioningFields" -Quiet) {
    Write-Host "esp-wifi-connect SoftAP DLC patch already applied."
    exit 0
}

Push-Location $ComponentDir
try {
    git apply --check $Patch 2>$null
    if ($LASTEXITCODE -ne 0) {
        patch -p1 --forward --input $Patch
    } else {
        git apply $Patch
    }
    if ($LASTEXITCODE -ne 0) { throw "patch failed (exit $LASTEXITCODE)" }
    Write-Host "Applied esp-wifi-connect SoftAP DLC patch."
} finally {
    Pop-Location
}
