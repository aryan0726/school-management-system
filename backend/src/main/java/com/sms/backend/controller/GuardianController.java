
package com.sms.backend.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/guardians")
@PreAuthorize("hasRole('ADMIN')")
public class GuardianController {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    private static final Set<String> RELATIONSHIPS =
            Set.of(
                    "FATHER",
                    "MOTHER",
                    "GRANDFATHER",
                    "GRANDMOTHER",
                    "UNCLE",
                    "AUNT",
                    "LEGAL_GUARDIAN",
                    "OTHER"
            );

    public GuardianController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    // ================= REQUEST MODELS =================

    public record GuardianRequest(
            Long userId,
            String phone,
            String address
    ) {}

    public record GuardianUpdateRequest(
            String phone,
            String address
    ) {}

    public record LinkStudentRequest(
            Long studentId,
            String relationship
    ) {}

    public record RelationshipRequest(
            String relationship
    ) {}

    // ================= GUARDIAN LIST =================

    @GetMapping
    public List<Map<String, Object>> listGuardians() {

        return jdbc.queryForList("""
            SELECT
                g.id,
                g.user_id AS userId,
                u.full_name AS fullName,
                u.email,
                g.phone,
                g.address,
                (
                    SELECT COUNT(*)
                    FROM student_guardians sg
                    WHERE sg.guardian_id = g.id
                ) AS linkedStudents
            FROM guardians g
            JOIN users u ON u.id = g.user_id
            ORDER BY g.id DESC
            """);
    }

    // ================= GUARDIAN DETAILS =================

    @GetMapping("/{guardianId}")
    public Map<String, Object> getGuardian(
            @PathVariable Long guardianId
    ) {

        Map<String, Object> guardian =
                new LinkedHashMap<>(
                        findGuardian(guardianId)
                );

        guardian.put(
                "students",
                getLinkedStudents(guardianId)
        );

        return guardian;
    }

