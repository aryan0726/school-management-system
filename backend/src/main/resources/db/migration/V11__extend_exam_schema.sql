
-- V11: Extend examination schema for frontend requirements.
-- V10 is an already-applied empty migration.

ALTER TABLE exams
    ADD COLUMN class_id BIGINT NULL,
    ADD COLUMN exam_type VARCHAR(30) NOT NULL DEFAULT 'Mid-term',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'Draft',
    ADD COLUMN description VARCHAR(500) NULL,
    ADD COLUMN published_at DATETIME NULL;

-- Preserve existing examinations by deriving class from section.
UPDATE exams e
    JOIN sections s ON s.id = e.section_id
    SET e.class_id = s.class_id
WHERE e.class_id IS NULL;

-- Allow class-level and section-level examinations.
ALTER TABLE exams
    MODIFY COLUMN section_id BIGINT NULL,
    MODIFY COLUMN class_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_exam_class
    FOREIGN KEY (class_id) REFERENCES classes(id);

-- Store paper dates and assigned teachers.
ALTER TABLE exam_subjects
    ADD COLUMN exam_date DATE NULL,
    ADD COLUMN teacher_id BIGINT NULL,
    ADD CONSTRAINT fk_exam_paper_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers(id);

-- Extend the grading system.
ALTER TABLE grading_rules
    ADD COLUMN grade_point DECIMAL(4,1) NOT NULL DEFAULT 0.0,
    ADD COLUMN description VARCHAR(255) NULL,
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_exams_class_status
    ON exams (class_id, status);

CREATE INDEX idx_exam_subjects_teacher
    ON exam_subjects (teacher_id);
