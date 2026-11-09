package cn.academy.port.highpower.core;

import java.util.Map;
import java.util.Set;

/** Persisted NEW252 phase and break debt; no world time, input edge or wall clock can advance these. */
public record ZeroGapArmorTiming(int phaseTick, int breakTicks) {
    public static final int REVOLUTION_TICKS = 120;
    public static final int BREAK_TICKS = 4;
    public static final ZeroGapArmorTiming INITIAL = new ZeroGapArmorTiming(0, 0);

    public ZeroGapArmorTiming {
        if (phaseTick < 0 || phaseTick >= REVOLUTION_TICKS || breakTicks < 0 || breakTicks > BREAK_TICKS)
            throw new IllegalArgumentException("Invalid NEW252 physical timing state");
    }

    /** Adapter must call once at the genuine server tick boundary, before damage for that tick. */
    public ZeroGapArmorTiming advanceOneNativeTick() {
        return new ZeroGapArmorTiming((phaseTick + 1) % REVOLUTION_TICKS, Math.max(0, breakTicks - 1));
    }

    /** Concurrent breaks overlap. Persist with the resource debit before any mutable callback. */
    public ZeroGapArmorTiming brokenNow() { return new ZeroGapArmorTiming(phaseTick, BREAK_TICKS); }
    public boolean openGap() { return breakTicks != 0; }

    public Map<String, String> toDecimalMap() {
        return Map.of("version", "1", "phaseTick", Integer.toString(phaseTick), "breakTicks", Integer.toString(breakTicks));
    }

    public static ZeroGapArmorTiming fromDecimalMap(Map<String, String> fields) {
        if (fields == null || !fields.keySet().equals(Set.of("version", "phaseTick", "breakTicks"))
                || !"1".equals(fields.get("version")))
            throw new IllegalArgumentException("Unsupported NEW252 timing schema");
        return new ZeroGapArmorTiming(integer(fields.get("phaseTick")), integer(fields.get("breakTicks")));
    }

    private static int integer(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]{0,2}"))
            throw new IllegalArgumentException("Noncanonical NEW252 physical counter");
        return Integer.parseInt(value);
    }
}