    // ================= CREATE GUARDIAN PROFILE =================

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createGuardian(
            @RequestBody GuardianRequest request
    ) {

        if (request == null || request.userId() == null) {
            throw badRequest(
                    "Existing guardian user ID is required."
            );
        }

        validateContact(request.phone(), request.address());

        if (!exists(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                request.userId()
        )) {
            throw badRequest("User account not found.");
        }

        if (exists(
                "SELECT COUNT(*) FROM guardians WHERE user_id = ?",
                request.userId()
        )) {
            throw conflict(
                    "This user already has a guardian profile."
            );
        }

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("userId", request.userId())
                        .addValue("phone", clean(request.phone()))
                        .addValue("address", clean(request.address()));

        GeneratedKeyHolder holder = new GeneratedKeyHolder();

        try {
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
                """, params, holder, new String[]{"id"});

        } catch (DataIntegrityViolationException ex) {
            throw conflict(
                    "Guardian profile could not be created."
            );
        }

        Long guardianId = generatedId(holder);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(getGuardian(guardianId));
    }

    // ================= UPDATE GUARDIAN =================

    @PutMapping("/{guardianId}")
    @Transactional
    public Map<String, Object> updateGuardian(
            @PathVariable Long guardianId,
            @RequestBody GuardianUpdateRequest request
    ) {

        findGuardian(guardianId);

        if (request == null) {
            throw badRequest("Guardian details are required.");
        }

        validateContact(request.phone(), request.address());

        jdbc.update("""
            UPDATE guardians
            SET phone = ?,
                address = ?
            WHERE id = ?
            """,
                clean(request.phone()),
                clean(request.address()),
                guardianId
        );

        return getGuardian(guardianId);
    }

    // ================= DELETE GUARDIAN PROFILE =================

    @DeleteMapping("/{guardianId}")
    @Transactional
    public Map<String, String> deleteGuardian(
            @PathVariable Long guardianId
    ) {

        findGuardian(guardianId);

        if (exists("""
                SELECT COUNT(*)
                FROM student_guardians
                WHERE guardian_id = ?
                """, guardianId)) {

            throw conflict(
                    "Unlink students before deleting this guardian profile."
            );
        }

        jdbc.update(
                "DELETE FROM guardians WHERE id = ?",
                guardianId
        );

        return Map.of(
                "message",
                "Guardian profile deleted successfully"
        );
    }

    // ================= LINK STUDENT =================

    @PostMapping("/{guardianId}/students")
    @Transactional
    public ResponseEntity<Map<String, Object>> linkStudent(
            @PathVariable Long guardianId,
            @RequestBody LinkStudentRequest request
    ) {

        findGuardian(guardianId);

        if (request == null || request.studentId() == null) {
            throw badRequest("Student ID is required.");
        }

        String relationship =
                validateRelationship(request.relationship());

        if (!exists("""
                SELECT COUNT(*)
                FROM students
                WHERE id = ?
                  AND active = TRUE
                """, request.studentId())) {

            throw badRequest("Active student not found.");
        }

        if (exists("""
                SELECT COUNT(*)
                FROM student_guardians
                WHERE guardian_id = ?
                  AND student_id = ?
                """,
                guardianId,
                request.studentId()
        )) {
            throw conflict(
                    "This student is already linked to the guardian."
            );
        }

        try {
            jdbc.update("""
                INSERT INTO student_guardians (
                    student_id,
                    guardian_id,
                    relationship
                )
                VALUES (?, ?, ?)
                """,
                    request.studentId(),
                    guardianId,
                    relationship
            );

        } catch (DataIntegrityViolationException ex) {
            throw conflict(
                    "Student-guardian link already exists or is invalid."
            );
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(getGuardian(guardianId));
    }

    // ================= LINKED STUDENTS =================

    @GetMapping("/{guardianId}/students")
    public List<Map<String, Object>> getLinkedStudents(
            @PathVariable Long guardianId
    ) {

        findGuardian(guardianId);

        return jdbc.queryForList("""
            SELECT
                sg.id AS linkId,
                s.id AS studentId,
                s.admission_number AS admissionNumber,
                s.full_name AS studentName,
                s.date_of_birth AS dateOfBirth,
                s.gender,
                s.active,
                sg.relationship
            FROM student_guardians sg
            JOIN students s ON s.id = sg.student_id
            WHERE sg.guardian_id = ?
            ORDER BY s.full_name
            """, guardianId);
    }

    // ================= UPDATE RELATIONSHIP =================

    @PutMapping("/{guardianId}/students/{studentId}")
    @Transactional
    public Map<String, Object> updateRelationship(
            @PathVariable Long guardianId,
            @PathVariable Long studentId,
            @RequestBody RelationshipRequest request
    ) {

        findGuardian(guardianId);

        String relationship = validateRelationship(
                request == null ? null : request.relationship()
        );

        int updated = jdbc.update("""
            UPDATE student_guardians
            SET relationship = ?
            WHERE guardian_id = ?
              AND student_id = ?
            """,
                relationship,
                guardianId,
                studentId
        );

        if (updated == 0) {
            throw notFound(
                    "Student is not linked to this guardian."
            );
        }

        return getGuardian(guardianId);
    }

    // ================= UNLINK STUDENT =================

    @DeleteMapping("/{guardianId}/students/{studentId}")
    @Transactional
    public Map<String, String> unlinkStudent(
            @PathVariable Long guardianId,
            @PathVariable Long studentId
    ) {

        findGuardian(guardianId);

        int deleted = jdbc.update("""
            DELETE FROM student_guardians
            WHERE guardian_id = ?
              AND student_id = ?
            """,
                guardianId,
                studentId
        );

        if (deleted == 0) {
            throw notFound(
                    "Student-guardian relationship not found."
            );
        }

        return Map.of(
                "message",
                "Student unlinked successfully"
        );
    }

    // ================= STUDENT'S GUARDIANS =================

    @GetMapping("/student/{studentId}")
    public List<Map<String, Object>> getStudentGuardians(
            @PathVariable Long studentId
    ) {

        if (!exists(
                "SELECT COUNT(*) FROM students WHERE id = ?",
                studentId
        )) {
            throw notFound("Student not found.");
        }

        return jdbc.queryForList("""
            SELECT
                g.id AS guardianId,
                g.user_id AS userId,
                u.full_name AS fullName,
                u.email,
                g.phone,
                g.address,
                sg.relationship
            FROM student_guardians sg
            JOIN guardians g ON g.id = sg.guardian_id
            JOIN users u ON u.id = g.user_id
            WHERE sg.student_id = ?
            ORDER BY g.id
            """, studentId);
    }

    // ================= HELPERS =================

    private Map<String, Object> findGuardian(Long guardianId) {

        List<Map<String, Object>> rows =
                jdbc.queryForList("""
                    SELECT
                        g.id,
                        g.user_id AS userId,
                        u.full_name AS fullName,
                        u.email,
                        g.phone,
                        g.address
                    FROM guardians g
                    JOIN users u ON u.id = g.user_id
                    WHERE g.id = ?
                    """, guardianId);

        if (rows.isEmpty()) {
            throw notFound("Guardian not found.");
        }

        return rows.get(0);
    }

    private String validateRelationship(String relationship) {

        if (relationship == null || relationship.isBlank()) {
            throw badRequest("Relationship is required.");
        }

        String normalized = relationship
                .trim()
                .toUpperCase()
                .replace(' ', '_');

        if (!RELATIONSHIPS.contains(normalized)) {
            throw badRequest(
                    "Invalid relationship. Use FATHER, MOTHER, " +
                            "GRANDFATHER, GRANDMOTHER, UNCLE, AUNT, " +
                            "LEGAL_GUARDIAN or OTHER."
            );
        }

        return normalized;
    }

    private void validateContact(String phone, String address) {

        if (phone != null && phone.trim().length() > 20) {
            throw badRequest(
                    "Phone number cannot exceed 20 characters."
            );
        }

        if (address != null && address.length() > 10000) {
            throw badRequest(
                    "Address is too long."
            );
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private boolean exists(String sql, Object... args) {

        Integer count = jdbc.queryForObject(
                sql,
                Integer.class,
                args
        );

        return count != null && count > 0;
    }

    private Long generatedId(GeneratedKeyHolder holder) {

        Number key = holder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "Generated ID was not returned."
            );
        }

        return key.longValue();
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
