package com.jarvis.controller;

import com.jarvis.dto.JarvisResponse;
import com.jarvis.dto.TextCommandRequest;
import com.jarvis.service.JarvisOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * WebSocket controller for real-time voice assistant interaction.
 * Enables live streaming of commands and responses via STOMP protocol.
 */
@Controller
public class JarvisWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(JarvisWebSocketController.class);

    private final JarvisOrchestratorService orchestrator;

    public JarvisWebSocketController(JarvisOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Handle incoming text commands via WebSocket.
     * Client sends to: /app/command
     * Response sent to: /topic/response
     */
    @MessageMapping("/command")
    @SendTo("/topic/response")
    public JarvisResponse handleCommand(TextCommandRequest request) {
        log.info("WebSocket command received: '{}'", request.getText());

        return orchestrator.processTextCommand(
                request.getText(),
                request.isSpeakResponse(),
                request.getVoice()
        );
    }
}
