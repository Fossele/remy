package com.example.messaging.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fund_withdrawal_approvals",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fund_withdrawal_approver",
                columnNames = {"withdrawal_id", "admin_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundWithdrawalApproval {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "withdrawal_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID withdrawalId;

    @Column(name = "admin_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID adminId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WithdrawalApprovalDecision decision = WithdrawalApprovalDecision.PENDING;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
