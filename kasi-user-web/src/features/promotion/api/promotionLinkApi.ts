import { apiRequest } from '../../../shared/api/httpClient'
import type { PromotionDramaPage, PromotionDramaQuery } from './dramaTypes'
import type {
  CreatePromotionLinkRequest,
  PromotionLink,
  PromotionLinkPage,
} from './promotionLinkTypes'

function requireData<T>(data: T | undefined, message: string): T {
  if (data === undefined) throw new Error(message)
  return data
}

export async function fetchPublishedPromotionDramas(
  query: PromotionDramaQuery = {},
): Promise<PromotionDramaPage> {
  const serverQuery = Object.fromEntries(
    Object.entries(query).filter(([key]) => key !== 'dramaType'),
  )
  const params = Object.fromEntries(
    Object.entries({ page: 1, size: 20, ...serverQuery }).filter(
      ([, value]) => value !== undefined && String(value) !== '',
    ),
  )
  return requireData(
    await apiRequest<PromotionDramaPage>({
      method: 'GET',
      url: '/api/user/promotion/dramas',
      params,
    }),
    '可推广短剧列表缺失',
  )
}

export async function fetchPromotionLinks(
  page = 1,
  size = 20,
): Promise<PromotionLinkPage> {
  return requireData(
    await apiRequest<PromotionLinkPage>({
      method: 'GET',
      url: '/api/user/promotion/links',
      params: { page, size },
    }),
    '推广链接列表缺失',
  )
}

export async function createPromotionLink(
  request: CreatePromotionLinkRequest,
): Promise<PromotionLink> {
  return requireData(
    await apiRequest<PromotionLink>({
      method: 'POST',
      url: '/api/user/promotion/links',
      data: request,
    }),
    '推广链接生成结果缺失',
  )
}
