import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../features/auth/authStore'
import WorkspacePage from './WorkspacePage'

const apiMocks = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
  getProjects: vi.fn(),
}))

vi.mock('../features/auth/authApi', () => ({
  getCurrentUser: apiMocks.getCurrentUser,
}))
vi.mock('../features/projects/projectApi', () => ({
  getProjects: apiMocks.getProjects,
}))

beforeEach(() => {
  apiMocks.getCurrentUser.mockReset()
  apiMocks.getProjects.mockReset()
  apiMocks.getCurrentUser.mockResolvedValue({ nickname: '卡司用户' })
  apiMocks.getProjects.mockResolvedValue([
    {
      id: 2,
      name: '项目B',
      coverImageUrl: '/uploads/promotion-projects/b.png',
      projectDocumentUrl: 'https://example.com/b',
      sortOrder: 20,
    },
    {
      id: 1,
      name: '项目A',
      coverImageUrl: '/uploads/promotion-projects/a.png',
      projectDocumentUrl: 'https://example.com/a',
      sortOrder: 30,
    },
  ])
  useAuthStore.setState({ accessToken: 'test-token' })
})

describe('WorkspacePage', () => {
  it('renders ordered project cards with document links opening in a new tab', async () => {
    renderPage()

    const projects = screen.getByRole('region', { name: '项目推荐' })
    const cards = await within(projects).findAllByRole('article')
    expect(cards).toHaveLength(2)
    expect(cards[0].getAttribute('aria-label')).toBe('项目B')
    expect(cards[1].getAttribute('aria-label')).toBe('项目A')
    const documentLink = within(cards[0]).getByRole('link', {
      name: '项目文档',
    })
    expect(documentLink.getAttribute('href')).toBe('https://example.com/b')
    expect(documentLink.getAttribute('target')).toBe('_blank')
    expect(documentLink.getAttribute('rel')).toBe('noopener noreferrer')
  })

  it('shows a retry action when project loading fails', async () => {
    apiMocks.getProjects.mockRejectedValueOnce(new Error('offline'))
    renderPage()

    expect(await screen.findByText('项目加载失败')).toBeTruthy()
    apiMocks.getProjects.mockResolvedValueOnce([])
    await userEvent.setup().click(screen.getByRole('button', { name: '重试' }))
    await waitFor(() =>
      expect(apiMocks.getProjects.mock.calls.length).toBeGreaterThanOrEqual(2),
    )
    expect(await screen.findByText('暂无项目')).toBeTruthy()
  })

  it('shows an empty state when no enabled projects exist', async () => {
    apiMocks.getProjects.mockResolvedValueOnce([])
    renderPage()
    expect(await screen.findByText('暂无项目')).toBeTruthy()
  })
})

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <WorkspacePage />
    </QueryClientProvider>,
  )
}
