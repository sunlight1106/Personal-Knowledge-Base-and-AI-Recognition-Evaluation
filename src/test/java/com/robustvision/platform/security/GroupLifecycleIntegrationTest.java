package com.robustvision.platform.security;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
class GroupLifecycleIntegrationTest extends GroupCommunicationIntegrationTest {
 @Test void blockedInviterDoesNotDisclosePrivateModerationReasonsToInvitees() throws Exception {
  long id=group(ownerToken);
  membership(id,ownerToken,member,"MEMBER",Set.of()).andExpect(status().isOk());
  mvc.perform(json(post("/api/v1/workspaces/{id}/invitations",id),ownerToken,Map.of("targetId",outsider.getId(),"role","MEMBER"))).andExpect(status().isOk());
  String ticket=data(mvc.perform(auth(get("/api/v1/workspaces/invitations"),outsiderToken)).andReturn()).get(0).path("id").asText();
  String message=data(postGroup(id,ownerToken,"Synthetic review evidence",null,false).andExpect(status().isOk()).andReturn()).path("id").asText();
  String report=data(mvc.perform(json(post("/api/v1/moderation/reports"),memberToken,Map.of("type","MESSAGE","sourceId",message,"reason","Review request"))).andExpect(status().isOk()).andReturn()).path("id").asText();
  String reason="Private moderation decision " + UUID.randomUUID();
  mvc.perform(json(post("/api/v1/moderation/reports/{id}/review",report),adminToken,Map.of("decision","BAN","minutes",60,"features",Set.of(),"reason",reason))).andExpect(status().isOk());
  String response=mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/decision",ticket),outsiderToken,Map.of("accept",true)))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("GROUP_OPERATION_INVALID"))
    .andReturn().getResponse().getContentAsString();
  assertThat(response).doesNotContain(reason);
  mvc.perform(auth(get("/api/v1/workspaces/{id}",id),outsiderToken)).andExpect(status().isForbidden());
 }
 @Test void memberUpdateCannotBypassInvitationConsentEvenForPlatformAdmins() throws Exception {
  long id=group(ownerToken);
  for(String token:List.of(ownerToken,adminToken))
   mvc.perform(json(put("/api/v1/workspaces/{id}/members",id),token,Map.of("userId",member.getId(),"role","MEMBER","permissions",Set.of())))
     .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GROUP_INVITATION_REQUIRED"));
  mvc.perform(auth(get("/api/v1/workspaces/{id}",id),memberToken)).andExpect(status().isForbidden());
 }

