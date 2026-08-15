package com.aibuilder.lovableclone.generation.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibuilder.lovableclone.generation.dto.GeneratedAppDto;
import com.aibuilder.lovableclone.generation.dto.GeneratedFileResponseDto;
import com.aibuilder.lovableclone.generation.entity.GeneratedFileEntity;
import com.aibuilder.lovableclone.generation.repository.GeneratedFileRepository;
import com.aibuilder.lovableclone.workspace.service.ProjectService;

@Service
@Transactional(readOnly = true)
public class GeneratedFileService {

    private final GeneratedFileRepository generatedFileRepository;
    private final ProjectService projectService;

    public GeneratedFileService(GeneratedFileRepository generatedFileRepository,
                                ProjectService projectService) {
        this.generatedFileRepository = generatedFileRepository;
        this.projectService = projectService;
    }

    // Ek project ki saari files ko naye set se badal deta hai
    @Transactional
    public void replaceFiles(Long projectId, GeneratedAppDto app) {
        generatedFileRepository.deleteByProjectId(projectId);
        generatedFileRepository.flush();   // delete ko insert se pehle DB tak bhejo

        List<GeneratedFileEntity> entities = app.files().stream()
                .map(file -> {
                    GeneratedFileEntity entity = new GeneratedFileEntity();
                    entity.setProjectId(projectId);
                    entity.setPath(file.path());
                    entity.setContent(file.content());
                    return entity;
                })
                .toList();

        generatedFileRepository.saveAll(entities);
    }

    public List<GeneratedFileResponseDto> getFiles(Long projectId, Long ownerId) {
        // generated_files mein owner_id nahi hai, isliye ownership project se verify hoti hai.
        // Yeh read ka single entry point hai — check yahin hona chahiye
        projectService.getProjectById(projectId, ownerId);

        return generatedFileRepository.findByProjectIdOrderByPath(projectId)
                .stream()
                .map(f -> new GeneratedFileResponseDto(
                        f.getId(), f.getPath(), f.getContent(), f.getUpdatedAt()))
                .toList();
    }
}
