package com.robustvision.platform.service;
import com.robustvision.platform.common.BusinessException;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.*;

@Service
public class WorkspaceJobService {
 private final EntityManager em;private final CurrentUserService current;private final FileService files;private final FileAssetRepository assets;private final JdbcTemplate db;
 public WorkspaceJobService(EntityManager em,CurrentUserService current,FileService files,FileAssetRepository assets,JdbcTemplate db){this.em=em;this.current=current;this.files=files;this.assets=assets;this.db=db;}
 private void writePermission(){var u=current.requireCurrent();if(!current.hasPermission(u,"note:write")||!current.hasPermission(u,"research:use"))throw new BusinessException(HttpStatus.FORBIDDEN,"DOCUMENT_FORBIDDEN","文档权限已变更，请联系管理员");}
 @Transactional public WorkspaceJobEntity submit(MultipartFile upload,boolean ocr,String key){
  writePermission();long owner=current.requireCurrent().getId();if(key==null||!key.matches("[a-fA-F0-9-]{36}"))throw bad("请求编号无效");
  db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",Long.class,owner);
  var old=em.createQuery("select j from WorkspaceJobEntity j where j.ownerId=:owner and j.requestKey=:key",WorkspaceJobEntity.class).setParameter("owner",owner).setParameter("key",key).getResultList();if(!old.isEmpty())return old.get(0);
  if(db.queryForObject("SELECT COUNT(*) FROM workspace_job WHERE owner_id=? AND status IN ('QUEUED','RUNNING','CANCELLING')",Long.class,owner)>=3)throw bad("已有 3 个任务正在处理，请完成后再上传");
  if(db.queryForObject("SELECT COUNT(*) FROM workspace_job WHERE owner_id=? AND kind IN ('DOCUMENT_IMPORT','DOCUMENT_OCR')",Long.class,owner)>=300)throw bad("请先在任务中心清理已结束的文档任务");
  String name=Objects.toString(upload.getOriginalFilename(),"");if(upload.isEmpty()||upload.getSize()>10*1024*1024||!name.toLowerCase(Locale.ROOT).matches(".*\\.(pdf|docx)$"))throw bad("请选择不超过 10 MB 的 PDF 或 Word 文档");
  var file=files.storeMessageAttachment(upload,current.requireCurrent());if(file.getScanStatus()!=FileScanStatus.CLEAN)throw bad("文件尚未通过安全扫描");
  var j=new WorkspaceJobEntity();j.id=UUID.randomUUID().toString();j.ownerId=owner;j.kind=ocr?"DOCUMENT_OCR":"DOCUMENT_IMPORT";j.title=name.substring(0,Math.min(200,name.length()));j.resourceId=file.getId();j.status="QUEUED";j.requestKey=key;j.createdAt=j.updatedAt=Instant.now();em.persist(j);return j;
 }
 @Transactional(readOnly=true) public List<WorkspaceJobEntity> list(int page){if(page<0||page>1000)throw bad("页码无效");return em.createQuery("select j from WorkspaceJobEntity j where j.ownerId=:owner order by j.createdAt desc,j.id",WorkspaceJobEntity.class).setParameter("owner",current.requireCurrent().getId()).setFirstResult(page*30).setMaxResults(30).getResultList();}
 @Transactional(readOnly=true) public WorkspaceJobEntity get(String id){var j=em.find(WorkspaceJobEntity.class,id);if(j==null||!j.ownerId.equals(current.requireCurrent().getId()))throw new BusinessException(HttpStatus.NOT_FOUND,"JOB_NOT_FOUND","任务不存在或无权访问");return j;}
 @Transactional public WorkspaceJobEntity action(String id,String action){
  db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",Long.class,current.requireCurrent().getId());
  var j=get(id);em.refresh(j,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  if(action.equals("cancel")){if(j.status.equals("QUEUED"))j.status="CANCELLED";else if(j.status.equals("RUNNING"))j.status="CANCELLING";}
  else if(action.equals("retry")){writePermission();if(!Set.of("FAILED","CANCELLED").contains(j.status)||!j.kind.startsWith("DOCUMENT_"))throw bad("此任务不能重试");if(j.attempts>=3)throw bad("已尝试三次，请检查文件后重新上传");if(db.queryForObject("SELECT COUNT(*) FROM workspace_job WHERE owner_id=? AND status IN ('QUEUED','RUNNING','CANCELLING')",Long.class,j.ownerId)>=3)throw bad("请等待已有任务完成");j.status="QUEUED";j.message=null;j.progress=0;j.leaseUntil=null;}
  else throw bad("不支持的操作");j.updatedAt=Instant.now();em.flush();return j;
 }
 public FileAssetEntity file(WorkspaceJobEntity j){writePermission();return assets.findById(j.resourceId).filter(f->f.getOwner().getId().equals(current.requireCurrent().getId())&&f.getScanStatus()==FileScanStatus.CLEAN).orElseThrow(()->bad("文件已删除或未通过扫描"));}
 @Transactional public void complete(String jobId,List<DocumentTextExtractor.Part> parts,Instant expectedLease){
  writePermission();long owner=current.requireCurrent().getId();db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",Long.class,owner);
  var job=get(jobId);em.refresh(job,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);if(!job.status.equals("RUNNING")||job.leaseUntil==null||!job.leaseUntil.equals(expectedLease)||job.leaseUntil.isBefore(Instant.now()))throw bad("任务已取消或处理超时");var f=file(job);
  var existing=em.createQuery("select d from StudyDocumentEntity d where d.ownerId=:owner and d.sha256=:hash",StudyDocumentEntity.class).setParameter("owner",owner).setParameter("hash",f.getSha256()).getResultList();
  if(!existing.isEmpty()){job.resultId=existing.get(0).id;job.message="文档已经存在，已定位到原文档";}
  else{
   if(db.queryForObject("SELECT COUNT(*) FROM study_document WHERE owner_id=?",Long.class,owner)>=200)throw bad("最多保存 200 份文档，请先整理");
   var d=new StudyDocumentEntity();d.ownerId=owner;d.fileId=f.getId();d.title=f.getOriginalName();d.format=d.title.toLowerCase(Locale.ROOT).endsWith(".pdf")?"PDF":"DOCX";d.sha256=f.getSha256();d.ocr=job.kind.equals("DOCUMENT_OCR");em.persist(d);int n=0;
   for(var part:parts){var p=new DocumentPassageEntity();p.ownerId=owner;p.documentId=d.id;p.locator=part.locator();p.positionNumber=++n;p.body=part.text();em.persist(p);}job.resultId=d.id;
  }
  job.status="SUCCEEDED";job.progress=100;job.updatedAt=Instant.now();job.leaseUntil=null;
  if(!existing.isEmpty()){cleanupUpload(job);job.resourceId=null;}
 }
 @Transactional public void delete(String id){var j=get(id);em.refresh(j,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);if(Set.of("RUNNING","QUEUED","CANCELLING").contains(j.status))throw bad("请先等待任务结束或取消");cleanupUpload(j);em.remove(j);}
 private void cleanupUpload(WorkspaceJobEntity j){if(j.resourceId==null)return;var f=assets.findById(j.resourceId);if(f.isEmpty()||!f.get().getOwner().getId().equals(j.ownerId))return;
  if(db.queryForObject("SELECT COUNT(*) FROM study_document WHERE file_id=?",Long.class,j.resourceId)>0||db.queryForObject("SELECT COUNT(*) FROM message_attachment WHERE file_id=?",Long.class,j.resourceId)>0||db.queryForObject("SELECT COUNT(*) FROM inference_task WHERE input_file_id=? OR output_file_id=?",Long.class,j.resourceId,j.resourceId)>0)return;
  db.update("INSERT INTO account_cleanup(id,owner_id,kind,resource,attempts,retry_at) VALUES(?,?, 'OBJECT',?,0,?)",UUID.randomUUID().toString(),j.ownerId,f.get().getStoragePath(),Instant.now());assets.delete(f.get());
 }
 private BusinessException bad(String text){return new BusinessException(HttpStatus.BAD_REQUEST,"JOB_INVALID",text);}
}
