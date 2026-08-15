package com.aibuilder.lovableclone.generation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aibuilder.lovableclone.generation.entity.GeneratedFileEntity;

public interface GeneratedFileRepository extends JpaRepository<GeneratedFileEntity, Long> {

    List<GeneratedFileEntity> findByProjectIdOrderByPath(Long projectId);

    void deleteByProjectId(Long projectId);
}
