import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntdApp } from 'antd'
import { HttpResponse, http } from 'msw'
import { setupServer } from 'msw/node'
import React from 'react'
import {
  afterAll,
  afterEach,
  beforeAll,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({
    title,
    content,
    children,
  }: React.PropsWithChildren<{
    title?: React.ReactNode
    content?: React.ReactNode
  }>) => (
    <section>
      <h1>{title}</h1>
      <p>{content}</p>
      {children}
    </section>
  ),
}))

import { PromotionLinkPage } from './PromotionLinkPage'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

describe('PromotionLinkPage', () => {
  it('loads promotion tasks with conversion metrics', async () => {
    const urls: string[] = []
    let syncBody: unknown
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: [
            {
              id: 1,
              providerCode: 'GOODSHORT',
              providerName: 'GoodShort',
              status: 1,
              capabilities: ['ANALYTICS_SYNC'],
              connection: { id: 11, currency: 'USD', status: 1 },
            },
          ],
        }),
      ),
      http.get('/api/admin/promotion/links', ({ request }) => {
        urls.push(request.url)
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: {
            list: [
              {
                id: 1,
                userNo: '583104726918',
                nickname: '测试用户',
                providerName: 'GoodShort',
                dramaTitle: 'Drama',
                campaignName: 'summer',
                mediaType: 'TIKTOK',
                trackingNo: 'tracking-1',
                externalCode: 'code-1',
                shareUrl: 'https://example.test/link',
                clickCount: 11,
                attributedUserCount: 12,
                newRegisteredUserCount: 13,
                newPaidUserCount: 14,
                newMemberUserCount: 15,
                paidUserCount: 16,
                orderCount: 17,
                createdAt: '2026-09-08T10:00:00',
              },
            ],
            page: 1,
            size: 20,
            total: 1,
          },
        })
      }),
      http.post(
        '/api/admin/promotion/analytical-reports/sync',
        async ({ request }) => {
          syncBody = await request.json()
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: { fetchedCount: 8, upsertedCount: 8 },
          })
        },
      ),
    )

    render(
      <AntdApp>
        <PromotionLinkPage />
      </AntdApp>,
    )

    expect(
      await screen.findByRole('heading', { name: '推广任务' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('tracking-1')).toBeInTheDocument()
    expect(screen.getByText('code-1')).toBeInTheDocument()
    expect(screen.getByText('17')).toBeInTheDocument()
    await waitFor(() => expect(urls).toHaveLength(1))
    const url = new URL(urls[0])
    expect(url.pathname).toBe('/api/admin/promotion/links')

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('用户编号'), '583104726918')
    await user.type(screen.getByLabelText('口令'), 'code-1')
    await user.type(screen.getByLabelText('追踪号'), 'tracking-1')
    await user.click(screen.getByRole('button', { name: '查询' }))
    await waitFor(() => expect(urls).toHaveLength(2))
    const filterUrl = new URL(urls[1])
    expect(filterUrl.searchParams.get('userNo')).toBe('583104726918')
    expect(filterUrl.searchParams.get('externalCode')).toBe('code-1')
    expect(filterUrl.searchParams.get('trackingNo')).toBe('tracking-1')

    await user.click(screen.getByRole('button', { name: '手动同步转化' }))
    const dialog = (await screen.findByText('手动同步转化日报')).closest(
      '.ant-modal',
    ) as HTMLElement
    expect(within(dialog).getByText('GoodShort')).toBeInTheDocument()
    await user.type(within(dialog).getByLabelText('同步开始日期'), '2026-09-01')
    await user.type(within(dialog).getByLabelText('同步结束日期'), '2026-09-03')
    await user.click(within(dialog).getByRole('button', { name: '开始同步' }))

    await waitFor(() =>
      expect(syncBody).toEqual({
        providerId: 1,
        startDate: '2026-09-01',
        endDate: '2026-09-03',
      }),
    )
    expect(await screen.findByText(/获取 8 条/)).toBeInTheDocument()
    await waitFor(() => expect(urls).toHaveLength(3))
  })
})
