package com.jarvis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Central configuration properties for all Jarvis subsystems.
 * Loaded from application.yml under the 'jarvis' prefix.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jarvis")
public class JarvisProperties {

    private String wakeWord = "hey jarvis";
    private boolean continuousListening = false;
    private int commandTimeout = 30;

    private OpenAiConfig openai = new OpenAiConfig();
    private WhisperConfig whisper = new WhisperConfig();
    private TtsConfig tts = new TtsConfig();
    private AudioConfig audio = new AudioConfig();
    private ExecutionConfig execution = new ExecutionConfig();

    @Data
    public static class OpenAiConfig {
        private String apiKey;
        private String model = "gpt-4o";
        private String baseUrl = "https://api.openai.com/v1";
        private int maxTokens = 1024;
        private double temperature = 0.3;
    }

    @Data
    public static class WhisperConfig {
        private String mode = "api";
        private String apiUrl = "https://api.openai.com/v1/audio/transcriptions";
        private String model = "whisper-1";
        private String language = "en";
        private String localBinaryPath = "/usr/local/bin/whisper";
    }

    @Data
    public static class TtsConfig {
        private String engine = "macos_say";
        private String voice = "Samantha";
        private int rate = 200;
    }

    @Data
    public static class AudioConfig {
        private int sampleRate = 16000;
        private int sampleSize = 16;
        private int channels = 1;
        private String format = "WAV";
        private double silenceThreshold = 0.02;
        private int silenceDuration = 2000;
        private String tempDir;
    }

    @Data
    public static class ExecutionConfig {
        private boolean sandboxed = true;
        private List<String> allowedPrefixes;
        private List<String> blockedCommands;
    }
}
