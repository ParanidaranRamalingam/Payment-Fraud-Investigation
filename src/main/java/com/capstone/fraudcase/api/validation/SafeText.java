package com.capstone.fraudcase.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Free text must not contain control characters or anything resembling a full card/account number.
 * Length bounds are expressed separately with {@code @Size}.
 */
@Documented
@Constraint(validatedBy = SafeTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeText {

    String message() default "must not contain control characters or unmasked card/account numbers";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
