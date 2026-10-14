package com.robustvision.platform.domain;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="study_document",uniqueConstraints=@UniqueConstraint(columnNames={"owner_id","sha256"}))
public class StudyDocumentEntity {
    @Id @Column(length=36) public String id=UUID.randomUUID().toString();
    @Column(name="owner_id",nullable=false) public Long ownerId;
    @Column(name="file_id",length=36,nullable=false) @JdbcTypeCode(SqlTypes.CHAR) public String fileId;
    @Column(nullable=false) public String title;
    @Column(length=64,nullable=false) public String sha256;
    @Column(length=8,nullable=false) public String format;
    @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
}
