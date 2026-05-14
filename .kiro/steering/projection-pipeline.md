---
name: projection-pipeline
description: 投影管线 Skill——write_text/draw_generated 从语义到 run_path 的四阶转换，每阶有验收
type: steering
priority: P1
inclusion: auto
---

# 投影管线 Skill - Projection Pipeline

**版本**: v1.0
**创建日期**: 2026-05-15
**状态**: 生效中
**基准**: `docs/架构定稿-v2.md §10, §10bis, §10ter`

## §1 核心原则

```
write_text / draw_generated → 四阶投影管线 → run_path segments
每一阶有独立测试，最后一级是安全裁决。
投影出错不得静默裁剪或回退到预置素材。
```

## §2 四阶管线

| 阶 | 名称 | 输入 | 输出 | 实现位置 |
|----|------|------|------|----------|
| 1 | text_layout | text + font + canvas | glyph sequence | `manager-api/.../service/projection/`（M3 待建） |
| 2 | glyph_resolve | glyph + font | bezier outlines | `manager-api/.../service/glyph/`（M3 待建） |
| 3 | discretize | bezier outlines | path segments | `manager-api/.../service/projection/`（M3 待建） |
| 4 | canvas_transform | segments + workspace_mm | final segments | `manager-api/.../service/projection/`（M3 待建） |

### 2.1 离散化精度

```
弦高公差：≤ 0.05mm
单段长度：≤ 5mm
任何超出此精度的路径必须在 discretize 阶段报错。
```

### 2.2 工作区裁剪

```
强制裁剪：超出 writable_area 的路径点直接拒绝任务
禁止：静默裁剪（把超出部分悄悄去掉）
错误码：E_OUT_OF_RANGE
```

## §3 draw_generated 特殊约束

### 3.1 输入校验

| 输入类型 | 允许条件 | 拒绝条件 |
|----------|----------|----------|
| SVG | 单线、无填充、黑白 | 多填充、彩色、复杂路径 |
| 位图 | 可二值化、可矢量化、可单线化 | 照片级图像、不可矢量化 |
| 生成图 | 可按 workspace 等比缩放 | 超节点数/总长度/预计耗时上限 |

### 3.2 AI 重新生成

```
不合格生成结果 → AI 重新生成（最多 3 次）
3 次后仍不合格 → 返回 E_INVALID_DRAWING
不允许：自动回退到预置素材（starter_*）
```

### 3.3 尺寸自适应

```
自动缩放：保持纵横比，等比缩放到 writable_area
不能：拉伸变形以填满画布
用户指示："大一点/小一点/居中/靠左" → 改变布局参数
```

## §4 验收标准

### 4.1 各阶独立测试（M3 实现时创建，当前均不存在）

测试文件（待建）：
```
tests/test_text_layout.py       # text + font → glyph sequence
tests/test_glyph_resolve.py     # glyph → bezier outlines
tests/test_discretize.py        # bezier → segments（精度 ≤ 0.05mm）
tests/test_canvas_transform.py  # segments → workspace 适配
tests/test_safety_validator.py  # 前置裁决（§10bis）
```

### 4.2 端到端验收

```
输入："你好" + kai_basic_v1 + 100mm×50mm
输出：run_path segments
验收：segments 含正确的 PATH_BEGIN/PATH_SEG/PATH_END 序列
```

## §5 验收命令

```bash
# 投影管线测试（M3 实现后激活）
# python -m pytest tests/test_projection_*.py -v  [WAIT_ENV: 测试文件待 M3 创建]

# 精度检查（M3 实现后激活）
# rg "chord_tolerance|discretize" server/xiaozhi-esp32-server/main/manager-api/src/main/java  [WAIT_ENV]

# 内容审核集成（M3 实现后激活）
# rg "content_audit|E_CONTENT_BLOCKED" server/xiaozhi-esp32-server/main/manager-api  [WAIT_ENV]
```

## §6 修订记录

- 2026-05-15：初始版本，基于架构定稿-v2 §10/§10bis/§10ter
