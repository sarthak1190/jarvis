package com.jarvis.engine.execution.strategies;

import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Advanced Integration: Retrieves real-time weather using wttr.in (No API Key Required).
 * Action type: GET_WEATHER
 */
@Component
public class GetWeatherStrategy implements CommandStrategy {

    private static final Logger log = LoggerFactory.getLogger(GetWeatherStrategy.class);
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GetWeatherStrategy(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getActionType() {
        return "GET_WEATHER";
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        String location = intent.getTarget();
        if (location == null || location.isEmpty() || "null".equals(location)) {
            location = ""; // wttr.in automatically resolves IP location if empty
        }

        // Fetch JSON from wttr.in (format j1)
        String url = "https://wttr.in/" + location + "?format=j1";

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Failed to fetch weather: {}", response.code());
                return CommandResult.failure("I couldn't reach the weather service.", getActionType());
            }

            JsonNode root = objectMapper.readTree(response.body().string());
            JsonNode current = root.path("current_condition").get(0);
            
            String tempC = current.path("temp_C").asText();
            String desc = current.path("weatherDesc").get(0).path("value").asText();
            String actualLocation = root.path("nearest_area").get(0).path("areaName").get(0).path("value").asText();

            String speech = String.format("The current weather in %s is %s degrees Celsius and %s.", actualLocation, tempC, desc);

            return CommandResult.success(speech, getActionType());

        } catch (IOException e) {
            log.error("Exception fetching weather", e);
            return CommandResult.failure("I encountered a network error while checking the weather.", getActionType());
        }
    }
}
