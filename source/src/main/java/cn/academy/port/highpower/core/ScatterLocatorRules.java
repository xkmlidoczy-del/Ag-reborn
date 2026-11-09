package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW223 prepares one zero-damage fixed plane and escrows the existing NEW221 control fee. */
public final class ScatterLocatorRules {
    public static final int LIFETIME = SkillTuning.integer("scatter_locator_plate", "duration_ticks"),HOLD_LIMIT=100,RECALL_TICKS=12,PREVIEW_INTERVAL=4;
    public static final int MAX_PLATES=1,MAX_SEGMENTS=2,MAX_REFLECTIONS=1;
    public static final double PLACE_RANGE = SkillTuning.number("scatter_locator_plate", "placement_reach"),AIR_PLACE_RANGE=22,TOTAL_RANGE=40;
    private ScatterLocatorRules() {}
    /** Editing only: an exactly represented center at the original endpoint needs no quotient.
     * This does not relax native visibility, permissions, incidence or any firing intersection. */
    public static boolean exactCenterAtEditLimit(FoldedMirrorGeometry.Plate plate,
            FoldedMirrorGeometry.Vector origin, FoldedMirrorGeometry.Vector direction) {
        if (plate == null || origin == null || direction == null || !origin.finite() || !direction.finite()
                || Math.abs(direction.length() - 1) > 1e-10 || direction.dot(plate.normal()) == 0) return false;
        var delta = plate.center().subtract(origin);
        return delta.dot(delta) <= PLACE_RANGE * PLACE_RANGE
                && origin.add(direction.scale(PLACE_RANGE)).equals(plate.center());
    }
    public static double controlFee(BigDecimal position){return FoldedMirrorRules.placementCost(position);}
    public static boolean clock(long tick){return FoldedMirrorRules.clock(tick);}
    public static boolean expired(long start,long now,int duration){
        return duration<=0||duration>LIFETIME||FoldedMirrorRules.expired(start,now,duration);
    }
    public static boolean ready(long before,long now,int interval){
        return interval>0&&FoldedMirrorRules.ready(before,now,interval);
    }
}
