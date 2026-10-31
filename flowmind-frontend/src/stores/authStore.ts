import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { sessionApi } from '@/api/sessionApi'
import type { UserInfo } from '@/api/types'
export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('flowmind.token') ?? '')
  const user = ref<UserInfo | null>(null)
  const authenticated = computed(() => Boolean(token.value && user.value))
  function setSession(value: string, info: UserInfo) { token.value = value; user.value = info; localStorage.setItem('flowmind.token', value) }
  async function loadUser() { user.value = await sessionApi.me(); return user.value }
  function clear() { token.value = ''; user.value = null; localStorage.removeItem('flowmind.token'); localStorage.removeItem('flowmind.user') }
  async function logout() { await sessionApi.logout(); clear() }
  return { token, user, authenticated, setSession, loadUser, logout, clear }
})
