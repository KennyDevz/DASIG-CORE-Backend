package edu.cit.dasig_core.features.report.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LLMApiClient {

    private final RestTemplate restTemplate;

    @Value("${groq.api-key}")
    private String apiKey;

    @Value("${groq.model}")
    private String modelName;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    public String generateReport(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an assistant that generates structured reports."),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 4000
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        Map<?, ?> response = restTemplate.postForObject(GROQ_URL, request, Map.class);

        if (response == null || !response.containsKey("choices")) {
            throw new IllegalStateException("Groq API returned an empty or invalid response.");
        }

        List<?> choices = (List<?>) response.get("choices");
        if (choices.isEmpty()) {
            throw new IllegalStateException("No completion choices returned by Groq.");
        }

        Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
        return (String) message.get("content");
    }
}