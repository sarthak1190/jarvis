package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Strategy for deleting files and directories on macOS.
 * Includes safety checks to prevent dangerous deletions.
 */
@Component
public class DeleteFileStrategy implements CommandStrategy {

    private static final Logger log = LoggerFactory.getLogger(DeleteFileStrategy.class);
    private final MacOsCommandAdapter commandAdapter;

    public DeleteFileStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "DELETE_FILE";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String target = intent.getTarget();
        log.info("Deleting: {}", target);

        String path = target.replace("~", System.getProperty("user.home"));

        // Safety: prevent deletion of critical paths
        if (isDangerousPath(path)) {
            return CommandResult.failure(
                    "Cannot delete critical system path: " + target, "DELETE_FILE");
        }

        // Check if path exists first
        CommandResult checkResult = commandAdapter.execute(
                String.format("ls -la \"%s\" 2>/dev/null", path));

        if (!checkResult.isSuccess() || checkResult.getOutput().isEmpty()) {
            return CommandResult.failure("Path does not exist: " + target, "DELETE_FILE");
        }

        // Use -r for directories, regular rm for files
        // Note: we don't use -f to ensure we get errors for permission issues
        String command = String.format(
                "if [ -d \"%s\" ]; then rm -r \"%s\"; else rm \"%s\"; fi", path, path, path);

        CommandResult result = commandAdapter.execute(command);

        if (result.isSuccess()) {
            result.setOutput("Successfully deleted: " + target);
        }

        return result;
    }

    /**
     * Check if a path is too dangerous to delete.
     */
    private boolean isDangerousPath(String path) {
        String normalized = path.replaceAll("/+$", "").toLowerCase();
        return normalized.equals("/") ||
               normalized.equals("/users") ||
               normalized.equals("/system") ||
               normalized.equals("/applications") ||
               normalized.equals("/library") ||
               normalized.equals(System.getProperty("user.home")) ||
               normalized.matches("/users/[^/]+") ||
               normalized.startsWith("/system") ||
               normalized.startsWith("/private/var");
    }
}
