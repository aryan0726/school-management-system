
# School Management System
## Database Design Documentation

### 1. Overview

This document describes the proposed database structure and relationships for the School Management System.

**Database:** MySQL 8.4  
**Backend:** Java Spring Boot  
**ORM:** Spring Data JPA / Hibernate  
**Migration Tool:** Flyway  
**Database Environment:** Docker

---

### 2. Database Tables

The database is divided into six functional groups.

| Module | Tables |
|--------|--------|
| Authentication | `users`, `roles` |
| School Administration | `students`, `guardians`, `teachers`, `classes`, `sections`, `subjects`, `enrollments`, `teacher_assignments`, `student_guardians` |
| Attendance | `attendance_sessions`, `attendance_records` |
| Examinations | `exams`, `exam_subjects`, `marks`, `grading_rules` |
| Fee Management | `fee_invoices`, `fee_payments` |
| Audit & Logging | `audit_logs` |

**Total Proposed Tables:** 19

---

### 3. Important Database Relationships

#### 3.1 User and Role
- Each user has one assigned role.
- A role can be assigned to multiple users.
- Roles include Admin, Teacher, and Guardian.

#### 3.2 Student and Guardian
- One guardian can be linked to multiple students.
- One student can have multiple guardians.
- The `student_guardians` table manages these relationships.

#### 3.3 Teacher and Academic Assignments
- One teacher can teach multiple classes and subjects.
- The `teacher_assignments` table manages teacher assignments.

#### 3.4 Student Enrollment
- Students are enrolled in classes and sections.
- The `enrollments` table manages student enrollment information.

---

### 4. Database Design Principles

The database will follow these principles:

1. **Primary Keys:** Uniquely identify each record.
2. **Foreign Keys:** Maintain relationships between tables.
3. **Unique Constraints:** Prevent duplicate records where necessary.
4. **Normalization:** Reduce unnecessary data duplication.
5. **Data Integrity:** Maintain accurate and consistent records.
6. **Security:** Store passwords securely using hashing.
7. **Migrations:** Manage database schema changes using Flyway.

---

### 5. Implementation Status

- [x] Identify database modules
- [x] List proposed database tables
- [x] Define initial academic relationships
- [ ] Define attendance relationships
- [ ] Define examination relationships
- [ ] Define fee management relationships
- [ ] Create complete ER diagram
- [ ] Implement Flyway migrations
- [ ] Verify database tables in MySQL

---

### 6. Notes

This document represents the initial database design.

The table structures, relationships, constraints, and indexes will be finalized before implementing the database migrations.



#### 3.5 Attendance Management
- One class/section can have multiple attendance sessions.
- Each attendance session belongs to one class/section.
- Each attendance session contains attendance records for students.
- A student can have multiple attendance records across different sessions.
- One student can have only one attendance record per session.

#### 3.6 Examination Management
- One exam can include multiple subjects.
- The `exam_subjects` table connects exams with subjects.
- Each student can receive marks for multiple exam subjects.
- The `marks` table stores student marks.
- The `grading_rules` table defines how grades are calculated.

#### 3.7 Fee Management
- One student can have multiple fee invoices.
- One fee invoice can have multiple payments.
- The `fee_payments` table stores simulated payment transactions.
- Outstanding balance = Invoice Amount - Total Successful Payments.

#### 3.8 Audit Logging
- One user can perform multiple actions.
- The `audit_logs` table records important actions performed by users.
