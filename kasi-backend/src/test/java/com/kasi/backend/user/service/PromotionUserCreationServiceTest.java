package com.kasi.backend.user.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.generator.UserNumberGenerator;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import com.kasi.backend.user.service.impl.PromotionUserCreationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("推广用户创建服务")
class PromotionUserCreationServiceTest {

    @Mock private PromotionUserMapper promotionUserMapper;
    @Mock private UserNumberGenerator userNumberGenerator;
    @InjectMocks private PromotionUserCreationServiceImpl service;

    @Test
    @DisplayName("插入前写入随机编号并为未指定昵称补默认昵称")
    void createAssignsNumberBeforeInsert() {
        when(userNumberGenerator.generate()).thenReturn("583104726918");
        when(promotionUserMapper.insert(any(PromotionUser.class))).thenAnswer(invocation -> {
            PromotionUser user = invocation.getArgument(0);
            user.setId(42L);
            return 1;
        });
        PromotionUser user = new PromotionUser();
        user.setMobile("13600136000");

        service.create(user);

        assertThat(user.getUserNo()).isEqualTo("583104726918");
        assertThat(user.getNickname()).isEqualTo("用户583104726918");
        assertThat(user.getId()).isEqualTo(42L);
        verify(promotionUserMapper).insert(user);
    }

    @Test
    @DisplayName("随机编号唯一键冲突后重新生成并插入")
    void createRetriesWhenOnlyUserNumberCollides() {
        when(userNumberGenerator.generate()).thenReturn("100000000001", "200000000002");
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new DuplicateKeyException("Duplicate entry for key 'uk_user_no'");
            }
            PromotionUser user = invocation.getArgument(0);
            user.setId(43L);
            return 1;
        }).when(promotionUserMapper).insert(any(PromotionUser.class));
        when(promotionUserMapper.findByMobile("13600136000")).thenReturn(null);
        PromotionUser user = new PromotionUser();
        user.setMobile("13600136000");

        service.create(user);

        assertThat(user.getUserNo()).isEqualTo("200000000002");
        assertThat(user.getNickname()).isEqualTo("用户200000000002");
        verify(userNumberGenerator, times(2)).generate();
        verify(promotionUserMapper, times(2)).insert(user);
    }

    @Test
    @DisplayName("手机号唯一键冲突直接返回手机号重复错误")
    void createMapsContactConflictWithoutRetrying() {
        when(userNumberGenerator.generate()).thenReturn("583104726918");
        when(promotionUserMapper.insert(any(PromotionUser.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));
        PromotionUser existing = new PromotionUser();
        existing.setId(7L);
        when(promotionUserMapper.findByMobile("13600136000")).thenReturn(existing);
        PromotionUser user = new PromotionUser();
        user.setMobile("13600136000");

        assertThatThrownBy(() -> service.create(user))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(3006);
        verify(userNumberGenerator).generate();
        verify(promotionUserMapper).insert(user);
    }

    @Test
    @DisplayName("邮箱唯一键冲突直接返回邮箱重复错误")
    void createMapsEmailConflictWithoutRetrying() {
        when(userNumberGenerator.generate()).thenReturn("583104726918");
        when(promotionUserMapper.insert(any(PromotionUser.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));
        PromotionUser existing = new PromotionUser();
        existing.setId(8L);
        when(promotionUserMapper.findByEmail("user@example.com")).thenReturn(existing);
        PromotionUser user = new PromotionUser();
        user.setEmail("user@example.com");

        assertThatThrownBy(() -> service.create(user))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(3007);
        verify(userNumberGenerator).generate();
        verify(promotionUserMapper).insert(user);
    }

    @Test
    @DisplayName("连续三次编号冲突后创建失败")
    void createFailsAfterThreeNumberCollisions() {
        when(userNumberGenerator.generate())
                .thenReturn("100000000001", "200000000002", "300000000003");
        when(promotionUserMapper.insert(any(PromotionUser.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_user_no'"));
        when(promotionUserMapper.findByMobile("13600136000")).thenReturn(null);
        PromotionUser user = new PromotionUser();
        user.setMobile("13600136000");

        assertThatThrownBy(() -> service.create(user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3");
        verify(userNumberGenerator, times(3)).generate();
        verify(promotionUserMapper, times(3)).insert(user);
    }
}
