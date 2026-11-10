
CREATE TABLE attendance_sessions (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     section_id BIGINT NOT NULL,
                                     attendance_date DATE NOT NULL,
                                     session_type VARCHAR(20) NOT NULL DEFAULT 'DAILY',
                                     marked_by BIGINT NOT NULL,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                     CONSTRAINT fk_attendance_section
                                         FOREIGN KEY (section_id) REFERENCES sections(id),

                                     CONSTRAINT fk_attendance_teacher
                                         FOREIGN KEY (marked_by) REFERENCES teachers(id),

                                     CONSTRAINT uq_attendance_session
                                         UNIQUE (section_id, attendance_date, session_type)
);

CREATE TABLE attendance_records (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    session_id BIGINT NOT NULL,
                                    student_id BIGINT NOT NULL,
                                    status VARCHAR(20) NOT NULL,
                                    remarks VARCHAR(255),

                                    CONSTRAINT fk_record_session
                                        FOREIGN KEY (session_id) REFERENCES attendance_sessions(id),

                                    CONSTRAINT fk_record_student
                                        FOREIGN KEY (student_id) REFERENCES students(id),

                                    CONSTRAINT uq_session_student
                                        UNIQUE (session_id, student_id)
);
