package com.aibuilder.lovableclone.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.aibuilder.lovableclone.common.exception.InvalidCredentialsException;

@Component
public class AuthUtil {

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new InvalidCredentialsException("No authenticated user found");
        }
        return userId;
    }
}
