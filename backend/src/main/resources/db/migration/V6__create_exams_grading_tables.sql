
CREATE TABLE exams (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       section_id BIGINT NOT NULL,
                       name VARCHAR(100) NOT NULL,
                       academic_year VARCHAR(20) NOT NULL,
                       start_date DATE,
                       end_date DATE,

                       CONSTRAINT fk_exam_section
                           FOREIGN KEY (section_id) REFERENCES sections(id)
);

CREATE TABLE exam_subjects (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               exam_id BIGINT NOT NULL,
                               subject_id BIGINT NOT NULL,
                               max_marks DECIMAL(6,2) NOT NULL,
                               pass_marks DECIMAL(6,2) NOT NULL,

                               CONSTRAINT fk_exam_subject_exam
                                   FOREIGN KEY (exam_id) REFERENCES exams(id),

                               CONSTRAINT fk_exam_subject_subject
                                   FOREIGN KEY (subject_id) REFERENCES subjects(id),

                               CONSTRAINT uq_exam_subject
                                   UNIQUE (exam_id, subject_id),

                               CONSTRAINT chk_exam_marks
                                   CHECK (max_marks > 0 AND pass_marks >= 0
                                       AND pass_marks <= max_marks)
);

CREATE TABLE marks (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       exam_subject_id BIGINT NOT NULL,
                       student_id BIGINT NOT NULL,
                       marks_obtained DECIMAL(6,2) NOT NULL,

                       CONSTRAINT fk_marks_exam_subject
                           FOREIGN KEY (exam_subject_id) REFERENCES exam_subjects(id),

                       CONSTRAINT fk_marks_student
                           FOREIGN KEY (student_id) REFERENCES students(id),

                       CONSTRAINT uq_student_exam_subject
                           UNIQUE (exam_subject_id, student_id),

                       CONSTRAINT chk_marks_nonnegative
                           CHECK (marks_obtained >= 0)
);

CREATE TABLE grading_rules (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               grade VARCHAR(10) NOT NULL UNIQUE,
                               min_percentage DECIMAL(5,2) NOT NULL,
                               max_percentage DECIMAL(5,2) NOT NULL,

                               CONSTRAINT chk_grading_range
                                   CHECK (
                                       min_percentage >= 0
                                           AND max_percentage <= 100
                                           AND min_percentage <= max_percentage
                                       )
);
