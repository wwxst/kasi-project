import { CameraOutlined, DownOutlined, PlusOutlined } from '@ant-design/icons'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { PageContainer, ProTable } from '@ant-design/pro-components'
import {
  App as AntdApp,
  Avatar,
  Button,
  Drawer,
  Dropdown,
  Form,
  Modal,
  Spin,
  Tag,
  Upload,
} from 'antd'
import ImgCrop from 'antd-img-crop'
import type { FormInstance } from 'antd/es/form'
import type { MenuProps, UploadProps } from 'antd'
import type { ReactNode } from 'react'
import { useRef, useState } from 'react'
import { resolveApiAssetUrl } from '../../api/assets'
import { isUnauthorizedError } from '../../api/http'
import type { PageQuery, PageResult } from './managementTypes'
import '../../pages/management/management-page.css'

type OperationMode = 'detail-delete' | 'detail-more'
type UploadRequestOption = Parameters<
  NonNullable<UploadProps['customRequest']>
>[0]

export interface DetailField<T> {
  label: string
  dataIndex?: keyof T | string
  span?: number
  render?: (record: T) => ReactNode
}

export interface DetailSection<T> {
  title: string
  fields: DetailField<T>[]
  editable?: boolean
}

export interface ManagementTablePageProps<
  T extends { id: number; status: number },
  D extends T,
> {
  title: string
  entityName: string
  description: string
  resourceKey: string
  columns: ProColumns<T>[]
  detailSections: DetailSection<D>[]
  operationMode: OperationMode
  list: (query: PageQuery) => Promise<PageResult<T>>
  getDetail: (id: number) => Promise<D>
  create: (values: Record<string, unknown>) => Promise<unknown>
  update: (id: number, values: Record<string, unknown>) => Promise<unknown>
  updateStatus?: (id: number, status: number) => Promise<void>
  remove: (id: number) => Promise<void>
  recordName: (record: T) => string
  renderCreateForm: (form: FormInstance) => React.ReactNode
  renderEditForm: (form: FormInstance, record: D) => React.ReactNode
  formValues: (record: D) => Record<string, unknown>
  detailIdentity?: {
    avatarUrl: (record: D) => string | null
    title: (record: D) => ReactNode
    subtitle: (record: D) => ReactNode
    fallback: (record: D) => ReactNode
  }
  uploadAvatar?: (record: D, file: File) => Promise<D>
  changePassword?: (record: D, values: Record<string, unknown>) => Promise<void>
  renderPasswordForm?: (form: FormInstance, record: D) => ReactNode
  onPasswordChanged?: (record: D) => void
  refreshAfterUpdate?: (record: D, values: Record<string, unknown>) => boolean
  canDelete?: (record: T) => boolean
  canEdit?: (record: T) => boolean
  canChangeStatus?: (record: T) => boolean
}

export function StatusTag({ status }: { status: number }) {
  return status === 1 ? <Tag color="success">启用</Tag> : <Tag>禁用</Tag>
}

export function ManagementTablePage<
  T extends { id: number; status: number },
  D extends T,
