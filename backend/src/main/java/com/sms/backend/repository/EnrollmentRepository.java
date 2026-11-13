
package com.sms.backend.repository;

import com.sms.backend.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository
        extends JpaRepository<Enrollment, Long> {

    boolean existsByStudentIdAndAcademicYear(
            Long studentId,
            String academicYear
    );
}
