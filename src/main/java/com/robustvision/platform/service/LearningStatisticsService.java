package com.robustvision.platform.service;

import com.robustvision.platform.domain.*;
import com.robustvision.platform.repository.UserRepository;
import com.robustvision.platform.common.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service
public class LearningStatisticsService {
    private final CurrentUserService current;
    private final UserRepository users;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public LearningStatisticsService(CurrentUserService current,UserRepository users,EntityManager em,JdbcTemplate jdbc,ObjectProvider<Clock> clocks) {
        this.current=current;this.users=users;this.em=em;this.jdbc=jdbc;clock=clocks.getIfAvailable(Clock::systemUTC);
    }
    public record Pulse(@Min(0) @Max(30) int seconds,@jakarta.validation.constraints.NotNull @Pattern(regexp="VOCABULARY|NOTES|CARDS|KNOWLEDGE") String context) {}
    public record Metrics(long answers,long correct,long words,long cards,long notes,long seconds) {}
    public record Day(LocalDate date,Metrics metrics) {}
    public record Summary(long userId,String zoneId,Metrics total,List<Day> days,long activeDays) {}
    public record Resume(String id,String title,String url) {}
    public record Today(Summary statistics,List<Resume> notes,long dueCards,long dueWords,long unfinishedWords) {}

