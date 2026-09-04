package edu.cit.dasig_core.features.committee.service;

import edu.cit.dasig_core.features.committee.dto.CommitteeResponse;
import edu.cit.dasig_core.features.committee.dto.CreateCommitteeRequest;
import edu.cit.dasig_core.features.committee.dto.UpdateCommitteeRequest;
import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.committee.repository.CommitteeRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommitteeServiceTest {

    @Mock
    private CommitteeRepository committeeRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private CommitteeService committeeService;

    @BeforeEach
    void setUp() {
        committeeService = new CommitteeService(committeeRepository, organizationRepository);
    }

    private Organization organization(Long id, Committee currentCommittee) {
        Organization org = new Organization();
        org.setId(id);
        org.setName("Org " + id);
        org.setCommittee(currentCommittee);
        if (currentCommittee != null) {
            currentCommittee.getOrganizations().add(org);
        }
        return org;
    }

    @Test
    void createCommittee_throwsWhenNameAlreadyExists() {
        CreateCommitteeRequest request = new CreateCommitteeRequest();
        request.setName("Tech Committee");
        when(committeeRepository.existsByName("Tech Committee")).thenReturn(true);

        assertThatThrownBy(() -> committeeService.createCommittee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A committee with this name already exists.");

        verify(committeeRepository, never()).save(any());
    }

    @Test
    void createCommittee_assignsRequestedOrganizations() {
        CreateCommitteeRequest request = new CreateCommitteeRequest();
        request.setName("Tech Committee");
        request.setDescription("desc");
        request.setOrganizationIds(List.of(1L, 2L));

        Organization org1 = organization(1L, null);
        Organization org2 = organization(2L, null);

        when(committeeRepository.existsByName("Tech Committee")).thenReturn(false);
        when(organizationRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(org1, org2));
        when(organizationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(committeeRepository.save(any(Committee.class))).thenAnswer(invocation -> {
            Committee committee = invocation.getArgument(0);
            committee.setId(10L);
            return committee;
        });

        CommitteeResponse response = committeeService.createCommittee(request);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getOrganizationIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(org1.getCommittee()).isNotNull();
        assertThat(org2.getCommittee()).isNotNull();
    }

    @Test
    void createCommittee_withNoOrganizationIds_savesEmptyCommittee() {
        CreateCommitteeRequest request = new CreateCommitteeRequest();
        request.setName("Empty Committee");
        request.setOrganizationIds(null);

        when(committeeRepository.existsByName("Empty Committee")).thenReturn(false);
        when(committeeRepository.save(any(Committee.class))).thenAnswer(invocation -> {
            Committee committee = invocation.getArgument(0);
            committee.setId(11L);
            return committee;
        });

        CommitteeResponse response = committeeService.createCommittee(request);

        assertThat(response.getOrganizationIds()).isEmpty();
        verify(organizationRepository, never()).findAllById(anyList());
    }

    @Test
    void updateCommittee_throwsWhenNotFound() {
        when(committeeRepository.findById(1L)).thenReturn(Optional.empty());

        UpdateCommitteeRequest request = new UpdateCommitteeRequest();
        request.setName("New Name");

        assertThatThrownBy(() -> committeeService.updateCommittee(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Committee not found with ID: 1");
    }

    @Test
    void updateCommittee_throwsWhenNameTakenByAnotherCommittee() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Old Name");
        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));
        when(committeeRepository.existsByNameAndIdNot("Taken", 1L)).thenReturn(true);

        UpdateCommitteeRequest request = new UpdateCommitteeRequest();
        request.setName("Taken");

        assertThatThrownBy(() -> committeeService.updateCommittee(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Committee name is already in use by another entity.");
    }

    @Test
    void updateCommittee_reassignsOrganizationsAndClearsPreviousOnes() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Old Name");
        Organization previouslyAssigned = organization(5L, committee);

        Organization newOrg = organization(6L, null);

        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));
        when(committeeRepository.existsByNameAndIdNot("New Name", 1L)).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(organizationRepository.findAllById(List.of(6L))).thenReturn(List.of(newOrg));
        when(organizationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(committeeRepository.save(any(Committee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCommitteeRequest request = new UpdateCommitteeRequest();
        request.setName("New Name");
        request.setOrganizationIds(List.of(6L));

        CommitteeResponse response = committeeService.updateCommittee(1L, request);

        assertThat(previouslyAssigned.getCommittee()).isNull();
        assertThat(response.getOrganizationIds()).containsExactly(6L);
    }

    @Test
    void getCommitteeById_throwsWhenNotFound() {
        when(committeeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> committeeService.getCommitteeById(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Committee not found with ID: 1");
    }

    @Test
    void getAllCommittees_mapsEveryCommittee() {
        Committee a = new Committee();
        a.setId(1L);
        a.setName("A");
        Committee b = new Committee();
        b.setId(2L);
        b.setName("B");
        when(committeeRepository.findAll()).thenReturn(List.of(a, b));

        List<CommitteeResponse> responses = committeeService.getAllCommittees();

        assertThat(responses).extracting(CommitteeResponse::getName).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void deactivateCommittee_throwsWhenNotFound() {
        when(committeeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> committeeService.deactivateCommittee(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Committee not found with ID: 1");
    }

    @Test
    void deactivateCommittee_setsStatusInactive() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setStatus("Active");
        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));
        when(committeeRepository.save(any(Committee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        committeeService.deactivateCommittee(1L);

        assertThat(committee.getStatus()).isEqualTo("Inactive");
    }
}
