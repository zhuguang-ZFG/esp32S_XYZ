import type { V2DeviceInfo, V2TaskInfo } from '@/api/v2/types'
import { computed, ref } from 'vue'
import { v2GetDevices, v2ListTasks } from '@/api/v2'

/**
 * 首页数据加载 + 主设备派生（D2 从 pages/index/index.vue 提取）。
 *
 * 维护设备列表、最近任务、加载态；派生 primaryDevice / onlineCount，
 * 供模板直接绑定。
 */
export function useHomeData() {
  const devices = ref<V2DeviceInfo[]>([])
  const recentTasks = ref<V2TaskInfo[]>([])
  const loading = ref(false)

  const primaryDevice = computed(() => {
    return devices.value.find(d => d.status === 'online') || devices.value[0] || null
  })
  const onlineCount = computed(() => devices.value.filter(d => d.status === 'online').length)

  async function loadData() {
    loading.value = true
    try {
      const res = await v2GetDevices()
      devices.value = res.rows || []
      if (primaryDevice.value) {
        try {
          const taskRes = await v2ListTasks(primaryDevice.value.deviceId, '', 3)
          recentTasks.value = taskRes.tasks || []
        }
        catch { recentTasks.value = [] }
      }
    }
    catch (e) { console.error(e) }
    finally { loading.value = false }
  }

  return { devices, recentTasks, loading, primaryDevice, onlineCount, loadData }
}
