import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import {
  createCommissionRule,
  deleteCommissionRule,
  endCommissionRule,
  listProviders,
  listCommissionRules,
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
    effectiveFrom: '2026-09-01T00:00:00',
    effectiveTo: null,
    status: 'PENDING',
  }

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

  it('lists commission rules for a provider', async () => {
    server.use(
      http.get('/api/admin/drama/providers/1/commission-rules', () =>
        HttpResponse.json({ code: 0, message: '查询成功', data: [rule] }),
      ),
    )

    await expect(listCommissionRules(1)).resolves.toEqual([rule])
  })

  it('creates and updates commission rules with the exact request body', async () => {
    let createBody: unknown
    let updateBody: unknown
    server.use(
      http.post(
        '/api/admin/drama/providers/1/commission-rules',
        async ({ request }) => {
          createBody = await request.json()
          return HttpResponse.json({ code: 0, message: '创建成功', data: rule })
        },
      ),
      http.put(
        '/api/admin/drama/providers/1/commission-rules/7',
        async ({ request }) => {
          updateBody = await request.json()
          return HttpResponse.json({ code: 0, message: '更新成功', data: rule })
        },
      ),
    )

    const request = {
      channelFeeRate: 30,
      principalFeeRate: 0,
      principalCommissionRate: 80,
      downstreamFeeRate: 0,
      downstreamCommissionRate: 70,
      effectiveFrom: '2026-09-01T00:00:00',
      effectiveTo: null,
    }
    await createCommissionRule(1, request)
    await updateCommissionRule(1, 7, request)

    expect(createBody).toEqual(request)
    expect(updateBody).toEqual(request)
  })

  it('ends and deletes commission rules', async () => {
    let endedBody: unknown
    let deleted = false
    server.use(
      http.patch(
        '/api/admin/drama/providers/1/commission-rules/7/end-time',
        async ({ request }) => {
          endedBody = await request.json()
          return HttpResponse.json({ code: 0, message: '结束成功', data: rule })
        },
      ),
      http.delete('/api/admin/drama/providers/1/commission-rules/7', () => {
        deleted = true
        return HttpResponse.json({ code: 0, message: '删除成功', data: null })
      }),
    )

    await endCommissionRule(1, 7, { effectiveTo: '2026-10-01T00:00:00' })
    await deleteCommissionRule(1, 7)

    expect(endedBody).toEqual({ effectiveTo: '2026-10-01T00:00:00' })
    expect(deleted).toBe(true)
  })
})
