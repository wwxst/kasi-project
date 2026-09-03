import { Form, Input, InputNumber, Tag } from 'antd'
import type { ProColumns } from '@ant-design/pro-components'
import type { FormInstance } from 'antd/es/form'
import { useNavigate } from 'react-router-dom'
import {
  changeAdminPassword,
  updateAdminProfile,
  uploadCurrentAdminAvatar,
} from '../../features/auth/authApi'
import { useAuthStore } from '../../features/auth/authStore'
import type { UpdateAdminProfileRequest } from '../../features/auth/authTypes'
import {
  ManagementTablePage,
  StatusTag,
} from '../../features/management/ManagementTablePage'
import type { DetailSection } from '../../features/management/ManagementTablePage'
import {
  createAdmin,
  getAdmin,
  listAdmins,
  removeAdmin,
  resetAdminPassword,
  uploadAdminAvatar,
  updateAdmin as updateManagedAdmin,
} from '../../features/management/adminManagementApi'
import type {
  AdminDetail,
  AdminListItem,
  CreateAdminRequest,
  ResetPasswordRequest,
  UpdateAdminRequest,
} from '../../features/management/managementTypes'

const columns: ProColumns<AdminListItem>[] = [
  {
    title: '姓名',
    dataIndex: 'realName',
    fixed: 'left',
    width: 140,
  },
  {
    title: '手机号',
    dataIndex: 'mobile',
    width: 140,
    renderText: (value) => value || '-',
  },
  {
    title: '邮箱',
    dataIndex: 'email',
    width: 210,
    renderText: (value) => value || '-',
  },
  {
    title: '角色',
    dataIndex: 'isSuperAdmin',
    width: 120,
    render: (_, record) =>
      record.isSuperAdmin === 1 ? (
        <Tag color="blue">超级管理员</Tag>
      ) : (
        <Tag>管理员</Tag>
      ),
  },
  {
    title: '登录时间',
    dataIndex: 'lastLoginAt',
    width: 170,
    renderText: formatDate,
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 90,
    render: (_, record) => <StatusTag status={record.status} />,
  },
]

const detailSections: DetailSection<AdminDetail>[] = [
  {
    title: '基本信息',
    editable: true,
    fields: [
      { label: '登录账号', dataIndex: 'username' },
      { label: '姓名', dataIndex: 'realName' },
      {
        label: '手机号',
        dataIndex: 'mobile',
        render: (record) => emptyValue(record.mobile),
      },
      {
        label: '邮箱',
        dataIndex: 'email',
        render: (record) => emptyValue(record.email),
      },
      {
        label: '角色',
        render: (record) =>
          record.isSuperAdmin === 1 ? (
            <Tag color="blue">超级管理员</Tag>
          ) : (
            <Tag>管理员</Tag>
          ),
      },
      {
        label: '状态',
        render: (record) => <StatusTag status={record.status} />,
      },
      { label: '登录时间', render: (record) => formatDate(record.lastLoginAt) },
      { label: '登录 IP', render: (record) => emptyValue(record.lastLoginIp) },
    ],
  },
  {
    title: '账号资料',
    fields: [
      { label: '创建时间', render: (record) => formatDate(record.createdAt) },
      { label: '更新时间', render: (record) => formatDate(record.updatedAt) },
      {
        label: '部门编号',
        render: (record) => emptyValue(record.departmentId),
      },
      { label: '备注', render: (record) => emptyValue(record.remark), span: 2 },
    ],
  },
]

