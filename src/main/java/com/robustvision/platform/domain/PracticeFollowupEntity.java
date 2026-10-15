package com.robustvision.platform.domain;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name="practice_followup")
public class PracticeFollowupEntity {
 @Id @Column(length=96) public String id;
 public Long ownerId;
 @Column(length=100) public String termKey;
 @Column(length=20) public String kind;
 @Column(length=500) public String detail;
 public LocalDate dueDate;
 public int independentPasses;
 public LocalDate lastPass;
}
