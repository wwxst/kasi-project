import { useEffect, useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  Avatar,
  Badge,
  Button,
  Dropdown,
  Input,
  Layout,
  Menu,
  Space,
} from 'tdesign-react'
import {
  DashboardIcon,
  FileIcon,
  LinkIcon,
  LogoutIcon,
  MailIcon,
  MenuFoldIcon,
  MenuUnfoldIcon,
  SearchIcon,
  SettingIcon,
  UserCircleIcon,
} from 'tdesign-icons-react'
import { logoutUser, useCurrentUser } from '../features/account/api/accountApi'
import { useAuthStore } from '../features/auth/model/authStore'
import { AccountLogoutDialog } from './AccountLogoutDialog'

export function AccountLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const clearSession = useAuthStore((state) => state.clearSession)
  const { data: currentUser } = useCurrentUser()
  const [logoutDialogOpen, setLogoutDialogOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)

  const displayName =
    currentUser?.nickname || currentUser?.mobile || currentUser?.email || '用户'
  const activeMenu = location.pathname.startsWith('/account/filing')
    ? '/account/filing'
    : location.pathname.startsWith('/promotion/create')
      ? '/promotion/create'
      : location.pathname.startsWith('/promotion/links')
        ? '/promotion/links'
        : location.pathname.startsWith('/promotion/income')
          ? '/promotion/income'
          : location.pathname.startsWith('/account/security')
            ? '/account/security'
            : '/account'

  useEffect(() => {
    setMobileMenuOpen(false)
  }, [location.pathname])

  function handleMenuToggle() {
    if (window.innerWidth <= 760) {
      setMobileMenuOpen((open) => !open)
      return
    }
    setSidebarCollapsed((collapsed) => !collapsed)
  }

  async function handleLogout() {
    setLoggingOut(true)
    try {
      await logoutUser()
    } finally {
      clearSession()
      setLogoutDialogOpen(false)
      setLoggingOut(false)
      navigate('/login', { replace: true, state: { notice: '已退出登录' } })
    }
  }

  return (
    <div
      className={`account-app${mobileMenuOpen ? ' account-app-menu-open' : ''}`}
    >
      <Layout className="account-layout">
        <Layout.Aside
          className={`account-aside${sidebarCollapsed ? ' account-aside-collapsed' : ''}`}
          width={sidebarCollapsed ? '64px' : '232px'}
        >
          <nav aria-label="用户中心主导航" className="account-sidebar-nav">
            <Menu
              className="account-menu"
              width={sidebarCollapsed ? '64px' : '232px'}
              value={activeMenu}
              collapsed={sidebarCollapsed}
              theme="light"
              logo={
                <Link className="account-menu-logo" to="/account">
                  <span className="account-brand-mark" aria-hidden="true">
                    K
                  </span>
                  {!sidebarCollapsed && <span>Kasi 用户中心</span>}
                </Link>
              }
              operations={
                !sidebarCollapsed ? (
                  <div className="account-menu-tip">Kasi 推广平台</div>
                ) : undefined
              }
              onChange={(value) => navigate(String(value))}
            >
              <Menu.MenuItem
                value="/account"
                href="/account"
                icon={<DashboardIcon />}
                onClick={({ e }) => {
                  e.preventDefault()
                  navigate('/account')
                }}
              >
                账户概览
              </Menu.MenuItem>
              <Menu.MenuItem
                value="/account/filing"
                href="/account/filing"
                icon={<FileIcon />}
                onClick={({ e }) => {
                  e.preventDefault()
                  navigate('/account/filing')
                }}
              >
                账号报白
              </Menu.MenuItem>
              <Menu.MenuItem
                value="/promotion/create"
                href="/promotion/create"
                icon={<LinkIcon />}
                onClick={({ e }) => {
                  e.preventDefault()
                  navigate('/promotion/create')
                }}
              >
                创建推广
              </Menu.MenuItem>
              <Menu.MenuItem
                value="/promotion/links"
                href="/promotion/links"
                icon={<LinkIcon />}
                onClick={({ e }) => {
                  e.preventDefault()
                  navigate('/promotion/links')
                }}
              >
                推广链接记录
              </Menu.MenuItem>
              <Menu.MenuItem
                value="/promotion/income"
                href="/promotion/income"
                icon={<LinkIcon />}
                onClick={({ e }) => {
                  e.preventDefault()
                  navigate('/promotion/income')
                }}
              >
                佣金明细
              </Menu.MenuItem>
              <Menu.MenuItem
                value="/account/security"
                href="/account/security"
                icon={<SettingIcon />}
                onClick={({ e }) => {
                  e.preventDefault()
                  navigate('/account/security')
                }}
              >
                安全设置
              </Menu.MenuItem>
            </Menu>
          </nav>
        </Layout.Aside>
        <Layout className="account-main-layout">
          <Layout.Header className="account-header" height="56px">
            <div className="account-header-leading">
              <Button
                className="account-menu-toggle"
                aria-label={
                  window.innerWidth <= 760
                    ? mobileMenuOpen
                      ? '关闭菜单'
                      : '打开菜单'
                    : sidebarCollapsed
                      ? '展开菜单'
                      : '收起菜单'
                }
                icon={
                  window.innerWidth <= 760 || sidebarCollapsed ? (
                    <MenuUnfoldIcon />
                  ) : (
                    <MenuFoldIcon />
                  )
                }
                shape="square"
                variant="text"
                onClick={handleMenuToggle}
              />
              <Input
                className="account-header-search"
                prefixIcon={<SearchIcon />}
                placeholder="请输入搜索内容"
                aria-label="请输入搜索内容"
              />
            </div>
            <Space className="account-header-actions" align="center">
              <Badge
                className="account-header-badge"
                count={0}
                dot={false}
                showZero={false}
                shape="circle"
              >
                <Button
                  className="account-header-icon"
                  shape="square"
                  size="large"
                  variant="text"
                  icon={<MailIcon />}
                  aria-label="消息"
                />
              </Badge>
              <Dropdown
                trigger="click"
                onClick={({ value }) => {
                  if (value === 'profile') navigate('/account')
                  if (value === 'security') navigate('/account/security')
                  if (value === 'logout') setLogoutDialogOpen(true)
                }}
              >
                <Button
                  className="account-user-menu"
                  variant="text"
                  aria-label={`${displayName} 用户菜单`}
                >
                  <Avatar
                    className="account-user-avatar"
                    image={currentUser?.avatarUrl || undefined}
                    size="32px"
                  >
                    {displayName.slice(0, 1)}
                  </Avatar>
                  <span className="account-user-name">{displayName}</span>
                </Button>
                <Dropdown.DropdownMenu>
                  <Dropdown.DropdownItem value="profile">
                    <span className="account-dropdown-item">
                      <UserCircleIcon />
                      个人资料
                    </span>
                  </Dropdown.DropdownItem>
                  <Dropdown.DropdownItem value="security">
                    <span className="account-dropdown-item">
                      <SettingIcon />
                      安全设置
                    </span>
                  </Dropdown.DropdownItem>
                  <Dropdown.DropdownItem value="logout">
                    <span className="account-dropdown-item">
                      <LogoutIcon />
                      退出登录
                    </span>
                  </Dropdown.DropdownItem>
                </Dropdown.DropdownMenu>
              </Dropdown>
            </Space>
          </Layout.Header>
          <Layout.Content className="account-content">
            <div className="account-main">
              <Outlet />
            </div>
          </Layout.Content>
        </Layout>
      </Layout>
      {mobileMenuOpen && (
        <button
          className="account-mobile-backdrop"
          aria-label="关闭菜单"
          type="button"
          onClick={() => setMobileMenuOpen(false)}
        />
      )}
      <AccountLogoutDialog
        open={logoutDialogOpen}
        loading={loggingOut}
        onCancel={() => setLogoutDialogOpen(false)}
        onConfirm={() => void handleLogout()}
      />
    </div>
  )
}
