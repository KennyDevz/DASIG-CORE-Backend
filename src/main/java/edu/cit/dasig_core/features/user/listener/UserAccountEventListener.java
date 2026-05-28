package edu.cit.dasig_core.features.user.listener;

import edu.cit.dasig_core.core.event.UserCreatedEvent;
import edu.cit.dasig_core.core.smtp.EmailService; // Use your exact package location
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserAccountEventListener {

    private final EmailService emailService;

    // Direct Spring Managed Field Binding via implicit Autowiring Injection
    public UserAccountEventListener(EmailService emailService) {
        this.emailService = emailService;
    }

    // Modern Framework Strategy: Fires asynchronously, AFTER the active DB transaction successfully commits!
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserCreationNotification(UserCreatedEvent event) {
        if (emailService != null) {
            emailService.sendTemporaryPasswordEmail(
                    event.getEmail(),
                    event.getName(),
                    event.getPlainTextPassword()
            );
        } else {
            System.err.println("Infrastructural Alert: EmailService reference wiring failed inside target context listener.");
        }
    }
}