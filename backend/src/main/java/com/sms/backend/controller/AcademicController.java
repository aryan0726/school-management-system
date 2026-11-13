
package com.sms.backend.controller;

import com.sms.backend.entity.*;
import com.sms.backend.repository.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/academic")
@PreAuthorize("hasRole('ADMIN')")
public class AcademicController {

    private final SubjectRepository subjectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherAssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final SchoolSectionRepository sectionRepository;

    public AcademicController(
            SubjectRepository subjectRepository,
            EnrollmentRepository enrollmentRepository,
            TeacherAssignmentRepository assignmentRepository,
            StudentRepository studentRepository,
            TeacherRepository teacherRepository,
            SchoolSectionRepository sectionRepository) {

        this.subjectRepository = subjectRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.sectionRepository = sectionRepository;
    }

    // ===========================
    // SUBJECT MANAGEMENT
    // ===========================

    @GetMapping("/subjects")
    public List<Subject> getSubjects() {
        return subjectRepository.findAll();
    }

    @PostMapping("/subjects")
    @Transactional
    public ResponseEntity<?> createSubject(
            @Valid @RequestBody SubjectRequest request) {

        String code = request.code().trim().toUpperCase(Locale.ROOT);

        if (subjectRepository.existsByCodeIgnoreCase(code)) {
            return conflict("Subject code already exists");
        }

        Subject subject = new Subject();
        subject.setName(request.name().trim());
        subject.setCode(code);
        subject.setActive(true);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subjectRepository.save(subject));
    }

    @PutMapping("/subjects/{id}")
    @Transactional
    public ResponseEntity<?> updateSubject(
            @PathVariable Long id,
            @Valid @RequestBody SubjectRequest request) {

        Subject subject = subjectRepository.findById(id).orElse(null);

        if (subject == null) {
            return notFound("Subject not found");
        }

        String code = request.code().trim().toUpperCase(Locale.ROOT);

        if (!subject.getCode().equalsIgnoreCase(code)
                && subjectRepository.existsByCodeIgnoreCase(code)) {

            return conflict("Subject code already exists");
        }

        subject.setName(request.name().trim());
        subject.setCode(code);

        return ResponseEntity.ok(subjectRepository.save(subject));
    }

    @PatchMapping("/subjects/{id}/deactivate")
    @Transactional
    public ResponseEntity<?> deactivateSubject(@PathVariable Long id) {

        Subject subject = subjectRepository.findById(id).orElse(null);

        if (subject == null) {
            return notFound("Subject not found");
        }

        subject.setActive(false);

        return ResponseEntity.ok(subjectRepository.save(subject));
    }

    // ===========================
    // STUDENT ENROLLMENTS
    // ===========================

    @GetMapping("/enrollments")
    public List<Enrollment> getEnrollments() {
        return enrollmentRepository.findAll();
    }

    @PostMapping("/enrollments")
    @Transactional
    public ResponseEntity<?> createEnrollment(
            @Valid @RequestBody EnrollmentRequest request) {

        String year = request.academicYear().trim();

        String validationError = validateEnrollmentReferences(request);

        if (validationError != null) {
            return conflict(validationError);
        }

        if (enrollmentRepository.existsByStudentIdAndAcademicYear(
                request.studentId(), year)) {

            return conflict(
                    "Student is already enrolled for this academic year"
            );
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(request.studentId());
        enrollment.setSectionId(request.sectionId());
        enrollment.setAcademicYear(year);
        enrollment.setEnrollmentDate(request.enrollmentDate());
        enrollment.setStatus("ACTIVE");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enrollmentRepository.save(enrollment));
    }

    @PutMapping("/enrollments/{id}")
    @Transactional
    public ResponseEntity<?> updateEnrollment(
            @PathVariable Long id,
            @Valid @RequestBody EnrollmentRequest request) {

        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElse(null);

        if (enrollment == null) {
            return notFound("Enrollment not found");
        }

        if (!"ACTIVE".equals(enrollment.getStatus())) {
            return conflict("Only active enrollments can be edited");
        }

        String validationError = validateEnrollmentReferences(request);

        if (validationError != null) {
            return conflict(validationError);
        }

        String year = request.academicYear().trim();

        boolean changedStudentYear =
                !enrollment.getStudentId().equals(request.studentId())
                        || !enrollment.getAcademicYear().equals(year);

        if (changedStudentYear
                && enrollmentRepository.existsByStudentIdAndAcademicYear(
                request.studentId(), year)) {

            return conflict(
                    "Student is already enrolled for this academic year"
            );
        }

        enrollment.setStudentId(request.studentId());
        enrollment.setSectionId(request.sectionId());
        enrollment.setAcademicYear(year);
        enrollment.setEnrollmentDate(request.enrollmentDate());

        return ResponseEntity.ok(enrollmentRepository.save(enrollment));
    }

    @PatchMapping("/enrollments/{id}/status")
    @Transactional
    public ResponseEntity<?> updateEnrollmentStatus(
            @PathVariable Long id,
            @Valid @RequestBody EnrollmentStatusRequest request) {

        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElse(null);

        if (enrollment == null) {
            return notFound("Enrollment not found");
        }

        String status = request.status()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!Set.of("WITHDRAWN", "COMPLETED").contains(status)) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            "Status must be WITHDRAWN or COMPLETED"
                    )
            );
        }

        if (!"ACTIVE".equals(enrollment.getStatus())) {
            return conflict("Enrollment is already closed");
        }

        enrollment.setStatus(status);

        return ResponseEntity.ok(enrollmentRepository.save(enrollment));
    }

    private String validateEnrollmentReferences(
            EnrollmentRequest request) {

        Student student = studentRepository.findById(request.studentId())
                .orElse(null);

        if (student == null) {
            return "Student not found";
        }

        if (!Boolean.TRUE.equals(student.getActive())) {
            return "Cannot enroll an inactive student";
        }

        SchoolSection section =
                sectionRepository.findById(request.sectionId())
                        .orElse(null);

        if (section == null) {
            return "Section not found";
        }

        if (!Boolean.TRUE.equals(
                section.getSchoolClass().getActive())) {

            return "Cannot enroll in an inactive class";
        }

        return null;
    }

    // ===========================
    // TEACHER ASSIGNMENTS
    // ===========================

    @GetMapping("/assignments")
    public List<TeacherAssignment> getAssignments() {
        return assignmentRepository.findAll();
    }

    @PostMapping("/assignments")
    @Transactional
    public ResponseEntity<?> createAssignment(
            @Valid @RequestBody AssignmentRequest request) {

        String validationError = validateAssignmentReferences(request);

        if (validationError != null) {
            return conflict(validationError);
        }

        String year = request.academicYear().trim();

        if (assignmentRepository
                .existsByTeacherIdAndSectionIdAndSubjectIdAndAcademicYear(
                        request.teacherId(),
                        request.sectionId(),
                        request.subjectId(),
                        year)) {

            return conflict("Teacher assignment already exists");
        }

        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setTeacherId(request.teacherId());
        assignment.setSectionId(request.sectionId());
        assignment.setSubjectId(request.subjectId());
        assignment.setAcademicYear(year);
        assignment.setActive(true);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentRepository.save(assignment));
    }

    @PutMapping("/assignments/{id}")
    @Transactional
    public ResponseEntity<?> updateAssignment(
            @PathVariable Long id,
            @Valid @RequestBody AssignmentRequest request) {

        TeacherAssignment assignment = assignmentRepository.findById(id)
                .orElse(null);

        if (assignment == null) {
            return notFound("Assignment not found");
        }

        if (!Boolean.TRUE.equals(assignment.getActive())) {
            return conflict("Cannot edit inactive assignment");
        }

        String validationError = validateAssignmentReferences(request);

        if (validationError != null) {
            return conflict(validationError);
        }

        String year = request.academicYear().trim();

        boolean changed =
                !assignment.getTeacherId().equals(request.teacherId())
                        || !assignment.getSectionId().equals(request.sectionId())
                        || !assignment.getSubjectId().equals(request.subjectId())
                        || !assignment.getAcademicYear().equals(year);

        if (changed
                && assignmentRepository
                .existsByTeacherIdAndSectionIdAndSubjectIdAndAcademicYear(
                        request.teacherId(),
                        request.sectionId(),
                        request.subjectId(),
                        year)) {

            return conflict("Teacher assignment already exists");
        }

        assignment.setTeacherId(request.teacherId());
        assignment.setSectionId(request.sectionId());
        assignment.setSubjectId(request.subjectId());
        assignment.setAcademicYear(year);

        return ResponseEntity.ok(assignmentRepository.save(assignment));
    }

    @PatchMapping("/assignments/{id}/deactivate")
    @Transactional
    public ResponseEntity<?> deactivateAssignment(@PathVariable Long id) {

        TeacherAssignment assignment = assignmentRepository.findById(id)
                .orElse(null);

        if (assignment == null) {
            return notFound("Assignment not found");
        }

        assignment.setActive(false);

        return ResponseEntity.ok(assignmentRepository.save(assignment));
    }

    private String validateAssignmentReferences(
            AssignmentRequest request) {

        Teacher teacher = teacherRepository.findById(request.teacherId())
                .orElse(null);

        if (teacher == null) {
            return "Teacher not found";
        }

        if (!Boolean.TRUE.equals(teacher.getActive())
                || !Boolean.TRUE.equals(teacher.getUser().getActive())) {

            return "Cannot assign an inactive teacher";
        }

        Subject subject = subjectRepository.findById(request.subjectId())
                .orElse(null);

        if (subject == null) {
            return "Subject not found";
        }

        if (!Boolean.TRUE.equals(subject.getActive())) {
            return "Cannot assign an inactive subject";
        }

        SchoolSection section =
                sectionRepository.findById(request.sectionId())
                        .orElse(null);

        if (section == null) {
            return "Section not found";
        }

        if (!Boolean.TRUE.equals(
                section.getSchoolClass().getActive())) {

            return "Cannot assign teacher to an inactive class";
        }

        return null;
    }

    // ===========================
    // ERROR HELPERS AND DTOs
    // ===========================

    private ResponseEntity<?> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", message));
    }

    private ResponseEntity<?> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", message));
    }

    public record SubjectRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 20) String code
    ) {}

    public record EnrollmentRequest(
            @NotNull Long studentId,
            @NotNull Long sectionId,
            @NotBlank @Size(max = 20) String academicYear,
            @NotNull LocalDate enrollmentDate
    ) {}

    public record EnrollmentStatusRequest(
            @NotBlank String status
    ) {}

    public record AssignmentRequest(
            @NotNull Long teacherId,
            @NotNull Long sectionId,
            @NotNull Long subjectId,
            @NotBlank @Size(max = 20) String academicYear
    ) {}
}
