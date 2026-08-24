import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { HttpResponse, http } from 'msw'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PromotionLinkPage } from './PromotionLinkPage'
import { server } from '../../test/server'

const dramas = {
  list: [
    {
      id: 42,
      providerId: 1,
      providerName: 'GoodShort',
      externalDramaId: 'book-42',
      title: '\u91cd\u8fd4\u4e5d\u96f6',
      originalTitle: 'Back to 1990',
      description: 'Drama introduction',
      coverUrl: null,
      language: 'ENGLISH',
      dramaType: 'LOCAL_DRAMA',
      commissionScopes: ['ORDER', 'AD'],
      promotionDescription:
        '1. \u5355\u4e2a\u89c6\u9891\u5efa\u8bae\u4e0d\u8d85\u8fc717\u5206\u949f\n2. \u70b9\u51fb\u521b\u5efa\u63a8\u5e7f\u4efb\u52a1\u83b7\u53d6',
      remoteUpdatedAt: '2026-08-23T20:24:46',
      remoteShowStatus: '1',
      localStatus: 'PUBLISHED',
    },
  ],
  page: 1,
  size: 20,
  total: 1,
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <PromotionLinkPage />
    </QueryClientProvider>,
  )
}

function mockDramaList() {
  server.use(
    http.get('/api/user/promotion/dramas', () =>
      HttpResponse.json({ code: 0, message: 'success', data: dramas }),
    ),
    http.get('/api/user/promotion/media-accounts', () =>
      HttpResponse.json({
        code: 0,
        message: 'success',
        data: [
          {
            id: 8,
            mediaType: 'TIKTOK',
            externalAccountId: 'creator-8',
            accountName: 'Creator 8',
            status: 1,
            filings: [{ providerId: 1, status: 'APPROVED' }],
          },
        ],
      }),
    ),
    http.get('/api/user/promotion/links', () =>
      HttpResponse.json({
        code: 0,
        message: 'success',
        data: { list: [], page: 1, size: 20, total: 0 },
      }),
    ),
  )
}

describe('PromotionLinkPage', () => {
  beforeEach(() => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue(
      '123e4567-e89b-12d3-a456-426614174000',
    )
  })

  it('loads the published drama library and approved media accounts', async () => {
    mockDramaList()
    renderPage()
    expect(
      await screen.findByRole('heading', { name: '\u521b\u5efa\u63a8\u5e7f' }),
    ).toBeInTheDocument()
    expect(
      await screen.findByText('\u91cd\u8fd4\u4e5d\u96f6'),
    ).toBeInTheDocument()
    expect(screen.getByText('GoodShort')).toBeInTheDocument()
    expect(screen.getByText('\u8ba2\u5355')).toBeInTheDocument()
    expect(screen.getByText('\u5e7f\u544a')).toBeInTheDocument()
    expect(screen.getByText('2026-08-23 20:24:46')).toBeInTheDocument()
    expect(
      screen.getByText(
        /\u5355\u4e2a\u89c6\u9891\u5efa\u8bae\u4e0d\u8d85\u8fc717\u5206\u949f/,
      ),
    ).toBeInTheDocument()
    expect(screen.getByText('\u82f1\u8bed')).toBeInTheDocument()
    expect(screen.getByText('\u672c\u571f\u5267')).toBeInTheDocument()
  })

  it('creates one real promotion link with the selected media account', async () => {
    let payload: unknown
    mockDramaList()
    server.use(
      http.post('/api/user/promotion/links', async ({ request }) => {
        payload = await request.json()
        return HttpResponse.json({
          code: 0,
          message: 'success',
          data: {
            id: 9,
            providerId: 1,
            dramaId: 42,
            mediaAccountId: 8,
            landingType: 'DEFAULT',
            status: 'SUCCESS',
            trackingNo: 'tracking-9',
            externalCode: 'A12345',
            shareUrl: 'https://demo.test/link-9',
          },
        })
      }),
    )

    renderPage()
    const user = userEvent.setup()
    await user.click(
      await screen.findByRole('button', {
        name: '\u751f\u6210\u63a8\u5e7f\u94fe\u63a5',
      }),
    )
    expect(
      await screen.findByText('\u751f\u6210\u94fe\u63a5\u548c\u53e3\u4ee4'),
    ).toBeInTheDocument()
    await user.click(screen.getByLabelText('\u5a92\u4f53\u8d26\u53f7'))
    await user.click(await screen.findByText('TikTok / Creator 8'))
    await user.type(
      screen.getByPlaceholderText('\u8bf7\u8f93\u5165\u63a8\u5e7f\u540d\u79f0'),
      '\u590f\u5b63\u63a8\u5e7f',
    )
    await user.click(
      document.querySelector('.t-dialog__confirm') as HTMLElement,
    )

    await waitFor(() =>
      expect(payload).toEqual({
        providerId: 1,
        dramaId: 42,
        mediaAccountId: 8,
        campaignName: '\u590f\u5b63\u63a8\u5e7f',
        requestKey: '123e4567-e89b-12d3-a456-426614174000',
        landingType: 'DEFAULT',
      }),
    )
    expect(await screen.findByText(/tracking-9/)).toBeInTheDocument()
    expect(screen.getByText(/A12345/)).toBeInTheDocument()
  })

  it('filters the drama library and opens a detail drawer', async () => {
    let requestUrl = ''
    server.use(
      http.get('/api/user/promotion/dramas', ({ request }) => {
        requestUrl = request.url
        return HttpResponse.json({ code: 0, message: 'success', data: dramas })
      }),
    )

    renderPage()
    const user = userEvent.setup()
    await screen.findByText('\u91cd\u8fd4\u4e5d\u96f6')
    await user.type(
      screen.getByPlaceholderText('\u8bf7\u8f93\u5165\u77ed\u5267\u540d\u79f0'),
      'Magic',
    )
    await user.click(screen.getByRole('button', { name: '\u67e5\u8be2' }))
    await waitFor(() =>
      expect(new URL(requestUrl).searchParams.get('title')).toBe('Magic'),
    )
    await user.click(
      screen.getByRole('button', { name: '\u67e5\u770b\u8be6\u60c5' }),
    )
    expect(
      await screen.findByText('\u77ed\u5267\u8be6\u60c5'),
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        '\u5267\u96c6\u4fe1\u606f\u7531\u540e\u7aef\u540c\u6b65\u540e\u63d0\u4f9b',
      ),
    ).toBeInTheDocument()
  })

  it('shows an empty state when no dramas are available', async () => {
    server.use(
      http.get('/api/user/promotion/dramas', () =>
        HttpResponse.json({
          code: 0,
          message: 'success',
          data: { list: [], page: 1, size: 20, total: 0 },
        }),
      ),
    )
    renderPage()
    expect(
      await screen.findByText('\u6682\u65e0\u53ef\u63a8\u5e7f\u77ed\u5267'),
    ).toBeInTheDocument()
  })
})
