package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Strategy for controlling music playback on macOS.
 * Controls Apple Music via AppleScript.
 */
@Component
public class MusicControlStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    private static final Map<String, String> MUSIC_SCRIPTS = Map.of(
            "play", "tell application \"Music\" to play",
            "pause", "tell application \"Music\" to pause",
            "stop", "tell application \"Music\" to stop",
            "next", "tell application \"Music\" to next track",
            "previous", "tell application \"Music\" to previous track",
            "volume_up", "set volume output volume ((output volume of (get volume settings)) + 10)",
            "volume_down", "set volume output volume ((output volume of (get volume settings)) - 10)"
    );

    public MusicControlStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "MUSIC_CONTROL";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String action = intent.getTarget() != null ? intent.getTarget().toLowerCase() : "play";
        String script = MUSIC_SCRIPTS.getOrDefault(action, MUSIC_SCRIPTS.get("play"));

        CommandResult result = commandAdapter.executeAppleScript(script);

        if (result.isSuccess()) {
            result.setOutput("Music: " + action + " executed.");
        }

        return result;
    }
}
