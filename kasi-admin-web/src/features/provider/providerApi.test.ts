import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import {
  createCommissionRule,
  listCommissionRules,
  listProviders,
  testProviderConnection,
  updateCommissionRule,
  upsertProviderConnection,
} from './providerApi'

const server = setupServer()
beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('providerApi', () => {
  const rule = {
    id: 7,
    providerId: 1,
    channelFeeRate: 30,
    principalFeeRate: 0,
    principalCommissionRate: 80,
    downstreamFeeRate: 0,
    downstreamCommissionRate: 70,
  }

  it('lists providers from the backend', async () => {
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
              capabilities: [],
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
          return HttpResponse.json({ code: 0, message: 'ok', data: { id: 2 } })
        },
      ),
    )
    await upsertProviderConnection(1, {
      baseUrl: 'https://api.test',
      partnerId: 'p1',
      status: 1,
      filingMode: 'API',
    })
    expect(requestBody).toEqual({
      baseUrl: 'https://api.test',
      partnerId: 'p1',
      status: 1,
      filingMode: 'API',
    })
  })

  it('tests a provider connection and unwraps the result', async () => {
    server.use(
      http.post('/api/admin/drama/providers/1/connection/test', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: {
            reachable: true,
            message: 'ok',
            testedAt: '2026-08-18T10:00:00Z',
          },
        }),
      ),
    )
    await expect(testProviderConnection(1)).resolves.toEqual({
      reachable: true,
      message: 'ok',
      testedAt: '2026-08-18T10:00:00Z',
    })
  })

  it('lists the single default commission rule', async () => {
    server.use(
      http.get('/api/admin/drama/providers/1/commission-rules', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [rule] }),
      ),
    )
    await expect(listCommissionRules(1)).resolves.toEqual([rule])
  })

  it('creates and updates five commission rates without temporal fields', async () => {
    let createBody: unknown
    let updateBody: unknown
    server.use(
      http.post(
        '/api/admin/drama/providers/1/commission-rules',
        async ({ request }) => {
          createBody = await request.json()
          return HttpResponse.json({ code: 0, message: 'ok', data: rule })
        },
      ),
      http.put(
        '/api/admin/drama/providers/1/commission-rules/7',
        async ({ request }) => {
          updateBody = await request.json()
          return HttpResponse.json({ code: 0, message: 'ok', data: rule })
        },
      ),
    )
    const request = {
      channelFeeRate: 30,
      principalFeeRate: 0,
      principalCommissionRate: 80,
      downstreamFeeRate: 0,
      downstreamCommissionRate: 70,
    }
    await createCommissionRule(1, request)
    await updateCommissionRule(1, 7, request)
    expect(createBody).toEqual(request)
    expect(updateBody).toEqual(request)
  })
})
