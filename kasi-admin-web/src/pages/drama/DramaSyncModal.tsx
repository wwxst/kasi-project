import { App as AntdApp, Button, Form, Modal, Segmented, Select } from 'antd'
import { useEffect, useState } from 'react'
import { isUnauthorizedError } from '../../api/http'
import {
  listDramaLanguageOptions,
  requestDramaCatalogSync,
} from '../../features/drama/dramaCatalogApi'
import type {
  DramaSyncType,
  RequestDramaSync,
} from '../../features/drama/dramaCatalogTypes'
import type { DramaProvider } from '../../features/provider/providerTypes'

interface DramaSyncModalProps {
  open: boolean
  providers: DramaProvider[]
  preferredProviderId: number | null
  onClose: () => void
  onSubmitted: (providerId: number) => void
}

export function DramaSyncModal({
  open,
  providers,
  preferredProviderId,
  onClose,
  onSubmitted,
}: DramaSyncModalProps) {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<RequestDramaSync>()
  const [submitting, setSubmitting] = useState(false)
  const [languageOptions, setLanguageOptions] = useState<
    Awaited<ReturnType<typeof listDramaLanguageOptions>>
  >([])
  useEffect(() => {
    if (open)
      void listDramaLanguageOptions()
        .then(setLanguageOptions)
        .catch(() => undefined)
  }, [open])

  useEffect(() => {
    if (!open) return
    const providerId = providers.some(
      (provider) => provider.id === preferredProviderId,
    )
      ? preferredProviderId
      : providers[0]?.id
    form.setFieldsValue({
      providerId: providerId ?? undefined,
      syncType: 'INCREMENTAL',
      languages: undefined,
    })
  }, [form, open, preferredProviderId, providers])

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      await requestDramaCatalogSync(values)
      message.success('同步任务已提交')
      onSubmitted(values.providerId)
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '同步任务提交失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title="同步短剧目录"
      open={open}
      width={520}
      data-testid="drama-sync-modal"
      footer={
        <div className="drama-catalog-page__modal-footer">
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            loading={submitting}
            data-testid="drama-sync-submit"
            onClick={() => void handleSubmit()}
          >
            提交任务
          </Button>
        </div>
      }
      onCancel={onClose}
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          label="短剧平台"
          name="providerId"
          rules={[{ required: true, message: '请选择短剧平台' }]}
        >
          <Select
            placeholder="请选择短剧平台"
            options={providers.map((provider) => ({
              value: provider.id,
              label: provider.providerName,
            }))}
          />
        </Form.Item>
        <Form.Item
          label="同步方式"
          name="syncType"
          rules={[{ required: true, message: '请选择同步方式' }]}
        >
          <Segmented<DramaSyncType>
            block
            options={[
              { value: 'INCREMENTAL', label: '增量同步' },
              { value: 'FULL', label: '全量同步' },
            ]}
          />
        </Form.Item>
        <Form.Item label="语言" name="languages">
          <Select
            mode="multiple"
            allowClear
            placeholder="留空时同步全部支持语言"
            options={languageOptions}
          />
        </Form.Item>
      </Form>
    </Modal>
  )
}
