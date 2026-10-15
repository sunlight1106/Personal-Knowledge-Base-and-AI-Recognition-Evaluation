package com.robustvision.platform.security;
import com.fasterxml.jackson.databind.*;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import java.io.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:learning_flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","app.bootstrap.enabled=false","app.worker.enabled=false","app.rate-limit.enabled=false","app.workspace-jobs.enabled=false"})
@AutoConfigureMockMvc
class LearningFlowIntegrationTest {
 @org.springframework.boot.test.mock.mockito.MockBean com.robustvision.platform.service.AntivirusService scanner;
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired UserRepository users;@Autowired RoleRepository roles;@Autowired PasswordEncoder passwords;@Autowired org.springframework.jdbc.core.JdbcTemplate db;
 @Autowired com.robustvision.platform.service.VocabularySkillsService skills;
 @Autowired com.robustvision.platform.service.WorkspaceJobService jobs;
 String token,other,name;
 @BeforeEach void setup()throws Exception{
  org.mockito.Mockito.when(scanner.scan(org.mockito.ArgumentMatchers.any())).thenReturn(new com.robustvision.platform.service.AntivirusService.ScanResult(FileScanStatus.CLEAN,"ok"));
  var role=roles.findByCode("RESEARCHER").orElseGet(()->roles.save(new RoleEntity("RESEARCHER","Research","test",Permissions.CATALOG.stream().map(Permissions.Definition::code).collect(java.util.stream.Collectors.toSet()))));
  name="flow"+UUID.randomUUID().toString().substring(0,8);token=login(name,role);other=login(name+"b",role);
 }
 String login(String name,RoleEntity role)throws Exception{users.save(new UserEntity(name,passwords.encode("FlowTests123!"),name,name+"@example.test",role));return json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",name,"password","FlowTests123!")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("data").path("token").asText();}
 ResultActions call(MockHttpServletRequestBuilder r,String auth,Object body)throws Exception{r.header("Authorization","Bearer "+auth);if(body!=null)r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));return mvc.perform(r);}
 JsonNode ok(MockHttpServletRequestBuilder r,Object body)throws Exception{return json.readTree(call(r,token,body).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("data");}
 String note(String title,String body)throws Exception{return ok(post("/api/v1/notes"),Map.of("title",title,"body",body)).path("id").asText();}
 @Test void planRemainsOwnerScopedAndRejectsStaleChanges()throws Exception{
  var plan=ok(get("/api/v1/learning/plan"),null);long rev=plan.path("settings").path("revision").asLong();
  ok(put("/api/v1/learning/plan"),Map.of("minutes",35,"spreadDays",4,"revision",rev));
  assertThat(ok(get("/api/v1/learning/plan"),null).path("settings").path("minutes").asInt()).isEqualTo(35);
  call(get("/api/v1/learning/plan"),other,null).andExpect(status().isOk()).andExpect(jsonPath("$.data.settings.minutes").value(20));
  // A first save must also invalidate another tab's default revision.
  call(put("/api/v1/learning/plan"),token,Map.of("minutes",20,"spreadDays",7,"revision",0)).andExpect(status().isConflict());
  call(put("/api/v1/learning/plan"),token,Map.of("minutes",0,"spreadDays",7,"revision",1)).andExpect(status().isBadRequest());
 }
 @Test void naturalSearchFindsBilingualConceptWithoutLeakingAnotherOwnersNotes()throws Exception{
  String id=note("模型笔记","Use dropout and regularization to control model complexity.");
  assertThat(ok(get("/api/v1/research/search").param("q","之前那个解决过拟合的方法").param("sort","natural"),null).path("items").toString()).contains(id);
  call(get("/api/v1/research/search").param("q","过拟合").param("sort","natural"),other,null).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
  ok(delete("/api/v1/notes/"+id),null);
  assertThat(ok(get("/api/v1/research/search").param("q","过拟合").param("sort","natural"),null).path("items").size()).isZero();
 }
 @Test void selectedTextMustExistInCurrentSourceAndRepeatedSaveIsIdempotent()throws Exception{
  String id=note("Read","A useful sentence for practice.");String key=UUID.randomUUID().toString();var body=Map.of("kind","NOTE","sourceId",id,"quote","A useful sentence","action","CARD","requestId",key);
  ok(post("/api/v1/learning/clips"),body);ok(post("/api/v1/learning/clips"),body);
  assertThat(ok(get("/api/v1/learning/clips"),null).size()).isEqualTo(1);
  assertThat(ok(get("/api/v1/research/records").param("kind","CARD"),null).size()).isEqualTo(1);
  call(post("/api/v1/learning/clips"),other,body).andExpect(status().isNotFound());
  call(post("/api/v1/learning/clips"),token,Map.of("kind","NOTE","sourceId",id,"quote","invented content","action","NOTE","requestId",UUID.randomUUID().toString())).andExpect(status().isConflict());
  var user=users.findByUsername(name).orElseThrow();user.getPermissionOverrides().put("note:read",false);users.saveAndFlush(user);
  call(post("/api/v1/learning/clips"),token,body).andExpect(status().isNotFound());
 }
 byte[] pdf()throws Exception{try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument()){var page=new org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page);try(var c=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)){c.beginText();c.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);c.newLineAtOffset(50,700);c.showText("Document test content");c.endText();}var out=new ByteArrayOutputStream();pdf.save(out);return out.toByteArray();}}
 @Test void renderedMarkdownAndHtmlSelectionsMatchTheirSavedSource()throws Exception{
  for(String format:List.of("HTML","MARKDOWN")){
   String body=format.equals("HTML")?"<p>A <em>useful</em>&nbsp;phrase.</p>":"A **useful** phrase.";
   String id=ok(post("/api/v1/notes"),Map.of("title","Formatted source","body",body,"contentFormat",format)).path("id").asText();
   ok(post("/api/v1/learning/clips"),Map.of("kind","NOTE","sourceId",id,"quote","A useful phrase.","action","CARD","requestId",UUID.randomUUID().toString()));
  }
  assertThat(ok(get("/api/v1/learning/clips"),null).size()).isEqualTo(2);
 }
 @Test void queuedDocumentCanBeCancelledRetriedAndDeletedOnlyByOwner()throws Exception{
  byte[] bytes=pdf();String key=UUID.randomUUID().toString();var upload=multipart("/api/v1/workspace-jobs/documents").file(new MockMultipartFile("file","test.pdf","application/pdf",bytes)).param("requestId",key);
  var job=ok(upload,null);String id=job.path("id").asText();
  assertThat(ok(multipart("/api/v1/workspace-jobs/documents").file(new MockMultipartFile("file","test.pdf","application/pdf",bytes)).param("requestId",key),null).path("id").asText()).isEqualTo(id);
  call(post("/api/v1/workspace-jobs/"+id+"/cancel"),other,null).andExpect(status().isNotFound());
  assertThat(ok(post("/api/v1/workspace-jobs/"+id+"/cancel"),null).path("status").asText()).isEqualTo("CANCELLED");
  assertThat(ok(post("/api/v1/workspace-jobs/"+id+"/retry"),null).path("status").asText()).isEqualTo("QUEUED");
  call(delete("/api/v1/workspace-jobs/"+id),token,null).andExpect(status().isBadRequest());
  ok(post("/api/v1/workspace-jobs/"+id+"/cancel"),null);ok(delete("/api/v1/workspace-jobs/"+id),null);
  call(get("/api/v1/workspace-jobs/"+id),token,null).andExpect(status().isNotFound());
 }
 @Test void correctionHasRevisionProtectionAndOtherUserCannotRenderPage()throws Exception{
  var doc=ok(multipart("/api/v1/research/documents").file(new MockMultipartFile("file","read.pdf","application/pdf",pdf())),null);String id=doc.path("id").asText();var part=ok(get("/api/v1/research/documents/"+id),null).get(0);String pid=part.path("id").asText();
  ok(patch("/api/v1/research/documents/passages/"+pid),Map.of("body","Corrected text","revision",0));
  call(patch("/api/v1/research/documents/passages/"+pid),token,Map.of("body","Stale text","revision",0)).andExpect(status().isConflict());
  call(get("/api/v1/research/documents/"+id+"/pages/1"),other,null).andExpect(status().isNotFound());
  call(get("/api/v1/research/documents/"+id+"/pages/1"),token,null).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
 }
 @Test void staleWorkerCannotCompleteARetriedJobAndRevokedPermissionIsRechecked()throws Exception{
  String id=ok(multipart("/api/v1/workspace-jobs/documents").file(new MockMultipartFile("file","lease.pdf","application/pdf",pdf())).param("requestId",UUID.randomUUID().toString()),null).path("id").asText();
  var lease=java.time.Instant.now().plusSeconds(900).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  db.update("UPDATE workspace_job SET status='RUNNING',lease_until=? WHERE id=?",lease,id);
  var parts=List.of(new com.robustvision.platform.service.DocumentTextExtractor.Part("第 1 页","Extracted text"));
  org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(name,"",List.of()));
  try{
   assertThatThrownBy(()->jobs.complete(id,parts,lease.minusSeconds(1))).isInstanceOf(com.robustvision.platform.common.BusinessException.class);
   assertThat(db.queryForObject("SELECT status FROM workspace_job WHERE id=?",String.class,id)).isEqualTo("RUNNING");
   var user=users.findByUsername(name).orElseThrow();user.getPermissionOverrides().put("note:write",false);users.saveAndFlush(user);
   assertThatThrownBy(()->jobs.complete(id,parts,lease)).isInstanceOf(com.robustvision.platform.common.BusinessException.class);
   user.getPermissionOverrides().remove("note:write");users.saveAndFlush(user);jobs.complete(id,parts,lease);
   assertThat(db.queryForObject("SELECT status FROM workspace_job WHERE id=?",String.class,id)).isEqualTo("SUCCEEDED");
  }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 }
 @Test void mistakesRequireIndependentSuccessOnTwoLaterDays(){
  long owner=users.findByUsername(name).orElseThrow().getId();var word=new VocabularyWordEntity();word.termKey="sample";word.term="sample";word.meaning="示例";
  var q=new VocabularyQuestionEntity();q.ownerId=owner;q.practiceKind="SPELLING";q.expectedText="sample";q.studyDate=java.time.LocalDate.of(2026,10,1);
  skills.record(word,q,false,false,java.time.Instant.now());assertThat(db.queryForObject("SELECT independent_passes FROM practice_followup WHERE owner_id=?",Integer.class,owner)).isZero();
  skills.record(word,q,true,false,java.time.Instant.now());assertThat(db.queryForObject("SELECT independent_passes FROM practice_followup WHERE owner_id=?",Integer.class,owner)).isZero();
  q.studyDate=q.studyDate.plusDays(1);skills.record(word,q,true,true,java.time.Instant.now());skills.record(word,q,true,true,java.time.Instant.now());
  assertThat(db.queryForObject("SELECT independent_passes FROM practice_followup WHERE owner_id=?",Integer.class,owner)).isEqualTo(1);
  q.studyDate=q.studyDate.plusDays(3);skills.record(word,q,true,true,java.time.Instant.now());assertThat(db.queryForObject("SELECT COUNT(*) FROM practice_followup WHERE owner_id=?",Long.class,owner)).isZero();
 }
}
