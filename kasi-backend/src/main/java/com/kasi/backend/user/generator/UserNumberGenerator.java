package com.kasi.backend.user.generator;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;

@Component
public class UserNumberGenerator {

    private static final long MIN_INCLUSIVE = 100_000_000_000L;
    private static final long MAX_EXCLUSIVE = 1_000_000_000_000L;

    private final RandomGenerator randomGenerator;

    public UserNumberGenerator() {
        this(new SecureRandom());
    }

    UserNumberGenerator(RandomGenerator randomGenerator) {
        this.randomGenerator = Objects.requireNonNull(randomGenerator, "randomGenerator");
    }

    public String generate() {
        return Long.toString(randomGenerator.nextLong(MIN_INCLUSIVE, MAX_EXCLUSIVE));
    }
}
