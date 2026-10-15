package com.robustvision.platform.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="reading_clip")
public class ReadingClipEntity {
 @Id @Column(length=36) public String id;
 public Long ownerId;
 @Column(length=20) public String sourceKind;
 @Column(length=36) public String sourceId;
 @Column(length=4000) public String quoteText;
 @Column(length=100) public String term;
 @Column(length=200) public String sourceTitle;
 public Instant createdAt;
}
