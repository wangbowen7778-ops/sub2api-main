package com.sub2api.module.gateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.*;

/**
 * 请求体大小限制过滤器
 * 防止过大请求体导致内存溢出
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Component
@Order(1)
public class RequestBodyLimitFilter implements Filter {

    /**
     * 默认最大请求体大小: 256MB
     */
    private static final long DEFAULT_MAX_BODY_SIZE = 256 * 1024 * 1024;

    /**
     * 最大请求体大小配置
     */
    @Value("${sub2api.gateway.max-request-body-size:268435456}")
    private long maxRequestBodySize;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (maxRequestBodySize <= 0) {
            maxRequestBodySize = DEFAULT_MAX_BODY_SIZE;
        }
        log.info("RequestBodyLimitFilter initialized: maxSize={}", formatSize(maxRequestBodySize));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 只对 POST/PUT/PATCH 方法进行检查
        String method = httpRequest.getMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            // 检查 Content-Length
            String contentLengthStr = httpRequest.getHeader("Content-Length");
            if (contentLengthStr != null && !contentLengthStr.isEmpty()) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    if (contentLength > maxRequestBodySize) {
                        log.warn("Request body too large: contentLength={}, max={}, uri={}",
                                contentLength, maxRequestBodySize, httpRequest.getRequestURI());
                        sendError(httpResponse, 413, "Request body too large, limit is " + formatSize(maxRequestBodySize));
                        return;
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid Content-Length header: {}", contentLengthStr);
                }
            }

            // 使用限量读取包装器
            CappedRequestWrapper wrappedRequest = new CappedRequestWrapper(httpRequest, maxRequestBodySize);
            chain.doFilter(wrappedRequest, response);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        log.info("RequestBodyLimitFilter destroyed");
    }

    /**
     * 发送错误响应
     */
    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":{\"type\":\"request_too_large\",\"message\":\"" + message + "\"}}");
    }

    /**
     * 格式化大小
     */
    private String formatSize(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.2fGB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.2fMB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format("%.2fKB", bytes / 1024.0);
        } else {
            return bytes + "B";
        }
    }

    /**
     * 限量请求包装器
     * 限制读取的字节数
     */
    private static class CappedRequestWrapper extends HttpServletRequestWrapper {

        private final long maxSize;
        private CachedBodyHttpServletRequest cachedRequest;

        public CappedRequestWrapper(HttpServletRequest request, long maxSize) {
            super(request);
            this.maxSize = maxSize;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (cachedRequest == null) {
                cachedRequest = new CachedBodyHttpServletRequest((HttpServletRequest) getRequest(), maxSize);
            }
            return cachedRequest.getInputStream();
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }
    }

    /**
     * 缓存请求体以支持多次读取
     */
    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;
        private final long size;

        public CachedBodyHttpServletRequest(HttpServletRequest request, long maxSize) throws IOException {
            super(request);
            this.size = request.getContentLengthLong();
            this.cachedBody = readAndLimitBody(request, maxSize);
        }

        private byte[] readAndLimitBody(HttpServletRequest request, long maxSize) throws IOException {
            InputStream inputStream = request.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            if (size > maxSize) {
                // 预先检查：内容过长，直接拒绝
                throw new IOException("Request body too large");
            }

            int bytesRead;
            byte[] data = new byte[8192];
            long totalRead = 0;

            while ((bytesRead = inputStream.read(data)) != -1) {
                totalRead += bytesRead;
                if (totalRead > maxSize) {
                    throw new IOException("Request body too large during read");
                }
                buffer.write(data, 0, bytesRead);
            }

            return buffer.toByteArray();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(cachedBody);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }
    }

    /**
     * 缓存的 ServletInputStream
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream inputStream;

        public CachedBodyServletInputStream(byte[] body) {
            this.inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }
    }
}
