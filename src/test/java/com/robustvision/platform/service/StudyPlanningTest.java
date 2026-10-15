package com.robustvision.platform.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class StudyPlanningTest {
 @Test void planNeverExceedsTimeBudgetEvenWithLargeBacklog(){for(int budget:new int[]{0,10,60,300,1200,10800}){var steps=StudyPlanService.allocate(budget,7,30,10000,10000,10000,true,true);assertThat(steps.stream().mapToInt(StudyPlanService.Step::seconds).sum()).isLessThanOrEqualTo(budget);assertThat(steps).allMatch(s->s.count()>0&&s.seconds()>0);}}
 @Test void restrictedUserReceivesNoVocabularyOrReadingSuggestions(){assertThat(StudyPlanService.allocate(1200,7,30,0,0,0,false,false)).isEmpty();}
 @Test void naturalQueryExpandsConceptsAndBoundsSqlTerms(){assertThat(NaturalQuery.terms("之前那个解决过拟合的方法")).contains("dropout","regularization");assertThat(NaturalQuery.terms("如何记住英语介词搭配")).contains("preposition","collocation");assertThat(NaturalQuery.terms("过拟合 概率 大模型 词向量 数据库 微调")).hasSizeLessThanOrEqualTo(24);assertThat(NaturalQuery.terms("帮我找一下这个")).doesNotContain("帮我","一下");}
}
