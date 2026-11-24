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
@Table(name = "fund_withdrawals", indexes = {
        @Index(name = "idx_fund_withdrawals_fund_status", columnList = "fund_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundWithdrawal {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "fund_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID fundId;

    @Column(name = "requested_by", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID requestedBy;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 500)
    private String purpose;

    @Column(name = "beneficiary_name", nullable = false, length = 100)
    private String beneficiaryName;

    @Column(name = "beneficiary_phone", nullable = false, length = 9)
    private String beneficiaryPhone;

    @Column(name = "beneficiary_medium", length = 30)
    private String beneficiaryMedium;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FundWithdrawalStatus status = FundWithdrawalStatus.PENDING;

    @Column(name = "receipt_reference", length = 150)
    private String receiptReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
