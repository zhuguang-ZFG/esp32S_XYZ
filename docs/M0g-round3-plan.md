# M0g 轮 3 实施计划：ES8311/ES7210 音频芯片核对

**创建日期**: 2026-05-15
**状态**: 已完成（带风险项接续）
**目标**: 完成 `docs/硬件核对报告.md` §1.3 ES8311 / §1.4 ES7210

## 0. 任务概述

### 0.1 目标
完成 ES8311（音频 Codec）和 ES7210（音频 ADC）的 L1/L2/L3 三级硬件核对。

### 0.2 前置条件检查
- [x] 轮 1 ESP32-S3 已完成
- [x] 轮 2 HR4988E 已完成
- [x] PADS `.txt` 源文件已到位
- [x] SCH PDF / BOM 已到位
- [ ] ES8311 datasheet（本仓库未取得，报告按 `[DX]` 登记）
- [ ] ES7210 datasheet（本仓库未取得，报告按 `[DX]` 登记）

### 0.3 数据源策略
1. **优先**: 官方 datasheet（everest-semi.com）
2. **备选**: ESP-IDF 组件源码反查
3. **兜底**: 标注 `[DX]` 数据源不可得

## 1. 实施步骤

### 步骤 1: 数据源获取

#### 1.1 尝试获取 ES8311 datasheet
- 搜索官方网站
- 搜索 ESP-IDF 组件文档
- 记录数据源状态

#### 1.2 尝试获取 ES7210 datasheet
- 同上

#### 1.3 备选方案
如果 datasheet 不可得：
- 从 ESP-IDF `espressif/es8311` 组件源码反查
- 从 ESP-IDF `espressif/es7210` 组件源码反查
- 标注 `[DX]` 或 `[D5]`（推断）

### 步骤 2: PADS 源文件核对

#### 2.1 ES8311 相关网络
从 `DLC_Motor_Control_P1_V1.0_260513.txt` 的 `*SIGNAL*` 段提取：
- `IO38_I2S_MCK` -> U8.31 -> IO38
- `IO45_I2S_DO` -> U8.26 -> IO45
- `IO12_I2S_DI` -> U8.20 -> IO12
- `IO13_I2S_WS` -> U8.21 -> IO13
- `IO14_I2S_BCK` -> U8.22 -> IO14
- `PA_EN` -> U8.32 -> IO39
- `IO1_I2C_SDA` -> U8.39 -> IO1
- `IO2_I2C_SCL` -> U8.38 -> IO2

#### 2.2 ES7210 相关网络
- 同样从 PADS `.txt` 提取
- 核对 I2C 地址脚
- 核对 I2S 连接

### 步骤 3: L1 引脚层核对

#### 3.1 ES8311
- [x] I2C 地址（固件使用 `ES8311_CODEC_DEFAULT_ADDR`，待组件默认值和实机 scan 确认）
- [x] I2S 主从模式（MCLK/BCLK/LRCK 方向）
- [x] SDIN/SDOUT 方向
- [x] 电源引脚（PVDD/DVDD）
- [x] 地引脚（DGND/AGND/EP）

#### 3.2 ES7210
- [x] I2C 地址（PADS 显示 `AD0=1, AD1=0`，固件使用 `0x82`，待实机 scan 确认）
- [x] I2S 主从模式
- [x] SDOUT 方向
- [x] 电源引脚
- [x] 地引脚

### 步骤 4: L2 配置层核对

#### 4.1 ES8311
- [x] MCLK 频率配置（固件 `I2S_MCLK_MULTIPLE_256`）
- [x] BCLK/LRCK 时钟关系
- [ ] 上电复位时序（缺 datasheet，登记风险）
- [x] PA_EN 使能时序（固件路径已核，实机爆音登记风险）
- [ ] 去耦电容配置（缺 datasheet，登记风险）

#### 4.2 ES7210
- [x] MCLK 频率配置（固件 `I2S_MCLK_MULTIPLE_256`）
- [x] BCLK/LRCK 时钟关系
- [ ] 上电复位时序（电源树未完整追完，登记风险）
- [ ] 去耦电容配置（缺 datasheet，登记风险）

### 步骤 5: L3 应力层核对

