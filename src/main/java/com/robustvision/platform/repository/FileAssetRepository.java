package com.robustvision.platform.repository;

import com.robustvision.platform.domain.FileAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileAssetRepository extends JpaRepository<FileAssetEntity, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select f from FileAssetEntity f where f.id=:id")
    java.util.Optional<FileAssetEntity> lockById(@org.springframework.data.repository.query.Param("id") String id);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"owner"})
    org.springframework.data.domain.Page<FileAssetEntity> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Long owner,org.springframework.data.domain.Pageable pageable);
    boolean existsByIdAndOwnerId(String id, Long ownerId);
    List<FileAssetEntity> findAllByOrderByCreatedAtDesc();
    List<FileAssetEntity> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);


    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"owner"})
    @org.springframework.data.jpa.repository.Query("""
        select f from FileAssetEntity f where (:all=true or f.owner.id=:owner)
        and f.deletedAt is null and f.source<>com.robustvision.platform.domain.FileSource.MESSAGE_ATTACHMENT
        and (:source is null or f.source=:source)
        and (:query='' or locate(lower(:query),lower(f.originalName))>0)
        order by f.createdAt desc,f.id
        """)
    org.springframework.data.domain.Page<FileAssetEntity> pageRows(
        @org.springframework.data.repository.query.Param("owner") Long owner,
        @org.springframework.data.repository.query.Param("all") boolean all,
        @org.springframework.data.repository.query.Param("query") String query,
        @org.springframework.data.repository.query.Param("source") com.robustvision.platform.domain.FileSource source,
        org.springframework.data.domain.Pageable pageable);

}
