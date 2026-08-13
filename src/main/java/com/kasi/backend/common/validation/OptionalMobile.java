package com.kasi.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OptionalMobileValidator.class)
public @interface OptionalMobile {
    String message() default "请输入有效的手机号";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
