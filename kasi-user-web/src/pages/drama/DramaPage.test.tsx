import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DramaPage from './DramaPage'
import {
  createDramaDownloadTask,
  getPublishedDramaFreeContent,
  getPublishedDramas,
} from '../../features/dramas/dramasApi'

Object.defineProperty(HTMLMediaElement.prototype, 'load', {
  configurable: true,
  value: vi.fn(),
})

vi.mock('hls.js', () => {
  class MockHls {
    static Events = { ERROR: 'error' }
    static isSupported() {
      return true
    }

    loadSource = vi.fn()
    attachMedia = vi.fn()
    on = vi.fn()
    destroy = vi.fn()
  }

  return { default: MockHls }
})

const { navigateMock } = vi.hoisted(() => ({ navigateMock: vi.fn() }))

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => navigateMock,
}))

vi.mock('../../features/dramas/dramasApi', () => ({
  getPublishedDramas: vi.fn(),
  getPublishedDramaFreeContent: vi.fn(),
  createDramaDownloadTask: vi.fn(),
  getDramaDownloadTask: vi.fn(),
  downloadDramaTaskFile: vi.fn(),
}))
vi.mock('../../features/promotionLinks/promotionLinksApi', () => ({
  createPromotionLinks: vi.fn(),
}))

afterEach(() => {
  cleanup()
  navigateMock.mockReset()
  vi.clearAllMocks()
})

