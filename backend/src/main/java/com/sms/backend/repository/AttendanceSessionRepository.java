
package com.sms.backend.repository;

import com.sms.backend.entity.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceSessionRepository
        extends JpaRepository<AttendanceSession, Long> {

    boolean existsBySectionIdAndAttendanceDateAndSessionType(
            Long sectionId,
            LocalDate attendanceDate,
            String sessionType
    );

    List<AttendanceSession> findAllByOrderByAttendanceDateDesc();

    List<AttendanceSession> findByMarkedByOrderByAttendanceDateDesc(
            Long markedBy
    );
}
