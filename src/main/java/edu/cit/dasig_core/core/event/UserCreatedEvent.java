package edu.cit.dasig_core.core.event;

public class UserCreatedEvent {
    private final String email;
    private final String name;
    private final String plainTextPassword;

    public UserCreatedEvent(String email, String name, String plainTextPassword) {
        this.email = email;
        this.name = name;
        this.plainTextPassword = plainTextPassword;
    }

    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPlainTextPassword() { return plainTextPassword; }
}