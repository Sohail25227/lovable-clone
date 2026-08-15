package com.aibuilder.lovableclone.workspace.service;

import java.time.Duration;
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

    /**
     * Claim kitni der zinda maana jaye.
     *
     * Yeh sabse lambi jaayaz generation se bada hona chahiye, warna ek slow-par-zinda
     * generation doosri request cheen legi aur dono ek hi project ki files likhengi —
     * theek wahi cheez jise interlock rokta hai. Worst case: do validation attempts,
     * har ek mein SDK ki teen tries x 45s timeout, yaani ~4.5 minute. 10 minute usse
     * aaram se ooper hai, aur atke hue project ko theek hone mein itni hi der lagti hai.
     */
    private static final Duration GENERATION_LEASE = Duration.ofMinutes(10);

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
     * GENERATING pe daawa karta hai, sirf tab jab koi zinda claim maujood na ho.
     *
     * Optimistic locking writes ko safe banata hai par interlock nahi hai: do requests
     * apni-apni transaction mein row padh ke dono GENERATING likh sakti hain aur dono
     * model call kar sakti hain. Yeh check DB mein hota hai, isliye do mein se ek harta hai.
     *
     * Claim ke saath lease hai, kyunki iske bina ek crash project ko hamesha ke liye
     * bekaar kar deta tha: status GENERATING pe atka rehta aur har agli koshish 409 hoti.
     */
    @Transactional
    public void claimForGeneration(Long projectId, Long ownerId) {
        Instant now = Instant.now();

        int claimed = projectRepository.claimStatus(projectId, ownerId,
                ProjectStatusEnum.GENERATING, now, now.minus(GENERATION_LEASE));

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
