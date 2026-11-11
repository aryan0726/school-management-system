
package com.sms.backend.controller;

import com.sms.backend.dto.LoginRequest;
import com.sms.backend.dto.LoginResponse;
import com.sms.backend.entity.User;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.JwtService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService) {

        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request) {

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );

            User user = userRepository.findByEmail(request.email())
                    .orElseThrow();

            String token = jwtService.generateToken(user.getEmail());

            return ResponseEntity.ok(
                    new LoginResponse(
                            user.getId(),
                            user.getFullName(),
                            user.getEmail(),
                            user.getRole().getName(),
                            token,
                            "Login successful"
                    )
            );

        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401)
                    .body("Invalid email or password");
        }
    }
}
