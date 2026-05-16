// 全局要用的类型放到这里

declare global {
  const computed: typeof import('vue')['computed']
  const onLoad: typeof import('@dcloudio/uni-app')['onLoad']
  const onMounted: typeof import('vue')['onMounted']
  const onShow: typeof import('@dcloudio/uni-app')['onShow']
  const reactive: typeof import('vue')['reactive']
  const ref: typeof import('vue')['ref']
  const watch: typeof import('vue')['watch']
  type Ref<T = any> = import('vue').Ref<T>

  interface IResData<T> {
    code: number
    msg: string
    data: T
  }

  // uni.uploadFile文件上传参数
  interface IUniUploadFileOptions {
    file?: File
    files?: UniApp.UploadFileOptionFiles[]
    filePath?: string
    name?: string
    formData?: any
  }

  interface IUserInfo {
    nickname?: string
    avatar?: string
    /** 微信的 openid，非微信没有这个字段 */
    openid?: string
    token?: string
  }
}

export {} // 防止模块污染
