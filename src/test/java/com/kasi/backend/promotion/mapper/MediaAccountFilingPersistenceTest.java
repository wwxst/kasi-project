package com.kasi.backend.promotion.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("媒体账号与报备持久层")
class MediaAccountFilingPersistenceTest extends BaseAuthTest {

    @Autowired
    private PromotionMediaAccountMapper mediaAccountMapper;

    @Autowired
    private ProviderMediaFilingMapper filingMapper;

    @Test
    @DisplayName("同一媒体平台账号只能绑定一次")
    void mediaIdentityIsGloballyUnique() {
        Long firstUserId = userId(PRIMARY_USER_NO);
        Long secondUserId = userId(MOBILE_USER_NO);
        PromotionMediaAccount first = mediaAccount(firstUserId, MediaType.TIKTOK, "creator-1");
        assertThat(mediaAccountMapper.insert(first)).isEqualTo(1);

        PromotionMediaAccount duplicate = mediaAccount(secondUserId, MediaType.TIKTOK, "creator-1");
        assertThatThrownBy(() -> mediaAccountMapper.insert(duplicate))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(mediaAccountMapper.findByIdentity(MediaType.TIKTOK, "creator-1").getUserId())
                .isEqualTo(firstUserId);
    }

    @Test
    @DisplayName("媒体账号资料版本和启停状态可以更新")
    void mediaAccountDetailsAdvanceVersion() {
        Long userId = userId(PRIMARY_USER_NO);
        PromotionMediaAccount account = mediaAccount(userId, MediaType.YOUTUBE, "channel-1");
        mediaAccountMapper.insert(account);
        account.setAccountName("Updated");
        account.setAccountLink("https://youtube.com/@updated");
        account.setDataVersion(2);
        assertThat(mediaAccountMapper.updateDetails(account)).isEqualTo(1);
        assertThat(mediaAccountMapper.updateStatus(account.getId(), 0)).isEqualTo(1);

        PromotionMediaAccount stored = mediaAccountMapper.findOwnedById(account.getId(), userId);
        assertThat(stored.getAccountName()).isEqualTo("Updated");
        assertThat(stored.getDataVersion()).isEqualTo(2);
        assertThat(stored.getStatus()).isZero();
    }

    @Test
    @DisplayName("同一接入账号和媒体账号只保留一条报备")
    void filingIsUniquePerConnectionAndMediaAccount() {
        PromotionMediaAccount account = mediaAccount(userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-2");
        mediaAccountMapper.insert(account);
        Long connectionId = insertConnection();

        ProviderMediaFiling filing = pendingFiling(connectionId, account.getId(), 1);
        assertThat(filingMapper.insert(filing)).isEqualTo(1);
        assertThatThrownBy(() -> filingMapper.insert(pendingFiling(connectionId, account.getId(), 1)))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(filingMapper.findByConnectionAndMedia(connectionId, account.getId()).getStatus())
                .isEqualTo(FilingStatus.PENDING);
        assertThat(filingMapper.findByMediaAccountId(account.getId())).hasSize(1);
    }

    @Test
    @DisplayName("报备任务租约和资料版本条件可以保护写回")
    void filingTaskLeaseAndVersionGuard() {
        PromotionMediaAccount account = mediaAccount(userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-3");
        mediaAccountMapper.insert(account);
        Long connectionId = insertConnection();
        ProviderMediaFiling filing = pendingFiling(connectionId, account.getId(), 1);
        filingMapper.insert(filing);

        LocalDateTime now = LocalDateTime.now();
        assertThat(filingMapper.claimLease(filing.getId(), "worker-a", now, now.plusMinutes(2))).isEqualTo(1);
        assertThat(filingMapper.claimLease(filing.getId(), "worker-b", now, now.plusMinutes(2))).isZero();
        assertThat(filingMapper.enqueue(filing.getId(), FilingStatus.PENDING, FilingAction.SUBMIT, 2,
                now.plusMinutes(1))).isZero();
        assertThat(filingMapper.enqueue(filing.getId(), FilingStatus.PENDING, FilingAction.SUBMIT, 1,
                now.plusMinutes(1))).isEqualTo(1);
    }

    private Long insertConnection() {
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, base_url, partner_id, api_key_ciphertext, currency) "
                        + "VALUES (?, 'GoodShort', 'https://api.test/creek', 'partner-1', 'v1:cipher', 'USD')",
                providerId);
        return jdbcTemplate.queryForObject("SELECT id FROM short_drama_connection WHERE provider_id = ?",
                Long.class, providerId);
    }

    private Long userId(String userNo) {
        return jdbcTemplate.queryForObject("SELECT id FROM promotion_user WHERE user_no = ?", Long.class, userNo);
    }

    private PromotionMediaAccount mediaAccount(Long userId, MediaType type, String id) {
        PromotionMediaAccount account = new PromotionMediaAccount();
        account.setUserId(userId);
        account.setMediaType(type);
        account.setExternalAccountId(id);
        account.setAccountName("Creator");
        account.setAccountLink("https://www.tiktok.com/@" + id);
        account.setStatus(1);
        account.setDataVersion(1);
        return account;
    }

    private ProviderMediaFiling pendingFiling(Long connectionId, Long mediaAccountId, int version) {
        ProviderMediaFiling filing = new ProviderMediaFiling();
        filing.setConnectionId(connectionId);
        filing.setMediaAccountId(mediaAccountId);
        filing.setStatus(FilingStatus.PENDING);
        filing.setNextAction(FilingAction.SUBMIT);
        filing.setNextActionAt(LocalDateTime.now());
        filing.setTaskDataVersion(version);
        return filing;
    }
}
