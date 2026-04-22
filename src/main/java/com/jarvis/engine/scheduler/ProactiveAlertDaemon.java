package com.jarvis.engine.scheduler;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.adapter.tts.MacOsTtsAdapter;
import com.jarvis.engine.memory.ConversationMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background Scheduler daemon.
 * Periodically polls macOS system stats and proactively alerts the user via Voice.
 * Imitates Jarvis's situational awareness.
 */
@Service
public class ProactiveAlertDaemon {

    private static final Logger log = LoggerFactory.getLogger(ProactiveAlertDaemon.class);

    private final MacOsCommandAdapter osCommandAdapter;
    private final MacOsTtsAdapter ttsAdapter;
    private final ConversationMemoryService memoryService;

    // State trackers to prevent alert spamming
    private final AtomicBoolean lowBatteryAlerted = new AtomicBoolean(false);
    private final AtomicBoolean cpuSpikeAlerted = new AtomicBoolean(false);

    public ProactiveAlertDaemon(MacOsCommandAdapter osCommandAdapter,
                                MacOsTtsAdapter ttsAdapter,
                                ConversationMemoryService memoryService) {
        this.osCommandAdapter = osCommandAdapter;
        this.ttsAdapter = ttsAdapter;
        this.memoryService = memoryService;
    }

    /**
     * Checks battery status every 5 minutes.
     */
    @Scheduled(fixedRate = 300000)
    public void monitorBatteryStatus() {
        try {
            String output = osCommandAdapter.execute("pmset -g batt").getOutput();
            if (output != null && output.contains("discharging")) {
                // Parse percentage
                int percentStart = output.indexOf("	") + 1;
                if (percentStart > 0 && output.indexOf('%') > percentStart) {
                    String percentStr = output.substring(percentStart, output.indexOf('%')).trim();
                    try {
                        int battery = Integer.parseInt(percentStr);
                        if (battery <= 20 && !lowBatteryAlerted.get()) {
                            log.warn("Proactive Alert: Battery low ({}%)", battery);
                            String alertText = "Sir, your battery has dropped to " + battery + " percent. I advise plugging in your device.";
                            speakAndLog(alertText);
                            lowBatteryAlerted.set(true);
                        } else if (battery > 20) {
                            lowBatteryAlerted.set(false); // reset
                        }
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                lowBatteryAlerted.set(false); // Reset when charging
            }
        } catch (Exception e) {
            log.debug("Failed to check battery via daemon: {}", e.getMessage());
        }
    }

    private void speakAndLog(String text) {
        memoryService.addJarvisResponse(text);
        ttsAdapter.speakAsync(text);
    }
}
