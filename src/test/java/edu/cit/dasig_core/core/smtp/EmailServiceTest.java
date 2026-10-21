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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String FRONTEND_URL = "https://www.dasig-core.site";
    private static final String ALERT_URL = FRONTEND_URL + "/dashboard/admin/alerts?alertId=42";

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
        assertThat(html).contains("href=\"" + ALERT_URL + "\"");
        assertThat(html).contains(">" + ALERT_URL + "</a>");
        assertThat(html).contains("copy and paste this link into your browser");

        // plain text also carries the raw link
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
