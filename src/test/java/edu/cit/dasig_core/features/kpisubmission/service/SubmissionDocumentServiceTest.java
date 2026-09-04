package edu.cit.dasig_core.features.kpisubmission.service;

import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionDocument;
import edu.cit.dasig_core.features.kpisubmission.repository.SubmissionDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionDocumentServiceTest {

    @Mock
    private SubmissionDocumentRepository submissionDocumentRepository;
    @Mock
    private SupabaseStorageClient supabaseStorageClient;

    private SubmissionDocumentService submissionDocumentService;

    @BeforeEach
    void setUp() {
        submissionDocumentService = new SubmissionDocumentService(submissionDocumentRepository, supabaseStorageClient);
    }

    private KpiSubmission submission(Long id) {
        KpiSubmission submission = new KpiSubmission();
        submission.setId(id);
        return submission;
    }

    @Test
    void storeDocuments_returnsEmptyListWhenFilesIsNull() {
        assertThat(submissionDocumentService.storeDocuments(submission(1L), null)).isEmpty();
        verifyNoInteractions(supabaseStorageClient, submissionDocumentRepository);
    }

    @Test
    void storeDocuments_returnsEmptyListWhenFilesIsEmpty() {
        assertThat(submissionDocumentService.storeDocuments(submission(1L), List.of())).isEmpty();
        verifyNoInteractions(supabaseStorageClient, submissionDocumentRepository);
    }

    @Test
    void storeDocuments_throwsWhenFileIsEmpty() {
        MultipartFile empty = new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> submissionDocumentService.storeDocuments(submission(1L), List.of(empty)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Uploaded files cannot be empty.");
    }

    @Test
    void storeDocuments_throwsWhenFileExceedsMaxSize() {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        MultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", oversized);

        assertThatThrownBy(() -> submissionDocumentService.storeDocuments(submission(1L), List.of(file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Each uploaded file must be 10 MB or smaller.");
    }

    @Test
    void storeDocuments_throwsWhenContentTypeUnsupported() {
        MultipartFile file = new MockMultipartFile("file", "script.exe", "application/x-msdownload", new byte[]{1});

        assertThatThrownBy(() -> submissionDocumentService.storeDocuments(submission(1L), List.of(file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported file type: application/x-msdownload");
    }

    @Test
    void storeDocuments_throwsWhenFileNameBlank() {
        MultipartFile file = new MockMultipartFile("file", "   ", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> submissionDocumentService.storeDocuments(submission(1L), List.of(file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Uploaded file must have a valid name.");
    }

    @Test
    void storeDocuments_sanitizesFileNameAndUploadsToStorage() {
        MultipartFile file = new MockMultipartFile(
                "file", "my report (final)!.pdf", "application/pdf", "content".getBytes());
        when(submissionDocumentRepository.save(any(SubmissionDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<SubmissionDocument> saved = submissionDocumentService.storeDocuments(submission(42L), List.of(file));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getFileName()).isEqualTo("my_report__final__.pdf");
        assertThat(saved.get(0).getContentType()).isEqualTo("application/pdf");

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(supabaseStorageClient).upload(pathCaptor.capture(), any(byte[].class), eq("application/pdf"));
        assertThat(pathCaptor.getValue()).startsWith("42/");
        assertThat(pathCaptor.getValue()).endsWith("_my_report__final__.pdf");
    }

    @Test
    void storeDocuments_defaultsContentTypeWhenNull() {
        MultipartFile file = new MockMultipartFile("file", "data.bin", null, "content".getBytes());

        assertThatThrownBy(() -> submissionDocumentService.storeDocuments(submission(1L), List.of(file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported file type: application/octet-stream");
    }

    @Test
    void downloadDocument_delegatesToStorageClientUsingStoragePath() {
        SubmissionDocument document = new SubmissionDocument();
        document.setStoragePath("42/uuid_report.pdf");
        when(supabaseStorageClient.download("42/uuid_report.pdf")).thenReturn(new byte[]{9, 9});

        byte[] result = submissionDocumentService.downloadDocument(document);

        assertThat(result).containsExactly(9, 9);
    }
}
