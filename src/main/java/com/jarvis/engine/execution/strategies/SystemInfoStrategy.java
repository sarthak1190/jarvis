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
 * Strategy for retrieving macOS system information.
 * Supports battery, CPU, memory, disk, network, and OS version queries.
 */
@Component
public class SystemInfoStrategy implements CommandStrategy {

    private static final Logger log = LoggerFactory.getLogger(SystemInfoStrategy.class);
    private final MacOsCommandAdapter commandAdapter;

    /**
     * Mapping of info targets to their respective shell commands.
     */
    private static final Map<String, String> INFO_COMMANDS = Map.of(
            "battery", "pmset -g batt",
            "cpu", "sysctl -n machdep.cpu.brand_string && echo '---' && top -l 1 -n 0 | grep 'CPU usage'",
            "memory", "sysctl -n hw.memsize && echo '---' && vm_stat | head -10",
            "disk", "diskutil info / | grep -E '(Total|Available|Used)'",
            "network", "networksetup -getinfo Wi-Fi",
            "os_version", "sw_vers",
            "all", "echo '=== OS ===' && sw_vers && echo '\\n=== Battery ===' && pmset -g batt && echo '\\n=== CPU ===' && sysctl -n machdep.cpu.brand_string && echo '\\n=== Memory ===' && sysctl -n hw.memsize && echo '\\n=== Disk ===' && diskutil info / | grep -E '(Total|Available|Used)'"
    );

    public SystemInfoStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "SYSTEM_INFO";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String target = intent.getTarget() != null ? intent.getTarget().toLowerCase() : "all";
        log.info("Getting system info: {}", target);

        String command = INFO_COMMANDS.getOrDefault(target, INFO_COMMANDS.get("all"));
        CommandResult result = commandAdapter.execute(command);

        if (result.isSuccess()) {
            // Format the output for human readability
            result.setOutput(formatSystemInfo(target, result.getOutput()));
        }

        return result;
    }

    /**
     * Format raw system info output into human-friendly text.
     */
    private String formatSystemInfo(String target, String rawOutput) {
        return switch (target) {
            case "battery" -> parseBatteryInfo(rawOutput);
            case "os_version" -> "macOS " + rawOutput.replaceAll("\\s+", " ").trim();
            default -> rawOutput;
        };
    }

    private String parseBatteryInfo(String raw) {
        // Extract percentage from pmset output
        if (raw.contains("%")) {
            int idx = raw.indexOf('%');
            int start = idx - 1;
            while (start > 0 && Character.isDigit(raw.charAt(start - 1))) {
                start--;
            }
            String percentage = raw.substring(start, idx + 1);

            boolean charging = raw.toLowerCase().contains("charging") && !raw.toLowerCase().contains("not charging");
            String status = charging ? "charging" : "on battery";

            return String.format("Battery is at %s, currently %s.", percentage, status);
        }
        return raw;
    }
}
