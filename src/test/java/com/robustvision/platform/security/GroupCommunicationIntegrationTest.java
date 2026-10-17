package com.robustvision.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robustvision.platform.common.BusinessException;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.repository.*;
import com.robustvision.platform.service.AntivirusService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:group_security;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.bootstrap.enabled=false", "app.worker.enabled=false", "app.rate-limit.enabled=false"})
@AutoConfigureMockMvc
@Import(GroupCommunicationIntegrationTest.FastPasswords.class)
class GroupCommunicationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired MessageRepository messages;
    @Autowired MessageAttachmentRepository attachments;
    @Autowired PasswordEncoder encoder;
    @Autowired ContactLinkRepository links;
    @Autowired org.springframework.jdbc.core.JdbcTemplate groupDatabase;
    @MockBean AntivirusService scanner;
    UserEntity owner, member, outsider, admin;
    String ownerToken, memberToken, outsiderToken, adminToken;
    static final String PASSWORD = "SyntheticGroup123!";

    @BeforeEach void setup() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RoleEntity research = roles.save(new RoleEntity("R_" + suffix, "Researcher", "Synthetic", Set.of("workspace:manage", "group:use", "message:read")));
        RoleEntity basic = roles.save(new RoleEntity("B_" + suffix, "Basic", "Synthetic", Set.of("file:read", "group:use", "message:read")));
        RoleEntity administrator = roles.findByCode("ADMIN").orElseGet(() -> roles.save(new RoleEntity("ADMIN", "Admin", "Synthetic", Permissions.allCodes())));
        owner = user("owner" + suffix, research); member = user("member" + suffix, basic);
        outsider = user("outsider" + suffix, basic); admin = user("admin" + suffix, administrator);
        ownerToken = login(owner); memberToken = login(member); outsiderToken = login(outsider); adminToken = login(admin);
        when(scanner.scan(any(byte[].class))).thenReturn(new AntivirusService.ScanResult(FileScanStatus.CLEAN, "synthetic scanner"));
    }
    UserEntity user(String name, RoleEntity role) { return users.save(new UserEntity(name, encoder.encode(PASSWORD), name, name + "@example.test", role)); }
    JsonNode data(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsByteArray()).path("data"); }
    MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) { return request.header("Authorization", "Bearer " + token); }
    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token, Object body) throws Exception { return auth(request, token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)); }
    String login(UserEntity user) throws Exception { return data(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("username", user.getUsername(), "password", PASSWORD)))).andExpect(status().isOk()).andReturn()).path("token").asText(); }
    long group(String token) throws Exception { return data(mvc.perform(json(post("/api/v1/workspaces"), token, Map.of("name", "Synthetic group " + UUID.randomUUID(), "color", "#57788d"))).andExpect(status().isOk()).andReturn()).path("id").asLong(); }
    ResultActions membership(long group, String token, UserEntity user, String role, Set<String> permissions) throws Exception {
        // Set up new members through the same consent flow as the UI; this endpoint only edits members.
        var response = mvc.perform(auth(get("/api/v1/workspaces/{id}", group), token)).andReturn();
        if (response.getResponse().getStatus() == 200) {
            JsonNode view = data(response);
            boolean exists = java.util.stream.StreamSupport.stream(view.path("members").spliterator(), false)
                    .anyMatch(m -> m.path("userId").asLong() == user.getId());
            boolean manages = java.util.stream.StreamSupport.stream(view.path("currentPermissions").spliterator(), false)
                    .anyMatch(p -> p.asText().equals("MEMBERS_WRITE"));
            if (!exists && manages) {
                mvc.perform(json(post("/api/v1/workspaces/{id}/invitations", group), token,
                        Map.of("targetId", user.getId(), "role", role.equals("VIEWER") ? "VIEWER" : "MEMBER"))).andExpect(status().isOk());
                String recipientToken = login(user);
                JsonNode pending = data(mvc.perform(auth(get("/api/v1/workspaces/invitations"), recipientToken)).andExpect(status().isOk()).andReturn());
                String ticket = java.util.stream.StreamSupport.stream(pending.spliterator(), false)
                        .filter(i -> i.path("groupId").asLong(i.path("groupid").asLong()) == group).findFirst().orElseThrow().path("id").asText();
                mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/decision", ticket), recipientToken, Map.of("accept", true))).andExpect(status().isOk());
            }
        }
        return mvc.perform(json(put("/api/v1/workspaces/{id}/members", group), token,
                Map.of("userId", user.getId(), "role", role, "permissions", permissions)));
    }
    ResultActions postGroup(long id, String token, String text, String replyTo, boolean file) throws Exception {
        var request = multipart("/api/v1/messages/groups/{id}", id);
        if (file) request.file(new MockMultipartFile("files", "reading.txt", "text/plain", "Synthetic group material".getBytes(StandardCharsets.UTF_8)));
        request.param("body", text);
        if (replyTo != null) request.param("replyToId", replyTo);
        return mvc.perform(auth(request, token));
    }
    ResultActions direct(String token, List<Long> ids, String reply) throws Exception {
        var request = multipart("/api/v1/messages").param("recipientIds", ids.stream().map(String::valueOf).toArray(String[]::new)).param("subject", "Synthetic note").param("body", "Synthetic private text");
        if (reply != null) request.param("replyToId", reply);
        return mvc.perform(auth(request, token));
    }
    Set<Long> contacts(String token) throws Exception {
        Set<Long> ids = new HashSet<>();
        for (int page = 0; ; page++) {
            JsonNode result = data(mvc.perform(auth(get("/api/v1/messages/directory").param("page", String.valueOf(page)), token)).andExpect(status().isOk()).andReturn());
            result.forEach(item -> { ids.add(item.path("id").asLong()); assertThat(item.has("email")).isFalse(); });
            if (result.size() < 25) return ids;
        }
    }

    @Test void announcementReadReceiptsArePerMemberAndVersionAndIgnorePinChanges() throws Exception {
        long id=group(ownerToken);
        membership(id,ownerToken,member,"MEMBER",Set.of("CONTENT_READ","CONTENT_WRITE")).andExpect(status().isOk());
        mvc.perform(json(put("/api/v1/workspaces/{id}/announcement",id),ownerToken,Map.of("text","First announcement","revision",0))).andExpect(status().isOk()).andExpect(jsonPath("$.data.announcementUnread").value(false));
        mvc.perform(auth(get("/api/v1/notifications"),memberToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id == 'announcement-"+id+"')].count").value(org.hamcrest.Matchers.hasItem(1)));
        mvc.perform(json(post("/api/v1/workspaces/{id}/announcement/read",id),outsiderToken,Map.of("revision",1))).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/workspaces/{id}/announcement/read",id),memberToken,Map.of("revision",2))).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/workspaces/{id}/announcement/read",id),memberToken,Map.of("revision",1))).andExpect(status().isOk()).andExpect(jsonPath("$.data.announcementUnread").value(false));
        mvc.perform(auth(get("/api/v1/notifications"),memberToken)).andExpect(jsonPath("$.data[?(@.id == 'announcement-"+id+"')]").isEmpty());
        mvc.perform(json(put("/api/v1/workspaces/{id}/pin",id),ownerToken,Map.of("revision",1))).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/workspaces/{id}/features",id),memberToken)).andExpect(jsonPath("$.data.announcementUnread").value(false));
        mvc.perform(json(put("/api/v1/workspaces/{id}/announcement",id),ownerToken,Map.of("text","Updated announcement","revision",2))).andExpect(status().isOk());
        // A delayed acknowledgement of the old text cannot mark the new announcement read.
        mvc.perform(json(post("/api/v1/workspaces/{id}/announcement/read",id),memberToken,Map.of("revision",1))).andExpect(status().isOk()).andExpect(jsonPath("$.data.announcementUnread").value(true));
        mvc.perform(json(put("/api/v1/workspaces/{id}/preferences",id),memberToken,Map.of("pinned",false,"muted",true))).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/notifications"),memberToken)).andExpect(jsonPath("$.data[?(@.id == 'announcement-"+id+"')]").isEmpty());
        membership(id,ownerToken,member,"MEMBER",Set.of("MEMBERS_READ")).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/workspaces/{id}/features",id),memberToken)).andExpect(jsonPath("$.data.announcement").value("")).andExpect(jsonPath("$.data.announcementUnread").value(false));
    }

    @Test void mailAndGroupReadReceiptsClearNotificationsWithoutHidingNewMessages() throws Exception {
        String mail=data(direct(adminToken,List.of(member.getId()),null).andExpect(status().isOk()).andReturn()).path("id").asText();
        mvc.perform(auth(get("/api/v1/notifications"),memberToken)).andExpect(jsonPath("$.data[?(@.id == '"+mail+"')].count").value(org.hamcrest.Matchers.hasItem(1)));
        mvc.perform(auth(get("/api/v1/messages/{id}",mail),outsiderToken)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/messages/{id}",mail),memberToken)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/notifications"),memberToken)).andExpect(jsonPath("$.data[?(@.id == '"+mail+"')]").isEmpty());
        long id=group(ownerToken);membership(id,ownerToken,member,"MEMBER",Set.of("CONTENT_READ","CONTENT_WRITE")).andExpect(status().isOk());
        String message=data(postGroup(id,ownerToken,"First message",null,false).andReturn()).path("id").asText();
        mvc.perform(auth(get("/api/v1/notifications"),memberToken)).andExpect(jsonPath("$.data[?(@.id == 'group-"+id+"')].version").value(org.hamcrest.Matchers.hasItem(message)));
        mvc.perform(json(post("/api/v1/workspaces/{id}/read",id),memberToken,Map.of("messageId",message))).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/notifications"),memberToken)).andExpect(jsonPath("$.data[?(@.id == 'group-"+id+"')]").isEmpty());
        String second=data(postGroup(id,ownerToken,"Second message",null,false).andReturn()).path("id").asText();
        mvc.perform(json(post("/api/v1/workspaces/{id}/read",id),memberToken,Map.of("messageId",message))).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/notifications"),memberToken)).andExpect(jsonPath("$.data[?(@.id == 'group-"+id+"')].version").value(org.hamcrest.Matchers.hasItem(second)));
    }

    @Test void recipientSearchPagesDuplicateNamesAndRespectsPermissionsForIdentityLookups() throws Exception {
        String name = "Same " + UUID.randomUUID();
        List<UserEntity> peers = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            UserEntity peer = user("picker" + UUID.randomUUID().toString().replace("-", ""), member.getRole());
            peer.setDisplayName(name); peers.add(users.save(peer));
        }
        assertThat(peers.stream().map(UserEntity::getIdentityCode).distinct().count()).isEqualTo(26);
        JsonNode first = data(mvc.perform(auth(get("/api/v1/messages/directory").param("q", name), adminToken)).andExpect(status().isOk()).andReturn());
        JsonNode last = data(mvc.perform(auth(get("/api/v1/messages/directory").param("q", name).param("page", "1"), adminToken)).andExpect(status().isOk()).andReturn());
        assertThat(first.size()).isEqualTo(25); assertThat(last.size()).isEqualTo(1);
        Set<Long> ids = new HashSet<>(); first.forEach(row -> ids.add(row.path("id").asLong()));
        assertThat(ids).doesNotContain(last.get(0).path("id").asLong());
        UserEntity target = peers.get(25);
        mvc.perform(auth(get("/api/v1/messages/directory").param("q", target.getIdentityCode().toLowerCase(Locale.ROOT)), adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(target.getId()));
        mvc.perform(auth(get("/api/v1/messages/directory").param("userId", target.getId().toString()), adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].identityCode").value(target.getIdentityCode()));
        for (String key : List.of("q", "userId")) {
            mvc.perform(auth(get("/api/v1/messages/directory").param(key, key.equals("q") ? target.getIdentityCode() : target.getId().toString()), memberToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        }
        mvc.perform(auth(get("/api/v1/messages/directory").param("q", name + "%"), adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(auth(get("/api/v1/messages/directory").param("page", "-1"), adminToken)).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/v1/messages/directory").param("q", "a".repeat(81)), adminToken)).andExpect(status().isBadRequest());
    }

    @Test void groupReadCursorKeepsNewMessagesUnreadWhenWallClockDoesNotAdvance() throws Exception {
        long id=group(ownerToken);
        membership(id,ownerToken,member,"MEMBER",Set.of()).andExpect(status().isOk());
        String first=data(postGroup(id,ownerToken,"clock baseline",null,false).andExpect(status().isOk()).andReturn()).path("id").asText();
        // A stored timestamp ahead of the wall clock deterministically covers both
        // coarse clock ticks and clock correction; no timing sleeps or random UUID assumptions.
        var baseline=java.time.Instant.now().plusSeconds(30).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        groupDatabase.update("UPDATE internal_message SET created_at=? WHERE id=?",java.sql.Timestamp.from(baseline),first);
        mvc.perform(json(post("/api/v1/workspaces/{id}/read",id),memberToken,Map.of("messageId",first))).andExpect(status().isOk()).andExpect(jsonPath("$.data.unread").value(0));
        JsonNode second=data(postGroup(id,ownerToken,"clock second",null,false).andExpect(status().isOk()).andReturn());
        JsonNode third=data(postGroup(id,ownerToken,"clock third",null,false).andExpect(status().isOk()).andReturn());
        assertThat(java.time.Instant.parse(second.path("createdAt").asText())).isEqualTo(baseline.plusNanos(1000));
        assertThat(java.time.Instant.parse(third.path("createdAt").asText())).isEqualTo(baseline.plusNanos(2000));
        mvc.perform(auth(get("/api/v1/workspaces/{id}/features",id),memberToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(2)).andExpect(jsonPath("$.data.latestMessageId").value(third.path("id").asText()));
        mvc.perform(json(post("/api/v1/workspaces/{id}/read",id),memberToken,Map.of("messageId",second.path("id").asText())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unread").value(1));
        mvc.perform(json(post("/api/v1/workspaces/{id}/read",id),memberToken,Map.of("messageId",first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unread").value(1));
        mvc.perform(json(post("/api/v1/workspaces/{id}/read",id),memberToken,Map.of("messageId",third.path("id").asText())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unread").value(0));
    }

    @Test void groupNoticesPreferencesReadAndSearchStayWithinMembershipAndRevision() throws Exception {
        long id=group(ownerToken),other=group(ownerToken);
        membership(id,ownerToken,member,"MEMBER",Set.of()).andExpect(status().isOk());
        String first=data(postGroup(id,ownerToken,"needle first",null,false).andExpect(status().isOk()).andReturn()).path("id").asText();
        String last=data(postGroup(id,ownerToken,"needle last",null,false).andExpect(status().isOk()).andReturn()).path("id").asText();
        String foreign=data(postGroup(other,ownerToken,"foreign secret",null,false).andExpect(status().isOk()).andReturn()).path("id").asText();
        mvc.perform(auth(get("/api/v1/workspaces/{id}/features",id),memberToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.unread").value(2));
        mvc.perform(json(put("/api/v1/workspaces/{id}/preferences",id),memberToken,Map.of("pinned",true,"muted",true))).andExpect(status().isOk()).andExpect(jsonPath("$.data.pinned").value(true));
        mvc.perform(auth(get("/api/v1/workspaces/{id}/features",id),ownerToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.pinned").value(false));
        mvc.perform(json(post("/api/v1/workspaces/{id}/read",id),memberToken,Map.of("messageId",last))).andExpect(status().isOk()).andExpect(jsonPath("$.data.unread").value(0));
        mvc.perform(json(post("/api/v1/workspaces/{id}/read",id),memberToken,Map.of("messageId",first))).andExpect(status().isOk()).andExpect(jsonPath("$.data.unread").value(0));
        mvc.perform(json(post("/api/v1/workspaces/{id}/read",id),memberToken,Map.of("messageId",foreign))).andExpect(status().isNotFound());
        mvc.perform(json(put("/api/v1/workspaces/{id}/announcement",id),memberToken,Map.of("text","forbidden","revision",0))).andExpect(status().isForbidden());
        mvc.perform(json(put("/api/v1/workspaces/{id}/announcement",id),ownerToken,Map.of("text","Weekly reading","revision",0))).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(1));
        mvc.perform(json(put("/api/v1/workspaces/{id}/announcement",id),ownerToken,Map.of("text","stale","revision",0))).andExpect(status().isConflict());
        mvc.perform(json(put("/api/v1/workspaces/{id}/pin",id),ownerToken,Map.of("messageId",foreign,"revision",1))).andExpect(status().isNotFound());
        mvc.perform(json(put("/api/v1/workspaces/{id}/pin",id),ownerToken,Map.of("messageId",last,"revision",1))).andExpect(status().isOk()).andExpect(jsonPath("$.data.pinnedMessage.id").value(last));
        mvc.perform(auth(get("/api/v1/workspaces/{id}/search",id).param("q","needle"),memberToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(auth(get("/api/v1/workspaces/{id}/search",id).param("q","needle%"),memberToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(auth(get("/api/v1/workspaces/{id}/people",id).param("q",outsider.getIdentityCode()),ownerToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].identityCode").value(outsider.getIdentityCode())).andExpect(jsonPath("$.data.items[0].email").doesNotExist());
        mvc.perform(auth(get("/api/v1/workspaces/{id}/people",id),memberToken)).andExpect(status().isForbidden());
        membership(id,ownerToken,member,"VIEWER",Set.of()).andExpect(status().isOk());
        mvc.perform(json(put("/api/v1/workspaces/{id}/pin",id),memberToken,Map.of("messageId",first,"revision",2))).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/workspaces/{id}/features",id),outsiderToken)).andExpect(status().isForbidden());
        membership(id,ownerToken,member,"MEMBER",Set.of()).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/v1/workspaces/{id}/members/{user}",id,member.getId()),ownerToken)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/workspaces/{id}/features",id),memberToken)).andExpect(status().isForbidden());
    }

    @Test void basicAccountsCanContactAdminsAndReplyButCannotContactUnrelatedUsers() throws Exception {
        mvc.perform(get("/api/v1/messages/directory")).andExpect(status().isUnauthorized());
        assertThat(contacts(memberToken)).contains(admin.getId()).doesNotContain(owner.getId(), outsider.getId(), member.getId());
        String messageId = data(direct(memberToken, List.of(admin.getId()), null).andExpect(status().isOk()).andReturn()).path("id").asText();
        direct(adminToken, List.of(member.getId()), messageId).andExpect(status().isOk());
        long before = messages.count();
        direct(memberToken, List.of(admin.getId(), outsider.getId()), null).andExpect(status().isForbidden());
        direct(memberToken, List.of(member.getId()), null).andExpect(status().isForbidden());
        direct(memberToken, List.of(99999999L), null).andExpect(status().isForbidden());
        assertThat(messages.count()).isEqualTo(before);
        mvc.perform(auth(get("/api/v1/messages/{id}", messageId), outsiderToken)).andExpect(status().isForbidden());
        admin.setStatus(UserStatus.DISABLED); users.save(admin);
        direct(memberToken, List.of(admin.getId()), null).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/messages/inbox"), adminToken)).andExpect(status().isUnauthorized());
    }

    @Test void membersCanDiscussShareReplyAndMessageEachOtherWithoutPlatformAdminPrivileges() throws Exception {
        long id = group(ownerToken);
        membership(id, ownerToken, member, "MEMBER", Set.of()).andExpect(status().isOk());
        assertThat(contacts(memberToken)).contains(owner.getId(), admin.getId()).doesNotContain(outsider.getId());
        direct(memberToken, List.of(owner.getId()), null).andExpect(status().isOk());
        JsonNode posted = data(postGroup(id, memberToken, "A question", null, true).andExpect(status().isOk()).andReturn());
        String messageId = posted.path("id").asText();
        postGroup(id, ownerToken, "A reply", messageId, false).andExpect(status().isOk()).andExpect(jsonPath("$.data.replyToId").value(messageId));
        mvc.perform(auth(get("/api/v1/messages/groups/{id}", id), memberToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(auth(get(posted.path("attachments").get(0).path("downloadUrl").asText()), ownerToken)).andExpect(status().isOk()).andExpect(content().string("Synthetic group material"));
        verify(scanner).scan(any(byte[].class));
        mvc.perform(auth(get("/api/v1/workspaces/{id}/directory", id), memberToken)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/users"), memberToken)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/messages/groups/{id}", id), outsiderToken)).andExpect(status().isForbidden());
        postGroup(id, outsiderToken, "Forbidden", null, false).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/messages/{id}", messageId), outsiderToken)).andExpect(status().isForbidden());
        postGroup(id, adminToken, "Admin support", null, false).andExpect(status().isOk());
    }

    @Test void removedMembersIncludingOriginalSendersLoseAllGroupMessageAndAttachmentRoutes() throws Exception {
        long id = group(ownerToken);
        membership(id, ownerToken, member, "MEMBER", Set.of()).andExpect(status().isOk());
        JsonNode posted = data(postGroup(id, memberToken, "Private material", null, true).andExpect(status().isOk()).andReturn());
        String messageId = posted.path("id").asText(), url = posted.path("attachments").get(0).path("downloadUrl").asText();
        var attachment = attachments.findById(posted.path("attachments").get(0).path("id").asLong()).orElseThrow();
        mvc.perform(auth(get("/api/v1/files/{id}/content", attachment.getFile().getId()), memberToken)).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/v1/workspaces/{id}/members/{userId}", id, member.getId()), ownerToken)).andExpect(status().isOk());
        for (String path : List.of("/api/v1/messages/groups/" + id, "/api/v1/messages/" + messageId, url))
            mvc.perform(auth(get(path), memberToken)).andExpect(status().isForbidden());
        postGroup(id, memberToken, "No longer a member", null, false).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/messages/sent"), memberToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        assertThat(contacts(memberToken)).doesNotContain(owner.getId());
        mvc.perform(auth(get(url), outsiderToken)).andExpect(status().isForbidden());
        mvc.perform(auth(get(url), ownerToken)).andExpect(status().isOk());
    }

    @Test void readOnlyMembersCannotPostOrElevateTheirRoleAndLeavingRevokesReadAccess() throws Exception {
        long id = group(ownerToken);
        membership(id, ownerToken, member, "VIEWER", Set.of()).andExpect(status().isOk());
        postGroup(id, ownerToken, "Read only material", null, false).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/messages/groups/{id}", id), memberToken)).andExpect(status().isOk());
        postGroup(id, memberToken, "Forbidden", null, true).andExpect(status().isForbidden());
        verifyNoInteractions(scanner);
        membership(id, memberToken, member, "ADMIN", Set.of()).andExpect(status().isForbidden());
        membership(id, ownerToken, member, "VIEWER", Set.of("CONTENT_READ", "CONTENT_WRITE")).andExpect(status().isForbidden());
        direct(memberToken, List.of(admin.getId()), null).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/v1/workspaces/{id}/members/me", id), memberToken)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/messages/groups/{id}", id), memberToken)).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/v1/workspaces/{id}/members/me", id), ownerToken)).andExpect(status().isConflict());
    }

    @Test void groupAdminCannotGrantAdminRolesChangeOwnersOrCrossIntoOtherGroups() throws Exception {
        long id = group(ownerToken), other = group(adminToken);
        membership(id, ownerToken, member, "ADMIN", Set.of()).andExpect(status().isOk());
        membership(id, memberToken, outsider, "MEMBER", Set.of()).andExpect(status().isOk());
        membership(id, memberToken, outsider, "ADMIN", Set.of()).andExpect(status().isForbidden());
        membership(id, memberToken, owner, "MEMBER", Set.of()).andExpect(status().isConflict());
        mvc.perform(auth(delete("/api/v1/workspaces/{id}/members/{userId}", id, owner.getId()), memberToken)).andExpect(status().isConflict());
        membership(other, memberToken, member, "ADMIN", Set.of()).andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/v1/users/{id}", member.getId()), memberToken, Map.of("roleId", admin.getRole().getId()))).andExpect(status().isForbidden());
        membership(id, ownerToken, outsider, "ADMIN", Set.of()).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/v1/workspaces/{id}/members/{userId}", id, outsider.getId()), memberToken)).andExpect(status().isForbidden());
        assertThat(users.findById(member.getId()).orElseThrow().getRole().getCode()).isNotEqualTo("ADMIN");
    }

    @Test void repliesCannotReferenceOtherGroupsAndMalwareDoesNotCreateMessages() throws Exception {
        long first = group(ownerToken), second = group(ownerToken);
        String parent = data(postGroup(first, ownerToken, "First group", null, false).andExpect(status().isOk()).andReturn()).path("id").asText();
        postGroup(second, ownerToken, "Forged reply", parent, false).andExpect(status().isBadRequest());
        long before = messages.count();
        when(scanner.scan(any(byte[].class))).thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "MALWARE_DETECTED", "Synthetic scanner rejection"));
        postGroup(first, ownerToken, "Bad attachment", null, true).andExpect(status().isBadRequest());
        assertThat(messages.count()).isEqualTo(before);
        mvc.perform(auth(get("/api/v1/messages/groups/{id}", first).param("page", "-1"), ownerToken)).andExpect(status().isBadRequest());
    }

    @Test void revokingGroupModuleClosesEveryMessageRouteAndSharedGroupContactDiscovery() throws Exception {
        long id = group(ownerToken);
        membership(id, ownerToken, member, "MEMBER", Set.of()).andExpect(status().isOk());
        JsonNode posted = data(postGroup(id, ownerToken, "Private group material", null, true).andExpect(status().isOk()).andReturn());
        String messageId = posted.path("id").asText();
        String attachment = posted.path("attachments").get(0).path("downloadUrl").asText();
        // The token remains valid: authorization must use the current permission, not a login snapshot.
        member.setPermissionOverrides(Map.of("group:use", false)); users.save(member);
        for (String path : List.of("/api/v1/messages/groups/" + id, "/api/v1/messages/" + messageId, attachment))
            mvc.perform(auth(get(path), memberToken)).andExpect(status().isForbidden());
        postGroup(id, memberToken, "Forbidden write", null, false).andExpect(status().isForbidden());
        assertThat(contacts(memberToken)).doesNotContain(owner.getId()).contains(admin.getId());
        direct(memberToken, List.of(owner.getId()), null).andExpect(status().isForbidden());
        direct(memberToken, List.of(admin.getId()), null).andExpect(status().isOk());
        member.setPermissionOverrides(Map.of()); users.save(member);
        mvc.perform(auth(get(attachment), memberToken)).andExpect(status().isOk());
    }

    @Test void privateMailAndAttachmentsAreParticipantOnlyAndNeverExposeRecipientEmail() throws Exception {
        long id = group(ownerToken);
        membership(id, ownerToken, member, "MEMBER", Set.of()).andExpect(status().isOk());
        var request = multipart("/api/v1/messages")
                .file(new MockMultipartFile("files", "private.txt", "text/plain", "Private attachment".getBytes(StandardCharsets.UTF_8)))
                .param("recipientIds", member.getId().toString()).param("subject", "Private mail").param("body", "Participant-only content");
        JsonNode sent = data(mvc.perform(auth(request, ownerToken)).andExpect(status().isOk()).andReturn());
        String path = "/api/v1/messages/" + sent.path("id").asText();
        String file = sent.path("attachments").get(0).path("downloadUrl").asText();
        for (String token : List.of(adminToken, outsiderToken)) {
            mvc.perform(auth(get(path), token)).andExpect(status().isForbidden());
            mvc.perform(auth(get(file), token)).andExpect(status().isForbidden());
        }
        for (String token : List.of(ownerToken, memberToken)) {
            mvc.perform(auth(get(path), token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.recipients[0].email").doesNotExist());
            mvc.perform(auth(get(file), token)).andExpect(status().isOk());
        }
        mvc.perform(auth(get("/api/v1/messages/inbox"), memberToken)).andExpect(jsonPath("$.data[0].recipients[0].email").doesNotExist());
        mvc.perform(auth(get("/api/v1/messages/sent"), ownerToken)).andExpect(jsonPath("$.data[0].recipients[0].email").doesNotExist());
    }

    @Test void groupDiscoveryRespectsHiddenAccountsBlocksAndExpiryOnBothRoutes() throws Exception {
        long id = group(ownerToken);
        for (String suffix : List.of("people", "directory")) {
            String path = "/api/v1/workspaces/" + id + "/" + suffix;
            String visible = mvc.perform(auth(get(path).param("q", outsider.getIdentityCode()), ownerToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(visible).contains(outsider.getUsername());
            outsider.setDiscoverable(false); users.save(outsider);
            String body = mvc.perform(auth(get(path).param("q", outsider.getIdentityCode()), ownerToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(outsider.getUsername());
            outsider.setDiscoverable(true); outsider.setAccessExpiresAt(java.time.Instant.now().minusSeconds(60)); users.save(outsider);
            body = mvc.perform(auth(get(path).param("q", outsider.getIdentityCode()), ownerToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(outsider.getUsername());
            outsider.setAccessExpiresAt(null); users.save(outsider);
            ContactLinkEntity link = new ContactLinkEntity(owner.getId(), outsider.getId(), owner.getId());
            link.block(outsider.getId(), true); link = links.save(link);
            body = mvc.perform(auth(get(path).param("q", outsider.getIdentityCode()), ownerToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(outsider.getUsername());
            links.delete(link);
        }
    }

    @Test void revokedContactPermissionCannotBeBypassedThroughMailAndExpiredRecipientsAreRejected() throws Exception {
        ContactLinkEntity link = new ContactLinkEntity(owner.getId(), member.getId(), owner.getId());
        link.setStatus("ACCEPTED"); links.save(link);
        member.setPermissionOverrides(Map.of("contacts:use", true)); users.save(member);
        direct(memberToken, List.of(owner.getId()), null).andExpect(status().isOk());
        member.setPermissionOverrides(Map.of("contacts:use", false)); users.save(member);
        direct(memberToken, List.of(owner.getId()), null).andExpect(status().isForbidden());
        assertThat(contacts(memberToken)).doesNotContain(owner.getId());
        owner.setAccessExpiresAt(java.time.Instant.now().minusSeconds(1)); users.save(owner);
        direct(adminToken, List.of(owner.getId()), null).andExpect(status().isForbidden());
        assertThat(contacts(adminToken)).doesNotContain(owner.getId());
    }

    @TestConfiguration static class FastPasswords {
        @Bean @Primary PasswordEncoder groupTestEncoder() { return new BCryptPasswordEncoder(4); }
    }

    @Autowired FileAssetRepository pageFiles;
    @Autowired MessageRecipientRepository pageRecipients;
    @Test void pagedMailCountsUnreadAndLoadsOnlyTheSelectedPage()throws Exception {
        for(int n=0;n<61;n++){var message=messages.save(new MessageEntity(admin,"Page "+n,"x".repeat(1000)));pageRecipients.save(new MessageRecipientEntity(message,member));}
        var page=data(mvc.perform(auth(get("/api/v1/messages/inbox/page?page=1"),memberToken)).andExpect(status().isOk()).andReturn());
        assertThat(page.path("results").path("total").asInt()).isEqualTo(61);assertThat(page.path("results").path("items").size()).isEqualTo(30);assertThat(page.path("unread").asInt()).isEqualTo(61);
        String id=page.path("results").path("items").get(0).path("id").asText();
        assertThat(page.path("results").path("items").get(0).path("body").asText().length()).isEqualTo(240);
        var detail=data(mvc.perform(auth(get("/api/v1/messages/"+id),memberToken)).andExpect(status().isOk()).andReturn());assertThat(detail.path("body").asText().length()).isEqualTo(1000);
        assertThat(data(mvc.perform(auth(get("/api/v1/messages/inbox/page"),memberToken)).andExpect(status().isOk()).andReturn()).path("unread").asInt()).isEqualTo(60);
        assertThat(data(mvc.perform(auth(get("/api/v1/messages/inbox/page"),outsiderToken)).andExpect(status().isOk()).andReturn()).path("results").path("total").asInt()).isZero();
    }
    @Test void pagedFilesApplyOwnerAndAttachmentExclusionsBeforeCounting()throws Exception {
        String label="paged-"+UUID.randomUUID();
        for(var person:List.of(member,outsider))pageFiles.save(new FileAssetEntity(label+".png",UUID.randomUUID()+".png","image/png",42,"a".repeat(64),"test/fixture",person,FileSource.UPLOAD,FileScanStatus.CLEAN,"synthetic"));
        pageFiles.save(new FileAssetEntity(label+"-private.png",UUID.randomUUID()+".png","image/png",42,"b".repeat(64),"test/private",member,FileSource.MESSAGE_ATTACHMENT,FileScanStatus.CLEAN,"synthetic"));
        assertThat(data(mvc.perform(auth(get("/api/v1/files/page").param("q",label),memberToken)).andExpect(status().isOk()).andReturn()).path("total").asInt()).isEqualTo(1);
        assertThat(data(mvc.perform(auth(get("/api/v1/files/page").param("q",label),adminToken)).andExpect(status().isOk()).andReturn()).path("total").asInt()).isEqualTo(2);
        mvc.perform(auth(get("/api/v1/files/page?source=MESSAGE_ATTACHMENT"),memberToken)).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/v1/files/page"),ownerToken)).andExpect(status().isForbidden());
    }
}
