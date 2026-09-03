import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AccountFilingDialog from './AccountFilingDialog'

afterEach(cleanup)

describe('AccountFilingDialog', () => {
  it('blocks submission when required account fields are empty', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()

    render(
      <AccountFilingDialog visible onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    await user.click(screen.getByRole('button', { name: '提交报白' }))

    expect(onSubmit).not.toHaveBeenCalled()
    expect(await screen.findByText('请选择媒体平台')).toBeTruthy()
    expect(await screen.findByText('请输入账号 ID')).toBeTruthy()
    expect(await screen.findByText('请输入账号名称')).toBeTruthy()
    expect(await screen.findByText('请输入账号主页链接')).toBeTruthy()
  })

  it('submits the account filing form values', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)

    render(
      <AccountFilingDialog visible onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    await user.click(screen.getByPlaceholderText('请选择媒体平台'))
    await user.click(await screen.findByText('TikTok'))
    await user.type(screen.getByPlaceholderText('请输入账号 ID'), 'creator-7')
    await user.type(
      screen.getByPlaceholderText('请输入账号名称'),
      'Creator Seven',
    )
    await user.type(
      screen.getByPlaceholderText('请输入账号主页链接'),
      'https://tiktok.com/@creator-7',
    )
    await user.click(screen.getByRole('button', { name: '提交报白' }))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        mediaType: 'TIKTOK',
        externalAccountId: 'creator-7',
        accountName: 'Creator Seven',
        accountLink: 'https://tiktok.com/@creator-7',
      }),
    )
  })
})