>({
  title,
  entityName,
  description,
  resourceKey,
  columns,
  detailSections,
  operationMode,
  list,
  getDetail,
  create,
  update,
  updateStatus,
  remove,
  recordName,
  renderCreateForm,
  renderEditForm,
  formValues,
  detailIdentity,
  uploadAvatar,
  changePassword,
  renderPasswordForm,
  onPasswordChanged,
  refreshAfterUpdate,
  canDelete,
  canEdit,
  canChangeStatus,
}: ManagementTablePageProps<T, D>) {
  const { message, modal } = AntdApp.useApp()
  const actionRef = useRef<ActionType | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [createSubmitting, setCreateSubmitting] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailRecord, setDetailRecord] = useState<D | null>(null)
  const [avatarUploading, setAvatarUploading] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [passwordReady, setPasswordReady] = useState(false)
  const [passwordSubmitting, setPasswordSubmitting] = useState(false)
  const [statusLoadingId, setStatusLoadingId] = useState<number | null>(null)
  const [form] = Form.useForm()
  const [editForm] = Form.useForm()
  const [passwordForm] = Form.useForm()

  const openCreate = () => {
    form.resetFields()
    setCreateOpen(true)
  }

  const handleCreate = async () => {
    try {
      const values = await form.validateFields()
      setCreateSubmitting(true)
      await create(values)
      message.success('创建成功')
      setCreateOpen(false)
      actionRef.current?.reload()
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '创建失败')
    } finally {
      setCreateSubmitting(false)
    }
  }

  const openEdit = () => {
    if (!detailRecord) return
    setEditOpen(true)
  }

  const handleEdit = async () => {
    if (!detailRecord) return
    try {
      const values = await editForm.validateFields()
      setEditSubmitting(true)
      await update(detailRecord.id, values)
      message.success('编辑成功')
      setEditOpen(false)
      if (!refreshAfterUpdate || refreshAfterUpdate(detailRecord, values)) {
        const refreshed = await getDetail(detailRecord.id)
        setDetailRecord(refreshed)
      } else {
        setDetailRecord((record) =>
          record ? ({ ...record, ...values } as D) : record,
        )
      }
      actionRef.current?.reload()
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '编辑失败')
    } finally {
      setEditSubmitting(false)
    }
  }

  const openPassword = () => {
    setPasswordReady(false)
    setPasswordOpen(true)
  }

  const handlePassword = async () => {
    if (!detailRecord || !changePassword) return
    try {
      const values = await passwordForm.validateFields()
      setPasswordSubmitting(true)
      await changePassword(detailRecord, values)
      message.success('密码修改成功')
      setPasswordOpen(false)
      onPasswordChanged?.(detailRecord)
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '密码修改失败')
    } finally {
      setPasswordSubmitting(false)
    }
  }

  const openDetail = async (record: T) => {
    setDetailRecord(null)
    setDetailOpen(true)
    setDetailLoading(true)
    try {
      setDetailRecord(await getDetail(record.id))
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '详情加载失败')
      setDetailOpen(false)
    } finally {
      setDetailLoading(false)
    }
  }

  const validateAvatarBeforeCrop = (file: File) => {
    const allowed = ['image/jpeg', 'image/png', 'image/webp'].includes(
      file.type,
    )
    if (!allowed) {
      message.error('只支持 JPG、PNG、WebP 图片')
      return false
    }
    if (file.size > 2 * 1024 * 1024) {
      message.error('头像文件不能超过2MB')
      return false
    }
    return true
  }

  const handleAvatarUpload = async (options: UploadRequestOption) => {
    if (!detailRecord || !uploadAvatar || typeof options.file === 'string') {
      options.onError?.(new Error('头像上传不可用'))
      return
    }
    const file =
      options.file instanceof File
        ? options.file
        : new File([options.file], 'avatar.png', {
            type: options.file.type || 'image/png',
          })
    try {
      setAvatarUploading(true)
      const updated = await uploadAvatar(detailRecord, file)
      setDetailRecord(updated)
      actionRef.current?.reload()
      message.success('头像修改成功')
      options.onSuccess?.(updated)
    } catch (error) {
      if (isUnauthorizedError(error)) return
      const uploadError =
        error instanceof Error ? error : new Error('头像上传失败')
      message.error(uploadError.message)
      options.onError?.(uploadError)
    } finally {
      setAvatarUploading(false)
    }
  }

  const handleStatus = async (record: T) => {
    if (!updateStatus || (canChangeStatus && !canChangeStatus(record))) return
    const nextStatus = record.status === 1 ? 0 : 1
    setStatusLoadingId(record.id)
    try {
      await updateStatus(record.id, nextStatus)
      message.success(nextStatus === 1 ? '已启用' : '已禁用')
      actionRef.current?.reload()
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '状态修改失败')
    } finally {
      setStatusLoadingId(null)
    }
  }

  const handleRemove = (record: T) => {
    modal.confirm({
      title: '确认删除该账号？',
      content: recordName(record),
      okText: '删除',
      okButtonProps: {
        danger: true,
        'data-testid': `${resourceKey}-delete-confirm-${record.id}`,
      },
      cancelText: '取消',
      onOk: async () => {
        try {
          await remove(record.id)
          message.success('删除成功')
          actionRef.current?.reload()
        } catch (error) {
          if (isUnauthorizedError(error)) return
          message.error(error instanceof Error ? error.message : '删除失败')
          throw error
        }
      },
    })
  }

  const renderMoreAction = (record: T) => {
    const statusDisabled = Boolean(
      statusLoadingId === record.id ||
      !updateStatus ||
      (canChangeStatus && !canChangeStatus(record)),
    )
    const deleteDisabled = canDelete ? !canDelete(record) : false
    const items: MenuProps['items'] = [
      {
        key: 'status',
        label: record.status === 1 ? '禁用' : '启用',
        disabled: statusDisabled,
      },
      { type: 'divider' },
      { key: 'delete', label: '删除', danger: true, disabled: deleteDisabled },
    ]

    return (
      <Dropdown
        key="more"
        trigger={['click']}
        menu={{
          items,
          onClick: ({ key }) => {
            if (key === 'status') void handleStatus(record)
            if (key === 'delete') handleRemove(record)
          },
        }}
      >
        <Button
          type="link"
          size="small"
          data-testid={`${resourceKey}-more-${record.id}`}
        >
          更多 <DownOutlined />
        </Button>
      </Dropdown>
    )
  }

  const actionColumn: ProColumns<T> = {
    title: '操作',
    dataIndex: 'option',
    valueType: 'option',
    width: operationMode === 'detail-more' ? 150 : 140,
    fixed: 'right',
    render: (_, record) => {
      const detail = (
        <Button
          key="detail"
          type="link"
          size="small"
          data-testid={`${resourceKey}-detail-${record.id}`}
          onClick={() => void openDetail(record)}
        >
          详情
        </Button>
      )

      if (operationMode === 'detail-more') {
        return [detail, renderMoreAction(record)]
      }

      const deleteDisabled = canDelete ? !canDelete(record) : false
      return [
        detail,
        <Button
          key="delete"
          type="link"
          danger
          size="small"
          disabled={deleteDisabled}
          data-testid={`${resourceKey}-delete-${record.id}`}
          onClick={() => handleRemove(record)}
        >
          删除
        </Button>,
      ]
    },
  }

  const keywordColumn: ProColumns<T> = {
    title: '关键词',
    dataIndex: 'keyword',
    hideInTable: true,
    fieldProps: {
      allowClear: true,
      placeholder: '账号、姓名或联系方式',
    },
  }

  const tableColumns: ProColumns<T>[] = [
    keywordColumn,
    ...columns.map((column) => ({ ...column, search: false })),
    actionColumn,
  ]

  return (
    <PageContainer
      className="management-page"
      title={title}
      content={description}
      data-testid={`${resourceKey}-management-page`}
    >
      <ProTable<T>
        actionRef={actionRef}
        rowKey="id"
        headerTitle={title}
        columns={tableColumns}
        cardBordered
        search={{ labelWidth: 80 }}
        options={{
          density: true,
          fullScreen: true,
          reload: true,
          setting: true,
        }}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
        scroll={{ x: 'max-content' }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            data-testid={`${resourceKey}-create`}
            onClick={openCreate}
          >
            新建
          </Button>,
        ]}
        request={async (params) => {
          try {
            const keyword =
              typeof params.keyword === 'string' ? params.keyword.trim() : ''
            const result = await list({
              page: params.current ?? 1,
              size: params.pageSize ?? 20,
              keyword: keyword || undefined,
            })
            return { data: result.list, total: result.total, success: true }
          } catch (error) {
            if (isUnauthorizedError(error)) {
              return { data: [], total: 0, success: false }
            }
            message.error(
              error instanceof Error ? error.message : '列表加载失败',
            )
            return { data: [], total: 0, success: false }
          }
        }}
      />

      <Modal
        title={`新增${entityName}`}
        open={createOpen}
        width={640}
        okText="保存"
        cancelText="取消"
        okButtonProps={{ 'data-testid': `${resourceKey}-form-submit` }}
        styles={{
          body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' },
        }}
        onCancel={() => setCreateOpen(false)}
        onOk={() => void handleCreate()}
        confirmLoading={createSubmitting}
        forceRender
      >
        <Form
          form={form}
          name={`${resourceKey}-create-form`}
          layout="vertical"
          preserve={false}
          autoComplete="off"
        >
          {renderCreateForm(form)}
        </Form>
      </Modal>

      <Drawer
        title={`${entityName}管理`}
        open={detailOpen}
        size={800}
        rootClassName="management-detail-drawer"
        onClose={() => {
          setDetailOpen(false)
          setDetailRecord(null)
        }}
      >
        <Spin spinning={detailLoading}>
          {detailRecord ? (
            <div className="management-detail">
              {detailSections.map((section, sectionIndex) => (
                <section
                  className="management-detail__section"
                  key={section.title}
                >
                  <div className="management-detail__section-title">
                    <span className="management-detail__section-marker" />
                    <span>{section.title}</span>
                    <span className="management-detail__section-actions">
                      {section.editable &&
                      (!canEdit || canEdit(detailRecord)) ? (
                        <Button
                          type="link"
                          size="small"
                          data-testid={`${resourceKey}-detail-edit-${detailRecord.id}`}
                          onClick={openEdit}
                        >
                          编辑
                        </Button>
                      ) : null}
                      {sectionIndex === 0 &&
                      changePassword &&
                      renderPasswordForm ? (
                        <Button
                          type="link"
                          size="small"
                          data-testid={`${resourceKey}-detail-password-${detailRecord.id}`}
                          onClick={openPassword}
                        >
                          修改密码
                        </Button>
                      ) : null}
                    </span>
                  </div>
                  {sectionIndex === 0 && detailIdentity ? (
                    <div
                      className="management-detail__identity"
                      data-testid={`${resourceKey}-detail-identity-${detailRecord.id}`}
                    >
                      {uploadAvatar ? (
                        <ImgCrop
                          aspect={1}
                          cropShape="round"
                          modalTitle="裁剪头像"
                          modalOk="确认"
                          modalCancel="取消"
                          showGrid
                          rotationSlider
                          beforeCrop={validateAvatarBeforeCrop}
                        >
                          <Upload
                            accept="image/jpeg,image/png,image/webp"
                            showUploadList={false}
                            customRequest={(options) =>
                              void handleAvatarUpload(options)
                            }
                            disabled={avatarUploading}
                          >
                            <button
                              type="button"
                              className="management-detail__avatar-upload"
                              aria-label={`更换${String(detailIdentity.title(detailRecord))}的头像`}
                              data-testid={`${resourceKey}-detail-avatar-upload-${detailRecord.id}`}
                              disabled={avatarUploading}
                            >
                              <Spin spinning={avatarUploading} size="small">
                                <Avatar
                                  size={64}
                                  src={resolveApiAssetUrl(
                                    detailIdentity.avatarUrl(detailRecord),
                                  )}
                                >
                                  {detailIdentity.fallback(detailRecord)}
                                </Avatar>
                                <span className="management-detail__avatar-mask">
                                  <CameraOutlined aria-hidden="true" />
                                </span>
                              </Spin>
                            </button>
                          </Upload>
                        </ImgCrop>
                      ) : (
                        <Avatar
                          size={64}
                          src={resolveApiAssetUrl(
                            detailIdentity.avatarUrl(detailRecord),
                          )}
                        >
                          {detailIdentity.fallback(detailRecord)}
                        </Avatar>
                      )}
                      <div className="management-detail__identity-copy">
                        <strong>{detailIdentity.title(detailRecord)}</strong>
                        <span>{detailIdentity.subtitle(detailRecord)}</span>
                      </div>
                    </div>
                  ) : null}
                  <div className="management-detail__grid">
                    {section.fields.map((field) => {
                      const rawValue = field.dataIndex
                        ? detailRecord[field.dataIndex as keyof D]
                        : undefined
                      const value = field.render
                        ? field.render(detailRecord)
                        : rawValue === null ||
                            rawValue === undefined ||
                            rawValue === ''
                          ? '-'
                          : String(rawValue)
                      return (
                        <div
                          className="management-detail__field"
                          style={{ gridColumn: `span ${field.span ?? 1}` }}
                          key={field.label}
                        >
                          <span className="management-detail__label">
                            {field.label}
                          </span>
                          <span className="management-detail__value">
                            {value}
                          </span>
                        </div>
                      )
                    })}
                  </div>
                </section>
              ))}
            </div>
          ) : null}
        </Spin>
      </Drawer>

      <Drawer
        title={`编辑${entityName}`}
        open={editOpen}
        size={560}
        destroyOnHidden
        rootClassName="management-edit-drawer"
        afterOpenChange={(open) => {
          if (open && detailRecord) {
            editForm.setFieldsValue(formValues(detailRecord))
          }
        }}
        footer={
          <div className="management-edit-drawer__footer">
            <Button onClick={() => setEditOpen(false)}>取消</Button>
            <Button
              type="primary"
              loading={editSubmitting}
              data-testid={`${resourceKey}-edit-form-submit`}
              onClick={() => void handleEdit()}
            >
              确认
            </Button>
          </div>
        }
        onClose={() => setEditOpen(false)}
      >
        <div
          className="management-edit-drawer__form"
          data-testid={`${resourceKey}-edit-drawer-${detailRecord?.id ?? 'unknown'}`}
        >
          <Form
            form={editForm}
            name={`${resourceKey}-edit-form`}
            layout="horizontal"
            labelCol={{ flex: '0 0 88px' }}
            wrapperCol={{ flex: 1 }}
            preserve={false}
            autoComplete="off"
          >
            {detailRecord ? renderEditForm(editForm, detailRecord) : null}
          </Form>
        </div>
      </Drawer>

      <Drawer
        title="修改密码"
        open={passwordOpen}
        size={560}
        rootClassName="management-password-drawer"
        afterOpenChange={(open) => setPasswordReady(open)}
        footer={
          <div className="management-edit-drawer__footer">
            <Button onClick={() => setPasswordOpen(false)}>取消</Button>
            <Button
              type="primary"
              loading={passwordSubmitting}
              data-testid={`${resourceKey}-password-form-submit`}
              onClick={() => void handlePassword()}
            >
              确认
            </Button>
          </div>
        }
        onClose={() => setPasswordOpen(false)}
      >
        {passwordReady && detailRecord && renderPasswordForm ? (
          <div
            className="management-edit-drawer__form"
            data-testid={`${resourceKey}-password-drawer-${detailRecord.id}`}
          >
            <Form
              form={passwordForm}
              name={`${resourceKey}-password-form`}
              layout="horizontal"
              labelCol={{ flex: '0 0 88px' }}
              wrapperCol={{ flex: 1 }}
              preserve={false}
              autoComplete="off"
            >
              {renderPasswordForm(passwordForm, detailRecord)}
            </Form>
          </div>
        ) : null}
      </Drawer>
    </PageContainer>
  )
}
