package com.robustvision.platform.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="ai_diagnostic")
public class AiDiagnosticEntity {
 @Id @Column(length=80) public String id;
 @Column(nullable=false) public long ownerId;
 @Column(nullable=false,length=36) public String settingId;
 @Column(nullable=false) public long revision;
 @Column(nullable=false,length=16) public String mode;
 @Column(nullable=false) public boolean success;
 @Column(nullable=false,length=80) public String code;
 @Column(nullable=false) public Instant checkedAt;
}
