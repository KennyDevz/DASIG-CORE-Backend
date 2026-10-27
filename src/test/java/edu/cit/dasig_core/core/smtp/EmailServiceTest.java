package edu.cit.dasig_core.core.smtp;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String FRONTEND_URL = "https://www.dasig-core.site";
    // Plain-text URL (bare & separator) — used for plain-text body assertions.
    private static final String ALERT_URL = FRONTEND_URL + "/dashboard/admin/alerts?alertId=42&loginHint=admin%40dasig-core.site";
    // HTML URL (& escaped to &amp; by HtmlUtils.htmlEscape) — used for HTML body assertions.
    private static final String ALERT_URL_HTML = ALERT_URL.replace("&", "&amp;");

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "frontendUrl", FRONTEND_URL);
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void kpiAlertEmail_containsBothCtaButtonAndRawFallback() throws Exception {
        emailService.sendKpiAlertEmail(
                "admin@dasig-core.site", "Admin", "OVERDUE", "CRITICAL", "Graduate Employment Rate", 42L);

        String html = htmlBody();
        String plainText = plainTextBody();

        // styled CTA button is restored
        assertThat(html).contains(">Review Alert</a>");
        assertThat(html).contains("background-color: #007bff");

        // the deep link is also visible as raw text below the button (anti-phishing)
        // NOTE: HtmlUtils.htmlEscape escapes & to &amp; inside HTML attributes
        assertThat(html).contains("href=\"" + ALERT_URL_HTML + "\"");
        assertThat(html).contains(">" + ALERT_URL_HTML + "</a>");
        assertThat(html).contains("copy and paste this link into your browser");

        // plain text also carries the raw link (bare & separator, no escaping)
        assertThat(plainText).contains(ALERT_URL);

        // header logo is restored
        assertThat(html).contains("dasig_logo.svg");
    }

    @Test
    void kpiAlertEmail_withoutAlertId_linksToAlertsPage() throws Exception {
        emailService.sendKpiAlertEmail(
                "admin@dasig-core.site", "Admin", "AT_RISK", "WARNING", "Graduate Employment Rate", null);

        assertThat(htmlBody()).contains("href=\"" + FRONTEND_URL + "/dashboard/admin/alerts\"");
    }

    @Test
    void kpiAssignedEmail_listsKpiDetailsAndLinksToMemberKpiPage() throws Exception {
        emailService.sendKpiAssignedEmail("member@gmail.com", "Member", "STAFF", 10L,
                "Startups Incubated", "Committee A", "100 startups", LocalDate.of(2026, 12, 5));

        String html = htmlBody();
        assertThat(mimeMessage.getSubject()).isEqualTo("DASIG Core - New KPI assigned: Startups Incubated");
        assertThat(html).contains("New KPI Assigned", "Startups Incubated", "Committee A", "100 startups", "Dec 05, 2026");
        assertThat(html).contains("href=\"" + FRONTEND_URL + "/dashboard/staff/kpis/10\"");
        assertThat(html).contains(">View KPI</a>");
        assertThat(html).doesNotContain("loginHint");
        assertThat(plainTextBody()).contains(FRONTEND_URL + "/dashboard/staff/kpis/10");
    }

    @Test
    void kpiDeadlineUpdatedEmail_showsBothDatesAndRemark_andLinksToLeadKpiPage() throws Exception {
        emailService.sendKpiDeadlineUpdatedEmail("lead@test.com", "Lead", "TBI_MANAGER", 10L,
                "Startups Incubated", "Committee A", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 5),
                "Extended after <review>");

        String html = htmlBody();
        assertThat(mimeMessage.getSubject()).isEqualTo("DASIG Core - KPI deadline updated: Startups Incubated");
        assertThat(html).contains("Nov 01, 2026", "Dec 05, 2026");
        // user-entered text is HTML-escaped
        assertThat(html).contains("Extended after &lt;review&gt;");
        assertThat(html).contains("href=\"" + FRONTEND_URL + "/dashboard/tbi_manager/kpis/10\"");
    }

    @Test
    void submissionForReviewEmail_linksLeadToSubmissionHistory() throws Exception {
        emailService.sendSubmissionForReviewEmail("lead@test.com", "Lead", "Startups Incubated", "TBI Alpha",
                "Maria", "40 startups", "Due by Dec 15, 2026", LocalDate.of(2026, 10, 20), "SUB-0050");

        String html = htmlBody();
        assertThat(mimeMessage.getSubject()).isEqualTo("DASIG Core - KPI submission to review: Startups Incubated");
        assertThat(html).contains("TBI Alpha", "Maria", "40 startups", "SUB-0050", "Oct 20, 2026");
        assertThat(html).contains("href=\"" + FRONTEND_URL + "/dashboard/tbi_manager/submission-history\"");
        assertThat(html).contains(">Review Submission</a>");
    }

    @Test
    void submissionReviewedEmail_showsRejectionReason_andLinksMemberToSubmissionHistory() throws Exception {
        emailService.sendSubmissionReviewedEmail("member@gmail.com", "Member", "Startups Incubated", "40 startups",
                "SUB-0050", false, "Lead", "Missing <evidence>");

        String html = htmlBody();
        assertThat(mimeMessage.getSubject()).isEqualTo("DASIG Core - Your KPI submission was rejected: Startups Incubated");
        assertThat(html).contains("Rejected", "Missing &lt;evidence&gt;");
        assertThat(html).contains("href=\"" + FRONTEND_URL + "/dashboard/staff/submission-history\"");
    }

    @Test
    void submissionReviewedEmail_approvedHasNoReason() throws Exception {
        emailService.sendSubmissionReviewedEmail("member@gmail.com", "Member", "Startups Incubated", "40 startups",
                "SUB-0050", true, "Lead", null);

        assertThat(mimeMessage.getSubject()).isEqualTo("DASIG Core - Your KPI submission was approved: Startups Incubated");
        assertThat(htmlBody()).contains("Approved").doesNotContain("Reason:");
    }

    @Test
    void finalValueApprovedEmail_linksAdminToKpiDetail() throws Exception {
        emailService.sendFinalValueApprovedEmail("admin@gmail.com", "Admin", 10L, "Startups Incubated", "Committee A",
                "TBI Alpha", "40 startups", "Due by Dec 15, 2026", "Lead");

        String html = htmlBody();
        assertThat(mimeMessage.getSubject()).isEqualTo("DASIG Core - Final KPI value approved: Startups Incubated");
        assertThat(html).contains("Committee A", "TBI Alpha", "40 startups", "Lead");
        assertThat(html).contains("href=\"" + FRONTEND_URL + "/dashboard/admin/kpis/10\"");
    }

    @Test
    void temporaryPasswordEmail_linksToFrontendLoginPage() throws Exception {
        emailService.sendTemporaryPasswordEmail("staff@dasig-core.site", "Staff", "TempPass123", "STAFF");

        assertThat(htmlBody()).contains("href=\"" + FRONTEND_URL + "\"");
    }

    // ---- helpers ----

    /** Collects every text body of the message, in document order. */
    private List<String> textBodies() throws Exception {
        List<String> bodies = new ArrayList<>();
        collectTextBodies(mimeMessage.getContent(), bodies);
        return bodies;
    }

    private void collectTextBodies(Object content, List<String> bodies) throws Exception {
        if (content instanceof MimeMultipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                collectTextBodies(multipart.getBodyPart(i).getContent(), bodies);
            }
        } else if (content instanceof String text) {
            bodies.add(text);
        }
    }

    private String plainTextBody() throws Exception {
        return textBodies().stream()
                .filter(body -> !body.contains("<div"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No plain text body part found"));
    }

    private String htmlBody() throws Exception {
        return textBodies().stream()
                .filter(body -> body.contains("<div"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No HTML body part found"));
    }
}
