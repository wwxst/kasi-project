package com.kasi.backend.drama.service;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.dto.UpdateCommissionRuleDTO;
import com.kasi.backend.drama.mapper.ProviderCommissionRuleHistoryMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderCommissionRuleHistoryServiceTest extends BaseAuthTest {
    @Autowired
    private ProviderCommissionRuleService ruleService;
    @Autowired
    private ProviderCommissionRuleHistoryMapper historyMapper;
    @Autowired
    private ShortDramaProviderMapper providerMapper;

    @Test
    @DisplayName("创建和修改当前规则分别生成不可变的新费率快照")
    void createAndUpdateAppendRuleHistory() {
        Long providerId = providerMapper.findByCode("GOODSHORT").getId();
        Long operatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_admin_user WHERE username=?", Long.class, ADMIN_USERNAME);

        var created = ruleService.create(operatorId, providerId,
                createRequest("10", "2", "80", "3", "70"));
        var first = historyMapper.findLatestByProviderId(providerId);
        assertThat(first.getRuleId()).isEqualTo(created.getId());
        assertThat(first.getDownstreamCommissionRate()).isEqualByComparingTo("0.7000000000");

        ruleService.update(operatorId, providerId, created.getId(),
                updateRequest("12", "2", "80", "3", "65"));

        assertThat(historyMapper.findAllByProviderId(providerId)).hasSize(2);
        assertThat(historyMapper.findLatestByProviderId(providerId).getChannelFeeRate())
                .isEqualByComparingTo("0.1200000000");
        assertThat(first.getChannelFeeRate()).isEqualByComparingTo("0.1000000000");
    }

    private CreateCommissionRuleDTO createRequest(String channel, String principalFee,
                                                   String principalCommission, String downstreamFee,
                                                   String downstreamCommission) {
        CreateCommissionRuleDTO request = new CreateCommissionRuleDTO();
        request.setChannelFeeRate(new BigDecimal(channel));
        request.setPrincipalFeeRate(new BigDecimal(principalFee));
        request.setPrincipalCommissionRate(new BigDecimal(principalCommission));
        request.setDownstreamFeeRate(new BigDecimal(downstreamFee));
        request.setDownstreamCommissionRate(new BigDecimal(downstreamCommission));
        return request;
    }

    private UpdateCommissionRuleDTO updateRequest(String channel, String principalFee,
                                                   String principalCommission, String downstreamFee,
                                                   String downstreamCommission) {
        UpdateCommissionRuleDTO request = new UpdateCommissionRuleDTO();
        request.setChannelFeeRate(new BigDecimal(channel));
        request.setPrincipalFeeRate(new BigDecimal(principalFee));
        request.setPrincipalCommissionRate(new BigDecimal(principalCommission));
        request.setDownstreamFeeRate(new BigDecimal(downstreamFee));
        request.setDownstreamCommissionRate(new BigDecimal(downstreamCommission));
        return request;
    }
}
