package com.aibuilder.lovableclone.workspace.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibuilder.lovableclone.common.exception.GenerationInProgressException;
import com.aibuilder.lovableclone.common.exception.ResourceNotFoundException;
import com.aibuilder.lovableclone.workspace.dto.CreateProjectRequestDto;
import com.aibuilder.lovableclone.workspace.dto.ProjectResponseDto;
import com.aibuilder.lovableclone.workspace.entity.ProjectEntity;
import com.aibuilder.lovableclone.workspace.entity.ProjectStatusEnum;
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

    @Transactional
    public void updateStatus(Long projectId, Long ownerId, ProjectStatusEnum status) {
        ProjectEntity project = projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        project.setStatus(status);
        // save() ki zarurat nahi — managed entity hai, commit pe dirty checking likh degi
    }

    /**
     * GENERATING pe daawa karta hai, sirf tab jab project abhi GENERATING na ho.
     *
     * Optimistic locking writes ko safe banata hai par interlock nahi hai: do requests
     * apni-apni transaction mein row padh ke dono GENERATING likh sakti hain aur dono
     * model call kar sakti hain. Yeh check DB mein hota hai, isliye do mein se ek harta hai.
     */
    @Transactional
    public void claimForGeneration(Long projectId, Long ownerId) {
        int claimed = projectRepository.compareAndSetStatus(
                projectId, ownerId, ProjectStatusEnum.GENERATING, Instant.now());

        if (claimed == 1) {
            return;
        }

        // Zero rows ke do matlab hain: project maujood nahi/tumhara nahi, ya already
        // GENERATING hai. Inhe alag karne ke liye ek read, warna 404 ko 409 bata denge
        projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        throw new GenerationInProgressException(
                "A generation is already running for this project");
    }
}
