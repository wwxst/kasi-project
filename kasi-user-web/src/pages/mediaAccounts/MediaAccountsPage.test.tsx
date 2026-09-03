import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MessagePlugin } from 'tdesign-react'
import MediaAccountsPage from './MediaAccountsPage'
import { getMediaAccounts } from '../../features/mediaAccounts/mediaAccountsApi'

vi.mock('../../features/mediaAccounts/mediaAccountsApi', () => ({
  createMediaAccount: vi.fn(),
  getMediaAccounts: vi.fn(),
}))

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.clearAllMocks()
})

function renderPage() {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false } },
        })
      }
    >
      <MediaAccountsPage />
    </QueryClientProvider>,
  )
}

describe('MediaAccountsPage', () => {
  it('renders media accounts in the Starter table structure', async () => {
    vi.mocked(getMediaAccounts).mockResolvedValueOnce([
      {
        id: 1,
        mediaType: 'TIKTOK',
        externalAccountId: 'creator-1',
        accountName: 'Creator One',
        accountLink: 'https://tiktok.com/@creator-1',
        status: 1,
        filings: [
          {
            providerId: 3,
            providerName: 'GoodShort',
            status: 'APPROVED',
          } as never,
        ],
      },
    ])

    renderPage()

    expect(await screen.findByText('Creator One')).toBeTruthy()
    expect(screen.getByText('TikTok')).toBeTruthy()
    expect(screen.getAllByText('已报白').length).toBeGreaterThanOrEqual(1)
    expect(screen.queryByText('账号状态')).toBeNull()
    expect(screen.queryByText('操作')).toBeNull()
    expect(screen.getByText('查询')).toBeTruthy()
    expect(screen.getByText('重置')).toBeTruthy()
    expect(screen.getByRole('button', { name: '账号报白' })).toBeTruthy()
  })

  it('opens the account filing dialog from the toolbar', async () => {
    const user = userEvent.setup()
    vi.mocked(getMediaAccounts).mockResolvedValueOnce([])

    renderPage()

    await user.click(screen.getByRole('button', { name: '账号报白' }))
    expect(screen.getByPlaceholderText('请输入账号 ID')).toBeTruthy()
    expect(screen.getByText('提交报白')).toBeTruthy()
  })

  it('uses the Starter message for ordinary API errors', async () => {
    const messageError = vi
      .spyOn(MessagePlugin, 'error')
      .mockResolvedValue(undefined as never)
    vi.mocked(getMediaAccounts).mockRejectedValueOnce(new Error('接口暂不可用'))

    renderPage()

    await waitFor(() =>
      expect(messageError).toHaveBeenCalledWith('账号报白加载失败，请稍后重试'),
    )
    expect(screen.getByPlaceholderText('请选择媒体平台')).toBeTruthy()
    expect(screen.queryByText('接口暂不可用')).toBeNull()
  })

  it('suppresses the page message for unauthorized errors', async () => {
    const messageError = vi
      .spyOn(MessagePlugin, 'error')
      .mockResolvedValue(undefined as never)
    vi.mocked(getMediaAccounts).mockRejectedValueOnce(
      Object.assign(new Error('Request failed with status code 401'), {
        isAxiosError: true,
        response: { status: 401 },
      }),
    )

    renderPage()

    await waitFor(() => expect(getMediaAccounts).toHaveBeenCalledTimes(1))
    expect(messageError).not.toHaveBeenCalled()
    expect(screen.queryByText('Request failed with status code 401')).toBeNull()
  })
})
