package com.jarvis.service;

import com.jarvis.config.JarvisProperties;
import com.jarvis.adapter.tts.MacOsTtsAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Clap Detection Service — listens continuously for two sharp audio spikes
 * (double clap) and activates Jarvis's voice command pipeline.
 *
 * Algorithm:
 *   1. Stream microphone audio in 30ms chunks.
 *   2. Compute RMS (root-mean-square) energy for each chunk.
 *   3. If RMS > threshold → mark as a "clap".
 *   4. Enforce a refractory period (min gap between two claps = 200ms).
 *   5. If two claps fall within {@code maxClapGapMs}, trigger Jarvis.
 *   6. After trigger, suppress further detection for {@code cooldownMs}.
 */
@Service
public class ClapDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ClapDetectionService.class);

    // ── Tunable parameters ──────────────────────────────────────────────────
    /** RMS value (0.0–1.0) a chunk must exceed to count as a clap. */
    private static final double CLAP_RMS_THRESHOLD = 0.18;

    /** Minimum time between two individual claps (avoids echo double-count). */
    private static final long MIN_CLAP_GAP_MS = 150;

    /** Maximum window for the second clap to arrive after the first. */
    private static final long MAX_CLAP_GAP_MS = 1_200;

    /** After triggering, ignore microphone for this long (prevents re-trigger). */
    private static final long COOLDOWN_MS = 4_000;

    /** Audio chunk size in milliseconds. */
    private static final int CHUNK_MS = 30;
    // ────────────────────────────────────────────────────────────────────────

    private final JarvisOrchestratorService orchestrator;
    private final MacOsTtsAdapter ttsAdapter;
    private final JarvisProperties properties;
    private final Executor jarvisExecutor;
    private final SimpMessagingTemplate messagingTemplate;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cooldown = new AtomicBoolean(false);
    private final AtomicLong firstClapTime = new AtomicLong(0);
    private final AtomicLong lastClapTime = new AtomicLong(0);

    private Thread detectionThread;

    public ClapDetectionService(JarvisOrchestratorService orchestrator,
                                MacOsTtsAdapter ttsAdapter,
                                JarvisProperties properties,
                                Executor jarvisExecutor,
                                SimpMessagingTemplate messagingTemplate) {
        this.orchestrator = orchestrator;
        this.ttsAdapter = ttsAdapter;
        this.properties = properties;
        this.jarvisExecutor = jarvisExecutor;
        this.messagingTemplate = messagingTemplate;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Clap detection STARTED (threshold={}, maxGap={}ms)",
                    CLAP_RMS_THRESHOLD, MAX_CLAP_GAP_MS);
            broadcastStatus(true);
            detectionThread = new Thread(this::detectionLoop, "clap-detector");
            detectionThread.setDaemon(true);
            detectionThread.start();
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Clap detection STOPPED");
            broadcastStatus(false);
            if (detectionThread != null) {
                detectionThread.interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    // ── Core detection loop ───────────────────────────────────────────────────

    private void detectionLoop() {
        JarvisProperties.AudioConfig audioCfg = properties.getAudio();
        AudioFormat format = new AudioFormat(
                audioCfg.getSampleRate(),
                audioCfg.getSampleSize(),
                audioCfg.getChannels(),
                true, false);

        int bytesPerChunk = (int) (format.getFrameSize()
                * format.getSampleRate()
                * CHUNK_MS / 1000.0);

        TargetDataLine line = null;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                log.error("Microphone not supported for clap detection");
                return;
            }

            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            log.debug("Clap detector microphone open (chunk={}ms, {}bytes)",
                    CHUNK_MS, bytesPerChunk);

            byte[] buffer = new byte[bytesPerChunk];

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                int bytesRead = line.read(buffer, 0, buffer.length);
                if (bytesRead <= 0) continue;

                // Skip during cooldown
                if (cooldown.get()) continue;

                double rms = computeRms(buffer, bytesRead, audioCfg.getSampleSize());

                if (rms > CLAP_RMS_THRESHOLD) {
                    handleClapDetected(rms);
                }
            }
        } catch (LineUnavailableException e) {
            log.warn("Microphone unavailable for clap detection: {}", e.getMessage());
        } catch (Exception e) {
            if (running.get()) {
                log.error("Clap detection loop error", e);
            }
        } finally {
            if (line != null) {
                line.stop();
                line.close();
                log.debug("Clap detector microphone closed");
            }
        }
    }

    private void handleClapDetected(double rms) {
        long now = Instant.now().toEpochMilli();
        long sinceLastClap = now - lastClapTime.get();

        // Debounce: ignore if too close to previous clap (echo/ringing)
        if (sinceLastClap < MIN_CLAP_GAP_MS) return;

        lastClapTime.set(now);
        log.debug("Clap detected (RMS={:.3f}, sinceLastClap={}ms)", rms, sinceLastClap);

        long first = firstClapTime.get();

        if (first == 0 || (now - first) > MAX_CLAP_GAP_MS) {
            // First clap — set the timestamp window
            firstClapTime.set(now);
            log.info("👏 First clap detected — waiting for second clap...");

        } else {
            // Second clap within the window → TRIGGER!
            long gapMs = now - first;
            log.info("👏👏 Double clap detected! (gap={}ms) — Activating Jarvis 🔊", gapMs);

            firstClapTime.set(0);
            lastClapTime.set(0);

            // Enter cooldown to prevent re-triggering during voice capture
            cooldown.set(true);
            jarvisExecutor.execute(this::triggerVoiceCapture);
        }
    }

    private void triggerVoiceCapture() {
        try {
            // Notify dashboard
            broadcastClapEvent();

            // Short beep-like confirmation (non-blocking)
            ttsAdapter.speakAsync("Yes sir");

            // Small delay so TTS starts before mic reopens
            Thread.sleep(600);

            log.info("🎤 Starting voice capture after double clap trigger");
            long durationMs = properties.getCommandTimeout() * 1000L;
            orchestrator.processVoiceCommand(Math.min(durationMs, 7000L));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Voice capture after clap failed", e);
        } finally {
            // Release cooldown after voice capture completes
            try { Thread.sleep(500); } catch (InterruptedException ignored) { }
            cooldown.set(false);
            log.debug("Clap detector cooldown released — resuming detection");
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Computes the RMS energy of a raw PCM byte buffer, normalised to [0.0, 1.0].
     */
    private double computeRms(byte[] buffer, int length, int sampleSizeBits) {
        if (sampleSizeBits == 16) {
            long sum = 0;
            int samples = length / 2;
            for (int i = 0; i < length - 1; i += 2) {
                short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
                sum += (long) sample * sample;
            }
            double rms = Math.sqrt((double) sum / samples);
            return rms / Short.MAX_VALUE; // normalise to 0..1
        } else {
            // 8-bit PCM
            long sum = 0;
            for (int i = 0; i < length; i++) {
                int sample = (buffer[i] & 0xFF) - 128;
                sum += (long) sample * sample;
            }
            double rms = Math.sqrt((double) sum / length);
            return rms / 128.0;
        }
    }

    private void broadcastStatus(boolean active) {
        try {
            messagingTemplate.convertAndSend("/topic/jarvis",
                    Map.of("type", "CLAP_DETECTION_STATUS",
                           "active", active,
                           "message", active
                                   ? "Double-clap detection is now ACTIVE 👏👏"
                                   : "Double-clap detection stopped"));
        } catch (Exception e) {
            log.debug("WebSocket broadcast failed: {}", e.getMessage());
        }
    }

    private void broadcastClapEvent() {
        try {
            messagingTemplate.convertAndSend("/topic/jarvis",
                    Map.of("type", "CLAP_TRIGGERED",
                           "message", "👏👏 Double clap detected! Jarvis is listening..."));
        } catch (Exception e) {
            log.debug("WebSocket broadcast failed: {}", e.getMessage());
        }
    }
}
