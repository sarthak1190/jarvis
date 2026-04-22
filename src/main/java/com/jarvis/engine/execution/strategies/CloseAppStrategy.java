package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Strategy for closing applications on macOS.
 * Uses osascript to gracefully quit applications.
 */
@Component
public class CloseAppStrategy implements CommandStrategy {

    private static final Logger log = LoggerFactory.getLogger(CloseAppStrategy.class);
    private final MacOsCommandAdapter commandAdapter;

    public CloseAppStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "CLOSE_APP";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String appName = intent.getTarget();
        log.info("Closing application: {}", appName);

        String script = String.format("tell application \"%s\" to quit", appName);
        CommandResult result = commandAdapter.executeAppleScript(script);

        if (result.isSuccess()) {
            result.setOutput(appName + " has been closed.");
        }

        return result;
    }
}
