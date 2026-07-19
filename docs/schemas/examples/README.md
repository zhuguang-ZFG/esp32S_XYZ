# 高层路由示例

本目录提供若干高层 `motion_task` 示例，用于说明不同 `route_policy` 与 `capability` 的组合。所有示例均符合 `edge_c/motion_task.schema.json`。

| 文件 | 路由角色 | 能力 | 说明 |
|------|----------|------|------|
| `device_control.json` | `device_control` | `home` | 简单控制指令（归位、暂停、恢复、停止、获取设备信息），直接派发到设备执行，不走 `run_path`。 |
| `device_draw.json` | `device_draw` | `run_path` | 需要 AI 模型生成图像并转换为矢量路径，`prompt` 为自然语言，使用 `image_then_vector` 策略。 |
| `device_vector.json` | `device_vector` | `run_path` | SVG 路径直接作为输入，不需要 AI 模型，使用 `svg_vector` 策略。 |
| `device_write.json` | `device_write` | `run_path` | 预定义路径写字任务，不需要 AI 模型，`text` 由服务端渲染为路径，使用 `deterministic` 策略。 |
