import { Form, Input, Tag } from 'antd'
import type { ProColumns } from '@ant-design/pro-components'
import type { FormInstance } from 'antd/es/form'
import {
  ManagementTablePage,
  StatusTag,
} from '../../features/management/ManagementTablePage'
import type { DetailSection } from '../../features/management/ManagementTablePage'
import {
  createUser,
  getUser,
  listUsers,
  removeUser,
  updateUser,
  updateUserStatus,
} from '../../features/management/userManagementApi'
import type {
  UserDetail,
  UserListItem,
  CreateUserRequest,
  UpdateUserRequest,
} from '../../features/management/managementTypes'

const columns: ProColumns<UserListItem>[] = [
  {
    title: '用户ID',
    dataIndex: 'userNo',
    fixed: 'left',
    width: 100,
  },
  { title: '昵称', dataIndex: 'nickname', width: 150 },
  {
    title: '手机号',
    dataIndex: 'mobile',
    width: 140,
    renderText: emptyValue,
  },
  {
    title: '邮箱',
    dataIndex: 'email',
    width: 210,
    renderText: emptyValue,
  },
  {
    title: '注册来源',
    dataIndex: 'registerSource',
    width: 130,
    renderText: formatRegisterSource,
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 90,
    render: (_, record) => <StatusTag status={record.status} />,
  },
]

const detailSections: DetailSection<UserDetail>[] = [
  {
    title: '基本信息',
    editable: true,
    fields: [
      { label: '用户ID', dataIndex: 'userNo' },
      { label: '昵称', dataIndex: 'nickname' },
      {
        label: '姓名',
        dataIndex: 'realName',
        render: (record) => emptyValue(record.realName),
      },
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
        label: '注册来源',
        render: (record) => formatRegisterSource(record.registerSource),
      },
      {
        label: '状态',
        render: (record) => <StatusTag status={record.status} />,
      },
    ],
  },
  {
    title: '账号资料',
    fields: [
      { label: '登录时间', render: (record) => formatDate(record.lastLoginAt) },
      { label: '登录 IP', render: (record) => emptyValue(record.lastLoginIp) },
      { label: '创建时间', render: (record) => formatDate(record.createdAt) },
      { label: '更新时间', render: (record) => formatDate(record.updatedAt) },
      { label: '备注', render: (record) => emptyValue(record.remark), span: 2 },
    ],
  },
]

export function UserManagementPage() {
  return (
    <ManagementTablePage<UserListItem, UserDetail>
      title="用户管理"
      entityName="用户"
      description="管理推广用户资料和状态"
      resourceKey="user"
      columns={columns}
      detailSections={detailSections}
      operationMode="detail-more"
      list={listUsers}
      getDetail={getUser}
      create={(values) => createUser(values as unknown as CreateUserRequest)}
      update={(id, values) =>
        updateUser(id, values as unknown as UpdateUserRequest)
      }
      updateStatus={(id, status) => updateUserStatus(id, { status })}
      remove={removeUser}
      recordName={(record) => `${record.nickname}（${record.userNo}）`}
      formValues={(record) => ({
        mobile: record.mobile ?? undefined,
        email: record.email ?? undefined,
        nickname: record.nickname,
        realName: record.realName ?? undefined,
        avatarUrl: record.avatarUrl ?? undefined,
        remark: record.remark ?? undefined,
      })}
      detailIdentity={{
        avatarUrl: (record) => record.avatarUrl,
        title: (record) => record.nickname,
        subtitle: (record) => record.userNo,
        fallback: (record) => record.nickname.trim().charAt(0) || '用',
      }}
      renderCreateForm={(form) => renderUserForm(form, 'create')}
      renderEditForm={(form) => renderUserForm(form, 'edit')}
    />
  )
}

function renderUserForm(_form: FormInstance, mode: 'create' | 'edit') {
  return (
    <>
      <Form.Item
        label="手机号"
        name="mobile"
        dependencies={['email']}
        rules={[
          { pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确' },
          ({ getFieldValue }) => ({
            validator(_, value) {
              return value || getFieldValue('email')
                ? Promise.resolve()
                : Promise.reject(new Error('手机号或邮箱至少填写一项'))
            },
          }),
        ]}
      >
        <Input />
      </Form.Item>
      <Form.Item
        label="邮箱"
        name="email"
        rules={[{ type: 'email', message: '邮箱格式不正确' }]}
      >
        <Input />
      </Form.Item>
      <Form.Item
        label="昵称"
        name="nickname"
        rules={[{ required: true, message: '请输入昵称' }]}
      >
        <Input />
      </Form.Item>
      <Form.Item label="姓名" name="realName">
        <Input />
      </Form.Item>
      <Form.Item label="头像地址" name="avatarUrl">
        <Input />
      </Form.Item>
      <Form.Item label="备注" name="remark">
        <Input.TextArea rows={3} />
      </Form.Item>
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
          <Form.Item extra="手机号和邮箱至少填写一项">
            <Tag color="blue">联系方式至少一项</Tag>
          </Form.Item>
        </>
      ) : null}
    </>
  )
}

function formatDate(value: string | null | undefined) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function formatRegisterSource(value: string | null | undefined) {
  if (!value) return '-'
  return value === 'ADMIN' ? '管理员创建' : value
}

function emptyValue(value: unknown) {
  return value === null || value === undefined || value === ''
    ? '-'
    : String(value)
}
