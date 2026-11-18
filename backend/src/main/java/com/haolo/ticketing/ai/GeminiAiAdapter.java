package com.haolo.ticketing.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** External LLM Adapter (Google Gemini) — activates automatically when AI_GEMINI_API_KEY is configured in environment. */
@Component
public class GeminiAiAdapter implements AiAssistantPort {
    private static final Logger log = LoggerFactory.getLogger(GeminiAiAdapter.class);

    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public GeminiAiAdapter(
        @Value("${AI_GEMINI_API_KEY:}") String apiKey,
        @Value("${ai.gemini.model:gemini-2.0-flash}") String model,
        ObjectMapper mapper
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    }

    @Override
    public String adapterName() {
        return "GEMINI_API";
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isEmpty();
    }

    @Override
    public String enhanceResponse(String systemPrompt, String structuredContext, String userMessage, String fallbackDraft) {
        if (!isAvailable()) {
            return null;
        }
        try {
            String prompt = systemPrompt + "\n\nDữ liệu thực tế từ hệ thống PostgreSQL:\n" + structuredContext
                + "\n\nYêu cầu người dùng:\n" + userMessage
                + "\n\nHãy trả lời ngắn gọn, chính xác bằng tiếng Việt dựa đúng trên dữ liệu thực tế trên (không bịa số liệu).";
            Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
            );
            String json = mapper.writeValueAsString(body);
            URI uri = URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey);
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Gemini adapter HTTP {}, falling back to local adapter", response.statusCode());
                return null;
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            String text = textNode.isString() ? textNode.asString().trim() : "";
            return text.isEmpty() ? null : text;
        } catch (Exception ex) {
            log.warn("Gemini adapter unavailable ({}), falling back to local adapter", ex.getClass().getSimpleName());
            return null;
        }
    }
}
