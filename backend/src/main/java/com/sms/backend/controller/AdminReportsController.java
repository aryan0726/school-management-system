
package com.sms.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportsController {

    private final JdbcTemplate jdbc;

    public AdminReportsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ================= HELPERS =================

    private BigDecimal number(Object value) {
        return value == null
                ? BigDecimal.ZERO
                : new BigDecimal(value.toString());
    }

    private long count(Object value) {
        return value == null
                ? 0L
                : ((Number) value).longValue();
    }

    private BigDecimal percentage(
            BigDecimal obtained,
            BigDecimal maximum
    ) {
        if (maximum.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        return obtained
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        maximum,
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private void validateDates(
            LocalDate from,
            LocalDate to
    ) {
        if (from != null &&
                to != null &&
                from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "From date cannot be after to date"
            );
        }
    }

    private void requireExam(Long examId) {
        Integer total = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM exams
            WHERE id = ?
            """,
                Integer.class,
                examId
        );

        if (total == null || total == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Exam not found"
            );
        }
    }

    // ================= ATTENDANCE REPORT =================

    @GetMapping("/attendance")
    public Map<String, Object> attendanceReport(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {

        validateDates(from, to);

        String sql = """
            SELECT
                s.id AS studentId,
                s.admission_number AS admissionNumber,
                s.full_name AS studentName,
                COUNT(ar.id) AS totalRecords,
                SUM(
                    CASE
                        WHEN UPPER(ar.status) = 'PRESENT'
                        THEN 1 ELSE 0
                    END
                ) AS presentCount,
                SUM(
                    CASE
                        WHEN UPPER(ar.status) = 'ABSENT'
                        THEN 1 ELSE 0
                    END
                ) AS absentCount
            FROM students s
            LEFT JOIN attendance_records ar
                ON ar.student_id = s.id
            LEFT JOIN attendance_sessions ats
                ON ats.id = ar.session_id
            WHERE (
                ? IS NULL OR ats.attendance_date >= ?
            )
            AND (
                ? IS NULL OR ats.attendance_date <= ?
            )
            GROUP BY
                s.id,
                s.admission_number,
                s.full_name
            ORDER BY s.full_name
            """;

        // Use an alternate query for filtered dates so students
        // without records are still included.
        String filteredSql = """
            SELECT
                s.id AS studentId,
                s.admission_number AS admissionNumber,
                s.full_name AS studentName,
                COUNT(ar.id) AS totalRecords,
                COALESCE(SUM(
                    CASE
                        WHEN UPPER(ar.status) = 'PRESENT'
                        THEN 1 ELSE 0
                    END
                ), 0) AS presentCount,
                COALESCE(SUM(
                    CASE
                        WHEN UPPER(ar.status) = 'ABSENT'
                        THEN 1 ELSE 0
                    END
                ), 0) AS absentCount
            FROM students s
            LEFT JOIN attendance_records ar
                ON ar.student_id = s.id
                AND EXISTS (
                    SELECT 1
                    FROM attendance_sessions ats
                    WHERE ats.id = ar.session_id
                      AND (? IS NULL OR ats.attendance_date >= ?)
                      AND (? IS NULL OR ats.attendance_date <= ?)
                )
            GROUP BY
                s.id,
                s.admission_number,
                s.full_name
            ORDER BY s.full_name
            """;

        Object fromDate = from == null
                ? null : Date.valueOf(from);
        Object toDate = to == null
                ? null : Date.valueOf(to);

        List<Map<String, Object>> students =
                jdbc.queryForList(
                        filteredSql,
                        fromDate,
                        fromDate,
                        toDate,
                        toDate
                );

        long totalPresent = 0;
        long totalAbsent = 0;

        List<Map<String, Object>> rows =
                new ArrayList<>();

        for (Map<String, Object> student : students) {

            Map<String, Object> row =
                    new LinkedHashMap<>(student);

            long present = count(row.get("presentCount"));
            long absent = count(row.get("absentCount"));
            long marked = present + absent;

            totalPresent += present;
            totalAbsent += absent;

            row.put(
                    "attendancePercentage",
                    percentage(
                            BigDecimal.valueOf(present),
                            BigDecimal.valueOf(marked)
                    )
            );

            rows.add(row);
        }

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("from", from);
        result.put("to", to);
        result.put("totalStudents", rows.size());
        result.put("totalPresent", totalPresent);
        result.put("totalAbsent", totalAbsent);
        result.put(
                "overallPercentage",
                percentage(
                        BigDecimal.valueOf(totalPresent),
                        BigDecimal.valueOf(
                                totalPresent + totalAbsent
                        )
                )
        );
        result.put("students", rows);

        return result;
    }

    // ================= EXAM REPORT =================

    @GetMapping("/exams/{examId}")
    public Map<String, Object> examReport(
            @PathVariable Long examId
    ) {

        requireExam(examId);

        Map<String, Object> exam =
                new LinkedHashMap<>(
                        jdbc.queryForMap("""
                            SELECT
                                id AS examId,
                                name AS examName,
                                academic_year AS academicYear,
                                exam_type AS examType,
                                status,
                                start_date AS startDate,
                                end_date AS endDate
                            FROM exams
                            WHERE id = ?
                            """, examId)
                );

        List<Map<String, Object>> papers =
                jdbc.queryForList("""
                    SELECT
                        ep.id AS paperId,
                        subj.name AS subjectName,
                        ep.max_marks AS maximumMarks,
                        ep.pass_marks AS passingMarks,
                        COUNT(m.id) AS markedStudents,
                        AVG(m.marks_obtained) AS averageMarks
                    FROM exam_subjects ep
                    JOIN subjects subj
                        ON subj.id = ep.subject_id
                    LEFT JOIN marks m
                        ON m.exam_subject_id = ep.id
                    WHERE ep.exam_id = ?
                    GROUP BY
                        ep.id,
                        subj.name,
                        ep.max_marks,
                        ep.pass_marks
                    ORDER BY ep.id
                    """, examId);

        List<Map<String, Object>> studentMarks =
                jdbc.queryForList("""
                    SELECT
                        s.id AS studentId,
                        s.admission_number AS admissionNumber,
                        s.full_name AS studentName,
                        ep.id AS paperId,
                        subj.name AS subjectName,
                        ep.max_marks AS maximumMarks,
                        ep.pass_marks AS passingMarks,
                        m.marks_obtained AS obtainedMarks
                    FROM marks m
                    JOIN students s
                        ON s.id = m.student_id
                    JOIN exam_subjects ep
                        ON ep.id = m.exam_subject_id
                    JOIN subjects subj
                        ON subj.id = ep.subject_id
                    WHERE ep.exam_id = ?
                    ORDER BY s.full_name, ep.id
                    """, examId);

        Map<Long, Map<String, Object>> grouped =
                new LinkedHashMap<>();

        for (Map<String, Object> mark : studentMarks) {

            Long studentId =
                    ((Number) mark.get("studentId")).longValue();

            if (!grouped.containsKey(studentId)) {

                Map<String, Object> student =
                        new LinkedHashMap<>();

                student.put("studentId", studentId);
                student.put(
                        "admissionNumber",
                        mark.get("admissionNumber")
                );
                student.put(
                        "studentName",
                        mark.get("studentName")
                );
                student.put(
                        "totalObtained",
                        BigDecimal.ZERO
                );
                student.put(
                        "totalMaximum",
                        BigDecimal.ZERO
                );
                student.put("passed", true);
                student.put(
                        "subjects",
                        new ArrayList<Map<String, Object>>()
                );

                grouped.put(studentId, student);
            }

            Map<String, Object> student =
                    grouped.get(studentId);

            BigDecimal obtained =
                    number(mark.get("obtainedMarks"));

            BigDecimal maximum =
                    number(mark.get("maximumMarks"));

            BigDecimal required =
                    number(mark.get("passingMarks"));

            student.put(
                    "totalObtained",
                    number(student.get("totalObtained"))
                            .add(obtained)
            );

            student.put(
                    "totalMaximum",
                    number(student.get("totalMaximum"))
                            .add(maximum)
            );

            boolean passed =
                    obtained.compareTo(required) >= 0;

            if (!passed) {
                student.put("passed", false);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subjects =
                    (List<Map<String, Object>>)
                            student.get("subjects");

            Map<String, Object> subject =
                    new LinkedHashMap<>(mark);

            subject.put(
                    "result",
                    passed ? "PASS" : "FAIL"
            );

            subjects.add(subject);
        }

        List<Map<String, Object>> results =
                new ArrayList<>(grouped.values());

        long passedCount = 0;

        for (Map<String, Object> student : results) {

            student.put(
                    "percentage",
                    percentage(
                            number(student.get("totalObtained")),
                            number(student.get("totalMaximum"))
                    )
            );

            // Do not classify incomplete marks as a final pass.
            boolean complete =
                    ((List<?>) student.get("subjects")).size()
                            == papers.size()
                            && !papers.isEmpty();

            boolean passed =
                    complete &&
                            Boolean.TRUE.equals(student.get("passed"));

            student.put("complete", complete);
            student.put(
                    "result",
                    !complete ? "PENDING" :
                            passed ? "PASS" : "FAIL"
            );

            if (passed) {
                passedCount++;
            }
        }

        exam.put("papers", papers);
        exam.put("students", results);
        exam.put("reportedStudents", results.size());
        exam.put("passedStudents", passedCount);
        exam.put(
                "failedOrIncompleteStudents",
                results.size() - passedCount
        );

        return exam;
    }

    // ================= FEES REPORT =================

    @GetMapping("/fees")
    public Map<String, Object> feeReport() {

        List<Map<String, Object>> invoices =
                jdbc.queryForList("""
                    SELECT
                        fi.id AS invoiceId,
                        fi.invoice_number AS invoiceNumber,
                        s.id AS studentId,
                        s.full_name AS studentName,
                        s.admission_number AS admissionNumber,
                        fi.total_amount AS totalAmount,
                        fi.due_date AS dueDate,
                        fi.status,
                        COALESCE(SUM(fp.amount), 0)
                            AS paidAmount,
                        fi.total_amount -
                            COALESCE(SUM(fp.amount), 0)
                            AS outstandingAmount
                    FROM fee_invoices fi
                    JOIN students s
                        ON s.id = fi.student_id
                    LEFT JOIN fee_payments fp
                        ON fp.invoice_id = fi.id
                    GROUP BY
                        fi.id,
                        fi.invoice_number,
                        s.id,
                        s.full_name,
                        s.admission_number,
                        fi.total_amount,
                        fi.due_date,
                        fi.status
                    ORDER BY fi.id DESC
                    """);

        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;

        for (Map<String, Object> invoice : invoices) {
            billed = billed.add(
                    number(invoice.get("totalAmount"))
            );

            collected = collected.add(
                    number(invoice.get("paidAmount"))
            );

            outstanding = outstanding.add(
                    number(invoice.get("outstandingAmount"))
            );
        }

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("totalInvoices", invoices.size());
        result.put("totalBilled", billed);
        result.put("totalCollected", collected);
        result.put("totalOutstanding", outstanding);
        result.put("invoices", invoices);

        return result;
    }

    // ================= PAYMENTS REPORT =================

    @GetMapping("/payments")
    public Map<String, Object> paymentReport(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {

        validateDates(from, to);

        String sql = """
            SELECT
                fp.id AS paymentId,
                fp.invoice_id AS invoiceId,
                fi.invoice_number AS invoiceNumber,
                s.id AS studentId,
                s.full_name AS studentName,
                fp.amount,
                fp.payment_date AS paymentDate,
                fp.payment_method AS paymentMethod,
                fp.reference_number AS referenceNumber
            FROM fee_payments fp
            JOIN fee_invoices fi
                ON fi.id = fp.invoice_id
            JOIN students s
                ON s.id = fi.student_id
            WHERE (? IS NULL OR fp.payment_date >= ?)
              AND (? IS NULL OR fp.payment_date <= ?)
            ORDER BY fp.payment_date DESC, fp.id DESC
            """;

        Object fromDate = from == null
                ? null : Date.valueOf(from);

        Object toDate = to == null
                ? null : Date.valueOf(to);

        List<Map<String, Object>> payments =
                jdbc.queryForList(
                        sql,
                        fromDate,
                        fromDate,
                        toDate,
                        toDate
                );

        BigDecimal total = BigDecimal.ZERO;

        for (Map<String, Object> payment : payments) {
            total = total.add(
                    number(payment.get("amount"))
            );
        }

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("from", from);
        result.put("to", to);
        result.put("totalPayments", payments.size());
        result.put("totalCollected", total);
        result.put("payments", payments);

        return result;
    }

    // ================= REPORTS OVERVIEW =================

    @GetMapping
    public Map<String, Object> overview() {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("attendance", attendanceReport(null, null));
        result.put("fees", feeReport());
        result.put("payments", paymentReport(null, null));

        return result;
    }
}
