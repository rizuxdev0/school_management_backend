package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "school_announcements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "author_name", length = 100)
    private String authorName;

    @Column(name = "date_published", nullable = false)
    private LocalDateTime datePublished;

    @Builder.Default
    @Column(name = "target_audience", length = 50)
    private String targetAudience = "ALL"; // ALL, TEACHERS, PARENTS
}
