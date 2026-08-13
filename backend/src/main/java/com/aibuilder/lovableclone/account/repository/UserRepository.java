package com.aibuilder.lovableclone.account.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aibuilder.lovableclone.account.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
}
