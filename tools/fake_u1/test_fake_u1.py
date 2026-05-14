#!/usr/bin/env python3
"""
fake U1 单元测试

测试覆盖：
1. GET_STATUS 命令
2. HOME 命令（正常 + 错误注入）
3. MOVE 命令（正常 + 未归零 + 超界）
4. PATH 命令（BEGIN/SEG/END）
5. PAUSE/RESUME/STOP/ESTOP 命令
6. 错误注入场景

依据：
- 实施计划 v2 M0c.1
- 架构定稿 v2 §15 协议字段表
"""

import unittest
import asyncio
import json
from fake_u1 import FakeU1, DeviceState


class TestFakeU1(unittest.TestCase):
    """fake U1 单元测试"""
    
    def setUp(self):
        """测试前准备"""
        self.fake_u1 = FakeU1(latency_ms=0)  # 测试时不延迟
    
    def test_get_status_initial(self):
        """测试初始状态查询"""
        command = {
            'cmd': 'GET_STATUS',
            'msg_id': 'msg_001'
        }
        
        response = asyncio.run(self.fake_u1.handle_command(command))
        
        self.assertEqual(response['type'], 'status')
        self.assertEqual(response['msg_id'], 'msg_001')
        self.assertEqual(response['state'], 'IDLE')
        self.assertFalse(response['homed'])
        self.assertFalse(response['at_home'])
    
    def test_home_success(self):
        """测试归零成功"""
        async def run_test():
            command = {
                'cmd': 'HOME',
                'msg_id': 'msg_002',
                'task_id': 'task_001'
            }
            
            response = await self.fake_u1.handle_command(command)
            
            self.assertEqual(response['type'], 'ack')
            self.assertEqual(response['msg_id'], 'msg_002')
            self.assertEqual(response['task_id'], 'task_001')
            self.assertTrue(response['accepted'])
            
            # 等待归零完成
            await asyncio.sleep(2.5)
            
            # 检查状态
            status_cmd = {'cmd': 'GET_STATUS', 'msg_id': 'msg_003'}
            status = await self.fake_u1.handle_command(status_cmd)
            
            self.assertEqual(status['state'], 'IDLE')
            self.assertTrue(status['homed'])
            self.assertTrue(status['at_home'])
        
        asyncio.run(run_test())
    
    def test_home_error_injection(self):
        """测试归零失败（错误注入）"""
        fake_u1 = FakeU1(latency_ms=0, inject_errors=['E006'])
        
        command = {
            'cmd': 'HOME',
            'msg_id': 'msg_004',
            'task_id': 'task_002'
        }
        
        response = asyncio.run(fake_u1.handle_command(command))
        
        self.assertEqual(response['type'], 'error')
        self.assertEqual(response['error_code'], 'E006')
    
    def test_move_not_homed(self):
        """测试未归零时移动"""
        command = {
            'cmd': 'MOVE',
            'msg_id': 'msg_005',
            'task_id': 'task_003',
            'target': {'x': 10.0, 'y': 10.0, 'z': 10.0}
        }
        
        response = asyncio.run(self.fake_u1.handle_command(command))
        
        self.assertEqual(response['type'], 'error')
        self.assertEqual(response['error_code'], 'E001')
    
    def test_move_success(self):
        """测试移动成功"""
        async def run_test():
            # 先归零
            self.fake_u1.homed = True
            self.fake_u1.state = DeviceState.IDLE
            
            command = {
                'cmd': 'MOVE',
                'msg_id': 'msg_006',
                'task_id': 'task_004',
                'target': {'x': 50.0, 'y': 50.0, 'z': 20.0}
            }
            
            response = await self.fake_u1.handle_command(command)
            
            self.assertEqual(response['type'], 'ack')
            self.assertTrue(response['accepted'])
            
            # 等待移动完成
            await asyncio.sleep(1.5)
            
            # 检查位置
            self.assertAlmostEqual(self.fake_u1.position.x, 50.0)
            self.assertAlmostEqual(self.fake_u1.position.y, 50.0)
            self.assertAlmostEqual(self.fake_u1.position.z, 20.0)
        
        asyncio.run(run_test())
    
    def test_move_out_of_range(self):
        """测试超出工作区"""
        self.fake_u1.homed = True
        self.fake_u1.state = DeviceState.IDLE
        
        command = {
            'cmd': 'MOVE',
            'msg_id': 'msg_007',
            'task_id': 'task_005',
            'target': {'x': 300.0, 'y': 50.0, 'z': 20.0}  # 超出 x_max=200
        }
        
        response = asyncio.run(self.fake_u1.handle_command(command))
        
        self.assertEqual(response['type'], 'error')
        self.assertEqual(response['error_code'], 'E002')
    
    def test_path_execution(self):
        """测试路径执行"""
        async def run_test():
            self.fake_u1.homed = True
            self.fake_u1.state = DeviceState.IDLE
            
            # PATH_BEGIN
            begin_cmd = {
                'cmd': 'PATH_BEGIN',
                'msg_id': 'msg_008',
                'task_id': 'task_006'
            }
            response = await self.fake_u1.handle_command(begin_cmd)
            self.assertEqual(response['type'], 'ack')
            
            # PATH_SEG
            seg1_cmd = {
                'cmd': 'PATH_SEG',
                'msg_id': 'msg_009',
                'task_id': 'task_006',
                'segment': {'type': 'M', 'x': 10.0, 'y': 10.0, 'z': 10.0}
            }
            response = await self.fake_u1.handle_command(seg1_cmd)
            self.assertEqual(response['type'], 'ack')
            
            seg2_cmd = {
                'cmd': 'PATH_SEG',
                'msg_id': 'msg_010',
                'task_id': 'task_006',
                'segment': {'type': 'L', 'x': 20.0, 'y': 20.0, 'z': 10.0}
            }
            response = await self.fake_u1.handle_command(seg2_cmd)
            self.assertEqual(response['type'], 'ack')
            
            # PATH_END
            end_cmd = {
                'cmd': 'PATH_END',
                'msg_id': 'msg_011',
                'task_id': 'task_006'
            }
            response = await self.fake_u1.handle_command(end_cmd)
            self.assertEqual(response['type'], 'ack')
            
            # 等待路径完成
            await asyncio.sleep(0.5)
            
            # 检查状态
            self.assertEqual(self.fake_u1.state, DeviceState.IDLE)
            self.assertEqual(len(self.fake_u1.path_segments), 2)
        
        asyncio.run(run_test())
    
    def test_pause_resume(self):
        """测试暂停/恢复"""
        self.fake_u1.homed = True
        self.fake_u1.state = DeviceState.RUNNING
        
        # PAUSE
        pause_cmd = {
            'cmd': 'PAUSE',
            'msg_id': 'msg_012',
            'task_id': 'task_007'
        }
        response = asyncio.run(self.fake_u1.handle_command(pause_cmd))
        self.assertEqual(response['type'], 'ack')
        self.assertEqual(self.fake_u1.state, DeviceState.PAUSED)
        
        # RESUME
        resume_cmd = {
            'cmd': 'RESUME',
            'msg_id': 'msg_013',
            'task_id': 'task_007'
        }
        response = asyncio.run(self.fake_u1.handle_command(resume_cmd))
        self.assertEqual(response['type'], 'ack')
        self.assertEqual(self.fake_u1.state, DeviceState.RUNNING)
    
    def test_stop(self):
        """测试停止"""
        self.fake_u1.homed = True
        self.fake_u1.state = DeviceState.RUNNING
        self.fake_u1.current_task_id = 'task_008'
        
        stop_cmd = {
            'cmd': 'STOP',
            'msg_id': 'msg_014',
            'task_id': 'task_008'
        }
        response = asyncio.run(self.fake_u1.handle_command(stop_cmd))
        
        self.assertEqual(response['type'], 'result')
        self.assertEqual(response['result'], 'CANCELLED')
        self.assertEqual(self.fake_u1.state, DeviceState.IDLE)
        self.assertIsNone(self.fake_u1.current_task_id)
    
    def test_estop(self):
        """测试急停"""
        self.fake_u1.homed = True
        self.fake_u1.state = DeviceState.RUNNING
        
        estop_cmd = {
            'cmd': 'ESTOP',
            'msg_id': 'msg_015',
            'task_id': 'task_009'
        }
        response = asyncio.run(self.fake_u1.handle_command(estop_cmd))
        
        self.assertEqual(response['type'], 'ack')
        self.assertEqual(self.fake_u1.state, DeviceState.ESTOP)
    
    def test_error_injection_e001(self):
        """测试错误注入 E001（未归零）"""
        fake_u1 = FakeU1(latency_ms=0, inject_errors=['E001'])
        fake_u1.homed = True  # 即使已归零，也强制返回 E001
        fake_u1.state = DeviceState.IDLE
        
        command = {
            'cmd': 'MOVE',
            'msg_id': 'msg_016',
            'task_id': 'task_010',
            'target': {'x': 10.0, 'y': 10.0, 'z': 10.0}
        }
        
        response = asyncio.run(fake_u1.handle_command(command))
        
        self.assertEqual(response['type'], 'error')
        self.assertEqual(response['error_code'], 'E001')
    
    def test_error_injection_e005(self):
        """测试错误注入 E005（限位触发）"""
        fake_u1 = FakeU1(latency_ms=0, inject_errors=['E005'])
        fake_u1.homed = True
        fake_u1.state = DeviceState.IDLE
        
        command = {
            'cmd': 'MOVE',
            'msg_id': 'msg_017',
            'task_id': 'task_011',
            'target': {'x': 10.0, 'y': 10.0, 'z': 10.0}
        }
        
        response = asyncio.run(fake_u1.handle_command(command))
        
        self.assertEqual(response['type'], 'error')
        self.assertEqual(response['error_code'], 'E005')
        self.assertEqual(fake_u1.state, DeviceState.ALARM)
    
    def test_error_injection_e008(self):
        """测试错误注入 E008（急停）"""
        fake_u1 = FakeU1(latency_ms=0, inject_errors=['E008'])
        
        command = {
            'cmd': 'ESTOP',
            'msg_id': 'msg_018',
            'task_id': 'task_012'
        }
        
        response = asyncio.run(fake_u1.handle_command(command))
        
        # ESTOP 总是返回 ack，但状态会变成 ESTOP
        self.assertEqual(response['type'], 'ack')
        self.assertEqual(fake_u1.state, DeviceState.ESTOP)
        
        # 检查状态中的 alarm_code
        status_cmd = {'cmd': 'GET_STATUS', 'msg_id': 'msg_019'}
        status = asyncio.run(fake_u1.handle_command(status_cmd))
        self.assertEqual(status['alarm_code'], 'E008')


if __name__ == '__main__':
    unittest.main()
