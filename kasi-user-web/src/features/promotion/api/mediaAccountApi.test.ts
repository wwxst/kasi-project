import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'
import {
  createMediaAccount,
  fetchMediaAccount,
  fetchMediaAccounts,
  retryMediaAccountFiling,
  updateMediaAccount,
} from './mediaAccountApi'
import { server } from '../../../test/server'

const account = {
  id: 7,
  mediaType: 'TIKTOK',
  externalAccountId: 'creator-7',
  accountName: 'Creator 7',
  accountLink: 'https://tiktok.com/@creator-7',
  status: 1,
  filings: [],
}

describe('media account API', () => {
  it('fetches the current user media accounts', async () => {
    server.use(
      http.get('/api/user/promotion/media-accounts', () =>
        HttpResponse.json({ code: 0, message: 'success', data: [account] }),
      ),
    )

    await expect(fetchMediaAccounts()).resolves.toEqual([account])
  })

  it('fetches one media account by id', async () => {
    server.use(
      http.get('/api/user/promotion/media-accounts/7', () =>
        HttpResponse.json({ code: 0, message: 'success', data: account }),
      ),
    )

    await expect(fetchMediaAccount(7)).resolves.toEqual(account)
  })

  it('creates a media account without selecting a provider', async () => {
    let payload: unknown
    server.use(
      http.post('/api/user/promotion/media-accounts', async ({ request }) => {
        payload = await request.json()
        return HttpResponse.json({ code: 0, message: 'success', data: account })
      }),
    )

    await createMediaAccount({
      mediaType: 'TIKTOK',
      externalAccountId: 'creator-7',
      accountName: 'Creator 7',
      accountLink: 'https://tiktok.com/@creator-7',
    })

    expect(payload).toEqual({
      mediaType: 'TIKTOK',
      externalAccountId: 'creator-7',
      accountName: 'Creator 7',
      accountLink: 'https://tiktok.com/@creator-7',
    })
  })

  it('updates a media account', async () => {
    let payload: unknown
    server.use(
      http.put('/api/user/promotion/media-accounts/7', async ({ request }) => {
        payload = await request.json()
        return HttpResponse.json({ code: 0, message: 'success', data: account })
      }),
    )

    await updateMediaAccount(7, {
      mediaType: 'YOUTUBE',
      externalAccountId: 'channel-7',
      accountName: 'Channel 7',
      accountLink: 'https://youtube.com/@channel-7',
    })

    expect(payload).toEqual({
      mediaType: 'YOUTUBE',
      externalAccountId: 'channel-7',
      accountName: 'Channel 7',
      accountLink: 'https://youtube.com/@channel-7',
    })
  })

  it('retries a filing for the selected provider', async () => {
    server.use(
      http.post('/api/user/promotion/media-accounts/7/filings/1', () =>
        HttpResponse.json({
          code: 0,
          message: 'success',
          data: { providerId: 1, status: 'PENDING' },
        }),
      ),
    )

    await expect(retryMediaAccountFiling(7, 1)).resolves.toEqual({
      providerId: 1,
      status: 'PENDING',
    })
  })
})
