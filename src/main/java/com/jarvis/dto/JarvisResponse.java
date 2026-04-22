package com.jarvis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Complete response from the Jarvis pipeline.
 * Contains the full journey: transcription → intent → execution → response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JarvisResponse {

    /** Unique request identifier for tracing */
    private String requestId;

    /** The transcribed text from voice input */
    private String transcribedText;

    /** The parsed intent */
    private IntentResult intent;

    /** Results from command execution */
    private List<CommandResult> results;

    /** Human-friendly response text (spoken via TTS) */
    private String responseText;

    /** Whether the overall request was successful */
    private boolean success;

    /** Total pipeline processing time in milliseconds */
    private long totalTimeMs;

    /** Timestamp */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
