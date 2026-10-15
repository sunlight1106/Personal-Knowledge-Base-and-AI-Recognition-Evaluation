package com.robustvision.platform.service;
import com.robustvision.platform.common.BusinessException;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.dto.ApiDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;

@Service
public class ReadingCaptureService {
 private final EntityManager em;private final CurrentUserService current;private final KnowledgeSearchService sources;private final VocabularyService words;private final NoteService notes;private final LearningWorkspaceService cards;private final ObjectMapper json;
 public ReadingCaptureService(EntityManager em,CurrentUserService current,KnowledgeSearchService sources,VocabularyService words,NoteService notes,LearningWorkspaceService cards,ObjectMapper json){this.em=em;this.current=current;this.sources=sources;this.words=words;this.notes=notes;this.cards=cards;this.json=json;}
 public record Capture(@NotBlank @Pattern(regexp="NOTE|DOCUMENT") String kind,@NotBlank @Size(max=36) String sourceId,@NotBlank @Size(max=4000) String quote,@Size(max=80) String term,@NotBlank @Pattern(regexp="WORD|CARD|NOTE") String action,@NotBlank @Pattern(regexp="[a-fA-F0-9-]{36}") String requestId){}
 @Transactional public Object capture(Capture input){
  var user=current.requireCurrent();String permission=input.action().equals("WORD")?"vocabulary:use":input.action().equals("NOTE")?"note:write":"research:use";
  if(!current.hasPermission(user,permission))throw new BusinessException(HttpStatus.FORBIDDEN,"CAPTURE_FORBIDDEN","当前账户无权执行该操作");
  var source=sources.source(input.kind(),input.sourceId());String quote=input.quote().trim();
  // Only text present in the currently authorized source can be attributed to it.
  String visible=source.body();
  if(input.kind().equals("NOTE")){var note=em.find(NoteEntity.class,input.sourceId());if(note!=null)visible=org.jsoup.Jsoup.parseBodyFragment("HTML".equals(note.getContentFormat())?NoteContent.safeHtml(source.body()):NoteContent.markdownToHtml(source.body())).body().wholeText();}
  if(!normalize(source.body()).contains(normalize(quote))&&!normalize(visible).contains(normalize(quote)))throw new BusinessException(HttpStatus.CONFLICT,"SOURCE_CHANGED","选区不在已保存的原文中，请先保存或重新选择");
  String id=UUID.nameUUIDFromBytes((user.getId()+":"+input.requestId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
  em.createQuery("select u from UserEntity u where u.id=:id").setParameter("id",user.getId()).setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE).getSingleResult();
  if(em.find(ReadingClipEntity.class,id)!=null)return Map.of("saved",true);
  if(em.createQuery("select count(c) from ReadingClipEntity c where c.ownerId=:owner",Long.class).setParameter("owner",user.getId()).getSingleResult()>=5000)throw new BusinessException(HttpStatus.BAD_REQUEST,"CLIP_LIMIT","最多保存 5000 条阅读摘录，请先整理");
  Object result;
  if(input.action().equals("WORD")){
   if(input.term()==null||input.term().isBlank()||!normalize(quote).toLowerCase(Locale.ROOT).contains(input.term().trim().toLowerCase(Locale.ROOT)))throw new BusinessException(HttpStatus.BAD_REQUEST,"TERM_INVALID","请填写选区中实际出现的单词");
   result=words.collectTerm(input.term());
  }else if(input.action().equals("NOTE"))result=notes.create(new ApiDtos.CreateNoteRequest(source.title().substring(0,Math.min(150,source.title().length())),quote+"\n\n来源：["+source.title().replace("]","")+ "]("+source.url()+")",null,"DRAFT","阅读摘录","MARKDOWN",null));
  else {
   String question="回忆这段内容的主要意思："+source.title();
   result=cards.save("CARD",null,new LearningWorkspaceService.Save(question.substring(0,Math.min(180,question.length())),json.createObjectNode().put("question",question).put("answer",quote).put("subject","阅读摘录").put("sourceKind",source.kind()).put("sourceId",source.id()),0));
  }
  var clip=new ReadingClipEntity();clip.id=id;clip.ownerId=user.getId();clip.sourceKind=source.kind();clip.sourceId=source.id();clip.sourceTitle=source.title().substring(0,Math.min(200,source.title().length()));clip.quoteText=quote;clip.term=input.term();clip.createdAt=Instant.now();em.persist(clip);return result;
 }
 @Transactional(readOnly=true) public List<ReadingClipEntity> list(){var user=current.requireCurrent();var allowed=new ArrayList<String>();if(current.hasPermission(user,"note:read"))allowed.add("NOTE");if(current.hasPermission(user,"research:use"))allowed.add("DOCUMENT");if(allowed.isEmpty())return List.of();return em.createQuery("select c from ReadingClipEntity c where c.ownerId=:owner and c.sourceKind in :allowed order by c.createdAt desc",ReadingClipEntity.class).setParameter("owner",user.getId()).setParameter("allowed",allowed).setMaxResults(100).getResultList();}
 @Transactional public void delete(String id){var c=em.find(ReadingClipEntity.class,id);if(c==null||!c.ownerId.equals(current.requireCurrent().getId()))throw new BusinessException(HttpStatus.NOT_FOUND,"CLIP_NOT_FOUND","摘录不存在");em.remove(c);}
 static String normalize(String value){return value.replace('\u00a0',' ').replaceAll("\\s+"," ").trim();}
}
