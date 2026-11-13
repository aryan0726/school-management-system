
package com.sms.backend.repository;

import com.sms.backend.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository
        extends JpaRepository<Subject, Long> {

    boolean existsByCodeIgnoreCase(String code);
}
