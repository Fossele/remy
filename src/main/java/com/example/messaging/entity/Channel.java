package com.example.messaging.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "channels")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Channel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "created_by", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChannelType type;

    @CreationTimestamp
    @Column( name = "created_at",nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;



}
