
package com.sms.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {

    private static final Logger logger =
            LoggerFactory.getLogger(AuditLogService.class);

    private final JdbcTemplate jdbc;

    public AuditLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(
            Authentication authentication,
            String action,
            String entityType,
            Long entityId,
            String details
    ) {
        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication.getName() == null ||
                "anonymousUser".equalsIgnoreCase(
                        authentication.getName())) {
            return;
        }

        try {
            List<Long> userIds = jdbc.query("""
                SELECT id
                FROM users
                WHERE LOWER(email) = LOWER(?)
                """,
                    (rs, rowNum) -> rs.getLong("id"),
                    authentication.getName()
            );

            if (userIds.isEmpty()) {
                logger.warn(
                        "Audit skipped: authenticated user not found"
                );
                return;
            }

            jdbc.update("""
                INSERT INTO audit_logs (
                    user_id,
                    action,
                    entity_type,
                    entity_id,
                    details
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                    userIds.get(0),
                    action,
                    entityType,
                    entityId,
                    details
            );

        } catch (Exception exception) {
            logger.error(
                    "Unable to save audit log for action {}",
                    action,
                    exception
            );
        }
    }
}
