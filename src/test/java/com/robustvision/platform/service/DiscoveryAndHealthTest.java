package com.robustvision.platform.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscoveryAndHealthTest {
 @Test void selectsLateRelevantOriginalPassagesWithinBudget(){
  String body="unrelated introduction ".repeat(700)+"\nDropout reduces overfitting in a neural network.\n"+"unrelated ending ".repeat(700);
  var parts=PassageSelection.select(body,"overfitting dropout",2200);
  assertThat(parts.stream().map(PassageSelection.Passage::text).toList()).anyMatch(s->s.contains("Dropout reduces"));
  assertThat(parts.stream().mapToInt(p->p.text().length()).sum()).isLessThanOrEqualTo(2200);
  for(var p:parts)assertThat(p.text()).isEqualTo(body.substring(p.start(),p.end()));
  if(parts.size()>1)assertThat(parts.get(0).end()).isLessThanOrEqualTo(parts.get(1).start());
 }
 @Test void latentSearchFindsRelatedDocumentsWithoutInventingUnknownMatches(){
  var docs=List.of("neural network dropout overfitting regularization","neural network generalization learning dropout","garden flowers water sunlight","garden roses flowers water");
  var result=LocalSemanticRanker.rank("overfitting",docs);
  assertThat(result).isNotEmpty();assertThat(result.get(0).index()).isZero();
  assertThat(result).anyMatch(m->m.index()==1);
  assertThat(result).noneMatch(m->m.index()==2||m.index()==3);
  assertThat(LocalSemanticRanker.rank("zzzyyyxxx",docs)).isEmpty();
  assertThat(LocalSemanticRanker.rank("test",List.of("",""))).isEmpty();
 }
 @Test void chineseAndEnglishTermsCanBeAssociatedThroughConcepts(){
  var result=LocalSemanticRanker.rank("如何避免过拟合",List.of("dropout regularization neural network","旅游与饮食"));
  assertThat(result).isNotEmpty();assertThat(result.get(0).index()).isZero();
 }
 @Test void healthExpiresSamplesAndCountsOnlyServerErrors(){
  var h=new RuntimeHealthService(mock(org.springframework.jdbc.core.JdbcTemplate.class),mock(BackupStatusService.class));
  h.record(1000,100_000_000,200);h.record(1001,300_000_000,503);h.record(1001,50_000_000,403);
  var now=h.sample(1001);assertThat(now[0]).isEqualTo(3);assertThat(now[1]).isEqualTo(1);assertThat(now[2]).isEqualTo(150);assertThat(now[3]).isEqualTo(500);
  assertThat(h.sample(1301)[0]).isZero();
 }
 @Test void pageAndSearchBoundsRejectUnboundedInput(){
  assertThatThrownBy(()->com.robustvision.platform.dto.LibraryPage.request(0,1000)).isInstanceOf(com.robustvision.platform.common.BusinessException.class);
  assertThatThrownBy(()->com.robustvision.platform.dto.LibraryPage.request(-1,50)).isInstanceOf(com.robustvision.platform.common.BusinessException.class);
  assertThatThrownBy(()->com.robustvision.platform.dto.LibraryPage.query("a".repeat(161))).isInstanceOf(com.robustvision.platform.common.BusinessException.class);
 }
}
