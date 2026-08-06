package org.letspeppol.proxy.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class BalanceService {

    /** Maximum amount accepted in a single top-up. */
    public static final long MAX_INCREMENT = 100_000L;
    /** Ceiling the running balance is clamped to. */
    public static final long MAX_BALANCE = 1_000_000L;

    private final AtomicLong value = new AtomicLong(0);

    public boolean isPositive() {
        return value.get() > 0;
    }

    public long get() {
        return value.get();
    }

    public long incrementBy(long delta) {
        if (delta < -MAX_INCREMENT || delta > MAX_INCREMENT) {
            throw new IllegalArgumentException("Amount must be between 1 and " + MAX_INCREMENT);
        }
        return value.updateAndGet(current -> Math.min(current + delta, MAX_BALANCE));
    }

    public long decrement() {
        return value.decrementAndGet();
    }
}
