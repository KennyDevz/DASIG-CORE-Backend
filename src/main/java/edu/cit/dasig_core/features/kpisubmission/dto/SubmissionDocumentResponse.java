package edu.cit.dasig_core.features.kpisubmission.dto;

import lombok.Data;

@Data
public class SubmissionDocumentResponse {
    private Long id;
    private String fileName;
    private Long fileSize;
    private String contentType;
}
