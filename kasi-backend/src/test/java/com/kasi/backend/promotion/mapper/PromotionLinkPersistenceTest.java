package com.kasi.backend.promotion.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.promotion.enums.PromotionLinkStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionLinkPersistenceTest extends BaseAuthTest {
    @Autowired
    private PromotionLinkMapper linkMapper;

    @Test
    @DisplayName("推广链接持久化保存批次、平台和变体字段")
    void storesDualVariantFields() {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM promotion_user WHERE mobile='13800138000'", Long.class);
        Long providerId = jdbcTemplate.queryForObject("SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection (provider_id,connection_name,currency) VALUES (?,?,?)", providerId, "GoodShort", "USD");
        Long connectionId = jdbcTemplate.queryForObject("SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        jdbcTemplate.update("INSERT INTO provider_drama (connection_id,external_drama_id,title,language) VALUES (?,?,?,?)", connectionId, "book-1", "Drama", "ENGLISH");
        Long dramaId = jdbcTemplate.queryForObject("SELECT id FROM provider_drama WHERE external_drama_id='book-1'", Long.class);

        PromotionLink link = new PromotionLink();
        link.setUserId(userId); link.setProviderId(providerId); link.setConnectionId(connectionId); link.setDramaId(dramaId);
        link.setBatchNo("batch-1"); link.setMediaType("TIKTOK"); link.setLinkVariant("ONELINK");
        link.setRequestKey("request-1"); link.setTrackingNo("tracking-1");
        link.setStatus(PromotionLinkStatus.PENDING);
        assertThat(linkMapper.insert(link)).isEqualTo(1);

        PromotionLink stored = linkMapper.findByUserAndRequestKey(userId, "request-1", "TIKTOK", "ONELINK");
        assertThat(stored.getBatchNo()).isEqualTo("batch-1");
        assertThat(stored.getLinkVariant()).isEqualTo("ONELINK");
        assertThat(linkMapper.findBatchByUserAndRequestKey(userId, "request-1")).hasSize(1);
    }
}
