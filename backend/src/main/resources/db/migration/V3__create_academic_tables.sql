
CREATE TABLE classes (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         name VARCHAR(50) NOT NULL UNIQUE,
                         active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE sections (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          class_id BIGINT NOT NULL,
                          name VARCHAR(20) NOT NULL,

                          CONSTRAINT fk_section_class
                              FOREIGN KEY (class_id) REFERENCES classes(id),

                          CONSTRAINT uq_class_section
                              UNIQUE (class_id, name)
);

CREATE TABLE subjects (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          name VARCHAR(100) NOT NULL,
                          code VARCHAR(20) NOT NULL UNIQUE,
                          active BOOLEAN NOT NULL DEFAULT TRUE
);
