package org.camp.camp.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "camp_members", uniqueConstraints = @UniqueConstraint(columnNames = {"camp_id", "user_id"}))
@Data
public class CampMember {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "camp_id", nullable = false)
    private UUID campId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.MEMBER;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    void onCreate() {
        joinedAt = Instant.now();
    }
}