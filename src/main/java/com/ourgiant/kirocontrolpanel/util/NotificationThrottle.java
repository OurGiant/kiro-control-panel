package com.ourgiant.kirocontrolpanel.util;

import java.time.Duration;
import java.time.Instant;

/**
 * Rate-limits a disruptive action (e.g. a tray popup) to at most once per cooldown window,
 * separate from whatever unthrottled logging/tracking the caller does around it. Built for
 * {@link KiroFolderMonitor}'s alert: a single external editing session can trip the
 * monitor several times, each outside its debounce window, and each real change is still
 * worth logging -- but the user shouldn't have to dismiss one popup per event. See #62.
 */
public final class NotificationThrottle {
    private final Duration cooldown;
    private Instant lastAcquired = Instant.EPOCH;

    public NotificationThrottle(Duration cooldown) {
        this.cooldown = cooldown;
    }

    /** @return true if the cooldown has elapsed (and a new one just started), false if still within it. */
    public boolean tryAcquire() {
        return tryAcquire(Instant.now());
    }

    /** Package-private, for tests: an explicit clock instead of the real one. */
    boolean tryAcquire(Instant now) {
        if (Duration.between(lastAcquired, now).compareTo(cooldown) < 0) {
            return false;
        }
        lastAcquired = now;
        return true;
    }
}
