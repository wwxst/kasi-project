import { beforeEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../shared/api/httpClient'
import { getProjects } from './projectApi'

vi.mock('../../shared/api/httpClient', () => ({
  httpClient: { get: vi.fn() },
}))

beforeEach(() => vi.clearAllMocks())

describe('projectApi', () => {
  it('loads enabled promotion projects in backend order', async () => {
    const projects = [
      {
        id: 2,
        name: '项目B',
        coverImageUrl: '/uploads/promotion-projects/b.png',
        projectDocumentUrl: 'https://example.com/b',
        sortOrder: 20,
      },
      {
        id: 1,
        name: '项目A',
        coverImageUrl: '/uploads/promotion-projects/a.png',
        projectDocumentUrl: 'https://example.com/a',
        sortOrder: 30,
      },
    ]
    vi.mocked(httpClient.get).mockResolvedValueOnce({
      data: { code: 0, message: 'ok', data: projects },
    })

    await expect(getProjects()).resolves.toEqual(projects)
    expect(httpClient.get).toHaveBeenCalledWith('/api/user/promotion/projects')
  })

  it('throws the backend message when project loading fails', async () => {
    vi.mocked(httpClient.get).mockResolvedValueOnce({
      data: { code: 7001, message: '项目查询失败', data: null },
    })

    await expect(getProjects()).rejects.toThrow('项目查询失败')
  })
})
