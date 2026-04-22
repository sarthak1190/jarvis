package com.jarvis.service;

import com.jarvis.adapter.tts.MacOsTtsAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates human-friendly text responses from command execution results.
 * Also handles speaking the response via TTS.
 */
@Service
public class ResponseGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(ResponseGeneratorService.class);

    private final MacOsTtsAdapter ttsAdapter;

    public ResponseGeneratorService(MacOsTtsAdapter ttsAdapter) {
        this.ttsAdapter = ttsAdapter;
    }

    /**
     * Generate a human-friendly response text from execution results.
     *
     * @param intent  the original intent
     * @param results the execution results
     * @return human-readable response text
     */
    public String generateResponse(IntentResult intent, List<CommandResult> results) {
        if (results == null || results.isEmpty()) {
            return "I processed your request, but there were no results.";
        }

        if (results.size() == 1) {
            return generateSingleResponse(results.get(0));
        }

        // Multiple results from compound command
        StringBuilder sb = new StringBuilder("Here are the results:\n");
        for (int i = 0; i < results.size(); i++) {
            CommandResult result = results.get(i);
            sb.append(String.format("%d. %s\n", i + 1, generateSingleResponse(result)));
        }
        return sb.toString().trim();
    }

    /**
     * Generate response for a single command result.
     */
    private String generateSingleResponse(CommandResult result) {
        if (result.isSuccess()) {
            return result.getOutput() != null && !result.getOutput().isBlank()
                    ? result.getOutput()
                    : "Done. The command was executed successfully.";
        } else {
            return "I encountered an issue: " +
                    (result.getError() != null ? result.getError() : "Unknown error");
        }
    }

    /**
     * Speak the response text via TTS.
     *
     * @param text  the text to speak
     * @param voice optional voice override
     */
    public void speakResponse(String text, String voice) {
        try {
            // Truncate very long outputs for speech
            String speakableText = text.length() > 500
                    ? text.substring(0, 497) + "... and more."
                    : text;

            ttsAdapter.speak(speakableText, voice);
        } catch (Exception e) {
            log.error("Failed to speak response: {}", e.getMessage());
        }
    }

    /**
     * Speak the response text asynchronously.
     */
    public void speakResponseAsync(String text) {
        ttsAdapter.speakAsync(text);
    }
}
