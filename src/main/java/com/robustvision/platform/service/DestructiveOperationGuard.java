package com.robustvision.platform.service;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.robustvision.platform.common.BusinessException;
import org.springframework.http.HttpStatus;

@Service
public class DestructiveOperationGuard {
 private final JdbcTemplate db;public DestructiveOperationGuard(JdbcTemplate db){this.db=db;}
 @Transactional public boolean paused(){
  var rows=db.queryForList("SELECT expires_at FROM runtime_operation_lock WHERE name='object-snapshot' FOR UPDATE");
  return !rows.isEmpty()&&JdbcTime.instant(rows.get(0).get("expires_at")).isAfter(java.time.Instant.now());
 }
 @Transactional public void requireAvailable(){if(paused())throw new BusinessException(HttpStatus.CONFLICT,"BACKUP_IN_PROGRESS","正在备份，永久删除暂缓，请稍后重试。阅读与编辑不受影响。");}
}
