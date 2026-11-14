package cn.acamech;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Server timing, charge costs, and nominal emitter locations in chassis coordinates. */
public enum RangedWeapon {
    PULSE("pulse", 22, 66, 100, 30, 14, 0x52bfff, new Vec3(0, 3.51, 2.48)),
    NEEDLE("needle", 11, 40, 52, 38, 10, 0x8be2ff, new Vec3(-.84, 3.91, 1.91)),
    CONVERGENCE("convergence", 18, 56, 86, 32, 7, 0x50ffbf,
            new Vec3(1.49, 4.16, 2.05), new Vec3(-1.49, 4.16, 2.05));

    public static final int RECOVERY = 10;
    public static final int MISS = 0, BLOCKED = -2, PENDING = -1, ABORTED = -3;
    public final String id;
    public final int windup, cooldown, charge, range, damage, color;
    private final Vec3[] muzzles;

    RangedWeapon(String id, int windup, int cooldown, int charge, int range, int damage, int color, Vec3... muzzles) {
        this.id = id;
        this.windup = windup;
        this.cooldown = cooldown;
        this.charge = charge;
        this.range = range;
        this.damage = damage;
        this.color = color;
        this.muzzles = muzzles;
    }

    public int emitterCount() { return muzzles.length; }
    public Vec3 muzzle(int index) { return muzzles[index]; }
    public Component displayName() { return Component.translatable("ranged.acamech." + id); }
}
