package com.jarvis.service;

import com.jarvis.adapter.tts.MacOsTtsAdapter;
import com.jarvis.config.JarvisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Startup service that initializes Jarvis after the application context is ready.
 * Optionally starts continuous listening mode if configured.
 */
@Service
public class StartupService {

    private static final Logger log = LoggerFactory.getLogger(StartupService.class);

    private final JarvisProperties properties;
    private final WakeWordService wakeWordService;
    private final MacOsTtsAdapter ttsAdapter;
    private final ClapDetectionService clapDetectionService;

    public StartupService(JarvisProperties properties,
                          WakeWordService wakeWordService,
                          MacOsTtsAdapter ttsAdapter,
                          ClapDetectionService clapDetectionService) {
        this.properties = properties;
        this.wakeWordService = wakeWordService;
        this.ttsAdapter = ttsAdapter;
        this.clapDetectionService = clapDetectionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("============================================");
        log.info("  Jarvis AI Assistant is ONLINE");
        log.info("  API: http://localhost:8080/api/jarvis");
        log.info("  Dashboard: http://localhost:8080");
        log.info("  Status: http://localhost:8080/api/jarvis/status");
        log.info("============================================");

        // Greet on startup
        try {
            ttsAdapter.speak("Jarvis is online and ready to assist you, sir.");
        } catch (Exception e) {
            log.warn("TTS greeting failed (microphone/speaker might not be available): {}", e.getMessage());
        }

        // Auto-start continuous listening if configured
        if (properties.isContinuousListening()) {
            log.info("Auto-starting continuous listening mode...");
            wakeWordService.startListening();
        }

        // Auto-start clap detection
        log.info("Auto-starting double-clap detection...");
        clapDetectionService.start();
    }
}
