
CREATE TABLE students (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          admission_number VARCHAR(50) NOT NULL UNIQUE,
                          full_name VARCHAR(100) NOT NULL,
                          date_of_birth DATE,
                          gender VARCHAR(20),
                          active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE guardians (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           user_id BIGINT NOT NULL UNIQUE,
                           phone VARCHAR(20),
                           address TEXT,

                           CONSTRAINT fk_guardian_user
                               FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE teachers (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          user_id BIGINT NOT NULL UNIQUE,
                          employee_number VARCHAR(50) NOT NULL UNIQUE,
                          qualification VARCHAR(150),
                          joining_date DATE,
                          active BOOLEAN NOT NULL DEFAULT TRUE,

                          CONSTRAINT fk_teacher_user
                              FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE student_guardians (
                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                   student_id BIGINT NOT NULL,
                                   guardian_id BIGINT NOT NULL,
                                   relationship VARCHAR(30) NOT NULL,

                                   CONSTRAINT fk_student_guardian_student
                                       FOREIGN KEY (student_id) REFERENCES students(id),

                                   CONSTRAINT fk_student_guardian_guardian
                                       FOREIGN KEY (guardian_id) REFERENCES guardians(id),

                                   CONSTRAINT uq_student_guardian
                                       UNIQUE (student_id, guardian_id)
);
