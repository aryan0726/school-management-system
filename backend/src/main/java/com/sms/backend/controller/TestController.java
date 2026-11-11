
package com.sms.backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/me")
    public Map<String, String> getCurrentUser(
            Authentication authentication) {

        return Map.of(
                "email", authentication.getName(),
                "message", "JWT authentication successful"
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public Map<String, String> adminOnly() {
        return Map.of(
                "message", "Welcome Admin"
        );
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/teacher")
    public Map<String, String> teacherOnly() {
        return Map.of(
                "message", "Welcome Teacher"
        );
    }

    @PreAuthorize("hasRole('GUARDIAN')")
    @GetMapping("/guardian")
    public Map<String, String> guardianOnly() {
        return Map.of(
                "message", "Welcome Guardian"
        );
    }
}
