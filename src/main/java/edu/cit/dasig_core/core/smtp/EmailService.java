package edu.cit.dasig_core.core.smtp;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class EmailService {

    private static final String LOGO_URL = "https://www.dasig-core.site/dasig_logo.svg";
    private static final String ALERTS_PATH = "/dashboard/admin/alerts";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);

    /** Public base URL of the frontend SPA, used to build deep links inside emails. */
    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async // Runs on a background thread pool, immediately releasing the web HTTP response thread
    public void sendTemporaryPasswordEmail(String recipientEmail, String userName, String temporaryPassword, String role) {
        try {
            String accountDescription = describeAccountForRole(role);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("Dasig Core Team <no-reply@dasig-core.site>");
            helper.setTo(recipientEmail);
            helper.setSubject("DASIG Core Platform - Account Initialized");
            helper.setText(
                    buildPlainTextBody(userName, accountDescription, recipientEmail, temporaryPassword),
                    buildHtmlBody(userName, accountDescription, recipientEmail, temporaryPassword)
            );

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            // Log the error cleanly so it does not interrupt your system execution states
            System.err.println("Fatal: Asynchronous delivery phase failed for " + recipientEmail + ": " + e.getMessage());
        }
    }

    @Async
    public void sendKpiAlertEmail(String recipientEmail, String userName, String alertType, String severity, String kpiName, Long alertId) {
        try {
            String encodedEmail = URLEncoder.encode(recipientEmail, StandardCharsets.UTF_8);
            String alertUrl = buildAlertUrl(alertId, encodedEmail);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("Dasig Core Team <no-reply@dasig-core.site>");
            helper.setTo(recipientEmail);
            helper.setSubject("DASIG Core - KPI Alert: " + alertType + " (" + severity + ")");
            helper.setText(
                    buildKpiAlertPlainText(userName, alertType, severity, kpiName, alertUrl),
                    buildKpiAlertHtml(userName, alertType, severity, kpiName, alertUrl)
            );

            mailSender.send(mimeMessage);
            log.info("KPI alert email sent to {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send KPI alert email to {}: {}", recipientEmail, e.getMessage());
            e.printStackTrace();
        }
    }

    /** Deep link that opens the Alerts page with the given alert already selected and the recipient pre-hinted for MSAL login. */
    private String buildAlertUrl(Long alertId, String encodedEmail) {
        String base = frontendUrl + ALERTS_PATH;
        if (alertId != null) {
            return base + "?alertId=" + alertId + "&loginHint=" + encodedEmail;
        }
        return base;
    }

    // ---- Temporary-password email body builders ----

    private String buildPlainTextBody(String userName, String accountDescription, String recipientEmail, String temporaryPassword) {
        return String.format(
                "Hello %s,\n\n" +
                        "%s\n\n" +
                        "Your Temporary Credentials:\n" +
                        "Username/Email: %s\n" +
                        "Temporary Password: %s\n\n" +
                        "Please log in and update your security credentials under your profile dashboard options immediately.\n" +
                        "Log in here: %s\n\n" +
                        "Best regards,\n" +
                        "DASIG System Management Team",
                userName, accountDescription, recipientEmail, temporaryPassword, frontendUrl
        );
    }

    private String buildHtmlBody(String userName, String accountDescription, String recipientEmail, String temporaryPassword) {
        String safeUserName = HtmlUtils.htmlEscape(userName);
        String safeAccountDescription = HtmlUtils.htmlEscape(accountDescription);
        String safeRecipientEmail = HtmlUtils.htmlEscape(recipientEmail);
        String safeTemporaryPassword = HtmlUtils.htmlEscape(temporaryPassword);
        String safeFrontendUrl = HtmlUtils.htmlEscape(frontendUrl);

        return "<div style=\"font-family:Arial,Helvetica,sans-serif;background-color:#f4f5f7;padding:32px 0;\">"
                + "<div style=\"max-width:520px;margin:0 auto;background:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;\">"
                + "<div style=\"background-color:#ffffff;padding:24px 32px;border-bottom:1px solid #e5e7eb;\">"
                + "<img src=\"" + LOGO_URL + "\" alt=\"DASIG Core\" width=\"48\" height=\"48\" style=\"display:inline-block;vertical-align:middle;border:0;\">"
                + "<span style=\"color:#111827;font-size:22px;font-weight:bold;vertical-align:middle;margin-left:12px;\">DASIG Core</span>"
                + "</div>"
                + "<div style=\"padding:32px;\">"
                + "<h1 style=\"font-size:18px;color:#111827;margin:0 0 16px;\">Account Initialized</h1>"
                + "<p style=\"font-size:14px;color:#374151;line-height:1.6;margin:0 0 16px;\">Hello " + safeUserName + ",</p>"
                + "<p style=\"font-size:14px;color:#374151;line-height:1.6;margin:0 0 24px;\">" + safeAccountDescription + "</p>"
                + "<p style=\"margin:0 0 8px;font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:0.05em;\">Temporary Credentials</p>"
                + "<p style=\"margin:0 0 4px;font-size:14px;color:#111827;\"><strong>Email:</strong> " + safeRecipientEmail + "</p>"
                + "<p style=\"margin:0 0 24px;font-size:14px;color:#111827;\"><strong>Temporary Password:</strong> " + safeTemporaryPassword + "</p>"
                + "<p style=\"font-size:14px;color:#374151;line-height:1.6;margin:0 0 24px;\">Please log in and update your security credentials under your profile dashboard options immediately.</p>"
                + "<div style=\"text-align:center;\">"
                + "<a href=\"" + safeFrontendUrl + "\" style=\"display:inline-block;background-color:#0f172a;color:#ffffff;text-decoration:none;font-size:14px;font-weight:bold;padding:12px 28px;border-radius:6px;\">Log In to DASIG Core</a>"
                + "</div>"
                + "</div>"
                + "<div style=\"background-color:#f9fafb;padding:16px 32px;border-top:1px solid #e5e7eb;\">"
                + "<p style=\"font-size:12px;color:#9ca3af;margin:0;\">DASIG Core System Management Team</p>"
                + "</div>"
                + "</div>"
                + "</div>";
    }

    // ---- KPI alert email body builders ----

    private String buildKpiAlertPlainText(String userName, String alertType, String severity, String kpiName, String alertUrl) {
        return String.format(
                "Hello %s,\n\n" +
                        "A KPI alert has been triggered on the DASIG Core Platform.\n\n" +
                        "Alert Details:\n" +
                        "KPI: %s\n" +
                        "Alert Type: %s\n" +
                        "Severity: %s\n\n" +
                        "Please log in to the platform to review and take appropriate action.\n" +
                        "Review this alert here: %s\n\n" +
                        "Best regards,\n" +
                        "DASIG System Management Team",
                userName, kpiName, alertType, severity, alertUrl
        );
    }

    private String buildKpiAlertHtml(String userName, String alertType, String severity, String kpiName, String alertUrl) {
        String safeUserName = HtmlUtils.htmlEscape(userName);
        String safeAlertType = HtmlUtils.htmlEscape(alertType);
        String safeSeverity = HtmlUtils.htmlEscape(severity);
        String safeKpiName = HtmlUtils.htmlEscape(kpiName);
        String safeAlertUrl = HtmlUtils.htmlEscape(alertUrl);

        String severityColor = "CRITICAL".equals(severity) ? "#dc2626" : "#f59e0b";

        return "<div style=\"font-family:Arial,Helvetica,sans-serif;background-color:#f4f5f7;padding:32px 0;\">"
                + "<div style=\"max-width:520px;margin:0 auto;background:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;\">"
                + "<div style=\"background-color:#ffffff;padding:24px 32px;border-bottom:1px solid #e5e7eb;\">"
                + "<img src=\"" + LOGO_URL + "\" alt=\"DASIG Core\" width=\"48\" height=\"48\" style=\"display:inline-block;vertical-align:middle;border:0;\">"
                + "<span style=\"color:#111827;font-size:22px;font-weight:bold;vertical-align:middle;margin-left:12px;\">DASIG Core</span>"
                + "</div>"
                + "<div style=\"padding:32px;\">"
                + "<h1 style=\"font-size:18px;color:#111827;margin:0 0 16px;\">KPI Alert Notification</h1>"
                + "<p style=\"font-size:14px;color:#374151;line-height:1.6;margin:0 0 16px;\">Hello " + safeUserName + ",</p>"
                + "<p style=\"font-size:14px;color:#374151;line-height:1.6;margin:0 0 24px;\">A KPI alert has been triggered on the DASIG Core Platform.</p>"
                + "<p style=\"margin:0 0 8px;font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:0.05em;\">Alert Details</p>"
                + "<p style=\"margin:0 0 4px;font-size:14px;color:#111827;\"><strong>KPI:</strong> " + safeKpiName + "</p>"
                + "<p style=\"margin:0 0 4px;font-size:14px;color:#111827;\"><strong>Alert Type:</strong> " + safeAlertType + "</p>"
                + "<p style=\"margin:0 0 24px;font-size:14px;color:" + severityColor + ";font-weight:bold;\"><strong>Severity:</strong> " + safeSeverity + "</p>"
                + "<p style=\"font-size:14px;color:#374151;line-height:1.6;margin:0 0 16px;\">Please log in to the platform to review and take appropriate action.</p>"
                + "<div style=\"margin: 20px 0;\">"
                + "<a href=\"" + safeAlertUrl + "\" style=\"background-color: #007bff; color: #ffffff; padding: 10px 20px; text-decoration: none; border-radius: 5px; display: inline-block; font-weight: bold;\">"
                + "Review Alert"
                + "</a>"
                + "</div>"
                + "<p style=\"font-size: 12px; color: #666666; margin-top: 10px;\">"
                + "If the button above does not work, copy and paste this link into your browser:<br>"
                + "<a href=\"" + safeAlertUrl + "\" style=\"color: #007bff; word-break: break-all;\">" + safeAlertUrl + "</a>"
                + "</p>"
                + "</div>"
                + "<div style=\"background-color:#f9fafb;padding:16px 32px;border-top:1px solid #e5e7eb;\">"
                + "<p style=\"font-size:12px;color:#9ca3af;margin:0;\">DASIG Core System Management Team</p>"
                + "</div>"
                + "</div>"
                + "</div>";
    }

    // ---- KPI assigned / deadline updated emails (UC-2.1, UC-2.2, UC-4.5) ----

    /** Tells a Committee Lead or Member that a KPI was assigned to their committee. Called by KpiEmailListener in the background. */
    public void sendKpiAssignedEmail(String recipientEmail, String userName, String role, Long kpiId,
                                     String kpiName, String committeeName, String target, LocalDate deadline) {
        List<String[]> details = new ArrayList<>();
        details.add(new String[]{"KPI", kpiName});
        details.add(new String[]{"Committee", committeeName});
        details.add(new String[]{"Target", target});
        details.add(new String[]{"Deadline", formatDate(deadline)});

        sendKpiEmail(recipientEmail, "DASIG Core - New KPI assigned: " + kpiName, "New KPI Assigned", userName,
                "A new KPI has been assigned to your committee on the DASIG Core Platform.",
                details, "View KPI", buildKpiUrl(role, kpiId));
    }

    /** Tells a Committee Lead or Member that the Admin changed a KPI's deadline. Called by KpiEmailListener in the background. */
    public void sendKpiDeadlineUpdatedEmail(String recipientEmail, String userName, String role, Long kpiId,
                                            String kpiName, String committeeName, LocalDate oldDeadline,
                                            LocalDate newDeadline, String adminRemark) {
        List<String[]> details = new ArrayList<>();
        details.add(new String[]{"KPI", kpiName});
        details.add(new String[]{"Committee", committeeName});
        details.add(new String[]{"Previous deadline", formatDate(oldDeadline)});
        details.add(new String[]{"New deadline", formatDate(newDeadline)});
        if (adminRemark != null && !adminRemark.isBlank()) {
            details.add(new String[]{"Admin remark", adminRemark});
        }

        sendKpiEmail(recipientEmail, "DASIG Core - KPI deadline updated: " + kpiName, "KPI Deadline Updated", userName,
                "The deadline of a KPI assigned to your committee has been changed.",
                details, "View KPI", buildKpiUrl(role, kpiId));
    }

    // ---- Submission emails (UC-3.1, UC-3.3, UC-4.5) ----

    /** Tells a Committee Lead that a Member submitted KPI progress that is waiting for review. */
    public void sendSubmissionForReviewEmail(String recipientEmail, String userName, String kpiName,
                                             String organizationName, String submittedBy, String value,
                                             String reportingPeriod, LocalDate submissionDate, String referenceCode) {
        List<String[]> details = new ArrayList<>();
        details.add(new String[]{"KPI", kpiName});
        details.add(new String[]{"Organization", organizationName});
        details.add(new String[]{"Submitted by", submittedBy});
        details.add(new String[]{"Submitted value", value});
        details.add(new String[]{"Reporting period", reportingPeriod});
        details.add(new String[]{"Submission date", formatDate(submissionDate)});
        details.add(new String[]{"Reference code", referenceCode});

        sendKpiEmail(recipientEmail, "DASIG Core - KPI submission to review: " + kpiName, "KPI Submission to Review", userName,
                "A Member submitted KPI progress that is waiting for your review.",
                details, "Review Submission", frontendUrl + "/dashboard/tbi_manager/submission-history");
    }

    /** Tells the Member who submitted progress that the Committee Lead approved or rejected it. */
    public void sendSubmissionReviewedEmail(String recipientEmail, String userName, String kpiName, String value,
                                            String referenceCode, boolean approved, String reviewedBy,
                                            String rejectionReason) {
        String result = approved ? "Approved" : "Rejected";
        List<String[]> details = new ArrayList<>();
        details.add(new String[]{"KPI", kpiName});
        details.add(new String[]{"Submitted value", value});
        details.add(new String[]{"Reference code", referenceCode});
        details.add(new String[]{"Result", result});
        details.add(new String[]{"Reviewed by", reviewedBy});
        if (!approved && rejectionReason != null && !rejectionReason.isBlank()) {
            details.add(new String[]{"Reason", rejectionReason});
        }

        String intro = approved
                ? "Your KPI submission was approved and now counts toward your committee's official progress."
                : "Your KPI submission was rejected. Please review the reason below and submit again if needed.";
        sendKpiEmail(recipientEmail, "DASIG Core - Your KPI submission was " + result.toLowerCase() + ": " + kpiName,
                "KPI Submission " + result, userName, intro,
                details, "View Submission", frontendUrl + "/dashboard/staff/submission-history");
    }

    /** Tells an Admin that a final KPI value now counts toward official progress. */
    public void sendFinalValueApprovedEmail(String recipientEmail, String userName, Long kpiId, String kpiName,
                                            String committeeName, String organizationName, String value,
                                            String reportingPeriod, String approvedBy) {
        List<String[]> details = new ArrayList<>();
        details.add(new String[]{"KPI", kpiName});
        details.add(new String[]{"Committee", committeeName});
        details.add(new String[]{"Organization", organizationName});
        details.add(new String[]{"Approved value", value});
        details.add(new String[]{"Reporting period", reportingPeriod});
        details.add(new String[]{"Approved by", approvedBy});

        sendKpiEmail(recipientEmail, "DASIG Core - Final KPI value approved: " + kpiName, "Final KPI Value Approved", userName,
                "A final KPI value was approved and now counts toward official progress.",
                details, "View KPI", frontendUrl + "/dashboard/admin/kpis/" + kpiId);
    }

    private void sendKpiEmail(String recipientEmail, String subject, String title, String userName,
                              String intro, List<String[]> details, String buttonLabel, String kpiUrl) {
        String name = userName != null && !userName.isBlank() ? userName : "there";
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("Dasig Core Team <no-reply@dasig-core.site>");
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(
                    buildKpiPlainText(name, intro, details, buttonLabel, kpiUrl),
                    buildKpiHtml(title, name, intro, details, buttonLabel, kpiUrl)
            );

            mailSender.send(mimeMessage);
            log.info("KPI email '{}' sent to {}", title, recipientEmail);
        } catch (Exception e) {
            // A failed email is logged only; the KPI change that triggered it is already saved
            log.error("Failed to send KPI email '{}' to {}: {}", title, recipientEmail, e.getMessage());
        }
    }

    /** Link to the KPI page of the recipient's dashboard (no login hint, so local and Microsoft accounts both work). */
    private String buildKpiUrl(String role, Long kpiId) {
        String dashboard = "TBI_MANAGER".equals(role) ? "tbi_manager" : "staff";
        return frontendUrl + "/dashboard/" + dashboard + "/kpis/" + kpiId;
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMAT) : "Not set";
    }

    private String buildKpiPlainText(String userName, String intro, List<String[]> details, String buttonLabel, String kpiUrl) {
        StringBuilder text = new StringBuilder("Hello " + userName + ",\n\n" + intro + "\n\n");
        for (String[] detail : details) {
            text.append(detail[0]).append(": ").append(detail[1] != null ? detail[1] : "").append("\n");
        }
        text.append("\n").append(buttonLabel).append(": ").append(kpiUrl).append("\n\n")
                .append("Best regards,\n")
                .append("DASIG System Management Team");
        return text.toString();
    }

    private String buildKpiHtml(String title, String userName, String intro, List<String[]> details, String buttonLabel, String kpiUrl) {
        String safeUrl = HtmlUtils.htmlEscape(kpiUrl);
        StringBuilder rows = new StringBuilder();
        for (String[] detail : details) {
            rows.append("<p style=\"margin:0 0 4px;font-size:14px;color:#111827;\"><strong>")
                    .append(HtmlUtils.htmlEscape(detail[0])).append(":</strong> ")
                    .append(HtmlUtils.htmlEscape(detail[1] != null ? detail[1] : "")).append("</p>");
        }

        return "<div style=\"font-family:Arial,Helvetica,sans-serif;background-color:#f4f5f7;padding:32px 0;\">"
                + "<div style=\"max-width:520px;margin:0 auto;background:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;\">"
                + "<div style=\"background-color:#ffffff;padding:24px 32px;border-bottom:1px solid #e5e7eb;\">"
                + "<img src=\"" + LOGO_URL + "\" alt=\"DASIG Core\" width=\"48\" height=\"48\" style=\"display:inline-block;vertical-align:middle;border:0;\">"
                + "<span style=\"color:#111827;font-size:22px;font-weight:bold;vertical-align:middle;margin-left:12px;\">DASIG Core</span>"
                + "</div>"
                + "<div style=\"padding:32px;\">"
                + "<h1 style=\"font-size:18px;color:#111827;margin:0 0 16px;\">" + HtmlUtils.htmlEscape(title) + "</h1>"
                + "<p style=\"font-size:14px;color:#374151;line-height:1.6;margin:0 0 16px;\">Hello " + HtmlUtils.htmlEscape(userName) + ",</p>"
                + "<p style=\"font-size:14px;color:#374151;line-height:1.6;margin:0 0 24px;\">" + HtmlUtils.htmlEscape(intro) + "</p>"
                + "<p style=\"margin:0 0 8px;font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:0.05em;\">KPI Details</p>"
                + rows
                + "<div style=\"margin: 24px 0 20px;\">"
                + "<a href=\"" + safeUrl + "\" style=\"background-color: #007bff; color: #ffffff; padding: 10px 20px; text-decoration: none; border-radius: 5px; display: inline-block; font-weight: bold;\">"
                + HtmlUtils.htmlEscape(buttonLabel)
                + "</a>"
                + "</div>"
                + "<p style=\"font-size: 12px; color: #666666; margin-top: 10px;\">"
                + "If the button above does not work, copy and paste this link into your browser:<br>"
                + "<a href=\"" + safeUrl + "\" style=\"color: #007bff; word-break: break-all;\">" + safeUrl + "</a>"
                + "</p>"
                + "</div>"
                + "<div style=\"background-color:#f9fafb;padding:16px 32px;border-top:1px solid #e5e7eb;\">"
                + "<p style=\"font-size:12px;color:#9ca3af;margin:0;\">DASIG Core System Management Team</p>"
                + "</div>"
                + "</div>"
                + "</div>";
    }

    private String describeAccountForRole(String role) {
        if (role == null) {
            return "An account has been successfully generated for you on the DASIG Core Platform.";
        }
        return switch (role) {
            case "DASIG_ADMIN" -> "An administrative account has been successfully generated for you on the DASIG Core Platform. "
                    + "You have full access to manage organizations, users, and system-wide KPI settings.";
            case "TBI_MANAGER" -> "A Committee Lead account has been successfully generated for you on the DASIG Core Platform. "
                    + "You can review and manage KPI submissions for your organization.";
            case "STAFF" -> "A member account has been successfully generated for you on the DASIG Core Platform. "
                    + "You can submit and track KPI documents for your organization.";
            default -> "An account has been successfully generated for you on the DASIG Core Platform.";
        };
    }
}
