
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
@RequestMapping("/api/guardian")
@PreAuthorize("hasRole('GUARDIAN')")
public class GuardianPortalController {

    private final JdbcTemplate jdbc;

    public GuardianPortalController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // =============== AUTHORIZATION ===============

    private Long currentGuardianId(Authentication auth) {

        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Please sign in"
            );
        }

        List<Long> guardianIds = jdbc.query("""
            SELECT g.id
            FROM guardians g
            JOIN users u ON u.id = g.user_id
            JOIN roles r ON r.id = u.role_id
            WHERE LOWER(u.email) = LOWER(?)
              AND UPPER(r.name) = 'GUARDIAN'
            """,
                (rs, index) -> rs.getLong("id"),
                auth.getName()
        );

        if (guardianIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Guardian account not found"
            );
        }

        return guardianIds.get(0);
    }

    private void requireLinkedStudent(
            Authentication auth,
            Long studentId
    ) {

        Long guardianId = currentGuardianId(auth);

        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM student_guardians
            WHERE guardian_id = ?
              AND student_id = ?
            """,
                Integer.class,
                guardianId,
                studentId
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot access this student's records"
            );
        }
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
    }

    private Long asLong(Object value) {
        return ((Number) value).longValue();
    }

    // =============== MY PROFILE ===============

    @GetMapping("/profile")
    public Map<String, Object> myProfile(
            Authentication auth
    ) {

        Long guardianId = currentGuardianId(auth);

        return jdbc.queryForMap("""
            SELECT
                g.id AS guardianId,
                u.full_name AS fullName,
                u.email,
                g.phone,
                g.address,
                (
                    SELECT COUNT(*)
                    FROM student_guardians sg
                    WHERE sg.guardian_id = g.id
                ) AS childrenCount
            FROM guardians g
            JOIN users u ON u.id = g.user_id
            WHERE g.id = ?
            """,
                guardianId
        );
    }

    // =============== MY CHILDREN ===============

    @GetMapping("/children")
    public List<Map<String, Object>> myChildren(
            Authentication auth
    ) {

        Long guardianId = currentGuardianId(auth);

        return jdbc.queryForList("""
            SELECT
                s.id AS studentId,
                s.full_name AS studentName,
                s.admission_number AS admissionNumber,
                s.date_of_birth AS dateOfBirth,
                s.gender,
                s.active,
                sg.relationship
            FROM student_guardians sg
            JOIN students s
                ON s.id = sg.student_id
            WHERE sg.guardian_id = ?
            ORDER BY s.full_name
            """,
                guardianId
        );
    }

    @GetMapping("/children/{studentId}")
    public Map<String, Object> childDetails(
            @PathVariable Long studentId,
            Authentication auth
    ) {

        requireLinkedStudent(auth, studentId);

        List<Map<String, Object>> students =
                jdbc.queryForList("""
                    SELECT
                        id,
                        full_name AS studentName,
                        admission_number AS admissionNumber,
                        date_of_birth AS dateOfBirth,
                        gender,
                        active
                    FROM students
                    WHERE id = ?
                    """,
                        studentId
                );

        if (students.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Student not found"
            );
        }

        Map<String, Object> result =
                new LinkedHashMap<>(students.get(0));

        result.put(
                "enrollments",
                jdbc.queryForList("""
                    SELECT
                        e.id,
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
                    """,
                        studentId
                )
        );

        return result;
    }

    // =============== CHILD ATTENDANCE ===============

    @GetMapping("/children/{studentId}/attendance")
    public Map<String, Object> childAttendance(
            @PathVariable Long studentId,
            Authentication auth
    ) {

        requireLinkedStudent(auth, studentId);

        List<Map<String, Object>> records =
                jdbc.queryForList("""
                    SELECT
                        a.id AS sessionId,
                        a.attendance_date AS attendanceDate,
                        a.session_type AS sessionType,
                        c.name AS className,
                        sec.name AS sectionName,
                        ar.status,
                        ar.remarks
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
                    """,
                        studentId
                );

        long present = records.stream()
                .filter(row ->
                        "PRESENT".equalsIgnoreCase(
                                String.valueOf(row.get("status"))
                        )
                )
                .count();

        long absent = records.stream()
                .filter(row ->
                        "ABSENT".equalsIgnoreCase(
                                String.valueOf(row.get("status"))
                        )
                )
                .count();

        long total = records.size();

        BigDecimal percentage = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(present)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(total),
                        2,
                        RoundingMode.HALF_UP
                );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("studentId", studentId);
        response.put("totalSessions", total);
        response.put("present", present);
        response.put("absent", absent);
        response.put("attendancePercentage", percentage);
        response.put("records", records);

        return response;
    }

    // =============== PUBLISHED RESULTS ===============

    @GetMapping("/children/{studentId}/results")
    public List<Map<String, Object>> childResults(
            @PathVariable Long studentId,
            Authentication auth
    ) {

        requireLinkedStudent(auth, studentId);

        // Never expose draft/unpublished examinations.
        List<Map<String, Object>> exams =
                jdbc.queryForList("""
                    SELECT DISTINCT
                        ex.id,
                        ex.name,
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
                    WHERE ex.status = 'Published'
                      AND sec.class_id = ex.class_id
                      AND (
                          ex.section_id IS NULL
                          OR ex.section_id = en.section_id
                      )
                    ORDER BY ex.id DESC
                    """,
                        studentId
                );

        List<Map<String, Object>> results =
                new ArrayList<>();

        for (Map<String, Object> exam : exams) {

            Long examId = asLong(exam.get("id"));

            List<Map<String, Object>> papers =
                    jdbc.queryForList("""
                        SELECT
                            ep.id AS paperId,
                            s.name AS subjectName,
                            ep.max_marks AS maxMarks,
                            ep.pass_marks AS passingMarks,
                            m.marks_obtained AS obtainedMarks
                        FROM exam_subjects ep
                        JOIN subjects s
                            ON s.id = ep.subject_id
                        LEFT JOIN marks m
                            ON m.exam_subject_id = ep.id
                           AND m.student_id = ?
                        WHERE ep.exam_id = ?
                        ORDER BY ep.id
                        """,
                            studentId,
                            examId
                    );

            BigDecimal obtained = BigDecimal.ZERO;
            BigDecimal maximum = BigDecimal.ZERO;

            boolean complete = !papers.isEmpty();
            boolean passed = !papers.isEmpty();

            for (Map<String, Object> paper : papers) {

                maximum = maximum.add(
                        decimal(paper.get("maxMarks"))
                );

                Object mark = paper.get("obtainedMarks");

                if (mark == null) {
                    complete = false;
                    passed = false;
                    paper.put("status", "PENDING");
                    continue;
                }

                BigDecimal score = decimal(mark);
                BigDecimal required =
                        decimal(paper.get("passingMarks"));

                obtained = obtained.add(score);

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
                            : obtained
                            .multiply(BigDecimal.valueOf(100))
                            .divide(
                                    maximum,
                                    2,
                                    RoundingMode.HALF_UP
                            );

            String grade = "PENDING";

            if (complete) {

                List<String> grades = jdbc.query("""
                    SELECT grade
                    FROM grading_rules
                    WHERE is_active = TRUE
                      AND min_percentage <= ?
                      AND max_percentage >= ?
                    ORDER BY min_percentage DESC
                    LIMIT 1
                    """,
                        (rs, index) ->
                                rs.getString("grade"),
                        percentage,
                        percentage
                );

                grade = grades.isEmpty()
                        ? "UNASSIGNED"
                        : grades.get(0);
            }

            Map<String, Object> result =
                    new LinkedHashMap<>(exam);

            result.put("studentId", studentId);
            result.put("subjects", papers);
            result.put("totalMarks", obtained);
            result.put("maximumMarks", maximum);
            result.put("percentage", percentage);
            result.put("grade", grade);
            result.put("complete", complete);
            result.put(
                    "result",
                    !complete ? "PENDING" :
                            passed ? "PASS" : "FAIL"
            );

            results.add(result);
        }

        return results;
    }

    // =============== CHILD FEES ===============

    @GetMapping("/children/{studentId}/fees")
    public List<Map<String, Object>> childFees(
            @PathVariable Long studentId,
            Authentication auth
    ) {

        requireLinkedStudent(auth, studentId);

        return jdbc.queryForList("""
            SELECT
                fi.id AS invoiceId,
                fi.invoice_number AS invoiceNumber,
                fi.total_amount AS totalAmount,
                fi.due_date AS dueDate,
                fi.status,
                fi.created_at AS createdAt,
                COALESCE((
                    SELECT SUM(fp.amount)
                    FROM fee_payments fp
                    WHERE fp.invoice_id = fi.id
                ), 0) AS paidAmount,
                fi.total_amount - COALESCE((
                    SELECT SUM(fp.amount)
                    FROM fee_payments fp
                    WHERE fp.invoice_id = fi.id
                ), 0) AS outstandingAmount
            FROM fee_invoices fi
            WHERE fi.student_id = ?
            ORDER BY fi.id DESC
            """,
                studentId
        );
    }

    // =============== INVOICE DETAILS ===============

    @GetMapping(
            "/children/{studentId}/fees/{invoiceId}"
    )
    public Map<String, Object> childInvoice(
            @PathVariable Long studentId,
            @PathVariable Long invoiceId,
            Authentication auth
    ) {

        requireLinkedStudent(auth, studentId);

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
                    """,
                        invoiceId,
                        studentId
                );

        if (invoices.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Invoice not found for this student"
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
                    """,
                        invoiceId
                );

        BigDecimal paid = BigDecimal.ZERO;

        for (Map<String, Object> payment : payments) {
            paid = paid.add(decimal(payment.get("amount")));
        }

        BigDecimal total =
                decimal(response.get("totalAmount"));

        response.put("paidAmount", paid);
        response.put(
                "outstandingAmount",
                total.subtract(paid)
        );
        response.put("payments", payments);

        return response;
    }
}
