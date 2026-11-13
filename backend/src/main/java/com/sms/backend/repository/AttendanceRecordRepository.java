
package com.sms.backend.repository;

import com.sms.backend.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository
        extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findBySessionIdOrderByStudentIdAsc(
            Long sessionId
    );

    Optional<AttendanceRecord> findBySessionIdAndStudentId(
            Long sessionId,
            Long studentId
    );
}
