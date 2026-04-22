package com.jarvis.engine.execution;

import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;

/**
 * Strategy interface for command execution.
 * Each action type has its own strategy implementation.
 * Follows the Strategy Pattern for extensible command handling.
 */
public interface CommandStrategy {

    /**
     * Get the action type this strategy handles.
     *
     * @return the action type string (e.g., "OPEN_APP")
     */
    String getActionType();

    /**
     * Execute the command based on the intent.
     *
     * @param intent the parsed intent with action details
     * @return the result of execution
     */
    CommandResult execute(IntentResult intent);
}
