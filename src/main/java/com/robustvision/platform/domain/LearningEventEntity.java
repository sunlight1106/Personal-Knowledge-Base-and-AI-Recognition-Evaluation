package com.robustvision.platform.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity @Table(name="learning_event")
public class LearningEventEntity {
    @Id @Column(length=120) public String id;
    @Column(name="owner_id",nullable=false) public Long ownerId;
    @Column(nullable=false,length=20) public String kind;
    @Column(name="source_id",nullable=false,length=100) public String sourceId;
    @Column(name="study_date",nullable=false) public LocalDate studyDate;
    public Boolean correct;
    @Column(name="created_at",nullable=false) public Instant createdAt;
}
