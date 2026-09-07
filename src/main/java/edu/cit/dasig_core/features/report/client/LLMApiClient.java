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

    /**
     * Fixed shape for a structured report: exactly the sections the prompt asks for,
     * each with a heading, narrative text, and the ephemeral record tags (e.g. "REC-3")
     * it cites. Hand-written rather than generated from a Java class, since the shape
     * is small and fixed and this avoids adding a schema-generation dependency.
     */
    private static Map<String, Object> reportJsonSchema() {
        Map<String, Object> sectionSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "heading", Map.of("type", "string"),
                        "text", Map.of("type", "string"),
                        "citedRecordTags", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        )
                ),
                "required", List.of("heading", "text", "citedRecordTags"),
                "additionalProperties", false
        );

        Map<String, Object> rootSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "sections", Map.of(
                                "type", "array",
                                "items", sectionSchema
                        )
                ),
                "required", List.of("sections"),
                "additionalProperties", false
        );

        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "structured_report",
                        "strict", true,
                        "schema", rootSchema
                )
        );
    }

    public String generateReport(String prompt) {
        return generateReport(prompt, reportJsonSchema());
    }

    /**
     * Looser fallback for when the model/account rejects strict json_schema mode —
     * still forces valid JSON, just without a guaranteed shape.
     */
    public String generateReportAsJsonObject(String prompt) {
        return generateReport(prompt, Map.of("type", "json_object"));
    }

    private String generateReport(String prompt, Map<String, Object> responseFormat) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an assistant that generates structured reports."),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 5000,
                "response_format", responseFormat
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