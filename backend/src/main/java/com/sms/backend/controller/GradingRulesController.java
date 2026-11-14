
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/grading-rules")
@PreAuthorize("hasRole('ADMIN')")
public class GradingRulesController {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public GradingRulesController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @GetMapping
    public List<Map<String, Object>> listRules() {

        return jdbc.queryForList("""
            SELECT
                id,
                grade,
                min_percentage,
                max_percentage,
                grade_point,
                description,
                is_active
            FROM grading_rules
            ORDER BY min_percentage DESC
            """);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createRule(
            @RequestBody GradeRuleRequest request
    ) {

        ensureGradingCanChange();
        validateRule(request, -1L);

        var params = params(request);
        var holder = new GeneratedKeyHolder();

        try {
            namedJdbc.update("""
                INSERT INTO grading_rules (
                    grade,
                    min_percentage,
                    max_percentage,
                    grade_point,
                    description,
                    is_active
                )
                VALUES (
                    :grade,
                    :min,
                    :max,
                    :point,
                    :description,
                    :active
                )
                """,
                    params,
                    holder,
                    new String[]{"id"});

        } catch (DataIntegrityViolationException exception) {
            throw conflict("Grade already exists or violates a constraint.");
        }

        Long id = holder.getKey().longValue();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(findRule(id));
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> updateRule(
            @PathVariable Long id,
            @RequestBody GradeRuleRequest request
    ) {

        findRule(id);
        ensureGradingCanChange();
        validateRule(request, id);

        var params = params(request)
                .addValue("id", id);

        try {
            namedJdbc.update("""
                UPDATE grading_rules SET
                    grade = :grade,
                    min_percentage = :min,
                    max_percentage = :max,
                    grade_point = :point,
                    description = :description,
                    is_active = :active
                WHERE id = :id
                """, params);

        } catch (DataIntegrityViolationException exception) {
            throw conflict("Grade already exists or violates a constraint.");
        }

        return findRule(id);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, String> deleteRule(
            @PathVariable Long id
    ) {

        findRule(id);
        ensureGradingCanChange();

        jdbc.update(
                "DELETE FROM grading_rules WHERE id = ?",
                id
        );

        return Map.of(
                "message",
                "Grading rule deleted successfully"
        );
    }

    private void validateRule(
            GradeRuleRequest request,
            Long excludeId
    ) {

        if (request == null ||
                request.grade() == null ||
                request.grade().isBlank() ||
                request.minPercentage() == null ||
                request.maxPercentage() == null) {

            throw badRequest(
                    "Grade, minimum and maximum percentages are required."
            );
        }

        String grade = request.grade().trim();

        if (grade.length() > 10) {
            throw badRequest("Grade is too long.");
        }

        BigDecimal min = request.minPercentage();
        BigDecimal max = request.maxPercentage();

        if (min.compareTo(BigDecimal.ZERO) < 0 ||
                max.compareTo(new BigDecimal("100")) > 0 ||
                min.compareTo(max) > 0 ||
                min.scale() > 2 ||
                max.scale() > 2) {

            throw badRequest(
                    "Grade percentage range must be between 0 and 100."
            );
        }

        BigDecimal point = request.gradePoint() == null
                ? BigDecimal.ZERO
                : request.gradePoint();

        if (point.compareTo(BigDecimal.ZERO) < 0 ||
                point.compareTo(new BigDecimal("10")) > 0 ||
                point.scale() > 1) {

            throw badRequest(
                    "Grade point must be between 0 and 10 with one decimal place."
            );
        }

        if (request.description() != null &&
                request.description().length() > 255) {
            throw badRequest("Description is too long.");
        }

        Integer duplicate = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM grading_rules
            WHERE grade = ?
              AND id <> ?
            """,
                Integer.class,
                grade,
                excludeId);

        if (duplicate != null && duplicate > 0) {
            throw conflict("This grade already exists.");
        }

        boolean active = request.isActive() == null ||
                request.isActive();

        if (active) {

            Integer overlap = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM grading_rules
                WHERE id <> ?
                  AND is_active = TRUE
                  AND min_percentage <= ?
                  AND max_percentage >= ?
                """,
                    Integer.class,
                    excludeId,
                    max,
                    min);

            if (overlap != null && overlap > 0) {
                throw conflict(
                        "This percentage range overlaps an active grading rule."
                );
            }
        }
    }

    private void ensureGradingCanChange() {

        Integer published = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM exams
            WHERE status = 'Published'
            """,
                Integer.class);

        if (published != null && published > 0) {
            throw conflict(
                    "Reopen published examinations before changing the grading scale."
            );
        }
    }

    private Map<String, Object> findRule(Long id) {

        List<Map<String, Object>> rows =
                jdbc.queryForList("""
                    SELECT
                        id,
                        grade,
                        min_percentage,
                        max_percentage,
                        grade_point,
                        description,
                        is_active
                    FROM grading_rules
                    WHERE id = ?
                    """, id);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Grading rule not found"
            );
        }

        return rows.get(0);
    }

    private MapSqlParameterSource params(
            GradeRuleRequest request
    ) {

        return new MapSqlParameterSource()
                .addValue("grade", request.grade().trim())
                .addValue("min", request.minPercentage())
                .addValue("max", request.maxPercentage())
                .addValue(
                        "point",
                        request.gradePoint() == null
                                ? BigDecimal.ZERO
                                : request.gradePoint()
                )
                .addValue("description", request.description())
                .addValue(
                        "active",
                        request.isActive() == null ||
                                request.isActive()
                );
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

    public record GradeRuleRequest(
            String grade,
            BigDecimal minPercentage,
            BigDecimal maxPercentage,
            BigDecimal gradePoint,
            String description,
            Boolean isActive
    ) {}
}
