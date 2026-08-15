package com.aibuilder.lovableclone.common.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    // Liveness check. Public, kyunki iska kaam hi bina auth batana hai ki app zinda hai
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "lovable-clone",
                "timestamp", Instant.now().toString()
        );
    }
}
