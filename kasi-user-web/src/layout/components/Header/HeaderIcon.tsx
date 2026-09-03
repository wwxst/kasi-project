import { memo } from 'react'
import { Avatar, Badge, Button, Dropdown, Space } from 'tdesign-react'
import { MailIcon, PoweroffIcon, UserCircleIcon } from 'tdesign-icons-react'
import { useNavigate } from 'react-router-dom'
import Style from './HeaderIcon.module.less'
import { getCurrentUser } from '../../../features/auth/authApi'
import { useAuthStore } from '../../../features/auth/authStore'
import { useQuery } from '@tanstack/react-query'

const { DropdownMenu, DropdownItem } = Dropdown

export default memo(() => {
  const navigate = useNavigate()
  const accessToken = useAuthStore((store) => store.accessToken)
  const { data: user } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getCurrentUser,
    enabled: Boolean(accessToken),
  })

  const nickname = user?.nickname?.trim() || '用户'
  return (
    <Space align="center">
      <Badge className={Style.badge} count={0} dot={false} showZero={false}>
        <Button
          className={Style.menuIcon}
          shape="square"
          size="large"
          variant="text"
          icon={<MailIcon />}
        />
      </Badge>
      <Dropdown trigger="click">
        <Button variant="text" className={Style.dropdown}>
          {user?.avatarUrl ? (
            <Avatar size="32px" image={user.avatarUrl} alt={nickname} />
          ) : (
            <span
              className={Style.avatarFallback}
              data-testid="header-avatar-fallback"
              aria-label={nickname}
            >
              {nickname.slice(0, 1)}
            </span>
          )}
          <span className={Style.text}>{nickname}</span>
        </Button>
        <DropdownMenu>
          <DropdownItem
            value="profile"
            onClick={() => navigate('/workspace/profile')}
          >
            <div className={Style.dropItem}>
              <UserCircleIcon />
              <span>个人中心</span>
            </div>
          </DropdownItem>
          <DropdownItem value="logout" onClick={() => navigate('/login')}>
            <div className={Style.dropItem}>
              <PoweroffIcon />
              <span>退出登录</span>
            </div>
          </DropdownItem>
        </DropdownMenu>
      </Dropdown>
    </Space>
  )
})
