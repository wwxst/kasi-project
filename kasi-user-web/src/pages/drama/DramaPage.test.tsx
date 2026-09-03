import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MessagePlugin } from 'tdesign-react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DramaPage from './DramaPage'
import {
  getPublishedDramaFreeContent,
  getPublishedDramas,
} from '../../features/dramas/dramasApi'
import { createPromotionLinks } from '../../features/promotionLinks/promotionLinksApi'

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
    vi.mocked(getPublishedDramaFreeContent).mockResolvedValueOnce([])
    await user.click(screen.getByRole('button', { name: '创建推广任务' }))
    expect(navigateMock).not.toHaveBeenCalled()
    expect(
      await screen.findByRole('button', { name: '创建链接和口令' }),
    ).toBeTruthy()
    expect(screen.getAllByRole('img', { name: '故事' })).toHaveLength(2)
    expect(screen.getByText('描述')).toBeTruthy()
    expect(screen.getAllByText('推广说明')).toHaveLength(2)
    expect(screen.getByText('查询')).toBeTruthy()
    expect(screen.getByText('重置')).toBeTruthy()
  })

  it('navigates to promotion tasks only after links are generated', async () => {
    const user = userEvent.setup()
    vi.mocked(getPublishedDramas).mockResolvedValueOnce({
      list: [promotionDrama()],
      page: 1,
      size: 20,
      total: 1,
    })
    vi.mocked(getPublishedDramaFreeContent).mockResolvedValueOnce([])
    vi.mocked(createPromotionLinks).mockResolvedValueOnce({
      batchNo: 'batch-1',
      requestKey: 'request-1',
      links: [],
      complete: true,
    })
    vi.spyOn(MessagePlugin, 'success').mockResolvedValue({} as never)

    renderDramaPage()

    await user.click(
      await screen.findByRole('button', { name: '创建推广任务' }),
    )
    await user.click(
      await screen.findByRole('button', { name: '创建链接和口令' }),
    )
    await user.click(screen.getByPlaceholderText('请选择媒体平台'))
    await user.click(await screen.findByText('TikTok'))
    await user.click(screen.getByPlaceholderText('请选择链接类型'))
    await user.click(await screen.findByText('OneLink'))
    await user.type(screen.getByPlaceholderText('请输入推广名称'), '夏季推广')
    await user.click(screen.getByRole('button', { name: '生成链接' }))

    await waitFor(() =>
      expect(createPromotionLinks).toHaveBeenCalledWith({
        providerId: 2,
        dramaId: 1,
        mediaTypes: ['TIKTOK'],
        linkVariant: 'ONELINK',
        campaignName: '夏季推广',
      }),
    )
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/workspace/promotion-links'),
    )
  })

  it('stays on the drama page when link generation fails', async () => {
    const user = userEvent.setup()
    vi.mocked(getPublishedDramas).mockResolvedValueOnce({
      list: [promotionDrama()],
      page: 1,
      size: 20,
      total: 1,
    })
    vi.mocked(getPublishedDramaFreeContent).mockResolvedValueOnce([])
    vi.mocked(createPromotionLinks).mockRejectedValueOnce(new Error('生成失败'))
    vi.spyOn(MessagePlugin, 'error').mockResolvedValue({} as never)

    renderDramaPage()

    await user.click(
      await screen.findByRole('button', { name: '创建推广任务' }),
    )
    await user.click(
      await screen.findByRole('button', { name: '创建链接和口令' }),
    )
    await user.click(screen.getByPlaceholderText('请选择媒体平台'))
    await user.click(await screen.findByText('TikTok'))
    await user.click(screen.getByRole('button', { name: '生成链接' }))

    await waitFor(() => expect(createPromotionLinks).toHaveBeenCalledTimes(1))
    expect(screen.getByRole('button', { name: '生成链接' })).toBeTruthy()
    expect(navigateMock).not.toHaveBeenCalled()
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

    await user.click(
      await screen.findByRole('button', { name: '创建推广任务' }),
    )
    expect(await screen.findByText('剧集观看与素材下载')).toBeTruthy()
    expect(await screen.findByText('第1集')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: '播放' }))
    expect(await screen.findByTestId('drama-video')).toBeTruthy()
  })

  it('downloads all available episodes directly in the browser', async () => {
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
        playUrl: 'https://cdn.example.com/episode-1.mp4',
        downloadUrl: 'https://cdn.example.com/episode-1.mp4',
      },
      {
        id: 102,
        sequenceNo: 2,
        title: '第2集',
        free: true,
        playUrl: 'https://cdn.example.com/episode-2.mp4',
        downloadUrl: 'https://cdn.example.com/episode-2.mp4',
      },
    ])
    const anchors: HTMLAnchorElement[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        blob: vi
          .fn()
          .mockResolvedValue(new Blob(['video'], { type: 'video/mp4' })),
      }),
    )
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:episode')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const originalCreateElement = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tagName) => {
      const element = originalCreateElement(tagName)
      if (tagName === 'a') {
        anchors.push(element as HTMLAnchorElement)
        vi.spyOn(element as HTMLAnchorElement, 'click').mockImplementation(
          () => {},
        )
      }
      return element
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

    await user.click(
      await screen.findByRole('button', { name: '创建推广任务' }),
    )
    await user.click(await screen.findByRole('button', { name: '下载全部' }))

    await waitFor(() => expect(anchors).toHaveLength(2))
    expect(fetch).toHaveBeenCalledTimes(2)
    expect(anchors.map((anchor) => anchor.href)).toEqual([
      'blob:episode',
      'blob:episode',
    ])
    expect(anchors.map((anchor) => anchor.download)).toEqual([
      '故事-第01集.mp4',
      '故事-第02集.mp4',
    ])
  })
})

function promotionDrama() {
  return {
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
    localStatus: 'PUBLISHED' as const,
    remoteCreatedAt: null,
    remoteUpdatedAt: null,
    lastSeenAt: null,
    updatedAt: null,
  }
}

function renderDramaPage() {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <DramaPage title="短剧" />
    </QueryClientProvider>,
  )
}
