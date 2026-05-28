package edu.cit.dasig_core.features.kpisubmission.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SupabaseStorageClient {

    private final String bucket;
    private final RestClient restClient;

    public SupabaseStorageClient(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-role-key}") String serviceRoleKey,
            @Value("${supabase.storage.bucket}") String bucket
    ) {
        this.bucket = bucket;
        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .build();
    }

    public void upload(String objectPath, byte[] content, String contentType) {
        try {
            restClient.post()
                    .uri("/storage/v1/object/{bucket}/{objectPath}", bucket, objectPath)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Failed to upload file to Supabase Storage: " + ex.getResponseBodyAsString(), ex);
        }
    }
}
