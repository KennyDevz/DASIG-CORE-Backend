package edu.cit.dasig_core.features.notification.service;

import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.notification.dto.NotificationDetailResponse;
import edu.cit.dasig_core.features.notification.dto.NotificationResponse;
import edu.cit.dasig_core.features.notification.model.Notification;
import edu.cit.dasig_core.features.notification.model.NotificationType;
import edu.cit.dasig_core.features.notification.repository.NotificationRepository;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private KpiDefinitionRepository kpiDefinitionRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserRepository userRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository, kpiDefinitionRepository, organizationRepository, userRepository);
        ReflectionTestUtils.setField(notificationService, "businessTimezone", "Asia/Manila");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User staffUser(Long orgId) {
        User user = new User();
        user.setId(1L);
        user.setEmail("staff@example.com");
        user.setRole("STAFF");
        user.setOrganizationId(orgId);
        user.setStatus("Active");
        return user;
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private KpiDefinition kpiWithDeadline(LocalDate deadline) {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(1L);
        kpi.setName("Revenue Target");
        Committee committee = new Committee();
        committee.setId(5L);
        kpi.setCommittee(committee);
        kpi.setDeadline(deadline);
        return kpi;
    }

    // ---- getAllNotifications ----

    @Test
    void getAllNotifications_throwsWhenRoleIsNotStaffOrTbiManager() {
        User admin = staffUser(1L);
        admin.setRole("DASIG_ADMIN");
        authenticateAs("staff@example.com");
        when(userRepository.findByEmail("staff@example.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> notificationService.getAllNotifications())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only TBI Managers and Staff can view notifications.");
    }

    @Test
    void getAllNotifications_throwsWhenOrganizationMissing() {
        User staff = staffUser(null);
        authenticateAs("staff@example.com");
        when(userRepository.findByEmail("staff@example.com")).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> notificationService.getAllNotifications())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Organization is required to view notifications.");
    }

    @Test
    void getAllNotifications_returnsNotificationsForUserOrganization() {
        User staff = staffUser(9L);
        authenticateAs("staff@example.com");
        when(userRepository.findByEmail("staff@example.com")).thenReturn(Optional.of(staff));

        Notification notification = new Notification();
        notification.setId(1L);
        notification.setKpiDefinitionId(1L);
        notification.setOrganizationId(9L);
        notification.setNotificationType(NotificationType.SEVEN_DAYS_BEFORE);
        notification.setStatus(Notification.STATUS_UNREAD);
        notification.setMessage("msg");

        when(notificationRepository.findByOrganizationIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(notification));
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.of(kpiWithDeadline(LocalDate.now())));

        List<NotificationResponse> result = notificationService.getAllNotifications();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrganizationId()).isEqualTo(9L);
    }

    // ---- getNotificationById / markAsRead ----

    @Test
    void getNotificationById_throwsWhenAccessedByDifferentOrganization() {
        User staff = staffUser(9L);
        authenticateAs("staff@example.com");
        when(userRepository.findByEmail("staff@example.com")).thenReturn(Optional.of(staff));

        Notification notification = new Notification();
        notification.setId(1L);
        notification.setOrganizationId(999L); // different org
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.getNotificationById(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You do not have access to this notification.");
    }

    @Test
    void markAsRead_throwsWhenAlreadyRead() {
        User staff = staffUser(9L);
        authenticateAs("staff@example.com");
        when(userRepository.findByEmail("staff@example.com")).thenReturn(Optional.of(staff));

        Notification notification = new Notification();
        notification.setId(1L);
        notification.setOrganizationId(9L);
        notification.setStatus(Notification.STATUS_READ);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification is already marked as read.");
    }

    @Test
    void markAsRead_updatesStatusToReadOnSuccess() {
        User staff = staffUser(9L);
        authenticateAs("staff@example.com");
        when(userRepository.findByEmail("staff@example.com")).thenReturn(Optional.of(staff));

        Notification notification = new Notification();
        notification.setId(1L);
        notification.setKpiDefinitionId(1L);
        notification.setOrganizationId(9L);
        notification.setNotificationType(NotificationType.SEVEN_DAYS_BEFORE);
        notification.setStatus(Notification.STATUS_UNREAD);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.of(kpiWithDeadline(LocalDate.now())));

        NotificationDetailResponse response = notificationService.markAsRead(1L);

        assertThat(notification.getStatus()).isEqualTo(Notification.STATUS_READ);
        assertThat(response.getStatus()).isEqualTo(Notification.STATUS_READ);
    }

    // ---- createDeadlineNotificationsForKpi ----

    @Test
    void createDeadlineNotificationsForKpi_doesNothingWhenKpiOrDeadlineIsNull() {
        notificationService.createDeadlineNotificationsForKpi(null);

        KpiDefinition kpiWithoutDeadline = kpiWithDeadline(null);
        notificationService.createDeadlineNotificationsForKpi(kpiWithoutDeadline);

        verifyNoInteractions(organizationRepository, notificationRepository);
    }

    @Test
    void createDeadlineNotificationsForKpi_createsSevenDayNotificationWhenExactlySevenDaysOut() {
        KpiDefinition kpi = kpiWithDeadline(LocalDate.now().plusDays(7));
        Organization org = new Organization();
        org.setId(9L);

        when(organizationRepository.findByCommitteeId(5L)).thenReturn(List.of(org));
        when(notificationRepository.existsByKpiDefinitionIdAndOrganizationIdAndNotificationType(
                1L, 9L, NotificationType.SEVEN_DAYS_BEFORE)).thenReturn(false);

        notificationService.createDeadlineNotificationsForKpi(kpi);

        verify(notificationRepository).save(argThat(n ->
                n.getNotificationType() == NotificationType.SEVEN_DAYS_BEFORE
                        && n.getOrganizationId().equals(9L)
                        && n.getKpiDefinitionId().equals(1L)));
    }

    @Test
    void createDeadlineNotificationsForKpi_doesNotDuplicateWhenNotificationAlreadyExists() {
        KpiDefinition kpi = kpiWithDeadline(LocalDate.now().plusDays(7));
        Organization org = new Organization();
        org.setId(9L);

        when(organizationRepository.findByCommitteeId(5L)).thenReturn(List.of(org));
        when(notificationRepository.existsByKpiDefinitionIdAndOrganizationIdAndNotificationType(
                1L, 9L, NotificationType.SEVEN_DAYS_BEFORE)).thenReturn(true);

        notificationService.createDeadlineNotificationsForKpi(kpi);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createDeadlineNotificationsForKpi_doesNothingWhenNotSevenOrTwoDaysOut() {
        KpiDefinition kpi = kpiWithDeadline(LocalDate.now().plusDays(5));

        notificationService.createDeadlineNotificationsForKpi(kpi);

        verifyNoInteractions(organizationRepository);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createDeadlineNotificationsForKpi_skipsWhenCommitteeIsNull() {
        KpiDefinition kpi = kpiWithDeadline(LocalDate.now().plusDays(7));
        kpi.setCommittee(null);

        notificationService.createDeadlineNotificationsForKpi(kpi);

        verifyNoInteractions(organizationRepository, notificationRepository);
    }
}
