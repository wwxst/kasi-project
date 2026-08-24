import { Avatar, Card, Descriptions, Tag } from 'antd'
import { useAuthStore } from '../../features/auth/authStore'
import { resolveApiAssetUrl } from '../../api/assets'
import './profile-page.css'

export function ProfilePage() {
  const admin = useAuthStore((state) => state.admin)
  const roleName = admin?.isSuperAdmin === 1 ? '超级管理员' : '普通管理员'

  return (
    <div className="profile-page">
      <div className="profile-page__heading">
        <h1>个人主页</h1>
        <p>查看当前登录管理员的账户资料。</p>
      </div>

      <Card className="profile-card">
        <div className="profile-card__identity">
          <Avatar size={72} src={resolveApiAssetUrl(admin?.avatarUrl)}>
            {admin?.realName?.slice(0, 1)}
          </Avatar>
          <div>
            <strong>{admin?.realName ?? '-'}</strong>
            <span>{admin?.username ?? '-'}</span>
            <Tag color={admin?.isSuperAdmin === 1 ? 'blue' : 'default'}>
              {roleName}
            </Tag>
          </div>
        </div>

        <Descriptions
          className="profile-card__details"
          column={{ xs: 1, sm: 2 }}
          colon={false}
        >
          <Descriptions.Item label="管理员 ID">
            {admin?.id ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label="登录账号">
            {admin?.username ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label="姓名">
            {admin?.realName ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label="管理员类型">{roleName}</Descriptions.Item>
          <Descriptions.Item label="手机号码">
            {admin?.mobile ?? '未设置'}
          </Descriptions.Item>
          <Descriptions.Item label="电子邮箱">
            {admin?.email ?? '未设置'}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  )
}
