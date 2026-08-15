package com.aibuilder.lovableclone.generation.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// Ek project ki baatcheet, append-only. Generated files "abhi kya hai" batati hain,
// yeh "kya maanga gaya tha" batata hai — follow-up ke liye dono chahiye
@Entity
@Table(
        name = "chat_messages",
        indexes = @Index(name = "idx_chat_message_project", columnList = "project_id")
)
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cross-module reference id se, JPA relation se nahi — service split ke baad bhi tikega
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRoleEnum role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // updatedAt nahi hai, aur updatable = false jaan-boojh ke: jo history badli ja sake
    // woh history nahi hai. Sudhaar naya message hota hai, purane ka edit nahi
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public MessageRoleEnum getRole() {
        return role;
    }

    public void setRole(MessageRoleEnum role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
