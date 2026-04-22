package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

/**
 * Strategy for toggling WiFi on/off.
 */
@Component
public class ToggleWifiStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public ToggleWifiStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "TOGGLE_WIFI";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        // Get current WiFi status
        CommandResult statusResult = commandAdapter.execute(
                "networksetup -getairportpower en0 | awk '{print $NF}'");

        if (!statusResult.isSuccess()) {
            return CommandResult.failure("Could not determine WiFi status", "TOGGLE_WIFI");
        }

        boolean isOn = statusResult.getOutput().trim().equalsIgnoreCase("on");
        String newState = isOn ? "off" : "on";

        CommandResult result = commandAdapter.execute(
                String.format("networksetup -setairportpower en0 %s", newState));

        if (result.isSuccess()) {
            result.setOutput("WiFi has been turned " + newState + ".");
        }

        return result;
    }
}
