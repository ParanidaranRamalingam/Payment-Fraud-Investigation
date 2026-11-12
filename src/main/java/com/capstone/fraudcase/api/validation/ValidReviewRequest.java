package com.capstone.fraudcase.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Cross-field rules for a reviewer disposition (override needs final classification and route). */
@Documented
@Constraint(validatedBy = ValidReviewRequestValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidReviewRequest {

    String message() default "invalid review disposition";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
