package dev.surgelyrics.core;

import java.util.ArrayDeque;

/** Clicks per second over a sliding one-second window. */
public final class ClickCounter {
    private final ArrayDeque<Long> times = new ArrayDeque<>();

    public void click(long nowMs) { times.addLast(nowMs); prune(nowMs); }

    public int cps(long nowMs) { prune(nowMs); return times.size(); }

    private void prune(long nowMs) { while (!times.isEmpty() && nowMs - times.peekFirst() >= 1000) times.removeFirst(); }
}
