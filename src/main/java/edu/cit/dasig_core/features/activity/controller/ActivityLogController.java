package edu.cit.dasig_core.features.activity.controller;

import edu.cit.dasig_core.features.activity.model.ActivityLog;
import edu.cit.dasig_core.features.activity.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * Receives page events from the frontend (sign-in and page openings that mark the start
 * or end of a validation task). Only active when app.activity-log=true.
 */
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.activity-log", havingValue = "true")
public class ActivityLogController {

    private static final Set<String> PAGE_ACTIONS = Set.of(
            "SIGNED_IN",
            "OPEN_DASHBOARD",
            "OPEN_CREATE_KPI",
            "OPEN_KPI_DETAIL",
            "OPEN_ALERTS",
            "OPEN_REPORT_GENERATION",
            "OPEN_SUBMIT_KPI",
            "OPEN_SUBMISSION_HISTORY",
            "OPEN_SUBMISSION_DETAIL",
            "OPEN_NOTIFICATIONS",
            "OPEN_NOTIFICATION"
    );

    private final ActivityLogRepository activityLogRepository;

    @PostMapping
    public ResponseEntity<Void> record(@RequestBody Map<String, String> body, Authentication authentication) {
        String action = body.get("action");
        if (!PAGE_ACTIONS.contains(action)) {
            return ResponseEntity.badRequest().build();
        }
        String path = body.get("path");
        if (path == null || !path.startsWith("/") || path.length() > 255) {
            path = "/api/activity";
        }
        activityLogRepository.save(new ActivityLog(authentication.getName(), action, path));
        return ResponseEntity.noContent().build();
    }
}
