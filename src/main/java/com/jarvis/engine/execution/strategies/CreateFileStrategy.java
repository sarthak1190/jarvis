package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Strategy for file system operations on macOS.
 * Supports creating files and directories with optional content.
 */
@Component
public class CreateFileStrategy implements CommandStrategy {

    private static final Logger log = LoggerFactory.getLogger(CreateFileStrategy.class);
    private final MacOsCommandAdapter commandAdapter;

    public CreateFileStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "CREATE_FILE";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String target = intent.getTarget();
        Map<String, String> params = intent.getParameters();

        log.info("Creating file/directory: {}", target);

        // Expand ~ to home directory
        String path = target.replace("~", System.getProperty("user.home"));

        // Determine if this is a directory (ends with /) or a file
        boolean isDirectory = path.endsWith("/") ||
                (params != null && "directory".equalsIgnoreCase(params.get("type")));

        String command;
        if (isDirectory) {
            command = String.format("mkdir -p \"%s\"", path);
        } else {
            // Create parent directories if needed, then create file
            String content = params != null ? params.getOrDefault("content", "") : "";
            if (content.isEmpty()) {
                command = String.format("mkdir -p \"$(dirname '%s')\" && touch \"%s\"", path, path);
            } else {
                command = String.format("mkdir -p \"$(dirname '%s')\" && echo '%s' > \"%s\"",
                        path, content.replace("'", "'\\''"), path);
            }
        }

        CommandResult result = commandAdapter.execute(command);

        if (result.isSuccess()) {
            String type = isDirectory ? "Directory" : "File";
            result.setOutput(String.format("%s created successfully at %s", type, target));
        }

        return result;
    }
}