#### 5.1 ES8311
- [ ] 工作电压范围（缺 datasheet，登记风险）
- [ ] 工作电流（缺 datasheet）
- [ ] 工作温度范围（缺 datasheet）
- [ ] 输出驱动能力（缺 datasheet）

#### 5.2 ES7210
- [ ] 工作电压范围（缺 datasheet，登记风险）
- [ ] 工作电流（缺 datasheet）
- [ ] 工作温度范围（缺 datasheet）
- [ ] 输入阻抗（缺 datasheet）

### 步骤 6: 风险识别与登记

#### 6.1 检查项
- [x] I2C 地址冲突
- [x] I2S 时钟方向错误
- [x] 电源域不匹配
- [x] 去耦电容不足
- [x] 上电时序违反

#### 6.2 风险登记
- 新风险进入 §A.1 风险表：`R-009`~`R-013`
- 格式：`R-00X | §1.3/§1.4 | 位置 | 等级 | 摘要 | 状态`

### 步骤 7: 文档更新

#### 7.1 更新 `docs/硬件核对报告.md`
- 新增 §1.3 ES8311
- 新增 §1.4 ES7210
- 更新 §A.1 风险表
- 更新 §A.2 待覆盖清单
- 更新 §B 修订历史

#### 7.2 验证
- [x] 每个结论有数据源标注
- [x] 风险表格式正确
- [x] 章节编号连续

### 步骤 8: 提交与推送

#### 8.1 Git 操作
```bash
rtk git add docs/硬件核对报告.md docs/M0g-round3-plan.md
rtk git commit -m "M0g round 3: ES8311/ES7210 audio chip verification"
rtk git push origin main
```

#### 8.2 验证
- [ ] commit 成功
- [ ] push 成功
- [ ] GitHub 可见

## 2. 文件清单

### 2.1 将修改的文件
- `docs/硬件核对报告.md` - 新增 §1.3 / §1.4 / 更新 §A / §B

### 2.2 将创建的文件
- `docs/M0g-round3-plan.md` - 本计划文件（临时）

### 2.3 将读取的文件
- `docs/DLC_Motor_Control_P1_V1.0_260513.txt`
- `docs/DLC_Motor_Control_P1_V1.0_260513SCH.pdf`
- `docs/DLC_Motor_Control_P1_V1.0_260513BOM.xls`
- `docs/硬件连接与GPIO分配说明.md`
- `docs/硬件核对报告.md`

## 3. 验证命令

### 3.1 文档检查
```bash
# 检查文档格式
rtk git diff --check

# 检查章节编号
grep "^## " docs/硬件核对报告.md

# 检查风险表
grep "^| R-" docs/硬件核对报告.md
```

### 3.2 提交检查
```bash
# 检查暂存状态
rtk git status --short

# 检查 commit 历史
rtk git log --oneline -1
```

## 4. 风险与回退

### 4.1 风险
| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| datasheet 不可得 | 高 | 中 | 用 ESP-IDF 源码反查 + 标注 `[DX]` |
| I2C 地址冲突 | 中 | 高 | 登记风险，建议硬件改版 |
| 时钟方向错误 | 中 | 高 | 登记风险，建议固件修正 |
| 电源域不匹配 | 低 | 极高 | 立即登记 L1 风险 |

### 4.2 回退方式
如果核对过程中发现阻塞问题：
1. 保存当前进度到 plan 文件
2. 标注 `[WAIT_HW]` 或 `[DX]`
3. 不强行编造参数
4. commit 当前进度 + 问题说明

## 5. 完成判定

- [x] §1.3 ES8311 完成 L1/L2/L3 核对（未闭环项已登记风险）
- [x] §1.4 ES7210 完成 L1/L2/L3 核对（未闭环项已登记风险）
- [x] 每个结论有数据源标注
- [x] 新风险进入 §A.1 风险表
- [ ] 文档 commit + push（本轮未执行，等待用户明确允许）
- [x] 更新 `docs/接续指令.md`

## 6. 下一步

完成本轮后：
- 轮 4: §1.5 摄像头 + 2V8 LDO
- 或并行启动 M0a JSON Schema（如果 §1 章节已足够稳定）

## 7. 修订记录

- 2026-05-15: 初始版本，基于 M0g 轮 2 完成状态
- 2026-05-15: 完成轮 3 文档核对，补齐 R-009~R-013；commit/push 待用户确认
