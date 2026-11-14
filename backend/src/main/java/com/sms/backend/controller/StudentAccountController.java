
package com.sms.backend.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/student-accounts")
@PreAuthorize("hasRole('ADMIN')")
public class StudentAccountController {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final PasswordEncoder passwordEncoder;

    public StudentAccountController(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder
    ) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.passwordEncoder = passwordEncoder;
    }

    public record CreateAccountRequest(
            Long studentId,
            String email,
            String password
    ) {}

    public record ResetPasswordRequest(
            String newPassword
    ) {}

    public record UpdateEmailRequest(
            String email
    ) {}

    // =============== LIST STUDENT ACCOUNTS ===============

    @GetMapping
    public List<Map<String, Object>> listAccounts() {
        return jdbc.queryForList("""
            SELECT
                s.id AS studentId,
                s.full_name AS studentName,
                s.admission_number AS admissionNumber,
                s.active AS studentActive,
                u.id AS userId,
                u.email,
                u.active AS accountActive,
                u.created_at AS createdAt
            FROM students s
            LEFT JOIN users u ON u.id = s.user_id
            ORDER BY s.id DESC
            """);
    }

    // =============== ACCOUNT DETAILS ===============

    @GetMapping("/{studentId}")
    public Map<String, Object> getAccount(
            @PathVariable Long studentId
    ) {
        List<Map<String, Object>> rows =
                jdbc.queryForList("""
                    SELECT
                        s.id AS studentId,
                        s.full_name AS studentName,
                        s.admission_number AS admissionNumber,
                        s.date_of_birth AS dateOfBirth,
                        s.gender,
                        s.active AS studentActive,
                        u.id AS userId,
                        u.email,
                        u.active AS accountActive,
                        u.created_at AS createdAt
                    FROM students s
                    LEFT JOIN users u ON u.id = s.user_id
                    WHERE s.id = ?
                    """, studentId);

        if (rows.isEmpty()) {
            throw notFound("Student not found.");
        }

        return new LinkedHashMap<>(rows.get(0));
    }

    // =============== CREATE LOGIN ACCOUNT ===============

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createAccount(
            @RequestBody CreateAccountRequest request
    ) {
        if (request == null || request.studentId() == null) {
            throw badRequest("Student ID is required.");
        }

        String email = validateEmail(request.email());
        validatePassword(request.password());

        // Lock student row to prevent concurrent account creation.
        List<Map<String, Object>> students =
                jdbc.queryForList("""
                    SELECT id, full_name, active, user_id
                    FROM students
                    WHERE id = ?
                    FOR UPDATE
                    """, request.studentId());

        if (students.isEmpty()) {
            throw notFound("Student not found.");
        }

        Map<String, Object> student = students.get(0);

        if (student.get("user_id") != null) {
            throw conflict(
                    "This student already has a login account."
            );
        }

        if (!Boolean.TRUE.equals(student.get("active"))) {
            throw badRequest(
                    "Cannot create an account for an inactive student."
            );
        }

        if (emailExists(email, null)) {
            throw conflict("Email is already registered.");
        }

        Long roleId = studentRoleId();

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue(
                                "fullName",
                                student.get("full_name")
                        )
                        .addValue("email", email)
                        .addValue(
                                "password",
                                passwordEncoder.encode(
                                        request.password()
                                )
                        )
                        .addValue("roleId", roleId);

        GeneratedKeyHolder holder = new GeneratedKeyHolder();

        try {
            namedJdbc.update("""
                INSERT INTO users (
                    full_name,
                    email,
                    password,
                    role_id,
                    active
                )
                VALUES (
                    :fullName,
                    :email,
                    :password,
                    :roleId,
                    TRUE
                )
                """,
                    params,
                    holder,
                    new String[]{"id"});

            Number generatedKey = holder.getKey();

            if (generatedKey == null) {
                throw new IllegalStateException(
                        "User ID was not generated."
                );
            }

            Long userId = generatedKey.longValue();

            int updated = jdbc.update("""
                UPDATE students
                SET user_id = ?
                WHERE id = ?
                  AND user_id IS NULL
                """, userId, request.studentId());

            if (updated != 1) {
                throw conflict(
                        "Student account was linked concurrently."
                );
            }

        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "Email or student account already exists."
            );
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(getAccount(request.studentId()));
    }

    // =============== UPDATE LOGIN EMAIL ===============

    @PutMapping("/{studentId}/email")
    @Transactional
    public Map<String, Object> updateEmail(
            @PathVariable Long studentId,
            @RequestBody UpdateEmailRequest request
    ) {
        if (request == null) {
            throw badRequest("Email is required.");
        }

        String email = validateEmail(request.email());
        Long userId = linkedUserId(studentId);

        if (emailExists(email, userId)) {
            throw conflict("Email already belongs to another user.");
        }

        try {
            jdbc.update("""
                UPDATE users
                SET email = ?
                WHERE id = ?
                """, email, userId);

        } catch (DataIntegrityViolationException exception) {
            throw conflict("Email already exists.");
        }

        return getAccount(studentId);
    }

    // =============== RESET PASSWORD ===============

    @PatchMapping("/{studentId}/password")
    @Transactional
    public Map<String, String> resetPassword(
            @PathVariable Long studentId,
            @RequestBody ResetPasswordRequest request
    ) {
        if (request == null) {
            throw badRequest("New password is required.");
        }

        validatePassword(request.newPassword());
        Long userId = linkedUserId(studentId);

        jdbc.update("""
            UPDATE users
            SET password = ?
            WHERE id = ?
            """,
                passwordEncoder.encode(request.newPassword()),
                userId
        );

        return Map.of(
                "message",
                "Student password updated successfully"
        );
    }

    // =============== ACTIVATE ACCOUNT ===============

    @PatchMapping("/{studentId}/activate")
    @Transactional
    public Map<String, Object> activateAccount(
            @PathVariable Long studentId
    ) {
        Long userId = linkedUserId(studentId);

        Boolean studentActive = jdbc.queryForObject("""
            SELECT active FROM students WHERE id = ?
            """, Boolean.class, studentId);

        if (!Boolean.TRUE.equals(studentActive)) {
            throw conflict(
                    "Cannot activate login for an inactive student."
            );
        }

        jdbc.update("""
            UPDATE users
            SET active = TRUE
            WHERE id = ?
            """, userId);

        return getAccount(studentId);
    }

    // =============== DEACTIVATE ACCOUNT ===============

    @PatchMapping("/{studentId}/deactivate")
    @Transactional
    public Map<String, Object> deactivateAccount(
            @PathVariable Long studentId
    ) {
        Long userId = linkedUserId(studentId);

        jdbc.update("""
            UPDATE users
            SET active = FALSE
            WHERE id = ?
            """, userId);

        return getAccount(studentId);
    }

    // =============== HELPERS ===============

    private Long linkedUserId(Long studentId) {

        Map<String, Object> account = getAccount(studentId);
        Object userId = account.get("userId");

        if (userId == null) {
            throw conflict(
                    "Student does not have a login account."
            );
        }

        Long id = ((Number) userId).longValue();

        List<String> roles = jdbc.query("""
            SELECT r.name
            FROM users u
            JOIN roles r ON r.id = u.role_id
            WHERE u.id = ?
            """,
                (rs, rowNum) -> rs.getString("name"),
                id);

        if (roles.isEmpty() ||
                !"STUDENT".equalsIgnoreCase(roles.get(0))) {
            throw conflict(
                    "Linked user does not have the STUDENT role."
            );
        }

        return id;
    }

    private Long studentRoleId() {
        List<Long> roles = jdbc.query("""
            SELECT id
            FROM roles
            WHERE UPPER(name) = 'STUDENT'
            """,
                (rs, rowNum) -> rs.getLong("id"));

        if (roles.isEmpty()) {
            throw conflict(
                    "STUDENT role is missing from the database."
            );
        }

        return roles.get(0);
    }

    private boolean emailExists(
            String email,
            Long excludedUserId
    ) {
        Integer count;

        if (excludedUserId == null) {
            count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM users
                WHERE LOWER(email) = LOWER(?)
                """, Integer.class, email);
        } else {
            count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM users
                WHERE LOWER(email) = LOWER(?)
                  AND id <> ?
                """,
                    Integer.class,
                    email,
                    excludedUserId);
        }

        return count != null && count > 0;
    }

    private String validateEmail(String value) {
        if (value == null || value.isBlank()) {
            throw badRequest("Email is required.");
        }

        String email = value.trim().toLowerCase(Locale.ROOT);

        if (email.length() > 150 ||
                !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw badRequest("Invalid email address.");
        }

        return email;
    }

    private void validatePassword(String password) {
        if (password == null ||
                password.length() < 8 ||
                password.length() > 72 ||
                password.isBlank()) {
            throw badRequest(
                    "Password must contain 8 to 72 characters."
            );
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }
}
