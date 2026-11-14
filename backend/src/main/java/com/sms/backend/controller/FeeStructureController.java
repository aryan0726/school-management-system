
package com.sms.backend.controller;

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
import java.sql.Date;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;

@RestController
@RequestMapping("/api/admin/fees/structures")
@PreAuthorize("hasRole('ADMIN')")
public class FeeStructureController {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("99999999.99");

    public FeeStructureController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    // =============== REQUEST MODELS ===============

    public record FeeStructureRequest(
            Long classId,
            String academicYear,
            String name,
            BigDecimal amount,
            LocalDate dueDate
    ) {}

    public record GenerateRequest(
            Long studentId
    ) {}

    // =============== LIST STRUCTURES ===============

    @GetMapping
    public List<Map<String, Object>> listStructures(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String academicYear
    ) {

        StringBuilder query = new StringBuilder("""
            SELECT
                fs.id,
                fs.class_id AS classId,
                c.name AS className,
                fs.academic_year AS academicYear,
                fs.name,
                fs.amount,
                fs.due_date AS dueDate,
                fs.active,
                fs.created_at AS createdAt,
                (
                    SELECT COUNT(*)
                    FROM fee_structure_invoices fsi
                    WHERE fsi.fee_structure_id = fs.id
                ) AS generatedInvoices
            FROM fee_structures fs
            JOIN classes c ON c.id = fs.class_id
            WHERE 1 = 1
            """);

        List<Object> params = new ArrayList<>();

        if (classId != null) {
            query.append(" AND fs.class_id = ? ");
            params.add(classId);
        }

        if (academicYear != null &&
                !academicYear.isBlank()) {

            query.append(" AND fs.academic_year = ? ");
            params.add(academicYear.trim());
        }

        query.append(" ORDER BY fs.id DESC ");

        return jdbc.queryForList(
                query.toString(),
                params.toArray()
        );
    }

    // =============== STRUCTURE DETAILS ===============

    @GetMapping("/{id}")
    public Map<String, Object> getStructure(
            @PathVariable Long id
    ) {

        Map<String, Object> structure =
                new LinkedHashMap<>(findStructure(id));

        List<Map<String, Object>> invoices =
                jdbc.queryForList("""
                    SELECT
                        fi.id,
                        fi.student_id AS studentId,
                        st.full_name AS studentName,
                        fi.invoice_number AS invoiceNumber,
                        fi.total_amount AS totalAmount,
                        fi.status,
                        fi.due_date AS dueDate,
                        COALESCE(SUM(fp.amount), 0)
                            AS paidAmount,
                        fi.total_amount -
                            COALESCE(SUM(fp.amount), 0)
                            AS balanceAmount
                    FROM fee_structure_invoices fsi
                    JOIN fee_invoices fi
                        ON fi.id = fsi.invoice_id
                    JOIN students st
                        ON st.id = fi.student_id
                    LEFT JOIN fee_payments fp
                        ON fp.invoice_id = fi.id
                    WHERE fsi.fee_structure_id = ?
                    GROUP BY
                        fi.id,
                        fi.student_id,
                        st.full_name,
                        fi.invoice_number,
                        fi.total_amount,
                        fi.status,
                        fi.due_date
                    ORDER BY fi.id DESC
                    """, id);

        structure.put("invoices", invoices);

        return structure;
    }

    // =============== CREATE STRUCTURE ===============

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createStructure(
            @RequestBody FeeStructureRequest request
    ) {

        validateRequest(request);

        MapSqlParameterSource params =
                parameters(request);

        GeneratedKeyHolder holder =
                new GeneratedKeyHolder();

        namedJdbc.update("""
            INSERT INTO fee_structures (
                class_id,
                academic_year,
                name,
                amount,
                due_date,
                active
            )
            VALUES (
                :classId,
                :academicYear,
                :name,
                :amount,
                :dueDate,
                TRUE
            )
            """,
                params,
                holder,
                new String[]{"id"});

        Long id = generatedId(holder);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(getStructure(id));
    }

    // =============== UPDATE STRUCTURE ===============

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> updateStructure(
            @PathVariable Long id,
            @RequestBody FeeStructureRequest request
    ) {

        lockStructure(id);

        validateRequest(request);

        // Prevent changing billed amounts after invoices exist.
        if (hasGeneratedInvoices(id)) {
            throw conflict(
                    "Invoices already exist. Create a new fee structure instead of modifying this one."
            );
        }

        MapSqlParameterSource params =
                parameters(request)
                        .addValue("id", id);

        namedJdbc.update("""
            UPDATE fee_structures
            SET class_id = :classId,
                academic_year = :academicYear,
                name = :name,
                amount = :amount,
                due_date = :dueDate
            WHERE id = :id
            """, params);

        return getStructure(id);
    }

