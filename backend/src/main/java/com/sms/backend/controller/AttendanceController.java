
package com.sms.backend.controller;

import com.sms.backend.entity.*;
import com.sms.backend.repository.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class AttendanceController {

    private final AttendanceSessionRepository sessionRepo;
    private final AttendanceRecordRepository recordRepo;
    private final TeacherRepository teacherRepo;
    private final TeacherAssignmentRepository assignmentRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final StudentRepository studentRepo;
    private final SchoolSectionRepository sectionRepo;

    public AttendanceController(
            AttendanceSessionRepository sessionRepo,
            AttendanceRecordRepository recordRepo,
            TeacherRepository teacherRepo,
            TeacherAssignmentRepository assignmentRepo,
            EnrollmentRepository enrollmentRepo,
            StudentRepository studentRepo,
            SchoolSectionRepository sectionRepo) {

        this.sessionRepo = sessionRepo;
        this.recordRepo = recordRepo;
        this.teacherRepo = teacherRepo;
        this.assignmentRepo = assignmentRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.studentRepo = studentRepo;
        this.sectionRepo = sectionRepo;
    }

    // GET CURRENT LOGGED-IN TEACHER
    private Teacher currentTeacher(Authentication auth) {

        Teacher teacher = teacherRepo
                .findByUser_EmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Teacher profile not found"
                ));

        if (!Boolean.TRUE.equals(teacher.getActive())
                || !Boolean.TRUE.equals(
                teacher.getUser().getActive())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Teacher account is inactive"
            );
        }

        return teacher;
    }

    // VERIFY TEACHER'S SECTION ASSIGNMENT
    private void verifyAssignment(
            Teacher teacher,
            Long sectionId,
            String academicYear) {

        SchoolSection section = sectionRepo.findById(sectionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Section not found"
                ));

        if (!Boolean.TRUE.equals(
                section.getSchoolClass().getActive())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Class is inactive"
            );
        }

        boolean assigned = assignmentRepo
                .existsByTeacherIdAndSectionIdAndAcademicYearAndActiveTrue(
                        teacher.getId(),
                        sectionId,
                        academicYear
                );

        if (!assigned) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Teacher is not assigned to this section"
            );
        }
    }

    // FIND ELIGIBLE STUDENTS
    private List<Student> eligibleStudents(
            Long sectionId,
            String academicYear,
            LocalDate attendanceDate) {

        return enrollmentRepo
                .findBySectionIdAndAcademicYearAndStatus(
                        sectionId,
                        academicYear,
                        "ACTIVE"
                )
                .stream()
                .filter(e -> !e.getEnrollmentDate()
                        .isAfter(attendanceDate))
                .map(e -> studentRepo.findById(e.getStudentId())
                        .orElse(null))
                .filter(s -> s != null
                        && Boolean.TRUE.equals(s.getActive()))
                .toList();
    }

    // 1. GET ROSTER FOR ASSIGNED SECTION
    @GetMapping("/sections/{sectionId}/roster")
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional(readOnly = true)
    public List<StudentView> getRoster(
            @PathVariable Long sectionId,
            @RequestParam String academicYear,
            @RequestParam(required = false)
            LocalDate attendanceDate,
            Authentication auth) {

        Teacher teacher = currentTeacher(auth);

        String year = academicYear.trim();
        verifyAssignment(teacher, sectionId, year);

        LocalDate date = attendanceDate != null
                ? attendanceDate
                : LocalDate.now();

        return eligibleStudents(sectionId, year, date)
                .stream()
                .map(s -> new StudentView(
                        s.getId(),
                        s.getAdmissionNumber(),
                        s.getFullName()
                ))
                .toList();
    }

    // 2. MARK FULL DAILY ATTENDANCE
    @PostMapping("/sessions")
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public ResponseEntity<?> markAttendance(
            @Valid @RequestBody MarkAttendanceRequest request,
            Authentication auth) {

        Teacher teacher = currentTeacher(auth);

        String year = request.academicYear().trim();
        Long sectionId = request.sectionId();
        LocalDate date = request.attendanceDate();

        verifyAssignment(teacher, sectionId, year);

        if (date.isAfter(LocalDate.now())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Attendance date cannot be in the future"
                    ));
        }

        // One DAILY session per section and date
        if (sessionRepo
                .existsBySectionIdAndAttendanceDateAndSessionType(
                        sectionId,
                        date,
                        "DAILY")) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Daily attendance is already marked"
                    ));
        }

        List<Student> roster =
                eligibleStudents(sectionId, year, date);

        if (roster.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "No eligible students in this section"
                    ));
        }

        Set<Long> eligibleIds = roster.stream()
                .map(Student::getId)
                .collect(Collectors.toSet());

        List<AttendanceMark> marks = request.records();

        Set<Long> submittedIds = marks.stream()
                .map(AttendanceMark::studentId)
                .collect(Collectors.toSet());

        // Reject duplicates, missing students,
        // or students from another section
        if (submittedIds.size() != marks.size()
                || !submittedIds.equals(eligibleIds)) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Submit exactly one attendance record "
                                    + "for every eligible student"
                    ));
        }

        AttendanceSession session = new AttendanceSession();
        session.setSectionId(sectionId);
        session.setAttendanceDate(date);
        session.setSessionType("DAILY");
        session.setMarkedBy(teacher.getId());

        AttendanceSession savedSession =
                sessionRepo.save(session);

        List<AttendanceRecord> records = marks.stream()
                .map(mark -> {

                    AttendanceRecord record =
                            new AttendanceRecord();

                    record.setSessionId(savedSession.getId());
                    record.setStudentId(mark.studentId());
                    record.setStatus(mark.status());

                    record.setRemarks(
                            mark.remarks() == null
                                    ? null
                                    : mark.remarks().trim()
                    );

                    return record;
                })
                .toList();

        recordRepo.saveAll(records);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Attendance marked successfully",
                        "sessionId", savedSession.getId(),
                        "totalStudents", records.size()
                ));
    }

    // 3. LIST ATTENDANCE SESSIONS
    @GetMapping("/sessions")
    @Transactional(readOnly = true)
    public List<SessionView> getSessions(Authentication auth) {

        boolean admin = isAdmin(auth);

        List<AttendanceSession> sessions;

        if (admin) {
            sessions =
                    sessionRepo.findAllByOrderByAttendanceDateDesc();
        } else {
            Teacher teacher = currentTeacher(auth);

            sessions =
                    sessionRepo.findByMarkedByOrderByAttendanceDateDesc(
                            teacher.getId()
                    );
        }

        return sessions.stream()
                .map(this::toSessionView)
                .toList();
    }

    // 4. GET A SESSION WITH STUDENT RECORDS
    @GetMapping("/sessions/{id}")
    @Transactional(readOnly = true)
    public SessionView getSession(
            @PathVariable Long id,
            Authentication auth) {

        AttendanceSession session = getAllowedSession(id, auth);

        return toSessionView(session);
    }

    // 5. CORRECT EXISTING STUDENT ATTENDANCE
    @PutMapping("/sessions/{id}/records/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public RecordView updateRecord(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @Valid @RequestBody UpdateRecordRequest request,
            Authentication auth) {

        AttendanceSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Attendance session not found"
                ));

        Teacher teacher = currentTeacher(auth);

        // Only the teacher who marked attendance can correct it
        if (!session.getMarkedBy().equals(teacher.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot edit another teacher's attendance"
            );
        }

        AttendanceRecord record = recordRepo
                .findBySessionIdAndStudentId(id, studentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Student attendance record not found"
                ));

        record.setStatus(request.status());

        record.setRemarks(
                request.remarks() == null
                        ? null
                        : request.remarks().trim()
        );

        return toRecordView(recordRepo.save(record));
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private AttendanceSession getAllowedSession(
            Long id,
            Authentication auth) {

        AttendanceSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Attendance session not found"
                ));

        if (!isAdmin(auth)) {
            Teacher teacher = currentTeacher(auth);

            if (!session.getMarkedBy().equals(teacher.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Access denied to this attendance session"
                );
            }
        }

        return session;
    }

    private SessionView toSessionView(AttendanceSession session) {

        List<RecordView> records = recordRepo
                .findBySessionIdOrderByStudentIdAsc(session.getId())
                .stream()
                .map(this::toRecordView)
                .toList();

        return new SessionView(
                session.getId(),
                session.getSectionId(),
                session.getAttendanceDate(),
                session.getSessionType(),
                session.getMarkedBy(),
                records
        );
    }

    private RecordView toRecordView(AttendanceRecord record) {

        String studentName = studentRepo.findById(
                        record.getStudentId())
                .map(Student::getFullName)
                .orElse("Unknown Student");

        return new RecordView(
                record.getId(),
                record.getStudentId(),
                studentName,
                record.getStatus(),
                record.getRemarks()
        );
    }

    // Convert database unique-constraint conflicts to HTTP 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDuplicate(
            DataIntegrityViolationException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "message",
                        "Duplicate or conflicting attendance record"
                ));
    }

    // REQUEST DTOs

    public record AttendanceMark(
            @NotNull @Positive Long studentId,
            @NotBlank
            @Pattern(regexp = "PRESENT|ABSENT|LATE|EXCUSED")
            String status,
            @Size(max = 255) String remarks
    ) {}

    public record MarkAttendanceRequest(
            @NotNull @Positive Long sectionId,
            @NotBlank
            @Pattern(regexp = "\\d{4}-\\d{4}")
            String academicYear,
            @NotNull LocalDate attendanceDate,
            @NotEmpty List<@Valid AttendanceMark> records
    ) {}

    public record UpdateRecordRequest(
            @NotBlank
            @Pattern(regexp = "PRESENT|ABSENT|LATE|EXCUSED")
            String status,
            @Size(max = 255) String remarks
    ) {}

    // RESPONSE DTOs

    public record StudentView(
            Long id,
            String admissionNumber,
            String fullName
    ) {}

    public record RecordView(
            Long id,
            Long studentId,
            String studentName,
            String status,
            String remarks
    ) {}

    public record SessionView(
            Long id,
            Long sectionId,
            LocalDate attendanceDate,
            String sessionType,
            Long markedBy,
            List<RecordView> records
    ) {}
}
