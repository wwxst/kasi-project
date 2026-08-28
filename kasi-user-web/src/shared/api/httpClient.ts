import axios from 'axios'
import { MessagePlugin } from 'tdesign-react/es/message/index.js'
import { useAuthStore } from '../../features/auth/authStore'

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 10000,
})

export function isUnauthorizedError(error: unknown) {
  return axios.isAxiosError(error) && error.response?.status === 401
}

export function isHandledRequestError(error: unknown) {
  return axios.isAxiosError(error)
}

function getRequestErrorMessage(error: unknown) {
  if (!axios.isAxiosError(error)) return '请求失败，请稍后重试'
  if (error.response?.status === 503) return '服务暂时不可用，请稍后重试'
  if (!error.response) return '网络请求失败，请检查网络后重试'
  return '请求失败，请稍后重试'
}

httpClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (isUnauthorizedError(error)) {
      useAuthStore.getState().clearSession()
    } else if (isHandledRequestError(error)) {
      void MessagePlugin.error(getRequestErrorMessage(error))
    }
    return Promise.reject(error)
  },
)
