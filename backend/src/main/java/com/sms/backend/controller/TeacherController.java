
package com.sms.backend.controller;

import com.sms.backend.entity.Role;
import com.sms.backend.entity.Teacher;
import com.sms.backend.entity.User;
import com.sms.backend.repository.RoleRepository;
import com.sms.backend.repository.TeacherRepository;
import com.sms.backend.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/teachers")
@PreAuthorize("hasRole('ADMIN')")
public class TeacherController {

    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public TeacherController(
            TeacherRepository teacherRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {

        this.teacherRepository = teacherRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 1. GET ALL TEACHERS
    @GetMapping
    @Transactional(readOnly = true)
    public List<TeacherResponse> getAllTeachers() {

        return teacherRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 2. GET TEACHER BY ID
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getTeacher(@PathVariable Long id) {

        return teacherRepository.findById(id)
                .<ResponseEntity<?>>map(
                        teacher -> ResponseEntity.ok(toResponse(teacher))
                )
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Teacher not found")));
    }

    // 3. CREATE TEACHER AND LOGIN ACCOUNT
    @PostMapping
    @Transactional
    public ResponseEntity<?> createTeacher(
            @Valid @RequestBody CreateTeacherRequest request) {

        String email = request.email().trim().toLowerCase();
        String employeeNumber = request.employeeNumber().trim();

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Email already exists"));
        }

        if (teacherRepository.existsByEmployeeNumber(employeeNumber)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message", "Employee number already exists"
                    ));
        }

        Role teacherRole = roleRepository.findByName("TEACHER")
                .orElse(null);

        if (teacherRole == null) {
            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of("message", "TEACHER role not found"));
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPassword(
                passwordEncoder.encode(request.password())
        );
        user.setRole(teacherRole);
        user.setActive(true);

        User savedUser = userRepository.save(user);

        Teacher teacher = new Teacher();
        teacher.setUser(savedUser);
        teacher.setEmployeeNumber(employeeNumber);
        teacher.setQualification(request.qualification());
        teacher.setJoiningDate(request.joiningDate());
        teacher.setActive(true);

        Teacher savedTeacher = teacherRepository.save(teacher);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(savedTeacher));
    }

    // 4. UPDATE TEACHER
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateTeacher(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTeacherRequest request) {

        Teacher teacher = teacherRepository.findById(id)
                .orElse(null);

        if (teacher == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Teacher not found"));
        }

        String email = request.email().trim().toLowerCase();
        String employeeNumber = request.employeeNumber().trim();

        User user = teacher.getUser();

        if (!user.getEmail().equalsIgnoreCase(email)
                && userRepository.existsByEmail(email)) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Email already exists"));
        }

        if (!teacher.getEmployeeNumber().equals(employeeNumber)
                && teacherRepository.existsByEmployeeNumber(employeeNumber)) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message", "Employee number already exists"
                    ));
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(email);

        teacher.setEmployeeNumber(employeeNumber);
        teacher.setQualification(request.qualification());
        teacher.setJoiningDate(request.joiningDate());

        userRepository.save(user);
        Teacher saved = teacherRepository.save(teacher);

        return ResponseEntity.ok(toResponse(saved));
    }

    // 5. DEACTIVATE TEACHER
    @PatchMapping("/{id}/deactivate")
    @Transactional
    public ResponseEntity<?> deactivateTeacher(@PathVariable Long id) {

        Teacher teacher = teacherRepository.findById(id)
                .orElse(null);

        if (teacher == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Teacher not found"));
        }

        teacher.setActive(false);

        User user = teacher.getUser();
        user.setActive(false);

        userRepository.save(user);
        Teacher saved = teacherRepository.save(teacher);

        return ResponseEntity.ok(toResponse(saved));
    }

    // SAFE API RESPONSE (NO PASSWORD)
    private TeacherResponse toResponse(Teacher teacher) {

        User user = teacher.getUser();

        return new TeacherResponse(
                teacher.getId(),
                teacher.getEmployeeNumber(),
                user.getFullName(),
                user.getEmail(),
                teacher.getQualification(),
                teacher.getJoiningDate(),
                teacher.getActive() && user.getActive()
        );
    }

    public record CreateTeacherRequest(
            @NotBlank @Size(max = 50) String employeeNumber,
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8) String password,
            @Size(max = 150) String qualification,
            LocalDate joiningDate
    ) {}

    public record UpdateTeacherRequest(
            @NotBlank @Size(max = 50) String employeeNumber,
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @Size(max = 150) String qualification,
            LocalDate joiningDate
    ) {}

    public record TeacherResponse(
            Long id,
            String employeeNumber,
            String fullName,
            String email,
            String qualification,
            LocalDate joiningDate,
            Boolean active
    ) {}
}
