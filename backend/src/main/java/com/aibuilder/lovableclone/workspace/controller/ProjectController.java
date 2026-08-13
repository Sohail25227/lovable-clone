package com.aibuilder.lovableclone.workspace.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aibuilder.lovableclone.common.security.AuthUtil;
import com.aibuilder.lovableclone.workspace.dto.CreateProjectRequestDto;
import com.aibuilder.lovableclone.workspace.dto.ProjectResponseDto;
import com.aibuilder.lovableclone.workspace.service.ProjectService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final AuthUtil authUtil;

    public ProjectController(ProjectService projectService, AuthUtil authUtil) {
        this.projectService = projectService;
        this.authUtil = authUtil;
    }

    @PostMapping
    public ResponseEntity<ProjectResponseDto> createProject(
            @Valid @RequestBody CreateProjectRequestDto request) {

        Long userId = authUtil.getCurrentUserId();
        ProjectResponseDto response = projectService.createProject(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponseDto>> getMyProjects() {
        Long userId = authUtil.getCurrentUserId();
        return ResponseEntity.ok(projectService.getMyProjects(userId));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponseDto> getProjectById(@PathVariable Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        ProjectResponseDto projectResponseDto= projectService.getProjectById(projectId, userId);
        return ResponseEntity.ok(projectResponseDto);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        projectService.deleteProject(projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
