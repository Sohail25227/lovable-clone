package com.aibuilder.lovableclone.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.aibuilder.lovableclone.account.entity.UserEntity;
import com.aibuilder.lovableclone.common.exception.InvalidCredentialsException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final String SCOPE_CLAIM = "scope";
    private static final String PREVIEW_SCOPE = "preview";
    private static final String OWNER_CLAIM = "ownerId";

    // Chhota TTL, kyunki preview token URL mein khula rehta hai — history, logs, referrer
    public static final Duration PREVIEW_TOKEN_TTL = Duration.ofMinutes(30);

    @Value("${jwt.secret-key}")
    private String secretKey;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // User se token banao
    public String generateToken(UserEntity user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 hours
                .signWith(getSecretKey())
                .compact();
    }

    // Token se userId nikalo (verify bhi hota hai)
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);

        // Preview token ka subject projectId hota hai. Use API access ke liye chalne dena
        // matlab projectId ko userId maan lena — seedhi privilege confusion
        if (PREVIEW_SCOPE.equals(claims.get(SCOPE_CLAIM))) {
            throw new InvalidCredentialsException("A preview token cannot authenticate API calls");
        }

        return Long.valueOf(claims.getSubject());
    }

    // Preview browser se khulta hai, jahan Authorization header bheja nahi ja sakta.
    // Isliye capability token: ek project, chhoti umr, sirf padhne ke liye
    public String generatePreviewToken(Long projectId, Long ownerId) {
        return Jwts.builder()
                .subject(projectId.toString())
                .claim(SCOPE_CLAIM, PREVIEW_SCOPE)
                .claim(OWNER_CLAIM, ownerId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + PREVIEW_TOKEN_TTL.toMillis()))
                .signWith(getSecretKey())
                .compact();
    }

    public PreviewGrant getPreviewGrant(String token) {
        Claims claims = parseClaims(token);

        // Access token ko preview ke roop mein chalne dena uska subject (userId) ko
        // projectId bana deta — ulti taraf ki wahi confusion
        if (!PREVIEW_SCOPE.equals(claims.get(SCOPE_CLAIM))) {
            throw new InvalidCredentialsException("Not a preview token");
        }

        return new PreviewGrant(
                Long.valueOf(claims.getSubject()),
                claims.get(OWNER_CLAIM, Number.class).longValue());
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            // Malformed, galat signature ya expired — teenon client ki galti hain.
            // Bina iske yeh generic handler tak jaake 500 ban jaate the
            throw new InvalidCredentialsException("Invalid or expired token");
        }
    }
}
