package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

/**
 * Strategy for system control actions: sleep display, lock screen, empty trash, toggle WiFi.
 */
@Component("sleepDisplayStrategy")
public class SystemControlStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public SystemControlStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "SLEEP_DISPLAY";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        CommandResult result = commandAdapter.execute("pmset displaysleepnow");
        if (result.isSuccess()) {
            result.setOutput("Display is going to sleep.");
        }
        return result;
    }
}
