
CREATE TABLE teacher_assignments (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     teacher_id BIGINT NOT NULL,
                                     section_id BIGINT NOT NULL,
                                     subject_id BIGINT NOT NULL,
                                     academic_year VARCHAR(20) NOT NULL,
                                     active BOOLEAN NOT NULL DEFAULT TRUE,

                                     CONSTRAINT fk_assignment_teacher
                                         FOREIGN KEY (teacher_id) REFERENCES teachers(id),

                                     CONSTRAINT fk_assignment_section
                                         FOREIGN KEY (section_id) REFERENCES sections(id),

                                     CONSTRAINT fk_assignment_subject
                                         FOREIGN KEY (subject_id) REFERENCES subjects(id),

                                     CONSTRAINT uq_teacher_assignment
                                         UNIQUE (teacher_id, section_id, subject_id, academic_year)
);

CREATE TABLE enrollments (
                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                             student_id BIGINT NOT NULL,
                             section_id BIGINT NOT NULL,
                             academic_year VARCHAR(20) NOT NULL,
                             enrollment_date DATE NOT NULL,
                             status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

                             CONSTRAINT fk_enrollment_student
                                 FOREIGN KEY (student_id) REFERENCES students(id),

                             CONSTRAINT fk_enrollment_section
                                 FOREIGN KEY (section_id) REFERENCES sections(id),

                             CONSTRAINT uq_student_academic_year
                                 UNIQUE (student_id, academic_year)
);
