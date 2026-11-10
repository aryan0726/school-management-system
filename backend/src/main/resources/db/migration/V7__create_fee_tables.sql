
CREATE TABLE fee_invoices (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              student_id BIGINT NOT NULL,
                              invoice_number VARCHAR(50) NOT NULL UNIQUE,
                              total_amount DECIMAL(10,2) NOT NULL,
                              due_date DATE NOT NULL,
                              status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT fk_invoice_student
                                  FOREIGN KEY (student_id) REFERENCES students(id),

                              CONSTRAINT chk_invoice_amount
                                  CHECK (total_amount >= 0)
);

CREATE TABLE fee_payments (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              invoice_id BIGINT NOT NULL,
                              amount DECIMAL(10,2) NOT NULL,
                              payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              payment_method VARCHAR(30) NOT NULL,
                              recorded_by BIGINT NOT NULL,
                              reference_number VARCHAR(100),

                              CONSTRAINT fk_payment_invoice
                                  FOREIGN KEY (invoice_id) REFERENCES fee_invoices(id),

                              CONSTRAINT fk_payment_user
                                  FOREIGN KEY (recorded_by) REFERENCES users(id),

                              CONSTRAINT chk_payment_amount
                                  CHECK (amount > 0)
);
