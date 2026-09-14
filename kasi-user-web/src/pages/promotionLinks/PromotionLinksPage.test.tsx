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

function codeRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    providerId: 2,
    providerName: 'GoodShort',
    dramaId: 7,
    dramaTitle: 'Abandoned at the Altar',
    campaignName: '夏季推广',
    mediaType: 'TIKTOK' as const,
    externalCode: 'CODE-123',
    landingUrl: 'https://example.com/landing',
    oneLinkUrl: 'https://example.com/one',
    analyticsConflict: false,
    clickCount: 11,
    attributedUserCount: 12,
    newRegisteredUserCount: 13,
    newPaidUserCount: 14,
    newMemberUserCount: 15,
    paidUserCount: 16,
    orderCount: 17,
    createdAt: '2026-08-27T10:00:00',
    ...overrides,
  }
}

describe('PromotionLinksPage', () => {
  it('shows one code row with both links and a single set of metrics', async () => {
    vi.mocked(promotionLinksApi.getPromotionLinks).mockResolvedValue({
      list: [codeRow()],
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
    expect(screen.getByText('TikTok')).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: '推广链接' })).toBeTruthy()
    expect(screen.queryByRole('columnheader', { name: '落地页' })).toBeNull()
    expect(screen.queryByRole('columnheader', { name: 'OneLink' })).toBeNull()
    expect(
      screen
        .getByRole('link', { name: 'https://example.com/landing' })
        .getAttribute('href'),
    ).toBe('https://example.com/landing')
    expect(
      screen
        .getByRole('link', { name: 'https://example.com/one' })
        .getAttribute('href'),
    ).toBe('https://example.com/one')
    expect(screen.getByRole('button', { name: '复制落地页链接' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '复制OneLink链接' })).toBeTruthy()
    expect(screen.getByText('落地页')).toBeTruthy()
    expect(screen.getByText('OneLink')).toBeTruthy()
    expect(screen.getByText('https://example.com/landing')).toBeTruthy()
    expect(screen.getByText('https://example.com/one')).toBeTruthy()
    expect(screen.queryByText('归因冲突')).toBeNull()
    expect(screen.queryByRole('button', { name: '生成推广链接' })).toBeNull()
    expect(screen.queryByRole('button', { name: '创建链接和口令' })).toBeNull()
    expect(dramasApi.getPublishedDramaDetail).not.toHaveBeenCalled()
    expect(dramasApi.getPublishedDramaFreeContent).not.toHaveBeenCalled()
    expect(screen.queryByText('状态')).toBeNull()
    expect(screen.queryByText('已完成')).toBeNull()
    expect(screen.queryByText('链接类型')).toBeNull()
    expect(screen.getByText('点击数')).toBeTruthy()
    expect(screen.getByText('归因用户数')).toBeTruthy()
    expect(screen.getByText('新注册人数')).toBeTruthy()
    expect(screen.getByText('新充值人数')).toBeTruthy()
    expect(screen.getByText('新会员人数')).toBeTruthy()
    expect(screen.getByText('充值用户数')).toBeTruthy()
    expect(screen.getByText('订单数')).toBeTruthy()
    expect(screen.queryByText('充值金额')).toBeNull()
    expect(screen.queryByText('操作')).toBeNull()
    for (const value of ['11', '12', '13', '14', '15', '16', '17']) {
      expect(screen.getByText(value)).toBeTruthy()
    }
  })

  it('marks a cross-media code conflict and hides its conversion metrics', async () => {
    vi.mocked(promotionLinksApi.getPromotionLinks).mockResolvedValue({
      list: [
        codeRow({
          mediaType: null,
          analyticsConflict: true,
          clickCount: null,
          attributedUserCount: null,
          newRegisteredUserCount: null,
          newPaidUserCount: null,
          newMemberUserCount: null,
          paidUserCount: null,
          orderCount: null,
        }),
      ],
      page: 1,
      size: 20,
      total: 1,
    })

    renderPage()

    expect(await screen.findByText('归因冲突')).toBeTruthy()
    expect(screen.getByText('CODE-123')).toBeTruthy()
    expect(screen.queryByText('TikTok')).toBeNull()
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(7)
    for (const value of ['11', '12', '13', '14', '15', '16', '17']) {
      expect(screen.queryByText(value)).toBeNull()
    }
  })

  it('keeps a labeled placeholder for a missing historical link variant', async () => {
    vi.mocked(promotionLinksApi.getPromotionLinks).mockResolvedValue({
      list: [codeRow({ oneLinkUrl: null })],
      page: 1,
      size: 20,
      total: 1,
    })

    renderPage()

    expect(
      await screen.findByRole('link', {
        name: 'https://example.com/landing',
      }),
    ).toBeTruthy()
    expect(screen.getByText('落地页')).toBeTruthy()
    expect(screen.getByText('OneLink')).toBeTruthy()
    expect(screen.getByText('暂无')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '复制OneLink链接' })).toBeNull()
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
    languageLabel: '英语',
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
