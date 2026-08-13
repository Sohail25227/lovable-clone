package com.aibuilder.lovableclone.workspace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aibuilder.lovableclone.workspace.entity.ProjectEntity;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long>{
    List<ProjectEntity> findByOwnerId(Long ownerId);
    Optional<ProjectEntity> findByIdAndOwnerId(Long id, Long ownerId);
    long countByOwnerId(Long ownerId);

}
