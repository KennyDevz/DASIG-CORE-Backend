package edu.cit.dasig_core.features.committee.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.dasig_core.features.committee.dto.CreateCommitteeRequest;
import edu.cit.dasig_core.features.committee.dto.UpdateCommitteeRequest;
import edu.cit.dasig_core.features.committee.dto.CommitteeResponse;
import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.committee.repository.CommitteeRepository;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CommitteeService {

    private final CommitteeRepository committeeRepository;
    private final OrganizationRepository organizationRepository;

    public CommitteeService(CommitteeRepository committeeRepository, OrganizationRepository organizationRepository) {
        this.committeeRepository = committeeRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public CommitteeResponse createCommittee(CreateCommitteeRequest request) {
        if (committeeRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("A committee with this name already exists.");
        }

        Committee committee = new Committee();
        committee.setName(request.getName());
        committee.setDescription(request.getDescription());
        committee.setStatus("Active");

        if (request.getOrganizationIds() != null && !request.getOrganizationIds().isEmpty()) {
            List<Organization> orgs = organizationRepository.findAllById(request.getOrganizationIds());
            for (Organization org : orgs) {
                if (org.getCommittee() != null) {
                    org.getCommittee().getOrganizations().remove(org);
                }
                org.setCommittee(committee);
                committee.getOrganizations().add(org);
            }
            organizationRepository.saveAll(orgs);
        }

        Committee savedCommittee = committeeRepository.save(committee);
        return mapToResponse(savedCommittee);
    }

    @Transactional
    public CommitteeResponse updateCommittee(Long id, UpdateCommitteeRequest request) {
        Committee committee = committeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Committee not found with ID: " + id));

        if (committeeRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new IllegalArgumentException("Committee name is already in use by another entity.");
        }

        committee.setName(request.getName());
        committee.setDescription(request.getDescription());

        List<Organization> previouslyAssigned = new java.util.ArrayList<>(committee.getOrganizations());
        for (Organization org : previouslyAssigned) {
            org.setCommittee(null);
            organizationRepository.save(org);
        }
        committee.getOrganizations().clear();

        if (request.getOrganizationIds() != null && !request.getOrganizationIds().isEmpty()) {
            List<Organization> orgs = organizationRepository.findAllById(request.getOrganizationIds());
            for (Organization org : orgs) {
                if (org.getCommittee() != null && !org.getCommittee().getId().equals(id)) {
                    org.getCommittee().getOrganizations().remove(org);
                }
                org.setCommittee(committee);
                committee.getOrganizations().add(org);
            }
            organizationRepository.saveAll(orgs);
        }

        Committee updatedCommittee = committeeRepository.save(committee);
        return mapToResponse(updatedCommittee);
    }

    public CommitteeResponse getCommitteeById(Long id) {
        Committee committee = committeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Committee not found with ID: " + id));
        return mapToResponse(committee);
    }

    public List<CommitteeResponse> getAllCommittees() {
        return committeeRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deactivateCommittee(Long id) {
        Committee committee = committeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Committee not found with ID: " + id));
        committee.setStatus("Inactive");
        committeeRepository.save(committee);
    }

    private CommitteeResponse mapToResponse(Committee committee) {
        CommitteeResponse response = new CommitteeResponse();
        response.setId(committee.getId());
        response.setName(committee.getName());
        response.setDescription(committee.getDescription());
        response.setStatus(committee.getStatus());
        response.setOrganizationIds(
                committee.getOrganizations().stream()
                        .map(org -> org.getId())
                        .collect(Collectors.toList())
        );
        return response;
    }
}
