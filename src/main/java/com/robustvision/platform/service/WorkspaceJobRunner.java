package com.robustvision.platform.service;
import com.robustvision.platform.domain.WorkspaceJobEntity;
import com.robustvision.platform.security.UserAccountDetailsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.time.Instant;
import java.util.concurrent.*;

@Component @ConditionalOnProperty(name="app.workspace-jobs.enabled",havingValue="true",matchIfMissing=true)
public class WorkspaceJobRunner {
 private final JdbcTemplate db;private final WorkspaceJobService jobs;private final DocumentTextExtractor extractor;private final FileService files;private final UserAccountDetailsService accounts;
 private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"document-study-worker");t.setDaemon(true);return t;});private volatile boolean running;
 public WorkspaceJobRunner(JdbcTemplate db,WorkspaceJobService jobs,DocumentTextExtractor extractor,FileService files,UserAccountDetailsService accounts){this.db=db;this.jobs=jobs;this.extractor=extractor;this.files=files;this.accounts=accounts;}
 @Scheduled(fixedDelay=3000,initialDelay=20000) public void tick(){
  if(running)return;
  var ids=db.queryForList("SELECT id FROM workspace_job WHERE status='QUEUED' AND kind IN ('DOCUMENT_IMPORT','DOCUMENT_OCR') ORDER BY created_at,id LIMIT 1",String.class);
  if(ids.isEmpty())return;String id=ids.get(0);Instant lease=Instant.now().plusSeconds(900).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  if(db.update("UPDATE workspace_job SET status='RUNNING',attempts=attempts+1,lease_until=?,updated_at=? WHERE id=? AND status='QUEUED'",lease,Instant.now(),id)!=1)return;
  running=true;executor.submit(()->{try{run(id,lease);}finally{running=false;SecurityContextHolder.clearContext();}});
 }
 private void run(String id,Instant lease){try{
  String username=db.queryForObject("SELECT u.username FROM app_user u JOIN workspace_job j ON j.owner_id=u.id WHERE j.id=?",String.class,id);
  var details=accounts.loadUserByUsername(username);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details,null,details.getAuthorities()));
  var job=jobs.get(id);var f=jobs.file(job);
  var progress=new JobProgress(lease,System::nanoTime,value->db.update("UPDATE workspace_job SET progress=?,updated_at=? WHERE id=? AND status='RUNNING' AND lease_until=?",value,Instant.now(),id,lease));
  var parts=extractor.extract(files.readBytes(f),f.getOriginalName(),job.kind.equals("DOCUMENT_OCR"),progress);jobs.complete(id,parts,lease);
 }catch(Exception e){String message=e instanceof com.robustvision.platform.common.BusinessException?e.getMessage():"处理未完成，请检查文档或重试";message=message.substring(0,Math.min(500,message.length()));
  db.update("UPDATE workspace_job SET status=CASE WHEN status='CANCELLING' THEN 'CANCELLED' ELSE 'FAILED' END,message=?,updated_at=?,lease_until=NULL WHERE id=? AND status IN ('RUNNING','CANCELLING') AND lease_until=?",message,Instant.now(),id,lease);
 }}
 @jakarta.annotation.PreDestroy public void close(){executor.shutdownNow();}
 @Scheduled(fixedDelay=30000,initialDelay=20000) public void expire(){db.update("UPDATE workspace_job SET status=CASE WHEN status='CANCELLING' THEN 'CANCELLED' ELSE 'FAILED' END,message='处理被中断，可安全重试',updated_at=? WHERE status IN ('RUNNING','CANCELLING') AND lease_until<?",Instant.now(),Instant.now());}
 @Scheduled(fixedDelay=3600000,initialDelay=60000) public void prune(){db.update("DELETE FROM workspace_job WHERE kind IN ('IMPORT','EXPORT','SYNC') AND status IN ('SUCCEEDED','FAILED','CANCELLED') AND updated_at<?",Instant.now().minusSeconds(90L*86400));}
}
