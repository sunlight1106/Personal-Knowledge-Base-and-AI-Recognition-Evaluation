package com.robustvision.platform;

import com.robustvision.platform.database.IsolatedMySql;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches="jdbc:mysql:.*")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=true","app.bootstrap.enabled=false","app.worker.enabled=false","app.rate-limit.enabled=false","app.personal-ai.remote-enabled=false"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class MultiUserLoadMySqlIntegrationTest {
 static IsolatedMySql fixture;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r)throws Exception{fixture=new IsolatedMySql();fixture.properties(r);}
 @AfterAll static void cleanup()throws Exception{if(fixture!=null)fixture.close();}
 @LocalServerPort int port;
 @Autowired UserRepository users;@Autowired RoleRepository roles;@Autowired NoteRepository notes;@Autowired PasswordEncoder encoder;@Autowired ObjectMapper json;
 @Autowired org.springframework.jdbc.core.JdbcTemplate db;
 Map<String,Map<String,Object>> sqlSnapshot(){
  var result=new HashMap<String,Map<String,Object>>();
  for(var row:db.queryForList("SELECT DIGEST digest,DIGEST_TEXT statement_text,COUNT_STAR calls,SUM_TIMER_WAIT/1000000000 elapsed_ms FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME=DATABASE() AND DIGEST IS NOT NULL"))result.put(row.get("digest").toString(),row);
  return result;
 }
 final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
 record Sample(String operation,int code,double milliseconds){}
 HttpResponse<String> call(String method,String path,String token,String body)throws Exception{
  var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(60)).header("Content-Type","application/json");
  if(token!=null)request.header("Authorization","Bearer "+token);
  return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void mixedTrafficAcrossDistinctAccountsPreservesIsolation()throws Exception{
  var role=roles.save(new RoleEntity("LOAD_RESEARCH","Load test","Synthetic",Set.of("note:read","note:write","research:use","group:use","message:read","knowledge:read","vocabulary:use")));
  String password="Fixture9!"+UUID.randomUUID(),hash=encoder.encode(password);var tokens=new ArrayList<String>();var privateNotes=new ArrayList<String>();
  for(int i=0;i<100;i++){
   var user=users.save(new UserEntity("load_"+i,hash,"Load "+i,"load_"+i+"@example.invalid",role));var batch=new ArrayList<NoteEntity>();
   for(int n=0;n<100;n++)batch.add(new NoteEntity(user,"Isolation marker "+i+" note "+n,"Synthetic learning content algorithm 样本检索 "+i,"fixture",NoteStatus.DRAFT));
   notes.saveAllAndFlush(batch);privateNotes.add(batch.get(0).getId());
   var login=call("POST","/auth/login",null,json.writeValueAsString(Map.of("username",user.getUsername(),"password",password)));assertThat(login.statusCode()).isEqualTo(200);tokens.add(json.readTree(login.body()).path("data").path("token").asText());
  }
  var reports=new ArrayList<Map<String,Object>>();
  for(int concurrency:new int[]{10,50,100}){
   var beforeSql=sqlSnapshot();
   var executor=Executors.newFixedThreadPool(concurrency);var gate=new CountDownLatch(1);var results=new ArrayList<Future<List<Sample>>>();long began=System.nanoTime();
   try{
    for(int i=0;i<concurrency;i++){final int index=i;results.add(executor.submit(()->{
     gate.await();var samples=new ArrayList<Sample>();String token=tokens.get(index);
     String[] paths={"/notes/page?page=0&size=24","/research/search?q=algorithm&type=NOTE&sort=indexed","/workspaces/overview","/messages/inbox/page","/knowledge/topics","/vocabulary/dashboard"};
     for(int round=0;round<3;round++){
      for(String path:paths){long start=System.nanoTime();var response=call("GET",path,token,null);samples.add(new Sample(path,response.statusCode(),(System.nanoTime()-start)/1e6));assertThat(response.statusCode()).as(path).isEqualTo(200);}
      long start=System.nanoTime();var saved=call("POST","/notes",token,json.writeValueAsString(Map.of("title","Concurrent fixture","body","Synthetic autosave","status","DRAFT","contentFormat","MARKDOWN")));samples.add(new Sample("saveNote",saved.statusCode(),(System.nanoTime()-start)/1e6));assertThat(saved.statusCode()).isBetween(200,201);
      var denied=call("GET","/notes/"+privateNotes.get((index+1)%100),token,null);assertThat(denied.statusCode()).isIn(403,404);assertThat(denied.body()).doesNotContain("Synthetic learning content");
     }return samples;
    }));}
    gate.countDown();var all=new ArrayList<Sample>();for(var f:results)all.addAll(f.get(180,TimeUnit.SECONDS));
    var times=all.stream().mapToDouble(Sample::milliseconds).sorted().toArray();var perOperation=new TreeMap<String,Object>();
    all.stream().map(Sample::operation).distinct().forEach(op->{var t=all.stream().filter(s->s.operation().equals(op)).mapToDouble(Sample::milliseconds).sorted().toArray();perOperation.put(op,Map.of("count",t.length,"p50Ms",t[t.length/2],"p95Ms",t[(int)Math.ceil(t.length*.95)-1]));});
    var sql=new ArrayList<Map<String,Object>>();
    for(var entry:sqlSnapshot().entrySet()){
     var after=entry.getValue();var before=beforeSql.get(entry.getKey());
     long calls=((Number)after.get("calls")).longValue()-(before==null?0:((Number)before.get("calls")).longValue());
     double elapsed=((Number)after.get("elapsed_ms")).doubleValue()-(before==null?0:((Number)before.get("elapsed_ms")).doubleValue());
     if(calls>0)sql.add(Map.of("statement",after.get("statement_text"),"calls",calls,"totalMs",elapsed,"meanMs",elapsed/calls));
    }
    sql.sort(Comparator.comparingDouble((Map<String,Object> row)->((Number)row.get("totalMs")).doubleValue()).reversed());
    reports.add(Map.of("users",concurrency,"requests",all.size(),"p50Ms",times[times.length/2],"p95Ms",times[(int)Math.ceil(times.length*.95)-1],"seconds",(System.nanoTime()-began)/1e9,"operations",perOperation,"crossAccountDenials",concurrency*3,"sql",sql));
   }finally{executor.shutdownNow();}
  }
  Path out=Path.of("target/multi-user-load.json");Files.createDirectories(out.getParent());json.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),Map.of("method","Loopback real HTTP + isolated MySQL; 100 accounts, 10000 initial notes; no external AI. Rate limiting disabled only for capacity measurement; security remains enabled. No browser rendering or WAN latency.","stages",reports));
 }
}
