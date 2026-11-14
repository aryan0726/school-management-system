
package com.sms.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentPortalController {

    private final JdbcTemplate jdbc;

    public StudentPortalController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ============== CURRENT STUDENT ==============

    private Long currentStudentId(Authentication auth) {

        if (auth == null ||
                !auth.isAuthenticated() ||
                auth.getName() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Student authentication required"
            );
        }

        List<Long> ids = jdbc.query("""
            SELECT s.id
            FROM students s
            JOIN users u ON u.id = s.user_id
            JOIN roles r ON r.id = u.role_id
            WHERE LOWER(u.email) = LOWER(?)
              AND UPPER(r.name) = 'STUDENT'
              AND u.active = TRUE
              AND s.active = TRUE
            """,
                (rs, rowNum) -> rs.getLong("id"),
                auth.getName()
        );

        if (ids.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Active student account not found"
            );
        }

        return ids.get(0);
    }

    private BigDecimal amount(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(value.toString());
    }

    private Long id(Object value) {
        return ((Number) value).longValue();
    }

    // ============== STUDENT PROFILE ==============

    @GetMapping("/profile")
    public Map<String, Object> profile(Authentication auth) {

        Long studentId = currentStudentId(auth);

        Map<String, Object> result =
                new LinkedHashMap<>(
                        jdbc.queryForMap("""
                            SELECT
                                s.id AS studentId,
                                s.full_name AS fullName,
                                s.admission_number AS admissionNumber,
                                s.date_of_birth AS dateOfBirth,
                                s.gender,
                                s.active,
                                u.email
                            FROM students s
                            JOIN users u ON u.id = s.user_id
                            WHERE s.id = ?
                            """, studentId)
                );

        List<Map<String, Object>> enrollments =
                jdbc.queryForList("""
                    SELECT
                        e.id AS enrollmentId,
                        e.academic_year AS academicYear,
                        e.status,
                        c.name AS className,
                        sec.name AS sectionName
                    FROM enrollments e
                    JOIN sections sec
                        ON sec.id = e.section_id
                    JOIN classes c
                        ON c.id = sec.class_id
                    WHERE e.student_id = ?
                    ORDER BY e.academic_year DESC, e.id DESC
                    """, studentId);

        result.put("enrollments", enrollments);

        return result;
    }

    // ============== ATTENDANCE ==============

    @GetMapping("/attendance")
    public Map<String, Object> attendance(
            Authentication auth
    ) {

        Long studentId = currentStudentId(auth);

        List<Map<String, Object>> records =
                jdbc.queryForList("""
                    SELECT
                        a.id AS sessionId,
                        a.attendance_date AS attendanceDate,
                        ar.status,
                        c.name AS className,
                        sec.name AS sectionName
                    FROM attendance_records ar
                    JOIN attendance_sessions a
                        ON a.id = ar.session_id
                    JOIN sections sec
                        ON sec.id = a.section_id
                    JOIN classes c
                        ON c.id = sec.class_id
                    WHERE ar.student_id = ?
                    ORDER BY
                        a.attendance_date DESC,
                        a.id DESC
                    """, studentId);

        long present = records.stream()
                .filter(record ->
                        "PRESENT".equalsIgnoreCase(
                                String.valueOf(
                                        record.get("status")
                                )
                        )
                )
                .count();

        long absent = records.stream()
                .filter(record ->
                        "ABSENT".equalsIgnoreCase(
                                String.valueOf(
                                        record.get("status")
                                )
                        )
                )
                .count();

        long marked = present + absent;

        BigDecimal percentage = marked == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(present)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(marked),
                        2,
                        RoundingMode.HALF_UP
                );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("studentId", studentId);
        response.put("totalRecords", records.size());
        response.put("present", present);
        response.put("absent", absent);
        response.put("attendancePercentage", percentage);
        response.put("records", records);

        return response;
    }

    // ============== PUBLISHED RESULTS ==============

    @GetMapping("/results")
    public List<Map<String, Object>> results(
            Authentication auth
    ) {

        Long studentId = currentStudentId(auth);

        // Only exams for the student's enrolled class/section
        // and academic year, after publication.
        List<Map<String, Object>> exams =
                jdbc.queryForList("""
                    SELECT DISTINCT
                        ex.id AS examId,
                        ex.name AS examName,
                        ex.exam_type AS examType,
                        ex.academic_year AS academicYear,
                        ex.start_date AS startDate,
                        ex.end_date AS endDate,
                        ex.published_at AS publishedAt
                    FROM exams ex
                    JOIN enrollments en
                        ON en.student_id = ?
                       AND en.academic_year = ex.academic_year
                    JOIN sections sec
                        ON sec.id = en.section_id
                    WHERE UPPER(TRIM(ex.status)) = 'PUBLISHED'
                      AND ex.published_at IS NOT NULL
                      AND sec.class_id = ex.class_id
                      AND (
                          ex.section_id IS NULL
                          OR ex.section_id = en.section_id
                      )
                    ORDER BY ex.id DESC
                    """, studentId);

        List<Map<String, Object>> response =
                new ArrayList<>();

        for (Map<String, Object> exam : exams) {

            Long examId = id(exam.get("examId"));

            List<Map<String, Object>> papers =
                    jdbc.queryForList("""
                        SELECT
                            ep.id AS paperId,
                            subj.name AS subjectName,
                            ep.max_marks AS maxMarks,
                            ep.pass_marks AS passMarks,
                            m.marks_obtained AS obtainedMarks
                        FROM exam_subjects ep
                        JOIN subjects subj
                            ON subj.id = ep.subject_id
                        LEFT JOIN marks m
                            ON m.exam_subject_id = ep.id
                           AND m.student_id = ?
                        WHERE ep.exam_id = ?
                        ORDER BY ep.id
                        """,
                            studentId,
                            examId
                    );

            BigDecimal total = BigDecimal.ZERO;
            BigDecimal maximum = BigDecimal.ZERO;

            boolean complete = !papers.isEmpty();
            boolean passed = !papers.isEmpty();

            for (Map<String, Object> paper : papers) {

                maximum = maximum.add(
                        amount(paper.get("maxMarks"))
                );

                Object obtained = paper.get("obtainedMarks");

                if (obtained == null) {
                    complete = false;
                    passed = false;
                    paper.put("status", "PENDING");
                    continue;
                }

                BigDecimal score = amount(obtained);
                BigDecimal required =
                        amount(paper.get("passMarks"));

                total = total.add(score);

                boolean subjectPassed =
                        score.compareTo(required) >= 0;

                if (!subjectPassed) {
                    passed = false;
                }

                paper.put(
                        "status",
                        subjectPassed ? "PASS" : "FAIL"
                );
            }

            BigDecimal percentage =
                    maximum.signum() == 0
                            ? BigDecimal.ZERO
                            : total
                            .multiply(BigDecimal.valueOf(100))
                            .divide(
                                    maximum,
                                    2,
                                    RoundingMode.HALF_UP
                            );

            Map<String, Object> item =
                    new LinkedHashMap<>(exam);

            item.put("studentId", studentId);
            item.put("subjects", papers);
            item.put("totalMarks", total);
            item.put("maximumMarks", maximum);
            item.put("percentage", percentage);
            item.put("complete", complete);

            item.put(
                    "result",
                    !complete
                            ? "PENDING"
                            : passed ? "PASS" : "FAIL"
            );

            response.add(item);
        }

        return response;
    }

    // ============== MY FEES ==============

    @GetMapping("/fees")
    public List<Map<String, Object>> fees(
            Authentication auth
    ) {

        Long studentId = currentStudentId(auth);

        return jdbc.queryForList("""
            SELECT
                fi.id AS invoiceId,
                fi.invoice_number AS invoiceNumber,
                fi.total_amount AS totalAmount,
                fi.due_date AS dueDate,
                fi.status,
                fi.created_at AS createdAt,
                COALESCE(
                    SUM(fp.amount),
                    0
                ) AS paidAmount,
                fi.total_amount - COALESCE(
                    SUM(fp.amount),
                    0
                ) AS outstandingAmount
            FROM fee_invoices fi
            LEFT JOIN fee_payments fp
                ON fp.invoice_id = fi.id
            WHERE fi.student_id = ?
            GROUP BY
                fi.id,
                fi.invoice_number,
                fi.total_amount,
                fi.due_date,
                fi.status,
                fi.created_at
            ORDER BY fi.id DESC
            """, studentId);
    }

    // ============== MY INVOICE DETAILS ==============

    @GetMapping("/fees/{invoiceId}")
    public Map<String, Object> invoiceDetails(
            @PathVariable Long invoiceId,
            Authentication auth
    ) {

        Long studentId = currentStudentId(auth);

        List<Map<String, Object>> invoices =
                jdbc.queryForList("""
                    SELECT
                        id AS invoiceId,
                        invoice_number AS invoiceNumber,
                        total_amount AS totalAmount,
                        due_date AS dueDate,
                        status,
                        created_at AS createdAt
                    FROM fee_invoices
                    WHERE id = ?
                      AND student_id = ?
                    """, invoiceId, studentId);

        if (invoices.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Invoice not found"
            );
        }

        Map<String, Object> response =
                new LinkedHashMap<>(invoices.get(0));

        List<Map<String, Object>> payments =
                jdbc.queryForList("""
                    SELECT
                        id AS paymentId,
                        amount,
                        payment_date AS paymentDate,
                        payment_method AS paymentMethod,
                        reference_number AS referenceNumber
                    FROM fee_payments
                    WHERE invoice_id = ?
                    ORDER BY id DESC
                    """, invoiceId);

        BigDecimal paid = BigDecimal.ZERO;

        for (Map<String, Object> payment : payments) {
            paid = paid.add(amount(payment.get("amount")));
        }

        BigDecimal total =
                amount(response.get("totalAmount"));

        response.put("studentId", studentId);
        response.put("paidAmount", paid);
        response.put(
                "outstandingAmount",
                total.subtract(paid)
        );
        response.put("payments", payments);

        return response;
    }
}
