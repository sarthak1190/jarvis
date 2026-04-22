package com.jarvis.service;

import com.jarvis.adapter.speech.AudioCaptureAdapter;
import com.jarvis.adapter.speech.WhisperSpeechAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.dto.JarvisResponse;
import com.jarvis.engine.execution.CommandExecutionEngine;
import com.jarvis.engine.intent.IntentRecognitionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Central Orchestrator — The "brain" of Jarvis.
 *
 * Coordinates the entire pipeline:
 * 1. Voice capture → audio file
 * 2. Audio → text (Whisper STT)
 * 3. Text → structured intent (LLM)
 * 4. Intent → command execution (Strategy Pattern)
 * 5. Results → response text generation
 * 6. Response → voice output (TTS)
 */
@Service
public class JarvisOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(JarvisOrchestratorService.class);

    private final AudioCaptureAdapter audioCaptureAdapter;
    private final WhisperSpeechAdapter whisperAdapter;
    private final IntentRecognitionEngine intentEngine;
    private final CommandExecutionEngine executionEngine;
    private final ResponseGeneratorService responseGenerator;
    private final com.jarvis.engine.memory.ConversationMemoryService memoryService;
    private final com.jarvis.engine.decision.ProactiveDecisionEngine decisionEngine;

    public JarvisOrchestratorService(AudioCaptureAdapter audioCaptureAdapter,
                                     WhisperSpeechAdapter whisperAdapter,
                                     IntentRecognitionEngine intentEngine,
                                     CommandExecutionEngine executionEngine,
                                     ResponseGeneratorService responseGenerator,
                                     com.jarvis.engine.memory.ConversationMemoryService memoryService,
                                     com.jarvis.engine.decision.ProactiveDecisionEngine decisionEngine) {
        this.audioCaptureAdapter = audioCaptureAdapter;
        this.whisperAdapter = whisperAdapter;
        this.intentEngine = intentEngine;
        this.executionEngine = executionEngine;
        this.responseGenerator = responseGenerator;
        this.memoryService = memoryService;
        this.decisionEngine = decisionEngine;
    }

    /**
     * Process a voice command — full pipeline from microphone to spoken response.
     *
     * @param recordingDurationMs how long to record (0 = auto-detect silence)
     * @return complete pipeline response
     */
    public JarvisResponse processVoiceCommand(long recordingDurationMs) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        log.info("[{}] Starting voice command pipeline", requestId);

        try {
            // Step 1: Capture audio
            log.info("[{}] Step 1: Capturing audio...", requestId);
            File audioFile = audioCaptureAdapter.recordAudio(recordingDurationMs);

            // Step 2: Transcribe
            log.info("[{}] Step 2: Transcribing audio...", requestId);
            String transcribedText = whisperAdapter.transcribe(audioFile);

            // Step 3-6: Process as text command
            JarvisResponse response = processTextCommand(transcribedText, true, null);
            response.setRequestId(requestId);
            response.setTotalTimeMs(System.currentTimeMillis() - startTime);

            // Cleanup audio file
            if (audioFile.exists()) {
                audioFile.delete();
            }

            return response;

        } catch (Exception e) {
            log.error("[{}] Voice command pipeline failed: {}", requestId, e.getMessage(), e);
            return JarvisResponse.builder()
                    .requestId(requestId)
                    .success(false)
                    .responseText("I'm sorry, I encountered an error processing your voice command: " + e.getMessage())
                    .totalTimeMs(System.currentTimeMillis() - startTime)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Process a text command — skips audio capture/transcription.
     *
     * @param text          the command text
     * @param speakResponse whether to speak the response via TTS
     * @param voice         optional TTS voice override
     * @return complete pipeline response
     */
    public JarvisResponse processTextCommand(String text, boolean speakResponse, String voice) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        log.info("[{}] Processing text command: '{}'", requestId, text);
        
        // Log to short-term conversational memory
        memoryService.addUserCommand(text);

        try {
            // Step 1: Intent recognition
            log.info("[{}] Step 1: Recognizing intent...", requestId);
            IntentResult intent = intentEngine.recognize(text);

            // Pre-execution Decision Engine evaluation
            if (!decisionEngine.evaluateBeforeExecution(intent)) {
                log.info("[{}] Decision Engine intercepted the command. Aborting normal execution.", requestId);
                return generateInterceptedResponse(requestId, intent, text, startTime);
            }

            // Step 2: Execute command(s)
            log.info("[{}] Step 2: Executing action '{}'...", requestId, intent.getAction());
            List<CommandResult> results = executionEngine.executeCompound(intent);
            
            // Post-execution Decision Engine evaluation
            decisionEngine.evaluateAfterExecution(intent, results);

            // Step 3: Generate response
            log.info("[{}] Step 3: Generating response...", requestId);
            String responseText = responseGenerator.generateResponse(intent, results);
            
            // Log assistant's response to short-term memory
            memoryService.addJarvisResponse(responseText);

            // Step 4: Speak response
            if (speakResponse) {
                log.info("[{}] Step 4: Speaking response...", requestId);
                responseGenerator.speakResponse(responseText, voice);
            }

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("[{}] Pipeline completed in {}ms", requestId, totalTime);

            return JarvisResponse.builder()
                    .requestId(requestId)
                    .transcribedText(text)
                    .intent(intent)
                    .results(results)
                    .responseText(responseText)
                    .success(results.stream().allMatch(CommandResult::isSuccess))
                    .totalTimeMs(totalTime)
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("[{}] Text command pipeline failed: {}", requestId, e.getMessage(), e);
            String errorMsg = "I encountered an error: " + e.getMessage();

            if (speakResponse) {
                responseGenerator.speakResponse(errorMsg, voice);
            }

            return JarvisResponse.builder()
                    .requestId(requestId)
                    .transcribedText(text)
                    .success(false)
                    .responseText(errorMsg)
                    .totalTimeMs(System.currentTimeMillis() - startTime)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Process an uploaded audio file — skips microphone capture.
     *
     * @param audioFile the audio file to process
     * @return complete pipeline response
     */
    public JarvisResponse processAudioFile(File audioFile) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        log.info("[{}] Processing audio file: {}", requestId, audioFile.getName());

        try {
            String transcribedText = whisperAdapter.transcribe(audioFile);
            JarvisResponse response = processTextCommand(transcribedText, true, null);
            response.setRequestId(requestId);
            response.setTotalTimeMs(System.currentTimeMillis() - startTime);
            return response;

        } catch (Exception e) {
            log.error("[{}] Audio file pipeline failed: {}", requestId, e.getMessage(), e);
            return JarvisResponse.builder()
                    .requestId(requestId)
                    .success(false)
                    .responseText("Failed to process audio file: " + e.getMessage())
                    .totalTimeMs(System.currentTimeMillis() - startTime)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    private JarvisResponse generateInterceptedResponse(String requestId, IntentResult intent, String text, long startTime) {
        String msg = "I have evaluated the context and handled this proactively, sir.";
        return JarvisResponse.builder()
                .requestId(requestId)
                .transcribedText(text)
                .intent(intent)
                .success(true)
                .responseText(msg)
                .totalTimeMs(System.currentTimeMillis() - startTime)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
