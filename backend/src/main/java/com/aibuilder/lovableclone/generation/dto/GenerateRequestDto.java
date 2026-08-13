package com.aibuilder.lovableclone.generation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateRequestDto(

        @NotBlank(message = "Prompt is required")
        @Size(max = 2000, message = "Prompt must be at most 2000 characters")
        String prompt
) {}
