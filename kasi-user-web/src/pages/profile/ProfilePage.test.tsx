import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  changePassword,
  getCurrentUser,
  updateUserProfile,
  uploadUserAvatar,
} from '../../features/auth/authApi'
import { useAuthStore } from '../../features/auth/authStore'
import ProfilePage from './ProfilePage'

vi.mock('tdesign-react', async () => {
  const actual =
    await vi.importActual<typeof import('tdesign-react')>('tdesign-react')
  return {
    ...actual,
    MessagePlugin: { success: vi.fn(), error: vi.fn() },
  }
})

vi.mock('../../features/auth/authApi', () => ({
  changePassword: vi.fn(),
  getCurrentUser: vi.fn(),
  updateUserProfile: vi.fn(),
  uploadUserAvatar: vi.fn(),
}))

const currentUser = {
  userNo: '701804677763',
  nickname: '卡司用户77763',
  realName: '张三',
  wechatId: 'wechat-zhangsan',
  studentType: 1,
  mobile: '13600136000',
  email: 'user@example.com',
  avatarUrl: null,
  status: 1,
  lastLoginAt: '2026-09-03T10:20:30',
  lastLoginIp: '127.0.0.1',
  createdAt: '2026-08-20T08:00:00',
}

beforeEach(() => {
  useAuthStore.getState().setSession('test-token')
  vi.mocked(getCurrentUser).mockResolvedValue(currentUser)
})

afterEach(() => {
  cleanup()
  useAuthStore.getState().clearSession()
  vi.clearAllMocks()
})

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const view = render(
    <MemoryRouter initialEntries={['/workspace/profile']}>
      <QueryClientProvider client={queryClient}>
        <Routes>
          <Route
            path="/workspace/profile"
            element={<ProfilePage title="个人中心" />}
          />
          <Route path="/login" element={<div>登录页</div>} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  )
  return { queryClient, ...view }
}

