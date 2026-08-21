import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { setupServer } from 'msw/node'
import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

vi.mock('@ant-design/pro-components', async () => {
  const React = await vi.importActual<typeof import('react')>('react')
  const antd = await vi.importActual<typeof import('antd')>('antd')

  type Column = {
    dataIndex?: string
    hideInTable?: boolean
    render?: (...args: unknown[]) => React.ReactNode
    renderText?: (value: unknown) => React.ReactNode
    title?: React.ReactNode
  }

  const PageContainer = ({
    title,
    content,
    children,
    ...rest
  }: React.PropsWithChildren<{
    title?: React.ReactNode
    content?: React.ReactNode
  }>) => (
    <section {...rest}>
      <h1>{title}</h1>
      <div>{content}</div>
      {children}
    </section>
  )

  const ProTable = ({
    actionRef,
    columns,
    request,
    rowKey,
    toolBarRender,
  }: {
    actionRef?: React.Ref<{ reload: () => void }>
    columns: Column[]
    request: (params: Record<string, unknown>) => Promise<{
      data?: Record<string, unknown>[]
    }>
    rowKey: string
    toolBarRender?: () => React.ReactNode[]
  }) => {
    const [data, setData] = React.useState<Record<string, unknown>[]>([])
    const [keyword, setKeyword] = React.useState('')
    const load = React.useCallback(
      async (nextKeyword = '') => {
        const result = await request({
          current: 1,
          pageSize: 20,
          keyword: nextKeyword || undefined,
        })
        setData(result.data ?? [])
      },
      [request],
    )

    React.useImperativeHandle(actionRef, () => ({
      reload: () => void load(keyword),
    }))
    React.useEffect(() => {
      void load()
    }, [load])

    return (
      <div>
        <form
          onSubmit={(event) => {
            event.preventDefault()
            void load(keyword)
          }}
        >
          <label>
            关键词
            <antd.Input
              value={keyword}
              placeholder="账号、姓名或联系方式"
              onChange={(event) => setKeyword(event.target.value)}
            />
          </label>
          <antd.Button type="primary" htmlType="submit">
            查询
          </antd.Button>
          <antd.Button
            onClick={() => {
              setKeyword('')
              void load()
            }}
          >
            重置
          </antd.Button>
        </form>
        <div>{toolBarRender?.()}</div>
        <antd.Table
          rowKey={rowKey}
          pagination={false}
          columns={columns.filter((column) => !column.hideInTable)}
          dataSource={data}
        />
      </div>
    )
  }

  const ProDescriptions = ({
    columns,
    dataSource,
  }: {
    columns: Column[]
    dataSource: Record<string, unknown>
  }) => (
    <dl>
      {columns.map((column, index) => {
        const value = column.dataIndex
          ? dataSource[column.dataIndex]
          : undefined
        const text = column.renderText ? column.renderText(value) : value
        const content = column.render
          ? column.render(text, dataSource, index)
          : text
        return (
          <React.Fragment key={column.dataIndex ?? index}>
            <dt>{column.title}</dt>
            <dd>{content as React.ReactNode}</dd>
          </React.Fragment>
        )
      })}
    </dl>
  )

  return { PageContainer, ProTable, ProDescriptions }
})

import App from './App'
import { useAuthStore } from './features/auth/authStore'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => window.history.replaceState({}, '', '/login'))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  useAuthStore.getState().clearSession()
  window.sessionStorage.clear()
  delete document.documentElement.dataset.theme
  vi.restoreAllMocks()
})
afterAll(() => server.close())

