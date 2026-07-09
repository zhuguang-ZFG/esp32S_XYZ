import type { Ref } from 'vue'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { v2IssueDeviceStatusWsTicket } from '@/api/v2'
import { buildDeviceStatusWsUrl } from '@/utils'

/**
 * 设备状态 WS 推送的服务端事件（M2 协议，device_app_status_ws.py）。
 */
interface ServerWsEvent {
  event: 'status_snapshot' | 'device_online' | 'device_offline' | 'task_started' | 'task_completed'
  payload: Record<string, unknown>
}

/**
 * 页面消费的统一事件格式（兼容 EdgeAEvent 结构）。
 */
export interface EdgeAEvent {
  event_type?: string
  device_id?: string
  task_id?: string
  seq?: number
  ts?: string
  payload?: Record<string, unknown>
}

export function useDeviceWebSocket(deviceId: Ref<string>) {
  const connected = ref(false)
  const logLines = ref<string[]>([])
  const latestEvent = ref<EdgeAEvent | null>(null)

  let socketTask: UniApp.SocketTask | null = null
  let reconnectAttempt = 0
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  const MAX_RECONNECT = 10
  const RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 16000, 16000, 30000, 30000, 30000, 30000]

  function appendLog(msg: string) {
    logLines.value.push(`[${new Date().toLocaleTimeString()}] ${msg}`)
    if (logLines.value.length > 30)
      logLines.value = logLines.value.slice(-30)
  }

  function startHeartbeat() {
    stopHeartbeat()
    // 服务端每 5s 推送快照，20s 心跳足够检测半开连接
    heartbeatTimer = setInterval(() => {
      if (socketTask && connected.value) {
        socketTask.send({ data: JSON.stringify({ op: 'ping' }) })
      }
    }, 20000)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function scheduleReconnect() {
    if (reconnectAttempt >= MAX_RECONNECT) {
      appendLog('已达最大重连次数，请手动重连')
      return
    }
    const delay = RECONNECT_DELAYS[reconnectAttempt] ?? 30000
    reconnectAttempt++
    appendLog(`${delay / 1000}s 后重连 (${reconnectAttempt}/${MAX_RECONNECT})`)
    reconnectTimer = setTimeout(() => connect(), delay)
  }

  function clearReconnectTimer() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  /**
   * 把服务端 M2 事件映射成页面消费的 EdgeAEvent 格式。
   */
  function mapServerEvent(ev: ServerWsEvent): EdgeAEvent | null {
    const did = deviceId.value
    const payload = ev.payload || {}
    switch (ev.event) {
      case 'status_snapshot':
        // 快照包含 online/working/activeTaskId/firmwareVersion
        return {
          event_type: 'status_snapshot',
          device_id: did,
          payload: { ...payload, phase: payload.working ? 'running' : 'idle' },
        }
      case 'task_started':
        return {
          event_type: 'job_status',
          device_id: did,
          task_id: payload.taskId as string | undefined,
          payload: { capability: '', phase: 'accepted' },
        }
      case 'task_completed':
        return {
          event_type: 'job_status',
          device_id: did,
          task_id: payload.taskId as string | undefined,
          payload: { capability: '', phase: 'done' },
        }
      case 'device_online':
        appendLog('设备上线')
        return null
      case 'device_offline':
        appendLog('设备离线')
        return null
      default:
        return null
    }
  }

  async function connect() {
    if (!deviceId.value)
      return

    try {
      const { ticket } = await v2IssueDeviceStatusWsTicket(deviceId.value)
      const url = buildDeviceStatusWsUrl(deviceId.value, ticket)
      appendLog(`连接 ${url.split('?')[0]}`)

      socketTask = uni.connectSocket({
        url,
      }) as unknown as UniApp.SocketTask

      socketTask.onOpen(() => {
        connected.value = true
        reconnectAttempt = 0
        appendLog('已连接')
        startHeartbeat()
      })

      socketTask.onMessage(({ data }) => {
        try {
          const m = typeof data === 'string' ? JSON.parse(data) : data
          if (m.event) {
            const mapped = mapServerEvent(m as ServerWsEvent)
            if (mapped)
              latestEvent.value = mapped
          }
          else if (m.type === 'pong') {
            // 心跳回复
          }
          else if (m.type === 'error' || m.detail) {
            appendLog(`错误: ${m.code || m.detail || '未知'}`)
          }
        }
        catch {
          appendLog(`原始数据: ${String(data).slice(0, 120)}`)
        }
      })

      socketTask.onClose(() => {
        connected.value = false
        stopHeartbeat()
        appendLog('连接关闭')
        scheduleReconnect()
      })

      socketTask.onError(() => {
        connected.value = false
        stopHeartbeat()
        appendLog('传输错误')
        scheduleReconnect()
      })
    }
    catch (error) {
      console.error('device status ws ticket failed:', error)
      appendLog('连接失败：无法获取 WS ticket')
      scheduleReconnect()
    }
  }

  function disconnect() {
    clearReconnectTimer()
    stopHeartbeat()
    reconnectAttempt = MAX_RECONNECT
    socketTask?.close({})
    socketTask = null
    connected.value = false
  }

  function reconnect() {
    disconnect()
    reconnectAttempt = 0
    connect()
  }

  onMounted(() => {
    if (deviceId.value)
      connect()
  })

  onUnmounted(() => {
    disconnect()
  })

  watch(deviceId, (newId) => {
    if (newId) {
      disconnect()
      reconnectAttempt = 0
      connect()
    }
  })

  return { connected, logLines, latestEvent, connect, disconnect, reconnect, appendLog }
}
