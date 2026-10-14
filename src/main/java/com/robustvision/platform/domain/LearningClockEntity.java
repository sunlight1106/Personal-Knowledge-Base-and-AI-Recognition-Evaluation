package com.robustvision.platform.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="learning_clock")
public class LearningClockEntity {
    @Id @Column(name="owner_id") public Long ownerId;
    @Column(name="last_ping") public Instant lastPing;
}
