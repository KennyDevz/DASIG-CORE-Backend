package edu.cit.dasig_core.features.kpisubmission.repository;

import edu.cit.dasig_core.features.kpisubmission.model.SubmissionDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SubmissionDocumentRepository extends JpaRepository<SubmissionDocument, Long> {

    List<SubmissionDocument> findBySubmissionId(Long submissionId);

    /** Documents for several submissions at once (submission history lists). */
    List<SubmissionDocument> findBySubmissionIdIn(Collection<Long> submissionIds);

    void deleteBySubmissionId(Long submissionId);
}
