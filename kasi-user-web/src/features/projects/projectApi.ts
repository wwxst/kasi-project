import { httpClient } from '../../shared/api/httpClient'
import type { ApiResponse } from '../../shared/api/types'
import type { Project } from './projectTypes'

function unwrap<T>(response: { data: ApiResponse<T> }) {
  if (response.data.code !== 0 || response.data.data == null) {
    throw new Error(response.data.message || '请求失败')
  }
  return response.data.data
}

export async function getProjects() {
  const response = await httpClient.get<ApiResponse<Project[]>>(
    '/api/user/promotion/projects',
  )
  return unwrap(response)
}
