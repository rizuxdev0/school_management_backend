package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * Représente un ouvrage/livre répertorié dans la bibliothèque de l'établissement (SaaS).
 */
@Entity
@Table(name = "books")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(length = 30)
    private String isbn;

    @Column(length = 100)
    private String publisher;

    @Column(name = "copies_total", nullable = false)
    @Builder.Default
    private Integer copiesTotal = 1;

    @Column(name = "copies_available", nullable = false)
    @Builder.Default
    private Integer copiesAvailable = 1;

    @Column(length = 50)
    private String category; // Roman, Dictionnaire, Scientifique, etc.

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
