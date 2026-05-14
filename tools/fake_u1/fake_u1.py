#!/usr/bin/env python3
"""
fake U1 - U1 固件仿真器

用途：
- 在没有真实硬件的情况下，模拟 U1 的协议行为
- 支持 M1~M3 阶段的软件开发与单元测试
- 支持错误注入，模拟各种异常场景

协议：
- 监听 TCP 端口（默认 7799）
- 接收 U8 私有协议帧：@{json}\n
- 返回 ack/status/result/error 响应

状态机：
- IDLE / HOMING / RUNNING / PAUSED / ALARM / ERROR / ESTOP

依据：
- 实施计划 v2 M0c.1
- 架构定稿 v2 §15 协议字段表
"""

import asyncio
import json
import logging
import argparse
from typing import Dict, Optional, List
from dataclasses import dataclass, asdict
from enum import Enum
import time


class DeviceState(Enum):
    """设备状态（对应 v2 §14）"""
    IDLE = "IDLE"
    HOMING = "HOMING"
    RUNNING = "RUNNING"
    PAUSED = "PAUSED"
    ALARM = "ALARM"
    ERROR = "ERROR"
    ESTOP = "ESTOP"


class ResponseType(Enum):
    """响应类型"""
    ACK = "ack"
    STATUS = "status"
    RESULT = "result"
    ERROR = "error"


@dataclass
class Position:
    """位置坐标"""
    x: float = 0.0
    y: float = 0.0
    z: float = 0.0


@dataclass
class DeviceStatus:
    """设备状态"""
    state: str
    homed: bool
    position_mm: Dict[str, float]
    at_home: bool
    alarm_code: Optional[str] = None
    error_code: Optional[str] = None


