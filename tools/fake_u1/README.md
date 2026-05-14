# fake U1 - U1 固件仿真器

## 用途

在没有真实硬件的情况下，模拟 U1 固件的协议行为，支持 M1~M3 阶段的软件开发与单元测试。

## 功能特性

### 支持的命令

- `GET_STATUS` - 查询设备状态
- `HOME` - 归零
- `MOVE` - 移动到指定位置
- `PATH_BEGIN` / `PATH_SEG` / `PATH_END` - 路径执行
- `PAUSE` / `RESUME` - 暂停/恢复
- `STOP` - 受控停止
- `ESTOP` - 急停

### 状态机

完整实现 v2 §14 定义的状态机：

- `IDLE` - 空闲
- `HOMING` - 归零中
- `RUNNING` - 运行中
- `PAUSED` - 暂停
- `ALARM` - 报警
- `ERROR` - 错误
- `ESTOP` - 急停

### 错误注入

支持模拟各种异常场景：

- `E001` - 未归零
- `E005` - 限位触发
- `E006` - 归零失败
- `E008` - 急停触发

### 协议

- 传输：TCP（默认端口 7799）
- 帧格式：`@{json}\n`
- 响应类型：`ack` / `status` / `result` / `error`

## 安装

```bash
# 无需额外依赖，Python 3.7+ 即可
cd tools/fake_u1
```

## 使用方法

### 启动服务器

```bash
# 默认配置（127.0.0.1:7799）
python fake_u1.py

# 自定义地址和端口
python fake_u1.py --host 0.0.0.0 --port 8888

# 模拟响应延迟（毫秒）
python fake_u1.py --latency-ms 50

# 注入错误
python fake_u1.py --inject E001 E005

# 详细日志
python fake_u1.py -v
```

### 测试客户端

使用 `nc` (netcat) 或 `telnet` 测试：

```bash
# 连接到 fake U1
nc 127.0.0.1 7799

# 发送命令（注意 @ 前缀和 \n 结尾）
@{"cmd":"GET_STATUS","msg_id":"msg_001"}

# 响应示例
@{"type":"status","msg_id":"msg_001","state":"IDLE","homed":false,"position_mm":{"x":0.0,"y":0.0,"z":0.0},"at_home":false,"alarm_code":null,"error_code":null}
```

### Python 客户端示例

```python
import socket
import json

# 连接到 fake U1
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.connect(('127.0.0.1', 7799))

# 发送 GET_STATUS 命令
command = {
    'cmd': 'GET_STATUS',
    'msg_id': 'msg_001'
}
sock.sendall(f"@{json.dumps(command)}\n".encode('utf-8'))

# 接收响应
response = sock.recv(4096).decode('utf-8')
print(response)

sock.close()
```

## 运行测试

```bash
# 运行所有测试
python test_fake_u1.py -v

# 运行特定测试
python test_fake_u1.py TestFakeU1.test_home_success -v
```

## 命令示例

### GET_STATUS

```json
@{"cmd":"GET_STATUS","msg_id":"msg_001"}
```

响应：

```json
@{"type":"status","msg_id":"msg_001","state":"IDLE","homed":false,"position_mm":{"x":0.0,"y":0.0,"z":0.0},"at_home":false,"alarm_code":null,"error_code":null}
```

### HOME

```json
@{"cmd":"HOME","msg_id":"msg_002","task_id":"task_001"}
```

响应：

```json
@{"type":"ack","msg_id":"msg_002","task_id":"task_001","accepted":true}
```

### MOVE

```json
@{"cmd":"MOVE","msg_id":"msg_003","task_id":"task_002","target":{"x":50.0,"y":50.0,"z":20.0}}
```

响应：

```json
@{"type":"ack","msg_id":"msg_003","task_id":"task_002","accepted":true}
```

### PATH 执行

```json
@{"cmd":"PATH_BEGIN","msg_id":"msg_004","task_id":"task_003"}
@{"cmd":"PATH_SEG","msg_id":"msg_005","task_id":"task_003","segment":{"type":"M","x":10.0,"y":10.0,"z":10.0}}
@{"cmd":"PATH_SEG","msg_id":"msg_006","task_id":"task_003","segment":{"type":"L","x":20.0,"y":20.0,"z":10.0}}
@{"cmd":"PATH_END","msg_id":"msg_007","task_id":"task_003"}
```

### ESTOP

```json
@{"cmd":"ESTOP","msg_id":"msg_008","task_id":"task_004"}
```

响应：

```json
@{"type":"ack","msg_id":"msg_008","task_id":"task_004","accepted":true}
```

## 错误注入示例

### 模拟未归零错误

```bash
python fake_u1.py --inject E001
```

发送 MOVE 命令会返回：

```json
@{"type":"error","msg_id":"msg_003","task_id":"task_002","error_code":"E001","message":"未归零"}
```

### 模拟限位触发

```bash
python fake_u1.py --inject E005
```

发送 MOVE 命令会返回：

```json
@{"type":"error","msg_id":"msg_003","task_id":"task_002","error_code":"E005","message":"限位触发"}
```

### 模拟归零失败

```bash
python fake_u1.py --inject E006
```

发送 HOME 命令会返回：

```json
@{"type":"error","msg_id":"msg_002","task_id":"task_001","error_code":"E006","message":"归零失败"}
```

## 工作区配置

默认工作区（mm）：

- X: 0.0 ~ 200.0
- Y: 0.0 ~ 150.0
- Z: 0.0 ~ 50.0

原点位置：(0.0, 0.0, 10.0)

原点容差：0.5 mm

## 与 U8 集成

fake U1 可以直接替换真实 U1，U8 侧无需修改代码：

1. 启动 fake U1 服务器
2. 配置 U8 连接到 fake U1 的地址和端口
3. U8 发送的所有命令都会被 fake U1 处理

## 限制

fake U1 是协议级仿真，不模拟真实物理行为：

- ❌ 不模拟真实电机运动
- ❌ 不模拟真实加速度曲线
- ❌ 不模拟真实时序抖动
- ❌ 不模拟 RMT 精度
- ✅ 仅模拟协议交互与状态机

真实硬件联调时，需要验证 Grbl 内部 API 在真实板子上的行为。

## 依据文档

- 实施计划 v2 M0c.1
- 架构定稿 v2 §14 状态机
- 架构定稿 v2 §15 协议字段表

## 修订记录

- 2026-05-14：初始版本，实现基础协议与状态机

