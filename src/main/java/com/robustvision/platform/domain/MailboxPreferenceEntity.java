package com.robustvision.platform.domain;

import jakarta.persistence.*;

@Entity @Table(name="mailbox_preference",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","message_id"}))
public class MailboxPreferenceEntity {
    @Id @Column(length=36) private String id;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="message_id",nullable=false,length=36)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR) private String messageId;
    @Column(nullable=false,length=12) private String state;
}
