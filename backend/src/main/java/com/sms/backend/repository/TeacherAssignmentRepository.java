
package com.sms.backend.repository;

import com.sms.backend.entity.TeacherAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherAssignmentRepository
        extends JpaRepository<TeacherAssignment, Long> {

    boolean existsByTeacherIdAndSectionIdAndSubjectIdAndAcademicYear(
            Long teacherId,
            Long sectionId,
            Long subjectId,
            String academicYear
    );
}
