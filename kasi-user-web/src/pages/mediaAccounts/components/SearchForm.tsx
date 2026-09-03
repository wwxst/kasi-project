import { Button, Col, Form, Input, Row, Select } from 'tdesign-react'
import type { SubmitContext } from 'tdesign-react'
import type { MediaType } from '../../../features/mediaAccounts/types'
import Style from './SearchForm.module.less'

export interface MediaAccountFilters {
  keyword: string
  mediaType?: MediaType
}

interface SearchFormProps {
  onSubmit: (values: MediaAccountFilters) => void
  onReset: () => void
}

const mediaTypeOptions = [
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'FACEBOOK', label: 'Facebook' },
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'INSTAGRAM', label: 'Instagram' },
]

function normalizeFilters(
  fields: Record<string, unknown>,
): MediaAccountFilters {
  return {
    keyword: String(fields.keyword ?? '').trim(),
    mediaType: fields.mediaType as MediaType | undefined,
  }
}

export default function SearchForm({ onSubmit, onReset }: SearchFormProps) {
  const handleSubmit = (event: SubmitContext) => {
    if (event.validateResult === true) {
      onSubmit(normalizeFilters(event.fields ?? {}))
    }
  }

  return (
    <div className={Style.query}>
      <Form onSubmit={handleSubmit} onReset={onReset} labelWidth={128} colon>
        <Row>
          <Col flex="1">
            <Row gutter={[16, 16]}>
              <Col span={3} xs={12} sm={6} lg={3} xl={3}>
                <Form.FormItem label="媒体平台" name="mediaType">
                  <Select
                    options={mediaTypeOptions}
                    placeholder="请选择媒体平台"
                  />
                </Form.FormItem>
              </Col>
              <Col span={3} xs={12} sm={6} lg={3} xl={3}>
                <Form.FormItem label="账号关键词" name="keyword">
                  <Input placeholder="输入账号名称或账号 ID" />
                </Form.FormItem>
              </Col>
            </Row>
          </Col>
          <Col flex="160px" className={Style.actions}>
            <Button
              theme="primary"
              type="submit"
              style={{ margin: '0px 20px' }}
            >
              查询
            </Button>
            <Button type="reset" variant="base" theme="default">
              重置
            </Button>
          </Col>
        </Row>
      </Form>
    </div>
  )
}
