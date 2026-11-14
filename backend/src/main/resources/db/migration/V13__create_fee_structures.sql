
-- V13: Fee structures and automatic invoice mapping.
-- V12 was previously applied as an empty migration.

CREATE TABLE fee_structures (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                class_id BIGINT NOT NULL,
                                academic_year VARCHAR(20) NOT NULL,
                                name VARCHAR(100) NOT NULL,
                                amount DECIMAL(10,2) NOT NULL,
                                due_date DATE NOT NULL,
                                active BOOLEAN NOT NULL DEFAULT TRUE,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_fee_structure_class
                                    FOREIGN KEY (class_id) REFERENCES classes(id),

                                CONSTRAINT chk_fee_structure_amount
                                    CHECK (amount > 0)
);

CREATE TABLE fee_structure_invoices (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        fee_structure_id BIGINT NOT NULL,
                                        student_id BIGINT NOT NULL,
                                        invoice_id BIGINT NOT NULL,

                                        CONSTRAINT fk_fsi_structure
                                            FOREIGN KEY (fee_structure_id)
                                                REFERENCES fee_structures(id),

                                        CONSTRAINT fk_fsi_student
                                            FOREIGN KEY (student_id)
                                                REFERENCES students(id),

                                        CONSTRAINT fk_fsi_invoice
                                            FOREIGN KEY (invoice_id)
                                                REFERENCES fee_invoices(id),

                                        CONSTRAINT uq_fee_structure_student
                                            UNIQUE (fee_structure_id, student_id),

                                        CONSTRAINT uq_fee_structure_invoice
                                            UNIQUE (invoice_id)
);

CREATE INDEX idx_fee_structures_class_year
    ON fee_structures (class_id, academic_year);
