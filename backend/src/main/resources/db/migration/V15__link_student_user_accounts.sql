
-- Link students to their login accounts.
-- Existing students remain valid without login accounts.

ALTER TABLE students
    ADD COLUMN user_id BIGINT NULL UNIQUE,
    ADD CONSTRAINT fk_student_user
        FOREIGN KEY (user_id) REFERENCES users(id);

-- Ensure STUDENT role exists.
INSERT INTO roles (name)
SELECT 'STUDENT'
    WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'STUDENT'
);
