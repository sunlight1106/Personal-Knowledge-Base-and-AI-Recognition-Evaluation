package com.robustvision.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robustvision.platform.common.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Bound API envelopes before deserialization; multipart streams belong to the servlet container. */
@Component @Order(20)
public class ApiRequestSizeFilter extends OncePerRequestFilter {
    static final int MAX_REQUEST_BYTES = 160000;
    private final ObjectMapper mapper;
    public ApiRequestSizeFilter(ObjectMapper mapper) { this.mapper = mapper; }
    private String path(HttpServletRequest request) {
        return request.getServletPath().isEmpty() ? request.getRequestURI() : request.getServletPath();
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        // Vocabulary has its own bounded import/backup envelopes.
        return !path.startsWith("/api/v1/") || path.startsWith("/api/v1/vocabulary/")
                || !Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = path(request);
        String type = request.getContentType();
        if (type != null && type.toLowerCase(java.util.Locale.ROOT).startsWith("multipart/form-data")
                && (path.equals("/api/v1/files") || path.equals("/api/v1/messages")
                || path.matches("/api/v1/messages/groups/[0-9]+")
                || path.matches("/api/v1/notes/[^/]+/attachments")
                || path.equals("/api/v1/research/documents") || path.equals("/api/v1/workspace-jobs/documents"))) {
            long max = path.endsWith("/documents") ? 11L * 1024 * 1024
                    : path.startsWith("/api/v1/messages") ? 105L * 1024 * 1024 : 21L * 1024 * 1024;
            if (request.getContentLengthLong() > max) { reject(response); return; }
            // Never read getInputStream here: doing so breaks getParts() and discards uploaded files.
            chain.doFilter(request, response); return;
        }
        int max = path.equals("/api/v1/notes") || path.startsWith("/api/v1/notes/") ? 1_000_000
                : path.equals("/api/v1/learning/cards/import") ? 800000
                : path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/account/")
                || path.startsWith("/api/v1/learning/") ? 16384 : MAX_REQUEST_BYTES;
        if (request.getContentLengthLong() > max) { reject(response); return; }
        byte[] bytes = request.getInputStream().readNBytes(max + 1);
        if (bytes.length > max) { reject(response); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public int getContentLength() { return bytes.length; }
            @Override public long getContentLengthLong() { return bytes.length; }
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] buffer, int offset, int length) { return input.read(buffer, offset, length); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new IllegalStateException("Synchronous request body"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        }, response);
    }
    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), ApiResponse.failure("REQUEST_TOO_LARGE", "请求内容过长，请减少内容后重试"));
    }
}
