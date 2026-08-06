package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "catering_menus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CateringMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "monday_menu", length = 500)
    private String mondayMenu;

    @Column(name = "tuesday_menu", length = 500)
    private String tuesdayMenu;

    @Column(name = "wednesday_menu", length = 500)
    private String wednesdayMenu;

    @Column(name = "thursday_menu", length = 500)
    private String thursdayMenu;

    @Column(name = "friday_menu", length = 500)
    private String fridayMenu;
}
