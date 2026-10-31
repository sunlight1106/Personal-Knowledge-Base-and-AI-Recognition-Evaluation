package com.robustvision.platform.service;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.dto.PersonalAiDtos;
import com.robustvision.platform.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.IntStream;
import jakarta.validation.Validation;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class KnowledgeAnswerServiceTest {
    @Test void selectionRequiresAtLeastOneSourceAndAllowsMoreThanEight(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var validator=factory.getValidator();
            var refs=IntStream.range(0,32).mapToObj(i->new KnowledgeAnswerService.Ref("NOTE","note"+i)).toList();
            assertThat(validator.validate(new KnowledgeAnswerService.Question(AiProvider.QWEN,"Question",refs))).isEmpty();
            assertThat(validator.validate(new KnowledgeAnswerService.Question(AiProvider.QWEN,"Question",List.of()))).isNotEmpty();
            assertThat(validator.validate(new KnowledgeAnswerService.Question(AiProvider.QWEN,"Question",java.util.Arrays.asList((KnowledgeAnswerService.Ref)null)))).isNotEmpty();
        }
    }
    @Test void manyLongSourcesAreAllIncludedWithinThePreviewBudgetAndDuplicatesLoadOnce(){
        var search=mock(KnowledgeSearchService.class);var ai=mock(PersonalAiService.class);var current=mock(CurrentUserService.class);
        var owner=mock(UserEntity.class);when(owner.getId()).thenReturn(1L);when(current.requireCurrent()).thenReturn(owner);
        var refs=new ArrayList<KnowledgeAnswerService.Ref>();
        for(int i=0;i<32;i++){
            String id="note"+i;refs.add(new KnowledgeAnswerService.Ref("NOTE",id));
            when(search.source("NOTE",id)).thenReturn(new KnowledgeSearchService.Source(id,"NOTE","Title "+i,"evidence ".repeat(2000),"/notes/"+id,Instant.EPOCH));
        }
        refs.add(refs.get(0));
        when(ai.preview(any())).thenReturn(new PersonalAiDtos.PreviewView("token",Instant.now().plusSeconds(60),AiProvider.QWEN,"model","endpoint","answer","context","system",100));
        var service=new KnowledgeAnswerService(search,ai,current);
        service.preview(new KnowledgeAnswerService.Question(AiProvider.QWEN,"evidence",refs));
        var request=org.mockito.ArgumentCaptor.forClass(PersonalAiDtos.PreviewRequest.class);verify(ai).preview(request.capture());
        String body=request.getValue().body();assertThat(body).hasSizeLessThanOrEqualTo(24000).contains("原文片段","未包含全文");
        for(int i=0;i<32;i++)assertThat(body).contains("[S"+(i+1)+"] Title "+i+"\n");
        assertThat(body).doesNotContain("[S33]");verify(search,times(1)).source("NOTE","note0");
        service.execute(new PersonalAiDtos.ExecuteRequest("token",true));
        verify(ai).executeKnowledge(any());
        for(var ref:refs.stream().distinct().toList())verify(search,times(2)).source(ref.kind(),ref.id());
    }
    @Test void emptySelectionNeverBuildsAnAiPreview(){
        var search=mock(KnowledgeSearchService.class);var ai=mock(PersonalAiService.class);
        var service=new KnowledgeAnswerService(search,ai,mock(CurrentUserService.class));
        assertThatThrownBy(()->service.preview(new KnowledgeAnswerService.Question(AiProvider.QWEN,"Q",List.of()))).isInstanceOf(BusinessException.class).hasMessageContaining("至少选择一个资料");
        verifyNoInteractions(search,ai);
    }
    @Test void oversizedSourceMetadataFailsClearlyInsteadOfSilentlyDroppingSources(){
        var search=mock(KnowledgeSearchService.class);var ai=mock(PersonalAiService.class);
        when(search.source("NOTE","one")).thenReturn(new KnowledgeSearchService.Source("one","NOTE","title".repeat(5000),"text","/note",Instant.EPOCH));
        var service=new KnowledgeAnswerService(search,ai,mock(CurrentUserService.class));
        assertThatThrownBy(()->service.preview(new KnowledgeAnswerService.Question(AiProvider.QWEN,"Q",List.of(new KnowledgeAnswerService.Ref("NOTE","one"))))).isInstanceOf(BusinessException.class).hasMessageContaining("单次发送容量");
        verifyNoInteractions(ai);
    }
    @Test void changedSourcesCannotBeSentAndForeignUserCannotConsumeApproval(){
        var search=mock(KnowledgeSearchService.class);var ai=mock(PersonalAiService.class);var current=mock(CurrentUserService.class);
        var owner=mock(UserEntity.class);when(owner.getId()).thenReturn(1L);when(current.requireCurrent()).thenReturn(owner);
        var source=new KnowledgeSearchService.Source("one","NOTE","Own note","private evidence","/app/notes/one/edit",Instant.EPOCH);
        when(search.source("NOTE","one")).thenReturn(source);
        when(ai.preview(any())).thenReturn(new PersonalAiDtos.PreviewView("token",Instant.now().plusSeconds(60),AiProvider.QWEN,"model","endpoint","answer","context","system",100));
        var service=new KnowledgeAnswerService(search,ai,current);
        service.preview(new KnowledgeAnswerService.Question(AiProvider.QWEN,"Question?",List.of(new KnowledgeAnswerService.Ref("NOTE","one"))));
        when(owner.getId()).thenReturn(2L);
        assertThatThrownBy(()->service.execute(new PersonalAiDtos.ExecuteRequest("token",true))).isInstanceOf(BusinessException.class);
        when(owner.getId()).thenReturn(1L);when(search.source("NOTE","one")).thenReturn(new KnowledgeSearchService.Source("one","NOTE","Own note","changed","/app/notes/one/edit",Instant.now()));
        assertThatThrownBy(()->service.execute(new PersonalAiDtos.ExecuteRequest("token",true))).isInstanceOf(BusinessException.class);
        verify(ai,never()).executeKnowledge(any());
    }
    @Test void revokedGroupAccessIsCheckedAgainBeforeExecution(){
        var search=mock(KnowledgeSearchService.class);var ai=mock(PersonalAiService.class);var current=mock(CurrentUserService.class);
        var owner=mock(UserEntity.class);when(owner.getId()).thenReturn(1L);when(current.requireCurrent()).thenReturn(owner);
        when(search.source("GROUP","one")).thenReturn(new KnowledgeSearchService.Source("one","GROUP","Group","data","/group",Instant.EPOCH));
        when(ai.preview(any())).thenReturn(new PersonalAiDtos.PreviewView("token",Instant.now().plusSeconds(60),AiProvider.QWEN,"model","endpoint","answer","context","system",100));
        var service=new KnowledgeAnswerService(search,ai,current);service.preview(new KnowledgeAnswerService.Question(AiProvider.QWEN,"Q",List.of(new KnowledgeAnswerService.Ref("GROUP","one"))));
        when(search.source("GROUP","one")).thenThrow(new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND,"SOURCE_UNAVAILABLE","Unavailable"));
        assertThatThrownBy(()->service.execute(new PersonalAiDtos.ExecuteRequest("token",true))).isInstanceOf(BusinessException.class);
        verify(ai,never()).executeKnowledge(any());
    }
    @Test void unicodeMetricsCountCharactersAndNotUtf16Units(){assertThat(BenchmarkService.distance("😀字","😀词")).isEqualTo(1);assertThat(BenchmarkService.distance("abc","abXYc")).isEqualTo(2);}
}
