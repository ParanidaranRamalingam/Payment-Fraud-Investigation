package com.capstone.fraudcase.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Value must be a tokenized ({@code cus_...}) or masked ({@code ****1234}) reference; never a full
 * card or account number. {@code null} is considered valid; combine with {@code @NotBlank} when required.
 */
@Documented
@Constraint(validatedBy = MaskedReferenceValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaskedReference {

    String message() default "must be a masked or tokenized reference, never a full card/account number";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
