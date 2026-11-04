package db.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robustvision.platform.service.VocabularyTermKey;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

/** Additive offline books. Existing words, profiles and owner-scoped progress stay intact. */
public class V49__thematic_vocabulary_books extends BaseJavaMigration {
    private InputStream resource(String name) {
        return Objects.requireNonNull(getClass().getResourceAsStream("/vocabulary/"+name), "Missing vocabulary data: "+name);
    }
    @Override public Integer getChecksum() {
        try(var base=resource("v17.json.gz");var expansion=resource("v49.json")) {
            var crc=new CRC32();crc.update(base.readAllBytes());crc.update(expansion.readAllBytes());return (int)crc.getValue();
        } catch(Exception error) {throw new IllegalStateException("Cannot checksum vocabulary expansion",error);}
    }
    @Override public void migrate(Context context) throws Exception {
        var json=new ObjectMapper();
        try(var base=new GZIPInputStream(resource("v17.json.gz"));var expansion=resource("v49.json");
            var book=context.getConnection().prepareStatement("INSERT INTO vocabulary_book (id,owner_id,title,description,attribution,level,created_at) VALUES (?,NULL,?,?,?,?,?)");
            var word=context.getConnection().prepareStatement("INSERT INTO vocabulary_word (id,book_id,term,term_key,ipa,pos,meaning,example_text,example_translation,distractors,sort_order) VALUES (?,?,?,?,?,?,?,'','',?,?)")) {
            var data=json.readTree(expansion);
            var entries=(com.fasterxml.jackson.databind.node.ArrayNode)json.readTree(base).path("entries");
            entries.addAll((com.fasterxml.jackson.databind.node.ArrayNode)data.path("entries"));
            for(var b:data.path("books")) {
                String id=b.path("id").asText();
                book.setString(1,id);book.setString(2,b.path("title").asText());book.setString(3,b.path("description").asText());
                book.setString(4,"ECDICT · MIT · 主题选编；部分补充释义与美式音标为本项目原创");book.setString(5,b.path("level").asText());
                book.setTimestamp(6,Timestamp.from(Instant.parse("2026-11-04T00:00:00Z")));book.executeUpdate();
                int order=0;
                for(var ref:b.path("words")) {
                    var entry=entries.get(ref.asInt());String term=entry.path("term").asText();
                    word.setString(1,id+"-"+ref.asInt());word.setString(2,id);word.setString(3,term);word.setString(4,VocabularyTermKey.of(term));
                    word.setString(5,entry.path("ipa").asText());word.setString(6,entry.path("pos").asText());word.setString(7,entry.path("meaning").asText());
                    word.setString(8,json.writeValueAsString(entry.path("distractors")));word.setInt(9,order++);word.addBatch();
                    if(order%500==0)word.executeBatch();
                }
                word.executeBatch();
            }
        }
    }
}
