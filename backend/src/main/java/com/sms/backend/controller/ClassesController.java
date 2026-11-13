
package com.sms.backend.controller;

import com.sms.backend.entity.SchoolClass;
import com.sms.backend.entity.SchoolSection;
import com.sms.backend.repository.SchoolClassRepository;
import com.sms.backend.repository.SchoolSectionRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/classes")
@PreAuthorize("hasRole('ADMIN')")
public class ClassesController {

    private final SchoolClassRepository classRepository;
    private final SchoolSectionRepository sectionRepository;

    public ClassesController(
            SchoolClassRepository classRepository,
            SchoolSectionRepository sectionRepository) {

        this.classRepository = classRepository;
        this.sectionRepository = sectionRepository;
    }

    // 1. GET ALL CLASSES
    @GetMapping
    @Transactional(readOnly = true)
    public List<ClassResponse> getAllClasses() {
        return classRepository.findAll()
                .stream()
                .map(this::toClassResponse)
                .toList();
    }

    // 2. GET CLASS BY ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getClassById(@PathVariable Long id) {

        SchoolClass schoolClass =
                classRepository.findById(id).orElse(null);

        if (schoolClass == null) {
            return notFound("Class not found");
        }

        return ResponseEntity.ok(toClassResponse(schoolClass));
    }

    // 3. CREATE CLASS
    @PostMapping
    @Transactional
    public ResponseEntity<?> createClass(
            @Valid @RequestBody ClassRequest request) {

        String name = request.name().trim();

        if (classRepository.existsByNameIgnoreCase(name)) {
            return conflict("Class name already exists");
        }

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setName(name);
        schoolClass.setActive(true);

        SchoolClass saved = classRepository.save(schoolClass);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toClassResponse(saved));
    }

    // 4. UPDATE CLASS
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateClass(
            @PathVariable Long id,
            @Valid @RequestBody ClassRequest request) {

        SchoolClass schoolClass =
                classRepository.findById(id).orElse(null);

        if (schoolClass == null) {
            return notFound("Class not found");
        }

        String name = request.name().trim();

        if (!schoolClass.getName().equalsIgnoreCase(name)
                && classRepository.existsByNameIgnoreCase(name)) {

            return conflict("Class name already exists");
        }

        schoolClass.setName(name);

        return ResponseEntity.ok(
                toClassResponse(classRepository.save(schoolClass))
        );
    }

    // 5. DEACTIVATE CLASS
    @PatchMapping("/{id}/deactivate")
    @Transactional
    public ResponseEntity<?> deactivateClass(
            @PathVariable Long id) {

        SchoolClass schoolClass =
                classRepository.findById(id).orElse(null);

        if (schoolClass == null) {
            return notFound("Class not found");
        }

        schoolClass.setActive(false);

        return ResponseEntity.ok(
                toClassResponse(classRepository.save(schoolClass))
        );
    }

    // 6. GET SECTIONS OF A CLASS
    @GetMapping("/{classId}/sections")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getSections(
            @PathVariable Long classId) {

        if (!classRepository.existsById(classId)) {
            return notFound("Class not found");
        }

        List<SectionResponse> sections =
                sectionRepository
                        .findBySchoolClass_IdOrderByNameAsc(classId)
                        .stream()
                        .map(this::toSectionResponse)
                        .toList();

        return ResponseEntity.ok(sections);
    }

    // 7. ADD SECTION TO CLASS
    @PostMapping("/{classId}/sections")
    @Transactional
    public ResponseEntity<?> createSection(
            @PathVariable Long classId,
            @Valid @RequestBody SectionRequest request) {

        SchoolClass schoolClass =
                classRepository.findById(classId).orElse(null);

        if (schoolClass == null) {
            return notFound("Class not found");
        }

        if (!schoolClass.getActive()) {
            return conflict("Cannot add section to inactive class");
        }

        String name = request.name().trim();

        if (sectionRepository
                .existsBySchoolClass_IdAndNameIgnoreCase(
                        classId, name)) {

            return conflict("Section already exists in this class");
        }

        SchoolSection section = new SchoolSection();
        section.setSchoolClass(schoolClass);
        section.setName(name);

        SchoolSection saved = sectionRepository.save(section);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toSectionResponse(saved));
    }

    // 8. RENAME SECTION
    @PutMapping("/{classId}/sections/{sectionId}")
    @Transactional
    public ResponseEntity<?> updateSection(
            @PathVariable Long classId,
            @PathVariable Long sectionId,
            @Valid @RequestBody SectionRequest request) {

        SchoolSection section =
                sectionRepository.findById(sectionId).orElse(null);

        if (section == null ||
                !section.getSchoolClass().getId().equals(classId)) {

            return notFound("Section not found in this class");
        }

        String name = request.name().trim();

        if (!section.getName().equalsIgnoreCase(name)
                && sectionRepository
                .existsBySchoolClass_IdAndNameIgnoreCase(
                        classId, name)) {

            return conflict("Section already exists in this class");
        }

        section.setName(name);

        return ResponseEntity.ok(
                toSectionResponse(sectionRepository.save(section))
        );
    }

    private ClassResponse toClassResponse(SchoolClass schoolClass) {
        return new ClassResponse(
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.getActive()
        );
    }

    private SectionResponse toSectionResponse(SchoolSection section) {
        return new SectionResponse(
                section.getId(),
                section.getSchoolClass().getId(),
                section.getName()
        );
    }

    private ResponseEntity<?> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", message));
    }

    private ResponseEntity<?> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", message));
    }

    public record ClassRequest(
            @NotBlank @Size(max = 50) String name
    ) {}

    public record SectionRequest(
            @NotBlank @Size(max = 20) String name
    ) {}

    public record ClassResponse(
            Long id,
            String name,
            Boolean active
    ) {}

    public record SectionResponse(
            Long id,
            Long classId,
            String name
    ) {}
}
