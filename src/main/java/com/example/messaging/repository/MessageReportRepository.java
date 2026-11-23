package com.example.messaging.repository;

import com.example.messaging.entity.MessageReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MessageReportRepository extends JpaRepository<MessageReport, UUID> {
}