class FakeU1:
    """fake U1 仿真器"""
    
    # 工作区范围（mm）
    WORKSPACE = {
        'x_min': 0.0, 'x_max': 200.0,
        'y_min': 0.0, 'y_max': 150.0,
        'z_min': 0.0, 'z_max': 50.0
    }
    
    # 原点位置
    HOME_POSITION = Position(0.0, 0.0, 10.0)
    
    # 原点容差（mm）
    HOME_TOLERANCE = 0.5
    
    def __init__(self, latency_ms: int = 10, inject_errors: Optional[List[str]] = None):
        """
        初始化 fake U1
        
        Args:
            latency_ms: 模拟响应延迟（毫秒）
            inject_errors: 要注入的错误码列表
        """
        self.latency_ms = latency_ms
        self.inject_errors = inject_errors or []
        
        # 设备状态
        self.state = DeviceState.IDLE
        self.position = Position()
        self.homed = False
        self.current_task_id: Optional[str] = None
        self.path_segments: List[Dict] = []
        self.path_progress = 0.0
        
        # 日志
        self.logger = logging.getLogger('FakeU1')
    
    async def handle_command(self, command: Dict) -> Dict:
        """
        处理命令
        
        Args:
            command: 命令 JSON
        
        Returns:
            响应 JSON
        """
        # 模拟延迟
        if self.latency_ms > 0:
            await asyncio.sleep(self.latency_ms / 1000.0)
        
        cmd_type = command.get('cmd')
        msg_id = command.get('msg_id')
        task_id = command.get('task_id')
        
        self.logger.info(f"收到命令: {cmd_type}, msg_id={msg_id}, task_id={task_id}")
        
        # 路由到具体处理函数
        handlers = {
            'GET_STATUS': self._handle_get_status,
            'HOME': self._handle_home,
            'MOVE': self._handle_move,
            'PATH_BEGIN': self._handle_path_begin,
            'PATH_SEG': self._handle_path_seg,
            'PATH_END': self._handle_path_end,
            'PAUSE': self._handle_pause,
            'RESUME': self._handle_resume,
            'STOP': self._handle_stop,
            'ESTOP': self._handle_estop,
        }
        
        handler = handlers.get(cmd_type)
        if not handler:
            return self._error_response(msg_id, task_id, "E_UNKNOWN_CMD", f"未知命令: {cmd_type}")
        
        return await handler(command)
    
    async def _handle_get_status(self, command: Dict) -> Dict:
        """处理 GET_STATUS 命令"""
        msg_id = command.get('msg_id')
        
        status = self._get_current_status()
        
        return {
            'type': ResponseType.STATUS.value,
            'msg_id': msg_id,
            'state': status.state,
            'homed': status.homed,
            'position_mm': status.position_mm,
            'at_home': status.at_home,
            'alarm_code': status.alarm_code,
            'error_code': status.error_code
        }
    
    async def _handle_home(self, command: Dict) -> Dict:
        """处理 HOME 命令"""
        msg_id = command.get('msg_id')
        task_id = command.get('task_id')
        
        # 检查错误注入
        if 'E006' in self.inject_errors:
            self.state = DeviceState.ALARM
            return self._error_response(msg_id, task_id, "E006", "归零失败")
        
        # 检查状态
        if self.state in [DeviceState.RUNNING, DeviceState.HOMING]:
            return self._error_response(msg_id, task_id, "E_DEVICE_BUSY", "设备忙")
        
        if self.state == DeviceState.ESTOP:
            return self._error_response(msg_id, task_id, "E008", "急停状态")
        
        # 开始归零
        self.state = DeviceState.HOMING
        self.current_task_id = task_id
        
        # 返回 ack
        ack = {
            'type': ResponseType.ACK.value,
            'msg_id': msg_id,
            'task_id': task_id,
            'accepted': True
        }
        
        # 异步执行归零
        asyncio.create_task(self._execute_home())
        
        return ack
    
    async def _execute_home(self):
        """执行归零过程"""
        # 模拟归零耗时
        await asyncio.sleep(2.0)
        
        # 归零完成
        self.position = Position(
            self.HOME_POSITION.x,
            self.HOME_POSITION.y,
            self.HOME_POSITION.z
        )
        self.homed = True
        self.state = DeviceState.IDLE
        
        self.logger.info("归零完成")
    
    async def _handle_move(self, command: Dict) -> Dict:
        """处理 MOVE 命令"""
        msg_id = command.get('msg_id')
        task_id = command.get('task_id')
        target = command.get('target', {})
        
        # 检查错误注入
        if 'E001' in self.inject_errors:
            return self._error_response(msg_id, task_id, "E001", "未归零")
        
        if 'E005' in self.inject_errors:
            self.state = DeviceState.ALARM
            return self._error_response(msg_id, task_id, "E005", "限位触发")
        
        # 检查状态
        if not self.homed:
            return self._error_response(msg_id, task_id, "E001", "未归零")
        
        if self.state != DeviceState.IDLE:
            return self._error_response(msg_id, task_id, "E_DEVICE_BUSY", "设备忙")
        
        # 检查目标位置
        x = target.get('x', self.position.x)
        y = target.get('y', self.position.y)
        z = target.get('z', self.position.z)
        
        if not self._is_in_workspace(x, y, z):
            return self._error_response(msg_id, task_id, "E002", "超出工作区")
        
        # 开始移动
        self.state = DeviceState.RUNNING
        self.current_task_id = task_id
        
        # 返回 ack
        ack = {
            'type': ResponseType.ACK.value,
            'msg_id': msg_id,
            'task_id': task_id,
            'accepted': True
        }
        
        # 异步执行移动
        asyncio.create_task(self._execute_move(x, y, z))
        
        return ack
    
    async def _execute_move(self, x: float, y: float, z: float):
        """执行移动过程"""
        # 模拟移动耗时
        await asyncio.sleep(1.0)
        
        # 移动完成
        self.position = Position(x, y, z)
        self.state = DeviceState.IDLE
        
        self.logger.info(f"移动完成: ({x}, {y}, {z})")
    
    async def _handle_path_begin(self, command: Dict) -> Dict:
        """处理 PATH_BEGIN 命令"""
        msg_id = command.get('msg_id')
        task_id = command.get('task_id')
        
        # 检查状态
        if not self.homed:
            return self._error_response(msg_id, task_id, "E001", "未归零")
        
        if self.state != DeviceState.IDLE:
            return self._error_response(msg_id, task_id, "E_DEVICE_BUSY", "设备忙")
        
        # 开始路径
        self.state = DeviceState.RUNNING
        self.current_task_id = task_id
        self.path_segments = []
        self.path_progress = 0.0
        
        return {
            'type': ResponseType.ACK.value,
            'msg_id': msg_id,
            'task_id': task_id,
            'accepted': True
        }
    
    async def _handle_path_seg(self, command: Dict) -> Dict:
        """处理 PATH_SEG 命令"""
        msg_id = command.get('msg_id')
        task_id = command.get('task_id')
        segment = command.get('segment', {})
        
        # 添加段
        self.path_segments.append(segment)
        
        return {
            'type': ResponseType.ACK.value,
            'msg_id': msg_id,
            'task_id': task_id,
            'accepted': True
        }
    
    async def _handle_path_end(self, command: Dict) -> Dict:
        """处理 PATH_END 命令"""
        msg_id = command.get('msg_id')
        task_id = command.get('task_id')
        
        # 返回 ack
        ack = {
            'type': ResponseType.ACK.value,
            'msg_id': msg_id,
            'task_id': task_id,
            'accepted': True
        }
        
        # 异步执行路径
        asyncio.create_task(self._execute_path())
        
        return ack
    
    async def _execute_path(self):
        """执行路径"""
        total_segments = len(self.path_segments)
        
        for i, segment in enumerate(self.path_segments):
            # 模拟段执行耗时
            await asyncio.sleep(0.1)
            
            # 更新进度
            self.path_progress = (i + 1) / total_segments
            
            # 更新位置（简化：直接跳到段终点）
            if segment.get('type') == 'L':
                self.position.x = segment.get('x', self.position.x)
                self.position.y = segment.get('y', self.position.y)
                self.position.z = segment.get('z', self.position.z)
        
        # 路径完成
        self.state = DeviceState.IDLE
        self.logger.info(f"路径完成: {total_segments} 段")
    
    async def _handle_pause(self, command: Dict) -> Dict:
        """处理 PAUSE 命令"""
        msg_id = command.get('msg_id')
        task_id = command.get('task_id')
        
        if self.state != DeviceState.RUNNING:
            return self._error_response(msg_id, task_id, "E_INVALID_STATE", "当前状态不可暂停")
        
        self.state = DeviceState.PAUSED
        
        return {
            'type': ResponseType.ACK.value,
            'msg_id': msg_id,
            'task_id': task_id,
            'accepted': True
        }
    
    async def _handle_resume(self, command: Dict) -> Dict:
        """处理 RESUME 命令"""
        msg_id = command.get('msg_id')
        task_id = command.get('task_id')
        
        if self.state != DeviceState.PAUSED:
            return self._error_response(msg_id, task_id, "E_INVALID_STATE", "当前状态不可恢复")
        
        self.state = DeviceState.RUNNING
        
        return {
            'type': ResponseType.ACK.value,
            'msg_id': msg_id,
            'task_id': task_id,
            'accepted': True
        }
    
    async def _handle_stop(self, command: Dict) -> Dict:
        """处理 STOP 命令"""
        msg_id = command.get('msg_id')
        task_id = command.get('task_id')
        
        if self.state in [DeviceState.IDLE, DeviceState.ESTOP]:
            return self._error_response(msg_id, task_id, "E_INVALID_STATE", "当前状态不可停止")
        
        # 受控停止
        self.state = DeviceState.IDLE
        self.current_task_id = None
        
        return {
            'type': ResponseType.RESULT.value,
            'msg_id': msg_id,
            'task_id': task_id,
            'result': 'CANCELLED'
        }
    
    async def _handle_estop(self, command: Dict) -> Dict:
        """处理 ESTOP 命令"""
        msg_id = command.get('msg_id')
        task_id = command.get('task_id')
        
        # 立即进入急停状态
        self.state = DeviceState.ESTOP
        self.current_task_id = None
        
        # 检查错误注入
        if 'E008' in self.inject_errors:
            # 注入时仍返回 ack，但状态已是 ESTOP
            pass
        
        return {
            'type': ResponseType.ACK.value,
            'msg_id': msg_id,
            'task_id': task_id,
            'accepted': True
        }
    
    def _get_current_status(self) -> DeviceStatus:
        """获取当前状态"""
        at_home = (
            self.homed and
            abs(self.position.x - self.HOME_POSITION.x) < self.HOME_TOLERANCE and
            abs(self.position.y - self.HOME_POSITION.y) < self.HOME_TOLERANCE and
            abs(self.position.z - self.HOME_POSITION.z) < self.HOME_TOLERANCE
        )
        
        alarm_code = None
        error_code = None
        
        if self.state == DeviceState.ALARM:
            if 'E005' in self.inject_errors:
                alarm_code = "E005"
            elif 'E006' in self.inject_errors:
                alarm_code = "E006"
        
        if self.state == DeviceState.ESTOP:
            alarm_code = "E008"
        
        return DeviceStatus(
            state=self.state.value,
            homed=self.homed,
            position_mm={'x': self.position.x, 'y': self.position.y, 'z': self.position.z},
            at_home=at_home,
            alarm_code=alarm_code,
            error_code=error_code
        )
    
    def _is_in_workspace(self, x: float, y: float, z: float) -> bool:
        """检查坐标是否在工作区内"""
        return (
            self.WORKSPACE['x_min'] <= x <= self.WORKSPACE['x_max'] and
            self.WORKSPACE['y_min'] <= y <= self.WORKSPACE['y_max'] and
            self.WORKSPACE['z_min'] <= z <= self.WORKSPACE['z_max']
        )
    
    def _error_response(self, msg_id: str, task_id: Optional[str], error_code: str, message: str) -> Dict:
        """生成错误响应"""
        return {
            'type': ResponseType.ERROR.value,
            'msg_id': msg_id,
            'task_id': task_id,
            'error_code': error_code,
            'message': message
        }


