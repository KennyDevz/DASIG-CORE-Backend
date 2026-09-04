package edu.cit.dasig_core.features.kpisubmission.service;

import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiAssignmentServiceTest {

    @Mock
    private KpiAssignmentRepository kpiAssignmentRepository;

    private KpiAssignmentService kpiAssignmentService;

    @BeforeEach
    void setUp() {
        kpiAssignmentService = new KpiAssignmentService(kpiAssignmentRepository);
    }

    @Test
    void getAssignedKpi_throwsWhenOrganizationIsNull() {
        assertThatThrownBy(() -> kpiAssignmentService.getAssignedKpi(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Organization is required to submit KPI values.");
    }

    @Test
    void getAssignedKpi_throwsWhenKpiNotAssignedToOrganization() {
        when(kpiAssignmentRepository.findByIdAndCommittee_Organizations_Id(1L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kpiAssignmentService.getAssignedKpi(1L, 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KPI is not assigned to your organization or does not exist.");
    }

    @Test
    void getAssignedKpi_returnsKpiWhenAssigned() {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(1L);
        when(kpiAssignmentRepository.findByIdAndCommittee_Organizations_Id(1L, 9L)).thenReturn(Optional.of(kpi));

        assertThat(kpiAssignmentService.getAssignedKpi(1L, 9L)).isEqualTo(kpi);
    }

    @Test
    void getAssignedKpis_throwsWhenOrganizationIsNull() {
        assertThatThrownBy(() -> kpiAssignmentService.getAssignedKpis(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Organization is required.");
    }

    @Test
    void getAssignedKpis_returnsKpisForOrganization() {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(1L);
        when(kpiAssignmentRepository.findByCommittee_Organizations_Id(9L)).thenReturn(List.of(kpi));

        assertThat(kpiAssignmentService.getAssignedKpis(9L)).containsExactly(kpi);
    }

    @Test
    void validateAssignment_throwsWhenNotAssigned() {
        when(kpiAssignmentRepository.findByIdAndCommittee_Organizations_Id(1L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kpiAssignmentService.validateAssignment(1L, 9L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateAssignment_doesNotThrowWhenAssigned() {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(1L);
        when(kpiAssignmentRepository.findByIdAndCommittee_Organizations_Id(1L, 9L)).thenReturn(Optional.of(kpi));

        kpiAssignmentService.validateAssignment(1L, 9L);
    }
}
