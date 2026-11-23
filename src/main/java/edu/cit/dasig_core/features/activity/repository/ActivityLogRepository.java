package edu.cit.dasig_core.features.activity.repository;

import edu.cit.dasig_core.features.activity.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
}
