package com.jarvis.engine.memory;

import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Conversational Memory Engine.
 * Maintains a short-term rolling window of the user's conversation to provide context
 * to the LLM when parsing intents (e.g. "Do it again", "Close it instead").
 */
@Service
public class ConversationMemoryService {

    private static final int MAX_MEMORY_SIZE = 10;
    
    // Simple thread-safe in-memory store for a single user (since this is a personal assistant)
    private final Deque<Map<String, String>> conversationHistory = new LinkedList<>();

    /**
     * Add a user command to memory.
     */
    public synchronized void addUserCommand(String text) {
        addEntry("user", text);
    }

    /**
     * Add Jarvis's response/action to memory.
     */
    public synchronized void addJarvisResponse(String text) {
        addEntry("jarvis", text);
    }

    private synchronized void addEntry(String role, String content) {
        if (content == null || content.isBlank()) return;
        
        conversationHistory.addLast(Map.of("role", role, "content", content));
        if (conversationHistory.size() > MAX_MEMORY_SIZE) {
            conversationHistory.removeFirst();
        }
    }

    /**
     * Get the recent conversation history formatted for the LLM prompt.
     */
    public synchronized String getFormattedContext() {
        if (conversationHistory.isEmpty()) {
            return "No prior context.";
        }

        return conversationHistory.stream()
                .map(entry -> entry.get("role").toUpperCase() + ": " + entry.get("content"))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Clears the current memory.
     */
    public synchronized void clear() {
        conversationHistory.clear();
    }
}
