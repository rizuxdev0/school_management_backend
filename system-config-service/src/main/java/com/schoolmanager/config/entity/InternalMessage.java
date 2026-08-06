package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "internal_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "sender_name", length = 150)
    private String senderName;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "recipient_name", length = 150)
    private String recipientName;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Builder.Default
    @Column(name = "is_read")
    private Boolean isRead = false;
}
