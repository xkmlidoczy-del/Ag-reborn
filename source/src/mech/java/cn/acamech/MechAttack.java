package cn.acamech;

import net.minecraft.network.chat.Component;

/** Timing is shared by the authoritative hit and the client animation. */
public enum MechAttack {
    HYDRAULIC("hydraulic", 9, 24, 30, .92, .25f, 0x50bfff),
    SWEEP("sweep", 5, 16, 58, .50, .65f, 0x62efff),
    WINGS("wings", 10, 25, 42, .64, .42f, 0x5effbc);

    public static final int CHARGE_COST = 24;
    public static final int IMPACT_LIFE = 13;
    public static final int MAX_VISIBLE_HITS = 6;
    public final String id;
    public final int contactTick, duration, color;
    public final double coneCos, knockback;
    public final float movement;

    MechAttack(String id, int contactTick, int duration, double halfAngle, double knockback, float movement, int color) {
        this.id = id;
        this.contactTick = contactTick;
        this.duration = duration;
        this.coneCos = Math.cos(Math.toRadians(halfAngle));
        this.knockback = knockback;
        this.movement = movement;
        this.color = color;
    }

    public Component displayName() {
        return Component.translatable("attack.acamech." + id);
    }
}
