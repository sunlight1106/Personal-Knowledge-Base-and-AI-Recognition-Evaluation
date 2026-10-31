package com.robustvision.platform.service;

import com.robustvision.platform.common.BusinessException;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.dto.SocialDtos;
import com.robustvision.platform.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class SocialService {
    @org.springframework.beans.factory.annotation.Autowired private ModerationGuard moderation;
    @org.springframework.beans.factory.annotation.Autowired private LiveUpdateService live;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final UserRepository users;
    private final ContactLinkRepository contacts;
    private final ChatMessageRepository chats;
    private final CurrentUserService current;
    public SocialService(UserRepository users, ContactLinkRepository contacts, ChatMessageRepository chats, CurrentUserService current) {
        this.users = users; this.contacts = contacts; this.chats = chats; this.current = current;
    }
    @Transactional(readOnly = true)
    public SocialDtos.Discoverability settings() { return new SocialDtos.Discoverability(current.requireCurrent().isDiscoverable(), ContactDiscoveryPolicy.protectedAccount(current.requireCurrent())); }
    @Transactional
    public SocialDtos.Discoverability settings(boolean value) {
        UserEntity user = users.findLockedById(current.requireCurrent().getId()).orElseThrow();
        user.setDiscoverable(value);
        return new SocialDtos.Discoverability(value,ContactDiscoveryPolicy.protectedAccount(user));
    }
    @Transactional(readOnly=true)
    public SocialDtos.DiscoverySettings discovery() { return discoveryView(current.requireCurrent()); }
    private SocialDtos.DiscoverySettings discoveryView(UserEntity u) {
        return new SocialDtos.DiscoverySettings(u.isDiscoverable(), u.getContactNumber(), u.isSearchByNumber(), u.isSearchByEmail(), u.isSearchByPhone(), ContactDiscoveryPolicy.protectedAccount(u));
    }
    @Transactional
    public SocialDtos.DiscoverySettings discovery(SocialDtos.DiscoveryUpdate value) {
        var u=users.findLockedById(current.requireCurrent().getId()).orElseThrow();
        u.setDiscoverable(value.discoverable());u.setSearchByNumber(value.searchByNumber());u.setSearchByEmail(value.searchByEmail());u.setSearchByPhone(value.searchByPhone());
        return discoveryView(u);
    }
    @Transactional
    public SocialDtos.DiscoverySettings number(String raw) {
        var u=users.findLockedById(current.requireCurrent().getId()).orElseThrow();
        String number=raw.trim().toLowerCase(Locale.ROOT);
        if (!number.matches("[a-z0-9]{6,24}")) throw bad("用户号须为 6–24 位字母或数字");
        if (users.existsByContactNumberAndIdNot(number,u.getId())) throw numberTaken();
        u.setContactNumber(number);
        try { users.saveAndFlush(u); } catch(org.springframework.dao.DataIntegrityViolationException conflict) { throw numberTaken(); }
        return discoveryView(u);
    }
    private BusinessException numberTaken() { return new BusinessException(HttpStatus.CONFLICT,"CONTACT_NUMBER_TAKEN","此用户号不可用，请换一个"); }
    @Transactional(readOnly = true)
    public List<SocialDtos.Person> search(String query) { return search(query, "auto"); }
    @Transactional(readOnly = true)
    public List<SocialDtos.Person> search(String query, String mode) {
        String text = query == null ? "" : query.trim();
        if (text.length() < 2 || text.length() > 160) throw bad("请输入完整身份码，或 2–160 个字符的用户名、昵称前缀");
        String escaped = text.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        if (text.matches("(?i)PKB-[0-9A-F]{32}")) escaped = ""; // A full identity code is an exact lookup, not a username prefix.
        String kind = mode == null ? "auto" : mode;
        if ("auto".equals(kind)) kind = text.contains("@") ? "email" : text.startsWith("+") || text.matches("1[3-9][0-9]{9}") ? "phone" : "name";
        if (!Set.of("name","number","email","phone").contains(kind)) throw bad("请选择有效的查找方式");
        String exact = "phone".equals(kind) ? ContactDiscoveryPolicy.phone(text) : text.toLowerCase(Locale.ROOT);
        return users.searchPeople(current.requireCurrent().getId(), escaped, text.toUpperCase(Locale.ROOT), kind, exact, PageRequest.of(0, 20)).stream()
                .filter(u -> users.findById(u.getId()).filter(ContactDiscoveryPolicy::canDiscover).isPresent())
                .map(u -> new SocialDtos.Person(u.getId(), u.getIdentityCode(), u.getUsername(), u.getDisplayName(), u.getContactNumber())).toList();
    }
    @Transactional(readOnly = true)
    public List<SocialDtos.Contact> list() {
        long me = current.requireCurrent().getId();
        return contacts.visible(me).stream().map(c -> new SocialDtos.Contact(c.getId(), c.getUserId(), c.getIdentityCode(), c.getUsername(), c.getDisplayName(),
                c.getStatus(), c.getRequesterId() != me, c.getBlockedByMe(), c.getAvailable(), c.getRemark(), c.getPinned(), c.getMuted(),
                c.getAvailable() && "ACCEPTED".equals(c.getStatus()) ? c.getUnreadCount() : 0, c.getClearedThrough())).toList();
    }
    @Transactional
    public void request(long peer) {
        long me = current.requireCurrent().getId();
        if (me == peer) throw bad("不能添加自己");
        UserEntity target = lockUsers(me, peer);
        ContactLinkEntity link = contacts.lockPair(Math.min(me, peer), Math.max(me, peer)).orElse(null);
        if (ContactDiscoveryPolicy.protectedAccount(target) || ContactDiscoveryPolicy.protectedAccount(users.findById(me).orElseThrow()) || !target.hasActiveAccess(Instant.now()) || (link == null && !target.isDiscoverable()) || link != null && link.blocked()) throw unavailable();
        if (link != null && Set.of("PENDING", "ACCEPTED").contains(link.getStatus())) return;
        if (!target.isDiscoverable()) throw unavailable();
        if (link != null && link.getUpdatedAt().isAfter(Instant.now().minusSeconds(86400)))
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "CONTACT_COOLDOWN", "关系变更后请等待 24 小时再申请");
        if (contacts.countActive(me) >= 200 || contacts.countActive(peer) >= 200)
            throw new BusinessException(HttpStatus.CONFLICT, "CONTACT_LIMIT", "联系人和待处理申请已达上限（200），请先处理已有申请");
        if (link == null) link = new ContactLinkEntity(me, peer, me); else link.request(me);
        contacts.save(link);
        live.changed(List.of(me,peer));
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void act(long id, String action) {
        long me = current.requireCurrent().getId();
        ContactLinkEntity link = locked(id, me);
        switch (action) {
            case "accept", "reject" -> {
                if (link.blocked() || !"PENDING".equals(link.getStatus()) || link.getRequesterId() == me) throw unavailable();
                if (!users.findById(link.peer(me)).orElseThrow().hasActiveAccess(Instant.now())) throw unavailable();
                if (action.equals("accept") && (ContactDiscoveryPolicy.protectedAccount(users.findById(me).orElseThrow()) || ContactDiscoveryPolicy.protectedAccount(users.findById(link.peer(me)).orElseThrow()))) throw unavailable();
                link.setStatus(action.equals("accept") ? "ACCEPTED" : "REJECTED");
            }
            case "withdraw" -> {
                if (!"PENDING".equals(link.getStatus()) || link.getRequesterId() != me || link.blocked()) throw unavailable();
                link.setStatus("REMOVED");
            }
            case "remove" -> {
                if (!"ACCEPTED".equals(link.getStatus())) throw unavailable();
                link.setStatus("REMOVED");
            }
            case "block" -> { checkBlockLimit(me, link); link.block(me, true); }
            case "unblock" -> link.block(me, false);
            default -> throw bad("不支持的联系人操作");
        }
        live.changed(List.of(me, link.peer(me)));
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void block(long peer) {
        long me = current.requireCurrent().getId();
        if (me == peer) throw bad("不能将自己加入黑名单");
        UserEntity target = lockUsers(me, peer);
        ContactLinkEntity link = contacts.lockPair(Math.min(me, peer), Math.max(me, peer)).orElse(null);
        if (link == null) {
            if (!target.isDiscoverable() || !target.hasActiveAccess(Instant.now())) throw unavailable();
            link = new ContactLinkEntity(me, peer, me);
            link.setStatus("REMOVED"); // Blocking a stranger never creates a friend request.
        } else {
            entityManager.refresh(link, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        }
        checkBlockLimit(me, link);
        if (link.blockedBy(me)) return;
        link.block(me, true);
        contacts.save(link);
        live.changed(List.of(me, peer));
    }
    private void checkBlockLimit(long me, ContactLinkEntity link) {
        // A stranger's private block record must not become an identity-discovery endpoint.
        if (!link.blockedBy(me) && !Set.of("PENDING", "ACCEPTED").contains(link.getStatus()) &&
                (link.blocked() || users.findById(link.peer(me)).filter(u -> u.isDiscoverable() && u.hasActiveAccess(Instant.now())).isEmpty()))
            throw unavailable();
        if (!link.blockedBy(me) && contacts.countBlockedBy(me) >= 500)
            throw new BusinessException(HttpStatus.CONFLICT, "BLACKLIST_LIMIT", "黑名单已达上限（500），请先整理已有名单");
    }
    @Transactional
    public void preferences(long id, SocialDtos.Preferences value) {
        long me = current.requireCurrent().getId();
        ContactLinkEntity link = locked(id, me);
        if (!"ACCEPTED".equals(link.getStatus())) throw unavailable();
        link.preferences(me, value.remark(), value.pinned(), value.muted());
        live.changed(List.of(me));
    }
    @Transactional
    public void read(long id, long through) {
        long me = current.requireCurrent().getId();
        ContactLinkEntity link = locked(id, me);
        requireChat(link, me);
        if (!chats.existsByIdAndContactId(through, id)) throw bad("消息不属于当前会话");
        if (link.readThrough(me) < through) {
            link.markRead(me, through);
            live.changed(List.of(me));
        }
    }
    @Transactional
    public void clearHistory(long id) {
        long me = current.requireCurrent().getId();
        ContactLinkEntity link = locked(id, me);
        long through = chats.findFirstByContactIdOrderByIdDesc(id).map(ChatMessageEntity::getId).orElse(0L);
        link.clearHistory(me, through);
        live.changed(List.of(me));
    }
    @Transactional(readOnly = true)
    public List<SocialDtos.ChatMessage> messages(long id, Long after, Long before) {
        if (after != null && before != null || after != null && after < 0 || before != null && before < 1) throw bad("消息游标无效");
        long me = current.requireCurrent().getId();
        ContactLinkEntity link = owned(id, me);
        requireChat(link, me);
        long cleared = link.clearedThrough(me);
        List<ChatMessageEntity> rows = after != null
                ? chats.findByContactIdAndIdGreaterThanOrderByIdAsc(id, Math.max(after, cleared), PageRequest.of(0, 50))
                : chats.findByContactIdAndIdGreaterThanAndIdLessThanOrderByIdDesc(id, cleared, before == null ? Long.MAX_VALUE : before, PageRequest.of(0, 50));
        return rows.stream().sorted(Comparator.comparing(ChatMessageEntity::getId)).map(this::view).toList();
    }
    @Transactional
    public SocialDtos.ChatMessage send(long id, SocialDtos.ChatRequest request) {
        UserEntity me = current.requireCurrent();
        moderation.sending(me.getId());
        ContactLinkEntity link = locked(id, me.getId());
        requireChat(link, me.getId());
        // Client-generated key makes a retry after a lost HTTP response safe.
        var existing = chats.findBySenderIdAndClientId(me.getId(), request.clientId());
        if (existing.isPresent()) {
            ChatMessageEntity message = existing.get();
            if (!message.getContact().getId().equals(id) || !message.getBody().equals(request.body().trim()))
                throw new BusinessException(HttpStatus.CONFLICT, "CHAT_RETRY_MISMATCH", "发送内容已变化，请重新发送");
            return view(message);
        }
        var saved=view(chats.save(new ChatMessageEntity(link, me, request.clientId(), request.body().trim())));
        live.changed(List.of(me.getId(),link.peer(me.getId())));
        return saved;
    }
    @Transactional(readOnly=true)
    public List<SocialDtos.ChatMessage> searchMessages(long id,String query,int page) {
        if(query==null||query.isBlank()||query.length()>160||page<0||page>10000) throw bad("请输入 1–160 个字符的搜索词");
        long me=current.requireCurrent().getId();ContactLinkEntity link=owned(id,me);requireChat(link,me);
        String pattern="%"+query.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return chats.searchHistory(id,link.clearedThrough(me),pattern,PageRequest.of(page,30)).stream().map(this::view).toList();
    }
    private SocialDtos.ChatMessage view(ChatMessageEntity message) {
        return new SocialDtos.ChatMessage(message.getId(), message.getSender().getId(), message.getSender().getDisplayName(),
                message.getClientId(), message.getBody(), message.getCreatedAt());
    }
    private ContactLinkEntity owned(long id, long me) {
        ContactLinkEntity link = contacts.findById(id).orElseThrow(this::unavailable);
        if (!link.includes(me)) throw unavailable(); // No administrator bypass for private conversations.
        return link;
    }
    private ContactLinkEntity locked(long id, long me) {
        ContactLinkEntity snapshot = owned(id, me);
        lockUsers(me, snapshot.peer(me));
        ContactLinkEntity link = contacts.lockPair(snapshot.getLowUserId(), snapshot.getHighUserId()).orElseThrow(this::unavailable);
        // The access check may already have loaded this entity before waiting on the user lock.
        entityManager.refresh(link, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return link;
    }
    private UserEntity lockUsers(long me, long peer) {
        UserEntity low = users.findLockedById(Math.min(me, peer)).orElseThrow(this::unavailable);
        UserEntity high = users.findLockedById(Math.max(me, peer)).orElseThrow(this::unavailable);
        entityManager.refresh(low, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(high, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return peer == low.getId() ? low : high;
    }
    private void requireChat(ContactLinkEntity link, long me) {
        if (link.blocked() || !"ACCEPTED".equals(link.getStatus()) || !users.findById(link.peer(me)).orElseThrow(this::unavailable).hasActiveAccess(Instant.now())) throw unavailable();
    }
    private BusinessException unavailable() { return new BusinessException(HttpStatus.NOT_FOUND, "CONTACT_UNAVAILABLE", "联系人不存在或当前无法交流，请刷新联系人列表"); }
    private BusinessException bad(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, "CONTACT_INVALID", message); }
}
