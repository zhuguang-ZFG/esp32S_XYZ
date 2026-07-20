import type { Ref } from 'vue'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { v2IssueDeviceStatusWsTicket } from '@/api/v2'
import { buildDeviceStatusWsUrl } from '@/utils'

/**
 * 设备状态 WS 推送的服务端事件（M2 协议，device_app_status_ws.py）。
 */
interface ServerWsEvent {
  event: 'status_snapshot' | 'device_online' | 'device_offline' | 'task_started' | 'task_completed' | 'task_failed'
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
  const deviceOnline = ref(false)
  const logLines = ref<string[]>([])
  const latestEvent = ref<EdgeAEvent | null>(null)

  let socketTask: UniApp.SocketTask | null = null
  let reconnectAttempt = 0
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let connectInFlight = false
  /**
   * MP-5 代际计数:uni-app SocketTask 无法 detach 回调,切设备/重连后旧 socket 的
   * onClose/onError 异步到达会污染新连接状态并触发并行重连。每次 connect/disconnect
   * 递增 epoch,回调闭包捕获自己的 epoch,不匹配即判废丢弃。
   */
  let socketEpoch = 0
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
        deviceOnline.value = Boolean(payload.online)
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
      case 'task_failed':
        // MP-2:漏处理 task_failed 曾导致 isDeviceBusy 永久 true、healthCheckLoading 不复位
        return {
          event_type: 'job_status',
          device_id: did,
          task_id: payload.taskId as string | undefined,
          payload: { capability: '', phase: 'failed' },
        }
      case 'device_online':
        deviceOnline.value = true
        appendLog('设备上线')
        return null
      case 'device_offline':
        deviceOnline.value = false
        appendLog('设备离线')
        return null
      default:
        return null
    }
  }

  /** 绑定 socket 生命周期回调,每个回调先做 epoch 判废(MP-5)。 */
  function attachSocketHandlers(task: UniApp.SocketTask, myEpoch: number) {
    task.onOpen(() => {
      if (myEpoch !== socketEpoch)
        return
      connected.value = true
      reconnectAttempt = 0
      appendLog('已连接')
      startHeartbeat()
    })

    task.onMessage(({ data }) => {
      if (myEpoch !== socketEpoch)
        return
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

    task.onClose(() => {
      if (myEpoch !== socketEpoch)
        return
      connected.value = false
      stopHeartbeat()
      appendLog('连接关闭')
      scheduleReconnect()
    })

    task.onError(() => {
      if (myEpoch !== socketEpoch)
        return
      connected.value = false
      stopHeartbeat()
      appendLog('传输错误')
      scheduleReconnect()
    })
  }

  async function connect() {
    if (!deviceId.value || connectInFlight)
      return

    connectInFlight = true
    const myEpoch = ++socketEpoch
    try {
      const { ticket } = await v2IssueDeviceStatusWsTicket(deviceId.value)
      if (myEpoch !== socketEpoch)
        return // await 期间已切设备/断开,放弃本次连接
      const url = buildDeviceStatusWsUrl(deviceId.value, ticket)
      appendLog(`连接 ${url.split('?')[0]}`)

      const task = uni.connectSocket({
        url,
      }) as unknown as UniApp.SocketTask
      socketTask = task
      attachSocketHandlers(task, myEpoch)
    }
    catch (error) {
      if (myEpoch !== socketEpoch)
        return // 过期连接的失败不触发重连
      console.error('device status ws ticket failed:', error)
      appendLog('连接失败：无法获取 WS ticket')
      scheduleReconnect()
    }
    finally {
      connectInFlight = false
    }
  }

  function disconnect() {
    socketEpoch++ // 判废所有在途连接与旧 socket 回调
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

  return { connected, deviceOnline, logLines, latestEvent, connect, disconnect, reconnect, appendLog }
}
