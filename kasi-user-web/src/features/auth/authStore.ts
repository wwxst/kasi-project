import { create } from 'zustand'
const TOKEN_KEY = 'kasi-user-access-token'

interface AuthState {
  accessToken: string | null
  setSession: (accessToken: string) => void
  clearSession: () => void
}

function readToken() {
  return typeof window === 'undefined'
    ? null
    : window.localStorage.getItem(TOKEN_KEY)
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: readToken(),
  setSession: (accessToken) => {
    window.localStorage.setItem(TOKEN_KEY, accessToken)
    set({ accessToken })
  },
  clearSession: () => {
    window.localStorage.removeItem(TOKEN_KEY)
    set({ accessToken: null })
  },
}))
