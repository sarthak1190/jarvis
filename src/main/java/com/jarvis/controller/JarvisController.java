package com.jarvis.controller;

import com.jarvis.dto.JarvisResponse;
import com.jarvis.dto.TextCommandRequest;
import com.jarvis.engine.execution.CommandExecutionEngine;
import com.jarvis.service.JarvisOrchestratorService;
import com.jarvis.service.WakeWordService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Main REST Controller for Jarvis AI Assistant.
 * Provides endpoints for text commands, voice commands, audio file upload,
 * wake word control, and system status.
 */
@RestController
@RequestMapping("/api/jarvis")
@CrossOrigin(origins = "*")
public class JarvisController {

    private static final Logger log = LoggerFactory.getLogger(JarvisController.class);

    private final JarvisOrchestratorService orchestrator;
    private final WakeWordService wakeWordService;
    private final CommandExecutionEngine executionEngine;
    private final com.jarvis.service.ClapDetectionService clapDetectionService;

    public JarvisController(JarvisOrchestratorService orchestrator,
                            WakeWordService wakeWordService,
                            CommandExecutionEngine executionEngine,
                            com.jarvis.service.ClapDetectionService clapDetectionService) {
        this.orchestrator = orchestrator;
        this.wakeWordService = wakeWordService;
        this.executionEngine = executionEngine;
        this.clapDetectionService = clapDetectionService;
    }

    // ========================
    // Command Processing
    // ========================

    /**
     * Process a text command.
     * This is the primary endpoint for testing and text-based interaction.
     *
     * POST /api/jarvis/command
     * Body: {"text": "Open Safari", "speakResponse": true}
     */
    @PostMapping("/command")
    public ResponseEntity<JarvisResponse> processTextCommand(
            @Valid @RequestBody TextCommandRequest request) {

        log.info("Received text command: '{}'", request.getText());

        JarvisResponse response = orchestrator.processTextCommand(
                request.getText(),
                request.isSpeakResponse(),
                request.getVoice()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Quick command via GET (for easy testing).
     *
     * GET /api/jarvis/command?text=open+safari&speak=true
     */
    @GetMapping("/command")
    public ResponseEntity<JarvisResponse> processQuickCommand(
            @RequestParam String text,
            @RequestParam(defaultValue = "false") boolean speak) {

        log.info("Received quick command: '{}'", text);

        JarvisResponse response = orchestrator.processTextCommand(text, speak, null);
        return ResponseEntity.ok(response);
    }

    /**
     * Process an uploaded audio file.
     *
     * POST /api/jarvis/audio
     * Content-Type: multipart/form-data
     * Body: file=@recording.wav
     */
    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JarvisResponse> processAudioFile(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("Received audio file: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        // Save to temp file
        Path tempPath = Files.createTempFile("jarvis_upload_", ".wav");
        File tempFile = tempPath.toFile();
        file.transferTo(tempFile);

        try {
            JarvisResponse response = orchestrator.processAudioFile(tempFile);
            return ResponseEntity.ok(response);
        } finally {
            tempFile.delete();
        }
    }

    /**
     * Start recording from microphone and process the voice command.
     *
     * POST /api/jarvis/voice
     * Body: {"durationMs": 5000} (optional, 0 = auto-detect silence)
     */
    @PostMapping("/voice")
    public ResponseEntity<JarvisResponse> processVoiceCommand(
            @RequestBody(required = false) Map<String, Long> body) {

        long durationMs = body != null ? body.getOrDefault("durationMs", 0L) : 0L;
        log.info("Starting voice command capture (duration: {}ms)", durationMs);

        JarvisResponse response = orchestrator.processVoiceCommand(durationMs);
        return ResponseEntity.ok(response);
    }

    // ========================
    // Wake Word Control
    // ========================

    /**
     * Start continuous listening mode (wake word detection).
     *
     * POST /api/jarvis/listen/start
     */
    @PostMapping("/listen/start")
    public ResponseEntity<Map<String, String>> startListening() {
        log.info("Starting continuous listening mode");
        wakeWordService.startListening();
        return ResponseEntity.ok(Map.of(
                "status", "listening",
                "message", "Jarvis is now listening for wake word"
        ));
    }

    /**
     * Stop continuous listening mode.
     *
     * POST /api/jarvis/listen/stop
     */
    @PostMapping("/listen/stop")
    public ResponseEntity<Map<String, String>> stopListening() {
        log.info("Stopping continuous listening mode");
        wakeWordService.stopListening();
        return ResponseEntity.ok(Map.of(
                "status", "stopped",
                "message", "Jarvis has stopped listening"
        ));
    }

    // ========================
    // Clap Detection Control
    // ========================

    @PostMapping("/clap/start")
    public ResponseEntity<Map<String, String>> startClapDetection() {
        log.info("Starting clap detection mode");
        clapDetectionService.start();
        return ResponseEntity.ok(Map.of(
                "status", "listening",
                "message", "Jarvis is now listening for double claps"
        ));
    }

    @PostMapping("/clap/stop")
    public ResponseEntity<Map<String, String>> stopClapDetection() {
        log.info("Stopping clap detection mode");
        clapDetectionService.stop();
        return ResponseEntity.ok(Map.of(
                "status", "stopped",
                "message", "Jarvis has stopped clap detection"
        ));
    }

    // ========================
    // Status & Info
    // ========================

    /**
     * Get Jarvis status and capabilities.
     *
     * GET /api/jarvis/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Set<String> supportedActions = executionEngine.getSupportedActions();

        return ResponseEntity.ok(Map.of(
                "status", "online",
                "name", "Jarvis AI Assistant",
                "version", "1.0.0",
                "listening", wakeWordService.isListening(),
                "supportedActions", supportedActions,
                "actionCount", supportedActions.size()
        ));
    }

    /**
     * Health check endpoint.
     *
     * GET /api/jarvis/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "Jarvis is operational"
        ));
    }
}
