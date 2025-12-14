package org.letspeppol.proxy.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class BalanceService {
    private final AtomicLong value = new AtomicLong(0);

    public boolean isPositive() {
        return value.get() > 0;
    }

    public long get() {
        return value.get();
    }

    public long incrementBy(long delta) {
        return value.addAndGet(delta);
    }

    public long decrement() {
        return value.decrementAndGet();
    }
}
