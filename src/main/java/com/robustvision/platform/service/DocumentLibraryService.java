package com.robustvision.platform.service;

import com.robustvision.platform.common.BusinessException;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
public class DocumentLibraryService {
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate db;
    private final EntityManager em;private final CurrentUserService current;private final UserRepository users;
    private final FileService files;private final FileAssetRepository assets;private final DocumentTextExtractor extractor;
    public DocumentLibraryService(EntityManager em,CurrentUserService current,UserRepository users,FileService files,FileAssetRepository assets,DocumentTextExtractor extractor){this.em=em;this.current=current;this.users=users;this.files=files;this.assets=assets;this.extractor=extractor;}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public StudyDocumentEntity upload(MultipartFile upload){
        var owner=current.requireCurrent();String name=Objects.toString(upload.getOriginalFilename(),"").toLowerCase(Locale.ROOT);
        if(upload.isEmpty()||upload.getSize()>10*1024*1024||!(name.endsWith(".pdf")||name.endsWith(".docx")))throw new BusinessException(HttpStatus.BAD_REQUEST,"DOCUMENT_INVALID","请选择不超过 10 MB 的 PDF 或 .docx 文档");
        users.findLockedById(owner.getId()).orElseThrow();
        if(em.createQuery("select count(d) from StudyDocumentEntity d where d.ownerId=:owner",Long.class).setParameter("owner",owner.getId()).getSingleResult()>=200)throw new BusinessException(HttpStatus.BAD_REQUEST,"DOCUMENT_LIMIT","最多保存 200 份文档，请先整理已有资料");
        var file=files.storeMessageAttachment(upload,owner);
        if(file.getScanStatus()!=FileScanStatus.CLEAN)throw new BusinessException(HttpStatus.CONFLICT,"DOCUMENT_SCAN_PENDING","文档尚未通过安全扫描，请稍后重试");
        var matches=em.createQuery("select d from StudyDocumentEntity d where d.ownerId=:owner and d.sha256=:hash",StudyDocumentEntity.class).setParameter("owner",owner.getId()).setParameter("hash",file.getSha256()).getResultList();
        // A duplicate upload is rolled back so it cannot leak orphaned storage objects.
        if(!matches.isEmpty())throw new BusinessException(HttpStatus.CONFLICT,"DOCUMENT_DUPLICATE","这份文档已经导入，请在文档列表中打开");
        var parts=extractor.extract(files.readBytes(file),name);
        StudyDocumentEntity doc=new StudyDocumentEntity();doc.ownerId=owner.getId();doc.fileId=file.getId();doc.title=file.getOriginalName();doc.sha256=file.getSha256();doc.format=name.endsWith(".pdf")?"PDF":"DOCX";em.persist(doc);
        int number=0;for(var part:parts){DocumentPassageEntity p=new DocumentPassageEntity();p.ownerId=doc.ownerId;p.documentId=doc.id;p.positionNumber=++number;p.locator=part.locator();p.body=part.text();em.persist(p);}return doc;
    }
    @Transactional(readOnly=true) public List<StudyDocumentEntity> list(){return em.createQuery("select d from StudyDocumentEntity d where d.ownerId=:owner order by d.createdAt desc",StudyDocumentEntity.class).setParameter("owner",current.requireCurrent().getId()).setMaxResults(200).getResultList();}
    @Transactional(readOnly=true) public DocumentPassageEntity passage(String id){var p=em.find(DocumentPassageEntity.class,id);if(p==null||!p.ownerId.equals(current.requireCurrent().getId()))throw new BusinessException(HttpStatus.NOT_FOUND,"DOCUMENT_NOT_FOUND","文档不存在或无权访问");owned(p.documentId);return p;}
    StudyDocumentEntity owned(String id){var doc=em.find(StudyDocumentEntity.class,id);if(doc==null||!doc.ownerId.equals(current.requireCurrent().getId()))throw new BusinessException(HttpStatus.NOT_FOUND,"DOCUMENT_NOT_FOUND","文档不存在或无权访问");return doc;}
    @Transactional(readOnly=true) public List<DocumentPassageEntity> parts(String id){owned(id);return em.createQuery("select p from DocumentPassageEntity p where p.documentId=:id order by p.positionNumber",DocumentPassageEntity.class).setParameter("id",id).getResultList();}
    @Transactional(readOnly=true) public FileAssetEntity original(String id){var doc=owned(id);return assets.findById(doc.fileId).filter(f->f.getOwner().getId().equals(doc.ownerId)&&f.getScanStatus()==FileScanStatus.CLEAN).orElseThrow();}
    public record Correction(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=4000) String body,@jakarta.validation.constraints.Min(0) long revision){}
    @Transactional public DocumentPassageEntity correct(String id,Correction input){var p=passage(id);if(p.revision!=input.revision())throw new BusinessException(HttpStatus.CONFLICT,"PASSAGE_CONFLICT","此段文字已被修改，请重新加载后校对");p.body=input.body();em.flush();return p;}
    @Transactional(readOnly=true) public byte[] pageImage(String id,int page){var doc=owned(id);if(!doc.format.equals("PDF")||page<1||page>300)throw new BusinessException(HttpStatus.BAD_REQUEST,"PAGE_INVALID","页码无效");try(var pdf=org.apache.pdfbox.Loader.loadPDF(files.readBytes(original(id)))){if(page>pdf.getNumberOfPages())throw new IllegalArgumentException();var box=pdf.getPage(page-1).getCropBox();float dpi=(float)Math.min(120,72*Math.sqrt(4_000_000/Math.max(1,(double)box.getWidth()*box.getHeight())));if(dpi<15)throw new IllegalArgumentException();var img=new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(page-1,dpi);var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(img,"png",out);img.flush();return out.toByteArray();}catch(Exception e){throw new BusinessException(HttpStatus.BAD_REQUEST,"PAGE_INVALID","无法预览此页，请下载原文核对");}}
    @Transactional public void delete(String id){var doc=owned(id);em.createQuery("delete from DocumentPassageEntity p where p.documentId=:id").setParameter("id",id).executeUpdate();em.remove(doc);em.flush();
        if(db.queryForObject("SELECT COUNT(*) FROM message_attachment WHERE file_id=?",Long.class,doc.fileId)==0){var file=assets.findById(doc.fileId).orElseThrow();db.update("INSERT INTO account_cleanup(id,owner_id,kind,resource,attempts,retry_at) VALUES(?,?, 'OBJECT',?,0,?)",UUID.randomUUID().toString(),doc.ownerId,file.getStoragePath(),java.sql.Timestamp.from(java.time.Instant.now()));assets.delete(file);}
    }
}
