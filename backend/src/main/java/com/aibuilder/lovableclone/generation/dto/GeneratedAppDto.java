package com.aibuilder.lovableclone.generation.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record GeneratedAppDto(

        @JsonPropertyDescription("Short human friendly name for the generated app")
        String appName,

        @JsonPropertyDescription("One sentence describing what the app does")
        String summary,

        @JsonPropertyDescription("All files that make up the app")
        List<GeneratedFileDto> files
) {}
