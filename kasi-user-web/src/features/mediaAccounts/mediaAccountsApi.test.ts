import { describe, expect, it, vi } from 'vitest'
import {
  createMediaAccount,
  getMediaAccounts,
  submitMediaFiling,
} from './mediaAccountsApi'
import { httpClient } from '../../shared/api/httpClient'

vi.mock('../../shared/api/httpClient', () => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

describe('getMediaAccounts', () => {
  it('unwraps the current user media account list', async () => {
    vi.mocked(httpClient.get).mockResolvedValueOnce({
      data: {
        code: 0,
        message: 'ok',
        data: [
          {
            id: 1,
            mediaType: 'TIKTOK',
            externalAccountId: 'creator-1',
            accountName: 'Creator One',
            accountLink: 'https://tiktok.com/@creator-1',
            status: 1,
            filings: [],
          },
        ],
      },
    })

    await expect(getMediaAccounts()).resolves.toEqual([
      expect.objectContaining({ id: 1, mediaType: 'TIKTOK' }),
    ])
    expect(httpClient.get).toHaveBeenCalledWith(
      '/api/user/promotion/media-accounts',
    )
  })

  it('throws the API message when loading fails', async () => {
    vi.mocked(httpClient.get).mockResolvedValueOnce({
      data: { code: 3001, message: '媒体账号不存在', data: null },
    })

    await expect(getMediaAccounts()).rejects.toThrow('媒体账号不存在')
  })
})

describe('media account mutations', () => {
  it('creates an account filing with the user account payload', async () => {
    vi.mocked(httpClient.post).mockResolvedValueOnce({
      data: { code: 0, message: 'ok', data: { id: 7, status: 1 } },
    })

    await expect(
      createMediaAccount({
        mediaType: 'TIKTOK',
        externalAccountId: 'creator-7',
        accountName: 'Creator Seven',
        accountLink: 'https://tiktok.com/@creator-7',
      }),
    ).resolves.toEqual({ id: 7, status: 1 })
    expect(httpClient.post).toHaveBeenCalledWith(
      '/api/user/promotion/media-accounts',
      {
        mediaType: 'TIKTOK',
        externalAccountId: 'creator-7',
        accountName: 'Creator Seven',
        accountLink: 'https://tiktok.com/@creator-7',
      },
    )
  })

  it('submits a GoodShort filing by account and provider id', async () => {
    vi.mocked(httpClient.post).mockResolvedValueOnce({
      data: {
        code: 0,
        message: 'ok',
        data: { providerId: 3, status: 'PENDING' },
      },
    })

    await expect(submitMediaFiling(7, 3)).resolves.toEqual({
      providerId: 3,
      status: 'PENDING',
    })
    expect(httpClient.post).toHaveBeenCalledWith(
      '/api/user/promotion/media-accounts/7/filings/3',
    )
  })
})
