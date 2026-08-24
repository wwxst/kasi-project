package com.kasi.backend.auth.service;

import com.kasi.backend.auth.entity.PasswordResetTokenReservation;
import com.kasi.backend.auth.service.impl.PasswordResetTokenServiceImpl;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.AuthStateUnavailableException;
import com.kasi.backend.common.enums.SubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class PasswordResetTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private PasswordResetTokenService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PasswordResetTokenServiceImpl(redisTemplate, 600);
    }

    @Test
    @DisplayName("重置Token只能从READY原子预占为PROCESSING")
    void reserveTokenMovesReadyToProcessing() {
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString())).thenReturn("USER:7");

        PasswordResetTokenReservation reservation = service.reserveToken("raw-token");

        assertEquals(7L, reservation.userId());
        assertEquals(SubjectType.USER, reservation.subjectType());
    }

    @Test
    @DisplayName("PROCESSING状态不能再次预占")
    void processingTokenCannotBeReservedAgain() {
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.reserveToken("raw-token"));
    }

    @Test
    @DisplayName("Redis异常必须原样向上传播")
    void redisFailureFailsClosed() {
        RedisConnectionFailureException redisFailure =
                new RedisConnectionFailureException("redis unavailable");
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString()))
                .thenThrow(redisFailure);

        AuthStateUnavailableException thrown = assertThrows(
                AuthStateUnavailableException.class,
                () -> service.reserveToken("raw-token"));

        assertSame(redisFailure, thrown.getCause());
    }
}
