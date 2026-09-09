import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PromotionProject } from './promotionProjectTypes'

const httpMocks = vi.hoisted(() => ({
  post: vi.fn(),
  put: vi.fn(),
}))

vi.mock('../../api/http', () => ({ httpClient: httpMocks }))

import {
  createPromotionProject,
  updatePromotionProject,
} from './promotionProjectApi'

const project: PromotionProject = {
  id: 1,
  name: '项目A',
  coverImageUrl: '/uploads/promotion-projects/a.png',
  projectDocumentUrl: 'https://example.com/a',
  status: 'ENABLED',
  sortOrder: 10,
  createdAt: '2026-09-09T10:00:00',
  updatedAt: '2026-09-09T10:00:00',
}

beforeEach(() => {
  vi.clearAllMocks()
  httpMocks.post.mockResolvedValue({
    data: { code: 0, message: 'ok', data: project },
  })
  httpMocks.put.mockResolvedValue({
    data: { code: 0, message: 'ok', data: project },
  })
})

describe('promotionProjectApi', () => {
  it('creates a multipart request with the selected cover', async () => {
    const cover = new File(['cover'], 'cover.png', { type: 'image/png' })

    await createPromotionProject({
      name: ' 项目A ',
      projectDocumentUrl: ' https://example.com/a ',
      status: 'ENABLED',
      sortOrder: 10,
      coverFile: cover,
    })

    const [path, body] = httpMocks.post.mock.calls[0]
    expect(path).toBe('/api/admin/promotion/projects')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('name')).toBe('项目A')
    expect((body as FormData).get('projectDocumentUrl')).toBe(
      'https://example.com/a',
    )
    expect((body as FormData).get('status')).toBe('ENABLED')
    expect((body as FormData).get('sortOrder')).toBe('10')
    expect((body as FormData).get('coverFile')).toBe(cover)
  })

  it('updates a project without a cover when it is unchanged', async () => {
    await updatePromotionProject(1, {
      name: '项目A修改',
      projectDocumentUrl: 'https://example.com/a',
      status: 'DISABLED',
      sortOrder: 20,
    })

    const [path, body] = httpMocks.put.mock.calls[0]
    expect(path).toBe('/api/admin/promotion/projects/1')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('name')).toBe('项目A修改')
    expect((body as FormData).get('status')).toBe('DISABLED')
    expect((body as FormData).get('sortOrder')).toBe('20')
    expect((body as FormData).has('coverFile')).toBe(false)
  })
})
