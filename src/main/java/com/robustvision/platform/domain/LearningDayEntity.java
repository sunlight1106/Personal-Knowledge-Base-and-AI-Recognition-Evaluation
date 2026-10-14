package com.robustvision.platform.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name="learning_day",uniqueConstraints=@UniqueConstraint(columnNames={"owner_id","study_date"}))
public class LearningDayEntity {
    @Id @Column(length=40) public String id;
    @Column(name="owner_id",nullable=false) public Long ownerId;
    @Column(name="study_date",nullable=false) public LocalDate studyDate;
    @Column(name="active_seconds",nullable=false) public long activeSeconds;
}
