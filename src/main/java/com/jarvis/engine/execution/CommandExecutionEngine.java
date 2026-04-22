package com.jarvis.engine.execution;

import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.exception.JarvisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central Command Execution Engine.
 * Routes intents to the appropriate CommandStrategy using the Strategy Pattern.
 * Handles both single and compound (multi-action) commands.
 */
@Service
public class CommandExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutionEngine.class);

    private final Map<String, CommandStrategy> strategyMap;
    private final Executor jarvisExecutor;

    public CommandExecutionEngine(List<CommandStrategy> strategies, Executor jarvisExecutor) {
        this.jarvisExecutor = jarvisExecutor;
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        CommandStrategy::getActionType,
                        Function.identity()
                ));
        log.info("Loaded {} command strategies: {}", strategyMap.size(), strategyMap.keySet());
    }

    /**
     * Execute a single intent.
     *
     * @param intent the parsed intent
     * @return execution result
     */
    public CommandResult execute(IntentResult intent) {
        String action = intent.getAction();
        log.info("Executing action: {} (target: {})", action, intent.getTarget());

        CommandStrategy strategy = strategyMap.get(action);

        if (strategy == null) {
            log.warn("No strategy found for action: {}", action);
            return CommandResult.failure(
                    "Unsupported action: " + action + ". Available actions: " + strategyMap.keySet(),
                    action
            );
        }

        try {
            long start = System.currentTimeMillis();
            CommandResult result = strategy.execute(intent);
            result.setExecutionTimeMs(System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("Error executing action {}: {}", action, e.getMessage(), e);
            return CommandResult.failure("Execution error: " + e.getMessage(), action);
        }
    }

    /**
     * Execute a compound intent (multiple sub-commands) in PARALLEL.
     *
     * @param intent the compound intent with subIntents
     * @return list of execution results aggregated from all futures
     */
    public List<CommandResult> executeCompound(IntentResult intent) {
        if (!intent.isCompound() || intent.getSubIntents() == null) {
            return List.of(execute(intent));
        }

        log.info("Executing compound command with {} sub-intents in PARALLEL", intent.getSubIntents().size());

        // Map each intent to an async CompletableFuture
        List<CompletableFuture<CommandResult>> futures = intent.getSubIntents().stream()
                .map(subIntent -> CompletableFuture.supplyAsync(() -> execute(subIntent), jarvisExecutor))
                .collect(Collectors.toList());

        // Wait for all to finish and aggregate results
        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    /**
     * Check if an action type is supported.
     */
    public boolean isSupported(String actionType) {
        return strategyMap.containsKey(actionType);
    }

    /**
     * Get all supported action types.
     */
    public java.util.Set<String> getSupportedActions() {
        return strategyMap.keySet();
    }
}
