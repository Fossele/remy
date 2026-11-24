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
@Table(name = "fund_withdrawal_payout_attempts",
        indexes = @Index(name = "idx_payout_attempts_withdrawal_created", columnList = "withdrawal_id, created_at"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payout_attempt_external_id", columnNames = "external_id"),
                @UniqueConstraint(name = "uk_payout_attempt_transaction_id", columnNames = "provider_transaction_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundWithdrawalPayoutAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "withdrawal_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID withdrawalId;

    @Column(name = "external_id", nullable = false, updatable = false, length = 100)
    private String externalId;

    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    @Column(nullable = false)
    private long amount;

    @Column(name = "initiated_by", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID initiatedBy;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FundWithdrawalPayoutStatus status = FundWithdrawalPayoutStatus.INITIATING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
