import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { HttpResponse, http } from 'msw'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { MediaAccountFilingPage } from './MediaAccountFilingPage'
import { server } from '../../test/server'

const accounts = [
  {
    id: 1,
    mediaType: 'TIKTOK',
    externalAccountId: 'creator-1',
    accountName: 'Creator 1',
    accountLink: 'https://tiktok.com/@creator-1',
    status: 1,
    filings: [{ providerId: 1, providerName: 'GoodShort', status: 'PENDING' }],
  },
  {
    id: 2,
    mediaType: 'YOUTUBE',
    externalAccountId: 'channel-2',
    accountName: 'Channel 2',
    accountLink: null,
    status: 1,
    filings: [{ providerId: 1, providerName: 'GoodShort', status: 'APPROVED' }],
  },
  {
    id: 3,
    mediaType: 'INSTAGRAM',
    externalAccountId: 'profile-3',
    accountName: 'Profile 3',
    accountLink: null,
    status: 1,
    filings: [
      {
        providerId: 1,
        providerName: 'GoodShort',
        status: 'FAILED',
        lastErrorMessage: '账号未通过平台审核',
      },
    ],
  },
]

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MediaAccountFilingPage />
    </QueryClientProvider>,
  )
}

describe('MediaAccountFilingPage', () => {
  it('renders the table and maps filing statuses', async () => {
    server.use(
      http.get('/api/user/promotion/media-accounts', () =>
        HttpResponse.json({ code: 0, message: 'success', data: accounts }),
      ),
    )

    renderPage()

    expect(
      await screen.findByRole('heading', { name: '账号报白' }),
    ).toBeInTheDocument()
    const headers = screen
      .getAllByRole('columnheader')
      .map((header) => header.textContent?.trim())
    expect(headers).toEqual(['', '媒体平台', '账号名称', 'GoodShort', '操作'])
    expect(await screen.findByText('审核中')).toBeInTheDocument()
    expect(screen.getByText('已加白')).toBeInTheDocument()
    expect(screen.getByText('已失败')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: '详情' })).toHaveLength(3)
    expect(
      screen.queryByRole('button', { name: '重试报白' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByText('启用')).not.toBeInTheDocument()
  })

  it('opens details and retries a failed filing', async () => {
    let retryCalled = false
    server.use(
      http.get('/api/user/promotion/media-accounts', () =>
        HttpResponse.json({ code: 0, message: 'success', data: accounts }),
      ),
      http.get('/api/user/promotion/media-accounts/3', () =>
        HttpResponse.json({ code: 0, message: 'success', data: accounts[2] }),
      ),
      http.post('/api/user/promotion/media-accounts/3/filings/1', () => {
        retryCalled = true
        return HttpResponse.json({
          code: 0,
          message: 'success',
          data: { providerId: 1, status: 'PENDING' },
        })
      }),
    )

    renderPage()
    const user = userEvent.setup()
    await screen.findByText('Profile 3')
    await user.click(screen.getAllByRole('button', { name: '详情' })[2])

    expect(await screen.findByText('账号未通过平台审核')).toBeInTheDocument()
    await user.click(
      screen.getAllByRole('button', { name: '重试报白' }).at(-1)!,
    )

    expect(retryCalled).toBe(true)
  })

  it('submits the new account form', async () => {
    let payload: unknown
    server.use(
      http.get('/api/user/promotion/media-accounts', () =>
        HttpResponse.json({ code: 0, message: 'success', data: [] }),
      ),
      http.post('/api/user/promotion/media-accounts', async ({ request }) => {
        payload = await request.json()
        return HttpResponse.json({
          code: 0,
          message: 'success',
          data: accounts[0],
        })
      }),
    )

    renderPage()
    const user = userEvent.setup()
    await screen.findByText('暂无媒体账号')
    await user.click(screen.getAllByRole('button', { name: '新增账号' })[0])
    const formInputs = screen.getAllByPlaceholderText('请输入')
    await user.type(formInputs[0], 'creator-1')
    await user.type(formInputs[1], 'Creator 1')
    await user.type(formInputs[2], 'https://tiktok.com/@creator-1')
    await user.click(screen.getByRole('button', { name: '提交报白' }))

    await waitFor(() =>
      expect(payload).toEqual({
        mediaType: 'TIKTOK',
        externalAccountId: 'creator-1',
        accountName: 'Creator 1',
        accountLink: 'https://tiktok.com/@creator-1',
      }),
    )
    expect(screen.queryByLabelText('鎶ョ櫧骞冲彴')).not.toBeInTheDocument()
  })
})
