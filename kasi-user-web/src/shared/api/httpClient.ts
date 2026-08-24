import axios, { type AxiosRequestConfig } from 'axios'
import { useAuthStore } from '../../features/auth/model/authStore'
import { ApiError } from './ApiError'
import type { ApiResponse } from './types'

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 15_000,
})

httpClient.interceptors.request.use((config) => {
  const accessToken = useAuthStore.getState().getValidAccessToken()
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

export async function apiRequest<T>(
  config: AxiosRequestConfig,
): Promise<T | undefined> {
  try {
    const response = await httpClient.request<ApiResponse<T>>(config)
    const payload = response.data
    if (payload.code !== 0) {
      throw new ApiError(payload.message || '请求失败', payload.code)
    }
    return payload.data
  } catch (error) {
    if (error instanceof ApiError) {
      throw error
    }

    if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
      const status = error.response?.status
      const body = error.response?.data
      const code = body?.code ?? status ?? 500
      const retryable = status === 503 || code === 1007 || status === 500

      if (status === 401) {
        useAuthStore.getState().clearSession()
      }

      throw new ApiError(
        body?.message || '请求失败，请稍后重试',
        code,
        status,
        retryable,
      )
    }

    throw error
  }
}
