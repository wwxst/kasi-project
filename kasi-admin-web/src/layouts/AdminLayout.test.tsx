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

    expect(screen.queryByRole('link', { name: '分析页' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Kasi 管理后台' })).toHaveAttribute(
      'href',
      '/user-management',
    )

    const search = screen.getByRole('searchbox')
    fireEvent.change(search, { target: { value: '用户' } })
    fireEvent.keyDown(search, { key: 'Enter', code: 'Enter' })
    expect(screen.getByTestId('location')).toHaveTextContent('/user-management')
  })
})
