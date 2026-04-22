package com.jarvis.engine.decision;

import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI Decision Engine.
 * Acts as an interceptor between Intent Recognition and Execution.
 * It analyzes the intent and context to optionally suggest modifications
 * or additional actions (proactive decision making) before they run.
 */
@Service
public class ProactiveDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(ProactiveDecisionEngine.class);

    /**
     * Inspects the intent before execution.
     * Returns true if it should be executed normally, false if the engine intercepted it.
     */
    public boolean evaluateBeforeExecution(IntentResult intent) {
        String action = intent.getAction();
        
        // Example Rule-Based Interception: Power-saving heuristic
        if ("SYSTEM_INFO".equals(action) && "battery".equals(intent.getTarget())) {
            log.info("DecisionEngine: Analyzing battery context for proactive suggestions...");
            // Real AI logic could evaluate LLM sentiment here based on power state, 
            // but for now we simply allow it to pass.
        }

        return true; 
    }

    /**
     * Inspects the execution results to determine if a follow-up action is required.
     */
    public void evaluateAfterExecution(IntentResult parsedIntent, List<CommandResult> results) {
        for (CommandResult result : results) {
            if ("GET_WEATHER".equals(result.getAction()) && result.isSuccess()) {
                if (result.getOutput().toLowerCase().contains("rain") || result.getOutput().toLowerCase().contains("showers")) {
                     log.info("DecisionEngine: Rain detected. Storing context constraint to suggest an umbrella later.");
                }
            }
        }
    }
}
