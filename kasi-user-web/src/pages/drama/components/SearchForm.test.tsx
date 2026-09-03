import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SearchForm from './SearchForm'

afterEach(cleanup)

describe('Drama SearchForm', () => {
  it('renders title and language filters and submits normalized values', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<SearchForm onSubmit={onSubmit} onReset={vi.fn()} />)

    expect(screen.getByText('短剧标题')).toBeTruthy()
    expect(screen.getByPlaceholderText('输入短剧标题')).toBeTruthy()
    expect(screen.getByText('语言')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: '查询' }))

    expect(onSubmit).toHaveBeenCalledWith({ title: '', language: undefined })
  })
})
