package com.robustvision.platform.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Schema mapping; all access is through explicitly owner-scoped queries. */
@Entity
@Table(name="code_run",indexes=@Index(name="idx_code_run_owner_time",columnList="owner_id,created_at,id"))
public class CodeRunEntity {
    @Id @Column(length=36) private String id;
    @Column(name="owner_id",nullable=false) private Long ownerId;
    @Column(nullable=false,length=16) private String language;
    @Column(name="entry_name",nullable=false,length=60) private String entryName;
    @Column(nullable=false,columnDefinition="MEDIUMTEXT") private String source;
    @Column(name="arguments_json",nullable=false,columnDefinition="TEXT") private String argumentsJson;
    @Column(name="result_json",nullable=false,columnDefinition="MEDIUMTEXT") private String resultJson;
    @Column(nullable=false,length=16) private String status;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected CodeRunEntity() {}
}
