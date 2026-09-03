import { Button, Form, Input, Switch, Tabs, message } from 'antd'
import { PageContainer } from '@ant-design/pro-components'
import { Save } from 'lucide-react'
import { useEffect, useState } from 'react'
import {
  getSmsConfig,
  updateSmsConfig,
} from '../../features/sms-config/smsConfigApi'
import type { UpdateSmsConfigRequest } from '../../features/sms-config/smsConfigTypes'
import './sms-config-page.css'

export function SmsConfigPage() {
  const [form] = Form.useForm<UpdateSmsConfigRequest>()
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    setLoading(true)
    getSmsConfig()
      .then((config) =>
        form.setFieldsValue({
          signName: config.signName ?? undefined,
          registerTemplateCode: config.registerTemplateCode ?? undefined,
          loginTemplateCode: config.loginTemplateCode ?? undefined,
          resetPasswordTemplateCode:
            config.resetPasswordTemplateCode ?? undefined,
          enabled: config.enabled,
          smtpHost: config.smtpHost ?? undefined,
          smtpPort: config.smtpPort ?? undefined,
          smtpUsername: config.smtpUsername ?? undefined,
          smtpFromAddress: config.smtpFromAddress ?? undefined,
          emailEnabled: config.emailEnabled,
        }),
      )
      .finally(() => setLoading(false))
  }, [form])

  const submit = async (values: UpdateSmsConfigRequest) => {
    setSaving(true)
    try {
      const body: UpdateSmsConfigRequest = { ...values }
      if (!body.accessKeyId?.trim()) delete body.accessKeyId
      if (!body.accessKeySecret?.trim()) delete body.accessKeySecret
      if (!body.smtpPassword?.trim()) delete body.smtpPassword
      await updateSmsConfig(body)
      form.resetFields(['accessKeyId', 'accessKeySecret', 'smtpPassword'])
      message.success('短信和邮箱配置已保存')
    } finally {
      setSaving(false)
    }
  }

  return (
    <PageContainer title="短信与邮箱配置" className="sms-config-page">
      <Form form={form} layout="vertical" onFinish={submit} disabled={loading}>
        <Tabs
          className="sms-config-tabs"
          items={[
            {
              key: 'sms',
              label: '短信配置',
              children: (
                <div className="sms-config-panel">
                  <Form.Item name="accessKeyId" label="AccessKey ID">
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                  <Form.Item name="accessKeySecret" label="AccessKey Secret">
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                  <Form.Item
                    name="signName"
                    label="短信签名"
                    rules={[{ required: true }]}
                  >
                    <Input />
                  </Form.Item>
                  <Form.Item
                    name="registerTemplateCode"
                    label="注册模板 Code"
                    rules={[{ required: true }]}
                  >
                    <Input />
                  </Form.Item>
                  <Form.Item
                    name="loginTemplateCode"
                    label="登录模板 Code"
                    rules={[{ required: true }]}
                  >
                    <Input />
                  </Form.Item>
                  <Form.Item
                    name="resetPasswordTemplateCode"
                    label="重置密码模板 Code"
                    rules={[{ required: true }]}
                  >
                    <Input />
                  </Form.Item>
                  <Form.Item
                    name="enabled"
                    label="启用短信"
                    valuePropName="checked"
                  >
                    <Switch />
                  </Form.Item>
                </div>
              ),
            },
            {
              key: 'email',
              label: '邮箱配置',
              children: (
                <div className="sms-config-panel">
                  <Form.Item name="smtpHost" label="SMTP 主机">
                    <Input placeholder="例如 smtp.qq.com" />
                  </Form.Item>
                  <Form.Item name="smtpPort" label="SMTP 端口">
                    <Input type="number" placeholder="例如 465 或 587" />
                  </Form.Item>
                  <Form.Item name="smtpUsername" label="SMTP 用户名">
                    <Input autoComplete="username" />
                  </Form.Item>
                  <Form.Item name="smtpPassword" label="SMTP 密码">
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                  <Form.Item name="smtpFromAddress" label="发件邮箱">
                    <Input type="email" />
                  </Form.Item>
                  <Form.Item
                    name="emailEnabled"
                    label="启用邮箱验证码"
                    valuePropName="checked"
                  >
                    <Switch />
                  </Form.Item>
                </div>
              ),
            },
          ]}
        />
        <div className="sms-config-actions">
          <Button
            type="primary"
            htmlType="submit"
            loading={saving}
            icon={<Save size={16} />}
          >
            保存配置
          </Button>
        </div>
      </Form>
    </PageContainer>
  )
}
