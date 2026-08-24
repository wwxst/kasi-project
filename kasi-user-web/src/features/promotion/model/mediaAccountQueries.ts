import { useQuery } from '@tanstack/react-query'
import { fetchMediaAccount, fetchMediaAccounts } from '../api/mediaAccountApi'

export const mediaAccountsQueryKey = ['media-accounts'] as const

export function useMediaAccounts() {
  return useQuery({
    queryKey: mediaAccountsQueryKey,
    queryFn: fetchMediaAccounts,
    staleTime: 15_000,
  })
}

export function useMediaAccount(id: number | null, enabled = true) {
  return useQuery({
    queryKey: [...mediaAccountsQueryKey, id],
    queryFn: () => fetchMediaAccount(id as number),
    enabled: enabled && id !== null,
    staleTime: 15_000,
  })
}
