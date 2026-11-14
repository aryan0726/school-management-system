
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
@RequestMapping("/api/admin/guardian-accounts")
@PreAuthorize("hasRole('ADMIN')")
public class GuardianAccountController {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final PasswordEncoder passwordEncoder;

    public GuardianAccountController(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder
    ) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.passwordEncoder = passwordEncoder;
    }

    // =============== REQUEST MODELS ===============

    public record CreateGuardianRequest(
            String fullName,
            String email,
            String password,
            String phone,
            String address
    ) {}

    public record UpdateGuardianRequest(
            String fullName,
            String email,
            String phone,
            String address
    ) {}

    public record ResetPasswordRequest(
            String newPassword
    ) {}

    // =============== ACCOUNT LIST ===============

    @GetMapping
    public List<Map<String, Object>> listAccounts() {

        return jdbc.queryForList("""
            SELECT
                g.id AS guardianId,
                u.id AS userId,
                u.full_name AS fullName,
                u.email,
                u.active,
                g.phone,
                g.address,
                u.created_at AS createdAt,
                (
                    SELECT COUNT(*)
                    FROM student_guardians sg
                    WHERE sg.guardian_id = g.id
                ) AS linkedStudents
            FROM guardians g
            JOIN users u ON u.id = g.user_id
            JOIN roles r ON r.id = u.role_id
            WHERE UPPER(r.name) = 'GUARDIAN'
            ORDER BY g.id DESC
            """);
    }

    // =============== ACCOUNT DETAILS ===============

    @GetMapping("/{guardianId}")
    public Map<String, Object> getAccount(
            @PathVariable Long guardianId
    ) {

        Map<String, Object> account =
                new LinkedHashMap<>(
                        findAccount(guardianId)
                );

        List<Map<String, Object>> children =
                jdbc.queryForList("""
                    SELECT
                        s.id AS studentId,
                        s.full_name AS studentName,
                        s.admission_number AS admissionNumber,
                        s.active,
                        sg.relationship
                    FROM student_guardians sg
                    JOIN students s
                        ON s.id = sg.student_id
                    WHERE sg.guardian_id = ?
                    ORDER BY s.full_name
                    """, guardianId);

        account.put("children", children);

        return account;
    }

    // =============== CREATE ACCOUNT ===============

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createAccount(
            @RequestBody CreateGuardianRequest request
    ) {

        validateCreate(request);

        Long roleId = guardianRoleId();

        String email = normalizeEmail(request.email());

        if (emailExists(email, null)) {
            throw conflict(
                    "An account with this email already exists."
            );
        }

        String passwordHash =
                passwordEncoder.encode(request.password());

        MapSqlParameterSource userParams =
                new MapSqlParameterSource()
                        .addValue(
                                "fullName",
                                request.fullName().trim()
                        )
                        .addValue("email", email)
                        .addValue("password", passwordHash)
                        .addValue("roleId", roleId);

        GeneratedKeyHolder userHolder =
                new GeneratedKeyHolder();

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
                    userParams,
                    userHolder,
                    new String[]{"id"});

            Long userId = generatedId(userHolder);

            MapSqlParameterSource guardianParams =
                    new MapSqlParameterSource()
                            .addValue("userId", userId)
                            .addValue(
                                    "phone",
                                    clean(request.phone())
                            )
                            .addValue(
                                    "address",
                                    clean(request.address())
                            );

            GeneratedKeyHolder guardianHolder =
                    new GeneratedKeyHolder();

            namedJdbc.update("""
                INSERT INTO guardians (
                    user_id,
                    phone,
                    address
                )
                VALUES (
                    :userId,
                    :phone,
                    :address
                )
                """,
                    guardianParams,
                    guardianHolder,
                    new String[]{"id"});

            Long guardianId = generatedId(guardianHolder);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(getAccount(guardianId));

        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "Guardian account could not be created. Check whether the email already exists."
            );
        }
    }

    // =============== UPDATE ACCOUNT ===============

    @PutMapping("/{guardianId}")
    @Transactional
    public Map<String, Object> updateAccount(
            @PathVariable Long guardianId,
            @RequestBody UpdateGuardianRequest request
    ) {

        Map<String, Object> existing =
                lockAccount(guardianId);

        validateUpdate(request);

        String email = normalizeEmail(request.email());

        Long userId = asLong(existing.get("userId"));

        if (emailExists(email, userId)) {
            throw conflict(
                    "Another account already uses this email."
            );
        }

        try {
            jdbc.update("""
                UPDATE users
                SET full_name = ?,
                    email = ?
                WHERE id = ?
                """,
                    request.fullName().trim(),
                    email,
                    userId);

            jdbc.update("""
                UPDATE guardians
                SET phone = ?,
                    address = ?
                WHERE id = ?
                """,
                    clean(request.phone()),
                    clean(request.address()),
                    guardianId);

        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "Unable to update guardian account."
            );
        }

        return getAccount(guardianId);
    }

    // =============== DEACTIVATE ACCOUNT ===============

    @PatchMapping("/{guardianId}/deactivate")
    @Transactional
    public Map<String, Object> deactivate(
            @PathVariable Long guardianId
    ) {

        Map<String, Object> account =
                lockAccount(guardianId);

        jdbc.update("""
            UPDATE users
            SET active = FALSE
            WHERE id = ?
            """, asLong(account.get("userId")));

        return getAccount(guardianId);
    }

    // =============== ACTIVATE ACCOUNT ===============

    @PatchMapping("/{guardianId}/activate")
    @Transactional
    public Map<String, Object> activate(
            @PathVariable Long guardianId
    ) {

        Map<String, Object> account =
                lockAccount(guardianId);

        jdbc.update("""
            UPDATE users
            SET active = TRUE
            WHERE id = ?
            """, asLong(account.get("userId")));

        return getAccount(guardianId);
    }

    // =============== PASSWORD RESET ===============

    @PatchMapping("/{guardianId}/password")
    @Transactional
    public Map<String, String> resetPassword(
            @PathVariable Long guardianId,
            @RequestBody ResetPasswordRequest request
    ) {

        if (request == null) {
            throw badRequest(
                    "New password is required."
            );
        }

        validatePassword(request.newPassword());

        Map<String, Object> account =
                lockAccount(guardianId);

        String hash =
                passwordEncoder.encode(
                        request.newPassword()
                );

        jdbc.update("""
            UPDATE users
            SET password = ?
            WHERE id = ?
            """,
                hash,
                asLong(account.get("userId")));

        return Map.of(
                "message",
                "Guardian password reset successfully"
        );
    }

    // =============== HELPERS ===============

    private Long guardianRoleId() {

        List<Long> roles = jdbc.query("""
            SELECT id
            FROM roles
            WHERE UPPER(name) = 'GUARDIAN'
            """,
                (rs, rowNumber) -> rs.getLong("id"));

        if (roles.isEmpty()) {
            throw conflict(
                    "GUARDIAN role not found. Check the existing role seed migration."
            );
        }

        return roles.get(0);
    }

    private Map<String, Object> findAccount(
            Long guardianId
    ) {

        List<Map<String, Object>> rows =
                jdbc.queryForList("""
                    SELECT
                        g.id AS guardianId,
                        u.id AS userId,
                        u.full_name AS fullName,
                        u.email,
                        u.active,
                        g.phone,
                        g.address,
                        u.created_at AS createdAt
                    FROM guardians g
                    JOIN users u
                        ON u.id = g.user_id
                    JOIN roles r
                        ON r.id = u.role_id
                    WHERE g.id = ?
                      AND UPPER(r.name) = 'GUARDIAN'
                    """, guardianId);

        if (rows.isEmpty()) {
            throw notFound(
                    "Guardian account not found."
            );
        }

        return rows.get(0);
    }

    private Map<String, Object> lockAccount(
            Long guardianId
    ) {

        List<Long> ids = jdbc.query("""
            SELECT u.id
            FROM users u
            JOIN guardians g
                ON g.user_id = u.id
            JOIN roles r
                ON r.id = u.role_id
            WHERE g.id = ?
              AND UPPER(r.name) = 'GUARDIAN'
            FOR UPDATE
            """,
                (rs, rowNumber) -> rs.getLong("id"),
                guardianId);

        if (ids.isEmpty()) {
            throw notFound(
                    "Guardian account not found."
            );
        }

        return findAccount(guardianId);
    }

    private void validateCreate(
            CreateGuardianRequest request
    ) {

        if (request == null) {
            throw badRequest(
                    "Guardian details are required."
            );
        }

        validateIdentity(
                request.fullName(),
                request.email()
        );

        validatePassword(request.password());

        validateContact(
                request.phone(),
                request.address()
        );
    }

    private void validateUpdate(
            UpdateGuardianRequest request
    ) {

        if (request == null) {
            throw badRequest(
                    "Guardian details are required."
            );
        }

        validateIdentity(
                request.fullName(),
                request.email()
        );

        validateContact(
                request.phone(),
                request.address()
        );
    }

    private void validateIdentity(
            String fullName,
            String email
    ) {

        if (fullName == null ||
                fullName.isBlank() ||
                fullName.trim().length() > 100) {
            throw badRequest(
                    "Full name is required and must not exceed 100 characters."
            );
        }

        if (email == null ||
                email.isBlank() ||
                email.trim().length() > 150) {
            throw badRequest(
                    "Valid email is required."
            );
        }

        String normalized = normalizeEmail(email);

        if (!normalized.matches(
                "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
        )) {
            throw badRequest(
                    "Invalid email format."
            );
        }
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

    private void validateContact(
            String phone,
            String address
    ) {

        if (phone != null &&
                phone.trim().length() > 20) {
            throw badRequest(
                    "Phone number cannot exceed 20 characters."
            );
        }

        if (address != null &&
                address.length() > 10000) {
            throw badRequest(
                    "Address cannot exceed 10000 characters."
            );
        }
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
                """,
                    Integer.class,
                    email);
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

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private Long generatedId(
            GeneratedKeyHolder holder
    ) {

        Number key = holder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "Generated database ID not returned."
            );
        }

        return key.longValue();
    }

    private Long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private ResponseStatusException badRequest(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private ResponseStatusException conflict(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }

    private ResponseStatusException notFound(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }
}
