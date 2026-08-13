package com.aibuilder.lovableclone.account.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aibuilder.lovableclone.account.dto.AuthResponseDto;
import com.aibuilder.lovableclone.account.dto.LoginRequestDto;
import com.aibuilder.lovableclone.account.dto.SignupRequestDto;
import com.aibuilder.lovableclone.account.service.AuthService;

import jakarta.validation.Valid;
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService){
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponseDto> signup (@Valid @RequestBody SignupRequestDto request){
        AuthResponseDto response = authService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login (@Valid @RequestBody LoginRequestDto request){

        AuthResponseDto response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        // authentication.getPrincipal() → wahi userId jo filter ne daala tha
        // return kar do Map.of("userId", authentication.getPrincipal())
        return ResponseEntity.ok(Map.of("userId", authentication.getPrincipal()));
    }
}
