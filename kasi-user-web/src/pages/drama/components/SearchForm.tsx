import { Button, Col, Form, Input, Row, Select } from 'tdesign-react'
import type { SubmitContext } from 'tdesign-react'
import { useEffect, useState } from 'react'
import { listDramaLanguageOptions } from '../../../features/dramas/dramasApi'
import Style from './SearchForm.module.less'

export interface DramaFilters {
  title: string
  language?: string
}

interface SearchFormProps {
  onSubmit: (values: DramaFilters) => void
  onReset: () => void
}

function normalizeFilters(fields: Record<string, unknown>): DramaFilters {
  return {
    title: String(fields.title ?? '').trim(),
    language: fields.language as string | undefined,
  }
}

export default function SearchForm({ onSubmit, onReset }: SearchFormProps) {
  const [languageOptions, setLanguageOptions] = useState<
    Awaited<ReturnType<typeof listDramaLanguageOptions>>
  >([])
  useEffect(() => {
    void listDramaLanguageOptions()
      .then(setLanguageOptions)
      .catch(() => undefined)
  }, [])
  const handleSubmit = (event: SubmitContext) => {
    if (event.validateResult === true) {
      onSubmit(normalizeFilters(event.fields ?? {}))
    }
  }

  return (
    <div className={Style.query}>
      <Form onSubmit={handleSubmit} onReset={onReset} labelWidth={96} colon>
        <Row>
          <Col flex="1">
            <Row gutter={[16, 16]}>
              <Col span={6} xs={12} sm={8} lg={6} xl={6}>
                <Form.FormItem label="短剧标题" name="title">
                  <Input placeholder="输入短剧标题" />
                </Form.FormItem>
              </Col>
              <Col span={6} xs={12} sm={8} lg={6} xl={6}>
                <Form.FormItem label="语言" name="language">
                  <Select options={languageOptions} placeholder="请选择语言" />
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
