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
  sendChangePasswordCode,
  updateUserProfile,
  uploadUserAvatar,
  verifyChangePasswordCode,
} from '../../features/auth/authApi'
import { useAuthStore } from '../../features/auth/authStore'
import { isHandledRequestError } from '../../shared/api/httpClient'
import Style from './ProfilePage.module.less'

function displayValue(value: string | null) {
  return value?.trim() || '未填写'
}

function studentTypeLabel(value: number) {
  return value === 1 ? '基础学员' : '基础用户'
}

function maskMobile(mobile: string) {
  const normalized = mobile.trim()
  if (normalized.length <= 7) return normalized
  return `${normalized.slice(0, 3)}****${normalized.slice(-4)}`
}

export default function ProfilePage({ title: _title }: { title: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const clearSession = useAuthStore((state) => state.clearSession)
  const formRef = useRef<FormInstanceFunctions | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [passwordStep, setPasswordStep] = useState<
    'VERIFY_MOBILE' | 'SET_PASSWORD'
  >('VERIFY_MOBILE')
  const [verificationCode, setVerificationCode] = useState('')
  const [resetToken, setResetToken] = useState<string | null>(null)
  const [sendingCode, setSendingCode] = useState(false)
  const [verifyingCode, setVerifyingCode] = useState(false)
  const [codeCountdown, setCodeCountdown] = useState(0)
  const [editing, setEditing] = useState(false)
  const [activeTab, setActiveTab] = useState<'basic' | 'security'>('basic')
  const [profileSubmitting, setProfileSubmitting] = useState(false)
  const [nicknameEditing, setNicknameEditing] = useState(false)
  const [nicknameSubmitting, setNicknameSubmitting] = useState(false)
  const [nicknameDraft, setNicknameDraft] = useState('')
  const [avatarSubmitting, setAvatarSubmitting] = useState(false)
  const [profileDraft, setProfileDraft] = useState({
    realName: '',
    wechatId: '',
    mobile: '',
    email: '',
  })
  const avatarInputRef = useRef<HTMLInputElement | null>(null)
  const userQuery = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getCurrentUser,
  })

  useEffect(() => {
    if (codeCountdown <= 0) return
    const timer = window.setInterval(() => {
      setCodeCountdown((current) => Math.max(0, current - 1))
    }, 1000)
    return () => window.clearInterval(timer)
  }, [codeCountdown])

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
    if (!resetToken) return
    const request = {
      resetToken,
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

  const handleSendChangePasswordCode = async () => {
    if (!user.mobile || sendingCode || codeCountdown > 0) return
    setSendingCode(true)
    try {
      await sendChangePasswordCode()
      setCodeCountdown(60)
      void MessagePlugin.success('验证码已发送')
    } catch (error) {
      if (!isHandledRequestError(error)) {
        void MessagePlugin.error(
          error instanceof Error ? error.message : '验证码发送失败，请稍后重试',
        )
      }
    } finally {
      setSendingCode(false)
    }
  }

  const handleVerifyChangePasswordCode = async () => {
    const code = verificationCode.trim()
    if (!/^\d{6}$/.test(code)) {
      void MessagePlugin.error('请输入6位数字验证码')
      return
    }
    setVerifyingCode(true)
    try {
      const result = await verifyChangePasswordCode(code)
      setResetToken(result.resetToken)
      setPasswordStep('SET_PASSWORD')
    } catch (error) {
      if (!isHandledRequestError(error)) {
        void MessagePlugin.error(
          error instanceof Error ? error.message : '验证码验证失败，请重试',
        )
      }
    } finally {
      setVerifyingCode(false)
    }
  }

  const startEditing = () => {
    if (!userQuery.data) return
    setProfileDraft({
      realName: userQuery.data.realName ?? '',
      wechatId: userQuery.data.wechatId ?? '',
      mobile: userQuery.data.mobile ?? '',
      email: userQuery.data.email ?? '',
    })
    setEditing(true)
  }

  const startNicknameEditing = () => {
    setNicknameDraft(userQuery.data?.nickname?.trim() ?? '')
    setNicknameEditing(true)
  }

  const saveNickname = async () => {
    if (nicknameSubmitting || !userQuery.data) return
    const nickname = nicknameDraft.trim()
    if (!nickname) {
      void MessagePlugin.error('请输入昵称')
      return
    }
    if (nickname === (userQuery.data.nickname?.trim() ?? '')) {
      setNicknameEditing(false)
      return
    }
    setNicknameSubmitting(true)
    try {
      const updatedUser = await updateUserProfile({
        nickname,
        realName: userQuery.data.realName,
        wechatId: userQuery.data.wechatId,
        mobile: userQuery.data.mobile,
        email: userQuery.data.email,
      })
      queryClient.setQueryData(['auth', 'me'], updatedUser)
      setNicknameEditing(false)
      void MessagePlugin.success('昵称已更新')
    } catch (error) {
      if (!isHandledRequestError(error)) {
        void MessagePlugin.error(
          error instanceof Error ? error.message : '昵称更新失败，请稍后重试',
        )
      }
    } finally {
      setNicknameSubmitting(false)
    }
  }

  const handleProfileSubmit = async () => {
    const currentNickname = userQuery.data?.nickname?.trim() ?? ''
    if (!currentNickname) {
      void MessagePlugin.error('请输入昵称')
      return
    }
    setProfileSubmitting(true)
    try {
      const updatedUser = await updateUserProfile({
        nickname: currentNickname,
        realName: profileDraft.realName.trim() || null,
        wechatId: profileDraft.wechatId.trim() || null,
        mobile: profileDraft.mobile.trim() || null,
        email: profileDraft.email.trim() || null,
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
            <h1 className={Style.visuallyHidden}>个人资料</h1>
            <div className={Style.summaryGrid}>
              <div className={Style.userSummary} data-testid="user-summary">
                <span>
                  用户昵称：
                  {nicknameEditing ? (
                    <label className={Style.nicknameField}>
                      <span className={Style.visuallyHidden}>用户昵称</span>
                      <Input
                        value={nicknameDraft}
                        maxlength={64}
                        onChange={setNicknameDraft}
                        onEnter={() => void saveNickname()}
                        onBlur={() => void saveNickname()}
                      />
                    </label>
                  ) : (
                    <strong>{nickname}</strong>
                  )}
                  <Button
                    className={Style.nicknameEditButton}
                    variant="text"
                    shape="square"
                    icon={<EditIcon />}
                    loading={nicknameSubmitting}
                    aria-label={nicknameEditing ? '保存昵称' : '编辑昵称'}
                    onClick={() =>
                      nicknameEditing
                        ? void saveNickname()
                        : startNicknameEditing()
                    }
                  />
                </span>
                <span>
                  学员类型：
                  <strong>{studentTypeLabel(user.studentType)}</strong>
                </span>
              </div>
              <span>
                账号ID：<strong>{user.userNo}</strong>
              </span>
            </div>
          </div>
          <div className={Style.profileActions}>
            <span className={Style.status}>
              {user.status === 1 ? '正常' : '已禁用'}
            </span>
          </div>
        </header>
      </section>

      <section className={Style.basicPanel}>
        <div className={Style.basicPanelHeader}>
          <nav className={Style.profileTabs} aria-label="个人中心分类">
            <button
              className={
                activeTab === 'basic'
                  ? Style.profileTabActive
                  : Style.profileTab
              }
              type="button"
              aria-pressed={activeTab === 'basic'}
              onClick={() => setActiveTab('basic')}
            >
              基本信息
            </button>
            <button
              className={
                activeTab === 'security'
                  ? Style.profileTabActive
                  : Style.profileTab
              }
              type="button"
              aria-pressed={activeTab === 'security'}
              onClick={() => setActiveTab('security')}
            >
              安全设置
            </button>
          </nav>
          {activeTab === 'basic' && (
            <div className={Style.basicActions}>
              {editing ? (
                <>
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
                </>
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
          )}
        </div>

        {activeTab === 'basic' ? (
          <dl className={Style.infoGrid}>
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
            <InfoItem
              label="微信号"
              value={
                editing ? (
                  <label className={Style.inlineField}>
                    <span className={Style.visuallyHidden}>微信号</span>
                    <Input
                      value={profileDraft.wechatId}
                      maxlength={128}
                      onChange={(value) =>
                        setProfileDraft((current) => ({
                          ...current,
                          wechatId: value,
                        }))
                      }
                    />
                  </label>
                ) : (
                  displayValue(user.wechatId)
                )
              }
            />
            <InfoItem
              label="手机号码"
              value={
                editing ? (
                  <label className={Style.inlineField}>
                    <span className={Style.visuallyHidden}>手机号码</span>
                    <Input
                      value={profileDraft.mobile}
                      maxlength={32}
                      onChange={(value) =>
                        setProfileDraft((current) => ({
                          ...current,
                          mobile: value,
                        }))
                      }
                    />
                  </label>
                ) : (
                  displayValue(user.mobile)
                )
              }
            />
            <InfoItem
              label="电子邮箱"
              value={
                editing ? (
                  <label className={Style.inlineField}>
                    <span className={Style.visuallyHidden}>电子邮箱</span>
                    <Input
                      value={profileDraft.email}
                      maxlength={128}
                      onChange={(value) =>
                        setProfileDraft((current) => ({
                          ...current,
                          email: value,
                        }))
                      }
                    />
                  </label>
                ) : (
                  displayValue(user.email)
                )
              }
            />
          </dl>
        ) : (
          <section
            className={Style.securitySection}
            data-testid="security-panel"
          >
            <h2>修改密码</h2>
            {passwordStep === 'VERIFY_MOBILE' ? (
              user.mobile ? (
                <div className={Style.passwordForm}>
                  <div className={Style.mobileVerificationHint}>
                    <span>验证手机号</span>
                    <strong>{maskMobile(user.mobile)}</strong>
                  </div>
                  <div className={Style.codeRow}>
                    <label className={Style.codeField}>
                      <span>短信验证码</span>
                      <Input
                        value={verificationCode}
                        maxlength={6}
                        placeholder="请输入6位验证码"
                        onChange={setVerificationCode}
                      />
                    </label>
                    <Button
                      variant="outline"
                      loading={sendingCode}
                      disabled={sendingCode || codeCountdown > 0}
                      onClick={() => void handleSendChangePasswordCode()}
                    >
                      {codeCountdown > 0
                        ? `${codeCountdown}秒后重发`
                        : '发送验证码'}
                    </Button>
                  </div>
                  <Button
                    className={Style.passwordSubmit}
                    theme="primary"
                    loading={verifyingCode}
                    disabled={verifyingCode || verificationCode.length !== 6}
                    onClick={() => void handleVerifyChangePasswordCode()}
                  >
                    验证并继续
                  </Button>
                </div>
              ) : (
                <p className={Style.mobileRequired}>请先在基本信息绑定手机号</p>
              )
            ) : (
              <Form
                ref={formRef}
                className={Style.passwordForm}
                labelAlign="top"
                onSubmit={handlePasswordSubmit}
              >
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
            )}
          </section>
        )}
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
