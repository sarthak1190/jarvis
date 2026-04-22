package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Strategy for clipboard operations (copy/paste/get).
 */
@Component
public class ClipboardStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public ClipboardStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "CLIPBOARD";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String operation = intent.getTarget() != null ? intent.getTarget().toLowerCase() : "get";
        Map<String, String> params = intent.getParameters();

        return switch (operation) {
            case "copy" -> {
                String text = params != null ? params.getOrDefault("text", "") : "";
                yield commandAdapter.execute(String.format("echo '%s' | pbcopy", text.replace("'", "'\\''")));
            }
            case "paste", "get" -> {
                CommandResult result = commandAdapter.execute("pbpaste");
                if (result.isSuccess()) {
                    result.setOutput("Clipboard contents: " + result.getOutput());
                }
                yield result;
            }
            default -> CommandResult.failure("Unknown clipboard operation: " + operation, "CLIPBOARD");
        };
    }
}
