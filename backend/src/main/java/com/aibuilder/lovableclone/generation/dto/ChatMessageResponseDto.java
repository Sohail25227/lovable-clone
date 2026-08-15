package com.aibuilder.lovableclone.generation.dto;

import java.time.Instant;

import com.aibuilder.lovableclone.generation.entity.MessageRoleEnum;

public record ChatMessageResponseDto(
        Long id,
        MessageRoleEnum role,
        String content,
        Instant createdAt
) {}
