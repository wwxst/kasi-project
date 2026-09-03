import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import {
  listProviderFilingModes,
  updateProviderFilingMode,
} from './filingModeApi'

const server = setupServer()

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('filingModeApi', () => {
  it('loads filing modes for configured providers', async () => {
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: [
            {
              id: 1,
              providerCode: 'GOODSHORT',
              providerName: 'GoodShort',
              status: 1,
              capabilities: ['ACCOUNT_FILING'],
              connection: { id: 2 },
            },
          ],
        }),
      ),
      http.get('/api/admin/drama/providers/1/filing-mode', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: {
            providerId: 1,
            providerName: 'GoodShort',
            filingMode: 'MANUAL',
            connectionConfigured: true,
          },
        }),
      ),
    )

    await expect(listProviderFilingModes()).resolves.toEqual([
      {
        providerId: 1,
        providerName: 'GoodShort',
        filingMode: 'MANUAL',
        connectionConfigured: true,
      },
    ])
  })

  it('sends exactly the selected filing mode', async () => {
    let requestBody: unknown
    server.use(
      http.put(
        '/api/admin/drama/providers/1/filing-mode',
        async ({ request }) => {
          requestBody = await request.json()
          return HttpResponse.json({
            code: 0,
            message: '保存成功',
            data: {
              providerId: 1,
              providerName: 'GoodShort',
              filingMode: 'API',
              connectionConfigured: true,
            },
          })
        },
      ),
    )

    await updateProviderFilingMode(1, 'API')

    expect(requestBody).toEqual({ filingMode: 'API' })
  })
})
