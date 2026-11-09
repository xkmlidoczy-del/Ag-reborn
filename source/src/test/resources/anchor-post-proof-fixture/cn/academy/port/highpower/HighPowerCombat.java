package cn.academy.port.highpower;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
public class HighPowerCombat {
    public static final Set<LivingEntity> pending=Collections.newSetFromMap(new IdentityHashMap<>());
    public static final Set<LivingEntity> guards=Collections.newSetFromMap(new IdentityHashMap<>());
    public static boolean fail;
    public static boolean anchorFallPending(LivingEntity e) {
        return pending.contains(e);
    }
    public static boolean anchorPostGuardPending(LivingEntity e) {
        if(fail)throw new IllegalStateException("observer");
        return guards.contains(e);
    }
    public static void abandonAnchorFall(LivingEntity e) {
        pending.remove(e);
    }
    public static void abandonAnchorPostGuard(LivingEntity e) {
        pending.remove(e);
        guards.remove(e);
    }
}
