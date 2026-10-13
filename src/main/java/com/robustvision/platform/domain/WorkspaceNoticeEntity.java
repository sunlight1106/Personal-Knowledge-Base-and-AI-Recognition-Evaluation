package com.robustvision.platform.domain;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
@Entity @Table(name="workspace_notice")
public class WorkspaceNoticeEntity {
    @Id @Column(name="workspace_id") public Long workspaceId;
    @Column(nullable=false,columnDefinition="TEXT") public String announcement;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="pinned_message_id",length=36) public String pinnedMessageId;
    @Column(nullable=false) public long revision;
    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name="announcement_revision",nullable=false) public long announcementRevision;
    @Column(name="updated_at",nullable=false) public Instant updatedAt;
}
