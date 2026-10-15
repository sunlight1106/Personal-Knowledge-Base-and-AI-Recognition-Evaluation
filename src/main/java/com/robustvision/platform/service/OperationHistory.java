package com.robustvision.platform.service;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.UUID;
@Service
public class OperationHistory {
 private final JdbcTemplate db;
 public OperationHistory(JdbcTemplate db){this.db=db;}
 public String start(long owner,String kind,String title){String id=UUID.randomUUID().toString();db.update("INSERT INTO workspace_job(id,owner_id,kind,title,status,progress,attempts,created_at,updated_at,lease_until,request_key) VALUES(?,?,?,?,'RUNNING',0,1,?,?,?,?)",id,owner,kind,title,Instant.now(),Instant.now(),Instant.now().plusSeconds(600),id);return id;}
 public void finish(String id,boolean ok){db.update("UPDATE workspace_job SET status=?,progress=?,updated_at=?,lease_until=NULL,message=? WHERE id=?",ok?"SUCCEEDED":"FAILED",ok?100:0,Instant.now(),ok?"服务器处理完成":"操作未完成，请回原页面查看详情后重试",id);}
}
