
package com.sms.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "sections",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_class_section",
                columnNames = {"class_id", "name"}
        )
)
public class SchoolSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @Column(nullable = false, length = 20)
    private String name;

    public SchoolSection() {}

    public Long getId() {
        return id;
    }

    public SchoolClass getSchoolClass() {
        return schoolClass;
    }

    public void setSchoolClass(SchoolClass schoolClass) {
        this.schoolClass = schoolClass;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
