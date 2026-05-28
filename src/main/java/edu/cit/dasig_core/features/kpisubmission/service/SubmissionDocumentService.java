package edu.cit.dasig_core.features.kpisubmission.service;

import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionDocument;
import edu.cit.dasig_core.features.kpisubmission.repository.SubmissionDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SubmissionDocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/jpg",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "text/csv",
            "application/csv"
    );

    private final SubmissionDocumentRepository submissionDocumentRepository;
    private final SupabaseStorageClient supabaseStorageClient;

    public SubmissionDocumentService(
            SubmissionDocumentRepository submissionDocumentRepository,
            SupabaseStorageClient supabaseStorageClient
    ) {
        this.submissionDocumentRepository = submissionDocumentRepository;
        this.supabaseStorageClient = supabaseStorageClient;
    }

    @Transactional
    public List<SubmissionDocument> storeDocuments(KpiSubmission submission, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<SubmissionDocument> savedDocuments = new ArrayList<>();

        for (MultipartFile file : files) {
            validateFile(file);

            String sanitizedFileName = sanitizeFileName(file.getOriginalFilename());
            String objectPath = buildObjectPath(submission.getId(), sanitizedFileName);
            String contentType = resolveContentType(file);

            supabaseStorageClient.upload(objectPath, readBytes(file), contentType);

            SubmissionDocument document = new SubmissionDocument();
            document.setSubmission(submission);
            document.setFileName(sanitizedFileName);
            document.setFileSize(file.getSize());
            document.setContentType(contentType);
            document.setStoragePath(objectPath);

            savedDocuments.add(submissionDocumentRepository.save(document));
        }

        return savedDocuments;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded files cannot be empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Each uploaded file must be 10 MB or smaller.");
        }

        String contentType = resolveContentType(file);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null ? contentType : "application/octet-stream";
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read uploaded file.", ex);
        }
    }

    private String buildObjectPath(Long submissionId, String sanitizedFileName) {
        return submissionId + "/" + UUID.randomUUID() + "_" + sanitizedFileName;
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("Uploaded file must have a valid name.");
        }

        String fileName = Paths.get(originalFileName).getFileName().toString();
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("Uploaded file must have a valid name.");
        }

        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
