package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

/**
 * Strategy for opening URLs in the default browser.
 */
@Component
public class OpenUrlStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public OpenUrlStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "OPEN_URL";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String url = intent.getTarget();

        // Ensure URL has a protocol
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        String command = String.format("open \"%s\"", url);
        CommandResult result = commandAdapter.execute(command);

        if (result.isSuccess()) {
            result.setOutput("Opened " + url + " in your browser.");
        }

        return result;
    }
}
