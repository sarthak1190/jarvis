package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Strategy for setting system volume via AppleScript.
 */
@Component
public class SetVolumeStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public SetVolumeStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "SET_VOLUME";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        Map<String, String> params = intent.getParameters();
        int level = 50; // default

        if (params != null && params.containsKey("level")) {
            try {
                level = Integer.parseInt(params.get("level"));
                level = Math.max(0, Math.min(100, level)); // clamp 0-100
            } catch (NumberFormatException ignored) {
            }
        }

        String script = String.format("set volume output volume %d", level);
        CommandResult result = commandAdapter.executeAppleScript(script);

        if (result.isSuccess()) {
            result.setOutput(String.format("Volume set to %d%%.", level));
        }

        return result;
    }
}
