package com.schoolmanager.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Tenant tenant;

    @Column(nullable = false, length = 50)
    private String code; // SUPER_ADMIN, SCHOOL_ADMIN, TEACHER, PARENT, STUDENT, etc.

    @Column(name = "name_fr", nullable = false, length = 100)
    private String nameFr;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Builder.Default
    @Column(name = "is_system_role", nullable = false)
    private Boolean isSystemRole = false;

    @Column(name = "description", length = 255)
    private String description;

    @org.hibernate.annotations.Formula("(SELECT COUNT(*) FROM user_roles ur WHERE ur.role_id = id)")
    private Integer userCount;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();
}
