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
}
