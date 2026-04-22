package com.jarvis.adapter.tts;

import com.jarvis.config.JarvisProperties;
import com.jarvis.exception.JarvisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Text-to-Speech adapter using macOS native 'say' command.
 * Provides synchronous and asynchronous speech synthesis.
 */
@Component
public class MacOsTtsAdapter {

    private static final Logger log = LoggerFactory.getLogger(MacOsTtsAdapter.class);

    private final JarvisProperties properties;
    private Process currentProcess;

    public MacOsTtsAdapter(JarvisProperties properties) {
        this.properties = properties;
    }

    /**
     * Speak text synchronously using macOS say command.
     *
     * @param text the text to speak
     */
    public void speak(String text) {
        speak(text, null);
    }

    /**
     * Speak text synchronously with optional voice override.
     *
     * @param text  the text to speak
     * @param voice optional voice name (null = use default from config)
     */
    public void speak(String text, String voice) {
        if (text == null || text.isBlank()) {
            log.warn("Attempted to speak empty text, skipping");
            return;
        }

        JarvisProperties.TtsConfig ttsConfig = properties.getTts();
        String selectedVoice = voice != null ? voice : ttsConfig.getVoice();
        int rate = ttsConfig.getRate();

        // Sanitize text for shell safety
        String sanitized = sanitizeForShell(text);

        log.info("Speaking: '{}' (voice: {}, rate: {})", truncate(text, 100), selectedVoice, rate);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "say",
                    "-v", selectedVoice,
                    "-r", String.valueOf(rate),
                    sanitized
            );
            pb.redirectErrorStream(true);

            currentProcess = pb.start();
            String output = new String(currentProcess.getInputStream().readAllBytes());
            int exitCode = currentProcess.waitFor();

            if (exitCode != 0) {
                log.error("TTS say command failed (exit {}): {}", exitCode, output);
                throw new JarvisException("TTS failed with exit code: " + exitCode);
            }

            log.debug("TTS completed successfully");

        } catch (IOException e) {
            log.error("Failed to execute say command", e);
            throw new JarvisException("Failed to speak text", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("TTS was interrupted");
        } finally {
            currentProcess = null;
        }
    }

    /**
     * Speak text asynchronously.
     *
     * @param text the text to speak
     * @return CompletableFuture that completes when speech finishes
     */
    @Async("jarvisExecutor")
    public CompletableFuture<Void> speakAsync(String text) {
        speak(text);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Stop any currently playing speech.
     */
    public void stop() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
            log.debug("TTS playback stopped");
        }
    }

    /**
     * Sanitize text to prevent shell injection in the say command.
     */
    private String sanitizeForShell(String text) {
        // Remove potentially dangerous characters but keep punctuation for natural speech
        return text.replaceAll("[;|&`$]", "")
                   .replaceAll("\\\\", "")
                   .trim();
    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
