package com.kasi.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class OptionalMobileValidator implements ConstraintValidator<OptionalMobile, String> {
    private static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim();
        return normalized.length() <= 32 && MOBILE.matcher(normalized).matches();
    }
}
