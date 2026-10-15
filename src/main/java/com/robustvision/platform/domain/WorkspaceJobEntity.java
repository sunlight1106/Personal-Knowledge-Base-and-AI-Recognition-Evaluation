package com.robustvision.platform.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="workspace_job",uniqueConstraints=@UniqueConstraint(columnNames={"owner_id","request_key"}))
public class WorkspaceJobEntity {
 @Id @Column(length=36) public String id;
 public Long ownerId;
 @Column(length=32) public String kind;
 @Column(length=200) public String title;
 @Column(length=20) public String status;
 @Column(length=36) public String resourceId;
 @Column(length=36) public String resultId;
 @Column(length=500) public String message;
 public int attempts;
 public int progress;
 public Instant createdAt;
 public Instant updatedAt;
 public Instant leaseUntil;
 @Column(length=100) public String requestKey;
}
