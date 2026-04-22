package com.jarvis.adapter.os;

import com.jarvis.config.JarvisProperties;
import com.jarvis.dto.CommandResult;
import com.jarvis.exception.JarvisException;
import com.jarvis.exception.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * macOS command execution adapter.
 * Provides sandboxed execution with security whitelisting/blacklisting.
 * Uses ProcessBuilder for system-level command execution.
 */
@Component
public class MacOsCommandAdapter {

    private static final Logger log = LoggerFactory.getLogger(MacOsCommandAdapter.class);

    private final JarvisProperties properties;

    public MacOsCommandAdapter(JarvisProperties properties) {
        this.properties = properties;
    }

    /**
     * Execute a shell command on macOS with security validation.
     *
     * @param command the command to execute
     * @return CommandResult with output and status
     */
    public CommandResult execute(String command) {
        long startTime = System.currentTimeMillis();

        log.info("Executing command: '{}'", command);

        // Security validation
        validateCommand(command);

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/zsh", "-c", command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            boolean completed = process.waitFor(
                    properties.getCommandTimeout(), TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                log.error("Command timed out after {}s: '{}'", properties.getCommandTimeout(), command);
                return CommandResult.builder()
                        .success(false)
                        .error("Command timed out after " + properties.getCommandTimeout() + " seconds")
                        .action(command)
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.exitValue();

            long duration = System.currentTimeMillis() - startTime;

            if (exitCode == 0) {
                log.info("Command succeeded in {}ms (output length: {})", duration, output.length());
                return CommandResult.builder()
                        .success(true)
                        .output(output)
                        .action(command)
                        .executionTimeMs(duration)
                        .build();
            } else {
                log.warn("Command exited with code {}: '{}'", exitCode, command);
                return CommandResult.builder()
                        .success(false)
                        .output(output)
                        .error("Command exited with code " + exitCode)
                        .action(command)
                        .executionTimeMs(duration)
                        .build();
            }

        } catch (IOException e) {
            log.error("Failed to execute command: '{}'", command, e);
            return CommandResult.builder()
                    .success(false)
                    .error("Execution failed: " + e.getMessage())
                    .action(command)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JarvisException("Command execution was interrupted", e);
        }
    }

    /**
     * Execute an AppleScript command.
     *
     * @param script the AppleScript to execute
     * @return CommandResult with output and status
     */
    public CommandResult executeAppleScript(String script) {
        String command = "osascript -e '" + script.replace("'", "'\\''") + "'";
        return execute(command);
    }

    /**
     * Validate a command against security whitelist/blacklist.
     *
     * @throws SecurityException if the command is blocked
     */
    private void validateCommand(String command) {
        JarvisProperties.ExecutionConfig execConfig = properties.getExecution();

        if (!execConfig.isSandboxed()) {
            log.warn("Sandbox mode is DISABLED — all commands are allowed");
            return;
        }

        String trimmedCommand = command.trim().toLowerCase();

        // Check blocked commands
        List<String> blocked = execConfig.getBlockedCommands();
        if (blocked != null) {
            for (String blockedCmd : blocked) {
                if (trimmedCommand.contains(blockedCmd.toLowerCase())) {
                    log.error("BLOCKED command detected: '{}' (matches: '{}')", command, blockedCmd);
                    throw new SecurityException("Command is blocked for security reasons: " + blockedCmd);
                }
            }
        }

        // Check allowed prefixes
        List<String> allowed = execConfig.getAllowedPrefixes();
        if (allowed != null && !allowed.isEmpty()) {
            boolean isAllowed = allowed.stream()
                    .anyMatch(prefix -> trimmedCommand.startsWith(prefix.toLowerCase()));

            if (!isAllowed) {
                log.error("Command not in whitelist: '{}'", command);
                throw new SecurityException(
                        "Command not in allowed whitelist. Allowed prefixes: " + String.join(", ", allowed));
            }
        }

        log.debug("Command passed security validation");
    }
}
