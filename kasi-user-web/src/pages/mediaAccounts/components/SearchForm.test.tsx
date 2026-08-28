import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SearchForm from './SearchForm'

afterEach(cleanup)

describe('Media account SearchForm', () => {
  it('renders Starter-style filters and submits the form values', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)

    render(<SearchForm onSubmit={onSubmit} onReset={vi.fn()} />)

    expect(screen.getByText('媒体平台')).toBeTruthy()
    expect(screen.getByPlaceholderText('输入账号名称或账号 ID')).toBeTruthy()
    expect(screen.queryByText('账号状态')).toBeNull()
    expect(screen.queryByText('GoodShort 报白状态')).toBeNull()

    await user.click(screen.getByRole('button', { name: '查询' }))

    expect(onSubmit).toHaveBeenCalledWith({
      keyword: '',
      mediaType: undefined,
    })
  })

  it('calls the reset callback when reset is clicked', async () => {
    const user = userEvent.setup()
    const onReset = vi.fn()

    render(<SearchForm onSubmit={vi.fn()} onReset={onReset} />)
    await user.click(screen.getByRole('button', { name: '重置' }))

    expect(onReset).toHaveBeenCalledTimes(1)
  })

  it('keeps the Starter spacing around the query button', () => {
    render(<SearchForm onSubmit={vi.fn()} onReset={vi.fn()} />)

    expect(screen.getByRole('button', { name: '查询' }).style.margin).toBe(
      '0px 20px',
    )
  })
})