    // =============== DEACTIVATE ===============

    @PatchMapping("/{id}/deactivate")
    @Transactional
    public Map<String, Object> deactivate(
            @PathVariable Long id
    ) {

        lockStructure(id);

        jdbc.update("""
            UPDATE fee_structures
            SET active = FALSE
            WHERE id = ?
            """, id);

        return getStructure(id);
    }

    // =============== ACTIVATE ===============

    @PatchMapping("/{id}/activate")
    @Transactional
    public Map<String, Object> activate(
            @PathVariable Long id
    ) {

        lockStructure(id);

        Map<String, Object> structure = findStructure(id);

        Long classId = number(structure.get("classId"));

        if (!exists("""
            SELECT COUNT(*)
            FROM classes
            WHERE id = ?
              AND active = TRUE
            """, classId)) {
            throw badRequest(
                    "Cannot activate a structure for an inactive class."
            );
        }

        jdbc.update("""
            UPDATE fee_structures
            SET active = TRUE
            WHERE id = ?
            """, id);

        return getStructure(id);
    }

    // =============== GENERATE INVOICES ===============

    @PostMapping("/{id}/generate")
    @Transactional
    public Map<String, Object> generateInvoices(
            @PathVariable Long id,
            @RequestBody(required = false) GenerateRequest request
    ) {

        // Lock the structure to serialize concurrent generation.
        lockStructure(id);

        Map<String, Object> structure =
                findStructure(id);

        if (!Boolean.TRUE.equals(structure.get("active"))) {
            throw conflict(
                    "Cannot generate invoices from an inactive fee structure."
            );
        }

        Long classId =
                number(structure.get("classId"));

        String academicYear =
                String.valueOf(structure.get("academicYear"));

        BigDecimal amount =
                decimal(structure.get("amount"));

        Object dueDate =
                structure.get("dueDate");

        Long requestedStudent =
                request == null ? null : request.studentId();

        List<Long> studentIds;

        if (requestedStudent != null) {

            if (!studentEligible(
                    requestedStudent,
                    classId,
                    academicYear
            )) {
                throw badRequest(
                        "Student is not actively enrolled in this class and academic year."
                );
            }

            studentIds = List.of(requestedStudent);

        } else {

            studentIds = jdbc.query("""
                SELECT DISTINCT st.id
                FROM students st
                JOIN enrollments en
                    ON en.student_id = st.id
                JOIN sections sec
                    ON sec.id = en.section_id
                WHERE st.active = TRUE
                  AND en.status = 'ACTIVE'
                  AND en.academic_year = ?
                  AND sec.class_id = ?
                ORDER BY st.id
                """,
                    (rs, rowNumber) -> rs.getLong("id"),
                    academicYear,
                    classId);
        }

        int created = 0;
        int skipped = 0;

        List<Long> createdInvoiceIds =
                new ArrayList<>();

        for (Long studentId : studentIds) {

            if (exists("""
                SELECT COUNT(*)
                FROM fee_structure_invoices
                WHERE fee_structure_id = ?
                  AND student_id = ?
                """, id, studentId)) {

                skipped++;
                continue;
            }

            String invoiceNumber =
                    "FS-" + id +
                            "-ST-" + studentId +
                            "-" + UUID.randomUUID()
                            .toString()
                            .substring(0, 8)
                            .toUpperCase(Locale.ROOT);

            GeneratedKeyHolder invoiceHolder =
                    new GeneratedKeyHolder();

            MapSqlParameterSource invoiceParams =
                    new MapSqlParameterSource()
                            .addValue(
                                    "studentId",
                                    studentId
                            )
                            .addValue(
                                    "invoiceNumber",
                                    invoiceNumber
                            )
                            .addValue(
                                    "amount",
                                    amount
                            )
                            .addValue(
                                    "dueDate",
                                    dueDate
                            );

            namedJdbc.update("""
                INSERT INTO fee_invoices (
                    student_id,
                    invoice_number,
                    total_amount,
                    due_date,
                    status
                )
                VALUES (
                    :studentId,
                    :invoiceNumber,
                    :amount,
                    :dueDate,
                    'UNPAID'
                )
                """,
                    invoiceParams,
                    invoiceHolder,
                    new String[]{"id"});

            Long invoiceId =
                    generatedId(invoiceHolder);

            jdbc.update("""
                INSERT INTO fee_structure_invoices (
                    fee_structure_id,
                    student_id,
                    invoice_id
                )
                VALUES (?, ?, ?)
                """,
                    id,
                    studentId,
                    invoiceId
            );

            createdInvoiceIds.add(invoiceId);
            created++;
        }

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "message",
                "Invoice generation completed"
        );

