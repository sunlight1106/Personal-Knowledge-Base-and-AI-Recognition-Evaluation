package com.robustvision.platform.domain;
import jakarta.persistence.*;
@Entity @Table(name="study_plan")
public class StudyPlanEntity {
 @Id public Long ownerId;
 public int minutes=20;
 public int spreadDays=7;
 @Version public long revision;
}
