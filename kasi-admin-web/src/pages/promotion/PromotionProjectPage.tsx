import { PageContainer } from '@ant-design/pro-components'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App as AntdApp,
  Button,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { ExternalLink, Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { resolveApiAssetUrl } from '../../api/assets'
import {
  createPromotionProject,
  deletePromotionProject,
  listPromotionProjects,
  updatePromotionProject,
} from '../../features/promotionProject/promotionProjectApi'
import type {
  PromotionProject,
  PromotionProjectFormValues,
} from '../../features/promotionProject/promotionProjectTypes'
import './promotion-project-page.css'

const queryKey = 'promotion-projects'

export function PromotionProjectPage() {
  const [query, setQuery] = useState({ page: 1, size: 20 })
  const [editing, setEditing] = useState<PromotionProject | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [coverFile, setCoverFile] = useState<File | undefined>()
  const [coverError, setCoverError] = useState(false)
  const [form] = Form.useForm<PromotionProjectFormValues>()
  const queryClient = useQueryClient()
  const { message, modal } = AntdApp.useApp()
  const projectsQuery = useQuery({
    queryKey: [queryKey, query.page, query.size],
    queryFn: () => listPromotionProjects(query),
  })

  const refresh = () => queryClient.invalidateQueries({ queryKey: [queryKey] })
  const saveMutation = useMutation({
    mutationFn: async (values: PromotionProjectFormValues) => {
      const request = { ...values, coverFile }
      return editing
        ? updatePromotionProject(editing.id, request)
        : createPromotionProject(request)
    },
    onSuccess: async () => {
      setModalOpen(false)
      await refresh()
      void message.success(editing ? '项目修改成功' : '项目新增成功')
    },
  })
  const deleteMutation = useMutation({
    mutationFn: deletePromotionProject,
    onSuccess: async () => {
      await refresh()
      void message.success('项目删除成功')
    },
  })

  const openCreate = () => {
    setEditing(null)
    setCoverFile(undefined)
    setCoverError(false)
    form.setFieldsValue({ status: 'ENABLED', sortOrder: 0 })
    form.resetFields(['name', 'projectDocumentUrl'])
    setModalOpen(true)
  }

  const openEdit = (project: PromotionProject) => {
    setEditing(project)
    setCoverFile(undefined)
    setCoverError(false)
    form.setFieldsValue({
      name: project.name,
      projectDocumentUrl: project.projectDocumentUrl,
      status: project.status,
      sortOrder: project.sortOrder,
    })
    setModalOpen(true)
  }

  const confirmDelete = (project: PromotionProject) => {
    modal.confirm({
      title: '删除项目',
      content: `删除后将立即从用户端移除“${project.name}”，且无法恢复。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => deleteMutation.mutateAsync(project.id),
    })
  }

  const columns: ColumnsType<PromotionProject> = [
    {
      title: '封面',
      dataIndex: 'coverImageUrl',
      width: 140,
      render: (value: string, record) => (
        <Image
          className="promotion-project-page__cover"
          width={96}
          height={64}
          src={resolveApiAssetUrl(value)}
          alt={record.name}
          preview={false}
        />
      ),
    },
    { title: '项目名称', dataIndex: 'name', ellipsis: true },
    {
      title: '项目文档',
      dataIndex: 'projectDocumentUrl',
      ellipsis: true,
      render: (value: string) => (
        <a href={value} target="_blank" rel="noopener noreferrer">
          <ExternalLink size={15} /> 查看文档
        </a>
      ),
    },
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
            aria-label={`编辑 ${record.name}`}
            title="编辑"
            onClick={() => openEdit(record)}
          />
          <Button
            type="text"
            danger
            icon={<Trash2 size={16} />}
            aria-label={`删除 ${record.name}`}
            title="删除"
            onClick={() => confirmDelete(record)}
          />
        </Space>
      ),
    },
  ]

  const handleTableChange = (pagination: TablePaginationConfig) => {
    setQuery({
      page: pagination.current ?? 1,
      size: pagination.pageSize ?? 20,
    })
  }

  const submitForm = () => {
    if (!editing && !coverFile) {
      setCoverError(true)
      return
    }
    form.submit()
  }

  return (
    <PageContainer>
      <div className="promotion-project-page__toolbar">
        <Button type="primary" icon={<Plus size={16} />} onClick={openCreate}>
          新增项目
        </Button>
      </div>
      <Table
        className="promotion-project-page__table"
        rowKey="id"
        columns={columns}
        dataSource={projectsQuery.data?.list ?? []}
        loading={projectsQuery.isLoading}
        pagination={{
          current: query.page,
          pageSize: query.size,
          total: projectsQuery.data?.total ?? 0,
          showSizeChanger: true,
        }}
        onChange={handleTableChange}
      />

      <Modal
        title={editing ? '编辑项目' : '新增项目'}
        open={modalOpen}
        okText={editing ? '保存修改' : '确认新增'}
        cancelText="取消"
        confirmLoading={saveMutation.isPending}
        onOk={submitForm}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
        >
          <Form.Item
            name="name"
            label="项目名称"
            rules={[
              { required: true, whitespace: true, message: '请输入项目名称' },
            ]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            name="projectDocumentUrl"
            label="项目文档 URL"
            rules={[
              { required: true, message: '请输入项目文档 URL' },
              { type: 'url', message: '请输入有效的 URL' },
              {
                pattern: /^https:\/\//i,
                message: '项目文档 URL 必须使用 HTTPS',
              },
            ]}
          >
            <Input type="url" maxLength={1024} />
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
              className="promotion-project-page__sort"
            />
          </Form.Item>
          <Form.Item label="项目封面" required={!editing}>
            <div className="promotion-project-page__upload-row">
              {editing && !coverFile ? (
                <Image
                  width={120}
                  height={80}
                  src={resolveApiAssetUrl(editing.coverImageUrl)}
                  alt={`${editing.name}当前封面`}
                  preview={false}
                />
              ) : null}
              <label className="promotion-project-page__file-button">
                <span>
                  {coverFile
                    ? coverFile.name
                    : editing
                      ? '替换封面'
                      : '选择封面'}
                </span>
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  aria-label="项目封面"
                  onChange={(event) => {
                    setCoverFile(event.target.files?.[0])
                    setCoverError(false)
                  }}
                />
              </label>
            </div>
            {coverError ? (
              <span className="promotion-project-page__cover-error">
                请选择项目封面
              </span>
            ) : null}
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  )
}
