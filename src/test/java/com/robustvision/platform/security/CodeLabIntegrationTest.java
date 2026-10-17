package com.robustvision.platform.security;

import com.robustvision.platform.service.CodeLabRunner;
import com.robustvision.platform.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import java.util.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CodeLabIntegrationTest extends GroupCommunicationIntegrationTest {
    @MockBean CodeLabRunner runner;
    @Autowired JdbcTemplate db;
    private Map<String,Object> input(){return Map.of("source","print(1)","language","python","entry","","arguments",List.of());}
    private void grant(long id){db.update("INSERT INTO user_permission_override(user_id,permission_code,allowed) VALUES (?,'code-lab:use',true)",id);}
    private void success()throws Exception{when(runner.run(any())).thenReturn(mapper.readTree("{\"language\":\"python\",\"status\":\"completed\",\"steps\":[],\"output\":\"1\\n\"}"));}
    @Test void privateRunsCannotBeReadOrRemovedByOtherUsersIncludingAdmin()throws Exception{
        grant(owner.getId());grant(member.getId());success();
        String id=data(mvc.perform(json(post("/api/v1/code-lab/runs"),ownerToken,input())).andExpect(status().isOk()).andReturn()).path("id").asText();
        assertThat(data(mvc.perform(auth(get("/api/v1/code-lab/runs"),ownerToken)).andReturn())).hasSize(1);
        for(String token:List.of(memberToken,adminToken)){
            mvc.perform(auth(get("/api/v1/code-lab/runs/{id}",id),token)).andExpect(status().isNotFound());
            mvc.perform(auth(delete("/api/v1/code-lab/runs/{id}",id),token)).andExpect(status().isNotFound());
            mvc.perform(json(patch("/api/v1/code-lab/runs/{id}/archive",id),token,Map.of("archived",true,"title","Not mine","memo",""))).andExpect(status().isNotFound());
            assertThat(data(mvc.perform(auth(get("/api/v1/code-lab/runs"),token)).andReturn())).isEmpty();
        }
        mvc.perform(auth(get("/api/v1/code-lab/runs/{id}",id),ownerToken)).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/v1/code-lab/runs/{id}",id),ownerToken)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/code-lab/runs/{id}",id),ownerToken)).andExpect(status().isNotFound());
    }
    @Test void permissionRevocationTakesEffectWithoutRelogin()throws Exception{
        mvc.perform(json(post("/api/v1/code-lab/runs"),ownerToken,input())).andExpect(status().isForbidden());
        grant(owner.getId());success();mvc.perform(json(post("/api/v1/code-lab/runs"),ownerToken,input())).andExpect(status().isOk());
        db.update("UPDATE user_permission_override SET allowed=false WHERE user_id=? AND permission_code='code-lab:use'",owner.getId());
        mvc.perform(auth(get("/api/v1/code-lab/runs"),ownerToken)).andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/v1/code-lab/runs/{id}/archive",UUID.randomUUID()),ownerToken,Map.of("archived",true,"title","Denied","memo",""))).andExpect(status().isForbidden());
        verify(runner,times(1)).run(any());
    }
    @Test void badRequestsNeverReachExecutionService()throws Exception{
        grant(owner.getId());
        for(var bad:List.of(Map.of("source","x=1","language","ruby","entry","","arguments",List.of()),
                Map.of("source","x".repeat(16001),"language","python","entry","","arguments",List.of()),
                Map.of("source","x=1","language","python","entry","","arguments",Map.of("owner",admin.getId())),
                Map.of("source","中".repeat(6000),"language","python","entry","","arguments",List.of()),
                Map.of("source","x=1","language","python","entry","../../bad","arguments",List.of())))
            mvc.perform(json(post("/api/v1/code-lab/runs"),ownerToken,bad)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/code-lab/runs")).andExpect(status().isUnauthorized());
        verifyNoInteractions(runner);
    }
    @Test void historyBoundedAndUnavailableExecutionDoesNotCreateFakeRun()throws Exception{
        grant(owner.getId());success();
        for(int i=0;i<22;i++)mvc.perform(json(post("/api/v1/code-lab/runs"),ownerToken,input())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM code_run WHERE owner_id=?",Integer.class,owner.getId())).isEqualTo(20);
        when(runner.run(any())).thenThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"CODE_LAB_UNAVAILABLE","Synthetic offline"));
        mvc.perform(json(post("/api/v1/code-lab/runs"),ownerToken,input())).andExpect(status().isServiceUnavailable());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM code_run WHERE owner_id=?",Integer.class,owner.getId())).isEqualTo(20);
    }
    @Test void featurePenaltyBlocksCodeExecution()throws Exception{
        grant(owner.getId());
        String report=UUID.randomUUID().toString();
        db.update("INSERT INTO moderation_report(id,source_type,source_id,reporter_id,target_id,target_name,target_identity,evidence,reason,status,urgent,created_at) VALUES (?,'USER',?,?,?,'Synthetic','Synthetic','Synthetic','Synthetic','PENDING',false,CURRENT_TIMESTAMP)",report,owner.getId().toString(),admin.getId(),owner.getId());
        db.update("INSERT INTO moderation_penalty(id,report_id,target_id,actor_id,kind,features,reason,automatic,created_at,expires_at) VALUES (?,?,?,?,'FEATURE','CODE','Synthetic',false,CURRENT_TIMESTAMP,?)",
                UUID.randomUUID().toString(),report,owner.getId(),admin.getId(),java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(3600)));
        mvc.perform(json(post("/api/v1/code-lab/runs"),ownerToken,input())).andExpect(status().isForbidden());
        verifyNoInteractions(runner);
    }
    @Test void archivesSurviveHistoryTrimmingAndCanBeRenamedAndDeleted()throws Exception{
        grant(owner.getId());success();
        String id=data(mvc.perform(json(post("/api/v1/code-lab/runs"),ownerToken,input())).andReturn()).path("id").asText();
        var body=Map.of("archived",true,"title","  Triangular loop  ","memo","Boundary n=1");
        mvc.perform(json(patch("/api/v1/code-lab/runs/{id}/archive",id),ownerToken,body)).andExpect(status().isOk());
        for(int i=0;i<24;i++)mvc.perform(json(post("/api/v1/code-lab/runs"),ownerToken,input())).andExpect(status().isOk());
        var record=data(mvc.perform(auth(get("/api/v1/code-lab/runs/{id}",id),ownerToken)).andExpect(status().isOk()).andReturn());
        assertThat(record.path("archived").asBoolean()).isTrue();
        assertThat(record.path("title").asText()).isEqualTo("Triangular loop");
        assertThat(record.path("memo").asText()).isEqualTo("Boundary n=1");
        assertThat(record.path("source").asText()).isEqualTo("print(1)");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM code_run WHERE owner_id=?",Integer.class,owner.getId())).isEqualTo(21);
        mvc.perform(json(patch("/api/v1/code-lab/runs/{id}/archive",id),ownerToken,Map.of("archived",true,"title","Renamed","memo","Updated"))).andExpect(status().isOk());
        assertThat(data(mvc.perform(auth(get("/api/v1/code-lab/runs"),ownerToken)).andReturn())).hasSize(21);
        mvc.perform(auth(delete("/api/v1/code-lab/runs/{id}",id),ownerToken)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/code-lab/runs/{id}",id),ownerToken)).andExpect(status().isNotFound());
    }
    @Test void archiveQuotaAndMetadataValidationAreEnforced()throws Exception{
        grant(owner.getId());success();
        String id=data(mvc.perform(json(post("/api/v1/code-lab/runs"),ownerToken,input())).andReturn()).path("id").asText();
        for(String title:List.of(" ","x".repeat(121)))mvc.perform(json(patch("/api/v1/code-lab/runs/{id}/archive",id),ownerToken,Map.of("archived",true,"title",title,"memo",""))).andExpect(status().isBadRequest());
        mvc.perform(json(patch("/api/v1/code-lab/runs/{id}/archive",id),ownerToken,Map.of("archived",true,"title","ok","memo","x".repeat(2001)))).andExpect(status().isBadRequest());
        String existing=UUID.randomUUID().toString();
        for(int i=0;i<50;i++)db.update("INSERT INTO code_run(id,owner_id,language,entry_name,source,arguments_json,result_json,status,created_at,archived,title,memo) SELECT ?,owner_id,language,entry_name,source,arguments_json,result_json,status,created_at,true,'archive','' FROM code_run WHERE id=?",i==0?existing:UUID.randomUUID().toString(),id);
        mvc.perform(json(patch("/api/v1/code-lab/runs/{id}/archive",id),ownerToken,Map.of("archived",true,"title","over limit","memo",""))).andExpect(status().isConflict());
        mvc.perform(json(patch("/api/v1/code-lab/runs/{id}/archive",existing),ownerToken,Map.of("archived",true,"title","rename at capacity","memo",""))).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM code_run WHERE owner_id=? AND archived=true",Integer.class,owner.getId())).isEqualTo(50);
    }
    @Test void noteSnapshotsRequireNotePermissionAndDeduplicateRetries()throws Exception{
        grant(owner.getId());
        String clientId=UUID.randomUUID().toString();
        String body="# Code note\n\n```python\nprint(1)\n```\n\n<math xmlns=\"http://www.w3.org/1998/Math/MathML\" display=\"block\" aria-label=\"n^2\"><msup><mi>n</mi><mn>2</mn></msup></math>";
        var input=Map.of("clientId",clientId,"title","Code snapshot","body",body,"library","计算机学习","contentFormat","MARKDOWN","status","DRAFT");
        mvc.perform(json(post("/api/v1/notes"),ownerToken,input)).andExpect(status().isForbidden());
        db.update("INSERT INTO user_permission_override(user_id,permission_code,allowed) VALUES (?,'note:write',true)",owner.getId());
        for(int i=0;i<2;i++)assertThat(data(mvc.perform(json(post("/api/v1/notes"),ownerToken,input)).andExpect(status().isOk()).andReturn()).path("id").asText()).isEqualTo(clientId);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM note WHERE id=?",Integer.class,clientId)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM note_share WHERE note_id=?",Integer.class,clientId)).isZero();
        mvc.perform(auth(get("/api/v1/notes/{id}",clientId),adminToken)).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/v1/notes/{id}",clientId),ownerToken)).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/notes"),ownerToken,input)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NOTE_CREATE_TRASHED"));
        assertThat(db.queryForObject("SELECT body FROM note WHERE id=?",String.class,clientId)).isEqualTo(body);
    }
}
