package com.aibuilder.lovableclone.common.dto;

import java.time.Instant;

public record ApiErrorDto(
        int status,
        String error,
        String message,
        Instant timestamp
) {}
