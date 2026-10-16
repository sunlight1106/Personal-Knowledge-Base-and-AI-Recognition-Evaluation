package com.robustvision.platform.domain;
import jakarta.persistence.*;
@Entity @Table(name="runtime_operation_lock")
public class RuntimeOperationLockEntity {
 @Id @Column(length=60) private String name;
 @Column(length=36) private String token;
 @Column(name="expires_at",nullable=false) private java.time.Instant expiresAt;
}