describe('DramaPage', () => {
  it('renders Starter-style filters, drama columns and pagination', async () => {
    const user = userEvent.setup()
    vi.mocked(getPublishedDramas).mockResolvedValueOnce({
      list: [
        {
          id: 1,
          providerId: 2,
          providerName: 'GoodShort',
          externalDramaId: 'drama-1',
          title: 'The Story',
          originalTitle: 'The Story',
          titleZh: '故事',
          description: '描述',
          coverUrl: 'https://example.com/story.jpg',
          labelNames: ['复仇', '离婚', '隐藏身份'],
          categoryName: '都市',
          language: 'ENGLISH',
          remoteRank: 1,
          dramaType: '短剧',
          novelType: null,
          novelSubType: null,
          commissionScopes: [],
          promotionDescription: '推广说明',
          remoteShowStatus: '1',
          localStatus: 'PUBLISHED',
          remoteCreatedAt: '2026-08-01T10:00:00',
          remoteUpdatedAt: '2026-08-02T10:00:00',
          lastSeenAt: null,
          updatedAt: null,
        },
      ],
      page: 1,
      size: 20,
      total: 1,
    })

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <DramaPage title="短剧" />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('故事')).toBeTruthy()
    expect(screen.getByText('The Story')).toBeTruthy()
    expect(screen.getByText('短剧信息')).toBeTruthy()
    expect(screen.queryByText('封面')).toBeNull()
    expect(screen.queryByText('短剧名称')).toBeNull()
    expect(screen.getByText('复仇')).toBeTruthy()
    expect(screen.getByText('离婚')).toBeTruthy()
    expect(screen.getByText('隐藏身份')).toBeTruthy()
    expect(screen.getByRole('img', { name: '故事' })).toBeTruthy()
    expect(screen.getByText('GoodShort')).toBeTruthy()
    expect(screen.getByText('都市')).toBeTruthy()
    expect(screen.getByText('英文')).toBeTruthy()
    expect(screen.getAllByText('推广说明').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('发布时间')).toBeTruthy()
    expect(screen.getByText('2026-08-01 10:00')).toBeTruthy()
    expect(screen.queryByText('更新时间')).toBeNull()
    expect(screen.queryByText('状态')).toBeNull()
    expect(screen.queryByText('已上架')).toBeNull()
    expect(screen.queryByText('2026-08-02 10:00')).toBeNull()
    expect(screen.getByText('操作')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: '创建推广任务' }))
    expect(navigateMock).toHaveBeenCalledWith({
      pathname: '/workspace/promotion-links',
      search: 'dramaId=1&providerId=2',
    })
    expect(screen.getByText('查询')).toBeTruthy()
    expect(screen.getByText('重置')).toBeTruthy()
  })

  it('opens an episode viewer and loads an HLS resource', async () => {
    const user = userEvent.setup()
    vi.mocked(getPublishedDramas).mockResolvedValueOnce({
      list: [
        {
          id: 1,
          providerId: 2,
          providerName: 'GoodShort',
          externalDramaId: 'drama-1',
          title: 'The Story',
          originalTitle: 'The Story',
          titleZh: '故事',
          description: '描述',
          coverUrl: null,
          labelNames: [],
          categoryName: '都市',
          language: 'ENGLISH',
          remoteRank: 1,
          dramaType: '短剧',
          novelType: null,
          novelSubType: null,
          commissionScopes: [],
          promotionDescription: null,
          remoteShowStatus: '1',
          localStatus: 'PUBLISHED',
          remoteCreatedAt: '2026-08-01T10:00:00',
          remoteUpdatedAt: null,
          lastSeenAt: null,
          updatedAt: null,
        },
      ],
      page: 1,
      size: 20,
      total: 1,
    })
    vi.mocked(getPublishedDramaFreeContent).mockResolvedValueOnce([
      {
        id: 101,
        sequenceNo: 1,
        title: '第1集',
        free: true,
        playUrl: 'https://cdn.example.com/episode-1.m3u8',
        downloadUrl: 'https://cdn.example.com/episode-1.m3u8',
      },
    ])

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <DramaPage title="短剧" />
      </QueryClientProvider>,
    )

    await user.click(await screen.findByRole('button', { name: '观看剧集' }))
    expect(await screen.findByText('剧集观看与素材下载')).toBeTruthy()
    expect(await screen.findByText('第1集')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: '播放' }))
    expect(await screen.findByTestId('drama-video')).toBeTruthy()
  })

  it('creates one download task for all available episodes', async () => {
    const user = userEvent.setup()
    vi.mocked(getPublishedDramas).mockResolvedValueOnce({
      list: [
        {
          id: 1,
          providerId: 2,
          providerName: 'GoodShort',
          externalDramaId: 'drama-1',
          title: 'The Story',
          originalTitle: 'The Story',
          titleZh: '故事',
          description: null,
          coverUrl: null,
          labelNames: [],
          categoryName: null,
          language: 'ENGLISH',
          remoteRank: null,
          dramaType: null,
          novelType: null,
          novelSubType: null,
          commissionScopes: [],
          promotionDescription: null,
          remoteShowStatus: '1',
          localStatus: 'PUBLISHED',
          remoteCreatedAt: null,
          remoteUpdatedAt: null,
          lastSeenAt: null,
          updatedAt: null,
        },
      ],
      page: 1,
      size: 20,
      total: 1,
    })
    vi.mocked(getPublishedDramaFreeContent).mockResolvedValueOnce([
      {
        id: 101,
        sequenceNo: 1,
        title: '第1集',
        free: true,
        playUrl: 'https://cdn.example.com/episode-1.m3u8',
        downloadUrl: 'https://cdn.example.com/episode-1.m3u8',
      },
      {
        id: 102,
        sequenceNo: 2,
        title: '第2集',
        free: true,
        playUrl: 'https://cdn.example.com/episode-2.m3u8',
        downloadUrl: 'https://cdn.example.com/episode-2.m3u8',
      },
    ])
    vi.mocked(createDramaDownloadTask).mockResolvedValueOnce({
      taskId: 9,
      status: 'PENDING',
      totalCount: 2,
      completedCount: 0,
      downloadUrl: null,
      errorMessage: null,
      expiresAt: '2026-08-29T10:00:00',
    })

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <DramaPage title="短剧" />
      </QueryClientProvider>,
    )

    await user.click(await screen.findByRole('button', { name: '观看剧集' }))
    await user.click(await screen.findByRole('button', { name: '下载全部' }))

    expect(createDramaDownloadTask).toHaveBeenCalledWith(1, [101, 102])
    expect(await screen.findByText('下载任务已创建')).toBeTruthy()
  })
})
