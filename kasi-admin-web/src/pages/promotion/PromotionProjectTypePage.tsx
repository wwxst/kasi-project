import { PageContainer } from '@ant-design/pro-components'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App as AntdApp,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import {
  createPromotionProjectType,
  deletePromotionProjectType,
  listPromotionProjectTypes,
  updatePromotionProjectType,
} from '../../features/promotionProjectType/promotionProjectTypeApi'
import type {
  PromotionProjectType,
  PromotionProjectTypeFormValues,
} from '../../features/promotionProjectType/promotionProjectTypeTypes'
import './promotion-project-type-page.css'

const queryKey = ['promotion-project-types']

export function PromotionProjectTypePage() {
  const [editing, setEditing] = useState<PromotionProjectType | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm<PromotionProjectTypeFormValues>()
  const queryClient = useQueryClient()
  const { message, modal } = AntdApp.useApp()
  const typesQuery = useQuery({ queryKey, queryFn: listPromotionProjectTypes })

  const refresh = () => queryClient.invalidateQueries({ queryKey })
  const saveMutation = useMutation({
    mutationFn: (values: PromotionProjectTypeFormValues) =>
      editing
        ? updatePromotionProjectType(editing.id, values)
        : createPromotionProjectType(values),
    onSuccess: async () => {
      setModalOpen(false)
      await refresh()
      void message.success(editing ? '项目类型修改成功' : '项目类型新增成功')
    },
    onError: (error) => {
      void message.error(
        error instanceof Error ? error.message : '项目类型保存失败',
      )
    },
  })
  const deleteMutation = useMutation({
    mutationFn: deletePromotionProjectType,
    onSuccess: async () => {
      await refresh()
      void message.success('项目类型删除成功')
    },
    onError: (error) => {
      void message.error(
        error instanceof Error ? error.message : '项目类型删除失败',
      )
    },
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ status: 'ENABLED', sortOrder: 0 })
    setModalOpen(true)
  }

  const openEdit = (projectType: PromotionProjectType) => {
    setEditing(projectType)
    form.setFieldsValue({
      code: projectType.code,
      name: projectType.name,
      status: projectType.status,
      sortOrder: projectType.sortOrder,
    })
    setModalOpen(true)
  }

  const confirmDelete = (projectType: PromotionProjectType) => {
    modal.confirm({
      title: '删除项目类型',
      content: `确认删除“${projectType.code} - ${projectType.name}”？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => deleteMutation.mutateAsync(projectType.id),
    })
  }

  const columns: ColumnsType<PromotionProjectType> = [
    { title: '类型编码', dataIndex: 'code', width: 160 },
    { title: '类型名称', dataIndex: 'name' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value) =>
        value === 'ENABLED' ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    { title: '排序', dataIndex: 'sortOrder', width: 90 },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <Space size={4}>
          <Button
            type="text"
            icon={<Pencil size={16} />}
            aria-label={`编辑 ${record.code}`}
            title="编辑"
            onClick={() => openEdit(record)}
          />
          <Button
            type="text"
            danger
            icon={<Trash2 size={16} />}
            aria-label={`删除 ${record.code}`}
            title="删除"
            onClick={() => confirmDelete(record)}
          />
        </Space>
      ),
    },
  ]

  return (
    <PageContainer>
      <div className="promotion-project-type-page__toolbar">
        <Button type="primary" icon={<Plus size={16} />} onClick={openCreate}>
          新增项目类型
        </Button>
      </div>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={typesQuery.data ?? []}
        loading={typesQuery.isLoading}
        pagination={false}
      />

      <Modal
        title={editing ? '编辑项目类型' : '新增项目类型'}
        open={modalOpen}
        okText={editing ? '保存修改' : '确认新增'}
        cancelText="取消"
        confirmLoading={saveMutation.isPending}
        onOk={() => form.submit()}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
        >
          <Form.Item
            name="code"
            label="类型编码"
            rules={[
              { required: true, whitespace: true, message: '请输入类型编码' },
              {
                pattern: /^[A-Za-z][A-Za-z0-9_]*$/,
                message: '只能使用字母、数字和下划线，并以字母开头',
              },
            ]}
          >
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item
            name="name"
            label="类型名称"
            rules={[
              { required: true, whitespace: true, message: '请输入类型名称' },
            ]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'ENABLED', label: '启用' },
                { value: 'DISABLED', label: '停用' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="sortOrder"
            label="显示顺序"
            rules={[{ required: true, message: '请输入显示顺序' }]}
          >
            <InputNumber
              min={0}
              precision={0}
              className="promotion-project-type-page__sort"
            />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  )
}
