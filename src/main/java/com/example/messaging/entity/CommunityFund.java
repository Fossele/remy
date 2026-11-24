package com.example.messaging.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "community_funds", indexes = {
        @Index(name = "idx_community_funds_neighborhood", columnList = "neighborhood_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityFund {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "neighborhood_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID neighborhoodId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "target_amount")
    private Long targetAmount;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "XAF";

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunityFundStatus status = CommunityFundStatus.ACTIVE;

    @Column(name = "created_by", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
