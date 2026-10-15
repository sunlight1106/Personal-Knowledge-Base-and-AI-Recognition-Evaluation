package com.robustvision.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class ApiRequestSizeFilterTest {
    @Test void rejectsBothDeclaredAndChunkedOversizedBodiesWithoutEchoingThem() throws Exception {
        var filter = new ApiRequestSizeFilter(new ObjectMapper().findAndRegisterModules());
        for (boolean chunked : new boolean[]{false, true}) {
            var req = new MockHttpServletRequest() {
                @Override public long getContentLengthLong() { return chunked ? -1 : super.getContentLengthLong(); }
            };
            req.setMethod("POST"); req.setRequestURI("/api/v1/personal-ai/preview");
            req.setContent(("sensitive-content" + "x".repeat(ApiRequestSizeFilter.MAX_REQUEST_BYTES)).getBytes(StandardCharsets.UTF_8));
            var response = new MockHttpServletResponse(); var called = new AtomicBoolean(false);
            filter.doFilter(req, response, (request, res) -> called.set(true));
            assertThat(called).isFalse(); assertThat(response.getStatus()).isEqualTo(413);
            assertThat(response.getContentAsString()).doesNotContain("sensitive-content");
        }
    }
    @Test void preservesValidJsonAndLeavesOtherRoutesUntouched() throws Exception {
        var filter = new ApiRequestSizeFilter(new ObjectMapper().findAndRegisterModules());
        var req = new MockHttpServletRequest("PUT", "/api/v1/personal-ai/settings/OPENAI");
        req.setContent("{\"model\":\"synthetic\"}".getBytes(StandardCharsets.UTF_8));
        filter.doFilter(req, new MockHttpServletResponse(), (request, response) ->
                assertThat(new String(((HttpServletRequest) request).getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("{\"model\":\"synthetic\"}"));
        var other = new MockHttpServletRequest("POST", "/api/v1/files");
        other.setContentType("multipart/form-data; boundary=synthetic");
        other.setContent(new byte[ApiRequestSizeFilter.MAX_REQUEST_BYTES + 1]);
        filter.doFilter(other, new MockHttpServletResponse(), (request, response) -> assertThat(request).isSameAs(other));
    }
    @Test void newJsonEndpointsRejectChunkedOversizeBeforeDeserialization() throws Exception {
        var filter = new ApiRequestSizeFilter(new ObjectMapper().findAndRegisterModules());
        for (String path : new String[]{"/api/v1/research/answers/execute", "/api/v1/social/contacts/1/messages",
                "/api/v1/workspaces", "/api/v1/workspace-jobs/test/action", "/api/v1/research/documents/passages/test"}) {
            var req = new MockHttpServletRequest("POST", "/context" + path) {
                @Override public long getContentLengthLong() { return -1; }
            };
            req.setServletPath(path); req.setContent(new byte[ApiRequestSizeFilter.MAX_REQUEST_BYTES + 1]);
            var response = new MockHttpServletResponse();
            filter.doFilter(req, response, (r, s) -> fail("Oversize reached the controller"));
            assertThat(response.getStatus()).isEqualTo(413);
        }
    }
    @Test void multipartAttachmentsRemainUnreadAndLargeNotesKeepTheirOwnLimit() throws Exception {
        var filter = new ApiRequestSizeFilter(new ObjectMapper().findAndRegisterModules());
        for (String path : new String[]{"/api/v1/notes/note/attachments", "/api/v1/research/documents",
                "/api/v1/workspace-jobs/documents", "/api/v1/messages", "/api/v1/messages/groups/1"}) {
            var req = new MockHttpServletRequest("POST", path) {
                @Override public jakarta.servlet.ServletInputStream getInputStream() { throw new AssertionError("Multipart stream consumed"); }
            };
            req.setContentType("multipart/form-data; boundary=test");
            req.setContent(new byte[2 * 1024 * 1024]);
            var called = new AtomicBoolean();
            filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> { assertThat(r).isSameAs(req); called.set(true); });
            assertThat(called).isTrue();
        }
        var note = new MockHttpServletRequest("PATCH", "/context/api/v1/notes/test");
        note.setServletPath("/api/v1/notes/test");note.setContent(new byte[500000]);
        filter.doFilter(note, new MockHttpServletResponse(), (r, s) -> assertThat(r.getInputStream().readAllBytes()).hasSize(500000));
        var wrong = new MockHttpServletRequest("POST", "/api/v1/account/profile");
        wrong.setContentType("multipart/form-data; boundary=wrong");wrong.setContent(new byte[20000]);
        var response = new MockHttpServletResponse();
        filter.doFilter(wrong, response, (r, s) -> fail("Wrong content type bypassed envelope limit"));
        assertThat(response.getStatus()).isEqualTo(413);
    }
}
