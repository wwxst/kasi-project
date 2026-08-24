import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'
import { fetchPublishedPromotionDramas } from './promotionLinkApi'
import { server } from '../../../test/server'

describe('promotionLinkApi', () => {
  it('serializes drama library filters and pagination', async () => {
    let requestUrl = ''
    server.use(
      http.get('/api/user/promotion/dramas', ({ request }) => {
        requestUrl = request.url
        return HttpResponse.json({
          code: 0,
          message: 'success',
          data: { list: [], page: 2, size: 10, total: 0 },
        })
      }),
    )

    await fetchPublishedPromotionDramas({
      title: 'Magic',
      providerId: 2,
      language: 'ENGLISH',
      dramaType: 'LOCAL',
      localStatus: 'PUBLISHED',
      page: 2,
      size: 10,
    })

    const query = new URL(requestUrl).searchParams
    expect(query.get('title')).toBe('Magic')
    expect(query.get('providerId')).toBe('2')
    expect(query.get('language')).toBe('ENGLISH')
    expect(query.has('dramaType')).toBe(false)
    expect(query.get('localStatus')).toBe('PUBLISHED')
    expect(query.get('page')).toBe('2')
    expect(query.get('size')).toBe('10')
  })

  it('omits empty optional filters and keeps default pagination', async () => {
    let requestUrl = ''
    server.use(
      http.get('/api/user/promotion/dramas', ({ request }) => {
        requestUrl = request.url
        return HttpResponse.json({
          code: 0,
          message: 'success',
          data: { list: [], page: 1, size: 20, total: 0 },
        })
      }),
    )

    await fetchPublishedPromotionDramas({ title: '' })

    const query = new URL(requestUrl).searchParams
    expect(query.get('page')).toBe('1')
    expect(query.get('size')).toBe('20')
    expect(query.has('title')).toBe(false)
    expect(query.has('providerId')).toBe(false)
  })
})
