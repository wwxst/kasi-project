package com.kasi.backend.user.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("推广用户编号生成器")
class UserNumberGeneratorTest {

    private static final long MIN_INCLUSIVE = 100_000_000_000L;
    private static final long MAX_EXCLUSIVE = 1_000_000_000_000L;

    @Test
    @DisplayName("最小边界生成首位非零的12位纯数字编号")
    void generateWithMinimumValueReturnsFirstValidNumber() {
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextLong(MIN_INCLUSIVE, MAX_EXCLUSIVE)).thenReturn(MIN_INCLUSIVE);

        String userNo = new UserNumberGenerator(random).generate();

        assertEquals("100000000000", userNo);
        assertTrue(userNo.matches("[1-9][0-9]{11}"));
        verify(random).nextLong(MIN_INCLUSIVE, MAX_EXCLUSIVE);
    }

    @Test
    @DisplayName("最大边界生成最后一个有效12位编号")
    void generateWithMaximumValueReturnsLastValidNumber() {
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextLong(MIN_INCLUSIVE, MAX_EXCLUSIVE))
                .thenReturn(MAX_EXCLUSIVE - 1);

        String userNo = new UserNumberGenerator(random).generate();

        assertEquals("999999999999", userNo);
        assertTrue(userNo.matches("[1-9][0-9]{11}"));
    }

    @Test
    @DisplayName("可控随机源按固定顺序生成可复现编号")
    void generateWithControlledSourceReturnsRepeatableSequence() {
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextLong(MIN_INCLUSIVE, MAX_EXCLUSIVE))
                .thenReturn(583_104_726_918L, 731_000_000_042L);
        UserNumberGenerator generator = new UserNumberGenerator(random);

        assertEquals("583104726918", generator.generate());
        assertEquals("731000000042", generator.generate());
        verify(random, times(2)).nextLong(MIN_INCLUSIVE, MAX_EXCLUSIVE);
    }
}
