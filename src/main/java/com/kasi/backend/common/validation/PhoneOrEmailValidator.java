package com.kasi.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class PhoneOrEmailValidator implements ConstraintValidator<PhoneOrEmail, String> {

    private static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim();
        return normalized.length() <= 128
                && (MOBILE.matcher(normalized).matches() || EMAIL.matcher(normalized).matches());
    }
}
