
package com.sms.backend.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ExamCatalogController {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    private static final Set<String> EXAM_TYPES =
            Set.of("Unit test", "Mid-term", "Final", "Practical");

    private static final Set<String> EDITABLE_STATUSES =
            Set.of("Draft", "Ongoing");

    private static final BigDecimal MAX_ALLOWED_MARKS =
            new BigDecimal("9999.99");

    private static final String EXAM_SELECT = """
        SELECT
            e.id,
            e.class_id,
            e.section_id,
            e.name,
            e.academic_year,
            e.exam_type,
            e.start_date,
            e.end_date,
            e.status,
            e.description,
            e.published_at,
            c.name AS class_name,
            s.name AS section_name,
            (
                SELECT COUNT(*)
                FROM exam_subjects ep
                WHERE ep.exam_id = e.id
            ) AS papers,
            (
                SELECT COUNT(*)
                FROM marks m
                JOIN exam_subjects ep
                    ON ep.id = m.exam_subject_id
                WHERE ep.exam_id = e.id
            ) AS marksEntered
        FROM exams e
        JOIN classes c ON c.id = e.class_id
        LEFT JOIN sections s ON s.id = e.section_id
        """;

    public ExamCatalogController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    // ---------- EXAMS: READ ----------

    @GetMapping("/exams")
    public List<Map<String, Object>> listExams() {
        return jdbc.queryForList(
                EXAM_SELECT + " ORDER BY e.id DESC"
        );
    }

    @GetMapping("/exams/{id}")
    public Map<String, Object> getExam(@PathVariable Long id) {

        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> papers = listPapers(id);

        result.put("exam", findExam(id));
        result.put("papers", papers);
        result.put("totalPapers", papers.size());

        return result;
    }

    // ---------- EXAMS: CREATE ----------

    @PostMapping("/exams")
    @Transactional
    public ResponseEntity<Map<String, Object>> createExam(
            @RequestBody ExamRequest request
    ) {

        ExamRequest data = validateExam(request, "Draft");

        MapSqlParameterSource params = examParams(data);

        KeyHolder keyHolder = new GeneratedKeyHolder();

        namedJdbc.update("""
            INSERT INTO exams (
                class_id,
                section_id,
                name,
                academic_year,
                exam_type,
                start_date,
                end_date,
                status,
                description
            )
            VALUES (
                :classId,
                :sectionId,
                :name,
                :academicYear,
                :examType,
                :startDate,
                :endDate,
                :status,
                :description
            )
            """, params, keyHolder, new String[]{"id"});

        Long id = generatedId(keyHolder);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(getExam(id));
    }

    // ---------- EXAMS: UPDATE ----------

    @PutMapping("/exams/{id}")
    @Transactional
    public Map<String, Object> updateExam(
            @PathVariable Long id,
            @RequestBody ExamRequest request
    ) {

        Map<String, Object> existing = findExam(id);
        ensureEditable(existing);

        String previousStatus =
                String.valueOf(existing.get("status"));

        ExamRequest data = validateExam(
                request,
                previousStatus
        );

        MapSqlParameterSource params = examParams(data)
                .addValue("id", id);

        namedJdbc.update("""
            UPDATE exams SET
                class_id = :classId,
                section_id = :sectionId,
                name = :name,
                academic_year = :academicYear,
                exam_type = :examType,
                start_date = :startDate,
                end_date = :endDate,
                status = :status,
                description = :description
            WHERE id = :id
            """, params);

        return getExam(id);
    }

    // ---------- EXAMS: DELETE ----------

    @DeleteMapping("/exams/{id}")
    @Transactional
    public Map<String, String> deleteExam(
            @PathVariable Long id
    ) {

        Map<String, Object> exam = findExam(id);
        ensureEditable(exam);

        if (exists(
                "SELECT COUNT(*) FROM exam_subjects WHERE exam_id = ?",
                id
        )) {
            throw conflict(
                    "Remove the exam papers before deleting this examination."
            );
        }

        jdbc.update(
                "DELETE FROM exams WHERE id = ?",
                id
        );

        return Map.of(
                "message",
                "Examination deleted successfully"
        );
    }

    // ---------- EXAM PAPERS: READ ----------

    @GetMapping({
            "/exams/{id}/papers",
            "/exams/{id}/subjects"
    })
    public List<Map<String, Object>> listPapers(
            @PathVariable Long id
    ) {

        findExam(id);

        return jdbc.queryForList("""
            SELECT
                ep.id,
                ep.exam_id,
                ep.subject_id,
                s.name AS subject_name,
                ep.max_marks,
                ep.pass_marks,
                ep.pass_marks AS passing_marks,
                ep.exam_date,
                ep.teacher_id,
                (
                    SELECT COUNT(*)
                    FROM marks m
                    WHERE m.exam_subject_id = ep.id
                ) AS marksEntered
            FROM exam_subjects ep
            JOIN subjects s ON s.id = ep.subject_id
            WHERE ep.exam_id = ?
            ORDER BY ep.id
            """, id);
    }

    // ---------- EXAM PAPERS: CREATE ----------

    @PostMapping("/exams/{examId}/papers")
    @Transactional
    public ResponseEntity<Map<String, Object>> createPaper(
            @PathVariable Long examId,
            @RequestBody PaperRequest request
    ) {

        ensureEditable(findExam(examId));
        validatePaper(request);

        if (exists("""
                SELECT COUNT(*)
                FROM exam_subjects
                WHERE exam_id = ?
                  AND subject_id = ?
                """, examId, request.subjectId())) {
            throw conflict(
                    "This subject already has a paper in the examination."
            );
        }

        MapSqlParameterSource params =
                paperParams(request)
                        .addValue("examId", examId);

        KeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            namedJdbc.update("""
                INSERT INTO exam_subjects (
                    exam_id,
                    subject_id,
                    max_marks,
                    pass_marks,
                    exam_date,
                    teacher_id
                )
                VALUES (
                    :examId,
                    :subjectId,
                    :maxMarks,
                    :passingMarks,
                    :examDate,
                    :teacherId
                )
                """,
                    params,
                    keyHolder,
                    new String[]{"id"});
        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "Unable to create paper. Check for duplicate subjects."
            );
        }

        Long paperId = generatedId(keyHolder);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(findPaper(examId, paperId));
    }

    // ---------- EXAM PAPERS: UPDATE ----------

    @PutMapping("/exams/{examId}/papers/{paperId}")
    @Transactional
    public Map<String, Object> updatePaper(
            @PathVariable Long examId,
            @PathVariable Long paperId,
            @RequestBody PaperRequest request
    ) {

        ensureEditable(findExam(examId));

        Map<String, Object> existing =
                findPaper(examId, paperId);

        validatePaper(request);

        if (exists("""
                SELECT COUNT(*)
                FROM exam_subjects
                WHERE exam_id = ?
                  AND subject_id = ?
                  AND id <> ?
                """,
                examId,
                request.subjectId(),
                paperId
        )) {
            throw conflict(
                    "This subject already exists in this examination."
            );
        }

        boolean hasMarks = exists(
                "SELECT COUNT(*) FROM marks WHERE exam_subject_id = ?",
                paperId
        );

        Long oldSubjectId =
                ((Number) existing.get("subject_id")).longValue();

        if (hasMarks && !oldSubjectId.equals(request.subjectId())) {
            throw conflict(
                    "Cannot change a paper's subject after marks are entered."
            );
        }

        BigDecimal highestRecordedMark = jdbc.queryForObject(
                """
                SELECT MAX(marks_obtained)
                FROM marks
                WHERE exam_subject_id = ?
                """,
                BigDecimal.class,
                paperId
        );

        if (highestRecordedMark != null &&
                highestRecordedMark.compareTo(request.maxMarks()) > 0) {
            throw badRequest(
                    "Maximum marks cannot be lower than existing student marks."
            );
        }

        MapSqlParameterSource params =
                paperParams(request)
                        .addValue("paperId", paperId)
                        .addValue("examId", examId);

        namedJdbc.update("""
            UPDATE exam_subjects SET
                subject_id = :subjectId,
                max_marks = :maxMarks,
                pass_marks = :passingMarks,
                exam_date = :examDate,
                teacher_id = :teacherId
            WHERE id = :paperId
              AND exam_id = :examId
            """, params);

        return findPaper(examId, paperId);
    }

    // ---------- EXAM PAPERS: DELETE ----------

    @DeleteMapping("/exams/{examId}/papers/{paperId}")
    @Transactional
    public Map<String, String> deletePaper(
            @PathVariable Long examId,
            @PathVariable Long paperId
    ) {

        ensureEditable(findExam(examId));
        findPaper(examId, paperId);

        if (exists(
                "SELECT COUNT(*) FROM marks WHERE exam_subject_id = ?",
                paperId
        )) {
            throw conflict(
                    "Cannot delete a paper with recorded marks."
            );
        }

        jdbc.update(
                "DELETE FROM exam_subjects WHERE id = ? AND exam_id = ?",
                paperId,
                examId
        );

        return Map.of(
                "message",
                "Exam paper deleted successfully"
        );
    }

    // ---------- VALIDATION ----------

    private ExamRequest validateExam(
            ExamRequest request,
            String fallbackStatus
    ) {

        if (request == null ||
                request.classId() == null ||
                isBlank(request.name()) ||
                isBlank(request.academicYear())) {
            throw badRequest(
                    "Class, examination name and academic year are required."
            );
        }

        String name = request.name().trim();
        String academicYear = request.academicYear().trim();

        String examType = isBlank(request.examType())
                ? "Mid-term"
                : request.examType().trim();

        String status = isBlank(request.status())
                ? fallbackStatus
                : request.status().trim();

        String description = request.description() == null
                ? null
                : request.description().trim();

        if (name.length() > 100 || academicYear.length() > 20) {
            throw badRequest(
                    "Examination name or academic year is too long."
            );
        }

        if (description != null && description.length() > 500) {
            throw badRequest(
                    "Description cannot exceed 500 characters."
            );
        }

        if (!EXAM_TYPES.contains(examType)) {
            throw badRequest("Invalid examination type.");
        }

        if (!EDITABLE_STATUSES.contains(status)) {
            throw badRequest(
                    "Use the result publishing API to publish an examination."
            );
        }

        if (request.startDate() != null &&
                request.endDate() != null &&
                request.endDate().isBefore(request.startDate())) {
            throw badRequest(
                    "End date cannot be earlier than start date."
            );
        }

        if (!exists(
                "SELECT COUNT(*) FROM classes WHERE id = ? AND active = TRUE",
                request.classId()
        )) {
            throw badRequest("Active class not found.");
        }

        if (request.sectionId() != null &&
                !exists("""
                    SELECT COUNT(*)
                    FROM sections
                    WHERE id = ?
                      AND class_id = ?
                    """,
                        request.sectionId(),
                        request.classId()
                )) {
            throw badRequest(
                    "The selected section does not belong to this class."
            );
        }

        return new ExamRequest(
                request.classId(),
                request.sectionId(),
                name,
                academicYear,
                examType,
                request.startDate(),
                request.endDate(),
                status,
                description
        );
    }

    private void validatePaper(PaperRequest request) {

        if (request == null ||
                request.subjectId() == null ||
                request.maxMarks() == null ||
                request.passingMarks() == null) {
            throw badRequest(
                    "Subject, maximum marks and passing marks are required."
            );
        }

        if (request.maxMarks().compareTo(BigDecimal.ZERO) <= 0 ||
                request.maxMarks().compareTo(MAX_ALLOWED_MARKS) > 0 ||
                request.maxMarks().scale() > 2) {
            throw badRequest("Invalid maximum marks.");
        }

        if (request.passingMarks().compareTo(BigDecimal.ZERO) < 0 ||
                request.passingMarks().compareTo(request.maxMarks()) > 0 ||
                request.passingMarks().scale() > 2) {
            throw badRequest("Invalid passing marks.");
        }

        if (!exists(
                "SELECT COUNT(*) FROM subjects WHERE id = ? AND active = TRUE",
                request.subjectId()
        )) {
            throw badRequest("Active subject not found.");
        }

        if (request.teacherId() != null &&
                !exists(
                        "SELECT COUNT(*) FROM teachers WHERE id = ? AND active = TRUE",
                        request.teacherId()
                )) {
            throw badRequest("Active teacher not found.");
        }
    }

    // ---------- HELPERS ----------

    private Map<String, Object> findExam(Long id) {

        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        EXAM_SELECT + " WHERE e.id = ?",
                        id
                );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Examination not found"
            );
        }

        return rows.get(0);
    }

    private Map<String, Object> findPaper(
            Long examId,
            Long paperId
    ) {

        return listPapers(examId)
                .stream()
                .filter(row ->
                        ((Number) row.get("id")).longValue() == paperId
                )
                .findFirst()
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Exam paper not found"
                        )
                );
    }

    private void ensureEditable(Map<String, Object> exam) {

        if ("Published".equalsIgnoreCase(
                String.valueOf(exam.get("status"))
        )) {
            throw conflict(
                    "Published examinations must be reopened before editing."
            );
        }
    }

    private boolean exists(String query, Object... args) {

        Integer count = jdbc.queryForObject(
                query,
                Integer.class,
                args
        );

        return count != null && count > 0;
    }

    private MapSqlParameterSource examParams(
            ExamRequest request
    ) {

        return new MapSqlParameterSource()
                .addValue("classId", request.classId())
                .addValue("sectionId", request.sectionId())
                .addValue("name", request.name())
                .addValue("academicYear", request.academicYear())
                .addValue("examType", request.examType())
                .addValue("startDate", sqlDate(request.startDate()))
                .addValue("endDate", sqlDate(request.endDate()))
                .addValue("status", request.status())
                .addValue("description", request.description());
    }

    private MapSqlParameterSource paperParams(
            PaperRequest request
    ) {

        return new MapSqlParameterSource()
                .addValue("subjectId", request.subjectId())
                .addValue("maxMarks", request.maxMarks())
                .addValue("passingMarks", request.passingMarks())
                .addValue("examDate", sqlDate(request.examDate()))
                .addValue("teacherId", request.teacherId());
    }

    private Date sqlDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private Long generatedId(KeyHolder holder) {

        Number key = holder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "Database did not return the generated ID."
            );
        }

        return key.longValue();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    // ---------- REQUEST MODELS ----------

    public record ExamRequest(
            Long classId,
            Long sectionId,
            String name,
            String academicYear,
            String examType,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String description
    ) {}

    public record PaperRequest(
            Long subjectId,
            BigDecimal maxMarks,
            BigDecimal passingMarks,
            LocalDate examDate,
            Long teacherId
    ) {}
}
