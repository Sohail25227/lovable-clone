package com.aibuilder.lovableclone.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequestDto(

        @NotBlank(message = "Project name is required")
        @Size(max = 100, message = "Project name must be at most 100 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description
) {}
