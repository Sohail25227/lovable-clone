package com.aibuilder.lovableclone.generation.dto;

import java.time.Instant;

public record GeneratedFileResponseDto(
        Long id,
        String path,
        String content,
        Instant updatedAt
) {}
