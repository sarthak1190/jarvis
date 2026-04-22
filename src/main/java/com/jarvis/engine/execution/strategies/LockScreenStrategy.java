package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

/**
 * Strategy for locking the macOS screen.
 */
@Component
public class LockScreenStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public LockScreenStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "LOCK_SCREEN";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        CommandResult result = commandAdapter.executeAppleScript(
                "tell application \"System Events\" to keystroke \"q\" using {command down, control down}");
        if (result.isSuccess()) {
            result.setOutput("Screen has been locked.");
        }
        return result;
    }
}
