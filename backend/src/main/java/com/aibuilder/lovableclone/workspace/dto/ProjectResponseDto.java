package com.aibuilder.lovableclone.workspace.dto;

import java.time.Instant;

import com.aibuilder.lovableclone.workspace.entity.ProjectStatusEnum;

public record ProjectResponseDto(
    Long id,
    String name,
    String description,
    ProjectStatusEnum status,
    String previewUrl,
    Instant createdAt,
    Instant updatedAt
) {}
