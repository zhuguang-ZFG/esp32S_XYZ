# ui-template.md — v2 小程序 UI 模板规范

> 基准：`docs/架构定稿-v2.md` §8bis
> 创建日期：2026-05-14
> 状态：active，所有 v2 客户端 PR 必须对齐本模板

## 1. 组件库

使用现有项目统一组件库 **`wot-design-uni`**（`wd-*` 组件族），不引入额外 UI 库。

| 组件 | 用途 |
|------|------|
| `wd-button` | 主要操作按钮 |
| `wd-input` | 文本输入 |
| `wd-loading` | 加载中状态 |
| `wd-popup` | 底部弹出面板 |
| `wd-message-box` | 确认/提示弹窗 |
| `wd-icon` | 图标 |
| `wd-navbar` | 自定义导航栏 |
| `wd-status-tip` | 空状态/错误/网络异常提示 |

## 2. 色板

沿用现有 `device/index.vue` 的配色习惯：

| 用途 | 色值 | UnoCSS |
|------|------|--------|
| 主色（品牌蓝） | `#336cff` | `text-[#336cff]`, `bg-[#336cff]` |
| 背景白 | `#fbfbfb` | `bg-[#fbfbfb]` |
| 卡片白 | `#ffffff` | `bg-white` |
| 文字主色 | `#333333` | `text-[#333]` |
| 文字次色 | `#999999` | `text-[#999]` |
| 成功绿 | `#07c160` | `text-[#07c160]` |
| 警告橙 | `#fa9b21` | `text-[#fa9b21]` |
| 错误红 | `#ee0a24` | `text-[#ee0a24]` |
| 分隔线 | `#f5f5f5` | `bg-[#f5f5f5]` |

## 3. 字号体系

基于微信小程序 **rpx** 单位（750rpx = 屏幕宽）：

| 用途 | 字号 | UnoCSS |
|------|------|--------|
| 页面标题 | `36rpx` | `text-[36rpx]` |
| 卡片标题 | `32rpx` | `text-[32rpx]` |
| 正文 | `28rpx` | `text-[28rpx]` |
| 辅助文字 | `24rpx` | `text-[24rpx]` |
| 小标签 | `20rpx` | `text-[20rpx]` |

## 4. 按钮规范

| 变体 | 样式 | 用途 |
|------|------|------|
| 主按钮 | `wd-button type="primary" block round` | 页面主操作（归零、提交任务） |
| 次按钮 | `wd-button type="default" block round` | 次要操作（取消、返回） |
| 文字按钮 | `wd-button type="text"` | 轻量操作（刷新、查看更多） |
| 加载态 | `loading` prop | 提交中禁用双击 |

按钮间距：垂直方向 `20rpx`，水平居中时使用 `width: 80%` 或 `block`。

## 5. 卡片规范

状态卡片用于设备详情页展示任务/设备状态：

```html
<view class="status-card rounded-[16rpx] bg-white p-[24rpx] mb-[20rpx]">
  <view class="flex justify-between items-center">
    <text class="text-[28rpx] font-medium">{{ title }}</text>
    <text class="text-[24rpx]" :class="statusColor">{{ statusText }}</text>
  </view>
  <view class="text-[24rpx] text-[#999] mt-[8rpx]" v-if="subtitle">
    {{ subtitle }}
  </view>
</view>
```

状态颜色映射：
- `IDLE` / `done` → 绿色 `#07c160`
- `RUNNING` / `HOMING` / `running` → 蓝色 `#336cff`
- `ALARM` / `ERROR` / `failed` / `ESTOP` → 红色 `#ee0a24`
- `disconnected` → 灰色 `#999999`

## 6. 布局规则

- **页面容器**：`<view class="page-container min-h-screen bg-[#fbfbfb] p-[20rpx]">`
- **安全区适配**：每个页面头部加 `<!-- #ifdef MP-WEIXIN -->` safe-area inset 处理
- **列表页**：使用 `scroll-view` + `v-for` 渲染卡片列表
- **详情页**：从上到下垂直排列卡片区域
- **空状态**：`wd-status-tip image="content" tip="暂无设备"` 居中显示
- **导航栏**：每个 v2 页面顶部使用自定义 `wd-navbar`，左侧有返回按钮，标题为页面中文名
- **加载状态**：页面级 loading 用 `wd-loading` 居中；操作级 loading 用按钮 `loading` prop

## 7. 导航流

```
小程序入口
    │
    ▼
/login (微信 code 登录)
    │ JWT token → uni.setStorageSync('token', ...)
    ▼
/device-list (已绑定设备列表)
    │ 点击设备卡片
    ▼
/device-detail (设备详情 + 实时状态)
    │
    ├── 点「归零」→ POST /api/v1/devices/{id}/tasks {capability:"home"}
    ├── WSS 连接 /ws/v1/client → 订阅 device:{id} → 实时接收 job_status 事件
    └── 状态卡片显示最新任务状态 (IDLE/RUNNING/DONE/FAILED)
```

## 8. 页面路由配置

在 `src/pages.json` 的 `pages` 数组中追加：

```json
{ "path": "pages/v2/login/index", "type": "page", "layout": "default",
  "style": { "navigationStyle": "custom", "navigationBarTitleText": "DLC 登录" } },
{ "path": "pages/v2/device-list/index", "type": "page", "layout": "default",
  "style": { "navigationStyle": "custom", "navigationBarTitleText": "我的设备" } },
{ "path": "pages/v2/device-detail/index", "type": "page", "layout": "default",
  "style": { "navigationStyle": "custom", "navigationBarTitleText": "设备详情" } }
```

## 9. i18n 命名约定

v2 页面专用 key 前缀 `v2.login.*`, `v2.deviceList.*`, `v2.deviceDetail.*`。

示例（先加中文，其他语言文件同步追加同 key）：

```typescript
// zh_CN.ts
'v2.login.title': 'DLC 写字机',
'v2.login.wxLogin': '微信一键登录',
'v2.login.privacy': '登录即同意《用户协议》和《隐私政策》',
'v2.deviceList.title': '我的设备',
'v2.deviceList.empty': '暂无绑定设备',
'v2.deviceList.addDevice': '添加设备',
'v2.deviceDetail.title': '设备详情',
'v2.deviceDetail.homeButton': '归零',
'v2.deviceDetail.status': '设备状态',
'v2.deviceDetail.disconnected': '未连接',
'v2.deviceDetail.taskStatus': '最近任务',
```

## 10. 不在本模板范围内

- 具体像素级设计稿（由设计师在进入 M3 前补）
- 动画/过渡效果（M3 阶段统一）
- 暗色模式（暂不规划）
