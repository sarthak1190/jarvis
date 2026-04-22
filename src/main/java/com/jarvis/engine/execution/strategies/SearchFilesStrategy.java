package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

/**
 * Strategy for searching files on macOS using Spotlight (mdfind).
 */
@Component
public class SearchFilesStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public SearchFilesStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "SEARCH_FILES";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String query = intent.getTarget();
        // Use mdfind (Spotlight) for fast file searching
        String command = String.format("mdfind -name \"%s\" | head -20", query);
        CommandResult result = commandAdapter.execute(command);

        if (result.isSuccess() && result.getOutput() != null && !result.getOutput().isEmpty()) {
            String[] files = result.getOutput().split("\n");
            result.setOutput(String.format("Found %d files matching '%s':\n%s",
                    files.length, query, result.getOutput()));
        } else if (result.isSuccess()) {
            result.setOutput("No files found matching: " + query);
        }

        return result;
    }
}
