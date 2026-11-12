package com.capstone.fraudcase.api.validation;

import com.capstone.fraudcase.domain.SensitiveDataGuard;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SafeTextValidator implements ConstraintValidator<SafeText, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return SensitiveDataGuard.isSafeFreeText(value);
    }
}
