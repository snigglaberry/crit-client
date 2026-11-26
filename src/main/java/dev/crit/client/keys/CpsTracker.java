package dev.crit.client.keys;

import java.util.ArrayDeque;

/** Counts clicks in the last second by watching for the left / right button going down. */
public final class CpsTracker {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>(), RIGHT = new ArrayDeque<>();
    private static boolean lastLeft, lastRight;

    /** Call once per rendered frame. */
    public static synchronized void update(boolean left, boolean right) {
        long now = System.currentTimeMillis();
        if (left && !lastLeft) LEFT.addLast(now);
        if (right && !lastRight) RIGHT.addLast(now);
        lastLeft = left;
        lastRight = right;
        trim(LEFT, now);
        trim(RIGHT, now);
    }

    public static synchronized int left() { trim(LEFT, System.currentTimeMillis()); return LEFT.size(); }
    public static synchronized int right() { trim(RIGHT, System.currentTimeMillis()); return RIGHT.size(); }

    private static void trim(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
    }

    private CpsTracker() {}
}
