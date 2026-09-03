import { describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../shared/api/httpClient'
import { getPublishedDramas } from './dramasApi'

vi.mock('../../shared/api/httpClient', () => ({
  httpClient: { get: vi.fn() },
}))

describe('dramasApi', () => {
  it('unwraps the published drama page response', async () => {
    vi.mocked(httpClient.get).mockResolvedValueOnce({
      data: {
        code: 0,
        message: '成功',
        data: { list: [], page: 1, size: 20, total: 0 },
      },
    })

    await expect(
      getPublishedDramas({ page: 1, size: 20, title: '星' }),
    ).resolves.toEqual({ list: [], page: 1, size: 20, total: 0 })
    expect(httpClient.get).toHaveBeenCalledWith('/api/user/promotion/dramas', {
      params: { page: 1, size: 20, title: '星' },
    })
  })
})
