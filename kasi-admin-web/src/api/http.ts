import axios from 'axios'
import { useAuthStore } from '../features/auth/authStore'

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 15_000,
})

httpClient.interceptors.request.use((config) => {
  const accessToken = useAuthStore.getState().accessToken
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

httpClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      useAuthStore.getState().accessToken
    ) {
      useAuthStore.getState().clearSession()
    }
    return Promise.reject(error)
  },
)

export function isUnauthorizedError(error: unknown) {
  return axios.isAxiosError(error) && error.response?.status === 401
}