describe('ProfilePage', () => {
  it('shows the current user profile as read-only information', async () => {
    renderPage()

    expect(
      await screen.findByRole('heading', { name: '个人资料' }),
    ).toBeTruthy()
    expect(screen.getAllByText('卡司用户77763')).toHaveLength(1)
    expect(screen.getByText('701804677763')).toBeTruthy()
    expect(screen.getByText('张三')).toBeTruthy()
    const userSummary = screen.getByTestId('user-summary')
    expect(userSummary.textContent).toContain(
      '用户昵称：卡司用户77763学员类型：基础学员',
    )
    expect(userSummary.querySelectorAll('span')).toHaveLength(2)
    expect(screen.queryByText('学员类型：', { selector: 'dt' })).toBeNull()
    expect(screen.queryByDisplayValue('基础学员')).toBeNull()
    expect(screen.getByText('13600136000')).toBeTruthy()
    expect(screen.getByText('user@example.com')).toBeTruthy()
    expect(screen.queryByDisplayValue('13600136000')).toBeNull()
    expect(screen.queryByDisplayValue('user@example.com')).toBeNull()
  })

  it('shows the basic-user label for studentType 0', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue({
      ...currentUser,
      studentType: 0,
    })
    renderPage()

    expect(await screen.findByText('基础用户')).toBeTruthy()
  })

  it('edits the nickname beside the avatar', async () => {
    const user = userEvent.setup()
    const updatedUser = { ...currentUser, nickname: '新昵称' }
    vi.mocked(updateUserProfile).mockResolvedValue(updatedUser)
    const { queryClient } = renderPage()

    await user.click(await screen.findByRole('button', { name: '编辑昵称' }))
    const nicknameInput = screen.getByLabelText('用户昵称')
    expect((nicknameInput as HTMLInputElement).value).toBe('卡司用户77763')
    await user.clear(nicknameInput)
    await user.type(nicknameInput, '新昵称')
    await user.keyboard('{Enter}')

    await waitFor(() =>
      expect(updateUserProfile).toHaveBeenCalledWith({
        nickname: '新昵称',
        realName: '张三',
        wechatId: 'wechat-zhangsan',
        mobile: '13600136000',
        email: 'user@example.com',
      }),
    )
    expect(queryClient.getQueryData(['auth', 'me'])).toEqual(updatedUser)
    expect(screen.getByRole('button', { name: '编辑昵称' })).toBeTruthy()
  })

  it('removes real-name verification and presents only pill profile tabs', async () => {
    renderPage()

    expect(
      await screen.findByRole('heading', { name: '个人资料' }),
    ).toBeTruthy()
    expect(screen.queryByText('实名认证')).toBeNull()

    const tabs = screen.getAllByRole('button', {
      name: /^(基本信息|安全设置)$/,
    })
    expect(tabs).toHaveLength(2)
    expect(tabs[0].getAttribute('aria-pressed')).toBe('true')
    expect(tabs[1].getAttribute('aria-pressed')).toBe('false')

    await userEvent.setup().click(tabs[1])
    expect(screen.getByTestId('security-panel').className).not.toContain(
      'profileSection',
    )
  })

  it('edits the four contact fields from the basic information panel', async () => {
    const user = userEvent.setup()
    const updatedUser = {
      ...currentUser,
      realName: '李四',
      wechatId: 'lisi-wechat',
      mobile: '13900139000',
      email: 'lisi@example.com',
    }
    vi.mocked(updateUserProfile).mockResolvedValue(updatedUser)
    const { queryClient } = renderPage()

    const editButton = await screen.findByRole('button', { name: '编辑资料' })
    expect(editButton.closest('header')).toBeNull()
    await user.click(editButton)
    const realNameInput = screen.getByLabelText('真实姓名')
    const wechatInput = screen.getByLabelText('微信号')
    const mobileInput = screen.getByLabelText('手机号码')
    const emailInput = screen.getByLabelText('电子邮箱')
    expect(realNameInput.closest('dd')).not.toBeNull()
    expect(screen.queryByLabelText('学员类型')).toBeNull()
    expect(screen.queryByDisplayValue('基础学员')).toBeNull()
    await user.clear(realNameInput)
    await user.type(realNameInput, '李四')
    await user.clear(wechatInput)
    await user.type(wechatInput, 'lisi-wechat')
    await user.clear(mobileInput)
    await user.type(mobileInput, '13900139000')
    await user.clear(emailInput)
    await user.type(emailInput, 'lisi@example.com')
    await user.click(screen.getByRole('button', { name: '保存资料' }))

    await waitFor(() =>
      expect(updateUserProfile).toHaveBeenCalledWith({
        nickname: '卡司用户77763',
        realName: '李四',
        wechatId: 'lisi-wechat',
        mobile: '13900139000',
        email: 'lisi@example.com',
      }),
    )
    expect(queryClient.getQueryData(['auth', 'me'])).toEqual(updatedUser)
    expect(screen.getAllByText('卡司用户77763')).toHaveLength(1)
    expect(screen.getByText('李四')).toBeTruthy()
    expect(screen.getByText('lisi-wechat')).toBeTruthy()
    expect(screen.getByText('13900139000')).toBeTruthy()
    expect(screen.getByText('lisi@example.com')).toBeTruthy()
  })

  it('uploads an avatar and refreshes the shared user cache', async () => {
    const user = userEvent.setup()
    const updatedUser = {
      ...currentUser,
      avatarUrl: '/uploads/user-avatars/avatar.png',
    }
    vi.mocked(uploadUserAvatar).mockResolvedValue(updatedUser)
    const { queryClient } = renderPage()
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })

    await user.upload(await screen.findByLabelText('上传头像'), file)

    await waitFor(() => expect(uploadUserAvatar).toHaveBeenCalledWith(file))
    expect(queryClient.getQueryData(['auth', 'me'])).toEqual(updatedUser)
    expect(screen.getByAltText('卡司用户77763').getAttribute('src')).toBe(
      '/uploads/user-avatars/avatar.png',
    )
  })

  it('clears the session and returns to login after changing the password', async () => {
    const user = userEvent.setup()
    vi.mocked(changePassword).mockResolvedValue(undefined)
    renderPage()

    await screen.findByRole('button', { name: '安全设置' })
    await user.click(screen.getByRole('button', { name: '安全设置' }))
    await screen.findByRole('heading', { name: '修改密码' })
    await user.type(
      screen.getByPlaceholderText('请输入当前密码'),
      'old-password',
    )
    await user.type(screen.getByPlaceholderText('请输入新密码'), 'new-password')
    await user.type(
      screen.getByPlaceholderText('请再次输入新密码'),
      'new-password',
    )
    await user.click(screen.getByRole('button', { name: '修改密码' }))

    await waitFor(() =>
      expect(changePassword).toHaveBeenCalledWith({
        oldPassword: 'old-password',
        newPassword: 'new-password',
        confirmPassword: 'new-password',
      }),
    )
    expect(await screen.findByText('登录页')).toBeTruthy()
    expect(useAuthStore.getState().accessToken).toBeNull()
  })
})
