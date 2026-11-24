package com.example.messaging.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fund_ledger_entries",
        indexes = @Index(name = "idx_fund_ledger_fund_created", columnList = "fund_id, created_at"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fund_ledger_source",
                columnNames = {"source_type", "source_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundLedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "fund_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID fundId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FundLedgerEntryType type;

    @Column(nullable = false)
    private long amount;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "XAF";

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private FundLedgerSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID sourceId;

    @Column(name = "created_by")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PreUpdate
    @PreRemove
    private void preventMutation() {
        throw new UnsupportedOperationException("Fund ledger entries are append-only");
    }
}
