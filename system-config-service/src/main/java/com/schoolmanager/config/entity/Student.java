package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Représente le profil individuel d'un élève inscrit au sein d'un établissement (SaaS).
 * Contient des contraintes de validation de données (JSR-380).
 */
@Entity
@Table(name = "students", indexes = {
    @Index(name = "idx_students_tenant", columnList = "tenant_id"),
    @Index(name = "idx_students_parent_phone", columnList = "tenant_id, parent_phone")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank(message = "Le matricule est obligatoire")
    @Size(max = 50, message = "Le matricule ne doit pas dépasser 50 caractères")
    @Column(name = "registration_number", nullable = false, unique = true, length = 50)
    private String registrationNumber; // Matricule unique de l'élève

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @NotNull(message = "La date de naissance est obligatoire")
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @NotBlank(message = "Le genre est obligatoire")
    @Size(max = 10, message = "Le genre ne doit pas dépasser 10 caractères")
    @Column(nullable = false, length = 10)
    private String gender; // MALE, FEMALE, OTHER

    @Email(message = "L'adresse email n'est pas valide")
    @Size(max = 100, message = "L'adresse email ne doit pas dépasser 100 caractères")
    @Column(length = 100)
    private String email;

    @Size(max = 30, message = "Le numéro de téléphone ne doit pas dépasser 30 caractères")
    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Size(max = 150, message = "Le nom du parent ne doit pas dépasser 150 caractères")
    @Column(name = "parent_name", length = 150)
    private String parentName;

    @Size(max = 30, message = "Le téléphone du parent ne doit pas dépasser 30 caractères")
    @Column(name = "parent_phone", length = 30)
    private String parentPhone;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Size(max = 250, message = "Les allergies ne doivent pas dépasser 250 caractères")
    @Column(name = "allergies", length = 250)
    private String allergies;

    @Size(max = 150, message = "Le nom du contact d'urgence ne doit pas dépasser 150 caractères")
    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Size(max = 30, message = "Le téléphone du contact d'urgence ne doit pas dépasser 30 caractères")
    @Column(name = "emergency_contact_phone", length = 30)
    private String emergencyContactPhone;
}