export function AdminManagementPage() {
  const navigate = useNavigate()
  const currentAdmin = useAuthStore((state) => state.admin)
  const updateCurrentAdmin = useAuthStore((state) => state.updateAdmin)
  const clearSession = useAuthStore((state) => state.clearSession)

  const updateAdmin = async (id: number, values: Record<string, unknown>) => {
    if (id !== currentAdmin?.id) {
      return updateManagedAdmin(id, values as unknown as UpdateAdminRequest)
    }

    const request = toProfileRequest(values)
    const identityChanged = hasIdentityChanged(currentAdmin, request)
    const updated = await updateAdminProfile(request)
    updateCurrentAdmin(updated)
    if (identityChanged) {
      clearSession()
      navigate('/login', { replace: true })
    }
    return updated
  }

  return (
    <ManagementTablePage<AdminListItem, AdminDetail>
      title="管理员管理"
      entityName="管理员"
      description="管理后台管理员账号和状态"
      resourceKey="admin"
      columns={columns}
      detailSections={detailSections}
      operationMode="detail-delete"
      list={listAdmins}
      getDetail={getAdmin}
      create={(values) => createAdmin(values as unknown as CreateAdminRequest)}
      update={(id, values) =>
        updateAdmin(id, values as unknown as UpdateAdminRequest)
      }
      remove={removeAdmin}
      recordName={(record) => `${record.realName}（${record.username}）`}
      formValues={(record) => ({
        username: record.username,
        realName: record.realName,
        mobile: record.mobile ?? undefined,
        email: record.email ?? undefined,
        departmentId: record.departmentId ?? undefined,
        remark: record.remark ?? undefined,
      })}
      detailIdentity={{
        avatarUrl: (record) => record.avatarUrl,
        title: (record) => record.realName,
        subtitle: (record) => record.username,
        fallback: (record) => record.realName.trim().charAt(0) || '管',
      }}
      uploadAvatar={async (record, file) => {
        if (record.id !== currentAdmin?.id) {
          return uploadAdminAvatar(record.id, file)
        }
        const updated = await uploadCurrentAdminAvatar(file)
        updateCurrentAdmin(updated)
        return { ...record, avatarUrl: updated.avatarUrl }
      }}
      changePassword={(record, values) =>
        record.id === currentAdmin?.id
          ? changeAdminPassword(values as unknown as ResetPasswordRequest)
          : resetAdminPassword(
              record.id,
              values as unknown as ResetPasswordRequest,
            )
      }
      renderPasswordForm={(form) => renderPasswordForm(form)}
      onPasswordChanged={(record) => {
        if (record.id !== currentAdmin?.id) return
        clearSession()
        navigate('/login', { replace: true })
      }}
      refreshAfterUpdate={(record) => record.id !== currentAdmin?.id}
      renderCreateForm={(form) => renderAdminForm(form, 'create')}
      renderEditForm={(form, record) =>
        renderAdminForm(form, 'edit', record.id === currentAdmin?.id)
      }
      canDelete={(record) => record.isSuperAdmin !== 1}
      canEdit={(record) =>
        record.id === currentAdmin?.id || record.isSuperAdmin !== 1
      }
    />
  )
}

function renderAdminForm(
  _form: FormInstance,
  mode: 'create' | 'edit',
  isSelf = false,
) {
  return (
    <>
      <Form.Item
        label="登录账号"
        name="username"
        rules={[
          { required: true, message: '请输入登录账号' },
          { pattern: /^[A-Za-z0-9]+$/, message: '只能包含英文字母和数字' },
        ]}
      >
        <Input autoComplete="off" />
      </Form.Item>
      <Form.Item
        label="姓名"
        name="realName"
        rules={[{ required: true, message: '请输入姓名' }]}
      >
        <Input />
      </Form.Item>
      <Form.Item label="手机号" name="mobile">
        <Input />
      </Form.Item>
      <Form.Item
        label="邮箱"
        name="email"
        rules={[{ type: 'email', message: '邮箱格式不正确' }]}
      >
        <Input />
      </Form.Item>
      {!isSelf ? (
        <>
          <Form.Item label="部门编号" name="departmentId">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={3} />
          </Form.Item>
        </>
      ) : null}
      {mode === 'create' ? (
        <>
          <Form.Item
            label="初始密码"
            name="password"
            rules={[{ required: true, min: 8, message: '密码至少8位' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="确认密码"
            name="confirmPassword"
            dependencies={['password']}
            rules={[
              { required: true, message: '请确认密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  return !value || getFieldValue('password') === value
                    ? Promise.resolve()
                    : Promise.reject(new Error('两次密码不一致'))
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </>
      ) : null}
    </>
  )
}

function renderPasswordForm(_form: FormInstance) {
  return (
    <>
      <Form.Item
        label="新密码"
        name="newPassword"
        rules={[{ required: true, min: 8, message: '密码至少8位' }]}
      >
        <Input.Password autoComplete="new-password" />
      </Form.Item>
      <Form.Item
        label="确认密码"
        name="confirmPassword"
        dependencies={['newPassword']}
        rules={[
          { required: true, message: '请确认密码' },
          ({ getFieldValue }) => ({
            validator(_, value) {
              return !value || getFieldValue('newPassword') === value
                ? Promise.resolve()
                : Promise.reject(new Error('两次密码不一致'))
            },
          }),
        ]}
      >
        <Input.Password autoComplete="new-password" />
      </Form.Item>
    </>
  )
}

function toProfileRequest(
  values: Record<string, unknown>,
): UpdateAdminProfileRequest {
  return {
    username: String(values.username ?? ''),
    realName: String(values.realName ?? ''),
    mobile: optionalText(values.mobile),
    email: optionalText(values.email),
  }
}

function optionalText(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function hasIdentityChanged(
  current: { username: string; mobile: string | null; email: string | null },
  next: UpdateAdminProfileRequest,
) {
  return (
    current.username !== next.username ||
    current.mobile !== (next.mobile ?? null) ||
    current.email !== (next.email ?? null)
  )
}

function formatDate(value: string | null | undefined) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function emptyValue(value: unknown) {
  return value === null || value === undefined || value === ''
    ? '-'
    : String(value)
}
