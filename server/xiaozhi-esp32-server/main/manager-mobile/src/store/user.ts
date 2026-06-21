import type { V2MeResponse } from '@/api/v2/types'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { v2GetMe } from '@/api/v2'

// 初始化状态
const userInfoState: V2MeResponse & { token?: string } = {
  accountId: '',
  phone: '',
  nickname: '',
  avatarUrl: '/static/images/default-avatar.png',
  role: 'user',
  createdAt: '',
  token: '',
}

export const useUserStore = defineStore(
  'userInfo',
  () => {
    // 定义用户信息
    const userInfo = ref<V2MeResponse & { token?: string }>({ ...userInfoState })
    // 设置用户信息
    const setUserInfo = (val: V2MeResponse & { token?: string }) => {
      // 若头像为空 则使用本地默认头像（已随 manager-mobile 迁移到 LiMa）
      if (!val.avatarUrl) {
        val.avatarUrl = userInfoState.avatarUrl
      }
      userInfo.value = val
    }
    const setUserAvatar = (avatar: string) => {
      userInfo.value.avatarUrl = avatar
    }
    // 删除用户信息
    const removeUserInfo = () => {
      userInfo.value = { ...userInfoState }
      uni.removeStorageSync('userInfo')
      uni.removeStorageSync('token')
    }
    /**
     * 获取用户信息
     */
    const getUserInfo = async () => {
      const userData = await v2GetMe()
      setUserInfo(userData)
      return userData
    }
    /**
     * 退出登录 并 删除用户信息
     */
    const logout = async () => {
      removeUserInfo()
    }

    return {
      userInfo,
      getUserInfo,
      setUserInfo,
      setUserAvatar,
      logout,
      removeUserInfo,
    }
  },
  {
    persist: {
      key: 'userInfo',
      serializer: {
        serialize: state => JSON.stringify(state.userInfo),
        deserialize: value => ({ userInfo: JSON.parse(value) }),
      },
    },
  },
)
