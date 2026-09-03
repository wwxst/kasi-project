import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntdApp } from 'antd'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import type { ComponentProps } from 'react'
import {
  afterAll,
  afterEach,
  beforeAll,
  describe,
  expect,
  it,
  vi,
} from 'vitest'
import type {
  DramaContentSyncStatus,
  DramaContentSyncTask,
} from '../../features/drama/dramaCatalogTypes'
import { DramaContentSyncSection } from './DramaContentSyncSection'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  vi.useRealTimers()
})
afterAll(() => server.close())

function contentTask(status: DramaContentSyncStatus): DramaContentSyncTask {
  return {
    id: 51,
    dramaId: 8,
    status,
    requestedAt: '2026-08-29T08:00:00',
    nextRunAt:
      status === 'SUCCESS' || status === 'FAILED'
        ? null
        : '2026-08-29T08:00:03',
    retryCount: status === 'FAILED' ? 3 : 0,
    totalFetched: 12,
    insertedCount: 10,
    updatedCount: 2,
    lastErrorCode: status === 'FAILED' ? 'PROVIDER_REMOTE_UNAVAILABLE' : null,
    lastErrorMessage: status === 'FAILED' ? 'GoodShort 暂时不可用' : null,
  }
}

function statusHandler(status: DramaContentSyncStatus) {
  return http.get('/api/admin/drama/catalog/8/contents/sync/status', () =>
    HttpResponse.json({ code: 0, message: 'ok', data: contentTask(status) }),
  )
}

function sectionElement(
  overrides: Partial<ComponentProps<typeof DramaContentSyncSection>> = {},
) {
  return (
    <AntdApp>
      <DramaContentSyncSection
        dramaId={8}
        active
        refreshKey={0}
        onSucceeded={vi.fn()}
        {...overrides}
      />
    </AntdApp>
  )
}

function renderSection(
  overrides: Partial<ComponentProps<typeof DramaContentSyncSection>> = {},
) {
  return render(sectionElement(overrides))
}

describe('DramaContentSyncSection', () => {
  it('shows the expected empty state for business code 6017', async () => {
    server.use(
      http.get('/api/admin/drama/catalog/8/contents/sync/status', () =>
        HttpResponse.json({
          code: 6017,
          message: '短剧剧集同步任务不存在',
          data: null,
        }),
      ),
    )
    renderSection()

    expect(await screen.findByText('尚未提交剧集同步任务')).toBeInTheDocument()
    expect(screen.queryByText('短剧剧集同步任务不存在')).not.toBeInTheDocument()
  })

  it.each([
    ['REQUESTED', '等待执行'],
    ['RUNNING', '运行中'],
    ['SUCCESS', '同步成功'],
    ['FAILED', '同步失败'],
  ] as const)('renders %s as %s', async (status, label) => {
    server.use(statusHandler(status))
    renderSection()

    expect(await screen.findByText(label)).toBeInTheDocument()
    expect(screen.getByText(/获取 12.*新增 10.*更新 2/)).toBeInTheDocument()
    if (status === 'FAILED') {
      expect(
        screen.getByText('PROVIDER_REMOTE_UNAVAILABLE'),
      ).toBeInTheDocument()
      expect(screen.getByText('GoodShort 暂时不可用')).toBeInTheDocument()
    }
  })

  it('polls every three seconds and stops after success', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let requests = 0
    const onSucceeded = vi.fn()
    server.use(
      http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
        requests += 1
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: contentTask(requests === 1 ? 'RUNNING' : 'SUCCESS'),
        })
      }),
    )
    renderSection({ onSucceeded })
    await screen.findByText('运行中')

    await vi.advanceTimersByTimeAsync(3_000)
    expect(await screen.findByText('同步成功')).toBeInTheDocument()
    expect(onSucceeded).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(6_000)
    expect(requests).toBe(2)
  })

  it('stops polling when inactive and allows a manual refresh', async () => {
    let requests = 0
    server.use(
      http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
        requests += 1
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: contentTask('RUNNING'),
        })
      }),
    )
    const view = renderSection()
    await screen.findByText('运行中')

    await userEvent.click(
      screen.getByRole('button', { name: '刷新剧集同步状态' }),
    )
    await waitFor(() => expect(requests).toBe(2))
    view.rerender(sectionElement({ active: false }))
    expect(
      screen.queryByTestId('drama-content-sync-section'),
    ).not.toBeInTheDocument()
  })

  it('stops a running poll after sixty seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let requests = 0
    server.use(
      http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
        requests += 1
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: contentTask('RUNNING'),
        })
      }),
    )
    renderSection()
    await screen.findByText('运行中')

    await vi.advanceTimersByTimeAsync(60_000)
    const requestsAtLimit = requests
    await vi.advanceTimersByTimeAsync(6_000)
    expect(requests).toBe(requestsAtLimit)
  })

  it.each([
    [403, 'Request failed with status code 403'],
    [503, 'Request failed with status code 503'],
  ] as const)('shows HTTP %s locally', async (status, expected) => {
    server.use(
      http.get('/api/admin/drama/catalog/8/contents/sync/status', () =>
        HttpResponse.json({ data: null }, { status }),
      ),
    )
    renderSection()

    expect(await screen.findByText(expected)).toBeInTheDocument()
  })

  it('shows an ordinary business error locally', async () => {
    server.use(
      http.get('/api/admin/drama/catalog/8/contents/sync/status', () =>
        HttpResponse.json({
          code: 6008,
          message: '短剧不存在',
          data: null,
        }),
      ),
    )
    renderSection()

    expect(await screen.findByText('短剧不存在')).toBeInTheDocument()
  })

  it('does not render a duplicate local error for HTTP 401', async () => {
    let requests = 0
    server.use(
      http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
        requests += 1
        return HttpResponse.json({ data: null }, { status: 401 })
      }),
    )
    renderSection()

    await waitFor(() => expect(requests).toBe(1))
    expect(screen.queryByText(/401/)).not.toBeInTheDocument()
  })
})
