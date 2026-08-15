package com.aibuilder.lovableclone.generation.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aibuilder.lovableclone.common.security.AuthUtil;
import com.aibuilder.lovableclone.generation.dto.GenerateRequestDto;
import com.aibuilder.lovableclone.generation.dto.GeneratedAppDto;
import com.aibuilder.lovableclone.generation.dto.GeneratedFileResponseDto;
import com.aibuilder.lovableclone.generation.service.CodeGenerationService;
import com.aibuilder.lovableclone.generation.service.GeneratedFileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
public class GenerationController {

    private final CodeGenerationService codeGenerationService;
    private final GeneratedFileService generatedFileService;
    private final AuthUtil authUtil;

    public GenerationController(CodeGenerationService codeGenerationService,
                                GeneratedFileService generatedFileService,
                                AuthUtil authUtil) {
        this.codeGenerationService = codeGenerationService;
        this.generatedFileService = generatedFileService;
        this.authUtil = authUtil;
    }

    @PostMapping("/{projectId}/generate")
    public ResponseEntity<GeneratedAppDto> generate(
            @PathVariable Long projectId,
            @Valid @RequestBody GenerateRequestDto request) {

        Long userId = authUtil.getCurrentUserId();
        GeneratedAppDto response = codeGenerationService.generateForProject(
                projectId, userId, request.prompt());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{projectId}/files")
    public ResponseEntity<List<GeneratedFileResponseDto>> getFiles(@PathVariable Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        return ResponseEntity.ok(generatedFileService.getFiles(projectId, userId));
    }
}
