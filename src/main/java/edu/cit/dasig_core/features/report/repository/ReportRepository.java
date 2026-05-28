package edu.cit.dasig_core.features.report.repository;

import edu.cit.dasig_core.features.report.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByOrganizationIdOrderByGeneratedAtDesc(Long organizationId);
}