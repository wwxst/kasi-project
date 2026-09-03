package com.kasi.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MobileValidator implements ConstraintValidator<Mobile, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && value.trim().matches("^1[3-9]\\d{9}$");
    }
}
