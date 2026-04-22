package com.jarvis.adapter.speech;

import com.jarvis.config.JarvisProperties;
import com.jarvis.exception.JarvisException;
import okhttp3.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/**
 * Speech-to-Text adapter using OpenAI Whisper API.
 * Converts audio files to text transcription.
 */
@Component
public class WhisperSpeechAdapter {

    private static final Logger log = LoggerFactory.getLogger(WhisperSpeechAdapter.class);

    private final OkHttpClient httpClient;
    private final JarvisProperties properties;
    private final ObjectMapper objectMapper;

    public WhisperSpeechAdapter(OkHttpClient httpClient,
                                JarvisProperties properties,
                                ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Transcribe an audio file to text using Whisper.
     *
     * @param audioFile the audio file to transcribe
     * @return the transcribed text
     * @throws JarvisException if transcription fails
     */
    public String transcribe(File audioFile) {
        log.info("Transcribing audio file: {} ({} bytes)", audioFile.getName(), audioFile.length());

        JarvisProperties.WhisperConfig whisperConfig = properties.getWhisper();

        if ("local".equalsIgnoreCase(whisperConfig.getMode())) {
            return transcribeLocal(audioFile);
        }

        return transcribeViaApi(audioFile);
    }

    /**
     * Transcribe via OpenAI Whisper API.
     */
    private String transcribeViaApi(File audioFile) {
        JarvisProperties.WhisperConfig whisperConfig = properties.getWhisper();

        RequestBody fileBody = RequestBody.create(audioFile, MediaType.parse("audio/wav"));

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.getName(), fileBody)
                .addFormDataPart("model", whisperConfig.getModel())
                .addFormDataPart("language", whisperConfig.getLanguage())
                .addFormDataPart("response_format", "json")
                .build();

        Request request = new Request.Builder()
                .url(whisperConfig.getApiUrl())
                .header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                log.error("Whisper API error: {} - {}", response.code(), errorBody);
                throw new JarvisException("Whisper API transcription failed: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            String text = jsonNode.get("text").asText().trim();

            log.info("Transcription result: '{}'", text);
            return text;

        } catch (IOException e) {
            log.error("Failed to call Whisper API", e);
            throw new JarvisException("Failed to transcribe audio via Whisper API", e);
        }
    }

    /**
     * Transcribe using local Whisper binary (e.g., whisper.cpp).
     */
    private String transcribeLocal(File audioFile) {
        JarvisProperties.WhisperConfig whisperConfig = properties.getWhisper();
        String binaryPath = whisperConfig.getLocalBinaryPath();
        File outputDir = audioFile.getParentFile();

        try {
            // Resolve ~/.cache/whisper so Whisper never tries to download the model
            String modelDir = System.getProperty("user.home") + "/.cache/whisper";

            // Using standard Python whisper CLI:
            // whisper filename.wav --model base.en --model_dir ~/.cache/whisper --output_format txt --output_dir /dir/
            ProcessBuilder pb = new ProcessBuilder(
                    binaryPath,
                    audioFile.getAbsolutePath(),
                    "--model", whisperConfig.getModel(),
                    "--model_dir", modelDir,
                    "--language", whisperConfig.getLanguage(),
                    "--output_format", "txt",
                    "--output_dir", outputDir.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            
            // Inject Homebrew paths so Whisper can find ffmpeg (required for audio decoding)
            String existingPath = pb.environment().getOrDefault("PATH", "/usr/bin:/bin");
            pb.environment().put("PATH", "/opt/homebrew/bin:/usr/local/bin:" + existingPath);

            log.info("Running local Whisper: {} {} --model {} ...", binaryPath, audioFile.getName(), whisperConfig.getModel());
            Process process = pb.start();
            
            // Read output logs to prevent hanging and help debug
            String outputLogs = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("Local Whisper failed with exit code {}: {}", exitCode, outputLogs);
                throw new JarvisException("Local Whisper transcription failed");
            }

            // Read the generated text file
            // Whisper creates a file with the exact same base name + .txt
            String baseName = audioFile.getName();
            int dotIndex = baseName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = baseName.substring(0, dotIndex);
            }
            File textFile = new File(outputDir, baseName + ".txt");
            
            if (!textFile.exists()) {
                // Whisper sometimes keeps the extension: "filename.wav.txt"
                textFile = new File(outputDir, audioFile.getName() + ".txt");
            }
            
            if (!textFile.exists()) {
                log.error("Whisper did not generate a output block: {}", outputLogs);
                throw new JarvisException("Local Whisper did not create a target text file.");
            }

            String transcribedText = java.nio.file.Files.readString(textFile.toPath()).trim();
            textFile.delete(); // cleanup

            log.info("Local transcription result: '{}'", transcribedText);
            return transcribedText;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to run local Whisper", e);
            throw new JarvisException("Failed to transcribe audio locally. Make sure whisper is installed via pip.", e);
        }
    }
}
