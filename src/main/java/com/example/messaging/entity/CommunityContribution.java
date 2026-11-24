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
@Table(name = "community_contributions", indexes = {
        @Index(name = "idx_contributions_fund_created", columnList = "fund_id, created_at"),
        @Index(name = "idx_contributions_pending_created", columnList = "status, created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_contributions_external_id", columnNames = "external_id"),
        @UniqueConstraint(name = "uk_contributions_provider_transaction", columnNames = "provider_transaction_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityContribution {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "fund_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID fundId;

    @Column(name = "contributor_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID contributorId;

    @Column(nullable = false)
    private long amount;

    @Builder.Default
    @Column(name = "net_amount")
    private long netAmount = 0L;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "XAF";

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContributionStatus status = ContributionStatus.PENDING;

    @Column(name = "external_id", nullable = false, updatable = false, length = 100)
    private String externalId;

    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    @Column(name = "payment_link", length = 1000)
    private String paymentLink;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
