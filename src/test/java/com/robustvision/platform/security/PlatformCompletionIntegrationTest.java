package com.robustvision.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

class PlatformCompletionIntegrationTest extends GroupCommunicationIntegrationTest {
 @Autowired JdbcTemplate db;
 private String send(String token,long target)throws Exception{return data(mvc.perform(auth(multipart("/api/v1/messages").param("recipientIds",Long.toString(target)).param("subject","Synthetic lifecycle").param("body","Private fixture"),token)).andExpect(status().isOk()).andReturn()).path("id").asText();}
 private boolean contains(String token,String folder,String id)throws Exception{return data(mvc.perform(auth(get("/api/v1/messages/"+folder),token)).andExpect(status().isOk()).andReturn()).path("results").path("items").toString().contains(id);}
 @Test void mailboxActionsOnlyChangeCurrentParticipantsFolder()throws Exception{
  String id=send(ownerToken,admin.getId());
  mvc.perform(json(post("/api/v1/messages/organize"),adminToken,Map.of("ids",List.of(id),"action","ARCHIVE"))).andExpect(status().isOk());
  assertThat(contains(adminToken,"inbox/page",id)).isFalse();assertThat(contains(adminToken,"folders/ARCHIVE",id)).isTrue();assertThat(contains(ownerToken,"sent/page",id)).isTrue();
  mvc.perform(json(post("/api/v1/messages/organize"),outsiderToken,Map.of("ids",List.of(id),"action","TRASH"))).andExpect(status().isForbidden());
  for(String action:List.of("TRASH","TRASH","ACTIVE"))mvc.perform(json(post("/api/v1/messages/organize"),adminToken,Map.of("ids",List.of(id),"action",action))).andExpect(status().isOk());
  assertThat(contains(adminToken,"inbox/page",id)).isTrue();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM message_recipient WHERE message_id=? AND recipient_id=? AND read_at IS NULL",Integer.class,id,admin.getId())).isZero();
 }
 @Test void mailboxRejectsMixedOwnershipAtomicallyAndInvalidBatch()throws Exception{
  String own=send(ownerToken,admin.getId()),foreign=send(memberToken,admin.getId());
  mvc.perform(json(post("/api/v1/messages/organize"),ownerToken,Map.of("ids",List.of(own,foreign),"action","TRASH"))).andExpect(status().isForbidden());
  assertThat(contains(ownerToken,"sent/page",own)).isTrue();
  mvc.perform(json(post("/api/v1/messages/organize"),ownerToken,Map.of("ids",Collections.nCopies(101,own),"action","TRASH"))).andExpect(status().isBadRequest());
  mvc.perform(json(post("/api/v1/messages/organize"),ownerToken,Map.of("ids",List.of(own),"action","DELETE_ALL"))).andExpect(status().isBadRequest());
 }
 @Test void invitationHistoryIsPrivateAndCancellationCannotGrantMembership()throws Exception{
  long group=group(ownerToken);
  mvc.perform(json(post("/api/v1/workspaces/{id}/invitations",group),ownerToken,Map.of("targetId",member.getId(),"role","MEMBER"))).andExpect(status().isOk());
  String id=data(mvc.perform(auth(get("/api/v1/workspaces/invitations"),memberToken)).andReturn()).get(0).path("id").asText();
  assertThat(data(mvc.perform(auth(get("/api/v1/workspaces/invitations/history"),outsiderToken)).andReturn()).path("items")).isEmpty();
  mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/cancel",id),outsiderToken,Map.of())).andExpect(status().isForbidden());
  mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/cancel",id),ownerToken,Map.of())).andExpect(status().isOk());
  mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/cancel",id),ownerToken,Map.of())).andExpect(status().isOk());
  mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/decision",id),memberToken,Map.of("accept",true))).andExpect(status().isBadRequest());
  assertThat(data(mvc.perform(auth(get("/api/v1/workspaces/invitations/history"),ownerToken)).andReturn()).path("items").get(0).path("status").asText()).isEqualTo("CANCELLED");
 }
 @Test void expiredInvitationsCannotBeCancelledOrAccepted()throws Exception{
  long group=group(ownerToken);mvc.perform(json(post("/api/v1/workspaces/{id}/invitations",group),ownerToken,Map.of("targetId",member.getId(),"role","VIEWER"))).andExpect(status().isOk());
  String id=data(mvc.perform(auth(get("/api/v1/workspaces/invitations"),memberToken)).andReturn()).get(0).path("id").asText();
  db.update("UPDATE group_invitation SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),id);
  mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/cancel",id),ownerToken,Map.of())).andExpect(status().isBadRequest());
  assertThat(data(mvc.perform(auth(get("/api/v1/workspaces/invitations/history"),ownerToken)).andReturn()).path("items").get(0).path("status").asText()).isEqualTo("EXPIRED");
 }
 @Test void recycleRequiresOwnershipReferenceRemovalAndTypedConfirmation()throws Exception{
  byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6zGQAAAAASUVORK5CYII=");
  String id=data(mvc.perform(auth(multipart("/api/v1/files").file(new MockMultipartFile("file","fixture.png","image/png",png)),adminToken)).andExpect(status().isOk()).andReturn()).path("id").asText();
  mvc.perform(auth(get("/api/v1/files/{id}/references",id),memberToken)).andExpect(status().isForbidden());
  String doc=UUID.randomUUID().toString();db.update("INSERT INTO study_document(id,owner_id,file_id,title,sha256,format,created_at,ocr) VALUES (?,?,?,?,?,?,?,false)",doc,admin.getId(),id,"fixture",UUID.randomUUID().toString(),"TXT",java.sql.Timestamp.from(java.time.Instant.now()));
  mvc.perform(json(post("/api/v1/files/{id}/lifecycle",id),adminToken,Map.of("action","TRASH"))).andExpect(status().isConflict());
  db.update("DELETE FROM study_document WHERE id=?",doc);
  mvc.perform(json(post("/api/v1/files/{id}/lifecycle",id),adminToken,Map.of("action","TRASH"))).andExpect(status().isOk());
  mvc.perform(auth(get("/api/v1/files/{id}/content",id),adminToken)).andExpect(status().isNotFound());
  mvc.perform(json(post("/api/v1/files/{id}/lifecycle",id),adminToken,Map.of("action","RESTORE"))).andExpect(status().isOk());
  mvc.perform(json(post("/api/v1/files/{id}/lifecycle",id),adminToken,Map.of("action","TRASH"))).andExpect(status().isOk());
  mvc.perform(json(post("/api/v1/files/{id}/lifecycle",id),adminToken,Map.of("action","PURGE","confirmName","wrong"))).andExpect(status().isBadRequest());
  mvc.perform(json(post("/api/v1/files/{id}/lifecycle",id),adminToken,Map.of("action","PURGE","confirmName","fixture.png"))).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM file_asset WHERE id=?",Integer.class,id)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM account_cleanup WHERE owner_id=? AND kind='OBJECT'",Integer.class,admin.getId())).isPositive();
 }
 @Test void operationalDetailsAndMetricsRejectOrdinaryAccounts()throws Exception{
  mvc.perform(auth(get("/api/v1/operations/startup"),memberToken)).andExpect(status().isForbidden());
  mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
  mvc.perform(auth(get("/api/v1/operations/startup"),adminToken)).andExpect(status().isOk());
 }
}