        response.put("feeStructureId", id);
        response.put("eligibleStudents", studentIds.size());
        response.put("generated", created);
        response.put("skipped", skipped);
        response.put("invoiceIds", createdInvoiceIds);

        return response;
    }

    // =============== HELPERS ===============

    private Map<String, Object> findStructure(
            Long id
    ) {

        List<Map<String, Object>> rows =
                jdbc.queryForList("""
                    SELECT
                        fs.id,
                        fs.class_id AS classId,
                        c.name AS className,
                        fs.academic_year AS academicYear,
                        fs.name,
                        fs.amount,
                        fs.due_date AS dueDate,
                        fs.active,
                        fs.created_at AS createdAt
                    FROM fee_structures fs
                    JOIN classes c
                        ON c.id = fs.class_id
                    WHERE fs.id = ?
                    """, id);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Fee structure not found"
            );
        }

        return rows.get(0);
    }

    private void lockStructure(Long id) {

        List<Map<String, Object>> rows =
                jdbc.queryForList("""
                    SELECT id
                    FROM fee_structures
                    WHERE id = ?
                    FOR UPDATE
                    """, id);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Fee structure not found"
            );
        }
    }

    private void validateRequest(
            FeeStructureRequest request
    ) {

        if (request == null ||
                request.classId() == null ||
                request.academicYear() == null ||
                request.academicYear().isBlank() ||
                request.name() == null ||
                request.name().isBlank() ||
                request.amount() == null ||
                request.dueDate() == null) {

            throw badRequest(
                    "Class, academic year, name, amount and due date are required."
            );
        }

        if (request.name().trim().length() > 100 ||
                request.academicYear().trim().length() > 20) {

            throw badRequest(
                    "Fee name or academic year is too long."
            );
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0 ||
                request.amount().compareTo(MAX_AMOUNT) > 0 ||
                request.amount().scale() > 2) {

            throw badRequest(
                    "Amount must be positive with at most 2 decimal places."
            );
        }

        if (!exists("""
            SELECT COUNT(*)
            FROM classes
            WHERE id = ?
              AND active = TRUE
            """, request.classId())) {

            throw badRequest(
                    "Active class not found."
            );
        }
    }

    private MapSqlParameterSource parameters(
            FeeStructureRequest request
    ) {

        return new MapSqlParameterSource()
                .addValue("classId", request.classId())
                .addValue(
                        "academicYear",
                        request.academicYear().trim()
                )
                .addValue(
                        "name",
                        request.name().trim()
                )
                .addValue("amount", request.amount())
                .addValue(
                        "dueDate",
                        Date.valueOf(request.dueDate())
                );
    }

    private boolean studentEligible(
            Long studentId,
            Long classId,
            String academicYear
    ) {

        return exists("""
            SELECT COUNT(*)
            FROM students st
            JOIN enrollments en
                ON en.student_id = st.id
            JOIN sections sec
                ON sec.id = en.section_id
            WHERE st.id = ?
              AND st.active = TRUE
              AND en.status = 'ACTIVE'
              AND en.academic_year = ?
              AND sec.class_id = ?
            """,
                studentId,
                academicYear,
                classId);
    }

    private boolean hasGeneratedInvoices(
            Long structureId
    ) {

        return exists("""
            SELECT COUNT(*)
            FROM fee_structure_invoices
            WHERE fee_structure_id = ?
            """, structureId);
    }

    private boolean exists(
            String sql,
            Object... args
    ) {

        Integer count =
                jdbc.queryForObject(
                        sql,
                        Integer.class,
                        args
                );

        return count != null && count > 0;
    }

    private Long generatedId(
            GeneratedKeyHolder holder
    ) {

        Number key = holder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "Generated database ID not returned"
            );
        }

        return key.longValue();
    }

    private Long number(Object value) {
        return ((Number) value).longValue();
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
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
}
