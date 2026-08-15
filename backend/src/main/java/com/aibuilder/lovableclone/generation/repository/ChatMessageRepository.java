package com.aibuilder.lovableclone.generation.repository;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.aibuilder.lovableclone.generation.entity.ChatMessageEntity;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    // Order id se, createdAt se nahi: do messages ek hi millisecond mein insert ho sakte
    // hain (user prompt aur uska jawab bhi), aur tab createdAt ka order tay nahi hota
    List<ChatMessageEntity> findByProjectIdOrderByIdAsc(Long projectId);

    // Prompt ke context ke liye sirf aakhri kuch messages chahiye. Limit caller deta hai
    // taaki bound service mein ek naam ke saath dikhe, query mein chhupa na rahe
    List<ChatMessageEntity> findByProjectIdOrderByIdDesc(Long projectId, Limit limit);
}
