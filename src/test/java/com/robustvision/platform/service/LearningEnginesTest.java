package com.robustvision.platform.service;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robustvision.platform.domain.AiProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
class LearningEnginesTest {
 @Test void cancellationIsOwnerScopedAndWorksBeforeStreamStarts(){
  var streams=new PersonalAiStreams();streams.cancel(1,"early");
  assertThatThrownBy(()->streams.begin(1,"early")).isInstanceOf(com.robustvision.platform.common.BusinessException.class);
  streams.cancel(2,"other");var running=streams.begin(1,"other");running.check();streams.cancel(2,"other");running.check();
  streams.cancel(1,"other");assertThatThrownBy(running::check).isInstanceOf(com.robustvision.platform.common.BusinessException.class);streams.end("other",running);
 }
 @Test void importedSchedulingStateRejectsNonFiniteOrOutOfRangeValues()throws Exception{
  var state=new ObjectMapper().readTree(new ReviewScheduler().review(null,2,.9,Instant.now()).state());
  ((com.fasterxml.jackson.databind.node.ObjectNode)state).put("stability",-1);
  assertThatThrownBy(()->ReviewScheduler.parse(state.toString())).isInstanceOf(IllegalArgumentException.class);
 }
 private PersonalAiTransport.Completion stream(String input,AiProvider provider,String secret,StringBuilder output) throws Exception{return PersonalAiStreamParser.read(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),provider,new ObjectMapper(),secret,output::append,()->{});}
 @Test void realStreamCollectsTextUsageAndRejectsIncompleteOrSplitSecrets() throws Exception{
  StringBuilder output=new StringBuilder();var r=stream("data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\ndata: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":2}}\n\ndata: [DONE]\n",AiProvider.OPENAI,"secret",output);assertThat(output.toString()).isEqualTo("hello");assertThat(r.inputTokens()).isEqualTo(4);
  assertThatThrownBy(()->stream("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n",AiProvider.OPENAI,"secret",new StringBuilder())).isInstanceOf(PersonalAiUpstreamFailure.class);
  StringBuilder leak=new StringBuilder();assertThatThrownBy(()->stream("data: {\"choices\":[{\"delta\":{\"content\":\"prefix tok\"}}]}\ndata: {\"choices\":[{\"delta\":{\"content\":\"en-value\"}}]}\n",AiProvider.OPENAI,"token-value",leak)).isInstanceOf(PersonalAiUpstreamFailure.class);assertThat(leak.toString()).doesNotContain("tok");
 }
 @Test void streamEnforcesLineBounds(){assertThatThrownBy(()->stream("data: "+"x".repeat(65540),AiProvider.OPENAI,"secret",new StringBuilder())).isInstanceOf(com.robustvision.platform.common.BusinessException.class);}
 @Test void fsrsIsDeterministicAndHigherRetentionShortensIntervals(){var scheduler=new ReviewScheduler();var now=Instant.parse("2026-10-01T10:00:00Z");var start=scheduler.review(null,2,.9,now);assertThat(start.state()).isNotBlank();var regular=scheduler.review(start.state(),2,.8,now.plus(Duration.ofDays(3)));var intensive=scheduler.review(start.state(),2,.97,now.plus(Duration.ofDays(3)));assertThat(intensive.due()).isBefore(regular.due());assertThat(scheduler.review(start.state(),2,.8,now.plus(Duration.ofDays(3))).due()).isEqualTo(regular.due());assertThatThrownBy(()->scheduler.review(null,4,.9,now)).isInstanceOf(IllegalArgumentException.class);}
 byte[] docx(String xml)throws Exception{var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry("word/document.xml"));zip.write(xml.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}return out.toByteArray();}
 @Test void wordPreservesParagraphLocationsAndRejectsExternalEntities()throws Exception{
  var extractor=new DocumentTextExtractor();var parts=extractor.extract(docx("<w:document xmlns:w='urn:test'><w:body><w:p><w:r><w:t>First paragraph</w:t></w:r></w:p><w:p><w:r><w:t>Second paragraph</w:t></w:r></w:p></w:body></w:document>"),"test.docx");assertThat(parts).hasSize(2);assertThat(parts.get(1).locator()).isEqualTo("段落 2");assertThat(parts.get(1).text()).isEqualTo("Second paragraph");assertThatThrownBy(()->extractor.extract(docx("<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///must-not-read'>]><root><p><t>&x;</t></p></root>"),"x.docx")).isInstanceOf(com.robustvision.platform.common.BusinessException.class);
 }
 @Test void pdfRetainsPageNumber()throws Exception{try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument()){for(String word:new String[]{"alpha","beta"}){var page=new org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page);try(var content=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)){content.beginText();content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);content.newLineAtOffset(50,700);content.showText(word);content.endText();}}var out=new ByteArrayOutputStream();pdf.save(out);var parts=new DocumentTextExtractor().extract(out.toByteArray(),"pages.pdf");assertThat(parts).hasSize(2);assertThat(parts.get(1).locator()).isEqualTo("第 2 页");assertThat(parts.get(1).text()).isEqualTo("beta");}}
}
