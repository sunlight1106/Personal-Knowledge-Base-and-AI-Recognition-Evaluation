package com.robustvision.platform.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Schema mapping; counters are updated under the owning user's database lock. */
@Entity @Table(name = "account_login_throttle")
public class AccountLoginThrottleEntity {
    @Id @Column(name = "owner_id") private Long ownerId;
    @Column(nullable = false) private int failures;
    @Column(name = "window_started_at", nullable = false) private Instant windowStartedAt;
    @Column(name = "blocked_until") private Instant blockedUntil;
}
