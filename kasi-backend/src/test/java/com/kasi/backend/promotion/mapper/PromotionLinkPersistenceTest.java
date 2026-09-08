package com.kasi.backend.promotion.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.promotion.enums.PromotionLinkStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("用户推广任务分页和总数只包含生成成功的链接")
    void userPageOnlyReturnsSuccessfulLinks() {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no=?", Long.class, PRIMARY_USER_NO);
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id,connection_name,partner_id,currency) VALUES (?,?,?,?)",
                providerId, "GoodShort", "partner-1", "USD");
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        jdbcTemplate.update("INSERT INTO provider_drama "
                        + "(connection_id,external_drama_id,title,language) VALUES (?,?,?,?)",
                connectionId, "book-1", "Drama", "ENGLISH");
        Long dramaId = jdbcTemplate.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id='book-1'", Long.class);

        insertLink(userId, providerId, connectionId, dramaId, "request-success", "SUCCESS",
                "CODE-1", "https://example.test/success");
        insertLink(userId, providerId, connectionId, dramaId, "request-pending", "PENDING", null, null);
        insertLink(userId, providerId, connectionId, dramaId, "request-failed", "FAILED", null, null);

        assertThat(linkMapper.findPageByUserId(userId, 0, 20))
                .extracting(PromotionLink::getRequestKey)
                .containsExactly("request-success");
        assertThat(linkMapper.countByUserId(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("推广任务只聚合口令、PID、短剧和用户均匹配的转化日报")
    void userPageAggregatesMatchingAnalyticalReports() {
        jdbcTemplate.execute("DELETE FROM promotion_analytical_report");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no=?", Long.class, PRIMARY_USER_NO);
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id,connection_name,partner_id,currency) VALUES (?,?,?,?)",
                providerId, "GoodShort", "partner-1", "USD");
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        jdbcTemplate.update("INSERT INTO provider_drama "
                        + "(connection_id,external_drama_id,title,language) VALUES (?,?,?,?)",
                connectionId, "book-1", "Drama", "ENGLISH");
        Long dramaId = jdbcTemplate.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id='book-1'", Long.class);
        jdbcTemplate.update("INSERT INTO promotion_link "
                        + "(user_id,provider_id,connection_id,drama_id,batch_no,media_type,link_variant,"
                        + "request_key,tracking_no,external_code,share_url,status) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,'SUCCESS')",
                userId, providerId, connectionId, dramaId, "batch-1", "TIKTOK", "LANDING",
                "request-1", "tracking-1", "CODE-1", "https://example.test/link");

        insertReport(LocalDate.of(2026, 9, 6), "partner-1", PRIMARY_USER_NO, "book-1", "CODE-1",
                1, 2, 3, 4, 5, 6, 7);
        insertReport(LocalDate.of(2026, 9, 7), "partner-1", PRIMARY_USER_NO, "book-1", "CODE-1",
                10, 20, 30, 40, 50, 60, 70);
        insertReport(LocalDate.of(2026, 9, 7), "other-partner", PRIMARY_USER_NO, "book-1", "CODE-1",
                100, 200, 300, 400, 500, 600, 700);
        insertReport(LocalDate.of(2026, 9, 7), "partner-1", MOBILE_USER_NO, "book-1", "CODE-1",
                1000, 2000, 3000, 4000, 5000, 6000, 7000);
        insertReport(LocalDate.of(2026, 9, 7), "partner-1", PRIMARY_USER_NO, "other-book", "CODE-1",
                10000, 20000, 30000, 40000, 50000, 60000, 70000);

        PromotionLink stored = linkMapper.findPageByUserId(userId, 0, 20).getFirst();

        assertThat(stored.getClickCount()).isEqualTo(11);
        assertThat(stored.getAttributedUserCount()).isEqualTo(22);
        assertThat(stored.getNewRegisteredUserCount()).isEqualTo(33);
        assertThat(stored.getNewPaidUserCount()).isEqualTo(44);
        assertThat(stored.getNewMemberUserCount()).isEqualTo(55);
        assertThat(stored.getPaidUserCount()).isEqualTo(66);
        assertThat(stored.getOrderCount()).isEqualTo(77);
    }

    @Test
    @DisplayName("管理员推广任务只显示成功链接并按四维归因聚合日报")
    void adminPageAggregatesMatchingAnalyticalReports() {
        jdbcTemplate.execute("DELETE FROM promotion_analytical_report");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no=?", Long.class, PRIMARY_USER_NO);
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id,connection_name,partner_id,currency) VALUES (?,?,?,?)",
                providerId, "GoodShort", "partner-1", "USD");
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        jdbcTemplate.update("INSERT INTO provider_drama "
                        + "(connection_id,external_drama_id,title,language) VALUES (?,?,?,?)",
                connectionId, "book-1", "Drama", "ENGLISH");
        Long dramaId = jdbcTemplate.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id='book-1'", Long.class);
        insertLink(userId, providerId, connectionId, dramaId, "request-success", "SUCCESS",
                "CODE-1", "https://example.test/success");
        insertLink(userId, providerId, connectionId, dramaId, "request-pending", "PENDING", null, null);
        insertReport(LocalDate.of(2026, 9, 7), "partner-1", PRIMARY_USER_NO, "book-1", "CODE-1",
                11, 12, 13, 14, 15, 16, 17);
        insertReport(LocalDate.of(2026, 9, 7), "wrong-pid", PRIMARY_USER_NO, "book-1", "CODE-1",
                100, 200, 300, 400, 500, 600, 700);

        var page = linkMapper.findAdminPage(PRIMARY_USER_NO, providerId, "CODE-1",
                "tracking-request-success", 0, 20);

        assertThat(page).hasSize(1);
        assertThat(page.getFirst().getUserNo()).isEqualTo(PRIMARY_USER_NO);
        assertThat(page.getFirst().getTrackingNo()).isEqualTo("tracking-request-success");
        assertThat(page.getFirst().getClickCount()).isEqualTo(11);
        assertThat(page.getFirst().getOrderCount()).isEqualTo(17);
        assertThat(linkMapper.countAdminPage(PRIMARY_USER_NO, providerId, "CODE-1",
                "tracking-request-success")).isEqualTo(1);
    }

    @Test
    @DisplayName("订单归因同时核对连接PID短剧用户编号和外部口令")
    void orderAttributionMatchesAllProviderDimensions() {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no=?", Long.class, PRIMARY_USER_NO);
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id,connection_name,partner_id,currency) VALUES (?,?,?,?)",
                providerId, "GoodShort", "partner-1", "USD");
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        jdbcTemplate.update("INSERT INTO provider_drama "
                        + "(connection_id,external_drama_id,title,language) VALUES (?,?,?,?)",
                connectionId, "book-1", "Drama", "ENGLISH");
        Long dramaId = jdbcTemplate.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id='book-1'", Long.class);
        insertLink(userId, providerId, connectionId, dramaId, "request-order", "SUCCESS",
                "CODE-1", "https://example.test/link");

        PromotionLink matched = linkMapper.findForOrderAttribution(
                connectionId, "partner-1", "book-1", PRIMARY_USER_NO, "CODE-1");

        assertThat(matched).isNotNull();
        assertThat(matched.getTrackingNo()).isEqualTo("tracking-request-order");
        assertThat(linkMapper.findForOrderAttribution(
                connectionId, "other-partner", "book-1", PRIMARY_USER_NO, "CODE-1")).isNull();
        assertThat(linkMapper.findForOrderAttribution(
                connectionId, "partner-1", "other-book", PRIMARY_USER_NO, "CODE-1")).isNull();
        assertThat(linkMapper.findForOrderAttribution(
                connectionId, "partner-1", "book-1", MOBILE_USER_NO, "CODE-1")).isNull();
        assertThat(linkMapper.findForOrderAttribution(
                connectionId, "partner-1", "book-1", PRIMARY_USER_NO, "OTHER-CODE")).isNull();
    }

    @Test
    @DisplayName("同一连接短剧用户和外部口令只能保存一条推广链接")
    void duplicateExternalCodeIdentityIsRejected() {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no=?", Long.class, PRIMARY_USER_NO);
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id,connection_name,partner_id,currency) VALUES (?,?,?,?)",
                providerId, "GoodShort", "partner-1", "USD");
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        jdbcTemplate.update("INSERT INTO provider_drama "
                        + "(connection_id,external_drama_id,title,language) VALUES (?,?,?,?)",
                connectionId, "book-1", "Drama", "ENGLISH");
        Long dramaId = jdbcTemplate.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id='book-1'", Long.class);
        insertLink(userId, providerId, connectionId, dramaId, "request-1", "SUCCESS",
                "CODE-1", "https://example.test/link");

        assertThatThrownBy(() -> insertLink(userId, providerId, connectionId, dramaId, "request-2", "SUCCESS",
                "CODE-1", "https://example.test/link"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private void insertReport(LocalDate date, String pid, String customParams, String bookId, String code,
                              long clicks, long attributed, long registered, long newPaid,
                              long newMembers, long paidUsers, long orders) {
        jdbcTemplate.update("INSERT INTO promotion_analytical_report "
                        + "(report_date,pid,custom_params,book_id,code,click_count,attributed_user_count,"
                        + "new_registered_user_count,new_paid_user_count,new_member_user_count,paid_user_count,"
                        + "order_count,order_amount) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                date, pid, customParams, bookId, code, clicks, attributed, registered, newPaid,
                newMembers, paidUsers, orders, "999.99");
    }

    private void insertLink(Long userId, Long providerId, Long connectionId, Long dramaId,
                            String requestKey, String status, String externalCode, String shareUrl) {
        jdbcTemplate.update("INSERT INTO promotion_link "
                        + "(user_id,provider_id,connection_id,drama_id,batch_no,media_type,link_variant,"
                        + "request_key,tracking_no,external_code,share_url,status) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                userId, providerId, connectionId, dramaId, "batch-1", "TIKTOK", "LANDING",
                requestKey, "tracking-" + requestKey, externalCode, shareUrl, status);
    }
}
