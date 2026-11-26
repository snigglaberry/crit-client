package dev.crit.client.keys;

/** Remembers the last mouse-wheel movement so the overlay can flash it. Fed by MouseHandlerMixin. */
public final class ScrollState {
    private static final long FLASH_MS = 160;
    private static volatile long upUntil, downUntil;

    public static void onScroll(double yOffset) {
        long until = System.currentTimeMillis() + FLASH_MS;
        if (yOffset > 0) upUntil = until;
        else if (yOffset < 0) downUntil = until;
    }

    public static boolean up() { return System.currentTimeMillis() < upUntil; }
    public static boolean down() { return System.currentTimeMillis() < downUntil; }

    private ScrollState() {}
}
