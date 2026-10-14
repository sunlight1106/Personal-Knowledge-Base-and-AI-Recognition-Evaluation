package com.robustvision.platform.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

@Service
public class OfflineStudyService {
 private final VocabularyService vocabulary;private final VocabularyLessons lessons;private final EntityManager em;private final ObjectMapper mapper;private final LearningStatisticsService stats;
 public OfflineStudyService(VocabularyService vocabulary,VocabularyLessons lessons,EntityManager em,ObjectMapper mapper,LearningStatisticsService stats){this.vocabulary=vocabulary;this.lessons=lessons;this.em=em;this.mapper=mapper;this.stats=stats;}
 public record Item(String id,String term,String ipa,String meaning,String example,String translation,Instant baseVersion){}
 public record Pack(String id,long ownerId,Instant expiresAt,List<Item> items){}
 public record Attempt(@NotBlank @Size(max=36) String wordId,@NotNull @Size(max=160) String text,boolean revealed,@NotNull Instant answeredAt){}
 public record Sync(@NotEmpty @Size(max=100) List<@Valid Attempt> attempts){}
 public record Result(int recorded,int merged,int conflicts){}
 @Transactional(isolation=Isolation.READ_COMMITTED)
 public Pack prepare(String bookId){
  long owner=vocabulary.lockedOwner();vocabulary.book(bookId,owner);var profile=vocabulary.profile(owner);if(profile.zoneId==null)profile.zoneId="Asia/Shanghai";
  em.createQuery("delete from OfflineStudyPackEntity p where p.ownerId=:o and p.expiresAt<:now").setParameter("o",owner).setParameter("now",vocabulary.now()).executeUpdate();
  if(em.createQuery("select count(p) from OfflineStudyPackEntity p where p.ownerId=:o",Long.class).setParameter("o",owner).getSingleResult()>=10)throw bad("最多保留 10 份未过期的离线包，请继续使用已有内容");
  var progress=vocabulary.progress(owner);var today=vocabulary.today(profile);
  var items=vocabulary.words(bookId).stream().filter(w->{var p=progress.get(w.termKey);return p==null||!p.skipped;})
    .sorted(Comparator.comparingInt(w->{var p=progress.get(w.termKey);return p!=null&&p.dueDate!=null&&!p.dueDate.isAfter(today)?0:p==null||p.learningCorrect<4?1:2;})).limit(100)
    .map(w->{var p=progress.get(w.termKey);var l=lessons.forWord(w);return new Item(w.id,w.term,l.ipa(),w.meaning,l.example(),l.exampleTranslation(),p==null?null:p.updatedAt);}).toList();
  var row=new OfflineStudyPackEntity();row.id=UUID.randomUUID().toString();row.ownerId=owner;row.createdAt=vocabulary.now();row.expiresAt=row.createdAt.plus(Duration.ofDays(7));row.payload=write(items);em.persist(row);return new Pack(row.id,owner,row.expiresAt,items);
 }
 @Transactional(isolation=Isolation.READ_COMMITTED)
 public Result sync(String id,Sync input){
  long owner=vocabulary.lockedOwner();var pack=em.find(OfflineStudyPackEntity.class,id);
  if(pack==null||pack.ownerId!=owner)throw new BusinessException(HttpStatus.NOT_FOUND,"OFFLINE_NOT_FOUND","离线包不存在或不属于当前账户");
  if(!pack.expiresAt.isAfter(vocabulary.now()))throw bad("离线包已超过 7 天。练习可保留在本地，请重新准备新的同步包");
  List<Item> items;try{items=mapper.readValue(pack.payload,new TypeReference<List<Item>>(){});}catch(Exception e){throw new IllegalStateException(e);}
  var profile=vocabulary.profile(owner);var zone=ZoneId.of(profile.zoneId==null?"Asia/Shanghai":profile.zoneId);int recorded=0,merged=0,conflicts=0;
  Set<String> seen=new HashSet<>();var progress=vocabulary.progress(owner);
  for(var attempt:input.attempts()){
   if(!seen.add(attempt.wordId()))throw bad("同一批次不能重复提交同一个词");
   var item=items.stream().filter(w->w.id().equals(attempt.wordId())).findFirst().orElseThrow(()->bad("单词不属于这个离线包"));
   if(attempt.answeredAt().isBefore(pack.createdAt)||attempt.answeredAt().isAfter(vocabulary.now().plusSeconds(300)))throw bad("练习时间不正确，请校准设备时间");
   var word=em.find(VocabularyWordEntity.class,item.id());if(word==null){conflicts++;continue;}vocabulary.book(word.bookId,owner);
   var date=attempt.answeredAt().atZone(zone).toLocalDate();String event="offline:"+owner+":"+UUID.nameUUIDFromBytes((word.termKey+":"+date).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   if(em.find(LearningEventEntity.class,event)!=null)continue;
   boolean correct=VocabularyTermKey.of(attempt.text()).equals(word.termKey);
   var previous=progress.get(word.termKey);boolean unchanged=previous==null?item.baseVersion()==null:Objects.equals(previous.updatedAt,item.baseVersion());
   stats.record(owner,event,"WORD",word.termKey,correct,attempt.answeredAt(),date);recorded++;
   if(!unchanged){conflicts++;continue;}
   var p=vocabulary.wordProgress(owner,word.id);
   // Offline evidence is practice, never a claim of independent recall or a substitute for online assessment.
   if(!correct){p.wrongCount++;p.mistake=true;}else if(attempt.revealed())p.promptedCorrect++;else p.immediateCorrect++;
   p.lastAttemptAt=attempt.answeredAt();p.updatedAt=vocabulary.now();merged++;
  }
  return new Result(recorded,merged,conflicts);
 }
 private String write(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 private BusinessException bad(String message){return new BusinessException(HttpStatus.BAD_REQUEST,"OFFLINE_INVALID",message);}
}
