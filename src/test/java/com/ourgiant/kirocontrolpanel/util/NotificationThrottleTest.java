package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationThrottleTest {

    @Test
    void firstAcquireAlwaysSucceeds() {
        NotificationThrottle throttle = new NotificationThrottle(Duration.ofSeconds(60));

        assertTrue(throttle.tryAcquire(Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void secondAcquireWithinTheCooldownFails() {
        NotificationThrottle throttle = new NotificationThrottle(Duration.ofSeconds(60));
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        throttle.tryAcquire(first);

        assertFalse(throttle.tryAcquire(first.plusSeconds(30)), "still within the 60s cooldown");
    }

    @Test
    void acquireAfterTheCooldownElapsesSucceeds() {
        NotificationThrottle throttle = new NotificationThrottle(Duration.ofSeconds(60));
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        throttle.tryAcquire(first);

        assertTrue(throttle.tryAcquire(first.plusSeconds(60)), "cooldown has fully elapsed");
    }

    @Test
    void aSuccessfulAcquireStartsANewCooldown() {
        NotificationThrottle throttle = new NotificationThrottle(Duration.ofSeconds(60));
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        throttle.tryAcquire(first);
        Instant second = first.plusSeconds(60);
        throttle.tryAcquire(second);

        assertFalse(throttle.tryAcquire(second.plusSeconds(30)), "should be measured from the second acquire, not the first");
    }
}
