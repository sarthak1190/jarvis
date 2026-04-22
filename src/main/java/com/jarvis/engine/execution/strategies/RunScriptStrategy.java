package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

/**
 * Strategy for running arbitrary AppleScript commands.
 */
@Component
public class RunScriptStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public RunScriptStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "RUN_SCRIPT";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String script = intent.getParameters() != null
                ? intent.getParameters().getOrDefault("script", "")
                : "";

        if (script.isBlank()) {
            return CommandResult.failure("No script provided", "RUN_SCRIPT");
        }

        return commandAdapter.executeAppleScript(script);
    }
}
