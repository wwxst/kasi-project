import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../features/auth/authStore'
import { AdminLayout } from './AdminLayout'

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname}</output>
}

describe('AdminLayout navigation', () => {
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

  it('hides the demo dashboard entry and sends brand/search to user management', () => {
    render(
      <MemoryRouter initialEntries={['/user-management']}>
        <Routes>
          <Route element={<AdminLayout />}>
            <Route path="*" element={<LocationProbe />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(
      screen.queryByRole('link', { name: '分析页' }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Kasi 管理后台' })).toHaveAttribute(
      'href',
      '/user-management',
    )

    const search = screen.getByRole('searchbox')
    fireEvent.change(search, { target: { value: '用户' } })
    fireEvent.keyDown(search, { key: 'Enter', code: 'Enter' })
    expect(screen.getByTestId('location')).toHaveTextContent('/user-management')
  })

  it('groups drama and promotion links under short drama management', () => {
    render(
      <MemoryRouter initialEntries={['/user-management']}>
        <Routes>
          <Route element={<AdminLayout />}>
            <Route path="*" element={<LocationProbe />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getAllByText('短剧管理').length).toBeGreaterThan(0)
    expect(
      screen.queryByRole('link', { name: '短剧目录' }),
    ).not.toBeInTheDocument()

    fireEvent.click(screen.getAllByRole('menuitem', { name: '短剧管理' })[0])
    expect(screen.getByRole('link', { name: '账号报备' })).toHaveAttribute(
      'href',
      '/promotion/media-accounts',
    )
    expect(screen.getByRole('link', { name: '推广任务' })).toHaveAttribute(
      'href',
      '/promotion/links',
    )
    expect(screen.getByRole('link', { name: '推广订单' })).toHaveAttribute(
      'href',
      '/promotion/orders',
    )
    expect(screen.getByText('同步管理')).toBeInTheDocument()

    fireEvent.click(screen.getAllByRole('menuitem', { name: '同步管理' })[0])
    expect(screen.getByRole('link', { name: '短剧同步' })).toHaveAttribute(
      'href',
      '/drama/sync/catalog',
    )
  })

  it('opens nested sync navigation for a deep sync route', () => {
    render(
      <MemoryRouter initialEntries={['/drama/sync/content']}>
        <Routes>
          <Route element={<AdminLayout />}>
            <Route path="*" element={<LocationProbe />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(
      screen.getAllByRole('link', { name: '剧集同步' })[0],
    ).toHaveAttribute('href', '/drama/sync/content')
  })

  it('opens system settings navigation for a deep settings route', () => {
    render(
      <MemoryRouter initialEntries={['/system-config/sms']}>
        <Routes>
          <Route element={<AdminLayout />}>
            <Route path="*" element={<LocationProbe />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(
      screen.getAllByRole('link', { name: '系统配置' })[0],
    ).toHaveAttribute('href', '/system-config/sms')
  })
})
