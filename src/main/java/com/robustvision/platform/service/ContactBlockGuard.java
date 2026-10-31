package com.robustvision.platform.service;

import com.robustvision.platform.repository.ContactLinkRepository;
import com.robustvision.platform.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.TreeSet;

/** Serializes delivery/invitations with block changes, including MySQL snapshot transactions. */
@Service
public class ContactBlockGuard {
    private final UserRepository users;
    private final ContactLinkRepository contacts;
    @PersistenceContext private EntityManager entityManager;
    public ContactBlockGuard(UserRepository users, ContactLinkRepository contacts) {
        this.users = users; this.contacts = contacts;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean anyBlocked(long sender, Collection<Long> recipients) {
        TreeSet<Long> ordered = new TreeSet<>(recipients);
        ordered.add(sender);
        // Use the same ordering as SocialService. Lock users even if no contact row exists yet.
        for (long id : ordered) if (users.findLockedById(id).isEmpty()) return true;
        for (long peer : recipients) {
            if (peer == sender) continue;
            var pair = contacts.lockPair(Math.min(sender, peer), Math.max(sender, peer));
            if (pair.isPresent()) {
                var link = pair.get();
                entityManager.refresh(link, LockModeType.PESSIMISTIC_WRITE);
                if (link.blocked()) return true;
            }
        }
        return false;
    }
}