    ZoneId zone(long owner) {
        VocabularyProfileEntity p=em.find(VocabularyProfileEntity.class,owner);
        return ZoneId.of(p!=null&&p.zoneId!=null?p.zoneId:"Asia/Shanghai");
    }
    /** Called in the source mutation transaction: a rollback also removes its event. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(long owner,String key,String kind,String source,Boolean correct,Instant at,LocalDate date) {
        if(em.find(LearningEventEntity.class,key)!=null)return;
        LearningEventEntity e=new LearningEventEntity();e.id=key;e.ownerId=owner;e.kind=kind;e.sourceId=source;
        e.correct=correct;e.createdAt=at;e.studyDate=date==null?at.atZone(zone(owner)).toLocalDate():date;em.persist(e);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void pulse(Pulse pulse) {
        var user=current.requireCurrent();String permission=switch(pulse.context()){case "VOCABULARY"->"vocabulary:use";case "NOTES"->"note:read";case "CARDS"->"research:use";default->"knowledge:read";};
        if(!current.hasPermission(user,permission))throw new BusinessException(HttpStatus.FORBIDDEN,"LEARNING_FORBIDDEN","当前账户无权使用该学习功能");
        long owner=user.getId();users.findLockedById(owner).orElseThrow();
        Instant now=clock.instant();LearningClockEntity ticker=em.find(LearningClockEntity.class,owner);
        if(ticker==null){ticker=new LearningClockEntity();ticker.ownerId=owner;em.persist(ticker);}
        long elapsed=ticker.lastPing==null?0:Duration.between(ticker.lastPing,now).getSeconds();
        if(elapsed>0&&elapsed<10)return; // Duplicate requests/tabs cannot accumulate time.
        long credit=elapsed>=10&&elapsed<=35?Math.min(elapsed,pulse.seconds()):0;
        ticker.lastPing=now;
        if(credit==0)return;
        ZoneId zone=zone(owner);LocalDate day=now.atZone(zone).toLocalDate();
        // Split a pulse crossing midnight into its two natural days.
        long todaySeconds=Math.min(credit,Duration.between(day.atStartOfDay(zone).toInstant(),now).getSeconds());
        addSeconds(owner,day,todaySeconds);if(todaySeconds<credit)addSeconds(owner,day.minusDays(1),credit-todaySeconds);
    }
    private void addSeconds(long owner,LocalDate day,long seconds){
        if(seconds<=0)return;String id=owner+":"+day;var row=em.find(LearningDayEntity.class,id);
        if(row==null){row=new LearningDayEntity();row.id=id;row.ownerId=owner;row.studyDate=day;em.persist(row);}
        row.activeSeconds=Math.min(86400,row.activeSeconds+seconds);
    }
    @Transactional(readOnly=true)
    public Summary mine(int days){return summary(current.requireCurrent().getId(),days);}
    @Transactional(readOnly=true)
    public Summary summary(long owner,int days){
        if(days!=7&&days!=30&&days!=90&&days!=365)throw new BusinessException(HttpStatus.BAD_REQUEST,"LEARNING_RANGE","请选择 7、30、90 或 365 天");
        if(!users.existsById(owner))throw new BusinessException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","用户不存在");
        ZoneId zone=zone(owner);LocalDate today=clock.instant().atZone(zone).toLocalDate(),start=today.minusDays(days-1L);
        Map<LocalDate,Metrics> daily=new HashMap<>();
        jdbc.query("SELECT study_date,COUNT(CASE WHEN kind='WORD' THEN 1 END) answers,COUNT(CASE WHEN kind='WORD' AND correct=TRUE THEN 1 END) correct,COUNT(DISTINCT CASE WHEN kind='WORD' THEN source_id END) words,COUNT(CASE WHEN kind='CARD' THEN 1 END) cards,COUNT(DISTINCT CASE WHEN kind='NOTE' THEN source_id END) notes FROM learning_event WHERE owner_id=? AND study_date>=? AND study_date<=? GROUP BY study_date",rs->{daily.put(rs.getDate("study_date").toLocalDate(),new Metrics(rs.getLong("answers"),rs.getLong("correct"),rs.getLong("words"),rs.getLong("cards"),rs.getLong("notes"),0));},owner,start,today);
        jdbc.query("SELECT study_date,active_seconds FROM learning_day WHERE owner_id=? AND study_date>=? AND study_date<=?",rs->{LocalDate day=rs.getDate(1).toLocalDate();Metrics m=daily.getOrDefault(day,zero());daily.put(day,new Metrics(m.answers,m.correct,m.words,m.cards,m.notes,rs.getLong(2)));},owner,start,today);
        Metrics counts=jdbc.queryForObject("SELECT COUNT(CASE WHEN kind='WORD' THEN 1 END),COUNT(CASE WHEN kind='WORD' AND correct=TRUE THEN 1 END),COUNT(DISTINCT CASE WHEN kind='WORD' THEN source_id END),COUNT(CASE WHEN kind='CARD' THEN 1 END),COUNT(DISTINCT CASE WHEN kind='NOTE' THEN source_id END) FROM learning_event WHERE owner_id=?",(rs,n)->new Metrics(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),0),owner);
        long seconds=jdbc.queryForObject("SELECT COALESCE(SUM(active_seconds),0) FROM learning_day WHERE owner_id=?",Long.class,owner);
        long activeDays=jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT study_date FROM learning_event WHERE owner_id=? UNION SELECT study_date FROM learning_day WHERE owner_id=? AND active_seconds>0) d",Long.class,owner,owner);
        List<Day> series=new ArrayList<>();for(LocalDate day=start;!day.isAfter(today);day=day.plusDays(1))series.add(new Day(day,daily.getOrDefault(day,zero())));
        return new Summary(owner,zone.getId(),new Metrics(counts.answers,counts.correct,counts.words,counts.cards,counts.notes,seconds),series,activeDays);
    }
    @Transactional(readOnly=true)
    public Today today(){
        var user=current.requireCurrent();long owner=user.getId();Summary s=summary(owner,7);
        List<Resume> notes=current.hasPermission(user,"note:read")?jdbc.query("SELECT id,title FROM note WHERE owner_id=? AND deleted_at IS NULL ORDER BY updated_at DESC LIMIT 3",(rs,n)->new Resume(rs.getString(1),rs.getString(2),"/app/notes/"+rs.getString(1)+"/edit"),owner):List.of();
        long cards=0;if(current.hasPermission(user,"research:use")){
            // JSON stays portable across H2/MySQL. The catalog is bounded to 2,000 cards.
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            for(String payload:jdbc.queryForList("SELECT payload FROM learning_record WHERE owner_id=? AND kind='CARD'",String.class,owner))try{if(!Instant.parse(mapper.readTree(payload).path("due").asText()).isAfter(clock.instant()))cards++;}catch(Exception ignored){}
        }
        long words=current.hasPermission(user,"vocabulary:use")?jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_progress WHERE owner_id=? AND learning_correct>=4 AND skipped=FALSE AND due_date<=?",Long.class,owner,clock.instant().atZone(zone(owner)).toLocalDate()):0;
        long unfinished=current.hasPermission(user,"vocabulary:use")?jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_progress WHERE owner_id=? AND learning_correct>0 AND learning_correct<4 AND skipped=FALSE",Long.class,owner):0;
        return new Today(s,notes,cards,words,unfinished);
    }
    static Metrics zero(){return new Metrics(0,0,0,0,0,0);}
}
