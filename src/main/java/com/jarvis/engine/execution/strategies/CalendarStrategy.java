package com.jarvis.engine.execution.strategies;

import com.jarvis.adapter.os.MacOsCommandAdapter;
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Strategy for calendar operations via AppleScript.
 * Supports listing today's events and creating new events.
 */
@Component
public class CalendarStrategy implements CommandStrategy {

    private final MacOsCommandAdapter commandAdapter;

    public CalendarStrategy(MacOsCommandAdapter commandAdapter) {
        this.commandAdapter = commandAdapter;
    }

    @Override
    public String getActionType() {
        return "CALENDAR";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String operation = intent.getTarget() != null ? intent.getTarget().toLowerCase() : "list";

        return switch (operation) {
            case "list" -> listTodayEvents();
            case "create" -> createEvent(intent.getParameters());
            default -> CommandResult.failure("Unknown calendar operation: " + operation, "CALENDAR");
        };
    }

    private CommandResult listTodayEvents() {
        String script = """
                tell application "Calendar"
                    set today to current date
                    set todayStart to today - (time of today)
                    set todayEnd to todayStart + (1 * days)
                    set eventList to ""
                    repeat with cal in calendars
                        set calEvents to (every event of cal whose start date ≥ todayStart and start date < todayEnd)
                        repeat with evt in calEvents
                            set eventList to eventList & summary of evt & " at " & time string of start date of evt & "\\n"
                        end repeat
                    end repeat
                    if eventList is "" then
                        return "No events scheduled for today."
                    end if
                    return eventList
                end tell
                """;

        CommandResult result = commandAdapter.executeAppleScript(script);
        if (result.isSuccess()) {
            result.setOutput("Today's events:\n" + result.getOutput());
        }
        return result;
    }

    private CommandResult createEvent(Map<String, String> params) {
        if (params == null || !params.containsKey("title")) {
            return CommandResult.failure("Event title is required", "CALENDAR");
        }

        String title = params.get("title");
        String time = params.getOrDefault("time", "9:00 AM");
        String duration = params.getOrDefault("duration", "60"); // minutes

        String script = String.format("""
                tell application "Calendar"
                    tell calendar "Home"
                        set newEvent to make new event with properties {summary:"%s", start date:date "%s", duration:%s}
                    end tell
                end tell
                return "Event created: %s"
                """, title, time, duration, title);

        return commandAdapter.executeAppleScript(script);
    }
}
