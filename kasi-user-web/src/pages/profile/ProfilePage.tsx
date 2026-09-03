import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, ReactNode } from 'react'
import {
  Avatar,
  Button,
  Form,
  Input,
  Loading,
  MessagePlugin,
} from 'tdesign-react'
import type { FormInstanceFunctions, SubmitContext } from 'tdesign-react'
import { EditIcon, UploadIcon } from 'tdesign-icons-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  changePassword,
  getCurrentUser,
  updateUserProfile,
  uploadUserAvatar,
} from '../../features/auth/authApi'
import { useAuthStore } from '../../features/auth/authStore'
import { isHandledRequestError } from '../../shared/api/httpClient'
import Style from './ProfilePage.module.less'

function formatDateTime(value: string | null) {
  return value ? value.slice(0, 19).replace('T', ' ') : '暂无'
}

function displayValue(value: string | null) {
  return value?.trim() || '未填写'
}

export default function ProfilePage({ title: _title }: { title: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const clearSession = useAuthStore((state) => state.clearSession)
  const formRef = useRef<FormInstanceFunctions | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [editing, setEditing] = useState(false)
  const [profileSubmitting, setProfileSubmitting] = useState(false)
  const [avatarSubmitting, setAvatarSubmitting] = useState(false)
  const [profileDraft, setProfileDraft] = useState({
    nickname: '',
    realName: '',
  })
  const avatarInputRef = useRef<HTMLInputElement | null>(null)
  const userQuery = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getCurrentUser,
  })

  useEffect(() => {
    if (userQuery.isError && !isHandledRequestError(userQuery.error)) {
      void MessagePlugin.error('个人资料加载失败，请稍后重试')
    }
  }, [userQuery.error, userQuery.isError])

  const handlePasswordSubmit = async ({
    fields,
    validateResult,
  }: SubmitContext) => {
    if (validateResult !== true) return
    const request = {
      oldPassword: String(fields?.oldPassword ?? ''),
      newPassword: String(fields?.newPassword ?? ''),
      confirmPassword: String(fields?.confirmPassword ?? ''),
    }
    setSubmitting(true)
    try {
      await changePassword(request)
      clearSession()
      queryClient.clear()
      void MessagePlugin.success('密码修改成功，请重新登录')
      navigate('/login', { replace: true })
    } catch (error) {
      if (!isHandledRequestError(error)) {
        void MessagePlugin.error(
          error instanceof Error ? error.message : '密码修改失败，请稍后重试',
        )
      }
    } finally {
      setSubmitting(false)
    }
  }

  const startEditing = () => {
    if (!userQuery.data) return
    setProfileDraft({
      nickname: userQuery.data.nickname ?? '',
      realName: userQuery.data.realName ?? '',
    })
    setEditing(true)
  }

  const handleProfileSubmit = async () => {
    const nickname = profileDraft.nickname.trim()
    if (!nickname) {
      void MessagePlugin.error('请输入昵称')
      return
    }
    setProfileSubmitting(true)
    try {
      const updatedUser = await updateUserProfile({
        nickname,
        realName: profileDraft.realName.trim() || null,
      })
      queryClient.setQueryData(['auth', 'me'], updatedUser)
      setEditing(false)
      void MessagePlugin.success('个人资料已更新')
    } catch (error) {
      if (!isHandledRequestError(error)) {
        void MessagePlugin.error(
          error instanceof Error
            ? error.message
            : '个人资料更新失败，请稍后重试',
        )
      }
    } finally {
      setProfileSubmitting(false)
    }
  }

  const handleAvatarChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    setAvatarSubmitting(true)
    try {
      const updatedUser = await uploadUserAvatar(file)
      queryClient.setQueryData(['auth', 'me'], updatedUser)
      void MessagePlugin.success('头像已更新')
    } catch (error) {
      if (!isHandledRequestError(error)) {
        void MessagePlugin.error(
          error instanceof Error ? error.message : '头像上传失败，请稍后重试',
        )
      }
    } finally {
      event.target.value = ''
      setAvatarSubmitting(false)
    }
  }

  if (userQuery.isLoading) {
    return (
      <div className={Style.state}>
        <Loading text="正在加载个人资料" />
      </div>
    )
  }

  if (!userQuery.data) {
    return (
      <div className={Style.state}>
        <span>个人资料加载失败</span>
        <Button variant="outline" onClick={() => void userQuery.refetch()}>
          重新加载
        </Button>
      </div>
    )
  }

  const user = userQuery.data
  const nickname = user.nickname?.trim() || '用户'

  return (
    <div className={Style.page}>
      <section className={Style.profileSection}>
        <header className={Style.profileHeader}>
          <div className={Style.avatarEditor}>
            {user.avatarUrl ? (
              <Avatar size="72px" image={user.avatarUrl} alt={nickname} />
            ) : (
              <div className={Style.avatarFallback} aria-label={nickname}>
                {nickname.slice(0, 1)}
              </div>
            )}
            <input
              ref={avatarInputRef}
              className={Style.fileInput}
              type="file"
              aria-label="上传头像"
              accept="image/jpeg,image/png,image/webp"
              onChange={(event) => void handleAvatarChange(event)}
            />
            <Button
              size="small"
              variant="text"
              icon={<UploadIcon />}
              loading={avatarSubmitting}
              onClick={() => avatarInputRef.current?.click()}
            >
              更换头像
            </Button>
          </div>
          <div className={Style.profileIdentity}>
            <h1>个人资料</h1>
            {editing ? (
              <label className={Style.inlineNickname}>
                <span className={Style.visuallyHidden}>昵称</span>
                <Input
                  value={profileDraft.nickname}
                  maxlength={64}
                  onChange={(value) =>
                    setProfileDraft((current) => ({
                      ...current,
                      nickname: value,
                    }))
                  }
                />
              </label>
            ) : (
              <strong>{nickname}</strong>
            )}
            <span>用户编号 {user.userNo}</span>
          </div>
          <div className={Style.profileActions}>
            <span className={Style.status}>
              {user.status === 1 ? '正常' : '已禁用'}
            </span>
            {editing ? (
              <div className={Style.editActions}>
                <Button variant="outline" onClick={() => setEditing(false)}>
                  取消
                </Button>
                <Button
                  theme="primary"
                  loading={profileSubmitting}
                  onClick={() => void handleProfileSubmit()}
                >
                  保存资料
                </Button>
              </div>
            ) : (
              <Button
                variant="outline"
                icon={<EditIcon />}
                onClick={startEditing}
              >
                编辑资料
              </Button>
            )}
          </div>
        </header>

        <dl className={Style.infoGrid}>
          <InfoItem label="用户编号" value={user.userNo} />
          <InfoItem
            label="真实姓名"
            value={
              editing ? (
                <label className={Style.inlineField}>
                  <span className={Style.visuallyHidden}>真实姓名</span>
                  <Input
                    value={profileDraft.realName}
                    maxlength={64}
                    onChange={(value) =>
                      setProfileDraft((current) => ({
                        ...current,
                        realName: value,
                      }))
                    }
                  />
                </label>
              ) : (
                displayValue(user.realName)
              )
            }
          />
          <InfoItem label="手机号码" value={displayValue(user.mobile)} />
          <InfoItem label="邮箱地址" value={displayValue(user.email)} />
          <InfoItem label="注册时间" value={formatDateTime(user.createdAt)} />
          <InfoItem
            label="最近登录时间"
            value={formatDateTime(user.lastLoginAt)}
          />
          <InfoItem
            label="最近登录 IP"
            value={displayValue(user.lastLoginIp)}
          />
        </dl>
      </section>

      <section className={Style.securitySection}>
        <h2>修改密码</h2>
        <Form
          ref={formRef}
          className={Style.passwordForm}
          labelAlign="top"
          onSubmit={handlePasswordSubmit}
        >
          <Form.FormItem
            label="当前密码"
            name="oldPassword"
            rules={[{ required: true, message: '请输入当前密码' }]}
          >
            <Input
              type="password"
              autocomplete="current-password"
              placeholder="请输入当前密码"
            />
          </Form.FormItem>
          <Form.FormItem
            label="新密码"
            name="newPassword"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 8, message: '新密码长度不能少于8位' },
            ]}
          >
            <Input
              type="password"
              autocomplete="new-password"
              placeholder="请输入新密码"
            />
          </Form.FormItem>
          <Form.FormItem
            label="确认新密码"
            name="confirmPassword"
            rules={[
              { required: true, message: '请再次输入新密码' },
              {
                validator: (value) =>
                  value === formRef.current?.getFieldValue('newPassword'),
                message: '两次输入的新密码不一致',
              },
            ]}
          >
            <Input
              type="password"
              autocomplete="new-password"
              placeholder="请再次输入新密码"
            />
          </Form.FormItem>
          <Button
            className={Style.passwordSubmit}
            theme="primary"
            type="submit"
            loading={submitting}
          >
            修改密码
          </Button>
        </Form>
      </section>
    </div>
  )
}

function InfoItem({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className={Style.infoItem}>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}
