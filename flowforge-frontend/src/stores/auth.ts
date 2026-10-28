import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import { TOKEN_KEY } from '@/api/client'
import type { UserProfile } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<UserProfile | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(username: string, password: string) {
    const result = await authApi.login(username, password)
    token.value = result.token
    user.value = result.user
    localStorage.setItem(TOKEN_KEY, result.token)
  }

  /** 刷新页面后恢复用户信息；失败说明 token 已失效，直接清掉。 */
  async function restore() {
    if (!token.value || user.value) {
      return
    }
    try {
      user.value = await authApi.fetchMe()
    } catch {
      logout()
    }
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  return { token, user, isLoggedIn, login, restore, logout }
})
