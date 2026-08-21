import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type {
  ScheduledTask,
  ScheduledTaskCode,
  UpdateScheduledTaskRequest,
} from './scheduledTaskTypes'

const basePath = '/api/admin/system/scheduled-tasks'

export async function listScheduledTasks(): Promise<ScheduledTask[]> {
  const response = await httpClient.get<ApiResponse<ScheduledTask[]>>(basePath)
  return unwrapApiResponse(response.data)
}

export async function updateScheduledTask(
  taskCode: ScheduledTaskCode,
  request: UpdateScheduledTaskRequest,
): Promise<ScheduledTask> {
  const response = await httpClient.put<ApiResponse<ScheduledTask>>(
    `${basePath}/${taskCode}`,
    request,
  )
  return unwrapApiResponse(response.data)
}
