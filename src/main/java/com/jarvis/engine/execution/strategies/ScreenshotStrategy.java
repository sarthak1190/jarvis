package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Strategy for taking screenshots on macOS.
 */
@Component
public class ScreenshotStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public ScreenshotStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "SCREENSHOT";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String target = intent.getTarget();

        if (target == null || target.isBlank()) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            target = System.getProperty("user.home") + "/Desktop/screenshot_" + timestamp + ".png";
        }

        String command = String.format("screencapture -x \"%s\"", target);
        CommandResult result = commandAdapter.execute(command);

        if (result.isSuccess()) {
            result.setOutput("Screenshot saved to: " + target);
        }

        return result;
    }
}
