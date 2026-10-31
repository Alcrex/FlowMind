import { request } from '@/utils/request'
import type { UserInfo } from './types'
export const sessionApi = {
  me: () => request<UserInfo>({ url: '/auth/me' }),
  logout: () => request<boolean>({ url: '/auth/logout', method: 'POST' })
}
