package edu.cit.dasig_core.features.kpisubmission.service;

import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiAssignmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KpiAssignmentService {

    private final KpiAssignmentRepository kpiAssignmentRepository;

    public KpiAssignmentService(KpiAssignmentRepository kpiAssignmentRepository) {
        this.kpiAssignmentRepository = kpiAssignmentRepository;
    }

    public KpiDefinition getAssignedKpi(Long kpiDefinitionId, Long organizationId) {
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization is required to submit KPI values.");
        }

        return kpiAssignmentRepository.findByIdAndOrganizationId(kpiDefinitionId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "KPI is not assigned to your organization or does not exist."));
    }

    public List<KpiDefinition> getAssignedKpis(Long organizationId) {
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization is required.");
        }

        return kpiAssignmentRepository.findByOrganizationId(organizationId);
    }

    public void validateAssignment(Long kpiDefinitionId, Long organizationId) {
        getAssignedKpi(kpiDefinitionId, organizationId);
    }
}
