
package com.sms.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ExamResultsController {

    private final JdbcTemplate jdbc;

    public ExamResultsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ================= EXAM HELPERS =================

    private Map<String, Object> exam(Long examId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, class_id, section_id, name,
                   academic_year, status
            FROM exams
            WHERE id = ?
            """, examId);

        if (rows.isEmpty()) {
            throw error(
                    HttpStatus.NOT_FOUND,
                    "Examination not found"
            );
        }

        return rows.get(0);
    }

    private Map<String, Object> paper(
            Long examId,
            Long paperId
    ) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT ep.id, ep.exam_id, ep.subject_id,
                   ep.max_marks, ep.pass_marks,
                   ep.teacher_id,
                   s.name AS subject_name
            FROM exam_subjects ep
            JOIN subjects s ON s.id = ep.subject_id
            WHERE ep.id = ? AND ep.exam_id = ?
            """, paperId, examId);

        if (rows.isEmpty()) {
            throw error(
                    HttpStatus.NOT_FOUND,
                    "Exam paper not found"
            );
        }

        return rows.get(0);
    }

    private List<Map<String, Object>> papers(Long examId) {
        return jdbc.queryForList("""
            SELECT ep.id, ep.subject_id,
                   s.name AS subject_name,
                   ep.max_marks, ep.pass_marks
            FROM exam_subjects ep
            JOIN subjects s ON s.id = ep.subject_id
            WHERE ep.exam_id = ?
            ORDER BY ep.id
            """, examId);
    }

    private Long number(Object value) {
        return ((Number) value).longValue();
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(
                sql, Integer.class, args
        );
        return count != null && count > 0;
    }

    private ResponseStatusException error(
            HttpStatus status,
            String message
    ) {
        return new ResponseStatusException(status, message);
    }

    private void ensureNotPublished(
            Map<String, Object> exam
    ) {
        if ("Published".equalsIgnoreCase(
                String.valueOf(exam.get("status"))
        )) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Published examination is locked"
            );
        }
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null &&
                auth.getAuthorities().stream()
                        .anyMatch(a ->
                                "ROLE_ADMIN".equals(
                                        a.getAuthority()
                                )
                        );
    }

    private Long teacherId(Authentication auth) {
        if (auth == null) {
            throw error(
                    HttpStatus.UNAUTHORIZED,
                    "Login required"
            );
        }

        List<Long> ids = jdbc.query("""
            SELECT t.id
            FROM teachers t
            JOIN users u ON u.id = t.user_id
            WHERE LOWER(u.email) = LOWER(?)
              AND t.active = TRUE
            """,
                (rs, rowNum) -> rs.getLong("id"),
                auth.getName());

        if (ids.isEmpty()) {
            throw error(
                    HttpStatus.FORBIDDEN,
                    "Active teacher account not found"
            );
        }

        return ids.get(0);
    }

    private void requirePaperAccess(
            Authentication auth,
            Map<String, Object> exam,
            Map<String, Object> paper
    ) {
        if (isAdmin(auth)) return;

        Long teacherId = teacherId(auth);

        boolean assigned = exists("""
            SELECT COUNT(*)
            FROM teacher_assignments ta
            JOIN sections s
                ON s.id = ta.section_id
            WHERE ta.teacher_id = ?
              AND ta.subject_id = ?
              AND ta.academic_year = ?
              AND ta.active = TRUE
              AND s.class_id = ?
              AND (? IS NULL OR ta.section_id = ?)
            """,
                teacherId,
                number(paper.get("subject_id")),
                exam.get("academic_year"),
                number(exam.get("class_id")),
                exam.get("section_id"),
                exam.get("section_id"));

        if (!assigned) {
            throw error(
                    HttpStatus.FORBIDDEN,
                    "You are not assigned to this exam paper"
            );
        }
    }

    private boolean eligibleStudent(
            Map<String, Object> exam,
            Long studentId
    ) {
        return exists("""
            SELECT COUNT(*)
            FROM enrollments en
            JOIN students st ON st.id = en.student_id
            JOIN sections sec ON sec.id = en.section_id
            WHERE en.student_id = ?
              AND en.academic_year = ?
              AND en.status = 'ACTIVE'
              AND st.active = TRUE
              AND sec.class_id = ?
              AND (? IS NULL OR en.section_id = ?)
            """,
                studentId,
                exam.get("academic_year"),
                number(exam.get("class_id")),
                exam.get("section_id"),
                exam.get("section_id"));
    }

    private List<Map<String, Object>> eligibleStudents(
            Map<String, Object> exam
    ) {
        return jdbc.queryForList("""
            SELECT DISTINCT
                   st.id AS studentId,
                   st.full_name AS studentName,
                   st.admission_number AS admissionNumber
            FROM enrollments en
            JOIN students st ON st.id = en.student_id
            JOIN sections sec ON sec.id = en.section_id
            WHERE en.academic_year = ?
              AND en.status = 'ACTIVE'
              AND st.active = TRUE
              AND sec.class_id = ?
              AND (? IS NULL OR en.section_id = ?)
            ORDER BY st.full_name
            """,
                exam.get("academic_year"),
                number(exam.get("class_id")),
                exam.get("section_id"),
                exam.get("section_id"));
    }

    // ================= MARKS ENTRY =================

    public record MarkItem(
            Long studentId,
            BigDecimal marksObtained
    ) {}

    public record BulkMarksRequest(
            List<MarkItem> records
    ) {}

    @GetMapping(
            "/exams/{examId}/papers/{paperId}/marks"
    )
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Map<String, Object> getPaperMarks(
            @PathVariable Long examId,
            @PathVariable Long paperId,
            Authentication auth
    ) {
        var e = exam(examId);
        var p = paper(examId, paperId);

        requirePaperAccess(auth, e, p);

        List<Map<String, Object>> students =
                eligibleStudents(e);

        List<Map<String, Object>> saved =
                jdbc.queryForList("""
                    SELECT student_id, marks_obtained
                    FROM marks
                    WHERE exam_subject_id = ?
                    """, paperId);

        Map<Long, BigDecimal> marksMap = new HashMap<>();

        for (var row : saved) {
            marksMap.put(
                    number(row.get("student_id")),
                    decimal(row.get("marks_obtained"))
            );
        }

        List<Map<String, Object>> roster = new ArrayList<>();

        for (var student : students) {
            Long id = number(student.get("studentId"));

            Map<String, Object> item =
                    new LinkedHashMap<>(student);

            item.put(
                    "marksObtained",
                    marksMap.get(id)
            );

            roster.add(item);
        }

        return Map.of(
                "examId", examId,
                "paper", p,
                "students", roster
        );
    }

    @PostMapping(
            "/exams/{examId}/papers/{paperId}/marks"
    )
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @Transactional
    public Map<String, Object> saveBulkMarks(
            @PathVariable Long examId,
            @PathVariable Long paperId,
            @RequestBody BulkMarksRequest request,
            Authentication auth
    ) {
        var e = exam(examId);
        var p = paper(examId, paperId);

        ensureNotPublished(e);
        requirePaperAccess(auth, e, p);

        if (request == null ||
                request.records() == null ||
                request.records().isEmpty()) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Marks records are required"
            );
        }

        BigDecimal maximum = decimal(p.get("max_marks"));

        Set<Long> seen = new HashSet<>();

        // Validate the entire batch before saving.
        for (MarkItem item : request.records()) {

            if (item == null ||
                    item.studentId() == null ||
                    item.marksObtained() == null) {
                throw error(
                        HttpStatus.BAD_REQUEST,
                        "Student ID and marks are required"
                );
            }

            if (!seen.add(item.studentId())) {
                throw error(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate student in request"
                );
            }

            if (!eligibleStudent(e, item.studentId())) {
                throw error(
                        HttpStatus.BAD_REQUEST,
                        "Student is not enrolled in this exam scope"
                );
            }

            BigDecimal value = item.marksObtained();

            if (value.compareTo(BigDecimal.ZERO) < 0 ||
                    value.compareTo(maximum) > 0 ||
                    value.scale() > 2) {
                throw error(
                        HttpStatus.BAD_REQUEST,
                        "Marks must be between 0 and " +
                                maximum
                );
            }
        }

        for (MarkItem item : request.records()) {

            jdbc.update("""
                INSERT INTO marks (
                    exam_subject_id,
                    student_id,
                    marks_obtained
                )
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    marks_obtained = VALUES(marks_obtained)
                """,
                    paperId,
                    item.studentId(),
                    item.marksObtained());
        }

        return Map.of(
                "message", "Marks saved successfully",
                "examId", examId,
                "paperId", paperId,
                "savedRecords", request.records().size()
        );
    }

    // ================= GRADING =================

    private String calculateGrade(
            BigDecimal percentage
    ) {
        List<String> grades = jdbc.query("""
            SELECT grade
            FROM grading_rules
            WHERE is_active = TRUE
              AND min_percentage <= ?
              AND max_percentage >= ?
            ORDER BY min_percentage DESC
            LIMIT 1
            """,
                (rs, index) -> rs.getString("grade"),
                percentage,
                percentage);

        return grades.isEmpty()
                ? "UNASSIGNED"
                : grades.get(0);
    }

    private Map<String, Object> buildReport(
            Long examId,
            Long studentId
    ) {
        Map<String, Object> e = exam(examId);

        if (!eligibleStudent(e, studentId)) {
            throw error(
                    HttpStatus.NOT_FOUND,
                    "Student is not enrolled in this exam"
            );
        }

        List<Map<String, Object>> students =
                jdbc.queryForList("""
                    SELECT id, full_name, admission_number
                    FROM students
                    WHERE id = ?
                    """, studentId);

        List<Map<String, Object>> rows =
                jdbc.queryForList("""
                    SELECT
                        ep.id AS paperId,
                        s.name AS subjectName,
                        ep.max_marks AS maxMarks,
                        ep.pass_marks AS passingMarks,
                        m.marks_obtained AS obtainedMarks
                    FROM exam_subjects ep
                    JOIN subjects s ON s.id = ep.subject_id
                    LEFT JOIN marks m
                        ON m.exam_subject_id = ep.id
                       AND m.student_id = ?
                    WHERE ep.exam_id = ?
                    ORDER BY ep.id
                    """, studentId, examId);

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal maximum = BigDecimal.ZERO;
        boolean complete = !rows.isEmpty();
        boolean passed = !rows.isEmpty();

        List<Map<String, Object>> subjectResults =
                new ArrayList<>();

        for (var row : rows) {

            BigDecimal max = decimal(row.get("maxMarks"));
            BigDecimal pass = decimal(row.get("passingMarks"));

            Object recorded = row.get("obtainedMarks");

            BigDecimal obtained = recorded == null
                    ? null
                    : decimal(recorded);

            maximum = maximum.add(max);

            String status = "PENDING";

            if (obtained == null) {
                complete = false;
                passed = false;
            } else {
                total = total.add(obtained);

                status = obtained.compareTo(pass) >= 0
                        ? "PASS"
                        : "FAIL";

                if ("FAIL".equals(status)) {
                    passed = false;
                }
            }

            Map<String, Object> result =
                    new LinkedHashMap<>(row);

            result.put("status", status);
            subjectResults.add(result);
        }

        BigDecimal percentage = maximum.signum() == 0
                ? BigDecimal.ZERO
                : total.multiply(new BigDecimal("100"))
                .divide(maximum, 2, RoundingMode.HALF_UP);

        String grade = complete
                ? calculateGrade(percentage)
                : "PENDING";

        Map<String, Object> report =
                new LinkedHashMap<>();

        report.put("examId", examId);
        report.put("examName", e.get("name"));
        report.put("academicYear", e.get("academic_year"));
        report.put("examStatus", e.get("status"));
        report.put("student", students.get(0));
        report.put("subjects", subjectResults);
        report.put("totalMarks", total);
        report.put("maximumMarks", maximum);
        report.put("percentage", percentage);
        report.put("grade", grade);
        report.put(
                "result",
                !complete ? "PENDING" :
                        passed ? "PASS" : "FAIL"
        );
        report.put("complete", complete);

        return report;
    }

    // ================= EXAM RESULTS =================

    @GetMapping("/admin/exams/{examId}/results")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getExamResults(
            @PathVariable Long examId
    ) {
        var e = exam(examId);

        List<Map<String, Object>> students =
                eligibleStudents(e);

        List<Map<String, Object>> reports =
                new ArrayList<>();

        for (var student : students) {
            reports.add(
                    buildReport(
                            examId,
                            number(student.get("studentId"))
                    )
            );
        }

        return Map.of(
                "examId", examId,
                "examName", e.get("name"),
                "status", e.get("status"),
                "totalStudents", students.size(),
                "results", reports
        );
    }

    @GetMapping(
            "/admin/exams/{examId}/students/{studentId}/report-card"
    )
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getReportCard(
            @PathVariable Long examId,
            @PathVariable Long studentId
    ) {
        return buildReport(examId, studentId);
    }

    // ================= PUBLISH RESULTS =================

    @PostMapping("/admin/exams/{examId}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String, Object> publish(
            @PathVariable Long examId
    ) {
        var e = exam(examId);
        ensureNotPublished(e);

        List<Map<String, Object>> examPapers =
                papers(examId);

        if (examPapers.isEmpty()) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Add exam papers before publishing"
            );
        }

        List<Map<String, Object>> students =
                eligibleStudents(e);

        if (students.isEmpty()) {
            throw error(
                    HttpStatus.CONFLICT,
                    "No enrolled students found for this exam"
            );
        }

        // Every student must have marks in every paper.
        for (var student : students) {
            var report = buildReport(
                    examId,
                    number(student.get("studentId"))
            );

            if (!Boolean.TRUE.equals(
                    report.get("complete")
            )) {
                throw error(
                        HttpStatus.CONFLICT,
                        "Enter all student marks before publishing"
                );
            }

            if ("UNASSIGNED".equals(report.get("grade"))) {
                throw error(
                        HttpStatus.CONFLICT,
                        "Configure grading rules for all result percentages"
                );
            }
        }

        int updated = jdbc.update("""
            UPDATE exams
            SET status = 'Published',
                published_at = NOW()
            WHERE id = ?
              AND status <> 'Published'
            """, examId);

        if (updated != 1) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Examination could not be published"
            );
        }

        return Map.of(
                "message", "Results published successfully",
                "examId", examId,
                "status", "Published",
                "totalStudents", students.size()
        );
    }

    @PostMapping("/admin/exams/{examId}/unpublish")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String, Object> unpublish(
            @PathVariable Long examId
    ) {
        var e = exam(examId);

        if (!"Published".equals(
                String.valueOf(e.get("status"))
        )) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Examination is not published"
            );
        }

        jdbc.update("""
            UPDATE exams
            SET status = 'Draft',
                published_at = NULL
            WHERE id = ?
            """, examId);

        return Map.of(
                "message", "Examination reopened successfully",
                "examId", examId,
                "status", "Draft"
        );
    }
}
