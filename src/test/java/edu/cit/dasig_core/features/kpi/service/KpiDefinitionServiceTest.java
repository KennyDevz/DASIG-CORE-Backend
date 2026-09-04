package edu.cit.dasig_core.features.kpi.service;

import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.committee.repository.CommitteeRepository;
import edu.cit.dasig_core.features.kpi.dto.CreateKpiDefinitionRequest;
import edu.cit.dasig_core.features.kpi.dto.KpiDefinitionResponse;
import edu.cit.dasig_core.features.kpi.dto.UpdateKpiDefinitionRequest;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KpiDefinitionServiceTest {

    @Mock
    private KpiDefinitionRepository kpiDefinitionRepository;
    @Mock
    private CommitteeRepository committeeRepository;
    @Mock
    private NotificationService notificationService;

    private KpiDefinitionService kpiDefinitionService;

    @BeforeEach
    void setUp() {
        kpiDefinitionService = new KpiDefinitionService(kpiDefinitionRepository, committeeRepository, notificationService);
    }

    private CreateKpiDefinitionRequest createRequest() {
        CreateKpiDefinitionRequest request = new CreateKpiDefinitionRequest();
        request.setName("Revenue");
        request.setDescription("desc");
        request.setTargetValue(1000.0);
        request.setUnit("PHP");
        request.setDeadline(LocalDate.now().plusMonths(6));
        request.setThreshold(80.0);
        request.setCommitteeId(1L);
        request.setReportingFrequency(ReportingFrequency.QUARTERLY);
        return request;
    }

    @Test
    void createKpiDefinition_throwsWhenCommitteeNotFound() {
        when(committeeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kpiDefinitionService.createKpiDefinition(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Committee not found with ID: 1");

        verify(kpiDefinitionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createKpiDefinition_savesAndTriggersDeadlineNotificationsOnSuccess() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Tech Committee");
        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));
        when(kpiDefinitionRepository.saveAndFlush(any(KpiDefinition.class))).thenAnswer(invocation -> {
            KpiDefinition kpi = invocation.getArgument(0);
            kpi.setId(10L);
            return kpi;
        });

        KpiDefinitionResponse response = kpiDefinitionService.createKpiDefinition(createRequest());

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCommitteeId()).isEqualTo(1L);
        verify(notificationService).createDeadlineNotificationsForKpi(any(KpiDefinition.class));
    }

    @Test
    void updateKpiDefinition_throwsWhenNotFound() {
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.empty());

        UpdateKpiDefinitionRequest request = new UpdateKpiDefinitionRequest();
        request.setName("X");
        request.setDescription("desc");
        request.setTargetValue(1.0);
        request.setUnit("unit");
        request.setDeadline(LocalDate.now());

        assertThatThrownBy(() -> kpiDefinitionService.updateKpiDefinition(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KPI Definition not found with ID: 1");
    }

    @Test
    void deleteKpiDefinition_throwsWhenNotFound() {
        when(kpiDefinitionRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> kpiDefinitionService.deleteKpiDefinition(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KPI Definition not found with ID: 1");

        verify(kpiDefinitionRepository, never()).deleteById(any());
    }

    @Test
    void deleteKpiDefinition_deletesWhenExists() {
        when(kpiDefinitionRepository.existsById(1L)).thenReturn(true);

        kpiDefinitionService.deleteKpiDefinition(1L);

        verify(kpiDefinitionRepository).deleteById(1L);
    }

    @Test
    void getKpiDefinitionById_throwsWhenNotFound() {
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kpiDefinitionService.getKpiDefinitionById(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KPI Definition not found with ID: 1");
    }

    @Test
    void getAllKpiDefinitions_mapsEveryDefinition() {
        KpiDefinition a = new KpiDefinition();
        a.setId(1L);
        a.setName("A");
        KpiDefinition b = new KpiDefinition();
        b.setId(2L);
        b.setName("B");
        when(kpiDefinitionRepository.findAll()).thenReturn(List.of(a, b));

        List<KpiDefinitionResponse> responses = kpiDefinitionService.getAllKpiDefinitions();

        assertThat(responses).extracting(KpiDefinitionResponse::getName).containsExactlyInAnyOrder("A", "B");
    }
}
