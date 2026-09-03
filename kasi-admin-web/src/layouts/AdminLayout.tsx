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
  Clock3,
  ChevronLeft,
  ChevronRight,
  LibraryBig,
  ListOrdered,
  LogOut,
  Maximize2,
  Menu as MenuIcon,
  PanelsTopLeft,
  Search,
  Settings,
  ShieldCheck,
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
  const [fullscreen, setFullscreen] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()
  const admin = useAuthStore((state) => state.admin)
  const clearSession = useAuthStore((state) => state.clearSession)
  const showBreadcrumb = location.pathname === '/profile'
  const selectedMenuKey = location.pathname

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
        selectedKeys={[selectedMenuKey]}
        defaultOpenKeys={
          location.pathname.startsWith('/drama/sync/')
            ? ['drama-management']
            : []
        }
        items={[
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
              {
                key: '/drama/sync/catalog',
                icon: <ListOrdered size={18} strokeWidth={1.8} />,
                label: <Link to="/drama/sync/catalog">短剧同步</Link>,
              },
              {
                key: '/drama/sync/content',
                icon: <ListOrdered size={18} strokeWidth={1.8} />,
                label: <Link to="/drama/sync/content">剧集同步</Link>,
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
              {
                key: '/promotion/orders',
                icon: <ListOrdered size={18} strokeWidth={1.8} />,
                label: <Link to="/promotion/orders">推广订单</Link>,
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
              {
                key: '/system-config/scheduled-tasks',
                icon: <Clock3 size={18} strokeWidth={1.8} />,
                label: (
                  <Link to="/system-config/scheduled-tasks">定时任务</Link>
                ),
              },
              {
                key: '/system-config/commission-rules',
                icon: <Clapperboard size={18} strokeWidth={1.8} />,
                label: (
                  <Link to="/system-config/commission-rules">分佣规则</Link>
                ),
              },
              ...(admin?.isSuperAdmin === 1
                ? [
                    {
                      key: '/system-config/sms',
                      icon: <Settings size={18} strokeWidth={1.8} />,
                      label: <Link to="/system-config/sms">短信配置</Link>,
                    },
                  ]
                : []),
            ],
          },
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
    <Layout className="admin-layout">
      <Header className="admin-header">
        <Link
          className="admin-header__brand"
          to="/user-management"
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
              onPressEnter={() => navigate('/user-management')}
            />

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
