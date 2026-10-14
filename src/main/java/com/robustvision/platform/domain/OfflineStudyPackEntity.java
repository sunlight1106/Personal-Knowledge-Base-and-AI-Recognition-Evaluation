package com.robustvision.platform.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="offline_study_pack")
public class OfflineStudyPackEntity {
 @Id @Column(length=36) public String id;
 @Column(nullable=false) public long ownerId;
 @Column(nullable=false,columnDefinition="MEDIUMTEXT") public String payload;
 @Column(nullable=false) public Instant createdAt;
 @Column(nullable=false) public Instant expiresAt;
}
