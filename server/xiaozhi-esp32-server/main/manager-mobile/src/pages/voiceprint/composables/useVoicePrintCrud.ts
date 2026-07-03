import type { Member } from '@/api/member'
import type { ChatHistory, CreateSpeakerData, VoicePrint } from '@/api/voiceprint'
import { computed, ref } from 'vue'
import { useToast } from 'wot-design-uni/components/wd-toast'
import { getMemberList } from '@/api/member'
import { createVoicePrint, deleteVoicePrint, getChatHistory, getVoicePrintList, updateVoicePrint } from '@/api/voiceprint'
import { t } from '@/i18n'

/**
 * 声纹管理页的 CRUD + 表单 + 成员/对话记录数据（P3.1 从 index.vue 提取）。
 *
 * deviceId 以 getter 传入，随路由 query 变化。message（确认弹窗）由页面注入，
 * 避免 composable 直接依赖具体 UI 组件实例的创建时机。
 */
export function useVoicePrintCrud(deviceId: () => string, message: any) {
  const toast = useToast()

  const voicePrintList = ref<VoicePrint[]>([])
  const chatHistoryList = ref<ChatHistory[]>([])
  const chatHistoryActions = ref<any[]>([])
  const memberList = ref<Member[]>([])
  const swipeStates = ref<Record<string, 'left' | 'close' | 'right'>>({})
  const loading = ref(false)

  const showAddDialog = ref(false)
  const showEditDialog = ref(false)
  const showChatHistoryDialog = ref(false)

  const addForm = ref<CreateSpeakerData>({
    deviceId: '',
    memberId: '',
    audioId: '',
    sourceName: '',
    introduce: '',
  })
  const editForm = ref<VoicePrint>({
    id: '',
    voiceprintId: '',
    memberId: '',
    memberName: '',
    deviceId: '',
    audioId: '',
    sourceName: '',
    introduce: '',
    sampleCount: 0,
    confidence: 0,
    status: '',
    createDate: '',
    createdAt: '',
  })

  const memberPickerRange = computed(() => memberList.value.map(m => m.name))
  const selectedMemberName = computed(() => {
    const member = memberList.value.find(m => m.memberId === addForm.value.memberId)
    return member?.name || ''
  })

  async function loadVoicePrintList() {
    try {
      if (!deviceId()) {
        voicePrintList.value = []
        return
      }
      loading.value = true
      const data = await getVoicePrintList(deviceId())
      const list = data || []
      list.forEach((item) => {
        if (!swipeStates.value[item.id])
          swipeStates.value[item.id] = 'close'
      })
      voicePrintList.value = list
    }
    catch (error) {
      voicePrintList.value = []
    }
    finally {
      loading.value = false
    }
  }

  async function refresh() {
    await loadVoicePrintList()
  }

  async function loadMemberList() {
    if (!deviceId()) {
      memberList.value = []
      return
    }
    try {
      const data = await getMemberList(deviceId())
      memberList.value = data || []
    }
    catch (error) {
      memberList.value = []
    }
  }

  function onMemberChange(event: any) {
    const index = event?.detail?.value ?? event?.target?.value
    const member = memberList.value[Number(index)]
    if (member)
      addForm.value.memberId = member.memberId
  }

  async function loadChatHistory() {
    try {
      if (!deviceId()) {
        toast.error(t('voiceprint.pleaseSelectAgent'))
        return
      }
      const data = await getChatHistory(deviceId())
      chatHistoryList.value = data || []
      chatHistoryActions.value = chatHistoryList.value.map((item, index) => ({
        name: item.content,
        audioId: item.audioId,
        index,
      }))
    }
    catch (error) {
      toast.error(t('voiceprint.fetchHistoryFailed'))
    }
  }

  async function openAddDialog() {
    if (!deviceId()) {
      toast.error(t('voiceprint.pleaseSelectAgent'))
      return
    }
    if (memberList.value.length === 0)
      await loadMemberList()
    if (memberList.value.length === 0) {
      toast.error(t('voiceprint.noMembers'))
      return
    }
    const defaultMember = memberList.value[0]
    addForm.value = {
      deviceId: deviceId(),
      memberId: defaultMember.memberId,
      audioId: '',
      sourceName: '',
      introduce: '',
    }
    showAddDialog.value = true
  }

  function openEditDialog(item: VoicePrint) {
    editForm.value = { ...item }
    showEditDialog.value = true
  }

  function getSelectedAudioContent(audioId: string) {
    if (!audioId)
      return t('voiceprint.clickToSelectVector')
    const chatItem = chatHistoryList.value.find(item => item.audioId === audioId)
    return chatItem ? chatItem.content : `已选择: ${audioId.substring(0, 8)}...`
  }

  function selectAudioId({ item }: { item: any }) {
    if (showAddDialog.value)
      addForm.value.audioId = item.audioId
    else if (showEditDialog.value)
      editForm.value.audioId = item.audioId
    showChatHistoryDialog.value = false
  }

  function handleItemClick(item: any) {
    selectAudioId({ item })
  }

  async function submitAdd() {
    if (!addForm.value.sourceName.trim()) {
      toast.error(t('voiceprint.pleaseInputName'))
      return
    }
    if (!addForm.value.audioId) {
      toast.error(t('voiceprint.pleaseSelectVector'))
      return
    }
    if (!addForm.value.memberId.trim()) {
      toast.error(t('voiceprint.pleaseSelectMember'))
      return
    }
    try {
      await createVoicePrint(addForm.value)
      toast.success(t('voiceprint.addSuccess'))
      showAddDialog.value = false
      await loadVoicePrintList()
    }
    catch (error) {
      toast.error(t('voiceprint.addFailed'))
    }
  }

  async function submitEdit() {
    if (!editForm.value.sourceName.trim()) {
      toast.error(t('voiceprint.pleaseInputName'))
      return
    }
    if (!editForm.value.audioId) {
      toast.error(t('voiceprint.pleaseSelectVector'))
      return
    }
    try {
      await updateVoicePrint({ ...editForm.value })
      toast.success(t('voiceprint.editSuccess'))
      showEditDialog.value = false
      await loadVoicePrintList()
    }
    catch (error) {
      toast.error(t('voiceprint.editFailed'))
    }
  }

  function handleEdit(item: VoicePrint) {
    openEditDialog(item)
    swipeStates.value[item.id] = 'close'
  }

  async function handleDelete(id: string) {
    message.confirm({
      msg: t('voiceprint.deleteConfirmMsg'),
      title: t('voiceprint.deleteConfirmTitle'),
    }).then(async () => {
      await deleteVoicePrint(id)
      toast.success(t('voiceprint.deleteSuccess'))
      await loadVoicePrintList()
    }).catch(() => {
      // cancelled
    })
  }

  return {
    voicePrintList,
    chatHistoryActions,
    memberList,
    swipeStates,
    loading,
    showAddDialog,
    showEditDialog,
    showChatHistoryDialog,
    addForm,
    editForm,
    memberPickerRange,
    selectedMemberName,
    loadVoicePrintList,
    refresh,
    loadMemberList,
    onMemberChange,
    loadChatHistory,
    openAddDialog,
    getSelectedAudioContent,
    handleItemClick,
    submitAdd,
    submitEdit,
    handleEdit,
    handleDelete,
  }
}
