package com.capstone.fraudcase.api.validation;

import com.capstone.fraudcase.domain.SensitiveDataGuard;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaskedReferenceValidator implements ConstraintValidator<MaskedReference, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || SensitiveDataGuard.isMaskedOrTokenizedReference(value);
    }
}
