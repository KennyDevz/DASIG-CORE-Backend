package edu.cit.dasig_core.features.report.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
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

        LLMChatResponse response;
        try {
            response = restTemplate.postForObject(GROQ_URL, request, LLMChatResponse.class);
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 429) {
                throw new LLMException(LLMException.Reason.RATE_LIMITED, e);
            } else if (status == 401 || status == 403) {
                log.error("Groq authentication failed (HTTP {}) - check groq.api-key", status);
                throw new LLMException(LLMException.Reason.AUTH, e);
            } else if (status == 400) {
                throw new LLMException(LLMException.Reason.SCHEMA_REJECTED, e);
            }
            throw new LLMException(LLMException.Reason.INVALID_RESPONSE, e);
        } catch (HttpServerErrorException e) {
            throw new LLMException(LLMException.Reason.UNAVAILABLE, e);
        } catch (ResourceAccessException e) {
            throw new LLMException(LLMException.Reason.TIMEOUT, e);
        }

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new LLMException(LLMException.Reason.EMPTY_RESPONSE);
        }

        LLMChatResponse.Choice choice = response.getChoices().get(0);

        if ("length".equals(choice.getFinishReason())) {
            throw new LLMException(LLMException.Reason.TRUNCATED);
        }

        String content = choice.getMessage() == null ? null : choice.getMessage().getContent();
        if (content == null || content.isBlank()) {
            throw new LLMException(LLMException.Reason.EMPTY_RESPONSE);
        }

        return content;
    }
}