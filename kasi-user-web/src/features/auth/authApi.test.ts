import { afterEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../shared/api/httpClient'
import { changePassword, updateUserProfile, uploadUserAvatar } from './authApi'

const updatedUser = {
  userNo: '701804677763',
  nickname: '新昵称',
  realName: '张三',
  mobile: '13600136000',
  email: 'user@example.com',
  avatarUrl: '/uploads/user-avatars/avatar.png',
  status: 1,
  lastLoginAt: '2026-09-03T10:20:30',
  lastLoginIp: '127.0.0.1',
  createdAt: '2026-08-20T08:00:00',
}

afterEach(() => vi.restoreAllMocks())

describe('changePassword', () => {
  it('updates the current user password through the user auth endpoint', async () => {
    const put = vi.spyOn(httpClient, 'put').mockResolvedValue({
      data: { code: 0, message: '密码修改成功', data: null },
    })

    await changePassword({
      oldPassword: 'old-password',
      newPassword: 'new-password',
      confirmPassword: 'new-password',
    })

    expect(put).toHaveBeenCalledWith('/api/user/auth/password', {
      oldPassword: 'old-password',
      newPassword: 'new-password',
      confirmPassword: 'new-password',
    })
  })
})

describe('user profile', () => {
  it('updates the editable profile fields through the user auth endpoint', async () => {
    const put = vi.spyOn(httpClient, 'put').mockResolvedValue({
      data: { code: 0, message: '成功', data: updatedUser },
    })

    await expect(
      updateUserProfile({ nickname: '新昵称', realName: '张三' }),
    ).resolves.toEqual(updatedUser)
    expect(put).toHaveBeenCalledWith('/api/user/auth/profile', {
      nickname: '新昵称',
      realName: '张三',
    })
  })

  it('uploads the avatar as multipart form data', async () => {
    const put = vi.spyOn(httpClient, 'put').mockResolvedValue({
      data: { code: 0, message: '成功', data: updatedUser },
    })
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })

    await expect(uploadUserAvatar(file)).resolves.toEqual(updatedUser)
    const formData = put.mock.calls[0]?.[1]
    expect(put).toHaveBeenCalledWith(
      '/api/user/auth/avatar',
      expect.any(FormData),
    )
    expect((formData as FormData).get('file')).toBe(file)
  })
})
