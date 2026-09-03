import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type { SmsConfig, UpdateSmsConfigRequest } from './smsConfigTypes'
export async function getSmsConfig() {
  const r = await httpClient.get<ApiResponse<SmsConfig>>(
    '/api/admin/system/sms-config',
  )
  return unwrapApiResponse(r.data)
}
export async function updateSmsConfig(body: UpdateSmsConfigRequest) {
  const r = await httpClient.put<ApiResponse<SmsConfig>>(
    '/api/admin/system/sms-config',
    body,
  )
  return unwrapApiResponse(r.data)
}
