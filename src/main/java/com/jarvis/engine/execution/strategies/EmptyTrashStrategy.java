package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

/**
 * Strategy for emptying the macOS Trash via AppleScript.
 */
@Component
public class EmptyTrashStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public EmptyTrashStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "EMPTY_TRASH";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        CommandResult result = commandAdapter.executeAppleScript(
                "tell application \"Finder\" to empty trash");
        if (result.isSuccess()) {
            result.setOutput("Trash has been emptied.");
        }
        return result;
    }
}
