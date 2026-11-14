
package com.sms.backend.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;

@RestController
@RequestMapping("/api/admin/fees")
@PreAuthorize("hasRole('ADMIN')")
public class FeeController {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("99999999.99");

    private static final Set<String> PAYMENT_METHODS =
            Set.of(
                    "CASH",
                    "UPI",
                    "BANK_TRANSFER",
                    "CARD",
                    "CHEQUE"
            );

    private static final String INVOICE_QUERY = """
        SELECT
            fi.id,
            fi.student_id AS studentId,
            st.full_name AS studentName,
            st.admission_number AS admissionNumber,
            fi.invoice_number AS invoiceNumber,
            fi.total_amount AS totalAmount,
            fi.due_date AS dueDate,
            fi.status,
            fi.created_at AS createdAt,
            COALESCE((
                SELECT SUM(fp.amount)
                FROM fee_payments fp
                WHERE fp.invoice_id = fi.id
            ), 0) AS paidAmount,
            (
                fi.total_amount - COALESCE((
                    SELECT SUM(fp.amount)
                    FROM fee_payments fp
                    WHERE fp.invoice_id = fi.id
                ), 0)
            ) AS balanceAmount
        FROM fee_invoices fi
        JOIN students st ON st.id = fi.student_id
        """;

    public FeeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    // ========== REQUEST MODELS ==========

    public record InvoiceRequest(
            Long studentId,
            String invoiceNumber,
            BigDecimal totalAmount,
            LocalDate dueDate
    ) {}

    public record InvoiceUpdateRequest(
            BigDecimal totalAmount,
            LocalDate dueDate
    ) {}

    public record PaymentRequest(
            BigDecimal amount,
            String paymentMethod,
            String referenceNumber
    ) {}

    // ========== INVOICE READ ==========

    @GetMapping("/invoices")
    public List<Map<String, Object>> listInvoices(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String status
    ) {

        StringBuilder sql = new StringBuilder(
                INVOICE_QUERY + " WHERE 1 = 1 "
        );

        List<Object> parameters = new ArrayList<>();

        if (studentId != null) {
            sql.append(" AND fi.student_id = ? ");
            parameters.add(studentId);
        }

        if (status != null && !status.isBlank()) {

            String normalized =
                    status.trim().toUpperCase(Locale.ROOT);

            if (!Set.of("UNPAID", "PARTIAL", "PAID")
                    .contains(normalized)) {
                throw badRequest("Invalid invoice status.");
            }

            sql.append(" AND fi.status = ? ");
            parameters.add(normalized);
        }

        sql.append(" ORDER BY fi.id DESC ");

        return jdbc.queryForList(
                sql.toString(),
                parameters.toArray()
        );
    }

    @GetMapping("/invoices/{invoiceId}")
    public Map<String, Object> getInvoice(
            @PathVariable Long invoiceId
    ) {

        Map<String, Object> invoice =
                new LinkedHashMap<>(findInvoice(invoiceId));

        invoice.put(
                "payments",
                listPaymentsForInvoice(invoiceId)
        );

        return invoice;
    }

    // ========== INVOICE CREATE ==========

    @PostMapping("/invoices")
    @Transactional
    public ResponseEntity<Map<String, Object>> createInvoice(
            @RequestBody InvoiceRequest request
    ) {

        if (request == null ||
                request.studentId() == null ||
                request.totalAmount() == null ||
                request.dueDate() == null) {
            throw badRequest(
                    "Student, total amount and due date are required."
            );
        }

        validateAmount(request.totalAmount());

        if (!exists("""
                SELECT COUNT(*)
                FROM students
                WHERE id = ?
                  AND active = TRUE
                """, request.studentId())) {
            throw badRequest(
                    "Active student not found."
            );
        }

        String invoiceNumber =
                request.invoiceNumber();

        if (invoiceNumber == null ||
                invoiceNumber.isBlank()) {

            invoiceNumber =
                    "INV-" +
                            Year.now().getValue() +
                            "-" +
                            UUID.randomUUID()
                                    .toString()
                                    .substring(0, 12)
                                    .toUpperCase(Locale.ROOT);
        } else {
            invoiceNumber = invoiceNumber.trim();
        }

        if (invoiceNumber.length() > 50) {
            throw badRequest(
                    "Invoice number cannot exceed 50 characters."
            );
        }

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue(
                                "studentId",
                                request.studentId()
                        )
                        .addValue(
                                "invoiceNumber",
                                invoiceNumber
                        )
                        .addValue(
                                "totalAmount",
                                request.totalAmount()
                        )
                        .addValue(
                                "dueDate",
                                Date.valueOf(request.dueDate())
                        );

        KeyHolder holder = new GeneratedKeyHolder();

        try {
            namedJdbc.update("""
                INSERT INTO fee_invoices (
                    student_id,
                    invoice_number,
                    total_amount,
                    due_date,
                    status
                )
                VALUES (
                    :studentId,
                    :invoiceNumber,
                    :totalAmount,
                    :dueDate,
                    'UNPAID'
                )
                """,
                    params,
                    holder,
                    new String[]{"id"});

        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "Invoice number already exists or invoice data is invalid."
            );
        }

