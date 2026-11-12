package com.robustvision.platform.domain;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity @Table(name="release_read_receipt")
public class ReleaseReadReceiptEntity {
    @EmbeddedId private Key id;
    @Column(name="read_at",nullable=false) private Instant readAt;
    @Embeddable public static class Key implements Serializable {
        @Column(name="owner_id",nullable=false) private Long ownerId;
        @Column(name="release_version",nullable=false,length=20) private String version;
        public Key() {}
        @Override public boolean equals(Object other) { return other instanceof Key k && Objects.equals(ownerId,k.ownerId) && Objects.equals(version,k.version); }
        @Override public int hashCode() { return Objects.hash(ownerId,version); }
    }
}
