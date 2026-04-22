package com.jarvis.adapter.speech;

import com.jarvis.config.JarvisProperties;
import com.jarvis.exception.JarvisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Microphone capture adapter using Java Sound API.
 * Supports both manual stop and automatic silence detection.
 */
@Component
public class AudioCaptureAdapter {

    private static final Logger log = LoggerFactory.getLogger(AudioCaptureAdapter.class);

    private final JarvisProperties properties;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private TargetDataLine microphone;

    public AudioCaptureAdapter(JarvisProperties properties) {
        this.properties = properties;
    }

    /**
     * Get the audio format for recording.
     */
    public AudioFormat getAudioFormat() {
        JarvisProperties.AudioConfig audioConfig = properties.getAudio();
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                audioConfig.getSampleRate(),
                audioConfig.getSampleSize(),
                audioConfig.getChannels(),
                audioConfig.getChannels() * (audioConfig.getSampleSize() / 8),
                audioConfig.getSampleRate(),
                false
        );
    }

    /**
     * Record audio from the microphone until manually stopped or silence is detected.
     *
     * @param durationMs maximum recording duration in milliseconds (0 = unlimited until silence)
     * @return the recorded audio file
     */
    public File recordAudio(long durationMs) {
        log.info("Starting audio recording (max duration: {}ms)", durationMs);

        JarvisProperties.AudioConfig audioConfig = properties.getAudio();
        AudioFormat format = getAudioFormat();

        try {
            // Ensure temp directory exists
            Path tempDir = Path.of(audioConfig.getTempDir() != null
                    ? audioConfig.getTempDir()
                    : System.getProperty("java.io.tmpdir") + "/jarvis/audio");
            Files.createDirectories(tempDir);

            File audioFile = tempDir.resolve("recording_" + UUID.randomUUID() + ".wav").toFile();

            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                throw new JarvisException("Microphone not supported with the specified audio format");
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();
            recording.set(true);

            log.debug("Microphone opened, recording to: {}", audioFile.getAbsolutePath());

            // Record in a separate thread with silence detection
            Thread recordingThread = new Thread(() -> {
                try (AudioInputStream audioStream = new AudioInputStream(microphone)) {
                    AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, audioFile);
                } catch (IOException e) {
                    log.error("Error writing audio file", e);
                }
            }, "jarvis-audio-recorder");
            recordingThread.start();

            // Wait for the specified duration or until stopped
            if (durationMs > 0) {
                Thread.sleep(durationMs);
                stopRecording();
            } else {
                // Run silence detection
                detectSilenceAndStop(format, audioConfig);
            }

            recordingThread.join(5000);

            log.info("Recording completed: {} ({} bytes)", audioFile.getName(), audioFile.length());
            return audioFile;

        } catch (LineUnavailableException e) {
            throw new JarvisException("Microphone is unavailable. Check system permissions.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JarvisException("Recording interrupted", e);
        } catch (IOException e) {
            throw new JarvisException("Failed to create audio temp directory", e);
        }
    }

    /**
     * Monitor audio levels and stop recording when silence is detected.
     */
    private void detectSilenceAndStop(AudioFormat format, JarvisProperties.AudioConfig audioConfig) {
        double silenceThreshold = audioConfig.getSilenceThreshold();
        int silenceDurationMs = audioConfig.getSilenceDuration();

        int bufferSize = (int) (format.getSampleRate() * format.getFrameSize() * 0.1); // 100ms buffer
        byte[] buffer = new byte[bufferSize];
        long silenceStartTime = 0;
        boolean inSilence = false;

        // Wait for initial speech (max 10 seconds)
        boolean speechDetected = false;
        long waitStart = System.currentTimeMillis();

        while (recording.get()) {
            int bytesRead = microphone.read(buffer, 0, buffer.length);
            if (bytesRead <= 0) continue;

            double rms = calculateRMS(buffer, bytesRead);

            if (!speechDetected) {
                if (rms > silenceThreshold) {
                    speechDetected = true;
                    log.debug("Speech detected (RMS: {:.4f})", rms);
                } else if (System.currentTimeMillis() - waitStart > 10000) {
                    log.warn("No speech detected within 10 seconds, stopping");
                    break;
                }
                continue;
            }

            // After speech is detected, watch for silence
            if (rms < silenceThreshold) {
                if (!inSilence) {
                    inSilence = true;
                    silenceStartTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - silenceStartTime >= silenceDurationMs) {
                    log.debug("Silence detected for {}ms, stopping recording", silenceDurationMs);
                    break;
                }
            } else {
                inSilence = false;
            }
        }

        stopRecording();
    }

    /**
     * Calculate RMS (Root Mean Square) of audio buffer for volume level detection.
     */
    private double calculateRMS(byte[] buffer, int bytesRead) {
        long sum = 0;
        int samples = bytesRead / 2; // 16-bit samples

        for (int i = 0; i < bytesRead - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += (long) sample * sample;
        }

        return Math.sqrt((double) sum / samples) / Short.MAX_VALUE;
    }

    /**
     * Stop the current recording.
     */
    public void stopRecording() {
        if (recording.compareAndSet(true, false) && microphone != null) {
            microphone.stop();
            microphone.close();
            log.debug("Microphone stopped and closed");
        }
    }

    /**
     * Check if currently recording.
     */
    public boolean isRecording() {
        return recording.get();
    }
}
