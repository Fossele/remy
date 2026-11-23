package com.example.messaging.controller;

import com.example.messaging.dto.AuditEventResponse;
import com.example.messaging.entity.AuditAction;
import com.example.messaging.entity.AuditOutcome;
import com.example.messaging.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/neighborhoods")
public class AuditController {
    private final AuditService auditService;

    @GetMapping("/{neighborhoodId}/audit-events")
    public Page<AuditEventResponse> listAuditEvents(
            @PathVariable UUID neighborhoodId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditOutcome outcome,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return auditService.listNeighborhoodEvents(
                neighborhoodId,
                UUID.fromString(jwt.getSubject()),
                actorId,
                action,
                outcome,
                pageable
        );
    }
}
