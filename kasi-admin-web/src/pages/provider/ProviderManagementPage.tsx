import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App,
  Button,
  Form,
  Input,
  Modal,
  Result,
  Radio,
  Space,
  Spin,
  Switch,
  Tabs,
  Tag,
  Tooltip,
} from 'antd'
import { PageContainer } from '@ant-design/pro-components'
import { CheckCircle2, FlaskConical, Save } from 'lucide-react'
import { useAuthStore } from '../../features/auth/authStore'
import {
  listProviders,
  testProviderConnection,
  upsertProviderConnection,
} from '../../features/provider/providerApi'
import type { FilingMode } from '../../features/promotion/filingModeTypes'
import type {
  DramaProvider,
  ProviderConnectionTestResult,
  UpsertProviderConnectionRequest,
} from '../../features/provider/providerTypes'
import './provider-management-page.css'

interface ProviderFormValues {
  baseUrl: string
  partnerId: string
  apiKey?: string
  status: boolean
}

export function ProviderManagementPage() {
  const [form] = Form.useForm<ProviderFormValues>()
  const { message } = App.useApp()
  const isSuperAdmin = useAuthStore((state) => state.admin?.isSuperAdmin === 1)
  const [providers, setProviders] = useState<DramaProvider[]>([])
  const [activeProviderId, setActiveProviderId] = useState<string>()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] =
    useState<ProviderConnectionTestResult | null>(null)
  const [filingMode, setFilingMode] = useState<FilingMode>('API')

  const activeProvider = useMemo(
    () =>
      providers.find((provider) => String(provider.id) === activeProviderId),
    [activeProviderId, providers],
  )

  const loadProviders = useCallback(async () => {
    setLoading(true)
    try {
      const result = await listProviders()
      setProviders(result)
      setActiveProviderId((current) =>
        current && result.some((provider) => String(provider.id) === current)
          ? current
          : result[0]
            ? String(result[0].id)
            : undefined,
      )
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : '短剧 API 配置加载失败',
      )
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    void loadProviders()
  }, [loadProviders])

  useEffect(() => {
    if (!activeProvider) {
      form.resetFields()
      return
    }
    form.setFieldsValue({
      baseUrl: activeProvider.connection?.baseUrl ?? '',
      partnerId: activeProvider.connection?.partnerId ?? '',
      apiKey: undefined,
      status: activeProvider.connection?.status !== 0,
    })
    setFilingMode(activeProvider.connection?.filingMode ?? 'API')
  }, [activeProvider, form])

  const handleSave = async () => {
    if (!activeProvider) return
    try {
      const values = await form.validateFields()
      const request: UpsertProviderConnectionRequest = {
        status: values.status ? 1 : 0,
        filingMode,
        ...(filingMode === 'API'
          ? {
              baseUrl: values.baseUrl.trim().replace(/\/$/, ''),
              partnerId: values.partnerId.trim(),
              ...(values.apiKey?.trim()
                ? { apiKey: values.apiKey.trim() }
                : {}),
            }
          : {}),
      }
      setSaving(true)
      await upsertProviderConnection(activeProvider.id, request)
      form.setFieldValue('apiKey', undefined)
      message.success(`${activeProvider.providerName} API 配置已保存`)
      await loadProviders()
    } catch (error) {
      if (isValidationError(error)) return
      message.error(
        error instanceof Error ? error.message : '短剧 API 配置保存失败',
      )
    } finally {
      setSaving(false)
    }
  }

  const handleTest = async () => {
    if (!activeProvider) return
    setTesting(true)
    try {
      setTestResult(await testProviderConnection(activeProvider.id))
    } catch (error) {
      message.error(error instanceof Error ? error.message : '连接测试失败')
    } finally {
      setTesting(false)
    }
  }

  const testDisabledReason = activeProvider
    ? getTestDisabledReason(activeProvider)
    : '暂无可测试的平台'

  return (
    <PageContainer
      className="provider-config-page"
      title="短剧 API 配置"
      content="配置短剧平台的接口地址和接入凭据"
      data-testid="provider-management-page"
    >
      <Spin spinning={loading}>
        <section className="provider-config-panel">
          {providers.length > 0 ? (
            <>
              <Tabs
                className="provider-config-tabs"
                activeKey={activeProviderId}
                items={providers.map((provider) => ({
                  key: String(provider.id),
                  label: provider.providerName,
                }))}
                onChange={(key) => setActiveProviderId(key)}
              />

              {activeProvider ? (
                <div className="provider-config-content">
                  <div className="provider-config-heading">
                    <div>
                      <h2>{activeProvider.providerName} API 接入</h2>
                      <span>{activeProvider.providerCode}</span>
                    </div>
                    <Space>
                      <Tag
                        color={
                          activeProvider.status === 1 ? 'success' : 'default'
                        }
                      >
                        平台{activeProvider.status === 1 ? '启用' : '停用'}
                      </Tag>
                      <ConnectionTag provider={activeProvider} />
                    </Space>
                  </div>

                  {!isSuperAdmin ? (
                    <Alert
                      type="info"
                      showIcon
                      message="当前为只读模式，只有超级管理员可以修改短剧 API 配置"
                    />
                  ) : null}

                  <Form
                    form={form}
                    className="provider-config-form"
                    layout="horizontal"
                    labelCol={{ flex: '0 0 120px' }}
                    wrapperCol={{ flex: '0 1 620px' }}
                    disabled={!isSuperAdmin}
                    preserve={false}
                    autoComplete="off"
                  >
                    {filingMode === 'API' ? (
                      <>
                        <Form.Item
                          label="接口 URL"
                          name="baseUrl"
                          extra="填写平台 API 的基础地址，不包含具体接口路径"
                          rules={[
                            { required: true, message: '请输入接口 URL' },
                            {
                              pattern: /^https?:\/\/\S+$/,
                              message:
                                '请输入以 http:// 或 https:// 开头的有效地址',
                            },
                            { max: 512, message: '接口 URL 不能超过512个字符' },
                          ]}
                        >
                          <Input placeholder="例如：https://api.novelopen.com/creek" />
                        </Form.Item>
                        <Form.Item
                          label="PID"
                          name="partnerId"
                          rules={[
                            { required: true, message: '请输入 PID' },
                            { max: 64, message: 'PID 不能超过64个字符' },
                          ]}
                        >
                          <Input placeholder="请输入平台提供的 PID" />
                        </Form.Item>
                        <Form.Item
                          label="KEY"
                          name="apiKey"
                          extra={
                            activeProvider.connection?.credentialConfigured
                              ? '已配置 KEY，留空表示保留当前 KEY'
                              : '首次配置必须填写平台提供的 KEY'
                          }
                          rules={[
                            {
                              required:
                                !activeProvider.connection
                                  ?.credentialConfigured,
                              message: '请输入 KEY',
                            },
                            { max: 256, message: 'KEY 不能超过256个字符' },
                          ]}
                        >
                          <Input.Password
                            placeholder="请输入平台提供的 KEY"
                            autoComplete="new-password"
                          />
                        </Form.Item>
                      </>
                    ) : (
                      <Alert
                        type="info"
                        showIcon
                        message="人工报备模式无需配置 API 地址、PID 和 KEY，由管理员手工维护报备状态"
                      />
                    )}
                    <Form.Item
                      label="启用状态"
                      name="status"
                      valuePropName="checked"
                    >
                      <Switch checkedChildren="启用" unCheckedChildren="停用" />
                    </Form.Item>
                    <Form.Item
                      label="账号报备方式"
                      extra="API 自动报备或人工维护报备状态"
                    >
                      <Radio.Group
                        aria-label="账号报备方式"
                        value={filingMode}
                        onChange={(event) =>
                          setFilingMode(event.target.value as FilingMode)
                        }
                        options={[
                          { label: 'API 自动报备', value: 'API' },
                          { label: '人工报备', value: 'MANUAL' },
                        ]}
                      />
                    </Form.Item>
                  </Form>
                </div>
              ) : null}

              {isSuperAdmin && activeProvider ? (
                <div className="provider-config-footer">
                  <Button
                    type="primary"
                    icon={<Save size={16} />}
                    loading={saving}
                    onClick={() => void handleSave()}
                  >
                    提交
                  </Button>
                  <Tooltip title={testDisabledReason ?? '测试当前已保存的配置'}>
                    <span>
                      <Button
                        icon={<FlaskConical size={16} />}
                        disabled={Boolean(testDisabledReason)}
                        loading={testing}
                        onClick={() => void handleTest()}
                      >
                        连接测试
                      </Button>
                    </span>
                  </Tooltip>
                </div>
              ) : null}
            </>
          ) : loading ? null : (
            <Result status="info" title="暂无短剧平台配置" />
          )}
        </section>
      </Spin>

      <Modal
        title="连接测试结果"
        open={Boolean(testResult)}
        footer={null}
        onCancel={() => setTestResult(null)}
      >
        {testResult && activeProvider ? (
          <Result
            status={testResult.reachable ? 'success' : 'error'}
            icon={testResult.reachable ? <CheckCircle2 size={48} /> : undefined}
            title={testResult.reachable ? '连接可达' : '连接失败'}
            subTitle={
              <span className="provider-config-test-copy">
                {activeProvider.providerName}：{testResult.message}
                <br />
                测试时间：{formatDate(testResult.testedAt)}
              </span>
            }
          />
        ) : null}
      </Modal>
    </PageContainer>
  )
}

function ConnectionTag({ provider }: { provider: DramaProvider }) {
  if (!provider.connection) return <Tag>未配置</Tag>
  return provider.connection.status === 1 ? (
    <Tag color="success">接入已启用</Tag>
  ) : (
    <Tag>接入已停用</Tag>
  )
}

function getTestDisabledReason(provider: DramaProvider) {
  if (provider.status !== 1) return '平台已停用'
  if (!provider.connection) return '请先提交 API 配置'
  if (provider.connection.status !== 1) return 'API 配置已停用'
  if (provider.connection.filingMode === 'MANUAL')
    return '人工报备模式无需连接测试'
  if (!provider.connection.credentialConfigured) return '请先配置 KEY'
  return null
}

function formatDate(value: string | null | undefined) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function isValidationError(
  error: unknown,
): error is { errorFields: unknown[] } {
  return typeof error === 'object' && error !== null && 'errorFields' in error
}
