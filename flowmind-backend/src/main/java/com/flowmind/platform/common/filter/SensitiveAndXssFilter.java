package com.flowmind.platform.common.filter;

import com.fasterxml.jackson.databind.*;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.security.*;
import com.flowmind.platform.system.service.SysAuditAndConfigService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Reads JSON once and replays the exact bytes for MVC. JSON escapes cannot bypass text inspection. */
public class SensitiveAndXssFilter extends OncePerRequestFilter {
    private static final Pattern XSS = Pattern.compile("(?is)<\\s*(script|iframe|object|embed)\\b|javascript\\s*:|<[^>]+\\bon[a-z]+\\s*=");
    private final SysAuditAndConfigService audit;
    private final ObjectMapper json;
    public SensitiveAndXssFilter(SysAuditAndConfigService audit, ObjectMapper json) { this.audit = audit; this.json = json; }

    @Override protected boolean shouldNotFilter(HttpServletRequest req) { return !req.getRequestURI().startsWith("/api/v1/") || "OPTIONS".equals(req.getMethod()); }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        LoginUser user = null;
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser p) user = p;
        boolean configWrite = "PUT".equals(req.getMethod()) && "/api/v1/admin/models/config".equals(req.getRequestURI()) && user != null && "ADMIN".equals(user.roleType());
        List<String> words = audit.sensitiveWords();
        for (var values : req.getParameterMap().values()) for (String value : values) {
            if (blocked(value, words)) { reject(req, res, user); return; }
        }
        HttpServletRequest replay = req;
        if (req.getContentType() != null && req.getContentType().toLowerCase(Locale.ROOT).contains("json")) {
            byte[] bytes = req.getInputStream().readAllBytes();
            if (bytes.length > 0) {
                JsonNode body;
                try { body = json.readTree(bytes); }
                catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                    res.setStatus(400); res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getOutputStream(), Result.failure(ErrorCode.PARAM_ERROR, ErrorCode.PARAM_ERROR.getMessage())); return;
                }
                // Only the administrator's root rule list is exempt, so rules can contain the phrases being blocked.
                if (configWrite && body.isObject()) { body = body.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) body).remove("sensitiveWords"); }
                if (blockedJson(body, words)) { reject(req, res, user); return; }
            }
            replay = new CachedBodyRequest(req, bytes);
        }
        chain.doFilter(replay, res);
    }

    private boolean blockedJson(JsonNode node, List<String> words) {
        if (node == null) return false;
        if (node.isTextual()) return blocked(node.asText(), words);
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) { var e = fields.next(); if (blocked(e.getKey(), words) || blockedJson(e.getValue(), words)) return true; }
        } else if (node.isArray()) for (var child : node) if (blockedJson(child, words)) return true;
        return false;
    }
    private boolean blocked(String text, List<String> words) {
        String normalized = normalize(text);
        return XSS.matcher(normalized).find() || words.stream().anyMatch(w -> normalized.contains(normalize(w)));
    }
    private String normalize(String value) { return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private void reject(HttpServletRequest req, HttpServletResponse res, LoginUser user) throws IOException {
        String path = req.getRequestURI();
        String module = path.startsWith("/api/v1/engine") ? "ENGINE" : path.startsWith("/api/v1/workflows") ? "WORKFLOW" : path.startsWith("/api/v1/edu") ? "EDU" : path.startsWith("/api/v1/auth") ? "AUTH" : "ADMIN";
        audit.record(user, module, "SENSITIVE_BLOCK", 400, req, Map.of("reason", "SENSITIVE_OR_XSS"), "请求包含违规敏感词或恶意脚本");
        res.setStatus(400); res.setContentType("application/json;charset=UTF-8");
        json.writeValue(res.getOutputStream(), Result.failure(ErrorCode.SENSITIVE_WORD_BLOCKED, ErrorCode.SENSITIVE_WORD_BLOCKED.getMessage()));
    }

    private static class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] bytes;
        CachedBodyRequest(HttpServletRequest request, byte[] bytes) { super(request); this.bytes = bytes; }
        @Override public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] b, int off, int len) { return input.read(b, off, len); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    try { if (!isFinished()) listener.onDataAvailable(); if (isFinished()) listener.onAllDataRead(); }
                    catch (IOException ex) { listener.onError(ex); }
                }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
