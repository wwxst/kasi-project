import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { HttpResponse, http } from 'msw'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { PromotionTaskPage } from './PromotionTaskPage'
import { server } from '../../test/server'

const task = {
  id: 9,
  taskName: '夏季推广',
  mediaType: 'TIKTOK',
  providerName: 'GoodShort',
  dramaTitle: '重返九零',
  trackingNo: 'tracking-9',
  externalCode: null,
  directUrl: null,
  status: 'PENDING',
  codeSearchCount: 2,
  directClickCount: 3,
  appClickCount: 4,
  leadCount: 5,
  orderAmount: '0.00',
  orderCount: 0,
  adAmount: '0.00',
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <PromotionTaskPage />
    </QueryClientProvider>,
  )
}

describe('PromotionTaskPage', () => {
  it('loads tasks and displays real statistics', async () => {
    server.use(
      http.get('/api/user/promotion/tasks', () =>
        HttpResponse.json({
          code: 0,
          message: 'success',
          data: { list: [task], page: 1, size: 20, total: 1 },
        }),
      ),
    )

    renderPage()

    expect(
      await screen.findByRole('heading', { name: '\u63a8\u5e7f\u4efb\u52a1' }),
    ).toBeInTheDocument()
    expect(
      await screen.findByText('\u590f\u5b63\u63a8\u5e7f'),
    ).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
  })

  it('sends task filters only after querying', async () => {
    let requestUrl = ''
    server.use(
      http.get('/api/user/promotion/tasks', ({ request }) => {
        requestUrl = request.url
        return HttpResponse.json({
          code: 0,
          message: 'success',
          data: { list: [], page: 1, size: 20, total: 0 },
        })
      }),
    )

    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: '\u63a8\u5e7f\u4efb\u52a1' })
    await user.type(
      screen.getByPlaceholderText('\u8bf7\u8f93\u5165\u4efb\u52a1\u540d\u79f0'),
      'Search',
    )
    await user.click(screen.getByRole('button', { name: '\u67e5\u8be2' }))

    await waitFor(() =>
      expect(new URL(requestUrl).searchParams.get('taskName')).toBe('Search'),
    )
  })

  it('shows an empty state when no tasks are available', async () => {
    server.use(
      http.get('/api/user/promotion/tasks', () =>
        HttpResponse.json({
          code: 0,
          message: 'success',
          data: { list: [], page: 1, size: 20, total: 0 },
        }),
      ),
    )

    renderPage()
    expect(
      await screen.findByText('\u6682\u65e0\u63a8\u5e7f\u4efb\u52a1'),
    ).toBeInTheDocument()
  })
})
