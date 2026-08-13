package com.aibuilder.lovableclone.generation.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record GeneratedFileDto(

        @JsonPropertyDescription("File name such as index.html, app.jsx or styles.css")
        String path,

        @JsonPropertyDescription("Complete file content, ready to run with no placeholders or TODOs")
        String content
) {}
