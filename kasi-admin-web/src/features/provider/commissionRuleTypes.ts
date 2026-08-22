export interface CommissionRule {
  id: number
  providerId: number
  channelFeeRate: number
  principalFeeRate: number
  principalCommissionRate: number
  downstreamFeeRate: number
  downstreamCommissionRate: number
}

export interface CommissionRuleRequest {
  channelFeeRate: number
  principalFeeRate: number
  principalCommissionRate: number
  downstreamFeeRate: number
  downstreamCommissionRate: number
}
