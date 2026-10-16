package com.robustvision.platform.service;

import com.robustvision.platform.common.BusinessException;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.dto.*;
import com.robustvision.platform.repository.FileAssetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class FileLifecycleService {
    @org.springframework.beans.factory.annotation.Autowired private DestructiveOperationGuard deletionGuard;
    private final FileAssetRepository files;private final CurrentUserService current;private final JdbcTemplate db;private final FileService storage;
    public FileLifecycleService(FileAssetRepository files,CurrentUserService current,JdbcTemplate db,FileService storage){this.files=files;this.current=current;this.db=db;this.storage=storage;}
    private FileAssetEntity owned(String id){var f=files.lockById(id).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"FILE_NOT_FOUND","文件不存在"));if(!f.getOwner().getId().equals(current.requireCurrent().getId())||f.getSource()==FileSource.MESSAGE_ATTACHMENT)throw new BusinessException(HttpStatus.FORBIDDEN,"FILE_OWNER_REQUIRED","只能整理自己的素材文件");return f;}
    public record Reference(String kind,long count){}
    private List<Reference> referencesOf(String id){
        var result=new ArrayList<Reference>();
        String[][] queries={{"实验","SELECT COUNT(*) FROM inference_task WHERE input_file_id=? OR output_file_id=?"},{"图片识别","SELECT COUNT(*) FROM personal_recognition_result WHERE file_id=?"},{"消息附件","SELECT COUNT(*) FROM message_attachment WHERE file_id=?"},{"学习资料","SELECT COUNT(*) FROM study_document WHERE file_id=?"},{"笔记","SELECT COUNT(*) FROM note WHERE body LIKE ?"},{"知识卡","SELECT COUNT(*) FROM knowledge_entry WHERE body LIKE ?"}};
        for(var q:queries){Object[] args=q[0].equals("实验")?new Object[]{id,id}:q[0].equals("笔记")||q[0].equals("知识卡")?new Object[]{"%/files/"+id+"%"}:new Object[]{id};long n=db.queryForObject(q[1],Long.class,args);if(n>0)result.add(new Reference(q[0],n));}
        return result;
    }
    @Transactional public List<Reference> references(String id){owned(id);return referencesOf(id);}
    @Transactional(readOnly=true) public Map<String,Object> usage(){long user=current.requireCurrent().getId();return db.queryForMap("SELECT COUNT(*) AS files,COALESCE(SUM(size_bytes),0) AS totalBytes,COALESCE(SUM(CASE WHEN deleted_at IS NOT NULL THEN size_bytes ELSE 0 END),0) AS trashBytes FROM file_asset WHERE owner_id=? AND source<>'MESSAGE_ATTACHMENT'",user);}
    @Transactional(readOnly=true) public LibraryPage<ApiDtos.FileView> trash(int page,int size){var rows=files.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(current.requireCurrent().getId(),LibraryPage.request(page,size));return new LibraryPage<>(rows.stream().map(storage::toView).toList(),page,size,rows.getTotalElements());}
    @Transactional public void change(String id,String action,String confirmName){
        if("PURGE".equals(action))deletionGuard.requireAvailable();
        var f=owned(id);
        if("RESTORE".equals(action)){f.setDeletedAt(null);return;}
        if(!Set.of("TRASH","PURGE").contains(action))throw new BusinessException(HttpStatus.BAD_REQUEST,"FILE_ACTION_INVALID","无效的素材操作");
        if(!referencesOf(id).isEmpty())throw new BusinessException(HttpStatus.CONFLICT,"FILE_IN_USE","文件仍被引用，请先移除引用后再删除");
        if(action.equals("TRASH")){f.setDeletedAt(Instant.now());return;}
        if(f.getDeletedAt()==null||!f.getOriginalName().equals(confirmName))throw new BusinessException(HttpStatus.BAD_REQUEST,"FILE_PURGE_CONFIRM","请先移入回收站，并输入完整文件名确认永久删除");
        // Commit a retryable storage cleanup job with the row deletion. FK constraints are the final reference guard.
        db.update("INSERT INTO account_cleanup(id,owner_id,kind,resource,attempts,retry_at) VALUES (?,?,'OBJECT',?,0,?)",UUID.randomUUID().toString(),current.requireCurrent().getId(),f.getStoragePath(),java.sql.Timestamp.from(Instant.now()));
        files.delete(f);files.flush();
    }
}
