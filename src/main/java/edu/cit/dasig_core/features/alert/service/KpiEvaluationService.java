package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.core.event.KpiSubmittedEvent;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.kpisubmission.util.PerformanceStatusClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KpiEvaluationService {

    private final KpiSubmissionRepository kpiSubmissionRepository;
    private final AlertService alertService;

    @Transactional
    public void evaluateSubmission(KpiSubmittedEvent event) {
        KpiSubmission submission = kpiSubmissionRepository.findById(event.getSubmissionId())
                .orElse(null);

        if (submission == null) {
            return;
        }

        if (PerformanceStatusClassifier.RED.equals(submission.getPerformanceStatus())) {
            if (!alertService.existsForSubmission(submission.getId())) {
                alertService.createBreachAlert(submission.getId());
            }
        }
    }
}
