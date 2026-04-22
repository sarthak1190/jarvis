package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Strategy for sending macOS notifications via AppleScript.
 */
@Component
public class SendNotificationStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public SendNotificationStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "SEND_NOTIFICATION";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String title = intent.getTarget() != null ? intent.getTarget() : "Jarvis";
        Map<String, String> params = intent.getParameters();
        String message = params != null ? params.getOrDefault("message", "") : "";

        String script = String.format(
                "display notification \"%s\" with title \"%s\" sound name \"Glass\"",
                message.replace("\"", "\\\""),
                title.replace("\"", "\\\"")
        );

        CommandResult result = commandAdapter.executeAppleScript(script);

        if (result.isSuccess()) {
            result.setOutput("Notification sent: " + title);
        }

        return result;
    }
}
