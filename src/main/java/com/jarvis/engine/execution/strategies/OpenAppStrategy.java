package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Strategy for opening applications on macOS.
 * Uses the 'open -a' command to launch applications.
 */
@Component
public class OpenAppStrategy implements CommandStrategy {

    private static final Logger log = LoggerFactory.getLogger(OpenAppStrategy.class);
    private final MacOsCommandAdapter commandAdapter;

    public OpenAppStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "OPEN_APP";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String appName = intent.getTarget();
        log.info("Opening application: {}", appName);

        String command = String.format("open -a \"%s\"", appName);
        CommandResult result = commandAdapter.execute(command);

        if (result.isSuccess()) {
            result.setOutput(appName + " has been opened successfully.");
        }

        return result;
    }
}
