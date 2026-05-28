package edu.cit.dasig_core.core.smtp;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async // Runs on a background thread pool, immediately releasing the web HTTP response thread
    public void sendTemporaryPasswordEmail(String recipientEmail, String userName, String temporaryPassword) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("no-reply@cit.edu"); // Set your platform source address
            message.setTo(recipientEmail);
            message.setSubject("DASIG Core Platform - Account Initialized");

            String emailContent = String.format(
                    "Hello %s,\n\n" +
                            "An administrative account has been successfully generated for you on the DASIG Core Platform.\n\n" +
                            "Your Temporary Credentials:\n" +
                            "Username/Email: %s\n" +
                            "Temporary Password: %s\n\n" +
                            "Please log in and update your security credentials under your profile dashboard options immediately.\n\n" +
                            "Best regards,\n" +
                            "DASIG System Management Team",
                    userName, recipientEmail, temporaryPassword
            );

            message.setText(emailContent);
            mailSender.send(message);
        } catch (Exception e) {
            // Log the error cleanly so it does not interrupt your system execution states
            System.err.println("Fatal: Asynchronous delivery phase failed for " + recipientEmail + ": " + e.getMessage());
        }
    }
}