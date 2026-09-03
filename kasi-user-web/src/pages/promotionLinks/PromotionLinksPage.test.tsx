import { cleanup, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PromotionLinksPage from './PromotionLinksPage'
import * as promotionLinksApi from '../../features/promotionLinks/promotionLinksApi'
import * as dramasApi from '../../features/dramas/dramasApi'

vi.mock('../../features/promotionLinks/promotionLinksApi', async () => {
  const actual = await vi.importActual<
    typeof import('../../features/promotionLinks/promotionLinksApi')
  >('../../features/promotionLinks/promotionLinksApi')
  return {
    ...actual,
    getPromotionLinks: vi.fn(),
  }
})

vi.mock('../../features/dramas/dramasApi', () => ({
  getPublishedDramaDetail: vi.fn(),
  getPublishedDramaFreeContent: vi.fn(),
}))

afterEach(() => cleanup())

function renderPage() {
  return render(
    <MemoryRouter
      initialEntries={['/workspace/promotion-links?dramaId=7&providerId=2']}
    >
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <PromotionLinksPage title="推广任务" />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('PromotionLinksPage', () => {
  it('shows created codes and links without generation or status controls', async () => {
    vi.mocked(promotionLinksApi.getPromotionLinks).mockResolvedValue({
      list: [
        {
          id: 1,
          providerId: 2,
          providerName: 'GoodShort',
          dramaId: 7,
          dramaTitle: 'Abandoned at the Altar',
          batchNo: 'batch-1',
          requestKey: 'request-1',
          mediaType: 'TIKTOK',
          linkVariant: 'LANDING',
          campaignName: '夏季推广',
          trackingNo: 'track-1',
          externalCode: 'CODE-123',
          shareUrl: 'https://example.com/share',
          customParams: null,
          status: 'SUCCESS',
          lastErrorCode: null,
          lastErrorMessage: null,
          createdAt: '2026-08-27T10:00:00',
          updatedAt: '2026-08-27T10:00:00',
        },
      ],
      page: 1,
      size: 20,
      total: 1,
    })
    vi.mocked(dramasApi.getPublishedDramaDetail).mockResolvedValue({
      ...promotionDramaDetail(),
      contents: [],
    })
    vi.mocked(dramasApi.getPublishedDramaFreeContent).mockResolvedValue([])

    renderPage()

    expect(await screen.findByText('Abandoned at the Altar')).toBeTruthy()
    expect(screen.getByText('创建时间')).toBeTruthy()
    expect(screen.getByText('2026-08-27 10:00:00')).toBeTruthy()
    expect(screen.getByText('推广名称')).toBeTruthy()
    expect(screen.getByText('夏季推广')).toBeTruthy()
    expect(screen.queryByText('批次')).toBeNull()
    expect(screen.queryByText('batch-1')).toBeNull()
    expect(screen.getByText('CODE-123')).toBeTruthy()
    expect(
      screen
        .getByRole('link', { name: 'https://example.com/share' })
        .getAttribute('href'),
    ).toBe('https://example.com/share')
    expect(screen.queryByRole('button', { name: '生成推广链接' })).toBeNull()
    expect(screen.queryByRole('button', { name: '创建链接和口令' })).toBeNull()
    expect(dramasApi.getPublishedDramaDetail).not.toHaveBeenCalled()
    expect(dramasApi.getPublishedDramaFreeContent).not.toHaveBeenCalled()
    expect(screen.queryByText('状态')).toBeNull()
    expect(screen.queryByText('已完成')).toBeNull()
  })
})

function promotionDramaDetail() {
  return {
    id: 7,
    providerId: 2,
    providerName: 'GoodShort',
    externalDramaId: 'drama-7',
    title: 'Abandoned at the Altar',
    originalTitle: 'Abandoned at the Altar',
    titleZh: null,
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
