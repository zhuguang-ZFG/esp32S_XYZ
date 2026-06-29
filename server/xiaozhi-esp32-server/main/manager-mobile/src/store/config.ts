import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface PublicConfig {
  enableMobileRegister: boolean
  version: string
  year: string
  allowUserRegister: boolean
  mobileAreaList: Array<{ name: string, key: string }>
  beianIcpNum: string
  beianGaNum: string
  name: string
  sm2PublicKey: string
}

// 初始化状态
const initialConfigState: PublicConfig = {
  enableMobileRegister: false,
  version: import.meta.env.VITE_APP_VERSION || '3.4.0',
  year: new Date().getFullYear().toString(),
  allowUserRegister: false,
  mobileAreaList: [],
  beianIcpNum: '',
  beianGaNum: '',
  sm2PublicKey: '',
  name: import.meta.env.VITE_APP_TITLE || 'LiMa',
}

export const useConfigStore = defineStore(
  'config',
  () => {
    // 定义全局配置
    const config = ref<PublicConfig>({ ...initialConfigState })

    // 设置配置信息
    const setConfig = (val: PublicConfig) => {
      config.value = val
    }

    // 获取公共配置
    // 旧版 /user/pub-config 已随原后端退役；LiMa v2 目前通过构建时 env 注入核心配置。
    const fetchPublicConfig = async () => {
      setConfig({ ...initialConfigState })
      return config.value
    }

    // 重置配置
    const resetConfig = () => {
      config.value = { ...initialConfigState }
    }

    return {
      config,
      setConfig,
      fetchPublicConfig,
      resetConfig,
    }
  },
  {
    persist: {
      key: 'config',
      serializer: {
        serialize: state => JSON.stringify(state.config),
        deserialize: value => ({ config: JSON.parse(value) }),
      },
    },
  },
)
