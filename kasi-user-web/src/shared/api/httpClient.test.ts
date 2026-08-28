import { afterEach, describe, expect, it, vi } from 'vitest'
import '../ui/tdesignReact19Adapter'
import { MessagePlugin } from 'tdesign-react/es/message/index.js'
import { httpClient, isHandledRequestError } from './httpClient'

afterEach(() => {
  vi.restoreAllMocks()
})

function axiosError(status?: number) {
  return Object.assign(new Error(`Request failed with status code ${status}`), {
    isAxiosError: true,
    response: status === undefined ? undefined : { status },
  })
}

describe('httpClient error messages', () => {
  it('renders TDesign messages through the React 19 adapter', async () => {
    await expect(MessagePlugin.error('测试消息', 0)).resolves.toBeDefined()
    MessagePlugin.closeAll()
  })

  it('shows a friendly TDesign message for HTTP 503 errors', async () => {
    const messageError = vi
      .spyOn(MessagePlugin, 'error')
      .mockResolvedValue(undefined as never)
    const error = axiosError(503)

    await expect(
      httpClient.get('/test', {
        adapter: () => Promise.reject(error),
      }),
    ).rejects.toBe(error)

    expect(messageError).toHaveBeenCalledTimes(1)
    expect(messageError).toHaveBeenCalledWith('服务暂时不可用，请稍后重试')
    expect(isHandledRequestError(error)).toBe(true)
  })

  it('does not show a message for HTTP 401 errors', async () => {
    const messageError = vi
      .spyOn(MessagePlugin, 'error')
      .mockResolvedValue(undefined as never)
    const error = axiosError(401)

    await expect(
      httpClient.get('/test', {
        adapter: () => Promise.reject(error),
      }),
    ).rejects.toBe(error)

    expect(messageError).not.toHaveBeenCalled()
    expect(isHandledRequestError(error)).toBe(true)
  })
})
