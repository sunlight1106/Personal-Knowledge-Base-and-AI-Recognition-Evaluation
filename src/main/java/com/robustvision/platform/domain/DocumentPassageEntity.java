package com.robustvision.platform.domain;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="document_passage")
public class DocumentPassageEntity {
    @Id @Column(length=36) public String id=UUID.randomUUID().toString();
    @Column(name="document_id",length=36,nullable=false) public String documentId;
    @Column(name="owner_id",nullable=false) public Long ownerId;
    @Column(name="position_number",nullable=false) public int positionNumber;
    @Column(length=60,nullable=false) public String locator;
    @Column(columnDefinition="TEXT",nullable=false) public String body;
}
