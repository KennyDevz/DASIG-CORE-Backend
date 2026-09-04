package edu.cit.dasig_core.core.smtp;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class EmailService {

    private static final String LOGIN_URL = "https://www.dasig-core.site";
    private static final String LOGO_URL = "https://www.dasig-core.site/dasig_logo.svg";

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
                userName, accountDescription, recipientEmail, temporaryPassword, LOGIN_URL
        );
    }

    private String buildHtmlBody(String userName, String accountDescription, String recipientEmail, String temporaryPassword) {
        String safeUserName = HtmlUtils.htmlEscape(userName);
        String safeAccountDescription = HtmlUtils.htmlEscape(accountDescription);
        String safeRecipientEmail = HtmlUtils.htmlEscape(recipientEmail);
        String safeTemporaryPassword = HtmlUtils.htmlEscape(temporaryPassword);

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
                + "<a href=\"" + LOGIN_URL + "\" style=\"display:inline-block;background-color:#0f172a;color:#ffffff;text-decoration:none;font-size:14px;font-weight:bold;padding:12px 28px;border-radius:6px;\">Log In to DASIG Core</a>"
                + "</div>"
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