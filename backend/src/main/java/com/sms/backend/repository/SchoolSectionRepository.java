
package com.sms.backend.repository;

import com.sms.backend.entity.SchoolSection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SchoolSectionRepository
        extends JpaRepository<SchoolSection, Long> {

    List<SchoolSection> findBySchoolClass_IdOrderByNameAsc(
            Long classId
    );

    boolean existsBySchoolClass_IdAndNameIgnoreCase(
            Long classId,
            String name
    );
}