        Long invoiceId = generatedId(holder);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(getInvoice(invoiceId));
    }

    // ========== INVOICE UPDATE ==========

    @PutMapping("/invoices/{invoiceId}")
    @Transactional
    public Map<String, Object> updateInvoice(
            @PathVariable Long invoiceId,
            @RequestBody InvoiceUpdateRequest request
    ) {

        lockInvoice(invoiceId);

        if (request == null ||
                request.totalAmount() == null ||
                request.dueDate() == null) {
            throw badRequest(
                    "Total amount and due date are required."
            );
        }

        validateAmount(request.totalAmount());

        BigDecimal paid = paidAmount(invoiceId);

        if (request.totalAmount().compareTo(paid) < 0) {
            throw conflict(
                    "Invoice total cannot be less than payments already recorded."
            );
        }

        jdbc.update("""
            UPDATE fee_invoices
            SET total_amount = ?,
                due_date = ?
            WHERE id = ?
            """,
                request.totalAmount(),
                Date.valueOf(request.dueDate()),
                invoiceId
        );

        refreshInvoiceStatus(invoiceId);

        return getInvoice(invoiceId);
    }

    // ========== INVOICE DELETE ==========

    @DeleteMapping("/invoices/{invoiceId}")
    @Transactional
    public Map<String, String> deleteInvoice(
            @PathVariable Long invoiceId
    ) {

        lockInvoice(invoiceId);

        if (exists("""
                SELECT COUNT(*)
                FROM fee_payments
                WHERE invoice_id = ?
                """, invoiceId)) {

            throw conflict(
                    "Cannot delete an invoice with recorded payments."
            );
        }

        jdbc.update(
                "DELETE FROM fee_invoices WHERE id = ?",
                invoiceId
        );

        return Map.of(
                "message",
                "Invoice deleted successfully"
        );
    }

    // ========== PAYMENT READ ==========

    @GetMapping("/payments")
    public List<Map<String, Object>> listAllPayments() {

        return jdbc.queryForList("""
            SELECT
                fp.id,
                fp.invoice_id AS invoiceId,
                fi.invoice_number AS invoiceNumber,
                fi.student_id AS studentId,
                st.full_name AS studentName,
                fp.amount,
                fp.payment_date AS paymentDate,
                fp.payment_method AS paymentMethod,
                fp.recorded_by AS recordedBy,
                fp.reference_number AS referenceNumber
            FROM fee_payments fp
            JOIN fee_invoices fi
                ON fi.id = fp.invoice_id
            JOIN students st
                ON st.id = fi.student_id
            ORDER BY fp.payment_date DESC, fp.id DESC
            """);
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    public List<Map<String, Object>> getInvoicePayments(
            @PathVariable Long invoiceId
    ) {

        findInvoice(invoiceId);

        return listPaymentsForInvoice(invoiceId);
    }

    // ========== PAYMENT CREATE ==========

    @PostMapping("/invoices/{invoiceId}/payments")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordPayment(
            @PathVariable Long invoiceId,
            @RequestBody PaymentRequest request,
            Authentication authentication
    ) {

        Map<String, Object> invoice =
                lockInvoice(invoiceId);

        if (request == null ||
                request.amount() == null ||
                request.paymentMethod() == null ||
                request.paymentMethod().isBlank()) {

            throw badRequest(
                    "Payment amount and method are required."
            );
        }

        validateAmount(request.amount());

        String method =
                request.paymentMethod()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        if (!PAYMENT_METHODS.contains(method)) {
            throw badRequest(
                    "Supported payment methods: CASH, UPI, BANK_TRANSFER, CARD, CHEQUE."
            );
        }

        String reference =
                request.referenceNumber() == null
                        ? null
                        : request.referenceNumber().trim();

        if (reference != null &&
                reference.length() > 100) {
            throw badRequest(
                    "Reference number cannot exceed 100 characters."
            );
        }

        BigDecimal paid = paidAmount(invoiceId);

        BigDecimal total =
                decimal(invoice.get("totalAmount"));

        BigDecimal remaining = total.subtract(paid);

        if (request.amount().compareTo(remaining) > 0) {
            throw conflict(
                    "Payment exceeds the outstanding balance of " +
                            remaining
            );
        }

        if (remaining.signum() <= 0) {
            throw conflict(
                    "This invoice has already been fully paid."
            );
        }

        Long adminId = authenticatedUserId(authentication);

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("invoiceId", invoiceId)
                        .addValue("amount", request.amount())
                        .addValue("method", method)
                        .addValue("recordedBy", adminId)
                        .addValue("referenceNumber", reference);

        KeyHolder holder = new GeneratedKeyHolder();

        namedJdbc.update("""
            INSERT INTO fee_payments (
                invoice_id,
                amount,
                payment_method,
                recorded_by,
                reference_number
            )
            VALUES (
                :invoiceId,
                :amount,
                :method,
                :recordedBy,
                :referenceNumber
            )
            """,
                params,
                holder,
                new String[]{"id"});

        Long paymentId = generatedId(holder);

        refreshInvoiceStatus(invoiceId);

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "message",
                "Payment recorded successfully"
        );

        result.put(
                "payment",
                findPayment(paymentId)
        );

        result.put(
                "invoice",
                getInvoice(invoiceId)
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result);
    }

    // ========== PAYMENT RECEIPT ==========

    @GetMapping("/payments/{paymentId}/receipt")
    public Map<String, Object> getReceipt(
            @PathVariable Long paymentId
    ) {

        Map<String, Object> payment =
                findPayment(paymentId);

        Long invoiceId =
                number(payment.get("invoiceId"));

        Map<String, Object> invoice =
                findInvoice(invoiceId);

        Map<String, Object> receipt =
                new LinkedHashMap<>();

        receipt.put(
                "receiptNumber",
                "REC-" + paymentId
        );

        receipt.put(
                "payment",
                payment
        );

        receipt.put(
                "invoice",
                invoice
        );

        receipt.put(
                "school",
                "EduSphere"
        );

        return receipt;
    }

    // ========== DASHBOARD SUMMARY ==========

    @GetMapping("/summary")
    public Map<String, Object> getFeeSummary() {

        Map<String, Object> totals =
                jdbc.queryForMap("""
                    SELECT
                        COUNT(*) AS totalInvoices,
                        COALESCE(SUM(total_amount), 0)
                            AS totalBilled,
                        COALESCE(SUM(
                            CASE
                                WHEN status = 'PAID'
                                THEN 1 ELSE 0
                            END
                        ), 0) AS paidInvoices,
                        COALESCE(SUM(
                            CASE
                                WHEN status = 'PARTIAL'
                                THEN 1 ELSE 0
                            END
                        ), 0) AS partialInvoices,
                        COALESCE(SUM(
                            CASE
                                WHEN status = 'UNPAID'
                                THEN 1 ELSE 0
                            END
                        ), 0) AS unpaidInvoices,
                        COALESCE(SUM(
                            CASE
                                WHEN due_date < CURRENT_DATE
                                 AND status <> 'PAID'
                                THEN 1 ELSE 0
                            END
                        ), 0) AS overdueInvoices
                    FROM fee_invoices
                    """);

        BigDecimal collected = jdbc.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM fee_payments
            """, BigDecimal.class);

        if (collected == null) {
            collected = BigDecimal.ZERO;
        }

        BigDecimal billed =
                decimal(totals.get("totalBilled"));

        Map<String, Object> summary =
                new LinkedHashMap<>(totals);

        summary.put(
                "totalCollected",
                collected
        );

        summary.put(
                "totalOutstanding",
                billed.subtract(collected)
        );

        return summary;
    }

    // ========== HELPERS ==========

    private Map<String, Object> findInvoice(Long invoiceId) {

        List<Map<String, Object>> invoices =
                jdbc.queryForList(
                        INVOICE_QUERY + " WHERE fi.id = ?",
                        invoiceId
                );

        if (invoices.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Invoice not found"
            );
        }

        return invoices.get(0);
    }

    private Map<String, Object> lockInvoice(Long invoiceId) {

        List<Map<String, Object>> lockedRows =
                jdbc.queryForList("""
                    SELECT id
                    FROM fee_invoices
                    WHERE id = ?
                    FOR UPDATE
                    """, invoiceId);

        if (lockedRows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Invoice not found"
            );
        }

        return findInvoice(invoiceId);
    }

    private List<Map<String, Object>> listPaymentsForInvoice(
            Long invoiceId
    ) {

        return jdbc.queryForList("""
            SELECT
                id,
                invoice_id AS invoiceId,
                amount,
                payment_date AS paymentDate,
                payment_method AS paymentMethod,
                recorded_by AS recordedBy,
                reference_number AS referenceNumber
            FROM fee_payments
            WHERE invoice_id = ?
            ORDER BY id DESC
            """, invoiceId);
    }

    private Map<String, Object> findPayment(Long paymentId) {

        List<Map<String, Object>> payments =
                jdbc.queryForList("""
                    SELECT
                        fp.id,
                        fp.invoice_id AS invoiceId,
                        fp.amount,
                        fp.payment_date AS paymentDate,
                        fp.payment_method AS paymentMethod,
                        fp.recorded_by AS recordedBy,
                        fp.reference_number AS referenceNumber,
                        fi.invoice_number AS invoiceNumber,
                        st.full_name AS studentName
                    FROM fee_payments fp
                    JOIN fee_invoices fi
                        ON fi.id = fp.invoice_id
                    JOIN students st
                        ON st.id = fi.student_id
                    WHERE fp.id = ?
                    """, paymentId);

        if (payments.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payment not found"
            );
        }

        return payments.get(0);
    }

    private BigDecimal paidAmount(Long invoiceId) {

        BigDecimal total = jdbc.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM fee_payments
            WHERE invoice_id = ?
            """,
                BigDecimal.class,
                invoiceId);

        return total == null
                ? BigDecimal.ZERO
                : total;
    }

    private void refreshInvoiceStatus(Long invoiceId) {

        BigDecimal paid = paidAmount(invoiceId);

        BigDecimal total =
                decimal(findInvoice(invoiceId).get("totalAmount"));

        String status;

        if (paid.compareTo(total) >= 0) {
            status = "PAID";
        } else if (paid.signum() > 0) {
            status = "PARTIAL";
        } else {
            status = "UNPAID";
        }

        jdbc.update("""
            UPDATE fee_invoices
            SET status = ?
            WHERE id = ?
            """,
                status,
                invoiceId
        );
    }

    private Long authenticatedUserId(
            Authentication authentication
    ) {

        if (authentication == null ||
                authentication.getName() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        List<Long> ids = jdbc.query("""
            SELECT id
            FROM users
            WHERE LOWER(email) = LOWER(?)
            """,
                (rs, rowNumber) -> rs.getLong("id"),
                authentication.getName());

        if (ids.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Authenticated user not found"
            );
        }

        return ids.get(0);
    }

    private void validateAmount(BigDecimal amount) {

        if (amount == null ||
                amount.compareTo(BigDecimal.ZERO) <= 0 ||
                amount.compareTo(MAX_AMOUNT) > 0 ||
                amount.scale() > 2) {

            throw badRequest(
                    "Amount must be greater than zero, with at most 2 decimal places."
            );
        }
    }

    private boolean exists(String sql, Object... args) {

        Integer count =
                jdbc.queryForObject(
                        sql,
                        Integer.class,
                        args
                );

        return count != null && count > 0;
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
    }

    private Long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long generatedId(KeyHolder holder) {

        Number key = holder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "Generated database ID not returned"
            );
        }

        return key.longValue();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
