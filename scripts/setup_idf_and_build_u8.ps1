# Bootstrap ESP-IDF for U8 (xiaozhi) and build firmware/u8-xiaozhi.
# Matches GH Actions: esp-idf-ci-action v5.5.2 + target esp32s3.
#
# Usage (PowerShell, NOT Git Bash / MSYS):
#   cd D:\QWEN3.0\esp32S_XYZ
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup_idf_and_build_u8.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup_idf_and_build_u8.ps1 -BuildOnly
#
# Notes:
# - Do not run from Git Bash (install.ps1 rejects MSYS/Mingw).
# - Prefer Espressif Install Manager (eim) when available.

param(
    [string]$IdfVersion = "v5.5.2",
    [string]$Target = "esp32s3",
    [string]$InstallBase = "D:\zhugu-home\.espressif",
    [switch]$BuildOnly,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path $PSScriptRoot -Parent
$U8Dir = Join-Path $RepoRoot "firmware\u8-xiaozhi"
$Eim = Join-Path $InstallBase "tools\eim\eim-cli-windows-x64.exe"

function Assert-NotMsys {
    if ($env:MSYSTEM -or $env:MSYSTEM_PREFIX) {
        throw "Refuse to run under MSYS/Git Bash (MSYSTEM=$($env:MSYSTEM)). Use Windows PowerShell."
    }
}

function Invoke-EimInstall {
    if (-not (Test-Path $Eim)) {
        throw "eim CLI not found: $Eim — install from https://dl.espressif.com/dl/esp-idf/ or place eim-cli-windows-x64.exe there"
    }
    Write-Host "=== eim list (before) ==="
    & $Eim list 2>&1 | Write-Host

    Write-Host "=== eim install $IdfVersion target=$Target ==="
    # non-interactive install; recurse submodules; only esp32s3 tools
    # Note: do not pass flags not supported by this eim-cli build.
    & $Eim install `
        --non-interactive true `
        --idf-versions $IdfVersion `
        --target $Target `
        --path $InstallBase `
        --recurse-submodules true `
        --install-all-prerequisites true `
        -v
    if ($LASTEXITCODE -ne 0) {
        throw "eim install failed exit=$LASTEXITCODE"
    }

    Write-Host "=== eim list (after) ==="
    & $Eim list 2>&1 | Write-Host
}

function Invoke-U8Build {
    if (-not (Test-Path $U8Dir)) {
        throw "U8 dir missing: $U8Dir"
    }
    if (-not (Test-Path $Eim)) {
        throw "eim CLI not found for run: $Eim"
    }

    Write-Host "=== idf.py --version via eim run ==="
    & $Eim run "idf.py --version" $IdfVersion
    if ($LASTEXITCODE -ne 0) {
        # try without v prefix / with path discovery
        Write-Host "retry eim run with bare version name..."
        & $Eim run "idf.py --version"
    }

    Write-Host "=== set-target $Target + build in $U8Dir ==="
    # eim run executes a single command string in IDF context
    $cmd = "cd /d `"$U8Dir`" && idf.py set-target $Target && idf.py build"
    & $Eim run $cmd $IdfVersion
    if ($LASTEXITCODE -ne 0) {
        throw "U8 build failed exit=$LASTEXITCODE"
    }
    Write-Host "=== U8 build OK ==="
    Get-ChildItem (Join-Path $U8Dir "build\*.bin") -ErrorAction SilentlyContinue | Select-Object -First 10 Name, Length
}

Assert-NotMsys

if (-not $BuildOnly) {
    Invoke-EimInstall
}

if (-not $SkipBuild) {
    Invoke-U8Build
}

Write-Host "Done. For daily builds after install:"
Write-Host "  eim run `"cd /d $U8Dir && idf.py build`" $IdfVersion"
