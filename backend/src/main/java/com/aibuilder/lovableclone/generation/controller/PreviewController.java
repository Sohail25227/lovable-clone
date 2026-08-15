package com.aibuilder.lovableclone.generation.controller;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aibuilder.lovableclone.common.exception.ResourceNotFoundException;
import com.aibuilder.lovableclone.common.security.AuthUtil;
import com.aibuilder.lovableclone.common.security.JwtService;
import com.aibuilder.lovableclone.common.security.PreviewGrant;
import com.aibuilder.lovableclone.generation.dto.GeneratedFileResponseDto;
import com.aibuilder.lovableclone.generation.dto.PreviewTokenResponseDto;
import com.aibuilder.lovableclone.generation.service.GeneratedFileService;
import com.aibuilder.lovableclone.workspace.service.ProjectService;

@RestController
public class PreviewController {

    private static final String INDEX = "index.html";

    private final String csp;

    private final JwtService jwtService;
    private final GeneratedFileService generatedFileService;
    private final ProjectService projectService;
    private final AuthUtil authUtil;

    public PreviewController(JwtService jwtService,
                             GeneratedFileService generatedFileService,
                             ProjectService projectService,
                             AuthUtil authUtil,
                             @Value("${app.frontend-origin}") String frontendOrigin) {
        this.csp = buildCsp(frontendOrigin);
        this.jwtService = jwtService;
        this.generatedFileService = generatedFileService;
        this.projectService = projectService;
        this.authUtil = authUtil;
    }

    // Generated code hamari hi origin se chalta hai, isliye scripts ko un CDNs tak
    // seemit karte hain jo contract maangta hai. unsafe-eval hataya nahi ja sakta —
    // Babel standalone JSX ko browser mein eval se hi compile karta hai
    private static String buildCsp(String frontendOrigin) {
        return String.join("; ",
                "default-src 'none'",
                "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://unpkg.com https://cdn.tailwindcss.com",
                "style-src 'self' 'unsafe-inline'",
                "img-src 'self' data: https:",
                "font-src https:",
                "connect-src 'self'",
                // Yeh hataye gaye X-Frame-Options ki jagah leta hai. Frontend preview ko
                // embed kar sake, par koi doosri website user ki app frame na kar sake
                "frame-ancestors 'self' " + frontendOrigin);
    }

    @PostMapping("/api/projects/{projectId}/preview-token")
    public ResponseEntity<PreviewTokenResponseDto> createPreviewToken(@PathVariable Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        projectService.getProjectById(projectId, userId);   // ownership, warna 404

        String token = jwtService.generatePreviewToken(projectId, userId);

        return ResponseEntity.ok(new PreviewTokenResponseDto(
                "/api/preview/" + token + "/",
                Instant.now().plus(JwtService.PREVIEW_TOKEN_TTL)));
    }

    // Trailing slash ke bina index.html ke andar ka src="app.jsx" ek level upar
    // resolve hota hai aur 404 deta hai, isliye redirect
    @GetMapping("/api/preview/{token}")
    public ResponseEntity<Void> redirectToRoot(@PathVariable String token) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/api/preview/" + token + "/"))
                .build();
    }

    @GetMapping({"/api/preview/{token}/", "/api/preview/{token}/{path}"})
    public ResponseEntity<String> serve(@PathVariable String token,
                                        @PathVariable(required = false) String path) {

        // Token hi permission hai — browser koi header nahi bhej sakta
        PreviewGrant grant = jwtService.getPreviewGrant(token);

        String wanted = (path == null || path.isBlank()) ? INDEX : path;

        // Lookup DB se exact path pe hota hai, filesystem chhua hi nahi jata,
        // isliye ".." wala traversal yahan possible nahi
        String content = generatedFileService.getFiles(grant.projectId(), grant.ownerId())
                .stream()
                .filter(file -> file.path().equals(wanted))
                .map(GeneratedFileResponseDto::content)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No such file in preview: " + wanted));

        return ResponseEntity.ok()
                .contentType(contentTypeFor(wanted))
                .header("Content-Security-Policy", csp)
                .header("X-Content-Type-Options", "nosniff")
                // Regenerate purani files badal deta hai, isliye kuch cache nahi hota
                .cacheControl(CacheControl.noStore())
                .body(content);
    }

    private MediaType contentTypeFor(String path) {
        List<String> parts = List.of(path.split("\\."));
        String extension = parts.size() > 1 ? parts.get(parts.size() - 1) : "";

        return switch (extension) {
            case "html" -> MediaType.valueOf("text/html;charset=UTF-8");
            case "css" -> MediaType.valueOf("text/css;charset=UTF-8");
            case "jsx", "js" -> MediaType.valueOf("application/javascript;charset=UTF-8");
            default -> MediaType.valueOf("text/plain;charset=UTF-8");
        };
    }
}
