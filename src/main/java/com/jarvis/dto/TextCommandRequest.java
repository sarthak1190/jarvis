package com.jarvis.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for text-based command input (REST API).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextCommandRequest {

    @NotBlank(message = "Command text must not be blank")
    private String text;

    /** Optional: override the response voice */
    private String voice;

    /** Whether to speak the response via TTS */
    private boolean speakResponse = true;
}
