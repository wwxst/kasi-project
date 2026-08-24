import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'

export interface AuthSession {
  accessToken: string
  expiresAt: number
}

type BootstrapStatus = 'idle' | 'checking' | 'ready'

interface AuthState {
  accessToken: string | null
  expiresAt: number | null
  bootstrapStatus: BootstrapStatus
  setSession: (session: AuthSession) => void
  clearSession: () => void
  getValidAccessToken: () => string | null
  setBootstrapStatus: (status: BootstrapStatus) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      expiresAt: null,
      bootstrapStatus: 'idle',
      setSession: ({ accessToken, expiresAt }) =>
        set({ accessToken, expiresAt }),
      clearSession: () => set({ accessToken: null, expiresAt: null }),
      getValidAccessToken: () => {
        const { accessToken, expiresAt } = get()
        if (!accessToken || !expiresAt || expiresAt <= Date.now()) {
          set({ accessToken: null, expiresAt: null })
          return null
        }
        return accessToken
      },
      setBootstrapStatus: (bootstrapStatus) => set({ bootstrapStatus }),
    }),
    {
      name: 'kasi-user-auth',
      storage: createJSONStorage(() => sessionStorage),
      partialize: ({ accessToken, expiresAt }) => ({ accessToken, expiresAt }),
    },
  ),
)
