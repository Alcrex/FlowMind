package com.flowmind.platform.workflow.websocket;

import com.fasterxml.jackson.databind.*;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.common.security.*;
import com.flowmind.platform.workflow.service.WfWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollabWebSocketHandler extends TextWebSocketHandler implements HandshakeInterceptor {
    private static final String[] COLORS = {"#3B82F6", "#10B981", "#F59E0B", "#EC4899", "#8B5CF6", "#EF4444"};
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final TokenAuthenticator authenticator;
    private final WfWorkflowService workflows;
    private final ObjectMapper json;
    @Override public boolean beforeHandshake(ServerHttpRequest req, ServerHttpResponse res, WebSocketHandler handler, Map<String, Object> attributes) {
        try {
            var uri = UriComponentsBuilder.fromUri(req.getURI()).build();
            String token = uri.getQueryParams().getFirst("token");
            Long id = Long.valueOf(req.getURI().getPath().substring(req.getURI().getPath().lastIndexOf('/') + 1));
            LoginUser user = authenticator.authenticate(token); workflows.requireEdit(id, user);
            attributes.put("user", user); attributes.put("workflowId", id); attributes.put("token", token); return true;
        } catch (BusinessException ex) { res.setStatusCode(HttpStatusCode.valueOf(ex.getErrorCode().getHttpStatus())); return false; }
        catch (IllegalArgumentException ex) { res.setStatusCode(HttpStatus.UNAUTHORIZED); return false; }
    }
    @Override public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res, WebSocketHandler handler, Exception ex) {
        if (ex != null) log.debug("Collaboration handshake failed: {}", ex.getClass().getSimpleName());
    }
    private Long roomId(WebSocketSession session) { return (Long) session.getAttributes().get("workflowId"); }
    private LoginUser user(WebSocketSession session) { return (LoginUser) session.getAttributes().get("user"); }
    private String color(LoginUser user) { return COLORS[Math.floorMod(user.id(), COLORS.length)]; }
    private CollabMessage frame(WebSocketSession session, String type, Object payload) {
        LoginUser user = user(session);
        return new CollabMessage(type, roomId(session), user.id(), user.realName(), color(user), payload, System.currentTimeMillis());
    }
    @Override public void afterConnectionEstablished(WebSocketSession original) throws Exception {
        original.setTextMessageSizeLimit(1024 * 1024);
        var session = new ConcurrentWebSocketSessionDecorator(original, 10000, 2 * 1024 * 1024);
        Long id = roomId(session);
        // Serialize membership changes per workflow, including the initial state transfer.
        rooms.compute(id, (key, room) -> {
            if (room == null) room = new ConcurrentHashMap<>();
            boolean first = room.isEmpty(); room.put(session.getId(), session);
            send(session, frame(session, "CONNECTED", Map.of("sessionId", session.getId())));
            if (first) send(session, frame(session, "GRAPH_INIT", workflows.detail(id, user(session))));
            else broadcast(room, frame(session, "SYNC_REQUEST", Map.of("targetSessionId", session.getId())), session.getId());
            presence(session, room); return room;
        });
        log.info("Collaboration joined: workflow={}, user={}", id, user(session).id());
    }
    private void presence(WebSocketSession origin, Map<String, WebSocketSession> room) {
        Map<Long, Object> members = new LinkedHashMap<>();
        room.values().forEach(s -> { var u = user(s); members.put(u.id(), Map.of("userId", u.id(), "realName", u.realName(), "color", color(u))); });
        broadcast(room, frame(origin, "PRESENCE_SYNC", members.values()), null);
    }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            LoginUser current = authenticator.authenticate((String) session.getAttributes().get("token"));
            workflows.requireEdit(roomId(session), current);
            JsonNode incoming = json.readTree(message.getPayload());
            String type = incoming.path("type").asText(); JsonNode payload = incoming.path("payload");
            if (!Set.of("CURSOR_MOVE", "GRAPH_DELTA", "YJS_UPDATE", "SYNC_REQUEST").contains(type)) { session.close(CloseStatus.BAD_DATA); return; }
            if ("CURSOR_MOVE".equals(type) && (!payload.path("x").isNumber() || !payload.path("y").isNumber()
                    || !Double.isFinite(payload.path("x").asDouble()) || !Double.isFinite(payload.path("y").asDouble()))) {
                session.close(CloseStatus.BAD_DATA); return;
            }
            var room = rooms.get(roomId(session)); if (room == null) return;
            // Recheck peers as well: revoked/expired identities cannot continue receiving updates.
            for (var peer : room.values()) {
                try { workflows.requireEdit(roomId(peer), authenticator.authenticate((String) peer.getAttributes().get("token"))); }
                catch (BusinessException ex) { peer.close(CloseStatus.POLICY_VIOLATION); }
            }
            if ("YJS_UPDATE".equals(type) && !payload.path("update").isTextual()) { session.close(CloseStatus.BAD_DATA); return; }
            var outgoing = frame(session, type, payload);
            if (payload.hasNonNull("targetSessionId") && "YJS_UPDATE".equals(type)) {
                var target = room.get(payload.path("targetSessionId").asText()); if (target != null) send(target, outgoing);
            } else broadcast(room, outgoing, session.getId());
        } catch (BusinessException ex) { session.close(CloseStatus.POLICY_VIOLATION); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { session.close(CloseStatus.BAD_DATA); }
    }
    private void send(WebSocketSession session, CollabMessage message) {
        if (!session.isOpen()) return;
        try { session.sendMessage(new TextMessage(json.writeValueAsString(message))); }
        catch (IOException | RuntimeException ex) {
            log.debug("Collaboration send failed for session {}", session.getId());
            try { session.close(CloseStatus.SERVER_ERROR); } catch (IOException ignored) { log.debug("Session already closed"); }
        }
    }
    private void broadcast(Map<String, WebSocketSession> room, CollabMessage message, String excludedSession) {
        room.values().stream().filter(s -> !s.getId().equals(excludedSession)).forEach(s -> send(s, message));
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        rooms.computeIfPresent(roomId(session), (id, room) -> {
            room.remove(session.getId()); if (room.isEmpty()) return null; presence(session, room); return room;
        });
    }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception { session.close(CloseStatus.SERVER_ERROR); }
    @TransactionalEventListener
    public void accessChanged(WfWorkflowService.AccessChanged event) {
        if (event.resetCanvas()) {
            var sessions = rooms.remove(event.workflowId());
            if (sessions != null) {
                for (var session : sessions.values()) {
                    send(session, frame(session, "WORKFLOW_REFRESH", Map.of("workflowId", event.workflowId())));
                    try { session.close(CloseStatus.SERVICE_RESTARTED); } catch (IOException ignored) { log.debug("Refreshing session already closed"); }
                }
            }
            return;
        }
        var room = rooms.get(event.workflowId()); if (room == null) return;
        for (var session : room.values()) {
            try {
                workflows.requireEdit(event.workflowId(), authenticator.authenticate((String) session.getAttributes().get("token")));
                var workflow = workflows.requireWorkflow(event.workflowId());
                send(session, frame(session, "MEMBERS_CHANGED", Map.of("collaboratorIds", workflow.getCollaboratorIds())));
            } catch (BusinessException ex) {
                try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { log.debug("Revoked session already closed"); }
            }
        }
    }
}
