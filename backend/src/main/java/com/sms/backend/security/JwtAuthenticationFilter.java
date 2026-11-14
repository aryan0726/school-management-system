
package com.sms.backend.security;

import com.sms.backend.service.JwtService;
import com.sms.backend.service.CustomUserDetailsService;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader =
                request.getHeader("Authorization");

        if (authHeader == null ||
                !authHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();

        // Reject empty Bearer tokens.
        if (token.isEmpty()) {
            reject(response);
            return;
        }

        try {
            // Validates JWT signature and expiration.
            String email = jwtService.extractEmail(token);

            if (email == null || email.isBlank()) {
                reject(response);
                return;
            }

            UserDetails userDetails =
                    userDetailsService.loadUserByUsername(email);

            // Critical security check:
            // Disabled accounts must not use existing tokens.
            if (!userDetails.isEnabled() ||
                    !userDetails.isAccountNonExpired() ||
                    !userDetails.isAccountNonLocked() ||
                    !userDetails.isCredentialsNonExpired()) {

                reject(response);
                return;
            }

            // Prevent an already authenticated principal
            // from being replaced unexpectedly.
            Authentication existing =
                    SecurityContextHolder.getContext()
                            .getAuthentication();

            if (existing == null) {

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            }

        } catch (
                JwtException |
                IllegalArgumentException |
                UsernameNotFoundException exception
        ) {
            reject(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response)
            throws IOException {

        SecurityContextHolder.clearContext();

        response.setStatus(
                HttpServletResponse.SC_UNAUTHORIZED
        );

        response.setContentType(
                "application/json"
        );

        response.getWriter().write(
                "{\"message\":\"Invalid, expired, or inactive authentication token\"}"
        );
    }
}
