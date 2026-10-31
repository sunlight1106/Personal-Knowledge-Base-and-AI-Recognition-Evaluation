package com.robustvision.platform.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="phone_binding")
public class PhoneBindingEntity {
    @Id @Column(name="owner_id") public Long ownerId;
    @Column(name="verified_phone",unique=true,length=16) public String verifiedPhone;
    @Column(name="pending_phone",length=16) public String pendingPhone;
    @Column(name="challenge_secret",columnDefinition="TEXT") public String challengeSecret;
    @Column(name="expires_at") public Instant expiresAt;
    @Column(nullable=false) public int attempts;
    @Column(name="next_send_at") public Instant nextSendAt;
    @Column(name="send_day",length=10) public String sendDay;
    @Column(name="send_count",nullable=false) public int sendCount;
    public PhoneBindingEntity() {}
    public PhoneBindingEntity(long owner) { ownerId=owner; }
    public void clearChallenge() { pendingPhone=null;challengeSecret=null;expiresAt=null;attempts=0; }
}
