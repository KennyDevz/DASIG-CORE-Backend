package edu.cit.dasig_core.features.notification.service;

import edu.cit.dasig_core.features.alert.model.Alert;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.notification.dto.NotificationDetailResponse;
import edu.cit.dasig_core.features.notification.dto.NotificationResponse;
import edu.cit.dasig_core.features.notification.model.Notification;
import edu.cit.dasig_core.features.notification.model.NotificationKeys;
import edu.cit.dasig_core.features.notification.model.NotificationType;
import edu.cit.dasig_core.features.notification.repository.NotificationRepository;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);

    @Value("${app.business-timezone:Asia/Manila}")
    private String businessTimezone;

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(businessTimezone));
    }

    @Transactional
    public List<NotificationResponse> getAllNotifications() {
        User user = resolveCurrentUser();
        validateNotificationViewerRole(user);

        List<Notification> notifications =
                notificationRepository.findByOrganizationIdOrderByCreatedAtDesc(user.getOrganizationId());

        // Load every KPI the notifications refer to in one query
        Set<Long> kpiIds = notifications.stream().map(Notification::getKpiDefinitionId).collect(Collectors.toSet());
        Map<Long, KpiDefinition> kpis = kpiDefinitionRepository.findAllById(kpiIds).stream()
                .collect(Collectors.toMap(KpiDefinition::getId, kpi -> kpi));

        List<NotificationResponse> responses = new ArrayList<>();
        for (Notification notification : notifications) {
            KpiDefinition kpi = kpis.get(notification.getKpiDefinitionId());
            if (kpi == null) {
                // Self-healing: Clean up orphaned notification whose KPI was deleted
                notificationRepository.delete(notification);
                continue;
            }
            if (kpi.isArchived()) {
                // Suppress notifications for archived KPIs
                notificationRepository.delete(notification);
                continue;
            }
            responses.add(toResponse(notification, kpi));
        }
        return responses;
    }

    /** Unread notifications for the user's organization (sidebar badge), counted in one query. */
    @Transactional(readOnly = true)
    public long countUnreadNotifications() {
        User user = resolveCurrentUser();
        validateNotificationViewerRole(user);

        return notificationRepository.countUnreadByOrganizationId(user.getOrganizationId());
    }

    @Transactional(readOnly = true)
    public NotificationDetailResponse getNotificationById(Long id) {
        User user = resolveCurrentUser();
        validateNotificationViewerRole(user);

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found with ID: " + id));

        validateNotificationAccess(notification, user);

        KpiDefinition kpiDefinition = loadKpiDefinition(notification.getKpiDefinitionId());
        return toDetailResponse(notification, kpiDefinition);
    }

    @Transactional
    public NotificationDetailResponse markAsRead(Long id) {
        User user = resolveCurrentUser();
        validateNotificationViewerRole(user);

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found with ID: " + id));

        validateNotificationAccess(notification, user);

        if (Notification.STATUS_READ.equals(notification.getStatus())) {
            throw new IllegalArgumentException("Notification is already marked as read.");
        }

        notification.setStatus(Notification.STATUS_READ);
        Notification saved = notificationRepository.save(notification);
        KpiDefinition kpiDefinition = loadKpiDefinition(saved.getKpiDefinitionId());
        return toDetailResponse(saved, kpiDefinition);
    }

    // ---- Alert notifications (UC-4.3) ----

    /** A new At Risk or Overdue alert was created for the KPI. */
    @Transactional
    public void notifyAlertCreated(KpiDefinition kpi, Alert alert) {
        boolean overdue = Alert.TYPE_OVERDUE.equals(alert.getAlertType());
        NotificationType type = overdue ? NotificationType.OVERDUE : NotificationType.AT_RISK;
        String message = overdue
                ? String.format("Overdue: KPI \"%s\" passed its deadline on %s without meeting the target.",
                        kpi.getName(), format(kpi.getDeadline()))
                : String.format("At Risk: KPI \"%s\" is behind pace for its %s deadline.",
                        kpi.getName(), format(kpi.getDeadline()));

        sendToCommittee(kpi, type, NotificationKeys.forAlert(type, alert.getId()), alert.getId(), message);
    }

    /**
     * The Admin acknowledged an alert. The remark is added to the alert's existing At Risk / Overdue
     * notification, which becomes unread again, so the committee sees one card per alert.
     * Older alerts without such a notification get a separate "Alert acknowledged" notification.
     */
    @Transactional
    public void notifyAlertAcknowledged(KpiDefinition kpi, Alert alert) {
        List<Notification> alertNotifications = notificationRepository.findByAlertIdAndNotificationTypeIn(
                alert.getId(), List.of(NotificationType.AT_RISK, NotificationType.OVERDUE));

        if (alertNotifications.isEmpty()) {
            String alertLabel = Alert.TYPE_OVERDUE.equals(alert.getAlertType()) ? "Overdue" : "At Risk";
            String message = String.format("The Admin acknowledged the %s alert for KPI \"%s\". Remark: \"%s\"",
                    alertLabel, kpi.getName(), alert.getAdminRemark());
            sendToCommittee(kpi, NotificationType.ALERT_ACKNOWLEDGED,
                    NotificationKeys.forAlert(NotificationType.ALERT_ACKNOWLEDGED, alert.getId()), alert.getId(), message);
            return;
        }

        String remark = String.format(" The Admin acknowledged this alert. Remark: \"%s\"", alert.getAdminRemark());
        for (Notification notification : alertNotifications) {
            notification.setMessage(notification.getMessage() + remark);
            notification.setStatus(Notification.STATUS_UNREAD); // show it as new again
        }
        notificationRepository.saveAll(alertNotifications);
    }

    /** The Admin changed the KPI deadline; includes the latest Admin remark if there is one. */
    @Transactional
    public void notifyDeadlineUpdated(KpiDefinition kpi, LocalDate oldDeadline, LocalDate newDeadline, String latestRemark) {
        String message = String.format("The deadline for KPI \"%s\" changed from %s to %s.",
                kpi.getName(), format(oldDeadline), format(newDeadline));
        if (latestRemark != null && !latestRemark.isBlank()) {
            message += String.format(" Admin remark: \"%s\"", latestRemark);
        }

        sendToCommittee(kpi, NotificationType.DEADLINE_UPDATED,
                NotificationKeys.deadlineUpdated(oldDeadline, newDeadline), null, message);
    }

    /** Saves one notification per organization under the KPI's committee, skipping duplicates. */
    private void sendToCommittee(KpiDefinition kpi, NotificationType type, String dedupeKey, Long alertId, String message) {
        if (kpi.getCommittee() == null) {
            return;
        }

        for (Organization org : kpi.getCommittee().getOrganizations()) {
            if (notificationRepository.existsByKpiDefinitionIdAndOrganizationIdAndDedupeKey(kpi.getId(), org.getId(), dedupeKey)) {
                continue;
            }

            Notification notification = new Notification();
            notification.setKpiDefinitionId(kpi.getId());
            notification.setOrganizationId(org.getId());
            notification.setNotificationType(type);
            notification.setDedupeKey(dedupeKey);
            notification.setAlertId(alertId);
            notification.setStatus(Notification.STATUS_UNREAD);
            notification.setMessage(message);
            notificationRepository.save(notification);
        }
    }

    private String format(LocalDate date) {
        return date != null ? date.format(DATE_FORMAT) : "-";
    }

    private KpiDefinition loadKpiDefinition(Long kpiDefinitionId) {
        return kpiDefinitionRepository.findById(kpiDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "KPI definition not found with ID: " + kpiDefinitionId));
    }

    private void validateNotificationAccess(Notification notification, User user) {
        if (user.getOrganizationId() == null
                || !user.getOrganizationId().equals(notification.getOrganizationId())) {
            throw new IllegalArgumentException("You do not have access to this notification.");
        }
    }

    private void validateNotificationViewerRole(User user) {
        if (!"TBI_MANAGER".equals(user.getRole()) && !"STAFF".equals(user.getRole())) {
            throw new IllegalArgumentException("Only TBI Managers and Staff can view notifications.");
        }

        if (user.getOrganizationId() == null) {
            throw new IllegalArgumentException("Organization is required to view notifications.");
        }
    }

    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication is required.");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

        if (!"Active".equals(user.getStatus())) {
            throw new IllegalArgumentException("Account is not active.");
        }

        return user;
    }

    private NotificationResponse toResponse(Notification notification, KpiDefinition kpi) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setKpiDefinitionId(notification.getKpiDefinitionId());
        response.setKpiName(kpi.getName());
        response.setOrganizationId(notification.getOrganizationId());
        response.setNotificationType(notification.getNotificationType());
        response.setDeadline(kpi.getDeadline());
        response.setStatus(notification.getStatus());
        response.setMessage(notification.getMessage());
        response.setCreatedAt(notification.getCreatedAt());
        response.setAlertId(notification.getAlertId());
        return response;
    }

    private NotificationDetailResponse toDetailResponse(Notification notification, KpiDefinition kpi) {
        NotificationDetailResponse response = new NotificationDetailResponse();
        response.setId(notification.getId());
        response.setKpiDefinitionId(notification.getKpiDefinitionId());
        response.setKpiName(kpi.getName());
        response.setKpiDescription(kpi.getDescription());
        response.setOrganizationId(notification.getOrganizationId());
        String orgName = organizationRepository.findById(notification.getOrganizationId())
                .map(Organization::getName)
                .orElse(null);
        response.setOrganizationName(orgName);
        response.setNotificationType(notification.getNotificationType());
        response.setDeadline(kpi.getDeadline());
        response.setStatus(notification.getStatus());
        response.setMessage(notification.getMessage());
        response.setCreatedAt(notification.getCreatedAt());
        response.setAlertId(notification.getAlertId());
        return response;
    }
}
