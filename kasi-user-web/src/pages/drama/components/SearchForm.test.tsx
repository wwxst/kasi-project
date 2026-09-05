import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SearchForm from './SearchForm'

vi.mock('../../../features/dramas/dramasApi', () => ({
  listDramaLanguageOptions: vi.fn().mockResolvedValue([]),
}))

afterEach(cleanup)

describe('Drama SearchForm', () => {
  it('renders title and language filters and submits normalized values', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <SearchForm onSubmit={onSubmit} onReset={vi.fn()} />
      </QueryClientProvider>,
    )

    expect(screen.getByText('短剧标题')).toBeTruthy()
    expect(screen.getByPlaceholderText('输入短剧标题')).toBeTruthy()
    expect(screen.getByText('语言')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: '查询' }))

    expect(onSubmit).toHaveBeenCalledWith({ title: '', language: undefined })
  })
})
