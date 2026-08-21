import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { AdminInfo, AdminLoginResponse } from './authTypes'

interface AuthState {
  accessToken: string | null
  admin: AdminInfo | null
  setSession: (session: AdminLoginResponse) => void
  updateAdmin: (admin: AdminInfo) => void
  clearSession: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      admin: null,
      setSession: (session) =>
        set({ accessToken: session.accessToken, admin: session.admin }),
      updateAdmin: (admin) => set({ admin }),
      clearSession: () => set({ accessToken: null, admin: null }),
    }),
    {
      name: 'kasi-admin-auth',
      storage: createJSONStorage(() => sessionStorage),
    },
  ),
)
