---
inclusion: auto
---

# 中文交流规范 - Chinese Communication Standard

**版本**: v1.0  
**创建日期**: 2026-05-14  
**状态**: 生效中  
**优先级**: P0（全局强制）

## 1. 核心原则

**全程使用中文与用户交流**，这是本项目的基本要求。

### 1.1 适用范围

- ✅ 所有与用户的对话
- ✅ 所有提示信息
- ✅ 所有确认询问
- ✅ 所有错误提示
- ✅ 所有进度报告
- ✅ 所有总结说明

### 1.2 例外情况

以下内容可以使用英文:

- ❌ 代码注释（可选中文，但英文也可接受）
- ❌ 变量名、函数名、类名（必须英文）
- ❌ Git commit message（建议英文，便于国际协作）
- ❌ 技术文档中的专业术语（可保留英文原文，但需附中文解释）

## 2. 交流风格

### 2.1 语言特点

- **简洁明了**: 避免冗长的解释，直击要点
- **专业准确**: 使用正确的技术术语
- **友好亲切**: 保持温和、支持性的语气
- **结构清晰**: 使用列表、标题等组织信息

### 2.2 避免的表达

❌ 不要使用:
- 过于正式的书面语（例如："兹有"、"特此"）
- 过于口语化的网络用语（例如："666"、"yyds"）
- 英文缩写（除非是广为人知的技术术语，如 API、HTTP）
- 中英文混杂的句子（例如："这个 feature 很 nice"）

✅ 应该使用:
- 清晰的现代汉语
- 必要时保留英文技术术语，但加中文解释
- 例如："API（应用程序接口）"、"HTTP 协议"

## 3. 技术术语处理

### 3.1 常用术语对照表

| 英文术语 | 中文翻译 | 使用建议 |
|---------|---------|---------|
| Feature | 功能 | 优先使用中文 |
| Bug | 缺陷/错误 | 优先使用中文 |
| Spec | 规范/规格说明 | 可保留英文，首次使用时注明 |
| Task | 任务 | 优先使用中文 |
| Milestone | 里程碑 | 优先使用中文 |
| Capability | 能力 | 优先使用中文 |
| Edge | 边界 | 优先使用中文 |
| Client | 客户端 | 优先使用中文 |
| Server | 服务器 | 优先使用中文 |
| Device | 设备 | 优先使用中文 |
| Firmware | 固件 | 优先使用中文 |

### 3.2 专有名词保留

以下专有名词保留英文:

- **项目名称**: esp32S_XYZ
- **组件名称**: BusinessServer, DeviceServer, U1, U8
- **协议名称**: Edge-A, Edge-B, Edge-C, Edge-D
- **技术栈**: ESP32-S3, Grbl, uni-app, Spring Boot
- **文件名**: README.md, tasks.md, requirements.md

## 4. 文档编写规范

### 4.1 Markdown 文档

- **标题**: 使用中文
- **正文**: 使用中文
- **代码块**: 代码本身用英文，注释可用中文
- **链接文字**: 使用中文

示例:

```markdown
# 用户认证功能

本功能实现了基于 JWT 的用户认证。

## 实现步骤

1. 用户提交登录凭证
2. 服务器验证凭证
3. 返回 JWT token

\`\`\`java
// 验证用户凭证
public boolean validateCredentials(String username, String password) {
    // 实现逻辑
}
\`\`\`
```

### 4.2 代码注释

代码注释**推荐使用中文**，但英文也可接受:

```java
// ✅ 推荐: 中文注释
// 验证用户是否有权访问该设备
public boolean hasDeviceAccess(String userId, String deviceId) {
    // ...
}

// ✅ 可接受: 英文注释
// Validate if user has access to the device
public boolean hasDeviceAccess(String userId, String deviceId) {
    // ...
}
```

### 4.3 Commit Message

Git commit message **建议使用英文**，便于国际协作:

```bash
# ✅ 推荐
git commit -m "feat(auth): add JWT token validation"

# ✅ 可接受（如果团队全是中文用户）
git commit -m "feat(auth): 添加 JWT token 验证"
```

但 commit body 可以使用中文详细说明:

```bash
git commit -m "feat(auth): add JWT token validation

添加了 JWT token 的验证逻辑:
- 实现了 token 解析
- 添加了过期时间检查
- 增加了签名验证

相关文档: docs/架构定稿-v2.md §11"
```

## 5. 交互场景示例

### 5.1 询问用户选择

❌ 错误示例:
```
What would you like to do?
1. Create a new spec
2. Update existing spec
```

✅ 正确示例:
```
您想要做什么?
1. 创建新的 spec
2. 更新现有 spec
```

### 5.2 报告进度

❌ 错误示例:
```
Running tests... 13/13 passed
```

✅ 正确示例:
```
正在运行测试... 13/13 通过
```

### 5.3 错误提示

❌ 错误示例:
```
Error: File not found
```

✅ 正确示例:
```
错误: 文件未找到
```

### 5.4 确认操作

❌ 错误示例:
```
Are you sure you want to delete this file?
```

✅ 正确示例:
```
确定要删除这个文件吗?
```

## 6. 特殊情况处理

### 6.1 技术文档引用

当引用英文技术文档时，可以保留原文并附中文说明:

```markdown
根据 Superpowers 原则（"先验证后实现"），我们需要...
```

### 6.2 代码示例说明

代码示例的说明文字使用中文:

```markdown
以下代码实现了设备状态查询:

\`\`\`java
public DeviceStatus getDeviceStatus(String deviceId) {
    // 实现逻辑
}
\`\`\`

该方法返回设备的当前状态。
```

### 6.3 混合内容

当内容中既有中文又有英文技术术语时，保持自然流畅:

```markdown
BusinessServer 负责账号与鉴权，DeviceServer 负责设备会话与实时流。
```

## 7. 质量检查清单

在提交任何用户可见的内容前，检查:

- [ ] 所有面向用户的文字都使用中文
- [ ] 技术术语使用恰当（该中文的中文，该保留英文的保留）
- [ ] 语言简洁明了，没有冗余
- [ ] 语气友好专业
- [ ] 标点符号使用正确（中文标点）
- [ ] 没有中英文混杂的句子

## 8. 与其他规范的关系

本规范与以下规范协同工作:

- **code-review.md**: 代码审查时的交流使用中文
- **code-simplifier.md**: 重构说明使用中文
- **milestone-acceptance.md**: 里程碑验收报告使用中文
- **所有 steering 文件**: 内容使用中文（除代码示例外）

## 9. 执行要求

### 9.1 强制性

本规范是 **P0 级别强制要求**，所有与用户的交互都必须遵循。

### 9.2 检查点

- 每次与用户对话前，确认使用中文
- 每次生成文档前，确认内容为中文
- 每次提示用户前，确认提示语为中文

### 9.3 违规处理

如果发现使用英文与用户交流（代码和技术术语除外），应:

1. 立即切换回中文
2. 向用户道歉
3. 重新用中文表达

## 10. 示例对话

### 示例 1: 创建 Spec

❌ 错误:
```
I'll help you create a new spec. What feature do you want to implement?
```

✅ 正确:
```
我来帮您创建一个新的 spec。您想要实现什么功能?
```

### 示例 2: 执行任务

❌ 错误:
```
Starting task execution...
Task 1: Completed
Task 2: In progress
```

✅ 正确:
```
开始执行任务...
任务 1: 已完成
任务 2: 进行中
```

### 示例 3: 报告错误

❌ 错误:
```
Error: GPIO conflict detected at line 45
```

✅ 正确:
```
错误: 在第 45 行检测到 GPIO 冲突
```

## 11. 修订记录

- 2026-05-14: 初始版本，建立中文交流规范

---

**重要提示**: 本规范是全局自动加载（`inclusion: auto`），所有 AI 助手在与用户交互时都必须遵循。
