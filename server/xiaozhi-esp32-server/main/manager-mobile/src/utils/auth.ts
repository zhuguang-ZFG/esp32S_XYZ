import { getBearerToken } from '@/utils'

/** True when a bearer token exists in local storage (primary MP auth signal). */
export function isSessionActive(): boolean {
  return !!getBearerToken()
}
