import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntdApp } from 'antd'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import {
  afterAll,
  afterEach,
  beforeAll,
  describe,
  expect,
  it,
  vi,
} from 'vitest'
import type { DramaProvider } from '../../features/provider/providerTypes'
import { DramaContentSyncModal } from './DramaContentSyncModal'

const server = setupServer()
const providers: DramaProvider[] = [
  {
    id: 1,
    providerCode: 'GOODSHORT',
    providerName: 'GoodShort',
    status: 1,
    capabilities: ['FREE_CONTENT_PREVIEW'],
    connection: {
      id: 11,
      connectionName: 'GoodShort production',
      baseUrl: 'https://example.com',
      partnerId: 'pid',
      currency: 'USD',
      status: 1,
      credentialConfigured: true,
      createdAt: '2026-08-20T08:00:00',
      updatedAt: '2026-08-20T08:00:00',
    },
  },
]

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

function renderModal(onSubmitted = vi.fn()) {
  render(
    <AntdApp>
      <DramaContentSyncModal
        open
        providers={providers}
        preferredProviderId={1}
        languageOptions={[{ value: 'ENGLISH', label: '英语' }]}
        onClose={vi.fn()}
        onSubmitted={onSubmitted}
      />
    </AntdApp>,
  )
  return onSubmitted
}

describe('DramaContentSyncModal', () => {
  it('submits all online dramas by default with no language', async () => {
    let body: unknown
    server.use(
      http.post(
        '/api/admin/drama/catalog/contents/sync/all',
        async ({ request }) => {
          body = await request.json()
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: {
              requestedCount: 24,
              queuedCount: 20,
              skippedCount: 3,
              invalidCount: 1,
              tasks: [],
            },
          })
        },
      ),
    )
    const onSubmitted = renderModal()
    const user = userEvent.setup()
    const modal = await screen.findByTestId('drama-content-sync-modal')

    expect(
      within(modal).getByText('当前仅同步 GoodShort 免费剧集'),
    ).toBeInTheDocument()
    expect(
      within(modal)
        .getByText('同步全部在线短剧')
        .closest('.ant-segmented-item'),
    ).toHaveClass('ant-segmented-item-selected')
    await user.click(within(modal).getByTestId('drama-content-sync-submit'))

    await waitFor(() =>
      expect(body).toEqual({ providerId: 1, missingOnly: false }),
    )
    expect(
      await screen.findByText(
        '匹配 24 部，排队 20 部，运行中跳过 3 部，无效 1 部',
      ),
    ).toBeInTheDocument()
    expect(onSubmitted).toHaveBeenCalledWith(1)
  })

  it('submits missing-only with a selected language and preserves values on failure', async () => {
    let body: unknown
    let attempts = 0
    server.use(
      http.post(
        '/api/admin/drama/catalog/contents/sync/all',
        async ({ request }) => {
          attempts += 1
          body = await request.json()
          if (attempts === 1) {
            return HttpResponse.json({
              code: 6016,
              message: '短剧剧集同步任务正在执行',
              data: null,
            })
          }
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: {
              requestedCount: 8,
              queuedCount: 8,
              skippedCount: 0,
              invalidCount: 0,
              tasks: [],
            },
          })
        },
      ),
    )
    renderModal()
    const user = userEvent.setup()
    const modal = await screen.findByTestId('drama-content-sync-modal')

    await user.click(within(modal).getByText('仅补齐缺失视频地址'))
    await user.click(within(modal).getByLabelText('语言'))
    await user.click(await screen.findByText('英语'))
    await user.click(within(modal).getByTestId('drama-content-sync-submit'))

    expect(
      await screen.findByText('短剧剧集同步任务正在执行'),
    ).toBeInTheDocument()
    expect(
      within(modal)
        .getByText('仅补齐缺失视频地址')
        .closest('.ant-segmented-item'),
    ).toHaveClass('ant-segmented-item-selected')
    await user.click(within(modal).getByTestId('drama-content-sync-submit'))
    await waitFor(() =>
      expect(body).toEqual({
        providerId: 1,
        language: 'ENGLISH',
        missingOnly: true,
      }),
    )
  })
})
