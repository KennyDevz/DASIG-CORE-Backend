package edu.cit.dasig_core.features.organization.service;

import edu.cit.dasig_core.features.organization.dto.CreateOrganizationRequest;
import edu.cit.dasig_core.features.organization.dto.OrganizationResponse;
import edu.cit.dasig_core.features.organization.dto.UpdateOrganizationRequest;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    private OrganizationService organizationService;

    @BeforeEach
    void setUp() {
        organizationService = new OrganizationService(organizationRepository);
    }

    private CreateOrganizationRequest createRequest(String name) {
        CreateOrganizationRequest request = new CreateOrganizationRequest();
        request.setName(name);
        request.setDescription("desc");
        request.setAddress("123 Main St");
        request.setContactEmail("org@example.com");
        request.setContactNumber("0900000000");
        return request;
    }

    @Test
    void createOrganization_throwsWhenNameAlreadyExists() {
        CreateOrganizationRequest request = createRequest("Wildcat Labs");
        when(organizationRepository.existsByName("Wildcat Labs")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.createOrganization(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An organization with this name already exists.");

        verify(organizationRepository, never()).save(any());
    }

    @Test
    void createOrganization_savesWithActiveStatusOnSuccess() {
        CreateOrganizationRequest request = createRequest("Wildcat Labs");
        when(organizationRepository.existsByName("Wildcat Labs")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
            Organization org = invocation.getArgument(0);
            org.setId(1L);
            return org;
        });

        OrganizationResponse response = organizationService.createOrganization(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Wildcat Labs");
        assertThat(response.getStatus()).isEqualTo("Active");
    }

    @Test
    void updateOrganization_throwsWhenNotFound() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.empty());

        UpdateOrganizationRequest request = new UpdateOrganizationRequest();
        request.setName("New Name");
        request.setAddress("Addr");
        request.setContactEmail("a@example.com");

        assertThatThrownBy(() -> organizationService.updateOrganization(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Organization not found with ID: 1");
    }

    @Test
    void updateOrganization_throwsWhenNameTakenByAnotherOrganization() {
        Organization org = new Organization();
        org.setId(1L);
        org.setName("Old Name");
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(org));
        when(organizationRepository.existsByNameAndIdNot("Taken Name", 1L)).thenReturn(true);

        UpdateOrganizationRequest request = new UpdateOrganizationRequest();
        request.setName("Taken Name");
        request.setAddress("Addr");
        request.setContactEmail("a@example.com");

        assertThatThrownBy(() -> organizationService.updateOrganization(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Organization name is already in use by another entity.");
    }

    @Test
    void updateOrganization_clearsCommitteeWhenCommitteeIdIsNull() {
        Organization org = new Organization();
        org.setId(1L);
        org.setName("Old Name");
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(org));
        when(organizationRepository.existsByNameAndIdNot("New Name", 1L)).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateOrganizationRequest request = new UpdateOrganizationRequest();
        request.setName("New Name");
        request.setAddress("Addr");
        request.setContactEmail("a@example.com");
        request.setCommitteeId(null);

        OrganizationResponse response = organizationService.updateOrganization(1L, request);

        assertThat(response.getCommitteeId()).isNull();
    }

    @Test
    void deactivateOrganization_throwsWhenNotFound() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.deactivateOrganization(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Organization not found with ID: 1");
    }

    @Test
    void deactivateOrganization_setsStatusInactive() {
        Organization org = new Organization();
        org.setId(1L);
        org.setStatus("Active");
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(org));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        organizationService.deactivateOrganization(1L);

        assertThat(org.getStatus()).isEqualTo("Inactive");
    }

    @Test
    void getAllOrganizations_mapsEveryOrganization() {
        Organization a = new Organization();
        a.setId(1L);
        a.setName("Org A");
        Organization b = new Organization();
        b.setId(2L);
        b.setName("Org B");
        when(organizationRepository.findAll()).thenReturn(List.of(a, b));

        List<OrganizationResponse> responses = organizationService.getAllOrganizations();

        assertThat(responses).extracting(OrganizationResponse::getName)
                .containsExactlyInAnyOrder("Org A", "Org B");
    }

    @Test
    void getOrganizationById_throwsWhenNotFound() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.getOrganizationById(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Organization not found with ID: 1");
    }
}
