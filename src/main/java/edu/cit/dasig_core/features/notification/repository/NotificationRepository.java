package edu.cit.dasig_core.features.notification.repository;

import edu.cit.dasig_core.features.notification.model.Notification;
import edu.cit.dasig_core.features.notification.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** @deprecated replaced by {@link #existsByKpiDefinitionIdAndOrganizationIdAndDedupeKey}; remove in Phase 6. */
    @Deprecated
    boolean existsByKpiDefinitionIdAndOrganizationIdAndNotificationType(Long kpiDefinitionId, Long organizationId, NotificationType notificationType);

    List<Notification> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    /** Unread notifications for an organization whose KPI still exists and is not archived (sidebar badge). */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.organizationId = :organizationId AND n.status = 'UNREAD' "
            + "AND EXISTS (SELECT k.id FROM KpiDefinition k WHERE k.id = n.kpiDefinitionId AND UPPER(k.status) <> 'ARCHIVED')")
    long countUnreadByOrganizationId(@Param("organizationId") Long organizationId);

    void deleteByKpiDefinitionId(Long kpiDefinitionId);

    boolean existsByKpiDefinitionIdAndOrganizationIdAndDedupeKey(Long kpiDefinitionId, Long organizationId, String dedupeKey);

    void deleteByKpiDefinitionIdAndNotificationTypeIn(Long kpiDefinitionId, Collection<NotificationType> notificationTypes);

    /** The At Risk / Overdue notifications sent for one alert (one per organization). */
    List<Notification> findByAlertIdAndNotificationTypeIn(Long alertId, Collection<NotificationType> types);
}
