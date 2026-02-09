package cs6650.assignment.chatserver.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import cs6650.assignment.chatserver.model.ChatMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // roomId -> sessions
    private static final Map<String, Set<WebSocketSession>> rooms =
            new ConcurrentHashMap<>();

    /* =================== 连接建立 =================== */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = extractRoomId(session);

        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
                .add(session);

        // 自动发送 JOIN 消息
        Map<String, Object> joinMsg = Map.of(
                "status", "info",
                "serverTimestamp", Instant.now().toString(),
                "message", "A user joined room " + roomId
        );

        broadcast(roomId, joinMsg);
    }

    /* =================== 接收消息 =================== */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomId = extractRoomId(session);

        try {
            ChatMessage chatMsg =
                    objectMapper.readValue(message.getPayload(), ChatMessage.class);

            String errorReason = getValidationError(chatMsg);

            if (errorReason == null) {
                Map<String, Object> response = Map.of(
                        "status", "success",
                        "serverTimestamp", Instant.now().toString(),
                        "roomId", roomId,
                        "payload", chatMsg
                );

                broadcast(roomId, response);
            } else {
                session.sendMessage(new TextMessage(
                        "{\"status\":\"error\",\"reason\":\"" + errorReason + "\"}"
                ));
            }

        } catch (Exception e) {
            session.sendMessage(new TextMessage(
                    "{\"status\":\"error\",\"reason\":\"Invalid JSON format\"}"
            ));
        }
    }

    /* =================== 连接关闭 =================== */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = extractRoomId(session);

        Set<WebSocketSession> sessions = rooms.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
        }

        Map<String, Object> leaveMsg = Map.of(
                "status", "info",
                "serverTimestamp", Instant.now().toString(),
                "message", "A user left room " + roomId
        );

        broadcast(roomId, leaveMsg);
    }

    /* =================== 广播工具方法 =================== */
    private void broadcast(String roomId, Map<String, Object> message) throws Exception {
        Set<WebSocketSession> sessions = rooms.get(roomId);
        if (sessions == null) return;

        String json = objectMapper.writeValueAsString(message);

        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(json));
            }
        }
    }

    /* =================== roomId 解析 =================== */
    private String extractRoomId(WebSocketSession session) {
        // ws://localhost:8080/chat/room123
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /* =================== 校验逻辑（保持不变） =================== */
    private String getValidationError(ChatMessage msg) {
        try {
            if (msg.getUserId() == null) return "userId is missing";
            int uid = Integer.parseInt(msg.getUserId());
            if (uid < 1 || uid > 100000) return "userId must be 1-100000";
        } catch (NumberFormatException e) {
            return "userId must be a numeric string";
        }

        if (msg.getUsername() == null ||
                !msg.getUsername().matches("^[a-zA-Z0-9]{3,20}$")) {
            return "username must be 3-20 alphanumeric characters";
        }

        if (msg.getMessage() == null ||
                msg.getMessage().length() < 1 ||
                msg.getMessage().length() > 500) {
            return "message length must be 1-500 characters";
        }

        try {
            if (msg.getTimestamp() == null) return "timestamp is missing";
            Instant.parse(msg.getTimestamp());
        } catch (DateTimeParseException e) {
            return "invalid ISO-8601 timestamp";
        }

        if (msg.getMessageType() == null ||
                !Set.of("TEXT", "JOIN", "LEAVE").contains(msg.getMessageType())) {
            return "messageType must be TEXT, JOIN, or LEAVE";
        }

        return null;
    }
}
