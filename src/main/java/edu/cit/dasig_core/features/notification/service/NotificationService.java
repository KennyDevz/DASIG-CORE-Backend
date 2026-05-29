package edu.cit.dasig_core.features.notification.service;

import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.notification.dto.NotificationDetailResponse;
import edu.cit.dasig_core.features.notification.dto.NotificationResponse;
import edu.cit.dasig_core.features.notification.model.Notification;
import edu.cit.dasig_core.features.notification.model.NotificationType;
import edu.cit.dasig_core.features.notification.repository.NotificationRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final UserRepository userRepository;

    @Value("${app.business-timezone:Asia/Manila}")
    private String businessTimezone;

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(businessTimezone));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getAllNotifications() {
        User user = resolveCurrentUser();
        validateNotificationViewerRole(user);

        List<Notification> notifications =
                notificationRepository.findByOrganizationIdOrderByCreatedAtDesc(user.getOrganizationId());

        return notifications.stream().map(this::toResponse).toList();
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

    @Transactional
    public void createDeadlineNotificationsForKpi(KpiDefinition kpi) {
        if (kpi == null || kpi.getId() == null || kpi.getDeadline() == null) {
            return;
        }

        long daysUntil = ChronoUnit.DAYS.between(today(), kpi.getDeadline());
        if (daysUntil == 7) {
            createNotificationIfAbsent(kpi, NotificationType.SEVEN_DAYS_BEFORE);
        } else if (daysUntil == 2) {
            createNotificationIfAbsent(kpi, NotificationType.TWO_DAYS_BEFORE);
        }
    }

    @Transactional
    public void createDeadlineNotificationsIfDue() {
        LocalDate today = today();
        List<KpiDefinition> kpis = kpiDefinitionRepository.findAll();

        for (KpiDefinition kpi : kpis) {
            if (kpi.getDeadline() == null) {
                continue;
            }

            long daysUntil = ChronoUnit.DAYS.between(today, kpi.getDeadline());
            if (daysUntil == 7) {
                createNotificationIfAbsent(kpi, NotificationType.SEVEN_DAYS_BEFORE);
            } else if (daysUntil == 2) {
                createNotificationIfAbsent(kpi, NotificationType.TWO_DAYS_BEFORE);
            }
        }
    }

    private void createNotificationIfAbsent(KpiDefinition kpi, NotificationType type) {
        if (notificationRepository.existsByKpiDefinitionIdAndNotificationType(kpi.getId(), type)) {
            return;
        }

        Long organizationId = kpi.getOrganization() != null
                ? kpi.getOrganization().getId()
                : null;
        if (organizationId == null) {
            throw new IllegalStateException(
                    "KPI " + kpi.getId() + " has no organization; cannot create notification.");
        }

        Notification notification = new Notification();
        notification.setKpiDefinitionId(kpi.getId());
        notification.setOrganizationId(organizationId);
        notification.setNotificationType(type);
        notification.setStatus(Notification.STATUS_UNREAD);
        notification.setMessage(buildMessage(kpi, type));
        notificationRepository.save(notification);
    }

    private String buildMessage(KpiDefinition kpi, NotificationType type) {
        int days = type.getDaysBeforeDeadline();
        return String.format(
                "Deadline alert: KPI \"%s\" is due in %d day%s on %s.",
                kpi.getName(),
                days,
                days == 1 ? "" : "s",
                kpi.getDeadline()
        );
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

    private NotificationResponse toResponse(Notification notification) {
        KpiDefinition kpi = loadKpiDefinition(notification.getKpiDefinitionId());

        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setKpiDefinitionId(notification.getKpiDefinitionId());
        response.setKpiName(kpi.getName());
        response.setOrganizationId(notification.getOrganizationId());
        response.setNotificationType(notification.getNotificationType());
        response.setDaysBeforeDeadline(notification.getNotificationType().getDaysBeforeDeadline());
        response.setDeadline(kpi.getDeadline());
        response.setStatus(notification.getStatus());
        response.setMessage(notification.getMessage());
        response.setCreatedAt(notification.getCreatedAt());
        return response;
    }

    private NotificationDetailResponse toDetailResponse(Notification notification, KpiDefinition kpi) {
        NotificationDetailResponse response = new NotificationDetailResponse();
        response.setId(notification.getId());
        response.setKpiDefinitionId(notification.getKpiDefinitionId());
        response.setKpiName(kpi.getName());
        response.setKpiDescription(kpi.getDescription());
        response.setOrganizationId(notification.getOrganizationId());
        response.setOrganizationName(kpi.getOrganization().getName());
        response.setNotificationType(notification.getNotificationType());
        response.setDaysBeforeDeadline(notification.getNotificationType().getDaysBeforeDeadline());
        response.setDeadline(kpi.getDeadline());
        response.setStatus(notification.getStatus());
        response.setMessage(notification.getMessage());
        response.setCreatedAt(notification.getCreatedAt());
        return response;
    }
}
