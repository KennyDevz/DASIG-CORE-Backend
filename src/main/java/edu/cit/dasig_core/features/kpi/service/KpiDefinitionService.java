package edu.cit.dasig_core.features.kpi.service;

import edu.cit.dasig_core.core.event.KpiDefinitionChangedEvent;
import edu.cit.dasig_core.features.kpi.dto.CreateKpiDefinitionRequest;
import edu.cit.dasig_core.features.kpi.dto.KpiDefinitionResponse;
import edu.cit.dasig_core.features.kpi.dto.UpdateKpiDefinitionRequest;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.notification.service.NotificationService;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class KpiDefinitionService {

    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final OrganizationRepository organizationRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    public KpiDefinitionService(
            KpiDefinitionRepository kpiDefinitionRepository,
            OrganizationRepository organizationRepository,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.kpiDefinitionRepository = kpiDefinitionRepository;
        this.organizationRepository = organizationRepository;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public KpiDefinitionResponse createKpiDefinition(CreateKpiDefinitionRequest request) {
        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Organization not found with ID: " + request.getOrganizationId()));

        KpiDefinition kpiDef = new KpiDefinition();
        kpiDef.setName(request.getName());
        kpiDef.setDescription(request.getDescription());
        kpiDef.setTargetValue(request.getTargetValue());
        kpiDef.setUnit(request.getUnit());
        kpiDef.setDeadline(request.getDeadline());
        kpiDef.setThreshold(request.getThreshold());
        kpiDef.setReportingFrequency(request.getReportingFrequency());
        kpiDef.setOrganization(org);

        KpiDefinition savedKpiDef = kpiDefinitionRepository.saveAndFlush(kpiDef);
        notificationService.createDeadlineNotificationsForKpi(savedKpiDef);

        eventPublisher.publishEvent(new KpiDefinitionChangedEvent(
                savedKpiDef.getId(),
                org.getId(),
                KpiDefinitionChangedEvent.ChangeType.CREATED
        ));

        return mapToResponse(savedKpiDef);
    }

    @Transactional
    public KpiDefinitionResponse updateKpiDefinition(Long id, UpdateKpiDefinitionRequest request) {
        KpiDefinition kpiDef = kpiDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KPI Definition not found with ID: " + id));

        kpiDef.setName(request.getName());
        kpiDef.setDescription(request.getDescription());
        kpiDef.setTargetValue(request.getTargetValue());
        kpiDef.setUnit(request.getUnit());
        kpiDef.setDeadline(request.getDeadline());
        kpiDef.setThreshold(request.getThreshold());
        kpiDef.setReportingFrequency(request.getReportingFrequency());

        KpiDefinition updatedKpiDef = kpiDefinitionRepository.saveAndFlush(kpiDef);
        notificationService.createDeadlineNotificationsForKpi(updatedKpiDef);

        eventPublisher.publishEvent(new KpiDefinitionChangedEvent(
                updatedKpiDef.getId(),
                updatedKpiDef.getOrganization().getId(),
                KpiDefinitionChangedEvent.ChangeType.UPDATED
        ));

        return mapToResponse(updatedKpiDef);
    }

    @Transactional
    public void deleteKpiDefinition(Long id) {
        KpiDefinition kpiDef = kpiDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KPI Definition not found with ID: " + id));

        Long organizationId = kpiDef.getOrganization().getId();
        kpiDefinitionRepository.deleteById(id);

        eventPublisher.publishEvent(new KpiDefinitionChangedEvent(
                id,
                organizationId,
                KpiDefinitionChangedEvent.ChangeType.DELETED
        ));
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
        return kpiDefinitionRepository.findByOrganizationId(organizationId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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
        response.setOrganizationId(kpiDef.getOrganization().getId());
        response.setOrganizationName(kpiDef.getOrganization().getName());
        response.setReportingFrequency(kpiDef.getReportingFrequency());
        return response;
    }
}