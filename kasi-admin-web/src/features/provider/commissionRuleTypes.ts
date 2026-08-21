export type CommissionRuleStatus = 'PENDING' | 'ACTIVE' | 'ENDED'

export interface CommissionRule {
  id: number
  providerId: number
  channelFeeRate: number
  principalFeeRate: number
  principalCommissionRate: number
  downstreamFeeRate: number
  downstreamCommissionRate: number
  effectiveFrom: string
  effectiveTo: string | null
  status: CommissionRuleStatus
}

export interface CommissionRuleRequest {
  channelFeeRate: number
  principalFeeRate: number
  principalCommissionRate: number
  downstreamFeeRate: number
  downstreamCommissionRate: number
  effectiveFrom: string
  effectiveTo: string | null
}

export interface EndCommissionRuleRequest {
  effectiveTo: string
}
