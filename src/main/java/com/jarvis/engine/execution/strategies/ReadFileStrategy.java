package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

/**
 * Strategy for reading file contents on macOS.
 */
@Component
public class ReadFileStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public ReadFileStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "READ_FILE";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String target = intent.getTarget().replace("~", System.getProperty("user.home"));
        String command = String.format("cat \"%s\"", target);
        return commandAdapter.execute(command);
    }
}
