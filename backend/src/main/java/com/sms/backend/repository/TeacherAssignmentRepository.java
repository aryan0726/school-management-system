
package com.sms.backend.repository;

import com.sms.backend.entity.TeacherAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeacherAssignmentRepository
        extends JpaRepository<TeacherAssignment, Long> {

    boolean existsByTeacherIdAndSectionIdAndSubjectIdAndAcademicYear(
            Long teacherId,
            Long sectionId,
            Long subjectId,
            String academicYear
    );

    boolean existsByTeacherIdAndSectionIdAndAcademicYearAndActiveTrue(
            Long teacherId,
            Long sectionId,
            String academicYear
    );

    List<TeacherAssignment> findByTeacherIdAndActiveTrue(
            Long teacherId
    );
}
