import { useEffect, useRef } from 'react'
import { Dialog, Form, Input, Select } from 'tdesign-react'
import type { FormInstanceFunctions, SubmitContext } from 'tdesign-react'
import type {
  CreateMediaAccountInput,
  MediaType,
} from '../../../features/mediaAccounts/types'
import Style from './AccountFilingDialog.module.less'

interface AccountFilingDialogProps {
  visible: boolean
  loading?: boolean
  onClose: () => void
  onSubmit: (values: CreateMediaAccountInput) => Promise<void>
}

const mediaTypeOptions = [
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'FACEBOOK', label: 'Facebook' },
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'INSTAGRAM', label: 'Instagram' },
]

function normalizeValues(
  fields: Record<string, unknown>,
): CreateMediaAccountInput {
  return {
    mediaType: fields.mediaType as MediaType,
    externalAccountId: String(fields.externalAccountId ?? '').trim(),
    accountName: String(fields.accountName ?? '').trim() || undefined,
    accountLink: String(fields.accountLink ?? '').trim() || undefined,
  }
}

export default function AccountFilingDialog({
  visible,
  loading = false,
  onClose,
  onSubmit,
}: AccountFilingDialogProps) {
  const formRef = useRef<FormInstanceFunctions | null>(null)

  useEffect(() => {
    if (!visible) formRef.current?.reset()
  }, [visible])

  const handleConfirm = () => {
    formRef.current?.submit()
  }

  const handleSubmit = (event: SubmitContext) => {
    if (event.validateResult === true) {
      void onSubmit(normalizeValues(event.fields ?? {}))
    }
  }

  return (
    <Dialog
      header="账号报白"
      visible={visible}
      width={520}
      confirmBtn="提交报白"
      cancelBtn="取消"
      confirmLoading={loading}
      onClose={onClose}
      onCancel={onClose}
      onConfirm={handleConfirm}
    >
      <Form
        ref={formRef}
        className={Style.form}
        labelWidth={112}
        colon
        onSubmit={handleSubmit}
      >
        <Form.FormItem
          label="媒体平台"
          name="mediaType"
          rules={[{ required: true, message: '请选择媒体平台', type: 'error' }]}
        >
          <Select options={mediaTypeOptions} placeholder="请选择媒体平台" />
        </Form.FormItem>
        <Form.FormItem
          label="账号 ID"
          name="externalAccountId"
          rules={[{ required: true, message: '请输入账号 ID', type: 'error' }]}
        >
          <Input placeholder="请输入账号 ID" />
        </Form.FormItem>
        <Form.FormItem label="账号名称" name="accountName">
          <Input placeholder="请输入账号名称（可选）" />
        </Form.FormItem>
        <Form.FormItem
          label="账号主页链接"
          name="accountLink"
          rules={[
            {
              pattern: /^https:\/\/.+/,
              message: '主页链接必须使用 HTTPS',
              type: 'error',
            },
          ]}
        >
          <Input placeholder="请输入账号主页链接（可选）" />
        </Form.FormItem>
      </Form>
    </Dialog>
  )
}
