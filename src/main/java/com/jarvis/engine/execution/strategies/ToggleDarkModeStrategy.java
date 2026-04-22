package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

/**
 * Strategy for toggling macOS Dark Mode via AppleScript.
 */
@Component
public class ToggleDarkModeStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public ToggleDarkModeStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "TOGGLE_DARK_MODE";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String script = "tell application \"System Events\" to tell appearance preferences to set dark mode to not dark mode";
        CommandResult result = commandAdapter.executeAppleScript(script);

        if (result.isSuccess()) {
            // Check current state
            CommandResult stateCheck = commandAdapter.executeAppleScript(
                    "tell application \"System Events\" to tell appearance preferences to get dark mode");
            boolean isDark = "true".equalsIgnoreCase(stateCheck.getOutput());
            result.setOutput("Dark mode is now " + (isDark ? "enabled" : "disabled") + ".");
        }

        return result;
    }
}
