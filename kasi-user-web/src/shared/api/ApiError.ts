export class ApiError extends Error {
  readonly code: number
  readonly status: number | undefined
  readonly retryable: boolean

  constructor(
    message: string,
    code: number,
    status?: number,
    retryable = false,
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.retryable = retryable
  }
}
