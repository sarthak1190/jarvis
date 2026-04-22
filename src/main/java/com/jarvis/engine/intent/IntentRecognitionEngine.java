package com.jarvis.engine.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.config.JarvisProperties;
import com.jarvis.dto.IntentResult;
import com.jarvis.exception.JarvisException;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Intent Recognition Engine powered by OpenAI GPT.
 * Parses natural language commands into structured IntentResult objects
 * that the execution engine can act upon.
 *
 * Uses a carefully crafted system prompt to produce consistent JSON output
 * for all supported action types.
 */
@Service
public class IntentRecognitionEngine {

    private static final Logger log = LoggerFactory.getLogger(IntentRecognitionEngine.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final JarvisProperties properties;
    private final ObjectMapper objectMapper;
    private final com.jarvis.engine.memory.ConversationMemoryService memoryService;

    /**
     * System prompt that instructs the LLM to return structured intent JSON.
     * This is the core "brain" of Jarvis's understanding.
     */
    private static final String SYSTEM_PROMPT = """
            You are Jarvis, an advanced, context-aware AI assistant for macOS. 
            Your job is to interpret user voice commands and return structured JSON that can be executed by a command engine.
            
            CONVERSATION CONTEXT:
            You will be provided with the last few lines of our conversation memory. Use this to understand pronouns like "it", "that", "open it instead", "search for something else", etc.
            
            IMPORTANT: You must ONLY return valid JSON. No markdown, no explanation, no code fences.
            
            Supported action types:
            - OPEN_APP: Open an application. target = app name.
            - CLOSE_APP: Close an application. target = app name.
            - SYSTEM_INFO: Get system information. target = one of: battery, cpu, memory, disk, network, os_version, all.
            - CREATE_FILE: Create a file. target = file path. parameters.content = optional file content.
            - DELETE_FILE: Delete a file. target = file path.
            - READ_FILE: Read a file. target = file path.
            - SEARCH_FILES: Search for files. target = search query.
            - RUN_SCRIPT: Run an AppleScript. parameters.script = the AppleScript code.
            - SET_VOLUME: Set system volume. parameters.level = 0-100.
            - TOGGLE_DARK_MODE: Toggle dark mode. No target needed.
            - SCREENSHOT: Take a screenshot. target = optional save path.
            - OPEN_URL: Open a URL in browser. target = the URL.
            - SEND_NOTIFICATION: Show a notification. target = title. parameters.message = body.
            - GET_WEATHER: Get weather info. target = location (optional).
            - CALENDAR: Calendar operations. target = operation (list, create). parameters as needed.
            - MUSIC_CONTROL: Control music. target = one of: play, pause, next, previous, volume_up, volume_down.
            - CLIPBOARD: Clipboard operations. target = one of: copy, paste, get. parameters.text = text to copy.
            - SLEEP_DISPLAY: Put display to sleep.
            - LOCK_SCREEN: Lock the screen.
            - EMPTY_TRASH: Empty the trash.
            - TOGGLE_WIFI: Toggle WiFi on/off.
            - CONVERSATION: General conversation/question that doesn't require system action. Put your response in parameters.response.
            - UNKNOWN: If you cannot determine the intent.
            
            For COMPOUND commands (multiple actions in one sentence), set "compound": true and include "subIntents" array.
            
            Response format for single command:
            {
              "action": "ACTION_TYPE",
              "target": "target_value",
              "parameters": {"key": "value"},
              "confidence": 0.95,
              "originalText": "the original command",
              "compound": false
            }
            
            Response format for compound command:
            {
              "action": "COMPOUND",
              "originalText": "the original command",
              "compound": true,
              "confidence": 0.9,
              "subIntents": [
                {"action": "OPEN_APP", "target": "Safari", "confidence": 0.95},
                {"action": "SYSTEM_INFO", "target": "battery", "confidence": 0.95}
              ]
            }
            
            Be smart about interpreting natural language. For example:
            - "What time is it?" → CONVERSATION with the current time context
            - "Open Chrome" → OPEN_APP with target "Google Chrome"
            - "How's my battery?" → SYSTEM_INFO with target "battery"
            - "Make a new folder called projects on desktop" → CREATE_FILE with appropriate path
            - "Play some music" → MUSIC_CONTROL with target "play"
            - "Open Safari and check battery" → COMPOUND with sub-intents
            """;

    public IntentRecognitionEngine(OkHttpClient httpClient,
                                   JarvisProperties properties,
                                   ObjectMapper objectMapper,
                                   com.jarvis.engine.memory.ConversationMemoryService memoryService) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.memoryService = memoryService;
    }

    /**
     * Recognize the intent from a natural language text command.
     *
     * @param text the transcribed voice command
     * @return structured IntentResult
     */
    public IntentResult recognize(String text) {
        log.info("Recognizing intent for: '{}'", text);

        JarvisProperties.OpenAiConfig openaiConfig = properties.getOpenai();

        try {
            String requestJson = buildChatRequest(text, openaiConfig);

            Request request = new Request.Builder()
                    .url(openaiConfig.getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + openaiConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestJson, JSON_MEDIA_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("OpenAI API error: {} - {}", response.code(), errorBody);
                    throw new JarvisException("OpenAI API call failed: " + response.code());
                }

                String responseBody = response.body().string();
                return parseIntentResponse(responseBody, text);
            }

        } catch (IOException e) {
            log.error("Failed to call OpenAI API for intent recognition", e);
            throw new JarvisException("Intent recognition failed", e);
        }
    }

    /**
     * Build the OpenAI Chat Completions API request body.
     */
    private String buildChatRequest(String userText, JarvisProperties.OpenAiConfig config)
            throws JsonProcessingException {

        String contextString = "RECENT CONVERSATION HISTORY:\\n" + memoryService.getFormattedContext();
        
        Map<String, Object> requestMap = Map.of(
                "model", config.getModel(),
                "max_tokens", config.getMaxTokens(),
                "temperature", config.getTemperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "system", "content", contextString),
                        Map.of("role", "user", "content", userText)
                ),
                "response_format", Map.of("type", "json_object")
        );

        return objectMapper.writeValueAsString(requestMap);
    }

    /**
     * Parse the OpenAI response into an IntentResult.
     */
    private IntentResult parseIntentResponse(String responseBody, String originalText) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");

            if (choices == null || choices.isEmpty()) {
                log.error("No choices in OpenAI response");
                return fallbackIntent(originalText);
            }

            String content = choices.get(0).get("message").get("content").asText();
            log.debug("LLM response content: {}", content);

            // Clean the content (remove markdown fences if present)
            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replaceAll("^```\\w*\\n?", "").replaceAll("\\n?```$", "").trim();
            }

            IntentResult intent = objectMapper.readValue(content, IntentResult.class);
            intent.setOriginalText(originalText);

            log.info("Intent recognized: action={}, target={}, confidence={}",
                    intent.getAction(), intent.getTarget(), intent.getConfidence());

            return intent;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse intent JSON from LLM response", e);
            return fallbackIntent(originalText);
        }
    }

    /**
     * Fallback intent when parsing fails.
     */
    private IntentResult fallbackIntent(String originalText) {
        return IntentResult.builder()
                .action("CONVERSATION")
                .originalText(originalText)
                .confidence(0.5)
                .parameters(Map.of("response",
                        "I understood your command but had trouble processing it. Could you rephrase?"))
                .build();
    }
}
