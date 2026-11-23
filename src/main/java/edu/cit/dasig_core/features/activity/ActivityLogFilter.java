package edu.cit.dasig_core.features.activity;

import edu.cit.dasig_core.features.activity.model.ActivityLog;
import edu.cit.dasig_core.features.activity.repository.ActivityLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Records successful validation-task actions (for example, saving a KPI) with the
 * signed-in user's email and the time, so unmoderated sessions can be measured.
 * Only active when app.activity-log=true. A failed save never affects the user's request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.activity-log", havingValue = "true")
public class ActivityLogFilter extends OncePerRequestFilter {

    /** HTTP method, path pattern, action name. */
    private static final List<String[]> RULES = List.of(
            new String[]{"POST", "/api/kpi-definitions/?", "KPI_CREATED"},
            new String[]{"PATCH", "/api/alerts/\\d+/acknowledge", "ALERT_ACKNOWLEDGED"},
            new String[]{"POST", "/api/reports/generate/(committee|kpi)", "REPORT_GENERATED"},
            new String[]{"GET", "/api/reports/[^/]+/export", "REPORT_EXPORTED"},
            new String[]{"PATCH", "/api/kpi-submissions/\\d+/review", "SUBMISSION_REVIEWED"},
            new String[]{"POST", "/api/kpi-submissions/?", "SUBMISSION_CREATED"},
            new String[]{"POST", "/api/committee-alerts/\\d+/explanation", "OVERDUE_EXPLANATION_ADDED"}
    );

    private final ActivityLogRepository activityLogRepository;

    /** Returns the action name for a request, or null if the request is not recorded. */
    static String actionFor(String method, String path) {
        for (String[] rule : RULES) {
            if (rule[0].equals(method) && path.matches(rule[1])) {
                return rule[2];
            }
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);

        String action = actionFor(request.getMethod(), request.getRequestURI());
        int status = response.getStatus();
        if (action == null || status < 200 || status >= 300) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        try {
            activityLogRepository.save(new ActivityLog(authentication.getName(), action, request.getRequestURI()));
        } catch (Exception e) {
            log.warn("Could not save activity log for {}: {}", action, e.getMessage());
        }
    }
}
