package com.robustvision.platform.service;

import com.robustvision.platform.domain.StudyPlanEntity;
import com.robustvision.platform.common.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

@Service
public class StudyPlanService {
 private final EntityManager em; private final CurrentUserService current;
 private final LearningStatisticsService stats; private final PersonalWorkflowService preferences; private final JdbcTemplate db;
 public StudyPlanService(EntityManager em,CurrentUserService current,LearningStatisticsService stats,PersonalWorkflowService preferences,JdbcTemplate db){this.em=em;this.current=current;this.stats=stats;this.preferences=preferences;this.db=db;}
 public record Settings(@Min(5) @Max(180) int minutes,@Min(1) @Max(14) int spreadDays,@Min(0) long revision){}
 public record Step(String name,int count,int seconds,String url){}
 public record Plan(Settings settings,boolean studyDay,int usedSeconds,int remainingSeconds,long backlog,List<Step> steps,String explanation){}
 @Transactional public Settings save(Settings input){
  long owner=current.requireCurrent().getId();db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",Long.class,owner);
  var p=em.find(StudyPlanEntity.class,owner);
  if((p==null?0:p.revision)!=input.revision())throw new BusinessException(HttpStatus.CONFLICT,"PLAN_CONFLICT","计划已在其他页面更改，请刷新后重试");
  if(p==null){p=new StudyPlanEntity();p.ownerId=owner;p.revision=1;em.persist(p);}
  p.minutes=input.minutes();p.spreadDays=input.spreadDays();em.flush();return settings(p);
 }
 @Transactional(readOnly=true) public Plan get(){
  var user=current.requireCurrent();long owner=user.getId();var row=em.find(StudyPlanEntity.class,owner);var settings=row==null?new Settings(20,7,0):settings(row);
  var today=stats.today();var date=today.statistics().days().get(today.statistics().days().size()-1).date();boolean studyDay=preferences.get(owner).studyDays().contains(date.getDayOfWeek().getValue()-1);
  int used=(int)today.statistics().days().stream().filter(d->d.date().equals(date)).mapToLong(d->d.metrics().seconds()).sum();
  long answers=today.statistics().days().stream().mapToLong(d->d.metrics().answers()+d.metrics().cards()).sum();
  long seconds=today.statistics().days().stream().mapToLong(d->d.metrics().seconds()).sum();
  int pace=answers>=20?(int)Math.max(15,Math.min(90,seconds/Math.max(1,answers))):30;
  long weak=current.hasPermission(user,"vocabulary:use")?db.queryForObject("SELECT COUNT(*) FROM practice_followup WHERE owner_id=? AND due_date<=?",Long.class,owner,date):0;
  int remaining=Math.max(0,settings.minutes()*60-used);List<Step> steps=allocate(remaining,settings.spreadDays(),pace,today.dueWords(),today.dueCards(),weak,current.hasPermission(user,"vocabulary:use"),current.hasPermission(user,"note:read"));
  if(!current.hasPermission(user,"research:use"))steps=steps.stream().map(s->s.name().equals("阅读与整理")?new Step(s.name(),s.count(),s.seconds(),"/app/notes"):s).toList();
  String explanation=remaining==0?"已达到今天的时间目标，可以休息，也可以自行继续。":!studyDay?"今天是计划休息日，以下练习可自愿进行。":"优先安排到期复习和隔日薄弱项；积压分摊不会修改原到期日期。耗时是估计，可随时暂停。";
  return new Plan(settings,studyDay,used,remaining,today.dueWords()+today.dueCards(),steps,explanation);
 }
 static List<Step> allocate(int budget,int spread,int pace,long words,long cards,long weak,boolean vocab,boolean notes){
  var out=new ArrayList<Step>();int left=budget;
  long[] counts={words,cards,weak};String[] names={"到期词汇","到期复习卡","隔日薄弱项"};String[] urls={"/app/vocabulary?mode=REVIEW","/app/research?tab=cards","/app/vocabulary?followups=1"};
  for(int i=0;i<counts.length;i++){int each=i==1?Math.max(40,pace):pace;int target=(int)Math.min(100,Math.ceil(counts[i]/(double)(i==2?1:spread)));int count=Math.min(target,left/each);if(count>0){out.add(new Step(names[i],count,count*each,urls[i]));left-=count*each;}}
  if(vocab&&left>=90){int count=Math.min(10,left/120);if(count>0){out.add(new Step("学习新词",count,count*120,"/app/vocabulary?mode=LEARN"));left-=count*120;}}
  if(notes&&left>=60)out.add(new Step("阅读与整理",1,left,"/app/research?tab=documents"));
  return out;
 }
 private Settings settings(StudyPlanEntity p){return new Settings(p.minutes,p.spreadDays,p.revision);}
}
