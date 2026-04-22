package com.jarvis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents the result of executing a command on the system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommandResult {

    /** Whether the command executed successfully */
    private boolean success;

    /** The output from the command execution */
    private String output;

    /** Error message if execution failed */
    private String error;

    /** The original action that was executed */
    private String action;

    /** Execution duration in milliseconds */
    private long executionTimeMs;

    /** Timestamp of execution */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static CommandResult success(String output, String action) {
        return CommandResult.builder()
                .success(true)
                .output(output)
                .action(action)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static CommandResult failure(String error, String action) {
        return CommandResult.builder()
                .success(false)
                .error(error)
                .action(action)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
