import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../features/auth/authStore'
import { DashboardPage } from './DashboardPage'

describe('DashboardPage', () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: 'test-token',
      admin: {
        id: 1,
        username: 'operator',
        realName: '平台负责人',
        mobile: null,
        email: null,
        avatarUrl: null,
        isSuperAdmin: 1,
      },
    })
  })

  it('renders a welcome message instead of static demo cards', () => {
    render(<DashboardPage />)

    expect(
      screen.getByRole('heading', {
        name: '欢迎 平台负责人 使用卡司短剧推广平台',
      }),
    ).toBeInTheDocument()
    expect(screen.queryByTestId('dashboard-demo-card')).not.toBeInTheDocument()
    expect(screen.queryByTestId('chart')).not.toBeInTheDocument()
  })
})
