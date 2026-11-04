package com.robustvision.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class VocabularyExpansionTest {
    @Test void additiveBooksHaveValidReferencesPhoneticsAndDistinctOptions() throws Exception {
        var json=new ObjectMapper();
        var raw=Objects.requireNonNull(getClass().getResourceAsStream("/vocabulary/v49.json")).readAllBytes();
        var data=json.readTree(raw);var manifest=json.readTree(getClass().getResourceAsStream("/vocabulary/expansion-v49-manifest.json"));
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))).isEqualTo(manifest.path("catalogSha256").asText());
        var base=json.readTree(new GZIPInputStream(getClass().getResourceAsStream("/vocabulary/v17.json.gz")));
        var lessons=json.readTree(new GZIPInputStream(getClass().getResourceAsStream("/vocabulary/lessons-v1.json.gz")));
        var entries=(com.fasterxml.jackson.databind.node.ArrayNode)base.path("entries");entries.addAll((com.fasterxml.jackson.databind.node.ArrayNode)data.path("entries"));
        Set<String> oldBooks=new HashSet<>();for(var b:base.path("books"))oldBooks.add(b.path("id").asText());
        Set<String> ids=new HashSet<>();int total=0;
        for(var b:data.path("books")) {
            String id=b.path("id").asText();assertThat(ids.add(id)).isTrue();assertThat(oldBooks).doesNotContain(id);
            Set<String> terms=new HashSet<>();
            for(var ref:b.path("words")) {
                assertThat(ref.asInt()).isBetween(0,entries.size()-1);var e=entries.get(ref.asInt());String term=e.path("term").asText();
                assertThat(terms.add(term)).isTrue();assertThat(id+"-"+ref.asInt()).hasSizeLessThanOrEqualTo(36);
                assertThat(e.path("meaning").asText()).isNotBlank().hasSizeLessThanOrEqualTo(160);
                assertThat(e.path("ipa").asText().isBlank()?lessons.path(term).path("ipa").asText():e.path("ipa").asText()).isNotBlank().doesNotContain("*").hasSizeLessThanOrEqualTo(120);
                Set<String> options=new HashSet<>();options.add(e.path("meaning").asText());for(var option:e.path("distractors"))assertThat(options.add(option.asText())).isTrue();assertThat(options).hasSize(4);
            }
            assertThat(terms.size()).isGreaterThanOrEqualTo(80);total+=terms.size();
        }
        assertThat(ids).hasSize(16);assertThat(total).isEqualTo(manifest.path("totalEntries").asInt());
        assertThat(data.path("entries").size()).isEqualTo(manifest.path("newHeadwords").asInt());
        assertThat(new db.migration.V49__thematic_vocabulary_books().getChecksum()).isNotNull();
    }

    @Test void migrationOnlyAddsBooksAndSharesCanonicalKeysWithoutChangingProgress() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:h2:mem:expansion_"+UUID.randomUUID()+";MODE=MySQL");var s=c.createStatement()) {
            s.execute("create table vocabulary_book(id varchar(36) primary key,owner_id bigint,title varchar(100),description varchar(600),attribution varchar(300),level varchar(40),created_at timestamp)");
            s.execute("create table vocabulary_word(id varchar(36) primary key,book_id varchar(36),term varchar(80),term_key varchar(80),ipa varchar(120),pos varchar(24),meaning varchar(160),example_text varchar(400),example_translation varchar(400),distractors varchar(1500),sort_order int,unique(book_id,term))");
            s.execute("create table vocabulary_progress(owner_id bigint,term_key varchar(80),learning_correct int)");
            s.execute("insert into vocabulary_book(id,owner_id,title) values('private',7,'Keep my book')");
            s.execute("insert into vocabulary_word(id,book_id,term,term_key,meaning) values('old-word','private','travel','travel','Keep my meaning')");
            s.execute("insert into vocabulary_progress values(7,'travel',4),(8,'travel',1)");
            var context=mock(org.flywaydb.core.api.migration.Context.class);when(context.getConnection()).thenReturn(c);
            new db.migration.V49__thematic_vocabulary_books().migrate(context);
            try(var r=s.executeQuery("select count(*) from vocabulary_book where owner_id is null")){r.next();assertThat(r.getInt(1)).isEqualTo(16);}
            try(var r=s.executeQuery("select meaning from vocabulary_word where id='old-word'")){r.next();assertThat(r.getString(1)).isEqualTo("Keep my meaning");}
            try(var r=s.executeQuery("select owner_id,learning_correct from vocabulary_progress order by owner_id")){r.next();assertThat(r.getLong(1)).isEqualTo(7);assertThat(r.getInt(2)).isEqualTo(4);r.next();assertThat(r.getInt(2)).isEqualTo(1);assertThat(r.next()).isFalse();}
            try(var r=s.executeQuery("select distinct term,term_key from vocabulary_word where book_id <> 'private'")){while(r.next())assertThat(r.getString(2)).isEqualTo(VocabularyTermKey.of(r.getString(1)));}
            try(var r=s.executeQuery("select count(*) from vocabulary_word where term_key='travel'")){r.next();assertThat(r.getInt(1)).isGreaterThan(1);}
        }
    }
}
