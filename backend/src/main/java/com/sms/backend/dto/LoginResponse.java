
package com.sms.backend.dto;

public record LoginResponse(
        Long id,
        String fullName,
        String email,
        String role,
        String token,
        String message
) {}
