export interface Member {
  memberId: string
  id: string
  accountId: string
  deviceId: string
  name: string
  role: string
  avatarUrl: string | null
  voiceprintId: string | null
  status: string
  createdAt: string
}

export interface MemberListResponse {
  members: Member[]
  count: number
}
