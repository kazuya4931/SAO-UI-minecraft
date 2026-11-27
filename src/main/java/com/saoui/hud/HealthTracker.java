package com.saoui.hud;

import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Smooth HP animation. "display" glides toward the real value, "lag" is the
 * white/red ghost segment that stays for a moment after taking damage and then
 * drains, like the SAO health bar.
 */
public final class HealthTracker {
    public static final class State {
        public float display = -1f;
        public float lag = -1f;
        public float last = -1f;
        public long lastHitMs;
        public long lastHealMs;
        public long lastSeenMs;
        public int lastHurtTime;
    }

    private static final Map<Object, State> STATES = new HashMap<>();

    private HealthTracker() {}

    /** @param frac current HP fraction 0..1 */
    public static State update(Object key, float frac) {
        long now = System.currentTimeMillis();
        State s = STATES.computeIfAbsent(key, k -> new State());
        if (s.display < 0) {
            s.display = s.lag = s.last = frac;
        }
        if (frac < s.last - 0.0001f) s.lastHitMs = now;
        if (frac > s.last + 0.0001f) s.lastHealMs = now;
        s.last = frac;

        // frame-rate independent smoothing
        long dt = s.lastSeenMs == 0 ? 16 : Math.min(100, now - s.lastSeenMs);
        s.lastSeenMs = now;
        float k = 1f - (float) Math.pow(0.0005, dt / 1000.0);
        if (frac < s.display) {
            s.display = MathHelper.lerp(Math.min(1f, k * 1.8f), s.display, frac);
        } else {
            s.display = MathHelper.lerp(k * 0.6f, s.display, frac);
        }
        if (Math.abs(s.display - frac) < 0.001f) s.display = frac;

        if (s.lag < s.display) {
            s.lag = s.display;
        } else if (now - s.lastHitMs > 600) {
            s.lag = Math.max(s.display, s.lag - dt / 1000f * 0.6f);
        }
        return s;
    }

    public static State get(Object key) {
        return STATES.get(key);
    }

    /** Drop states that have not been updated recently. */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Object, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastSeenMs > 20_000) it.remove();
        }
    }
}
