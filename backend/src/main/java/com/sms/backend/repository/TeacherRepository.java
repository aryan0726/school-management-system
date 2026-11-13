
package com.sms.backend.repository;

import com.sms.backend.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeacherRepository
        extends JpaRepository<Teacher, Long> {

    boolean existsByEmployeeNumber(String employeeNumber);

    Optional<Teacher> findByUser_EmailIgnoreCase(String email);
}
