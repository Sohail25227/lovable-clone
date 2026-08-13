package com.aibuilder.lovableclone.account.service;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.aibuilder.lovableclone.account.dto.AuthResponseDto;
import com.aibuilder.lovableclone.account.dto.LoginRequestDto;
import com.aibuilder.lovableclone.account.dto.SignupRequestDto;
import com.aibuilder.lovableclone.account.entity.UserEntity;
import com.aibuilder.lovableclone.account.repository.UserRepository;
import com.aibuilder.lovableclone.common.exception.InvalidCredentialsException;
import com.aibuilder.lovableclone.common.exception.ResourceAlreadyExistsException;
import com.aibuilder.lovableclone.common.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    //constructor injection
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponseDto signup(SignupRequestDto request){

        //1. Username already exist??
        userRepository.findByUsername(request.username()).ifPresent(existingUser -> {
            throw new ResourceAlreadyExistsException("Username already exists" );
        });

        // 2. Naya user banao — password HASH karke
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

         // 3. DB mein save — ab saved.getId() available hai
        UserEntity saved = userRepository.save(user);

         // 4. Token banao aur wapas bhejo
         return new AuthResponseDto(jwtService.generateToken(saved));

    }

    public AuthResponseDto login(LoginRequestDto request) {

            // 1. User nikalo — na mile to exception
        UserEntity user = userRepository.findByUsername(request.username())
                  .orElseThrow(()-> new InvalidCredentialsException("Invalid Username or Password"));

         // 2. Password verify karo
         if(!passwordEncoder.matches(request.password(),user.getPassword())){
            throw new InvalidCredentialsException("Invalid Username or Password");
         }

        // 3. Token do
        return new AuthResponseDto(jwtService.generateToken(user));
    }
}
