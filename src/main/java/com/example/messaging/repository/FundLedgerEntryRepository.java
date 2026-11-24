package com.example.messaging.repository;

import com.example.messaging.entity.FundLedgerEntry;
import com.example.messaging.entity.FundLedgerSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FundLedgerEntryRepository extends JpaRepository<FundLedgerEntry, UUID> {
    List<FundLedgerEntry> findByFundId(UUID fundId);
    boolean existsBySourceTypeAndSourceId(FundLedgerSourceType sourceType, UUID sourceId);
}
