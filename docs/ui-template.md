# ui-template.md — v2 小程序 UI 模板登记

> 基准：`docs/架构定稿-v2.md` §8bis.5
> 登记日期：2026-05-14
> 状态：active

## 模板仓库

| 字段 | 值 |
|------|-----|
| 名称 | unibest |
| 仓库 | https://github.com/unibest-tech/unibest |
| 版本 | main 分支，2026 年活跃维护 |
| 许可证 | MIT |
| 技术栈 | uni-app + Vue3 + TS + Vite5 + UnoCSS + wot-ui + z-paging + pinia + alova |

## 与现有 manager-mobile 的技术栈对齐

现有 `manager-mobile` 与 unibest 技术栈 **100% 重合**：

| 层 | unibest | manager-mobile |
|----|---------|---------------|
| 框架 | uni-app Vue3 | uni-app Vue3 |
| 语言 | TypeScript | TypeScript |
| 构建 | Vite5 | Vite5 |
| CSS | UnoCSS | UnoCSS |
| 组件库 | wot-ui | wot-ui |
| 分页 | z-paging | z-paging |
| 状态 | pinia | pinia |
| HTTP | alova | alova |
| Lint | ESLint | ESLint |
| 路由 | 约定式路由 | pages.json |
| 布局 | layout 系统 | layout 系统 |
| i18n | 内建多语言 | 内建多语言 |

**结论**：现有 manager-mobile 可视为 unibest 体系下的项目，无需迁移。后续 v2 页面严格在 unibest 的 layout/组件/routing 体系内开发。

## §8bis.2 四类场景走查

| 场景 | unibest 能力 | 状态 |
|------|-------------|------|
| §9 实时刷新列表/卡片 | z-paging + wot-ui 列表组件 | ✓ |
| §14bis 操作引导/向导 | wot-popup + wot-steps | ✓ |
| §10bis.7 安全二次确认 | wot-message-box confirm | ✓ |
| §10bis.8 错误态/空状态 | wot-status-tip | ✓ |

## 改造边界（§8bis.3）

### 允许（在模板组件之上做业务封装）

- 在 unibest 的 `default` layout 下新建 v2 页面路由
- 使用 wot-ui 组件 + UnoCSS 改色（主色 `#336cff`，见色板）
- 在 z-paging 组件上封装设备列表卡片
- 在 wot-navbar + wot-popup 上封装设备操作流程
- 使用现有 `@/api/` alova 封装 v2 业务接口

### 禁止

- 手写裸 `<view>` 替代 wot-ui 组件的页面骨架
- 从零写 Vue 页面不使用 layout 系统
- 私自引入其他 UI 库覆盖 wot-ui 主题
- 大幅重写 unibest 的 navigation/layout 结构

## UI 色板（从模板原生组件改色）

| 用途 | 色值 |
|------|------|
| 主色 | `#336cff` (品牌蓝，覆盖 wot-button primary) |
| 背景 | `#fbfbfb` |
| 卡片 | `#ffffff` |
| 成功 | `#07c160` |
| 警告 | `#fa9b21` |
| 错误 | `#ee0a24` |
| 文字主 | `#333333` |
| 文字次 | `#999999` |

## 替换记录 — 字体

| 位置 | 模板默认 | 替换为 | 原因 |
|------|---------|--------|------|
| 全局默认 | 系统字体栈 | PingFang SC / Microsoft YaHei（微信小程序默认） | 无需额外引入，rpx 自适应 |
| 等宽字体（日志区） | — | monospace（系统默认） | Edge-A WSS 日志对齐 |

## 替换记录 — 图标

| 位置 | 模板默认 | 替换为 | 原因 |
|------|---------|--------|------|
| 导航返回 | wot-navbar left-arrow | 保留默认 | 模板原生，无需替换 |
| 归零按钮 | — | wot-icon name="refresh" | 非模板原生，业务需要 |
| 添加设备 | wot-button icon="add" | 保留默认 | 模板原生 |
| 状态标签 | wot-tag | 保留默认（success/danger/primary/default） | 模板原生颜色映射 |

## 不与模板兼容的业务点

- unibest 无 IoT 设备管理页面 → 需在 template 上做业务卡片封装
- unibest 无 Edge-A WSS 实时日志组件 → 复用现有 `device/index.vue` 的 WSS 调试区模式
- unibest 无设备配网流程 → M5.1 时在 wot-popup + wot-steps 上封装

## 页面导航流（基于 unibest layout 系统）

```
/login (default layout, wot-button + wot-navbar)
  → /device-list (default layout, z-paging + wot-card)
    → /device-detail (default layout, wot-card + wot-navbar + WSS log)
```
