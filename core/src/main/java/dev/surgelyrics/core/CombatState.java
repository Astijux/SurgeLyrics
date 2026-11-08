package dev.surgelyrics.core;

/** Last-hit reach and the running combo. Written by the attack hook and read by the HUD (same thread in practice). */
public final class CombatState {
    private double reach = -1;
    private long reachAtMs = Long.MIN_VALUE / 2, lastHitMs = Long.MIN_VALUE / 2, timeoutMs = 3000;
    private int combo, best;

    public synchronized void setTimeoutMs(long ms) { timeoutMs = Math.max(500, ms); }

    public synchronized void hit(long nowMs, double reachBlocks) {
        if (nowMs - lastHitMs > timeoutMs) combo = 0;
        combo++;
        best = Math.max(best, combo);
        lastHitMs = nowMs;
        reach = reachBlocks;
        reachAtMs = nowMs;
    }

    /** You took damage: the combo is over. */
    public synchronized void damaged() { combo = 0; }

    public synchronized int combo(long nowMs) { return nowMs - lastHitMs > timeoutMs ? 0 : combo; }
    public synchronized int best() { return best; }
    public synchronized long lastHitMs() { return lastHitMs; }

    /** Reach of the last hit, or -1 once it is older than 4 seconds. */
    public synchronized double reach(long nowMs) { return nowMs - reachAtMs > 4000 ? -1 : reach; }
}
