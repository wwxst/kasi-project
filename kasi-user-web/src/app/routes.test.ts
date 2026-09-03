import { describe, expect, it } from 'vitest'
import { appRoutes } from './routes'

describe('appRoutes', () => {
  it('registers personal center without showing it in the sidebar', () => {
    expect(
      appRoutes.find((route) => route.path === '/workspace/profile'),
    ).toMatchObject({ title: '个人中心', hiddenInMenu: true })
  })

  it('does not expose a standalone commission menu route', () => {
    expect(
      appRoutes.some((route) => route.path === '/workspace/commission'),
    ).toBe(false)
  })
})
