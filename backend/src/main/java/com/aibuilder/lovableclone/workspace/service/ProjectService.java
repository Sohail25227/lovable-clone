package com.aibuilder.lovableclone.workspace.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibuilder.lovableclone.common.exception.ResourceNotFoundException;
import com.aibuilder.lovableclone.workspace.dto.CreateProjectRequestDto;
import com.aibuilder.lovableclone.workspace.dto.ProjectResponseDto;
import com.aibuilder.lovableclone.workspace.entity.ProjectEntity;
import com.aibuilder.lovableclone.workspace.repository.ProjectRepository;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectResponseDto createProject(CreateProjectRequestDto request, Long ownerId) {
        ProjectEntity project = new ProjectEntity();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOwnerId(ownerId);
        // status aur timestamps @PrePersist khud set karega

        ProjectEntity saved = projectRepository.save(project);
        return toDto(saved);
    }

    public List<ProjectResponseDto> getMyProjects(Long ownerId) {
        return projectRepository.findByOwnerId(ownerId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public ProjectResponseDto getProjectById(Long projectId, Long ownerId) {
        ProjectEntity project = projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        return toDto(project);
    }

    @Transactional
    public void deleteProject(Long projectId, Long ownerId) {
        ProjectEntity project = projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        projectRepository.delete(project);
    }

    // Entity → DTO
    private ProjectResponseDto toDto(ProjectEntity project) {
        return new ProjectResponseDto(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getPreviewUrl(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
