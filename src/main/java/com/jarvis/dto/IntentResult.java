package com.jarvis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a parsed intent from user voice/text input.
 * The LLM returns this structured format to drive command execution.
 *
 * Example:
 * {
 *   "action": "OPEN_APP",
 *   "target": "Google Chrome",
 *   "parameters": {"url": "https://google.com"},
 *   "confidence": 0.95,
 *   "originalText": "Open Chrome and go to Google"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntentResult {

    /** The classified action type (e.g., OPEN_APP, SYSTEM_INFO, CREATE_FILE) */
    private String action;

    /** The primary target of the action (e.g., app name, file path) */
    private String target;

    /** Additional parameters for the action */
    private Map<String, String> parameters;

    /** Confidence score from the LLM (0.0 - 1.0) */
    private double confidence;

    /** The original transcribed text */
    private String originalText;

    /** Whether this intent contains multiple sub-commands */
    private boolean compound;

    /** Sub-intents if this is a compound command */
    private java.util.List<IntentResult> subIntents;
}
