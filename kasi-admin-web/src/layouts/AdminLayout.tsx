import { useEffect, useState } from 'react'
import {
  Avatar,
  Breadcrumb,
  Button,
  Drawer,
  Dropdown,
  Input,
  Layout,
  Menu,
  Popover,
  Tooltip,
} from 'antd'
import {
  BadgeCheck,
  Bell,
  Clapperboard,
  ChevronLeft,
  ChevronRight,
  Gauge,
  Languages,
  LibraryBig,
  LogOut,
  Maximize2,
  Menu as MenuIcon,
  Moon,
  PanelsTopLeft,
  Search,
  Settings,
  ShieldCheck,
  Sun,
  Users,
  UserRound,
} from 'lucide-react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../features/auth/authStore'
import { resolveApiAssetUrl } from '../api/assets'
import './admin-layout.css'

const { Header, Sider, Content, Footer } = Layout

export function AdminLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [darkMode, setDarkMode] = useState(false)
  const [fullscreen, setFullscreen] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()
  const admin = useAuthStore((state) => state.admin)
  const clearSession = useAuthStore((state) => state.clearSession)
  const showBreadcrumb = location.pathname === '/profile'

  useEffect(() => {
    document.documentElement.dataset.theme = darkMode ? 'dark' : 'light'
  }, [darkMode])

  useEffect(() => {
    const handleFullscreenChange = () => {
      setFullscreen(Boolean(document.fullscreenElement))
    }

    document.addEventListener('fullscreenchange', handleFullscreenChange)
    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange)
    }
  }, [])

  const navigation = (
    <nav className="admin-navigation" aria-label="主导航">
      <Menu
        mode="inline"
        selectedKeys={[location.pathname]}
        items={[
          {
            type: 'group',
            label: '工作台',
            children: [
              {
                key: '/dashboard',
                icon: <Gauge size={18} strokeWidth={1.8} />,
                label: <Link to="/dashboard">分析页</Link>,
              },
              ...(admin?.isSuperAdmin === 1
                ? [
                    {
                      key: '/admin-management',
                      icon: <ShieldCheck size={18} strokeWidth={1.8} />,
                      label: <Link to="/admin-management">管理员管理</Link>,
                    },
                  ]
                : []),
              {
                key: '/user-management',
                icon: <Users size={18} strokeWidth={1.8} />,
                label: <Link to="/user-management">用户管理</Link>,
              },
            ],
          },
          {
            key: 'drama-management',
            icon: <LibraryBig size={18} strokeWidth={1.8} />,
            label: '短剧管理',
            children: [
              {
                key: '/drama/catalog',
                icon: <Clapperboard size={18} strokeWidth={1.8} />,
                label: <Link to="/drama/catalog">短剧目录</Link>,
              },
            ],
          },
          {
            key: 'promotion-management',
            icon: <BadgeCheck size={18} strokeWidth={1.8} />,
            label: '推广管理',
            children: [
              {
                key: '/promotion/media-accounts',
                icon: <BadgeCheck size={18} strokeWidth={1.8} />,
                label: <Link to="/promotion/media-accounts">媒体账号报备</Link>,
              },
            ],
          },
          {
            key: 'system-config',
            icon: <Settings size={18} strokeWidth={1.8} />,
            label: '系统配置',
            children: [
              {
                key: '/system-config/drama-api',
                icon: <Clapperboard size={18} strokeWidth={1.8} />,
                label: <Link to="/system-config/drama-api">短剧 API 配置</Link>,
              },
            ],
          },
        ]}
        defaultOpenKeys={[
          'drama-management',
          'promotion-management',
          'system-config',
        ]}
        onClick={() => setMobileMenuOpen(false)}
      />
    </nav>
  )

  const handleLogout = () => {
    clearSession()
    navigate('/login', { replace: true })
  }

  const handleFullscreen = async () => {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
      return
    }

    await document.documentElement.requestFullscreen()
  }

  return (
    <Layout
      className={darkMode ? 'admin-layout admin-layout--dark' : 'admin-layout'}
    >
      <Header className="admin-header">
        <Link
          className="admin-header__brand"
          to="/dashboard"
          aria-label="Kasi 管理后台"
        >
          <span className="admin-header__mark" aria-hidden="true">
            <PanelsTopLeft size={19} strokeWidth={2} />
          </span>
          <strong>Kasi 管理后台</strong>
        </Link>

        <div className="admin-header__workspace">
          <Button
            className="admin-header__mobile-menu"
            type="text"
            aria-label="打开主导航"
            icon={<MenuIcon size={20} />}
            onClick={() => setMobileMenuOpen(true)}
          />

          <div className="admin-header__actions">
            <Input
              className="admin-header__search"
              type="search"
              aria-label="搜索导航"
              placeholder="输入内容查询"
              allowClear
              suffix={<Search size={15} />}
              onPressEnter={() => navigate('/dashboard')}
            />

            <Popover content="当前语言：简体中文" placement="bottomRight">
              <Tooltip title="语言">
                <Button
                  className="admin-header__tool admin-header__language"
                  type="text"
                  aria-label="语言"
                  icon={<Languages size={16} />}
                />
              </Tooltip>
            </Popover>

            <Popover
              placement="bottomRight"
              trigger="click"
              content={
                <span className="admin-header__notification-empty">
                  暂无新通知
                </span>
              }
            >
              <Tooltip title="通知">
                <Button
                  className="admin-header__tool"
                  type="text"
                  aria-label="通知"
                  icon={<Bell size={18} />}
                />
              </Tooltip>
            </Popover>

            <Tooltip title={darkMode ? '切换浅色模式' : '切换深色模式'}>
              <Button
                className="admin-header__tool admin-header__theme"
                type="text"
                aria-label={darkMode ? '切换浅色模式' : '切换深色模式'}
                icon={darkMode ? <Sun size={16} /> : <Moon size={16} />}
                onClick={() => setDarkMode((value) => !value)}
              />
            </Tooltip>

            <Dropdown
              trigger={['click']}
              placement="bottomRight"
              menu={{
                items: [
                  {
                    key: 'toggle-sider',
                    icon: collapsed ? (
                      <ChevronRight size={16} />
                    ) : (
                      <ChevronLeft size={16} />
                    ),
                    label: collapsed ? '展开侧栏' : '收起侧栏',
                    onClick: () => setCollapsed((value) => !value),
                  },
                ],
              }}
            >
              <Tooltip title="布局设置">
                <Button
                  className="admin-header__tool admin-header__settings"
                  type="text"
                  aria-label="布局设置"
                  icon={<Settings size={16} />}
                />
              </Tooltip>
            </Dropdown>

            <Tooltip title={fullscreen ? '退出全屏' : '进入全屏'}>
              <Button
                className="admin-header__tool admin-header__fullscreen"
                type="text"
                aria-label={fullscreen ? '退出全屏' : '进入全屏'}
                icon={<Maximize2 size={16} />}
                onClick={() => void handleFullscreen()}
              />
            </Tooltip>

            <Dropdown
              trigger={['click']}
              placement="bottomRight"
              menu={{
                items: [
                  {
                    key: 'profile',
                    icon: <UserRound size={16} />,
                    label: '个人主页',
                    onClick: () => navigate('/profile'),
                  },
                  {
                    type: 'divider',
                  },
                  {
                    key: 'logout',
                    icon: <LogOut size={16} />,
                    label: '退出登录',
                    onClick: handleLogout,
                  },
                ],
              }}
            >
              <Button
                className="admin-header__account"
                type="text"
                aria-label="账户菜单"
              >
                <Avatar size={34} src={resolveApiAssetUrl(admin?.avatarUrl)}>
                  {admin?.realName?.slice(0, 1)}
                </Avatar>
                <span className="admin-header__account-name">
                  {admin?.realName}
                </span>
              </Button>
            </Dropdown>
          </div>
        </div>
      </Header>

      <Layout className="admin-layout__body">
        <Sider
          className="admin-sider"
          width={224}
          collapsedWidth={72}
          collapsed={collapsed}
          trigger={null}
          theme="light"
        >
          {navigation}
          <div className="admin-sider__footer">
            <div className="admin-sider__health">
              <span className="admin-sider__status" aria-hidden="true" />
              {!collapsed && <span>系统运行正常</span>}
            </div>
            <Tooltip title={collapsed ? '展开侧边栏' : '收起侧边栏'}>
              <Button
                className="admin-sider__collapse"
                type="text"
                aria-label={collapsed ? '展开侧边栏' : '收起侧边栏'}
                aria-expanded={!collapsed}
                icon={
                  collapsed ? (
                    <ChevronRight size={18} />
                  ) : (
                    <ChevronLeft size={18} />
                  )
                }
                onClick={() => setCollapsed((value) => !value)}
              />
            </Tooltip>
          </div>
        </Sider>

        <Drawer
          rootClassName="admin-mobile-drawer"
          open={mobileMenuOpen}
          placement="left"
          size={264}
          title="Kasi 管理后台"
          onClose={() => setMobileMenuOpen(false)}
        >
          {navigation}
        </Drawer>

        <Layout className="admin-main-layout">
          {showBreadcrumb && (
            <div className="admin-breadcrumb">
              <Breadcrumb items={[{ title: '首页' }, { title: '个人主页' }]} />
            </div>
          )}
          <Content className="admin-content">
            <Outlet />
          </Content>
          <Footer className="admin-footer">Kasi Promotion Platform</Footer>
        </Layout>
      </Layout>
    </Layout>
  )
}