class FakeU1Server:
    """fake U1 TCP 服务器"""
    
    def __init__(self, host: str = '127.0.0.1', port: int = 7799, **kwargs):
        """
        初始化服务器
        
        Args:
            host: 监听地址
            port: 监听端口
            **kwargs: 传递给 FakeU1 的参数
        """
        self.host = host
        self.port = port
        self.fake_u1 = FakeU1(**kwargs)
        self.logger = logging.getLogger('FakeU1Server')
    
    async def handle_client(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        """处理客户端连接"""
        addr = writer.get_extra_info('peername')
        self.logger.info(f"客户端连接: {addr}")
        
        try:
            while True:
                # 读取一行（以 \n 结尾）
                line = await reader.readline()
                if not line:
                    break
                
                # 解析命令
                line_str = line.decode('utf-8').strip()
                
                # 检查协议格式：@{json}\n
                if not line_str.startswith('@{'):
                    self.logger.warning(f"无效协议格式: {line_str}")
                    continue
                
                # 提取 JSON
                json_str = line_str[1:]  # 去掉 @
                
                try:
                    command = json.loads(json_str)
                except json.JSONDecodeError as e:
                    self.logger.error(f"JSON 解析失败: {e}")
                    continue
                
                # 处理命令
                response = await self.fake_u1.handle_command(command)
                
                # 发送响应
                response_str = f"@{json.dumps(response)}\n"
                writer.write(response_str.encode('utf-8'))
                await writer.drain()
        
        except Exception as e:
            self.logger.error(f"处理客户端时出错: {e}")
        
        finally:
            self.logger.info(f"客户端断开: {addr}")
            writer.close()
            await writer.wait_closed()
    
    async def start(self):
        """启动服务器"""
        server = await asyncio.start_server(
            self.handle_client,
            self.host,
            self.port
        )
        
        addr = server.sockets[0].getsockname()
        self.logger.info(f"fake U1 服务器启动: {addr}")
        
        async with server:
            await server.serve_forever()


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='fake U1 - U1 固件仿真器')
    parser.add_argument('--host', default='127.0.0.1', help='监听地址')
    parser.add_argument('--port', type=int, default=7799, help='监听端口')
    parser.add_argument('--latency-ms', type=int, default=10, help='响应延迟（毫秒）')
    parser.add_argument('--inject', nargs='*', help='注入错误码（E001/E005/E006/E008）')
    parser.add_argument('--verbose', '-v', action='store_true', help='详细日志')
    
    args = parser.parse_args()
    
    # 配置日志
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format='%(asctime)s [%(name)s] %(levelname)s: %(message)s'
    )
    
    # 创建服务器
    server = FakeU1Server(
        host=args.host,
        port=args.port,
        latency_ms=args.latency_ms,
        inject_errors=args.inject or []
    )
    
    # 启动服务器
    try:
        asyncio.run(server.start())
    except KeyboardInterrupt:
        logging.info("服务器停止")


if __name__ == '__main__':
    main()
