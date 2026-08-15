package com.aibuilder.lovableclone.generation.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibuilder.lovableclone.generation.dto.ChatMessageResponseDto;
import com.aibuilder.lovableclone.generation.entity.ChatMessageEntity;
import com.aibuilder.lovableclone.generation.entity.MessageRoleEnum;
import com.aibuilder.lovableclone.generation.repository.ChatMessageRepository;
import com.aibuilder.lovableclone.workspace.service.ProjectService;

@Service
@Transactional(readOnly = true)
public class ChatMessageService {

    // Poori history bhejna context ko bina fayde ke bada karta hai. Pichhli baaton ka kaam
    // sirf woh intent yaad rakhna hai jo code mein dikhta nahi ("minimal rakho", "neela accent"),
    // aur uske liye aakhri kuch turns kaafi hain
    private static final Limit CONTEXT_WINDOW = Limit.of(6);

    private final ChatMessageRepository chatMessageRepository;
    private final ProjectService projectService;

    public ChatMessageService(ChatMessageRepository chatMessageRepository,
                              ProjectService projectService) {
        this.chatMessageRepository = chatMessageRepository;
        this.projectService = projectService;
    }

    @Transactional
    public void record(Long projectId, MessageRoleEnum role, String content) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setProjectId(projectId);
        message.setRole(role);
        message.setContent(content);

        chatMessageRepository.save(message);
    }

    public List<ChatMessageResponseDto> getHistory(Long projectId, Long ownerId) {
        // chat_messages mein owner_id nahi hai, isliye ownership project se verify hoti hai.
        // Yeh read ka single entry point hai — check yahin hona chahiye
        projectService.getProjectById(projectId, ownerId);

        return chatMessageRepository.findByProjectIdOrderByIdAsc(projectId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Prompt banane ke liye aakhri kuch messages, purane se naye ke order mein.
     *
     * Ownership dobara check nahi hoti: yeh sirf generation ke andar se chalti hai, jahan
     * claim pehle hi atomically owner verify kar chuki hoti hai. Ek extra query ka faayda nahi
     */
    public List<ChatMessageResponseDto> getRecentForContext(Long projectId) {
        // Query newest-first hai, kyunki limit ko aakhri N chahiye. Model ko chronological
        List<ChatMessageResponseDto> chronological = new ArrayList<>(chatMessageRepository
                .findByProjectIdOrderByIdDesc(projectId, CONTEXT_WINDOW)
                .stream()
                .map(this::toDto)
                .toList());

        Collections.reverse(chronological);
        return chronological;
    }

    private ChatMessageResponseDto toDto(ChatMessageEntity message) {
        return new ChatMessageResponseDto(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt());
    }
}