 @Test void blockingAfterAnInvitePreventsAcceptanceButNotRejection() throws Exception {
  long id=group(ownerToken);
  mvc.perform(json(post("/api/v1/workspaces/{id}/invitations",id),ownerToken,Map.of("targetId",member.getId(),"role","MEMBER"))).andExpect(status().isOk());
  String ticket=data(mvc.perform(auth(get("/api/v1/workspaces/invitations"),memberToken)).andReturn()).get(0).path("id").asText();
  var link=new com.robustvision.platform.domain.ContactLinkEntity(owner.getId(),member.getId(),owner.getId());link.block(member.getId(),true);links.save(link);
  mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/decision",ticket),memberToken,Map.of("accept",true))).andExpect(status().isBadRequest());
  mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/decision",ticket),memberToken,Map.of("accept",false))).andExpect(status().isOk());
 }
 @Test void pendingInviteCannotOutliveInvitersPlatformOrContentPermission() throws Exception {
  long id=group(ownerToken);
  membership(id,ownerToken,member,"ADMIN",Set.of()).andExpect(status().isOk());
  mvc.perform(json(post("/api/v1/workspaces/{id}/invitations",id),memberToken,Map.of("targetId",outsider.getId(),"role","MEMBER"))).andExpect(status().isOk());
  String ticket=data(mvc.perform(auth(get("/api/v1/workspaces/invitations"),outsiderToken)).andReturn()).get(0).path("id").asText();
  member.setPermissionOverrides(Map.of("group:use",false));users.save(member);
  mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/decision",ticket),outsiderToken,Map.of("accept",true))).andExpect(status().isBadRequest());
  mvc.perform(auth(get("/api/v1/workspaces/{id}",id),outsiderToken)).andExpect(status().isForbidden());
  member.setPermissionOverrides(Map.of());users.save(member);
  membership(id,ownerToken,member,"ADMIN",Set.of("MEMBERS_WRITE","SETTINGS_WRITE")).andExpect(status().isOk());
  mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/decision",ticket),outsiderToken,Map.of("accept",true))).andExpect(status().isBadRequest());
  // Declining remains possible even when the original inviter can no longer authorize admission.
  mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/decision",ticket),outsiderToken,Map.of("accept",false))).andExpect(status().isOk());
 }
 @Test void globalMessageDenialStillAppliesToGroupOwners()throws Exception{
  var role=roles.save(new com.robustvision.platform.domain.RoleEntity("NOMSG_"+UUID.randomUUID().toString().substring(0,8),"No messaging","Synthetic",Set.of("workspace:manage","group:use")));var account=user("nomsg"+UUID.randomUUID().toString().substring(0,8),role);String auth=login(account);long id=group(auth);
  mvc.perform(auth(get("/api/v1/workspaces/{id}/reports",id),auth)).andExpect(status().isForbidden());
  mvc.perform(json(post("/api/v1/workspaces/{id}/messages/{message}/report",id,UUID.randomUUID().toString()),auth,Map.of("reason","Synthetic reason"))).andExpect(status().isForbidden());
 }
 @Test void recipientConsentArchiveAndDissolveControlAccess()throws Exception {
  long id=group(ownerToken);
  var invite=Map.of("targetId",member.getId(),"role","MEMBER");
  mvc.perform(json(post("/api/v1/workspaces/{id}/invitations",id),ownerToken,invite)).andExpect(status().isOk());
  mvc.perform(auth(get("/api/v1/workspaces/{id}/features",id),memberToken)).andExpect(status().isForbidden());
  var pending=data(mvc.perform(auth(get("/api/v1/workspaces/invitations"),memberToken)).andExpect(status().isOk()).andReturn());String ticket=pending.get(0).path("id").asText();
  mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/decision",ticket),outsiderToken,Map.of("accept",true))).andExpect(status().isForbidden());
  mvc.perform(json(post("/api/v1/workspaces/invitations/{id}/decision",ticket),memberToken,Map.of("accept",true))).andExpect(status().isOk());
  postGroup(id,memberToken,"Synthetic post",null,false).andExpect(status().isOk());
  mvc.perform(json(put("/api/v1/workspaces/{id}/lifecycle",id),ownerToken,Map.of("archived",true,"acceptRequests",false))).andExpect(status().isOk());
  postGroup(id,memberToken,"Must not post",null,false).andExpect(status().isConflict());
  mvc.perform(auth(get("/api/v1/workspaces/{id}",id),memberToken)).andExpect(status().isOk())
    .andExpect(jsonPath("$.data.archived").value(true)).andExpect(jsonPath("$.data.dissolved").value(false));
  var overview=data(mvc.perform(auth(get("/api/v1/workspaces/overview"),memberToken)).andExpect(status().isOk()).andReturn());
  var summary=java.util.stream.StreamSupport.stream(overview.path("groups").spliterator(),false).filter(g->g.path("id").asLong()==id).findFirst().orElseThrow();
  assertThat(summary.path("archived").asBoolean()).isTrue();
  assertThat(summary.path("dissolved").asBoolean()).isFalse();
  mvc.perform(auth(get("/api/v1/messages/groups/{id}",id),memberToken)).andExpect(status().isOk());
  var name=data(mvc.perform(auth(get("/api/v1/workspaces/{id}",id),ownerToken)).andReturn()).path("name").asText();
  mvc.perform(json(post("/api/v1/workspaces/{id}/dissolve",id),ownerToken,Map.of("name",name,"password",PASSWORD))).andExpect(status().isOk());
  mvc.perform(auth(get("/api/v1/messages/groups/{id}",id),memberToken)).andExpect(status().isForbidden());
  mvc.perform(auth(get("/api/v1/workspaces/{id}",id),ownerToken)).andExpect(status().isOk())
    .andExpect(jsonPath("$.data.archived").value(true)).andExpect(jsonPath("$.data.dissolved").value(true));
  assertThat(data(mvc.perform(auth(get("/api/v1/workspaces"),memberToken)).andExpect(status().isOk()).andReturn()).toString()).doesNotContain("\"id\":"+id+",");
 }
 @Test void recallRemovesAttachmentAccessAndReportsStayWithinGroup()throws Exception {
  long id=group(ownerToken),other=group(ownerToken);membership(other,ownerToken,outsider,"MEMBER",Set.of("CONTENT_READ","CONTENT_WRITE","MEMBERS_READ")).andExpect(status().isOk());membership(id,ownerToken,member,"MEMBER",Set.of("CONTENT_READ","CONTENT_WRITE","MEMBERS_READ")).andExpect(status().isOk());
  var post=data(postGroup(id,ownerToken,"Synthetic evidence",null,true).andExpect(status().isOk()).andReturn());String message=post.path("id").asText(),url=post.path("attachments").get(0).path("downloadUrl").asText();
  mvc.perform(json(post("/api/v1/workspaces/{id}/messages/{message}/report",other,message),outsiderToken,Map.of("reason","Synthetic reason"))).andExpect(status().isBadRequest());
  mvc.perform(json(post("/api/v1/workspaces/{id}/messages/{message}/report",id,message),memberToken,Map.of("reason","Synthetic reason"))).andExpect(status().isOk());
  mvc.perform(auth(get("/api/v1/workspaces/{id}/reports",id),memberToken)).andExpect(status().isForbidden());
  mvc.perform(json(post("/api/v1/workspaces/{id}/messages/{message}/recall",id,message),memberToken,Map.of())).andExpect(status().isForbidden());
  mvc.perform(json(post("/api/v1/workspaces/{id}/messages/{message}/recall",id,message),ownerToken,Map.of())).andExpect(status().isOk());
  mvc.perform(auth(get(url),memberToken)).andExpect(status().isNotFound());
  var history=data(mvc.perform(auth(get("/api/v1/messages/groups/{id}",id),memberToken)).andExpect(status().isOk()).andReturn());assertThat(history.get(0).path("body").asText()).isEqualTo("消息已撤回");assertThat(history.get(0).path("attachments").size()).isZero();
 }
}
