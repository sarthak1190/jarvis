package com.jarvis.service;

import com.jarvis.adapter.speech.AudioCaptureAdapter;
import com.jarvis.adapter.tts.MacOsTtsAdapter;
import com.jarvis.config.JarvisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wake Word Listener Service.
 * Runs in background daemon mode, continuously listening for the wake word
 * ("Hey Jarvis") before activating the full command pipeline.
 *
 * Uses continuous microphone monitoring with short recording windows
 * to detect the wake word without full Whisper processing overhead.
 */
@Service
public class WakeWordService {

    private static final Logger log = LoggerFactory.getLogger(WakeWordService.class);

    private final JarvisProperties properties;
    private final AudioCaptureAdapter audioCaptureAdapter;
    private final JarvisOrchestratorService orchestrator;
    private final MacOsTtsAdapter ttsAdapter;

    private final AtomicBoolean listening = new AtomicBoolean(false);

    public WakeWordService(JarvisProperties properties,
                           AudioCaptureAdapter audioCaptureAdapter,
                           JarvisOrchestratorService orchestrator,
                           MacOsTtsAdapter ttsAdapter) {
        this.properties = properties;
        this.audioCaptureAdapter = audioCaptureAdapter;
        this.orchestrator = orchestrator;
        this.ttsAdapter = ttsAdapter;
    }

    /**
     * Start continuous listening mode in the background.
     * When the wake word is detected, activate the command pipeline.
     */
    @Async("jarvisExecutor")
    public void startListening() {
        if (listening.compareAndSet(false, true)) {
            log.info("Wake word listener started. Say '{}' to activate.", properties.getWakeWord());
            ttsAdapter.speak("Jarvis is now listening. Say Hey Jarvis to activate.");

            while (listening.get()) {
                try {
                    // Record a short snippet for wake word detection
                    File audioSnippet = audioCaptureAdapter.recordAudio(3000); // 3 seconds

                    // For now, process via the orchestrator
                    // In production, use a lightweight local wake word detector
                    // like Porcupine or Snowboy for efficiency
                    if (audioSnippet.exists() && audioSnippet.length() > 1000) {
                        var response = orchestrator.processAudioFile(audioSnippet);

                        if (response.getTranscribedText() != null &&
                            response.getTranscribedText().toLowerCase()
                                    .contains(properties.getWakeWord().toLowerCase())) {

                            log.info("Wake word detected!");
                            ttsAdapter.speak("Yes? I'm listening.");

                            // Now record the actual command
                            orchestrator.processVoiceCommand(0);
                        }
                    }

                    // Clean up
                    if (audioSnippet.exists()) {
                        audioSnippet.delete();
                    }

                    // Small pause between listening cycles
                    Thread.sleep(200);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Wake word listener error: {}", e.getMessage());
                    try {
                        Thread.sleep(1000); // Back off on error
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            log.info("Wake word listener stopped.");
        }
    }

    /**
     * Stop the continuous listening mode.
     */
    public void stopListening() {
        listening.set(false);
        audioCaptureAdapter.stopRecording();
        log.info("Wake word listener deactivated.");
    }

    /**
     * Check if currently listening for wake word.
     */
    public boolean isListening() {
        return listening.get();
    }
}
