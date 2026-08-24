import { useEffect, useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { Alert, Button, Form, Steps } from 'tdesign-react'
import {
  resetPassword,
  sendForgotPasswordCode,
  verifyForgotPasswordCode,
} from '../../features/auth/api/authApi'
import {
  AuthInputField,
  VerificationCodeField,
} from '../../features/auth/components/AuthInputField'
import { ApiError } from '../../shared/api/ApiError'
import { AuthShell } from '../../features/auth/components/AuthShell'
import {
  isPhoneOrEmail,
  isVerificationCode,
} from '../../features/auth/model/authValidation'

type RecoveryStep = 'request' | 'verify' | 'reset'

type ResetPasswordFields = {
  newPassword: string
  confirmPassword: string
}

export function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [identityForm] = Form.useForm()
  const [resetForm] = Form.useForm()
  const [step, setStep] = useState<RecoveryStep>('request')
  const [target, setTarget] = useState('')
  const [code, setCode] = useState('')
  const [resetToken, setResetToken] = useState<string | null>(null)
  const [countdown, setCountdown] = useState(0)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (countdown <= 0) return undefined
    const timer = window.setInterval(() => {
      setCountdown((current) => Math.max(0, current - 1))
    }, 1_000)
    return () => window.clearInterval(timer)
  }, [countdown])

  async function handleSendCode() {
    setError('')
    setNotice('')
    const validationResult = await identityForm.validate({
      fields: ['target'],
    })
    if (validationResult !== true) return

    setSubmitting(true)
    try {
      await sendForgotPasswordCode(target.trim())
      setCountdown(60)
      setStep('verify')
      setNotice('验证码已发送')
    } catch (submissionError) {
      setError(
        submissionError instanceof ApiError
          ? submissionError.message
          : '验证码发送失败，请稍后重试',
      )
    } finally {
      setSubmitting(false)
    }
  }

  async function handleVerify() {
    setError('')
    const validationResult = await identityForm.validate({
      fields: ['code'],
    })
    if (validationResult !== true) return

    setSubmitting(true)
    try {
      const response = await verifyForgotPasswordCode(target.trim(), code)
      if (!response?.resetToken) {
        throw new Error('重置凭证缺失')
      }
      setResetToken(response.resetToken)
      setStep('reset')
      setNotice('验证成功，请设置新密码')
    } catch (submissionError) {
      setError(
        submissionError instanceof ApiError
          ? submissionError.message
          : '验证码验证失败，请重试',
      )
    } finally {
      setSubmitting(false)
    }
  }

  async function handleReset(fields: ResetPasswordFields) {
    setError('')
    if (!resetToken) {
      setError('重置凭证已失效，请重新开始')
      setStep('request')
      return
    }

    setSubmitting(true)
    try {
      await resetPassword(
        resetToken,
        fields.newPassword,
        fields.confirmPassword,
      )
      navigate('/login', {
        replace: true,
        state: { notice: '密码重置成功，请登录' },
      })
    } catch (submissionError) {
      setError(
        submissionError instanceof ApiError
          ? submissionError.message
          : '密码重置失败，请稍后重试',
      )
    } finally {
      setSubmitting(false)
    }
  }

  const activeIndex = step === 'request' ? 0 : step === 'verify' ? 1 : 2

  return (
    <AuthShell
      title="找回密码"
      description="验证账户后设置一个新的登录密码"
      footer={
        <span>
          想起密码了？ <RouterLink to="/login">返回登录</RouterLink>
        </span>
      }
    >
      <Steps current={activeIndex} className="recovery-steps">
        <Steps.StepItem title="发送验证码" />
        <Steps.StepItem title="验证身份" />
        <Steps.StepItem title="设置密码" />
      </Steps>
      {notice ? <p className="form-notice">{notice}</p> : null}
      {error ? (
        <Alert className="auth-alert" theme="error" message={error} />
      ) : null}
      {step === 'request' || step === 'verify' ? (
        <Form
          key="identity"
          form={identityForm}
          className="auth-form auth-validation-form"
          initialData={{ target: '', code: '' }}
          labelAlign="top"
          requiredMark={false}
          onValuesChange={() => setError('')}
        >
          <Form.FormItem
            name="target"
            rules={[
              {
                required: true,
                message: '请输入手机号或邮箱',
                trigger: 'blur',
              },
              {
                validator: (value) => isPhoneOrEmail(String(value ?? '')),
                message: '请输入正确的手机号或邮箱',
                trigger: 'blur',
              },
            ]}
          >
            <AuthInputField
              label="手机号或邮箱"
              value={target}
              onChange={setTarget}
              placeholder="请输入手机号或邮箱"
              disabled={step === 'verify'}
              autocomplete="email"
            />
          </Form.FormItem>
          {step === 'verify' ? (
            <>
              <Form.FormItem
                name="code"
                valueFormat={(value) =>
                  String(value ?? '')
                    .replace(/\D/g, '')
                    .slice(0, 6)
                }
                rules={[
                  {
                    required: true,
                    message: '请输入验证码',
                    trigger: 'blur',
                  },
                  {
                    validator: (value) =>
                      isVerificationCode(String(value ?? '')),
                    message: '请输入 6 位数字验证码',
                    trigger: 'blur',
                  },
                ]}
              >
                <VerificationCodeField
                  label="验证码"
                  value={code}
                  onChange={(value) =>
                    setCode(value.replace(/\D/g, '').slice(0, 6))
                  }
                  placeholder="6 位数字验证码"
                  maxlength={6}
                  autocomplete="one-time-code"
                  action={
                    <Button
                      type="button"
                      variant="outline"
                      disabled={countdown > 0}
                      onClick={() => void handleSendCode()}
                    >
                      {countdown > 0 ? `${countdown} 秒后重试` : '重新发送'}
                    </Button>
                  }
                />
              </Form.FormItem>
              <Button
                type="button"
                theme="primary"
                size="large"
                block
                loading={submitting}
                onClick={() => void handleVerify()}
              >
                验证并继续
              </Button>
            </>
          ) : (
            <Button
              type="button"
              theme="primary"
              size="large"
              block
              loading={submitting}
              onClick={() => void handleSendCode()}
            >
              获取验证码
            </Button>
          )}
        </Form>
      ) : (
        <Form
          key="reset"
          form={resetForm}
          className="auth-form auth-validation-form"
          initialData={{ newPassword: '', confirmPassword: '' }}
          labelAlign="top"
          requiredMark={false}
          scrollToFirstError="smooth"
          onValuesChange={() => setError('')}
          onSubmit={({ validateResult, fields }) => {
            if (validateResult === true) {
              void handleReset(fields as ResetPasswordFields)
            }
          }}
        >
          <Form.FormItem
            name="newPassword"
            rules={[
              {
                required: true,
                message: '请输入新密码',
                trigger: 'blur',
              },
              {
                validator: (value) => String(value ?? '').length >= 8,
                message: '密码长度不能少于 8 位',
                trigger: 'blur',
              },
            ]}
          >
            <AuthInputField
              label="新密码"
              type="password"
              placeholder="至少 8 位字符"
              autocomplete="new-password"
            />
          </Form.FormItem>
          <Form.FormItem
            name="confirmPassword"
            rules={[
              {
                required: true,
                message: '请再次输入新密码',
                trigger: 'blur',
              },
              {
                validator: (value) =>
                  value === resetForm.getFieldValue('newPassword'),
                message: '两次输入的密码不一致',
                trigger: 'blur',
              },
            ]}
          >
            <AuthInputField
              label="确认密码"
              type="password"
              placeholder="再次输入密码"
              autocomplete="new-password"
            />
          </Form.FormItem>
          <Button
            type="submit"
            theme="primary"
            size="large"
            block
            loading={submitting}
          >
            重置密码
          </Button>
        </Form>
      )}
    </AuthShell>
  )
}
