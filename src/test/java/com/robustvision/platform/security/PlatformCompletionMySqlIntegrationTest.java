package com.robustvision.platform.security;
import com.robustvision.platform.database.IsolatedMySql;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
@Tag("mysql") @EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches="jdbc:mysql:.*")
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=true","app.bootstrap.enabled=false","app.worker.enabled=false","app.rate-limit.enabled=false"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformCompletionMySqlIntegrationTest extends PlatformCompletionIntegrationTest {
 static IsolatedMySql fixture;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r)throws Exception{if(fixture==null)fixture=new IsolatedMySql();fixture.properties(r);}
 @AfterAll static void cleanup()throws Exception{if(fixture!=null)fixture.close();}
 @org.springframework.beans.factory.annotation.Autowired com.robustvision.platform.repository.NoteRepository notes;
 @org.springframework.beans.factory.annotation.Autowired com.robustvision.platform.service.KnowledgeSearchService search;
 @Test void firstIndexedSearchWorksWithOneDatabaseConnection()throws Exception{
  var pool=db.getDataSource().unwrap(com.zaxxer.hikari.HikariDataSource.class);int max=pool.getMaximumPoolSize(),min=pool.getMinimumIdle();
  try{
   pool.setMinimumIdle(0);pool.setMaximumPoolSize(1);pool.getHikariPoolMXBean().softEvictConnections();
   org.springframework.test.util.ReflectionTestUtils.setField(search,"mysql",null);
   mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/research/search?q=fixture&type=NOTE&sort=indexed"),adminToken)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
   org.assertj.core.api.Assertions.assertThat(org.springframework.test.util.ReflectionTestUtils.getField(search,"mysql")).isEqualTo(true);
  }finally{pool.setMaximumPoolSize(max);pool.setMinimumIdle(min);}
 }
 @Test void indexedSearchUpdatesImmediatelyWithoutCrossingOwnership()throws Exception{
  var own=notes.saveAndFlush(new com.robustvision.platform.domain.NoteEntity(admin,"Quartzindex own","Quartzindex body","fixture",com.robustvision.platform.domain.NoteStatus.DRAFT));
  var foreign=notes.saveAndFlush(new com.robustvision.platform.domain.NoteEntity(owner,"Quartzindex private","Private body","fixture",com.robustvision.platform.domain.NoteStatus.DRAFT));
  String url="/api/v1/research/search?q=Quartzindex&type=NOTE&sort=indexed";
  var response=mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url),adminToken)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
  org.assertj.core.api.Assertions.assertThat(data(response).toString()).contains(own.getId()).doesNotContain(foreign.getId());
  db.update("UPDATE note SET title='Replacementindex',body='Replacementindex' WHERE id=?",own.getId());
  org.assertj.core.api.Assertions.assertThat(data(mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url),adminToken)).andReturn()).toString()).doesNotContain(own.getId());
  db.update("UPDATE note SET deleted_at=UTC_TIMESTAMP() WHERE id=?",own.getId());
  org.assertj.core.api.Assertions.assertThat(data(mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url.replace("Quartzindex","Replacementindex")),adminToken)).andReturn()).toString()).doesNotContain(own.getId());
 }
 @Test void snapshotLockBlocksDestructionButExpires() {
  var guard=new com.robustvision.platform.service.DestructiveOperationGuard(db);
  db.update("UPDATE runtime_operation_lock SET expires_at=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 HOUR) WHERE name='object-snapshot'");
  try{org.assertj.core.api.Assertions.assertThatThrownBy(guard::requireAvailable).isInstanceOf(com.robustvision.platform.common.BusinessException.class);}
  finally{db.update("UPDATE runtime_operation_lock SET expires_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND) WHERE name='object-snapshot'");}
  org.assertj.core.api.Assertions.assertThatCode(guard::requireAvailable).doesNotThrowAnyException();
 }
 @Test void largePrivateCollectionUsesFulltextWithoutExposingOtherOwners()throws Exception{
  var now=java.sql.Timestamp.from(java.time.Instant.now());var rows=new java.util.ArrayList<Object[]>();
  for(int i=0;i<1001;i++)rows.add(new Object[]{java.util.UUID.randomUUID().toString(),"Synthetic catalog "+i,"Bounded fixture",admin.getId(),now,now});
  db.batchUpdate("INSERT INTO note(id,title,body,owner_id,created_at,updated_at,status) VALUES (?,?,?,?,?,?,'DRAFT')",rows);
  try{indexedSearchUpdatesImmediatelyWithoutCrossingOwnership();}
  finally{db.batchUpdate("DELETE FROM note WHERE id=?",rows.stream().map(r->new Object[]{r[0]}).toList());}
 }
}
