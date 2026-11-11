
package com.sms.backend.controller;

import com.sms.backend.entity.Student;
import com.sms.backend.repository.StudentRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/students")
@PreAuthorize("hasRole('ADMIN')")
public class StudentController {

    private final StudentRepository studentRepository;

    public StudentController(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    // 1. GET ALL STUDENTS
    @GetMapping
    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    // 2. GET STUDENT BY ID
    @GetMapping("/{id}")
    public ResponseEntity<Student> getStudent(@PathVariable Long id) {
        return studentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 3. CREATE STUDENT
    @PostMapping
    public ResponseEntity<?> createStudent(
            @Valid @RequestBody StudentRequest request) {

        if (studentRepository.existsByAdmissionNumber(
                request.admissionNumber())) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message", "Admission number already exists"
                    ));
        }

        Student student = new Student();

        student.setAdmissionNumber(request.admissionNumber());
        student.setFullName(request.fullName());
        student.setDateOfBirth(request.dateOfBirth());
        student.setGender(request.gender());
        student.setActive(true);

        Student saved = studentRepository.save(student);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // 4. UPDATE STUDENT
    @PutMapping("/{id}")
    public ResponseEntity<?> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody StudentRequest request) {

        Student student = studentRepository.findById(id).orElse(null);

        if (student == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Student not found"));
        }

        if (!student.getAdmissionNumber()
                .equals(request.admissionNumber())
                && studentRepository.existsByAdmissionNumber(
                request.admissionNumber())) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message", "Admission number already exists"
                    ));
        }

        student.setAdmissionNumber(request.admissionNumber());
        student.setFullName(request.fullName());
        student.setDateOfBirth(request.dateOfBirth());
        student.setGender(request.gender());

        Student updated = studentRepository.save(student);

        return ResponseEntity.ok(updated);
    }


    // 5. DEACTIVATE STUDENT
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateStudent(@PathVariable Long id) {

        Student student = studentRepository.findById(id).orElse(null);

        if (student == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Student not found"));
        }

        student.setActive(false);

        Student updated = studentRepository.save(student);

        return ResponseEntity.ok(updated);
    }


    // REQUEST DTO
    public record StudentRequest(
            @NotBlank String admissionNumber,
            @NotBlank @Size(max = 100) String fullName,
            LocalDate dateOfBirth,
            String gender
    ) {}
}
