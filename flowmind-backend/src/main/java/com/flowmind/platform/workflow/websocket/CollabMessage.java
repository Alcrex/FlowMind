package com.flowmind.platform.workflow.websocket;

public record CollabMessage(String type, Long workflowId, Long senderId, String senderName,
        String senderColor, Object payload, long timestamp) {}
