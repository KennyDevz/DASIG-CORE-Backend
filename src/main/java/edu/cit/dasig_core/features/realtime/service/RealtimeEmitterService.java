package edu.cit.dasig_core.features.realtime.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class RealtimeEmitterService {

    private record Subscriber(SseEmitter emitter, String role, Long organizationId) {
    }

    // Thread-safe collection holding all active client browser connections
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    /**
     * Registers a new client connection, tagged with the connecting user's role and organization
     * so broadcasts can be routed only to the audience that should see them.
     */
    public SseEmitter subscribe(String role, Long organizationId) {
        // Set connection timeout (30 minutes)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        Subscriber subscriber = new Subscriber(emitter, role, organizationId);
        subscribers.add(subscriber);

        // Lifecycle callbacks to remove the emitter once disconnected
        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> subscribers.remove(subscriber));
        emitter.onError(e -> subscribers.remove(subscriber));

        // Send initial handshake ping to confirm connection
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("Realtime SSE connection established"));
        } catch (IOException e) {
            subscribers.remove(subscriber);
        }

        return emitter;
    }

    /**
     * Broadcasts an event to every connected client, regardless of role or organization.
     * Used for events every dashboard should see (e.g. KPI definition changes).
     */
    public void broadcast(String eventName, Object data) {
        send(subscribers, eventName, data);
    }

    /**
     * Broadcasts an event only to clients connected with the given role (e.g. DASIG_ADMIN),
     * across all organizations.
     */
    public void broadcastToRole(String role, String eventName, Object data) {
        List<Subscriber> targets = subscribers.stream()
                .filter(subscriber -> Objects.equals(subscriber.role(), role))
                .toList();
        send(targets, eventName, data);
    }

    /**
     * Broadcasts an event only to clients connected with the given role within the given
     * organization (e.g. the TBI Manager of the organization a staff submission belongs to).
     */
    public void broadcastToOrganizationRole(Long organizationId, String role, String eventName, Object data) {
        List<Subscriber> targets = subscribers.stream()
                .filter(subscriber -> Objects.equals(subscriber.role(), role)
                        && Objects.equals(subscriber.organizationId(), organizationId))
                .toList();
        send(targets, eventName, data);
    }

    private void send(List<Subscriber> targets, String eventName, Object data) {
        for (Subscriber subscriber : targets) {
            try {
                subscriber.emitter().send(SseEmitter.event()
                        .name(eventName)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                subscribers.remove(subscriber);
            }
        }
    }
}