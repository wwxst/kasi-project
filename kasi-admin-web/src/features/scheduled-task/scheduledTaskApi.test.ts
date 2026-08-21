import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { listScheduledTasks, updateScheduledTask } from './scheduledTaskApi'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('scheduledTaskApi', () => {
  it('loads fixed scheduled tasks', async () => {
    server.use(
      http.get('/api/admin/system/scheduled-tasks', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: [
            {
              taskCode: 'GOODSHORT_DRAMA_INCREMENTAL_SYNC',
              title: 'GoodShort 短剧增量同步',
              description: '每隔60分钟执行一次GoodShort短剧目录增量同步',
              intervalMinutes: 60,
              enabled: true,
            },
          ],
        }),
      ),
    )

    await expect(listScheduledTasks()).resolves.toEqual([
      {
        taskCode: 'GOODSHORT_DRAMA_INCREMENTAL_SYNC',
        title: 'GoodShort 短剧增量同步',
        description: '每隔60分钟执行一次GoodShort短剧目录增量同步',
        intervalMinutes: 60,
        enabled: true,
      },
    ])
  })

  it('updates only interval description and enabled state', async () => {
    let requestBody: unknown
    server.use(
      http.put(
        '/api/admin/system/scheduled-tasks/GOODSHORT_DRAMA_INCREMENTAL_SYNC',
        async ({ request }) => {
          requestBody = await request.json()
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: {
              taskCode: 'GOODSHORT_DRAMA_INCREMENTAL_SYNC',
              title: 'GoodShort 短剧增量同步',
              ...(requestBody as Record<string, unknown>),
            },
          })
        },
      ),
    )

    const request = {
      intervalMinutes: 30,
      description: '每隔30分钟执行一次GoodShort短剧目录增量同步',
      enabled: false,
    }
    await updateScheduledTask('GOODSHORT_DRAMA_INCREMENTAL_SYNC', request)

    expect(requestBody).toEqual(request)
  })

  it('throws the backend business message', async () => {
    server.use(
      http.get('/api/admin/system/scheduled-tasks', () =>
        HttpResponse.json({ code: 1008, message: '定时任务不存在', data: null }),
      ),
    )

    await expect(listScheduledTasks()).rejects.toThrow('定时任务不存在')
  })
})
