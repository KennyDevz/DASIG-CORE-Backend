package edu.cit.dasig_core.core.event;

public class UserCreatedEvent {
    private final String email;
    private final String name;
    private final String plainTextPassword;
    private final String role;

    public UserCreatedEvent(String email, String name, String plainTextPassword, String role) {
        this.email = email;
        this.name = name;
        this.plainTextPassword = plainTextPassword;
        this.role = role;
    }

    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPlainTextPassword() { return plainTextPassword; }
    public String getRole() { return role; }
}