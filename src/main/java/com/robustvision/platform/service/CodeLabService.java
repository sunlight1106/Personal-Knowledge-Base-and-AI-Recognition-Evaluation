package com.robustvision.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robustvision.platform.common.BusinessException;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
public class CodeLabService {
    public record Input(@NotBlank @Size(max=16000) String source,
                        @NotNull @Pattern(regexp="auto|c|python|java|javascript") String language,
                        @NotNull @Pattern(regexp="(?:[A-Za-z_]\\w{0,59})?") String entry,
                        @NotNull JsonNode arguments) {}
    private final CurrentUserService current;
    private final CodeLabRunner runner;
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final Semaphore slots=new Semaphore(2);
    private final Set<Long> active=ConcurrentHashMap.newKeySet();
    public CodeLabService(CurrentUserService current,CodeLabRunner runner,JdbcTemplate db,ObjectMapper mapper,PlatformTransactionManager tm){
        this.current=current;this.runner=runner;this.db=db;this.mapper=mapper;this.transaction=new TransactionTemplate(tm);
    }
    public Map<String,Object> execute(Input input){
        long owner=current.requireCurrent().getId();
        if(input.source().getBytes(StandardCharsets.UTF_8).length>16000||input.source().lines().count()>400
                ||!input.arguments().isArray()||input.arguments().size()>16||input.arguments().toString().length()>8000)
            throw new BusinessException(HttpStatus.BAD_REQUEST,"CODE_LAB_INPUT","代码最多 400 行、16 KB；参数应为最多 16 项的 JSON 数组。");
        if(!active.add(owner))throw busy();
        boolean acquired=slots.tryAcquire();
        if(!acquired){active.remove(owner);throw busy();}
        try{
            JsonNode result=runner.run(mapper.valueToTree(input));
            String id=UUID.randomUUID().toString(),language=result.path("language").asText(input.language());
            String status=result.path("status").asText(),encoded=result.toString();
            if(encoded.getBytes(StandardCharsets.UTF_8).length>1_800_000)throw new BusinessException(HttpStatus.BAD_GATEWAY,"CODE_LAB_RESULT","执行结果过大。");
            transaction.executeWithoutResult(tx->{
                // Serialize history trimming with account deletion and concurrent API instances.
                var users=db.queryForList("SELECT status FROM app_user WHERE id=? FOR UPDATE",String.class,owner);
                if(users.isEmpty()||!"ACTIVE".equals(users.get(0)))throw new BusinessException(HttpStatus.FORBIDDEN,"ACCOUNT_INACTIVE","账号当前不可用。");
                db.update("INSERT INTO code_run(id,owner_id,language,entry_name,source,arguments_json,result_json,status,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                        id,owner,language,input.entry(),input.source(),input.arguments().toString(),encoded,status,Timestamp.from(Instant.now()));
                var stale=db.queryForList("SELECT id FROM code_run WHERE owner_id=? ORDER BY created_at DESC,id DESC LIMIT 100 OFFSET 20",String.class,owner);
                for(String old:stale)db.update("DELETE FROM code_run WHERE id=? AND owner_id=?",old,owner);
            });
            return Map.of("id",id,"result",result,"source",input.source(),"arguments",input.arguments(),"entry",input.entry());
        }finally{slots.release();active.remove(owner);}
    }
    public List<Map<String,Object>> list(){
        return db.query("SELECT id,language,entry_name,status,created_at FROM code_run WHERE owner_id=? ORDER BY created_at DESC,id DESC LIMIT 20",
                (r,n)->Map.of("id",r.getString(1),"language",r.getString(2),"entry",r.getString(3),"status",r.getString(4),"createdAt",r.getTimestamp(5).toInstant()),current.requireCurrent().getId());
    }
    public Map<String,Object> get(UUID id){
        var rows=db.query("SELECT source,entry_name,arguments_json,result_json FROM code_run WHERE id=? AND owner_id=?",(r,n)->
                Map.<String,Object>of("id",id.toString(),"source",r.getString(1),"entry",r.getString(2),"arguments",read(r.getString(3)),"result",read(r.getString(4))),id.toString(),current.requireCurrent().getId());
        if(rows.isEmpty())throw missing();return rows.get(0);
    }
    public void delete(UUID id){if(db.update("DELETE FROM code_run WHERE id=? AND owner_id=?",id.toString(),current.requireCurrent().getId())==0)throw missing();}
    private JsonNode read(String value){try{return mapper.readTree(value);}catch(java.io.IOException e){throw new IllegalStateException("Invalid code trace",e);}}
    private BusinessException busy(){return new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"CODE_LAB_BUSY","已有代码正在执行，请稍后再试。");}
    private BusinessException missing(){return new BusinessException(HttpStatus.NOT_FOUND,"CODE_RUN_NOT_FOUND","执行记录不存在或已删除。");}
}
