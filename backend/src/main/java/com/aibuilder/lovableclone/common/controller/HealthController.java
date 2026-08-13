package com.aibuilder.lovableclone.common.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * Pehla REST Controller.
 *
 * @RestController = @Controller + @ResponseBody
 * Matlab: methods ka return value JSON ban ke browser/Postman ko milta hai.
 *
 * @RequestMapping("/api") = is controller ke saare URLs /api se start
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * GET http://localhost:8080/api/health
     *
     * Browser ya Postman se open karo — JSON milega.
     * Ye prove karta hai: server chal raha hai.
     *
     */

    private final ChatClient chatClient;

    public HealthController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "lovable-clone",
                "message", "Day 1 — backend is alive!",
                "timestamp", Instant.now().toString()
        );
    }

    /**
     * GET http://localhost:8080/api/hello
     * Simple greeting — practice ke liye.
     */
    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of(
                "message", "Hello! AI App Builder backend ready."
        );
    }

    @PostMapping("/ai-test")
public Map<String, String> aiTest(@RequestBody Map<String, String> body) {
    String answer = chatClient.prompt()
            .user(body.get("message"))
            .call()
            .content();
    return Map.of("answer", answer);
}
}
