package edu.cit.dasig_core.features.notification.repository;

import edu.cit.dasig_core.features.notification.model.Notification;
import edu.cit.dasig_core.features.notification.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByKpiDefinitionIdAndNotificationType(Long kpiDefinitionId, NotificationType notificationType);

    List<Notification> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
