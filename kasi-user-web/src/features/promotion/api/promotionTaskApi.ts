import { apiRequest } from '../../../shared/api/httpClient'
import type {
  CreatePromotionTaskRequest,
  PromotionTaskPage,
} from './promotionTaskTypes'

export async function fetchPromotionTasks(
  params: Record<string, string | number | undefined> = {},
) {
  return (
    (await apiRequest<PromotionTaskPage>({
      method: 'GET',
      url: '/api/user/promotion/tasks',
      params,
    })) ?? { list: [], page: 1, size: 20, total: 0 }
  )
}

export async function createPromotionTasks(
  request: CreatePromotionTaskRequest,
) {
  return (
    (await apiRequest<PromotionTaskPage>({
      method: 'POST',
      url: '/api/user/promotion/tasks',
      data: request,
    })) ?? { list: [], page: 1, size: 20, total: 0 }
  )
}
