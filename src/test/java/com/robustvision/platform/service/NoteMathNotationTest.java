package com.robustvision.platform.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NoteMathNotationTest {
    private final String formula="<math xmlns=\"http://www.w3.org/1998/Math/MathML\" display=\"block\" aria-label=\"Theta(n^2)\"><msup><mi>n</mi><mn>2</mn></msup></math>";
    @Test void htmlExportKeepsMathematicsAndPlainFormatsHaveReadableFallback(){
        assertThat(NoteContent.markdownToHtml(formula)).contains("<math", "<msup>","<mn>2</mn>").doesNotContain("&lt;math");
        assertThat(MarkdownDocument.plainText(formula)).isEqualTo("Theta(n²)");
    }
    @Test void mathDoesNotReenableScriptsImagesOrEventHandlers(){
        String unsafe="<math display=\"block\" onclick=\"alert(1)\"><mrow><mi href=\"javascript:alert(1)\">n</mi><script>alert(1)</script><img src=\"https://invalid.example/track\"></mrow></math>";
        assertThat(NoteContent.safeHtml(unsafe)).contains("<math", "<mi>n</mi>").doesNotContain("script", "onclick", "javascript:","<img", "https:");
    }
    @Test void embeddedFencesRemainInsideCodeInEveryExport(){
        var blocks=MarkdownDocument.parse("````python\nvalue = '''\n```\n<script>literal</script>\n'''\n````\n\nAfter");
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).meta()).containsExactly("python");
        assertThat(blocks.get(0).lines()).contains("```", "<script>literal</script>");
        assertThat(NoteContent.markdownToHtml("````python\n```\n<script>literal</script>\n````")).contains("&lt;script&gt;").doesNotContain("<script>");
    }
}
