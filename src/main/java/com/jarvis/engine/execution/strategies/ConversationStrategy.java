package com.jarvis.engine.execution.strategies;

import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Strategy for general conversation responses.
 * When the LLM determines the input is a question or conversation
 * rather than a system command, it returns a direct response.
 */
@Component
public class ConversationStrategy implements CommandStrategy {

    @Override
    public String getActionType() {
        return "CONVERSATION";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        Map<String, String> params = intent.getParameters();
        String response = params != null
                ? params.getOrDefault("response", "I'm here to help! What would you like me to do?")
                : "I'm here to help! What would you like me to do?";

        return CommandResult.success(response, "CONVERSATION");
    }
}
