import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  App,
  Button,
  Form,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
} from 'antd'
import { PageContainer } from '@ant-design/pro-components'
import { Pencil, Plus } from 'lucide-react'
import { useAuthStore } from '../../features/auth/authStore'
import { isUnauthorizedError } from '../../api/http'
import {
  createCommissionRule,
  listCommissionRules,
  listProviders,
  updateCommissionRule,
} from '../../features/provider/providerApi'
import type {
  CommissionRule,
  CommissionRuleRequest,
} from '../../features/provider/commissionRuleTypes'
import type { DramaProvider } from '../../features/provider/providerTypes'
import './commission-rule-page.css'

interface RuleRow extends CommissionRule {
  providerName: string
  providerCode: string
}

interface RuleFormValues {
  providerId: number
  channelFeeRate: number
  principalFeeRate: number
  principalCommissionRate: number
  downstreamFeeRate: number
  downstreamCommissionRate: number
}

const rateRules = [
  { required: true, message: '请输入费率' },
  { type: 'number' as const, min: 0, max: 100, message: '费率范围为 0 到 100' },
]

export function CommissionRulePage() {
  const { message } = App.useApp()
  const isSuperAdmin = useAuthStore((state) => state.admin?.isSuperAdmin === 1)
  const [form] = Form.useForm<RuleFormValues>()
  const [providers, setProviders] = useState<DramaProvider[]>([])
  const [rows, setRows] = useState<RuleRow[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingRule, setEditingRule] = useState<RuleRow | null>(null)
  const [providerFilter, setProviderFilter] = useState<number>()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const providerList = await listProviders()
      const ruleLists = await Promise.all(
        providerList.map(async (provider) => ({
          provider,
          rules: await listCommissionRules(provider.id),
        })),
      )
      setProviders(providerList)
      setRows(
        ruleLists.flatMap(({ provider, rules }) =>
          rules.map((rule) => ({
            ...rule,
            providerName: provider.providerName,
            providerCode: provider.providerCode,
          })),
        ),
      )
    } catch (error) {
      setRows([])
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '分佣规则加载失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    void load()
  }, [load])

  const filteredRows = useMemo(
    () =>
      providerFilter
        ? rows.filter((row) => row.providerId === providerFilter)
        : rows,
    [providerFilter, rows],
  )

  const openCreate = () => {
    setEditingRule(null)
    form.resetFields()
    form.setFieldsValue({
      providerId: providerFilter,
      channelFeeRate: 0,
      principalFeeRate: 0,
      principalCommissionRate: 0,
      downstreamFeeRate: 0,
      downstreamCommissionRate: 0,
    })
    setModalOpen(true)
  }

  const openEdit = (rule: RuleRow) => {
    setEditingRule(rule)
    form.setFieldsValue({
      providerId: rule.providerId,
      channelFeeRate: rule.channelFeeRate,
      principalFeeRate: rule.principalFeeRate,
      principalCommissionRate: rule.principalCommissionRate,
      downstreamFeeRate: rule.downstreamFeeRate,
      downstreamCommissionRate: rule.downstreamCommissionRate,
    })
    setModalOpen(true)
  }

  const handleSave = async () => {
    try {
      const values = await form.validateFields()
      const request: CommissionRuleRequest = {
        channelFeeRate: values.channelFeeRate,
        principalFeeRate: values.principalFeeRate,
        principalCommissionRate: values.principalCommissionRate,
        downstreamFeeRate: values.downstreamFeeRate,
        downstreamCommissionRate: values.downstreamCommissionRate,
      }
      setSaving(true)
      if (editingRule) {
        await updateCommissionRule(values.providerId, editingRule.id, request)
      } else {
        await createCommissionRule(values.providerId, request)
      }
      message.success(editingRule ? '默认分佣规则已更新' : '默认分佣规则已设置')
      setModalOpen(false)
      await load()
    } catch (error) {
      if (isValidationError(error)) return
      if (isUnauthorizedError(error)) return
      message.error(
        error instanceof Error ? error.message : '默认分佣规则保存失败',
      )
    } finally {
      setSaving(false)
    }
  }

  const configuredProviderIds = new Set(rows.map((row) => row.providerId))

  return (
    <PageContainer
      title="分佣规则"
      content="按短剧平台配置默认分佣费率，平台下所有短剧和接入账号共用。"
      className="commission-rule-page"
    >
      {!isSuperAdmin ? <Tag color="blue">当前为只读模式</Tag> : null}
      <div className="commission-rule-toolbar">
        <Select
          allowClear
          placeholder="筛选平台"
          value={providerFilter}
          onChange={setProviderFilter}
          options={providers.map((provider) => ({
            label: provider.providerName,
            value: provider.id,
          }))}
          style={{ minWidth: 220 }}
        />
        {isSuperAdmin ? (
          <Button type="primary" icon={<Plus size={16} />} onClick={openCreate}>
            设置默认规则
          </Button>
        ) : null}
      </div>
      <Spin spinning={loading}>
        <Table<RuleRow>
          rowKey="providerId"
          dataSource={filteredRows}
          locale={{ emptyText: '暂未配置默认分佣规则' }}
          pagination={false}
          columns={[
            {
              title: '平台',
              dataIndex: 'providerName',
              render: (value, row) => (
                <span>
                  {value}（{row.providerCode}）
                </span>
              ),
            },
            {
              title: '渠道费率',
              dataIndex: 'channelFeeRate',
              render: (value) => `${value}%`,
            },
            {
              title: '甲方手续费率',
              dataIndex: 'principalFeeRate',
              render: (value) => `${value}%`,
            },
            {
              title: '甲方分佣比例',
              dataIndex: 'principalCommissionRate',
              render: (value) => `${value}%`,
            },
            {
              title: '我方手续费率',
              dataIndex: 'downstreamFeeRate',
              render: (value) => `${value}%`,
            },
            {
              title: '下游分佣比例',
              dataIndex: 'downstreamCommissionRate',
              render: (value) => `${value}%`,
            },
            ...(isSuperAdmin
              ? [
                  {
                    title: '操作',
                    key: 'actions',
                    render: (_: unknown, row: RuleRow) => (
                      <Space>
                        <Button
                          type="link"
                          icon={<Pencil size={14} />}
                          onClick={() => openEdit(row)}
                        >
                          编辑
                        </Button>
                      </Space>
                    ),
                  },
                ]
              : []),
          ]}
        />
      </Spin>
      {isSuperAdmin ? (
        <div className="commission-rule-unconfigured">
          {providers
            .filter((provider) => !configuredProviderIds.has(provider.id))
            .map((provider) => (
              <div
                key={provider.id}
                className="commission-rule-unconfigured-row"
              >
                <span>
                  {provider.providerName}（{provider.providerCode}）
                </span>
                <span>未配置</span>
                <Button
                  type="link"
                  onClick={() => {
                    setProviderFilter(provider.id)
                    form.resetFields()
                    form.setFieldsValue({
                      providerId: provider.id,
                      channelFeeRate: 0,
                      principalFeeRate: 0,
                      principalCommissionRate: 0,
                      downstreamFeeRate: 0,
                      downstreamCommissionRate: 0,
                    })
                    setEditingRule(null)
                    setModalOpen(true)
                  }}
                >
                  设置
                </Button>
              </div>
            ))}
        </div>
      ) : null}
      <Modal
        title={editingRule ? '编辑默认分佣规则' : '设置默认分佣规则'}
        open={modalOpen}
        confirmLoading={saving}
        onOk={() => void handleSave()}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="平台"
            name="providerId"
            rules={[{ required: true, message: '请选择平台' }]}
          >
            <Select
              disabled={Boolean(editingRule)}
              options={providers.map((provider) => ({
                label: provider.providerName,
                value: provider.id,
                disabled: configuredProviderIds.has(provider.id),
              }))}
            />
          </Form.Item>
          <div className="commission-rule-rate-grid">
            {[
              ['channelFeeRate', '渠道费率'],
              ['principalFeeRate', '甲方手续费率'],
              ['principalCommissionRate', '甲方分佣比例'],
              ['downstreamFeeRate', '我方手续费率'],
              ['downstreamCommissionRate', '下游分佣比例'],
            ].map(([name, label]) => (
              <Form.Item key={name} label={label} name={name} rules={rateRules}>
                <InputNumber min={0} max={100} precision={4} suffix="%" />
              </Form.Item>
            ))}
          </div>
        </Form>
      </Modal>
    </PageContainer>
  )
}

function isValidationError(error: unknown) {
  return Boolean(error && typeof error === 'object' && 'errorFields' in error)
}
