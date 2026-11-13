
package com.sms.backend.controller;

import com.sms.backend.entity.SchoolSection;
import com.sms.backend.entity.Teacher;
import com.sms.backend.entity.TeacherAssignment;
import com.sms.backend.repository.SchoolSectionRepository;
import com.sms.backend.repository.TeacherAssignmentRepository;
import com.sms.backend.repository.TeacherRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
public class TeacherSectionController {

    private final TeacherRepository teacherRepository;
    private final TeacherAssignmentRepository assignmentRepository;
    private final SchoolSectionRepository sectionRepository;

    public TeacherSectionController(
            TeacherRepository teacherRepository,
            TeacherAssignmentRepository assignmentRepository,
            SchoolSectionRepository sectionRepository) {

        this.teacherRepository = teacherRepository;
        this.assignmentRepository = assignmentRepository;
        this.sectionRepository = sectionRepository;
    }

    @GetMapping("/my-sections")
    @PreAuthorize("hasRole('TEACHER')")
    public List<AssignedSectionResponse> getMySections(
            Authentication authentication) {

        Teacher teacher = teacherRepository
                .findByUser_EmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Teacher profile not found"
                ));

        if (!Boolean.TRUE.equals(teacher.getActive())
                || !Boolean.TRUE.equals(teacher.getUser().getActive())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Teacher account is inactive"
            );
        }

        List<TeacherAssignment> assignments =
                assignmentRepository.findByTeacherIdAndActiveTrue(
                        teacher.getId()
                );

        // A teacher may teach multiple subjects in one section.
        // Display each section/year combination only once.
        Map<String, AssignedSectionResponse> unique =
                new LinkedHashMap<>();

        for (TeacherAssignment assignment : assignments) {

            SchoolSection section = sectionRepository
                    .findById(assignment.getSectionId())
                    .orElse(null);

            if (section == null
                    || !Boolean.TRUE.equals(
                    section.getSchoolClass().getActive())) {
                continue;
            }

            String key = section.getId()
                    + ":" + assignment.getAcademicYear();

            unique.putIfAbsent(
                    key,
                    new AssignedSectionResponse(
                            section.getId(),
                            section.getSchoolClass().getName(),
                            section.getName(),
                            assignment.getAcademicYear()
                    )
            );
        }

        return unique.values().stream()
                .sorted(
                        Comparator
                                .comparing(
                                        AssignedSectionResponse::className
                                )
                                .thenComparing(
                                        AssignedSectionResponse::sectionName
                                )
                                .thenComparing(
                                        AssignedSectionResponse::academicYear
                                )
                )
                .toList();
    }

    public record AssignedSectionResponse(
            Long sectionId,
            String className,
            String sectionName,
            String academicYear
    ) {}
}
