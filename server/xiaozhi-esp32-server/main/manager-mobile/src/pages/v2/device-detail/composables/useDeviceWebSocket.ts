import type { Ref } from 'vue'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { buildEdgeAClientWsUrl } from '@/utils'

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
  const MAX_RECONNECT = 5
  const RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 16000]

  function appendLog(msg: string) {
    logLines.value.push(`[${new Date().toLocaleTimeString()}] ${msg}`)
    if (logLines.value.length > 30)
      logLines.value = logLines.value.slice(-30)
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (socketTask && connected.value) {
        socketTask.send({ data: JSON.stringify({ op: 'ping' }) })
      }
    }, 30000)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function scheduleReconnect() {
    if (reconnectAttempt >= MAX_RECONNECT) {
      appendLog('max reconnect attempts reached')
      return
    }
    const delay = RECONNECT_DELAYS[reconnectAttempt] ?? 16000
    reconnectAttempt++
    appendLog(`reconnect in ${delay / 1000}s (attempt ${reconnectAttempt}/${MAX_RECONNECT})`)
    reconnectTimer = setTimeout(() => connect(), delay)
  }

  function clearReconnectTimer() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function connect() {
    if (!deviceId.value)
      return

    const url = buildEdgeAClientWsUrl()
    const token = uni.getStorageSync('token') || ''
    appendLog(`→ ${url}`)

    socketTask = uni.connectSocket({
      url,
      header: { Authorization: `Bearer ${token}` },
    }) as unknown as UniApp.SocketTask

    socketTask.onOpen(() => {
      connected.value = true
      reconnectAttempt = 0
      appendLog('connected')
      socketTask?.send({ data: JSON.stringify({ op: 'auth', token }) })
      startHeartbeat()
    })

    socketTask.onMessage(({ data }) => {
      try {
        const m = typeof data === 'string' ? JSON.parse(data) : data
        if (m.type === 'authed') {
          appendLog('authed')
          socketTask?.send({ data: JSON.stringify({ op: 'subscribe_device', device_id: deviceId.value }) })
        }
        else if (m.type === 'subscribed') {
          appendLog(`subscribed ${m.topic}`)
        }
        else if (m.type === 'event') {
          latestEvent.value = m.event as EdgeAEvent
        }
        else if (m.type === 'pong') {
          appendLog('pong')
        }
        else if (m.type === 'error') {
          appendLog(`error: ${m.code}`)
        }
      }
      catch {
        appendLog(`raw: ${String(data).slice(0, 120)}`)
      }
    })

    socketTask.onClose(() => {
      connected.value = false
      stopHeartbeat()
      appendLog('disconnected')
      scheduleReconnect()
    })

    socketTask.onError(() => {
      connected.value = false
      stopHeartbeat()
      appendLog('transport error')
      scheduleReconnect()
    })
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
