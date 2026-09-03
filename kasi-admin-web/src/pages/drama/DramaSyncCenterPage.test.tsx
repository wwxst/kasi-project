import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntdApp } from 'antd'
import React from 'react'
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
import { MemoryRouter } from 'react-router-dom'
import { DramaSyncCenterPage } from './DramaSyncCenterPage'

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({
    title,
    children,
  }: React.PropsWithChildren<{ title?: React.ReactNode }>) => (
    <section>
      <h1>{title}</h1>
      {children}
    </section>
  ),
}))

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

const provider = {
  id: 1,
  providerCode: 'GOODSHORT',
  providerName: 'GoodShort',
  status: 1,
  capabilities: ['FULL_DRAMA_SYNC', 'FREE_CONTENT_PREVIEW'],
  connection: { id: 11 },
}

function renderPage(path = '/drama/sync/catalog') {
  render(
    <AntdApp>
      <MemoryRouter initialEntries={[path]}>
        <DramaSyncCenterPage />
      </MemoryRouter>
    </AntdApp>,
  )
}

describe('DramaSyncCenterPage', () => {
  it('keeps catalog and content pages independent and renders the common columns', async () => {
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [provider] }),
      ),
      http.get('/api/admin/drama/catalog/sync/records', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: [
            {
              id: 'run-1',
              createdAt: '2026-08-29T03:40:00',
              triggerSource: 'MANUAL',
              taskType: 'FULL',
              status: 'SUCCESS',
              insertedCount: 1,
              updatedCount: 1212,
              totalProcessed: 1213,
            },
          ],
        }),
      ),
      http.get('/api/admin/drama/catalog/sync/records/run-1', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: [
            {
              taskId: 11,
              language: 'ENGLISH',
              syncType: 'FULL',
              status: 'SUCCESS',
              pageNo: 14,
              insertedCount: 1,
              updatedCount: 1212,
              totalProcessed: 1213,
              lastErrorCode: null,
              lastErrorMessage: null,
            },
          ],
        }),
      ),
    )

    renderPage()

    expect(await screen.findByText('GoodShort')).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: '短剧同步' }),
    ).toBeInTheDocument()
    for (const label of [
      '创建时间',
      '触发方式',
      '任务类型',
      '状态',
      '新增数',
      '更新数',
      '总处理数',
      '操作',
    ]) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0)
    }
    expect(await screen.findByText('成功')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '查看详情' }))
    expect(await screen.findByText('英语')).toBeInTheDocument()
  })

  it('content page calls only content records endpoint', async () => {
    let catalogCalled = false
    let contentCalled = false
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [provider] }),
      ),
      http.get('/api/admin/drama/catalog/sync/records', () => {
        catalogCalled = true
        return HttpResponse.json({ code: 0, message: 'ok', data: [] })
      }),
      http.get('/api/admin/drama/catalog/contents/sync/records', () => {
        contentCalled = true
        return HttpResponse.json({ code: 0, message: 'ok', data: [] })
      }),
    )
    renderPage('/drama/sync/content')
    expect(
      await screen.findByRole('heading', { name: '剧集同步' }),
    ).toBeInTheDocument()
    await waitFor(() => expect(contentCalled).toBe(true))
    expect(catalogCalled).toBe(false)
  })
})
