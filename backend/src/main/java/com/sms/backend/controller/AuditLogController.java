
package com.sms.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final NamedParameterJdbcTemplate jdbc;

    private static final Set<String> ACTIONS =
            Set.of("CREATE", "UPDATE", "DELETE");

    public AuditLogController(DataSource dataSource) {
        this.jdbc =
                new NamedParameterJdbcTemplate(dataSource);
    }

    // =============== AUDIT LOGS ===============

    @GetMapping
    public Map<String, Object> listLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {

        if (page < 0 || size < 1 || size > 100) {
            throw badRequest(
                    "Page must be >= 0 and size must be 1 to 100."
            );
        }

        if (page > Integer.MAX_VALUE / size) {
            throw badRequest("Requested page is too large.");
        }

        if (from != null &&
                to != null &&
                from.isAfter(to)) {
            throw badRequest(
                    "From date cannot be after to date."
            );
        }

        String normalizedAction = null;

        if (action != null && !action.isBlank()) {
            normalizedAction = action.trim()
                    .toUpperCase(Locale.ROOT);

            if (!ACTIONS.contains(normalizedAction)) {
                throw badRequest(
                        "Action must be CREATE, UPDATE or DELETE."
                );
            }
        }

        String normalizedEntity = null;

        if (entityType != null &&
                !entityType.isBlank()) {

            normalizedEntity = entityType.trim()
                    .toUpperCase(Locale.ROOT);

            if (!normalizedEntity.matches(
                    "[A-Z0-9_]{1,100}"
            )) {
                throw badRequest("Invalid entity type.");
            }
        }

        StringBuilder filter = new StringBuilder("""
            FROM audit_logs al
            LEFT JOIN users u ON u.id = al.user_id
            WHERE 1 = 1
            """);

        MapSqlParameterSource params =
                new MapSqlParameterSource();

        if (userId != null) {
            filter.append(" AND al.user_id = :userId");
            params.addValue("userId", userId);
        }

        if (normalizedAction != null) {
            filter.append(" AND al.action = :action");
            params.addValue(
                    "action",
                    normalizedAction
            );
        }

        if (normalizedEntity != null) {
            filter.append(
                    " AND UPPER(al.entity_type) = :entityType"
            );
            params.addValue(
                    "entityType",
                    normalizedEntity
            );
        }

        if (from != null) {
            filter.append(
                    " AND al.created_at >= :fromDate"
            );
            params.addValue(
                    "fromDate",
                    from.atStartOfDay()
            );
        }

        if (to != null) {
            filter.append(
                    " AND al.created_at < :toDate"
            );
            params.addValue(
                    "toDate",
                    to.plusDays(1).atStartOfDay()
            );
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) " + filter,
                params,
                Long.class
        );

        params.addValue("limit", size);
        params.addValue("offset", page * size);

        String sql = """
            SELECT
                al.id,
                al.user_id AS userId,
                u.full_name AS userName,
                u.email AS userEmail,
                al.action,
                al.entity_type AS entityType,
                al.entity_id AS entityId,
                al.details,
                al.created_at AS createdAt
            """ + filter + """
            ORDER BY al.created_at DESC, al.id DESC
            LIMIT :limit OFFSET :offset
            """;

        List<Map<String, Object>> logs =
                jdbc.queryForList(sql, params);

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("page", page);
        response.put("size", size);
        response.put(
                "totalRecords",
                total == null ? 0 : total
        );
        response.put("logs", logs);

        return response;
    }

    // =============== SINGLE AUDIT RECORD ===============

    @GetMapping("/{id}")
    public Map<String, Object> getLog(
            @PathVariable Long id
    ) {

        List<Map<String, Object>> rows =
                jdbc.queryForList("""
                    SELECT
                        al.id,
                        al.user_id AS userId,
                        u.full_name AS userName,
                        u.email AS userEmail,
                        al.action,
                        al.entity_type AS entityType,
                        al.entity_id AS entityId,
                        al.details,
                        al.created_at AS createdAt
                    FROM audit_logs al
                    LEFT JOIN users u
                        ON u.id = al.user_id
                    WHERE al.id = :id
                    """,
                        new MapSqlParameterSource()
                                .addValue("id", id)
                );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Audit log not found."
            );
        }

        return rows.get(0);
    }

    private ResponseStatusException badRequest(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
