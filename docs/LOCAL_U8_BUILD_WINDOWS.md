# 本机 Windows 构建 U8（无板 / agent 友好）

对齐 GH：`firmware-u8-build`（IDF **v5.5.2** + **esp32s3**）与 `firmware-native-tests`。

## 前提

- **PowerShell**，不要 Git Bash/MSYS（`install.ps1` / `idf_tools` 会拒 MSYSTEM）。
- 本机推荐 IDF 树：`D:\zhugu-home\.espressif\v5.5.2\esp-idf`。
- `eim list` 可能仍显示 No versions — **不挡** 下方手工 PATH 脚本。

## 全量 set-target + build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\zhugu-home\.espressif\build_u8_with_local_idf.ps1
```

产物：`firmware/u8-xiaozhi/build/xiaozhi.bin`（及 bootloader / partition / ota_data / generated_assets）。

## 增量 build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\zhugu-home\.espressif\build_u8_only.ps1
```

## Native g++ 单测（对齐 CI job）

MinGW 推荐路径（scoop 大包易超时，可手解压）：

`D:\zhugu-home\mingw64\mingw64\bin\g++.exe`

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\zhugu-home\.espressif\run_native_firmware_tests.ps1
```

含：U8 protocol / OTA allowlist / MQTT hex、U1 JSON parser、Protocol.cpp 无 `E009`。  
U1 JSON **不要** `-I` 整棵 `Grbl_Esp32/src`（工程 `limits.h` 在 MinGW 上会盖掉系统头）。

## 仓内 eim 引导（可选）

```powershell
cd D:\QWEN3.0\esp32S_XYZ
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup_idf_and_build_u8.ps1
# 已装好后：
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup_idf_and_build_u8.ps1 -BuildOnly
```

## 无板边界

无 DUT 时只签 **host 编译 + native/static/契约**；不签烧录、HIL、纸路/BT 真机。  
OTA 仅可做「包与元数据准备」，不能声称已 OTA 验证。

## 踩坑摘要

| 现象 | 处理 |
|------|------|
| Git Bash `tar` 解压工具链残缺 | 用 `C:\Windows\System32\tar.exe` |
| `cannot execute 'as'` | 工具链解压不完整 |
| `ESP_ROM_ELF_DIR` | 设为 `...\tools\esp-rom-elfs\20241011\` |
| LVGL examples + `LV_USE_LIST=n` | `CONFIG_LV_BUILD_EXAMPLES=n` |
| `httpd_resp_send_err(..., 401)` | 用 `HTTPD_401_UNAUTHORIZED` |
