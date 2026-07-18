package com.ourgiant.kirocontrolpanel.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notifies every {@link WorkspaceScopeBar} instance app-wide when the pinned
 * workspace list changes, so pinning/removing a workspace in one panel's
 * scope bar is immediately reflected in every other panel's — without this,
 * each panel's scope bar only reads {@code AppPreferences} at its own
 * construction/reload time and has no way to learn about a change made
 * through a sibling panel.
 */
public final class WorkspaceRegistry {

    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private WorkspaceRegistry() {
    }

    public static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    /** Call after any change to the pinned workspace list. Always invoked from the EDT (UI actions), so listeners run synchronously. */
    public static void notifyChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
