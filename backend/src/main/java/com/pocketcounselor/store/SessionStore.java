package com.pocketcounselor.store;

import com.pocketcounselor.model.Session;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public void save(Session session) {
        sessions.put(session.getSessionId(), session);
    }

    public Session findById(String sessionId) {
        return sessions.get(sessionId);
    }

    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    public void delete(String sessionId) {
        sessions.remove(sessionId);
    }

    public void clear() {
        sessions.clear();
    }
}
