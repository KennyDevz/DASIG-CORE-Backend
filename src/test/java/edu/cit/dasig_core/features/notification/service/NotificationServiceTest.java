package edu.cit.dasig_core.features.notification.service;

import edu.cit.dasig_core.features.alert.model.Alert;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
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
        notification.setAlertId(7L);

        when(notificationRepository.findByOrganizationIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(notification));
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.of(kpiWithDeadline(LocalDate.now())));

        List<NotificationResponse> result = notificationService.getAllNotifications();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrganizationId()).isEqualTo(9L);
        assertThat(result.get(0).getAlertId()).isEqualTo(7L);
    }

    @Test
    void getAllNotifications_cleansUpOrphanedNotificationWhenKpiNotFound() {
        User staff = staffUser(9L);
        authenticateAs("staff@example.com");
        when(userRepository.findByEmail("staff@example.com")).thenReturn(Optional.of(staff));

        Notification notification = new Notification();
        notification.setId(1L);
        notification.setKpiDefinitionId(99L);
        notification.setOrganizationId(9L);

        when(notificationRepository.findByOrganizationIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(notification));
        when(kpiDefinitionRepository.findById(99L)).thenReturn(Optional.empty());

        List<NotificationResponse> result = notificationService.getAllNotifications();

        assertThat(result).isEmpty();
        verify(notificationRepository).delete(notification);
    }

    @Test
    void getAllNotifications_suppressesNotificationsForArchivedKpis() {
        User staff = staffUser(9L);
        authenticateAs("staff@example.com");
        when(userRepository.findByEmail("staff@example.com")).thenReturn(Optional.of(staff));

        Notification notification = new Notification();
        notification.setId(1L);
        notification.setKpiDefinitionId(1L);
        notification.setOrganizationId(9L);

        KpiDefinition archivedKpi = kpiWithDeadline(LocalDate.now());
        archivedKpi.setStatus(KpiDefinition.STATUS_ARCHIVED);

        when(notificationRepository.findByOrganizationIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(notification));
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.of(archivedKpi));

        List<NotificationResponse> result = notificationService.getAllNotifications();

        assertThat(result).isEmpty();
        verify(notificationRepository).delete(notification);
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
        kpi.getCommittee().setOrganizations(List.of(org));

        String expectedKey = "SEVEN_DAYS_BEFORE:" + kpi.getDeadline();
        when(notificationRepository.existsByKpiDefinitionIdAndOrganizationIdAndDedupeKey(
                1L, 9L, expectedKey)).thenReturn(false);

        notificationService.createDeadlineNotificationsForKpi(kpi);

        verify(notificationRepository).save(argThat(n ->
                n.getNotificationType() == NotificationType.SEVEN_DAYS_BEFORE
                        && n.getOrganizationId().equals(9L)
                        && n.getKpiDefinitionId().equals(1L)
                        && expectedKey.equals(n.getDedupeKey())
                        && n.getAlertId() == null));
    }

    @Test
    void createDeadlineNotificationsForKpi_doesNotDuplicateWhenNotificationAlreadyExists() {
        KpiDefinition kpi = kpiWithDeadline(LocalDate.now().plusDays(7));
        Organization org = new Organization();
        org.setId(9L);
        kpi.getCommittee().setOrganizations(List.of(org));

        when(notificationRepository.existsByKpiDefinitionIdAndOrganizationIdAndDedupeKey(
                1L, 9L, "SEVEN_DAYS_BEFORE:" + kpi.getDeadline())).thenReturn(true);

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

    // ---- Alert notifications (UC-4.3) ----

    private KpiDefinition kpiForCommittee() {
        KpiDefinition kpi = kpiWithDeadline(LocalDate.of(2026, 5, 1));
        Organization orgA = new Organization();
        orgA.setId(10L);
        Organization orgB = new Organization();
        orgB.setId(11L);
        kpi.getCommittee().setOrganizations(List.of(orgA, orgB));
        return kpi;
    }

    private Alert alert(String type) {
        Alert alert = new Alert();
        alert.setId(7L);
        alert.setAlertType(type);
        return alert;
    }

    @Test
    void notifyAlertCreated_sendsOverdueToEveryOrganizationInCommittee() {
        notificationService.notifyAlertCreated(kpiForCommittee(), alert(Alert.TYPE_OVERDUE));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getOrganizationId).containsExactly(10L, 11L);
        Notification first = saved.getAllValues().get(0);
        assertThat(first.getNotificationType()).isEqualTo(NotificationType.OVERDUE);
        assertThat(first.getAlertId()).isEqualTo(7L);
        assertThat(first.getDedupeKey()).isEqualTo("OVERDUE:alert-7");
        assertThat(first.getMessage()).contains("Revenue Target").contains("May 01, 2026");
    }

    @Test
    void notifyAlertCreated_skipsOrganizationsAlreadyNotified() {
        when(notificationRepository.existsByKpiDefinitionIdAndOrganizationIdAndDedupeKey(1L, 10L, "AT_RISK:alert-7"))
                .thenReturn(true);
        when(notificationRepository.existsByKpiDefinitionIdAndOrganizationIdAndDedupeKey(1L, 11L, "AT_RISK:alert-7"))
                .thenReturn(false);

        notificationService.notifyAlertCreated(kpiForCommittee(), alert(Alert.TYPE_AT_RISK));

        verify(notificationRepository, times(1)).save(argThat(n -> n.getOrganizationId().equals(11L)));
    }

    @Test
    void notifyAlertAcknowledged_includesAdminRemark() {
        Alert alert = alert(Alert.TYPE_AT_RISK);
        alert.setAdminRemark("Please submit your progress this week.");

        notificationService.notifyAlertAcknowledged(kpiForCommittee(), alert);

        verify(notificationRepository, times(2)).save(argThat(n ->
                n.getNotificationType() == NotificationType.ALERT_ACKNOWLEDGED
                        && n.getMessage().contains("Please submit your progress this week.")));
    }

    @Test
    void notifyAlertAcknowledged_addsRemarkToExistingAlertNotification_andMarksUnread() {
        Notification overdueCard = new Notification();
        overdueCard.setNotificationType(NotificationType.OVERDUE);
        overdueCard.setStatus(Notification.STATUS_READ);
        overdueCard.setMessage("Overdue: KPI \"Revenue Target\" passed its deadline.");
        when(notificationRepository.findByAlertIdAndNotificationTypeIn(eq(7L), anyCollection()))
                .thenReturn(List.of(overdueCard));

        Alert alert = alert(Alert.TYPE_OVERDUE);
        alert.setAdminRemark("Please do something");
        notificationService.notifyAlertAcknowledged(kpiForCommittee(), alert);

        assertThat(overdueCard.getMessage())
                .endsWith("The Admin acknowledged this alert. Remark: \"Please do something\"");
        assertThat(overdueCard.getStatus()).isEqualTo(Notification.STATUS_UNREAD);
        verify(notificationRepository).saveAll(List.of(overdueCard));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyDeadlineUpdated_includesOldAndNewDatesAndRemark() {
        notificationService.notifyDeadlineUpdated(kpiForCommittee(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30), "Extended by one month.");

        verify(notificationRepository, times(2)).save(argThat(n ->
                n.getNotificationType() == NotificationType.DEADLINE_UPDATED
                        && n.getDedupeKey().equals("DEADLINE_UPDATED:2026-05-01->2026-06-30")
                        && n.getAlertId() == null
                        && n.getMessage().contains("May 01, 2026")
                        && n.getMessage().contains("Jun 30, 2026")
                        && n.getMessage().contains("Extended by one month.")));
    }
}
