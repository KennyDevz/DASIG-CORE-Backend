package edu.cit.dasig_core.features.kpi.service;

import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.committee.repository.CommitteeRepository;
import edu.cit.dasig_core.features.kpi.dto.CreateKpiDefinitionRequest;
import edu.cit.dasig_core.features.kpi.dto.KpiDefinitionResponse;
import edu.cit.dasig_core.features.kpi.dto.UpdateKpiDefinitionRequest;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;
import edu.cit.dasig_core.features.kpi.util.ReportingPeriodResolver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.alert.model.Alert;
import edu.cit.dasig_core.features.alert.repository.AlertRepository;
import edu.cit.dasig_core.features.alert.repository.OverdueExplanationRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.kpisubmission.repository.SubmissionDocumentRepository;
import edu.cit.dasig_core.features.notification.repository.NotificationRepository;
import edu.cit.dasig_core.features.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KpiDefinitionService {

    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final CommitteeRepository committeeRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final AlertRepository alertRepository;
    private final KpiSubmissionRepository kpiSubmissionRepository;
    private final SubmissionDocumentRepository submissionDocumentRepository;
    private final OverdueExplanationRepository overdueExplanationRepository;

    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);

    public KpiDefinitionService(
            KpiDefinitionRepository kpiDefinitionRepository,
            CommitteeRepository committeeRepository,
            NotificationService notificationService,
            NotificationRepository notificationRepository,
            AlertRepository alertRepository,
            KpiSubmissionRepository kpiSubmissionRepository,
            SubmissionDocumentRepository submissionDocumentRepository,
            OverdueExplanationRepository overdueExplanationRepository
    ) {
        this.kpiDefinitionRepository = kpiDefinitionRepository;
        this.committeeRepository = committeeRepository;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.alertRepository = alertRepository;
        this.kpiSubmissionRepository = kpiSubmissionRepository;
        this.submissionDocumentRepository = submissionDocumentRepository;
        this.overdueExplanationRepository = overdueExplanationRepository;
    }

    @Transactional
    public KpiDefinitionResponse createKpiDefinition(CreateKpiDefinitionRequest request) {
        Committee committee = committeeRepository.findById(request.getCommitteeId())
                .orElseThrow(() -> new IllegalArgumentException("Committee not found with ID: " + request.getCommitteeId()));

        KpiDefinition kpiDef = new KpiDefinition();
        kpiDef.setName(request.getName());
        kpiDef.setDescription(request.getDescription());
        kpiDef.setTargetValue(request.getTargetValue());
        kpiDef.setUnit(request.getUnit());
        kpiDef.setDeadline(request.getDeadline());
        kpiDef.setThreshold(request.getThreshold());
        kpiDef.setReportingFrequency(request.getReportingFrequency());
        kpiDef.setCommittee(committee);

        KpiDefinition savedKpiDef = kpiDefinitionRepository.saveAndFlush(kpiDef);
        notificationService.createDeadlineNotificationsForKpi(savedKpiDef);
        return mapToResponse(savedKpiDef);
    }

    @Transactional
    public KpiDefinitionResponse updateKpiDefinition(Long id, UpdateKpiDefinitionRequest request) {
        KpiDefinition kpiDef = kpiDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KPI Definition not found with ID: " + id));

        LocalDate oldDeadline = kpiDef.getDeadline();
        boolean deadlineChanged = !request.getDeadline().equals(oldDeadline);
        if (deadlineChanged && request.getDeadline().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("New deadline cannot be in the past.");
        }

        kpiDef.setName(request.getName());
        kpiDef.setDescription(request.getDescription());
        kpiDef.setTargetValue(request.getTargetValue());
        kpiDef.setUnit(request.getUnit());
        kpiDef.setDeadline(request.getDeadline());
        kpiDef.setThreshold(request.getThreshold());
        kpiDef.setReportingFrequency(request.getReportingFrequency());

        KpiDefinition updatedKpiDef = kpiDefinitionRepository.saveAndFlush(kpiDef);

        if (updatedKpiDef.getReportingFrequency() == ReportingFrequency.ONE_TIME) {
            LocalDate assignmentStart = updatedKpiDef.getDateCreated() != null
                    ? updatedKpiDef.getDateCreated().toLocalDate()
                    : LocalDate.now();
            String newReportingPeriod = ReportingPeriodResolver.resolveCurrentPeriod(
                    ReportingFrequency.ONE_TIME,
                    updatedKpiDef.getDeadline(),
                    assignmentStart,
                    LocalDate.now()
            );
            List<KpiSubmission> existingSubmissions = kpiSubmissionRepository.findByKpiDefinitionId(id);
            for (KpiSubmission submission : existingSubmissions) {
                if (newReportingPeriod != null && !newReportingPeriod.equals(submission.getReportingPeriod())) {
                    submission.setReportingPeriod(newReportingPeriod);
                    kpiSubmissionRepository.save(submission);
                }
            }
        }

        if (deadlineChanged) {
            String latestRemark = resolveOpenAlerts(id, updatedKpiDef.getDeadline());
            notificationService.notifyDeadlineUpdated(updatedKpiDef, oldDeadline, updatedKpiDef.getDeadline(), latestRemark);
        }

        // Creates reminders for the new deadline (old ones stay as history)
        notificationService.createDeadlineNotificationsForKpi(updatedKpiDef);
        return mapToResponse(updatedKpiDef);
    }

    @Transactional
    public KpiDefinitionResponse archiveKpiDefinition(Long id) {
        KpiDefinition kpiDef = kpiDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KPI Definition not found with ID: " + id));

        kpiDef.setStatus(KpiDefinition.STATUS_ARCHIVED);
        KpiDefinition saved = kpiDefinitionRepository.saveAndFlush(kpiDef);

        // Clear active alerts and notifications for this archived KPI
        alertRepository.deleteByKpiDefinitionId(id);
        notificationRepository.deleteByKpiDefinitionId(id);

        return mapToResponse(saved);
    }

    @Transactional
    public KpiDefinitionResponse unarchiveKpiDefinition(Long id) {
        KpiDefinition kpiDef = kpiDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KPI Definition not found with ID: " + id));

        kpiDef.setStatus(KpiDefinition.STATUS_ACTIVE);
        KpiDefinition saved = kpiDefinitionRepository.saveAndFlush(kpiDef);

        // Re-evaluate deadline notifications
        notificationService.createDeadlineNotificationsForKpi(saved);

        return mapToResponse(saved);
    }

    @Transactional
    public void deleteKpiDefinition(Long id) {
        if (!kpiDefinitionRepository.existsById(id)) {
            throw new IllegalArgumentException("KPI Definition not found with ID: " + id);
        }

        // 1. Delete notifications
        notificationRepository.deleteByKpiDefinitionId(id);

        // 2. Delete overdue explanations, then alerts
        overdueExplanationRepository.deleteByKpiDefinitionId(id);
        alertRepository.deleteByKpiDefinitionId(id);

        // 3. Delete submissions and associated documents/alerts
        List<KpiSubmission> submissions = kpiSubmissionRepository.findByKpiDefinitionId(id);
        for (KpiSubmission submission : submissions) {
            alertRepository.deleteBySubmissionId(submission.getId());
            submissionDocumentRepository.deleteBySubmissionId(submission.getId());
        }
        kpiSubmissionRepository.deleteAll(submissions);

        // 4. Delete KPI definition
        kpiDefinitionRepository.deleteById(id);
    }

    public List<KpiDefinitionResponse> getAllKpiDefinitions() {
        return kpiDefinitionRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public KpiDefinitionResponse getKpiDefinitionById(Long id) {
        KpiDefinition kpiDef = kpiDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KPI Definition not found with ID: " + id));
        return mapToResponse(kpiDef);
    }

    public List<KpiDefinitionResponse> getKpiDefinitionsByOrganizationId(Long organizationId) {
        return kpiDefinitionRepository.findByCommittee_Organizations_Id(organizationId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<KpiDefinitionResponse> getKpiDefinitionsByCommitteeId(Long committeeId) {
        return kpiDefinitionRepository.findByCommitteeId(committeeId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Closes open Overdue / At Risk alerts when the Admin changes the deadline (UC-2.2).
     * The next alert evaluation creates a new alert if the KPI is still behind.
     *
     * @return the latest Admin remark on those alerts, or null if there is none
     */
    private String resolveOpenAlerts(Long kpiId, LocalDate newDeadline) {
        String reason = "Deadline changed to " + newDeadline.format(DEADLINE_FORMAT);
        List<Alert> openAlerts = alertRepository.findByKpiDefinitionIdAndStatusIn(
                kpiId, List.of(Alert.STATUS_UNACKNOWLEDGED, Alert.STATUS_ACKNOWLEDGED));

        for (Alert alert : openAlerts) {
            alert.setStatus(Alert.STATUS_RESOLVED);
            alert.setResolvedAt(LocalDateTime.now());
            alert.setResolutionReason(reason);
        }
        alertRepository.saveAll(openAlerts);

        return openAlerts.stream()
                .filter(alert -> alert.getRemarkedAt() != null)
                .max(Comparator.comparing(Alert::getRemarkedAt))
                .map(Alert::getAdminRemark)
                .orElse(null);
    }

    private KpiDefinitionResponse mapToResponse(KpiDefinition kpiDef) {
        KpiDefinitionResponse response = new KpiDefinitionResponse();
        response.setId(kpiDef.getId());
        response.setName(kpiDef.getName());
        response.setDescription(kpiDef.getDescription());
        response.setTargetValue(kpiDef.getTargetValue());
        response.setUnit(kpiDef.getUnit());
        response.setDeadline(kpiDef.getDeadline());
        response.setThreshold(kpiDef.getThreshold());
        response.setReportingFrequency(kpiDef.getReportingFrequency());
        response.setStatus(kpiDef.getStatus());
        response.setArchived(kpiDef.isArchived());

        if (kpiDef.getCommittee() != null) {
            response.setCommitteeId(kpiDef.getCommittee().getId());
            response.setCommitteeName(kpiDef.getCommittee().getName());
        }

        return response;
    }
}