package com.inditex.suppliers.infrastructure.rest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a DUNS number is a 9-digit value in [100_000_000, 999_999_999].
 * Replaces the repeated {@code @Min(100_000_000L) @Max(999_999_999L)} pair
 * used across controller method parameters.
 */
@Min(100_000_000L)
@Max(999_999_999L)
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDuns {
    String message() default "DUNS must be a 9-digit number";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
