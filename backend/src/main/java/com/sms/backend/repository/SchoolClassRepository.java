
package com.sms.backend.repository;

import com.sms.backend.entity.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolClassRepository
        extends JpaRepository<SchoolClass, Long> {

    boolean existsByNameIgnoreCase(String name);
}
