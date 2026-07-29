package com.ourgiant.kirocontrolpanel.snapshot;

/** The predefined snapshot-frequency choices from issue #91, for the Settings combo box. */
public enum SnapshotFrequency {
    THIRTY_MINUTES("30 Minutes", 30),
    ONE_HOUR("1 Hour", 60),
    FOUR_HOURS("4 Hours", 240),
    TWELVE_HOURS("12 Hours", 720),
    ONE_DAY("1 Day", 1440);

    private final String label;
    private final int minutes;

    SnapshotFrequency(String label, int minutes) {
        this.label = label;
        this.minutes = minutes;
    }

    public int minutes() {
        return minutes;
    }

    @Override
    public String toString() {
        return label;
    }

    /** Maps a stored minutes value back to the nearest preset, in case it doesn't exactly match one. */
    public static SnapshotFrequency closestTo(int minutes) {
        SnapshotFrequency closest = ONE_DAY;
        int smallestDiff = Integer.MAX_VALUE;
        for (SnapshotFrequency frequency : values()) {
            int diff = Math.abs(frequency.minutes - minutes);
            if (diff < smallestDiff) {
                smallestDiff = diff;
                closest = frequency;
            }
        }
        return closest;
    }
}
