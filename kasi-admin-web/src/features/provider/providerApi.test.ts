import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import {
  listProviders,
  testProviderConnection,
  upsertProviderConnection,
} from './providerApi'

const server = setupServer()

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('providerApi', () => {
  it('lists providers from the backend', async () => {
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
              connection: null,
            },
          ],
        }),
      ),
    )

    await expect(listProviders()).resolves.toEqual([
      expect.objectContaining({ providerCode: 'GOODSHORT' }),
    ])
  })

  it('upserts a connection without sending an omitted api key', async () => {
    let requestBody: unknown
    server.use(
      http.put(
        '/api/admin/drama/providers/1/connection',
        async ({ request }) => {
          requestBody = await request.json()
          return HttpResponse.json({
            code: 0,
            message: '保存成功',
            data: {
              id: 2,
              connectionName: 'GoodShort 默认接入',
              baseUrl: 'https://api.goodshort.test/creek',
              partnerId: 'partner-1',
              currency: 'USD',
              status: 1,
              credentialConfigured: true,
              createdAt: '2026-08-18T10:00:00',
              updatedAt: '2026-08-18T10:00:00',
            },
          })
        },
      ),
    )

    await upsertProviderConnection(1, {
      baseUrl: 'https://api.goodshort.test/creek',
      partnerId: 'partner-1',
      status: 1,
      filingMode: 'API',
    })

    expect(requestBody).toEqual({
      baseUrl: 'https://api.goodshort.test/creek',
      partnerId: 'partner-1',
      status: 1,
      filingMode: 'API',
    })
  })

  it('tests a provider connection and unwraps the result', async () => {
    server.use(
      http.post('/api/admin/drama/providers/1/connection/test', () =>
        HttpResponse.json({
          code: 0,
          message: '连接成功',
          data: {
            reachable: true,
            message: 'GoodShort connection reachable',
            testedAt: '2026-08-18T10:00:00Z',
          },
        }),
      ),
    )

    await expect(testProviderConnection(1)).resolves.toEqual({
      reachable: true,
      message: 'GoodShort connection reachable',
      testedAt: '2026-08-18T10:00:00Z',
    })
  })
})
