package com.aibuilder.lovableclone.generation.dto;

import java.time.Instant;

public record PreviewTokenResponseDto(
        String previewUrl,
        Instant expiresAt
) {}
