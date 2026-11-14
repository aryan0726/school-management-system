
package com.sms.backend.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final JdbcTemplate jdbc;

    public AdminDashboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private long count(String sql) {
        Long result = jdbc.queryForObject(
                sql, Long.class
        );
        return result == null ? 0L : result;
    }

    private BigDecimal money(String sql) {
        BigDecimal result = jdbc.queryForObject(
                sql, BigDecimal.class
        );
        return result == null
                ? BigDecimal.ZERO
                : result;
    }

    // ========== DASHBOARD OVERVIEW ==========

    @GetMapping("/overview")
    public Map<String, Object> overview() {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("totalStudents", count(
                "SELECT COUNT(*) FROM students"
        ));

        result.put("activeStudents", count(
                "SELECT COUNT(*) FROM students WHERE active = TRUE"
        ));

        result.put("totalTeachers", count(
                "SELECT COUNT(*) FROM teachers"
        ));

        result.put("activeTeachers", count(
                "SELECT COUNT(*) FROM teachers WHERE active = TRUE"
        ));

        result.put("totalGuardians", count(
                "SELECT COUNT(*) FROM guardians"
        ));

        result.put("totalClasses", count(
                "SELECT COUNT(*) FROM classes"
        ));

        result.put("totalSections", count(
                "SELECT COUNT(*) FROM sections"
        ));

        result.put("totalSubjects", count(
                "SELECT COUNT(*) FROM subjects"
        ));

        result.put("totalEnrollments", count(
                "SELECT COUNT(*) FROM enrollments"
        ));

        result.put("totalExams", count(
                "SELECT COUNT(*) FROM exams"
        ));

        result.put("totalInvoices", count(
                "SELECT COUNT(*) FROM fee_invoices"
        ));

        return result;
    }

    // ========== ATTENDANCE SUMMARY ==========

    @GetMapping("/attendance")
    public Map<String, Object> attendance() {

        long present = count("""
            SELECT COUNT(*)
            FROM attendance_records
            WHERE UPPER(status) = 'PRESENT'
            """);

        long absent = count("""
            SELECT COUNT(*)
            FROM attendance_records
            WHERE UPPER(status) = 'ABSENT'
            """);

        long marked = present + absent;

        double percentage = marked == 0
                ? 0.0
                : Math.round(
                present * 10000.0 / marked
        ) / 100.0;

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("present", present);
        result.put("absent", absent);
        result.put("markedRecords", marked);
        result.put("attendancePercentage", percentage);

        result.put("totalSessions", count(
                "SELECT COUNT(*) FROM attendance_sessions"
        ));

        return result;
    }

    // ========== FINANCIAL SUMMARY ==========

    @GetMapping("/finance")
    public Map<String, Object> finance() {

        BigDecimal billed = money("""
            SELECT COALESCE(SUM(total_amount), 0)
            FROM fee_invoices
            """);

        BigDecimal collected = money("""
            SELECT COALESCE(SUM(amount), 0)
            FROM fee_payments
            """);

        BigDecimal outstanding =
                billed.subtract(collected);

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("totalBilled", billed);
        result.put("totalCollected", collected);
        result.put("totalOutstanding", outstanding);

        result.put("totalPayments", count(
                "SELECT COUNT(*) FROM fee_payments"
        ));

        return result;
    }

    // ========== EXAM SUMMARY ==========

    @GetMapping("/exams")
    public Map<String, Object> exams() {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("totalExams", count(
                "SELECT COUNT(*) FROM exams"
        ));

        result.put("publishedExams", count("""
            SELECT COUNT(*)
            FROM exams
            WHERE UPPER(TRIM(status)) = 'PUBLISHED'
            """));

        result.put("totalExamPapers", count(
                "SELECT COUNT(*) FROM exam_subjects"
        ));

        result.put("totalMarksEntries", count(
                "SELECT COUNT(*) FROM marks"
        ));

        return result;
    }

    // ========== CLASS-WISE STUDENT COUNT ==========

    @GetMapping("/class-distribution")
    public List<Map<String, Object>> classDistribution() {

        return jdbc.queryForList("""
            SELECT
                c.id AS classId,
                c.name AS className,
                COUNT(DISTINCT CASE
                    WHEN s.active = TRUE THEN s.id
                    ELSE NULL
                END) AS studentCount
            FROM classes c
            LEFT JOIN sections sec
                ON sec.class_id = c.id
            LEFT JOIN enrollments e
                ON e.section_id = sec.id
            LEFT JOIN students s
                ON s.id = e.student_id
            GROUP BY c.id, c.name
            ORDER BY c.id
            """);
    }

    // ========== MAIN DASHBOARD ==========

    @GetMapping
    public Map<String, Object> dashboard() {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("overview", overview());
        result.put("attendance", attendance());
        result.put("finance", finance());
        result.put("exams", exams());
        result.put(
                "classDistribution",
                classDistribution()
        );

        return result;
    }
}
