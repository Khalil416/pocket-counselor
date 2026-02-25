package com.pocketcounselor.store;

import com.pocketcounselor.model.Session;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Session get(String id) { return sessions.get(id); }
    public void put(Session session) { sessions.put(session.sessionId, session); }
}
