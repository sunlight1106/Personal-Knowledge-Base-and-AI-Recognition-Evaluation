package com.robustvision.platform.service;

import com.robustvision.platform.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.assertj.core.api.Assertions.*;

class DocumentResourceLimitsTest {
    private byte[] archive(int files, int expandedBytes) throws Exception {
        var out=new ByteArrayOutputStream();var block=new byte[8192];
        try(var zip=new ZipOutputStream(out)) {
            for(int n=0;n<files;n++) {
                zip.putNextEntry(new ZipEntry("word/media/ignored"+n));
                for(int left=expandedBytes;left>0;left-=block.length)zip.write(block,0,Math.min(left,block.length));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document><p><t>Readable paragraph</t></p></document>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
    @Test void highlyCompressedEntriesCannotExceedPerEntryOrTotalExpansionLimits() throws Exception {
        var extractor=new DocumentTextExtractor();
        for(byte[] compressed:new byte[][]{archive(1,21*1024*1024),archive(3,18*1024*1024)}){
            assertThat(compressed.length).isLessThan(1024*1024);
            assertThatThrownBy(()->extractor.extract(compressed,"test.docx"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("解压后过大");
        }
    }
    @Test void ignoresMediaWithoutRetainingItAndChecksCancellationWhileExpanding() throws Exception {
        byte[] compressed=archive(1,2*1024*1024);
        assertThat(new DocumentTextExtractor().extract(compressed,"test.docx").get(0).text()).isEqualTo("Readable paragraph");
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(()->new DocumentTextExtractor().extract(compressed,"test.docx",false,p->{
            calls.incrementAndGet();throw new IllegalStateException("cancelled");
        })).isInstanceOf(BusinessException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
}
