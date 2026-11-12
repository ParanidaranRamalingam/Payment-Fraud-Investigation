package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.api.validation.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CaseMessageRequest(
        @NotBlank @Size(max = 2000) @SafeText
        String message) {
}
