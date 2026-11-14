
package com.sms.backend.security;

import com.sms.backend.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class AuditLogInterceptor
        implements HandlerInterceptor, WebMvcConfigurer {

    private final AuditLogService auditLogService;

    public AuditLogInterceptor(
            AuditLogService auditLogService
    ) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void addInterceptors(
            InterceptorRegistry registry
    ) {
        registry.addInterceptor(this)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {

        String method = request.getMethod()
                .toUpperCase(Locale.ROOT);

        String action = switch (method) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> null;
        };

        if (action == null) {
            return;
        }

        // Only record successful requests.
        int status = response.getStatus();

        if (exception != null ||
                status < 200 ||
                status >= 300) {
            return;
        }

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {
            return;
        }

        String path = request.getRequestURI();

        if (path == null) {
            return;
        }

        String contextPath = request.getContextPath();

        if (contextPath != null &&
                !contextPath.isEmpty() &&
                path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        // Do not log sensitive request bodies or query strings.
        // Only use a sanitized endpoint path.
        if (!path.matches(
                "^/api(?:/[A-Za-z0-9_-]+)*/*$"
        )) {
            return;
        }

        String entityType = entityTypeFromPath(path);
        Long entityId = entityIdFromPath(path);

        String details = method + " " + path +
                " | HTTP " + status;

        if (details.length() > 1000) {
            details = details.substring(0, 1000);
        }

        auditLogService.record(
                authentication,
                action,
                entityType,
                entityId,
                details
        );
    }

    private String entityTypeFromPath(String path) {

        String[] rawParts = path.split("/");
        List<String> parts = new ArrayList<>();

        for (String part : rawParts) {
            if (part.isBlank() ||
                    "api".equalsIgnoreCase(part) ||
                    "admin".equalsIgnoreCase(part) ||
                    "teacher".equalsIgnoreCase(part) ||
                    "guardian".equalsIgnoreCase(part) ||
                    "student".equalsIgnoreCase(part)) {
                continue;
            }

            if (part.matches("\\d+")) {
                break;
            }

            parts.add(part);
        }

        if (parts.isEmpty()) {
            return "SYSTEM";
        }

        int size = Math.min(parts.size(), 2);

        return String.join(
                        "_",
                        parts.subList(0, size)
                ).replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }

    private Long entityIdFromPath(String path) {

        String[] parts = path.split("/");

        for (String part : parts) {

            if (part.matches("\\d+")) {
                try {
                    return Long.parseLong(part);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        return null;
    }
}
