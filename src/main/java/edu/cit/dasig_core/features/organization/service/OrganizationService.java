package edu.cit.dasig_core.features.organization.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.dasig_core.features.organization.dto.CreateOrganizationRequest;
import edu.cit.dasig_core.features.organization.dto.OrganizationResponse;
import edu.cit.dasig_core.features.organization.dto.UpdateOrganizationRequest;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        if (organizationRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("An organization with this name already exists.");
        }

        Organization org = new Organization();
        org.setName(request.getName());
        org.setDescription(request.getDescription());
        org.setAddress(request.getAddress());
        org.setContactEmail(request.getContactEmail());
        org.setContactNumber(request.getContactNumber());
        org.setStatus("Active");

        Organization savedOrg = organizationRepository.save(org);

        return mapToResponse(savedOrg);
    }

    @Transactional
    public OrganizationResponse updateOrganization(Long id, UpdateOrganizationRequest request) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found with ID: " + id));

        if (organizationRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new IllegalArgumentException("Organization name is already in use by another entity.");
        }

        org.setName(request.getName());
        org.setDescription(request.getDescription());
        org.setAddress(request.getAddress());
        org.setContactEmail(request.getContactEmail());
        org.setContactNumber(request.getContactNumber());

        Organization updatedOrg = organizationRepository.save(org);
        return mapToResponse(updatedOrg);
    }

    public OrganizationResponse getOrganizationById(Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found with ID: " + id));
        return mapToResponse(org);
    }

    public List<OrganizationResponse> getAllOrganizations() {
        return organizationRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deactivateOrganization(Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found with ID: " + id));
        
        org.setStatus("Inactive");
        organizationRepository.save(org);
    }

    private OrganizationResponse mapToResponse(Organization org) {
        OrganizationResponse response = new OrganizationResponse();
        response.setId(org.getId());
        response.setName(org.getName());
        response.setDescription(org.getDescription());
        response.setAddress(org.getAddress());
        response.setContactEmail(org.getContactEmail());
        response.setContactNumber(org.getContactNumber());
        response.setStatus(org.getStatus());
        response.setCommitteeIds(
                org.getCommittees().stream()
                        .map(committee -> committee.getId())
                        .collect(Collectors.toList())
        );
        response.setCommitteeNames(
                org.getCommittees().stream()
                        .map(committee -> committee.getName())
                        .collect(Collectors.toList())
        );
        return response;
    }
}