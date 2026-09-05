import {
  Alert,
  App as AntdApp,
  Button,
  Form,
  Modal,
  Segmented,
  Select,
} from 'antd'
import { useEffect, useState } from 'react'
import { isUnauthorizedError } from '../../api/http'
import {
  listDramaLanguageOptions,
  requestAllDramaContentSync,
} from '../../features/drama/dramaCatalogApi'
import type { RequestAllDramaContentSync } from '../../features/drama/dramaCatalogTypes'
import type { DramaProvider } from '../../features/provider/providerTypes'

type ContentSyncRange = 'ALL' | 'MISSING'

interface ContentSyncFormValues {
  providerId: number
  language?: string
  syncRange: ContentSyncRange
}

interface DramaContentSyncModalProps {
  open: boolean
  providers: DramaProvider[]
  preferredProviderId: number | null
  onClose: () => void
  onSubmitted: (providerId: number) => void
}

export function DramaContentSyncModal({
  open,
  providers,
  preferredProviderId,
  onClose,
  onSubmitted,
}: DramaContentSyncModalProps) {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<ContentSyncFormValues>()
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
    const providerId =
      preferredProviderId !== null &&
      providers.some((provider) => provider.id === preferredProviderId)
        ? preferredProviderId
        : providers[0]?.id
    form.setFieldsValue({
      providerId: providerId ?? undefined,
      language: undefined,
      syncRange: 'ALL',
    })
  }, [form, open, preferredProviderId, providers])

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      const request: RequestAllDramaContentSync = {
        providerId: values.providerId,
        missingOnly: values.syncRange === 'MISSING',
        ...(values.language ? { language: values.language } : {}),
      }
      const result = await requestAllDramaContentSync(request)
      message.success(
        `匹配 ${result.requestedCount} 部，排队 ${result.queuedCount} 部，运行中跳过 ${result.skippedCount} 部，无效 ${result.invalidCount} 部`,
      )
      onSubmitted(values.providerId)
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      if (isUnauthorizedError(error)) return
      message.error(
        error instanceof Error ? error.message : '免费剧集同步任务提交失败',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title="同步免费剧集"
      open={open}
      width={540}
      closable={!submitting}
      maskClosable={!submitting}
      keyboard={!submitting}
      data-testid="drama-content-sync-modal"
      footer={
        <div className="drama-catalog-page__modal-footer">
          <Button disabled={submitting} onClick={onClose}>
            取消
          </Button>
          <Button
            type="primary"
            loading={submitting}
            data-testid="drama-content-sync-submit"
            onClick={() => void handleSubmit()}
          >
            提交任务
          </Button>
        </div>
      }
      onCancel={submitting ? undefined : onClose}
    >
      <Alert type="info" showIcon message="当前仅同步 GoodShort 免费剧集" />
      <Form form={form} layout="vertical" preserve>
        <Form.Item
          label="短剧平台"
          name="providerId"
          rules={[{ required: true, message: '请选择短剧平台' }]}
        >
          <Select
            options={providers.map((provider) => ({
              value: provider.id,
              label: provider.providerName,
            }))}
          />
        </Form.Item>
        <Form.Item label="语言" name="language">
          <Select
            allowClear
            placeholder="留空同步该平台全部已同步语言"
            options={languageOptions}
          />
        </Form.Item>
        <Form.Item label="同步范围" name="syncRange">
          <Segmented<ContentSyncRange>
            block
            options={[
              { value: 'ALL', label: '同步全部在线短剧' },
              { value: 'MISSING', label: '仅补齐缺失视频地址' },
            ]}
          />
        </Form.Item>
      </Form>
    </Modal>
  )
}
