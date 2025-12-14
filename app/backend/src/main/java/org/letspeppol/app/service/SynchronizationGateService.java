package org.letspeppol.app.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SynchronizationGateService {

    @Value("${proxy.synchronize.delay-ms:60000}")
    private long synchronizeDelay;

    private static final class State {
        final AtomicBoolean inFlight = new AtomicBoolean(false);
        final AtomicLong nextAllowedAtMs = new AtomicLong(0);
    }

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    /** @return true if acquired (not in-flight AND not in cooldown). */
    public boolean tryStart(String peppolId) {
        long now = System.currentTimeMillis();
        State s = states.computeIfAbsent(peppolId, k -> new State());

        if (now < s.nextAllowedAtMs.get()) { // cooldown check
            return false;
        }

        if (!s.inFlight.compareAndSet(false, true)) { // in-flight check
            return false;
        }

        s.nextAllowedAtMs.set(now + synchronizeDelay); // reserve next slot immediately (prevents rapid-fire triggers)
        return true;
    }

    public void finish(String peppolId) {
        State s = states.get(peppolId);
        if (s != null) {
            s.inFlight.set(false);
        }
    }

}
