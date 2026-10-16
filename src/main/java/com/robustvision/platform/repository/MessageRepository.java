package com.robustvision.platform.repository;

import com.robustvision.platform.domain.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface MessageRepository extends JpaRepository<MessageEntity, String> {
    @Query("""
        select m.id as id, m.sender.id as senderId, m.sender.displayName as senderName,
        m.subject as subject, substring(m.body,1,240) as body, m.createdAt as createdAt,
        m.workspaceId as workspaceId, m.replyToId as replyToId
        from MessageEntity m where m.workspaceId is null
        and (m.sender.id=:userId or exists (select r.id from MessageRecipientEntity r where r.message.id=m.id and r.recipient.id=:userId))
        and exists (select p.id from MailboxPreferenceEntity p where p.userId=:userId and p.messageId=m.id and p.state=:state)
        order by m.createdAt desc,m.id
        """)
    org.springframework.data.domain.Page<MessageRow> pageState(@Param("userId") Long userId,@Param("state") String state,org.springframework.data.domain.Pageable pageable);
    List<MessageEntity> findBySenderIdOrderByCreatedAtDesc(Long senderId);

    // Scalar projections avoid fetching users, roles and permissions for each row.
    interface MessageRow {
        String getId();
        Long getSenderId();
        String getSenderName();
        String getSubject();
        String getBody();
        Instant getCreatedAt();
        Long getWorkspaceId();
        String getReplyToId();
    }

    @Query("""
            select m.id as id, m.sender.id as senderId, m.sender.displayName as senderName,
                   m.subject as subject, m.body as body, m.createdAt as createdAt,
                   m.workspaceId as workspaceId, m.replyToId as replyToId
            from MessageEntity m where m.sender.id = :userId and m.workspaceId is null
            and not exists (select p.id from MailboxPreferenceEntity p where p.userId=:userId and p.messageId=m.id and p.state<>'ACTIVE')
            order by m.createdAt desc, m.id asc
            """)
    List<MessageRow> findSentRows(@Param("userId") Long userId);

    @Query("""
            select m.id as id, m.sender.id as senderId, m.sender.displayName as senderName,
                   m.subject as subject, m.body as body, m.createdAt as createdAt,
                   m.workspaceId as workspaceId, m.replyToId as replyToId
            from MessageRecipientEntity r join r.message m where r.recipient.id = :userId and m.workspaceId is null
            and not exists (select p.id from MailboxPreferenceEntity p where p.userId=:userId and p.messageId=m.id and p.state<>'ACTIVE')
            order by m.createdAt desc, m.id asc
            """)
    List<MessageRow> findInboxRows(@Param("userId") Long userId);

    @Query("""
            select m.id as id, m.sender.id as senderId, m.sender.displayName as senderName,
                   m.subject as subject, m.body as body, m.createdAt as createdAt,
                   m.workspaceId as workspaceId, m.replyToId as replyToId
            from MessageEntity m where m.workspaceId = :workspaceId order by m.createdAt desc, m.id desc
            """)
    List<MessageRow> findGroupRows(@Param("workspaceId") Long workspaceId, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select m.id as id, m.sender.id as senderId, m.sender.displayName as senderName,
                   m.subject as subject, substring(m.body,1,240) as body, m.createdAt as createdAt,
                   m.workspaceId as workspaceId, m.replyToId as replyToId
            from MessageEntity m where m.sender.id = :userId and m.workspaceId is null
            and not exists (select p.id from MailboxPreferenceEntity p where p.userId=:userId and p.messageId=m.id and p.state<>'ACTIVE')
            order by m.createdAt desc, m.id asc
            """)
    org.springframework.data.domain.Page<MessageRow> pageSent(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select m.id as id, m.sender.id as senderId, m.sender.displayName as senderName,
                   m.subject as subject, substring(m.body,1,240) as body, m.createdAt as createdAt,
                   m.workspaceId as workspaceId, m.replyToId as replyToId
            from MessageRecipientEntity r join r.message m where r.recipient.id = :userId and m.workspaceId is null
            and not exists (select p.id from MailboxPreferenceEntity p where p.userId=:userId and p.messageId=m.id and p.state<>'ACTIVE')
            order by m.createdAt desc, m.id asc
            """)
    org.springframework.data.domain.Page<MessageRow> pageInbox(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);
}