describe('App', () => {
  it('routes regular administrators to the drama catalog menu', async () => {
    let catalogRequestCount = 0
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: [
            {
              id: 1,
              providerCode: 'GOODSHORT',
              providerName: 'GoodShort',
              status: 1,
              capabilities: ['FULL_DRAMA_SYNC', 'INCREMENTAL_DRAMA_SYNC'],
              connection: {
                id: 11,
                connectionName: 'GoodShort production',
                baseUrl: 'https://example.com',
                partnerId: 'pid',
                currency: 'USD',
                status: 1,
                credentialConfigured: true,
                createdAt: '2026-08-20T08:00:00',
                updatedAt: '2026-08-20T08:00:00',
              },
            },
          ],
        }),
      ),
      http.get('/api/admin/drama/catalog', () => {
        catalogRequestCount += 1
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { list: [], page: 1, size: 20, total: 0 },
        })
      }),
    )
    useAuthStore.getState().setSession({
      accessToken: 'test-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      admin: {
        id: 2,
        username: 'operator01',
        realName: '运营管理员',
        mobile: null,
        email: null,
        avatarUrl: null,
        isSuperAdmin: 0,
      },
    })
    window.history.replaceState({}, '', '/drama/catalog')

    render(<App />)

    expect(
      await screen.findByRole('heading', { name: '短剧目录' }),
    ).toBeInTheDocument()
    expect(screen.getByText('短剧管理')).toBeInTheDocument()
    expect(screen.getByText('短剧目录', { selector: 'a' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/drama/catalog')
    await waitFor(() => expect(catalogRequestCount).toBeGreaterThanOrEqual(2))
  })

  it('renders the administrator login entry', async () => {
    render(<App />)

    expect(
      await screen.findByRole('heading', { name: 'Kasi 管理后台' }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('登录账号')).toBeInTheDocument()
    expect(screen.getByLabelText('密码')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '登录' })).toBeInTheDocument()
  })

  it('opens the administration workspace after login', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    let requestBody: unknown
    server.use(
      http.post('/api/admin/auth/login', async ({ request }) => {
        requestBody = await request.json()
        return HttpResponse.json({
          code: 0,
          message: '登录成功',
          data: {
            accessToken: 'test-token',
            tokenType: 'Bearer',
            expiresIn: 7200,
            admin: {
              id: 1,
              username: 'kasiadmin',
              realName: '系统管理员',
              mobile: null,
              email: null,
              avatarUrl: null,
              isSuperAdmin: 1,
            },
          },
        })
      }),
    )

    const user = userEvent.setup()
    render(<App />)
    await user.type(screen.getByLabelText('登录账号'), 'kasiadmin')
    await user.type(screen.getByLabelText('密码'), 'kasi123456')
    await user.click(screen.getByRole('button', { name: '登录' }))

    expect(await screen.findByText('¥ 126,560')).toBeInTheDocument()
    expect(screen.getAllByText('访问量')).toHaveLength(2)
    expect(screen.getByText('支付笔数')).toBeInTheDocument()
    expect(screen.getByText('运营活动效果')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '销售额' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '访问量' })).toBeInTheDocument()
    expect(screen.getByTestId('analysis-sales-card')).toHaveStyle({
      marginTop: '24px',
    })
    expect(
      screen.getByRole('heading', { name: '门店销售额排名' }),
    ).toBeInTheDocument()
    expect(screen.getByText('线上热门搜索')).toBeInTheDocument()
    expect(screen.getByText('销售额类别占比')).toBeInTheDocument()
    expect(
      screen.getByRole('navigation', { name: '主导航' }),
    ).toBeInTheDocument()
    const banner = screen.getByRole('banner')
    expect(banner).toBeInTheDocument()
    expect(
      within(banner).getByRole('searchbox', { name: '搜索导航' }),
    ).toHaveAttribute('placeholder', '输入内容查询')
    expect(
      within(banner).getByRole('button', { name: '语言' }),
    ).toBeInTheDocument()
    expect(screen.queryByText('首页')).not.toBeInTheDocument()
    expect(
      within(banner).getByRole('button', { name: '通知' }),
    ).toBeInTheDocument()
    expect(
      within(banner).getByRole('button', { name: '切换深色模式' }),
    ).toBeInTheDocument()
    expect(
      within(banner).getByRole('button', { name: '布局设置' }),
    ).toBeInTheDocument()
    expect(
      within(banner).getByRole('button', { name: '进入全屏' }),
    ).toBeInTheDocument()
    expect(screen.getByText('管理员管理')).toBeInTheDocument()
    expect(screen.getByText('用户管理')).toBeInTheDocument()
    expect(screen.getByText('推广管理')).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: '媒体账号报备' }),
    ).toBeInTheDocument()
    const accountMenuButton = within(banner).getByRole('button', {
      name: '账户菜单',
    })
    expect(accountMenuButton).toHaveTextContent('系统管理员')
    expect(
      within(banner).queryByRole('button', { name: '收起侧边栏' }),
    ).not.toBeInTheDocument()

    await user.click(
      within(banner).getByRole('button', { name: '切换深色模式' }),
    )
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(
      within(banner).getByRole('button', { name: '切换浅色模式' }),
    ).toBeInTheDocument()

    await user.click(accountMenuButton)
    const profileMenuItem = await screen.findByRole('menuitem', {
      name: '个人主页',
    })
    await user.click(profileMenuItem)
    expect(
      await screen.findByRole('heading', { name: '个人主页' }),
    ).toBeInTheDocument()
    expect(screen.getByText('姓名')).toBeInTheDocument()
    expect(screen.getByText('首页')).toBeInTheDocument()

    const collapseButton = screen.getByRole('button', {
      name: '收起侧边栏',
    })
    await user.click(collapseButton)
    expect(
      screen.getByRole('button', { name: '展开侧边栏' }),
    ).toBeInTheDocument()
    expect(requestBody).toEqual({
      account: 'kasiadmin',
      password: 'kasi123456',
    })
    expect(consoleError.mock.calls.flat().join(' ')).not.toContain(
      '[antd: Drawer] `width` is deprecated',
    )
  })

  it('opens the short drama provider page for an ordinary administrator', async () => {
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: [
            {
              id: 1,
              providerCode: 'GOODSHORT',
              providerName: 'GoodShort',
              status: 1,
              capabilities: ['ACCOUNT_FILING'],
              connection: null,
            },
          ],
        }),
      ),
    )
    useAuthStore.getState().setSession({
      accessToken: 'ordinary-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      admin: {
        id: 2,
        username: 'operator',
        realName: '运营管理员',
        mobile: null,
        email: null,
        avatarUrl: null,
        isSuperAdmin: 0,
      },
    })

    const user = userEvent.setup()
    window.history.replaceState({}, '', '/dashboard')
    render(<App />)
    await screen.findByText('¥ 126,560')
    expect(screen.getByText('系统配置')).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: '短剧 API 配置' }))

    expect(
      await screen.findByRole('heading', { name: '短剧 API 配置' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('GoodShort')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: '配置' }),
    ).not.toBeInTheDocument()
  })

  it('loads the administrator table and protects the unique super administrator', async () => {
    const listRequestUrls: string[] = []
    server.use(
      http.get('/api/admin/management', ({ request }) => {
        listRequestUrls.push(request.url)
        return HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: {
            list: [
              {
                id: 1,
                username: 'kasiadmin',
                realName: '系统管理员',
                mobile: null,
                email: null,
                avatarUrl: null,
                departmentId: null,
                status: 1,
                isSuperAdmin: 1,
                lastLoginAt: null,
                createdAt: '2026-08-15T09:00:00',
              },
            ],
            page: 1,
            size: 20,
            total: 1,
          },
        })
      }),
    )
    useAuthStore.getState().setSession({
      accessToken: 'test-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      admin: {
        id: 1,
        username: 'kasiadmin',
        realName: '系统管理员',
        mobile: null,
        email: null,
        avatarUrl: null,
        isSuperAdmin: 1,
      },
    })
    window.history.replaceState({}, '', '/admin-management')
    const user = userEvent.setup()

    render(<App />)

    expect(
      await screen.findByRole(
        'heading',
        { name: '管理员管理' },
        { timeout: 5_000 },
      ),
    ).toBeInTheDocument()
    expect(await screen.findByTestId('admin-detail-1')).toBeInTheDocument()
    expect(
      screen.getByRole('columnheader', { name: '姓名' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('columnheader', { name: '真实姓名' }),
    ).not.toBeInTheDocument()
    expect(listRequestUrls.at(-1)).toContain('page=1')
    expect(listRequestUrls.at(-1)).toContain('size=20')
    expect(screen.getByTestId('admin-detail-1')).toBeEnabled()
    expect(screen.queryByTestId('admin-edit-1')).not.toBeInTheDocument()
    expect(screen.queryByRole('switch')).not.toBeInTheDocument()
    expect(screen.getByText('启用')).toBeInTheDocument()
    expect(screen.queryByTestId('admin-password-1')).not.toBeInTheDocument()
    expect(screen.getByTestId('admin-delete-1')).toBeDisabled()

    await user.type(screen.getByPlaceholderText('账号、姓名或联系方式'), 'kasi')
    await user.click(screen.getByRole('button', { name: '查 询' }))
    await waitFor(() =>
      expect(listRequestUrls.at(-1)).toContain('keyword=kasi'),
    )
    await user.click(screen.getByRole('button', { name: '重 置' }))
    await waitFor(() => expect(listRequestUrls.length).toBeGreaterThan(2))
  })

  it('hides administrator management from regular administrators', async () => {
    useAuthStore.getState().setSession({
      accessToken: 'test-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      admin: {
        id: 2,
        username: 'operator01',
        realName: '运营管理员',
        mobile: null,
        email: null,
        avatarUrl: null,
        isSuperAdmin: 0,
      },
    })
    window.history.replaceState({}, '', '/admin-management')

    render(<App />)

    expect(await screen.findByText('¥ 126,560')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/dashboard')
    expect(screen.queryByText('管理员管理')).not.toBeInTheDocument()
    expect(screen.getByText('用户管理')).toBeInTheDocument()
  })

  it('connects administrator detail, create and delete endpoints', async () => {
    let createdBody: unknown
    let updatedBody: unknown
    let passwordBody: unknown
    let deleted = false
    server.use(
      http.get('/api/admin/management', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: {
            list: [
              {
                id: 2,
                username: 'operator01',
                realName: '运营管理员',
                mobile: '13800138000',
                email: 'operator@example.com',
                avatarUrl: null,
                departmentId: 10,
                status: 1,
                isSuperAdmin: 0,
                lastLoginAt: null,
                createdAt: '2026-08-15T09:00:00',
              },
            ],
            page: 1,
            size: 20,
            total: 1,
          },
        }),
      ),
      http.get('/api/admin/management/2', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: {
            id: 2,
            username: 'operator01',
            realName: '运营管理员',
            mobile: '13800138000',
            email: 'operator@example.com',
            avatarUrl: 'https://example.com/avatar.png',
            departmentId: 10,
            status: 1,
            isSuperAdmin: 0,
            lastLoginAt: null,
            createdAt: '2026-08-15T09:00:00',
            lastLoginIp: null,
            passwordChangedAt: null,
            remark: '详情接口中的备注',
            createdBy: 1,
            updatedBy: 1,
            updatedAt: '2026-08-15T10:00:00',
          },
        }),
      ),
      http.post('/api/admin/management', async ({ request }) => {
        createdBody = await request.json()
        return HttpResponse.json({ code: 0, message: '创建成功', data: {} })
      }),
      http.put('/api/admin/management/2', async ({ request }) => {
        updatedBody = await request.json()
        return HttpResponse.json({ code: 0, message: '编辑成功', data: {} })
      }),
      http.put('/api/admin/management/2/password', async ({ request }) => {
        passwordBody = await request.json()
        return HttpResponse.json({ code: 0, message: '修改成功', data: null })
      }),
      http.delete('/api/admin/management/2', () => {
        deleted = true
        return HttpResponse.json({ code: 0, message: '删除成功', data: null })
      }),
    )
    useAuthStore.getState().setSession({
      accessToken: 'test-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      admin: {
        id: 1,
        username: 'kasiadmin',
        realName: '系统管理员',
        mobile: null,
        email: null,
        avatarUrl: null,
        isSuperAdmin: 1,
      },
    })
    window.history.replaceState({}, '', '/admin-management')
    const user = userEvent.setup()

    render(<App />)
    await screen.findByText('运营管理员')
    expect(screen.getByTestId('admin-detail-2')).toBeInTheDocument()
    expect(screen.queryByTestId('admin-password-2')).not.toBeInTheDocument()
    expect(screen.queryByTestId('admin-edit-2')).not.toBeInTheDocument()
    expect(screen.queryByRole('switch')).not.toBeInTheDocument()

    await user.click(screen.getByTestId('admin-delete-2'))
    await user.click(await screen.findByTestId('admin-delete-confirm-2'))
    await waitFor(() => expect(deleted).toBe(true))

    await user.click(screen.getByTestId('admin-create'))
    const createDialog = screen.getByText('新增管理员').closest('.ant-modal')
    expect(createDialog).not.toBeNull()
    expect(
      within(createDialog as HTMLElement).queryByLabelText('头像地址'),
    ).not.toBeInTheDocument()
    await user.type(
      within(createDialog as HTMLElement).getByLabelText('登录账号'),
      'operator02',
    )
    await user.type(
      within(createDialog as HTMLElement).getByLabelText('姓名'),
      '新管理员',
    )
    await user.type(
      within(createDialog as HTMLElement).getByLabelText('初始密码'),
      'Password123',
    )
    await user.type(
      within(createDialog as HTMLElement).getByLabelText('确认密码'),
      'Password123',
    )
    await user.click(screen.getByTestId('admin-form-submit'))
    await waitFor(() =>
      expect(createdBody).toEqual(
        expect.objectContaining({
          username: 'operator02',
          realName: '新管理员',
          password: 'Password123',
          confirmPassword: 'Password123',
        }),
      ),
    )

    await user.click(screen.getByTestId('admin-detail-2'))
    expect((await screen.findAllByText('管理员管理')).length).toBeGreaterThan(1)
    expect(await screen.findByText('基本信息')).toBeInTheDocument()
    expect(await screen.findByText('详情接口中的备注')).toBeInTheDocument()
    const adminIdentity = screen.getByTestId('admin-detail-identity-2')
    expect(within(adminIdentity).getByRole('img')).toHaveAttribute(
      'src',
      'https://example.com/avatar.png',
    )
    expect(adminIdentity).toHaveTextContent('运营管理员')
    expect(adminIdentity).toHaveTextContent('operator01')
    expect(
      within(adminIdentity).getByTestId('admin-detail-avatar-upload-2'),
    ).toHaveAccessibleName('更换运营管理员的头像')
    await user.click(screen.getByTestId('admin-detail-edit-2'))
    expect(await screen.findByText('编辑管理员')).toBeInTheDocument()
    const adminEditDrawer = await screen.findByTestId('admin-edit-drawer-2')
    await waitFor(() => {
      expect(within(adminEditDrawer).getByLabelText('备注')).toHaveValue(
        '详情接口中的备注',
      )
    })
    expect(
      within(adminEditDrawer).queryByLabelText('头像地址'),
    ).not.toBeInTheDocument()
    await user.click(screen.getByTestId('admin-edit-form-submit'))
    await waitFor(() =>
      expect(updatedBody).toEqual(
        expect.objectContaining({
          username: 'operator01',
          remark: '详情接口中的备注',
        }),
      ),
    )

    await user.click(screen.getByTestId('admin-detail-password-2'))
    const passwordDrawer = await screen.findByTestId('admin-password-drawer-2')
    expect(
      within(passwordDrawer).queryByLabelText('原密码'),
    ).not.toBeInTheDocument()
    await user.type(
      within(passwordDrawer).getByLabelText('新密码'),
      'NewPassword123',
    )
    await user.type(
      within(passwordDrawer).getByLabelText('确认密码'),
      'NewPassword123',
    )
    expect(within(passwordDrawer).getByLabelText('新密码')).toHaveValue(
      'NewPassword123',
    )
    expect(within(passwordDrawer).getByLabelText('确认密码')).toHaveValue(
      'NewPassword123',
    )
    await user.click(screen.getByTestId('admin-password-form-submit'))
    await waitFor(() =>
      expect(passwordBody).toEqual({
        newPassword: 'NewPassword123',
        confirmPassword: 'NewPassword123',
      }),
    )
    expect(useAuthStore.getState().accessToken).toBe('test-token')
  })

  it('routes self profile and password actions through administrator auth', async () => {
    const currentAdmin = {
      id: 1,
      username: 'kasiadmin',
      realName: '系统管理员',
      mobile: null,
      email: null,
      avatarUrl: null,
      departmentId: null,
      status: 1,
      isSuperAdmin: 1,
      lastLoginAt: null,
      createdAt: '2026-08-15T09:00:00',
      lastLoginIp: null,
      passwordChangedAt: null,
      remark: null,
      createdBy: null,
      updatedBy: null,
      updatedAt: null,
    }
    let profileBody: unknown
    let passwordBody: unknown
    server.use(
      http.get('/api/admin/management', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: { list: [currentAdmin], page: 1, size: 20, total: 1 },
        }),
      ),
      http.get('/api/admin/management/1', () =>
        HttpResponse.json({ code: 0, message: '查询成功', data: currentAdmin }),
      ),
      http.put('/api/admin/auth/profile', async ({ request }) => {
        profileBody = await request.json()
        return HttpResponse.json({
          code: 0,
          message: '修改成功',
          data: { ...currentAdmin, realName: '平台负责人' },
        })
      }),
      http.put('/api/admin/auth/password', async ({ request }) => {
        passwordBody = await request.json()
        return HttpResponse.json({ code: 0, message: '修改成功', data: null })
      }),
    )
    useAuthStore.getState().setSession({
      accessToken: 'test-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      admin: {
        id: 1,
        username: 'kasiadmin',
        realName: '系统管理员',
        mobile: null,
        email: null,
        avatarUrl: null,
        isSuperAdmin: 1,
      },
    })
    window.history.replaceState({}, '', '/admin-management')
    const user = userEvent.setup()

    render(<App />)
    await user.click(await screen.findByTestId('admin-detail-1'))
    expect(
      await screen.findByTestId('admin-detail-identity-1'),
    ).toHaveTextContent('系统管理员')
    await user.click(screen.getByTestId('admin-detail-edit-1'))
    const editDrawer = await screen.findByTestId('admin-edit-drawer-1')
    expect(
      within(editDrawer).queryByLabelText('部门编号'),
    ).not.toBeInTheDocument()
    expect(within(editDrawer).queryByLabelText('备注')).not.toBeInTheDocument()
    await waitFor(() =>
      expect(within(editDrawer).getByLabelText('登录账号')).toHaveValue(
        'kasiadmin',
      ),
    )
    await user.clear(within(editDrawer).getByLabelText('姓名'))
    await user.type(within(editDrawer).getByLabelText('姓名'), '平台负责人')
    await user.click(screen.getByTestId('admin-edit-form-submit'))
    await waitFor(() =>
      expect(profileBody).toEqual({
        username: 'kasiadmin',
        realName: '平台负责人',
        mobile: null,
        email: null,
      }),
    )
    expect(useAuthStore.getState().admin?.realName).toBe('平台负责人')

    await user.click(screen.getByTestId('admin-detail-password-1'))
    const passwordDrawer = await screen.findByTestId('admin-password-drawer-1')
    await user.type(
      within(passwordDrawer).getByLabelText('新密码'),
      'SelfPassword123',
    )
    await user.type(
      within(passwordDrawer).getByLabelText('确认密码'),
      'SelfPassword123',
    )
    expect(within(passwordDrawer).getByLabelText('新密码')).toHaveValue(
      'SelfPassword123',
    )
    expect(within(passwordDrawer).getByLabelText('确认密码')).toHaveValue(
      'SelfPassword123',
    )
    await user.click(screen.getByTestId('admin-password-form-submit'))
    await waitFor(() =>
      expect(passwordBody).toEqual({
        newPassword: 'SelfPassword123',
        confirmPassword: 'SelfPassword123',
      }),
    )
    expect(
      await screen.findByRole('heading', { name: 'Kasi 管理后台' }),
    ).toBeInTheDocument()
    expect(window.location.pathname).toBe('/login')
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('requires a contact method before creating a promotion user', async () => {
    let createCalled = false
    server.use(
      http.get('/api/user/management', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: { list: [], page: 1, size: 20, total: 0 },
        }),
      ),
      http.post('/api/user/management', async () => {
        createCalled = true
        return HttpResponse.json({ code: 0, message: '创建成功', data: {} })
      }),
    )
    useAuthStore.getState().setSession({
      accessToken: 'test-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      admin: {
        id: 1,
        username: 'kasiadmin',
        realName: '系统管理员',
        mobile: null,
        email: null,
        avatarUrl: null,
        isSuperAdmin: 1,
      },
    })
    window.history.replaceState({}, '', '/user-management')
    const user = userEvent.setup()

    render(<App />)
    await screen.findByRole('heading', { name: '用户管理' })
    await user.click(screen.getByTestId('user-create'))
    await user.type(screen.getByLabelText('昵称'), '新用户')
    await user.type(screen.getByLabelText('初始密码'), 'Password123')
    await user.type(screen.getByLabelText('确认密码'), 'Password123')
    await user.click(screen.getByRole('button', { name: '保 存' }))

    expect(
      await screen.findByText('手机号或邮箱至少填写一项'),
    ).toBeInTheDocument()
    expect(createCalled).toBe(false)
  })

  it('connects the complete promotion user CRUD workflow', async () => {
    const userListItem = {
      id: 7,
      userNo: 'USR0007',
      nickname: '推广用户七',
      realName: '用户七',
      mobile: '13900139000',
      email: 'user7@example.com',
      avatarUrl: null,
      status: 1,
      registerSource: 'ADMIN',
      lastLoginAt: null,
      createdAt: '2026-08-15T09:00:00',
    }
    let createdBody: unknown
    let updatedBody: unknown
    let statusBody: unknown
    let deleted = false
    server.use(
      http.get('/api/user/management', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: { list: [userListItem], page: 1, size: 20, total: 1 },
        }),
      ),
      http.get('/api/user/management/7', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: {
            ...userListItem,
            lastLoginIp: '127.0.0.1',
            remark: '用户详情备注',
            updatedAt: '2026-08-15T10:00:00',
          },
        }),
      ),
      http.post('/api/user/management', async ({ request }) => {
        createdBody = await request.json()
        return HttpResponse.json({
          code: 0,
          message: '创建成功',
          data: { ...userListItem, id: 8, userNo: 'USR0008' },
        })
      }),
      http.put('/api/user/management/7', async ({ request }) => {
        updatedBody = await request.json()
        return HttpResponse.json({
          code: 0,
          message: '编辑成功',
          data: { ...userListItem, nickname: '编辑后的用户' },
        })
      }),
      http.patch('/api/user/management/7/status', async ({ request }) => {
        statusBody = await request.json()
        return HttpResponse.json({
          code: 0,
          message: '状态修改成功',
          data: null,
        })
      }),
      http.delete('/api/user/management/7', () => {
        deleted = true
        return HttpResponse.json({ code: 0, message: '删除成功', data: null })
      }),
    )
    useAuthStore.getState().setSession({
      accessToken: 'test-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      admin: {
        id: 1,
        username: 'kasiadmin',
        realName: '系统管理员',
        mobile: null,
        email: null,
        avatarUrl: null,
        isSuperAdmin: 1,
      },
    })
    window.history.replaceState({}, '', '/user-management')
    const user = userEvent.setup()

    render(<App />)
    expect(await screen.findByText('推广用户七')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'USR0007' })).toBeInTheDocument()
    expect(screen.getByTestId('user-detail-7')).toBeInTheDocument()
    expect(screen.queryByTestId('user-password-7')).not.toBeInTheDocument()

    expect(screen.queryByTestId('user-edit-7')).not.toBeInTheDocument()
    expect(screen.queryByRole('switch')).not.toBeInTheDocument()
    await user.click(screen.getByTestId('user-detail-7'))
    expect((await screen.findAllByText('用户管理')).length).toBeGreaterThan(1)
    expect(await screen.findByText('基本信息')).toBeInTheDocument()
    expect(await screen.findByText('用户详情备注')).toBeInTheDocument()
    const userIdentity = screen.getByTestId('user-detail-identity-7')
    expect(userIdentity).toHaveTextContent('推')
    expect(userIdentity).toHaveTextContent('推广用户七')
    expect(userIdentity).toHaveTextContent('USR0007')
    expect(
      screen.queryByTestId('user-detail-password-7'),
    ).not.toBeInTheDocument()
    await user.click(screen.getByTestId('user-detail-edit-7'))
    expect(await screen.findByText('编辑用户')).toBeInTheDocument()
    const userEditDrawer = await screen.findByTestId('user-edit-drawer-7')
    await waitFor(() => {
      expect(within(userEditDrawer).getByLabelText('手机号')).toHaveValue(
        '13900139000',
      )
      expect(within(userEditDrawer).getByLabelText('昵称')).toHaveValue(
        '推广用户七',
      )
    })
    await user.clear(within(userEditDrawer).getByLabelText('昵称'))
    await user.type(
      within(userEditDrawer).getByLabelText('昵称'),
      '编辑后的用户',
    )
    await user.click(screen.getByTestId('user-edit-form-submit'))
    await waitFor(() =>
      expect(updatedBody).toEqual(
        expect.objectContaining({ nickname: '编辑后的用户' }),
      ),
    )
    await waitFor(() => expect(userEditDrawer).not.toBeVisible())
    const detailDrawer = screen
      .getAllByText('用户管理')
      .at(-1)
      ?.closest('.ant-drawer')
    expect(detailDrawer).not.toBeNull()
    await user.click(
      within(detailDrawer as HTMLElement).getByRole('button', { name: '关闭' }),
    )

    await user.click(screen.getByTestId('user-more-7'))
    await user.click(await screen.findByText('禁用'))
    await waitFor(() => expect(statusBody).toEqual({ status: 0 }))

    await user.click(screen.getByTestId('user-more-7'))
    await user.click(await screen.findByText('删除'))
    await user.click(await screen.findByTestId('user-delete-confirm-7'))
    await waitFor(() => expect(deleted).toBe(true))

    await user.click(screen.getByTestId('user-create'))
    const createDialog = screen.getByText('新增用户').closest('.ant-modal')
    expect(createDialog).not.toBeNull()
    await user.type(
      within(createDialog as HTMLElement).getByLabelText('手机号'),
      '13700137000',
    )
    await user.type(
      within(createDialog as HTMLElement).getByLabelText('昵称'),
      '新推广用户',
    )
    await user.type(
      within(createDialog as HTMLElement).getByLabelText('初始密码'),
      'Password123',
    )
    await user.type(
      within(createDialog as HTMLElement).getByLabelText('确认密码'),
      'Password123',
    )
    await user.click(screen.getByTestId('user-form-submit'))
    await waitFor(() =>
      expect(createdBody).toEqual(
        expect.objectContaining({
          mobile: '13700137000',
          nickname: '新推广用户',
          password: 'Password123',
          confirmPassword: 'Password123',
        }),
      ),
    )
  }, 20_000)
})
