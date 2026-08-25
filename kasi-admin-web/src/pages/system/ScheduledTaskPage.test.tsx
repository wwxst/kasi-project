import React from 'react'
import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import { App as AntdApp } from 'antd'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { useAuthStore } from '../../features/auth/authStore'
import { ScheduledTaskPage } from './ScheduledTaskPage'
import type { ScheduledTask } from '../../features/scheduled-task/scheduledTaskTypes'

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({
    title,
    children,
    ...rest
  }: React.PropsWithChildren<{ title?: React.ReactNode }>) => (
    <section {...rest}>
      <h1>{title}</h1>
      {children}
    </section>
  ),
}))

const server = setupServer()
const task = {
  taskCode: 'GOODSHORT_DRAMA_INCREMENTAL_SYNC',
  title: 'GoodShort 短剧增量同步',
  description: '每隔60分钟执行一次GoodShort短剧目录增量同步',
  intervalMinutes: 60,
  enabled: true,
}

const orderTask: ScheduledTask = {
  taskCode: 'GOODSHORT_ORDER_SYNC',
  title: 'GoodShort 订单同步',
  description: '每隔1分钟同步最近3天的GoodShort订单',
  cycleType: 'INTERVAL_MINUTES',
  intervalValue: 1,
  intervalMinutes: 5,
  enabled: true,
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

beforeEach(() => {
  useAuthStore.setState({
    accessToken: 'test-token',
    admin: {
      id: 1,
      username: 'kasiadmin',
      realName: '系统管理员',
      mobile: null,
      email: null,
      avatarUrl: null,
      isSuperAdmin: 1,
    },
  })
  server.use(
    http.get('/api/admin/system/scheduled-tasks', () =>
      HttpResponse.json({ code: 0, message: 'ok', data: [task] }),
    ),
  )
})

function renderPage() {
  return render(
    <AntdApp>
      <ScheduledTaskPage />
    </AntdApp>,
  )
}

describe('ScheduledTaskPage', () => {
  it('renders and toggles the GoodShort order task from the existing task page', async () => {
    let requestBody: unknown
    server.use(
      http.get('/api/admin/system/scheduled-tasks', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [task, orderTask] }),
      ),
      http.put(
        '/api/admin/system/scheduled-tasks/GOODSHORT_ORDER_SYNC',
        async ({ request }) => {
          requestBody = await request.json()
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: { ...orderTask, ...(requestBody as Record<string, unknown>) },
          })
        },
      ),
    )

    renderPage()

    expect(await screen.findByText('GoodShort 订单同步')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('GoodShort 订单同步是否开启'))

    await waitFor(() =>
      expect(requestBody).toEqual({
        cycleType: 'INTERVAL_MINUTES',
        intervalValue: 1,
        description: '每隔1分钟同步最近3天的GoodShort订单',
        enabled: false,
      }),
    )
  })

  it('renders only the compact five-column task table', async () => {
    renderPage()

    expect(
      await screen.findByText('GoodShort 短剧增量同步'),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('columnheader', { name: '标题' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('columnheader', { name: '任务说明' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('columnheader', { name: '执行周期' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('columnheader', { name: '是否开启' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('columnheader', { name: '操作' }),
    ).toBeInTheDocument()
    expect(screen.getByText('每隔60分钟执行一次')).toBeInTheDocument()
    expect(screen.queryByText('下次执行时间')).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: '新增' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: '立即执行' }),
    ).not.toBeInTheDocument()
  })

  it('edits only interval description and enabled state', async () => {
    let requestBody: unknown
    server.use(
      http.put(
        '/api/admin/system/scheduled-tasks/GOODSHORT_DRAMA_INCREMENTAL_SYNC',
        async ({ request }) => {
          requestBody = await request.json()
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: { ...task, ...(requestBody as Record<string, unknown>) },
          })
        },
      ),
    )
    renderPage()
    await screen.findByText('GoodShort 短剧增量同步')

    fireEvent.click(screen.getByRole('button', { name: '编辑' }))

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByLabelText('执行周期')).toBeInTheDocument()
    expect(screen.getByLabelText('周期类型')).toBeInTheDocument()
    expect(screen.getByText('每隔N分钟')).toBeInTheDocument()
    expect(screen.getByLabelText('任务说明')).toBeInTheDocument()
    expect(screen.getByLabelText('是否开启')).toBeInTheDocument()
    expect(screen.queryByLabelText('标题')).not.toBeInTheDocument()

    fireEvent.change(screen.getByRole('spinbutton'), {
      target: { value: '30' },
    })
    fireEvent.input(screen.getByRole('spinbutton'), {
      target: { value: '30' },
    })
    fireEvent.blur(screen.getByRole('spinbutton'))
    fireEvent.change(screen.getByLabelText('任务说明'), {
      target: { value: '每隔30分钟同步一次' },
    })
    fireEvent.click(screen.getByLabelText('是否开启'))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }))

    await waitFor(() =>
      expect(requestBody).toEqual({
        cycleType: 'INTERVAL_MINUTES',
        intervalValue: 30,
        description: '每隔30分钟同步一次',
        enabled: false,
      }),
    )
  })

  it('shows the cycle options from the scheduler reference', async () => {
    renderPage()
    await screen.findByText('GoodShort 短剧增量同步')
    fireEvent.click(screen.getByRole('button', { name: '编辑' }))
    fireEvent.mouseDown(screen.getByLabelText('周期类型'))

    for (const label of [
      '每隔N秒',
      '每隔N分钟',
      '每隔N小时',
      '每隔N天',
      '每天',
      '每星期',
      '每月',
      '每年',
    ]) {
      expect(
        await screen.findByText(label, {
          selector: '.ant-select-item-option-content',
        }),
      ).toBeInTheDocument()
    }
  })

  it('updates the unit when a different interval type is selected', async () => {
    renderPage()
    await screen.findByText('GoodShort 短剧增量同步')
    fireEvent.click(screen.getByRole('button', { name: '编辑' }))
    fireEvent.mouseDown(screen.getByLabelText('周期类型'))
    fireEvent.click(
      await screen.findByText('每隔N小时', {
        selector: '.ant-select-item-option-content',
      }),
    )

    const dialog = within(screen.getByRole('dialog'))
    expect(dialog.getAllByText('时').length).toBeGreaterThan(0)
    expect(dialog.getAllByText('分').length).toBeGreaterThan(0)
    expect(dialog.getByText('每隔60小时0分钟执行一次')).toBeInTheDocument()
  })

  it.each([
    ['每天', ['执行时间']],
    ['每星期', ['执行时间', '星期']],
    ['每月', ['执行时间', '日期']],
    ['每年', ['执行时间', '日期', '月份']],
  ])('shows the calendar fields for %s', async (label, fields) => {
    renderPage()
    await screen.findByText('GoodShort 短剧增量同步')
    fireEvent.click(screen.getByRole('button', { name: '编辑' }))
    fireEvent.mouseDown(screen.getByLabelText('周期类型'))
    fireEvent.click(
      await screen.findByText(label, {
        selector: '.ant-select-item-option-content',
      }),
    )

    const dialog = within(screen.getByRole('dialog'))
    for (const field of fields) {
      expect(dialog.getByText(field, { exact: true })).toBeInTheDocument()
    }
    expect(dialog.queryByText('执行周期', { exact: true })).toBeInTheDocument()
  })

  it('allows a super administrator to toggle the row directly', async () => {
    let requestBody: unknown
    server.use(
      http.put(
        '/api/admin/system/scheduled-tasks/GOODSHORT_DRAMA_INCREMENTAL_SYNC',
        async ({ request }) => {
          requestBody = await request.json()
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: { ...task, ...(requestBody as Record<string, unknown>) },
          })
        },
      ),
    )
    renderPage()
    await screen.findByText('GoodShort 短剧增量同步')

    fireEvent.click(screen.getByLabelText('GoodShort 短剧增量同步是否开启'))

    await waitFor(() =>
      expect(requestBody).toEqual({
        cycleType: 'INTERVAL_MINUTES',
        intervalValue: 60,
        description: task.description,
        enabled: false,
      }),
    )
  })

  it('keeps the task read-only for an ordinary administrator', async () => {
    useAuthStore.setState({
      admin: { ...useAuthStore.getState().admin!, isSuperAdmin: 0 },
    })
    renderPage()

    await screen.findByText('GoodShort 短剧增量同步')
    expect(
      screen.getByLabelText('GoodShort 短剧增量同步是否开启'),
    ).toBeDisabled()
    expect(
      screen.queryByRole('button', { name: '编辑' }),
    ).not.toBeInTheDocument()
    expect(
      screen.getByTestId('scheduled-task-readonly-action'),
    ).toHaveTextContent('-')
  })
})
