package com.jarvis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Jarvis AI Assistant - Main Application Entry Point
 *
 * A production-grade voice-controlled intelligent assistant for macOS.
 * Supports voice commands, NLP intent recognition, OS command execution,
 * and voice response synthesis.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class JarvisApplication {

    public static void main(String[] args) {
        SpringApplication.run(JarvisApplication.class, args);
    }
}
