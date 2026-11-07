package edu.cit.dasig_core.features.report.client;

public class LLMException extends RuntimeException {

    public enum Reason {
        TIMEOUT("The AI service took too long to respond. Please try again."),
        RATE_LIMITED("The AI service is busy right now. Please try again in a minute."),
        UNAVAILABLE("The AI service is temporarily unavailable. Please try again later."),
        AUTH("The AI service is not configured correctly. Please contact an administrator."),
        SCHEMA_REJECTED("The AI service could not process the report format."),
        TRUNCATED("The report was too long for the AI service to finish. Try a shorter reporting period."),
        EMPTY_RESPONSE("The AI service returned an empty response. Please try again."),
        INVALID_RESPONSE("The AI service returned an unusable response. Please try again.");

        private final String userMessage;

        Reason(String userMessage) {
            this.userMessage = userMessage;
        }

        public String getUserMessage() {
            return userMessage;
        }
    }

    private final Reason reason;

    public LLMException(Reason reason, Throwable cause) {
        super(reason.getUserMessage(), cause);
        this.reason = reason;
    }

    public LLMException(Reason reason) {
        this(reason, null);
    }

    public Reason getReason() {
        return reason;
    }
}