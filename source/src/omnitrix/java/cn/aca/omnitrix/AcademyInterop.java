package cn.aca.omnitrix;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

public final class AcademyInterop {
    private static boolean resolvedRelease;
    private static Method releaseMethod;

    private static final MethodHandle NATIVE_HURT = resolveNativeHurt();
    private static final MethodHandle HOLDING_SHIELD = resolveHoldingShield();

    static boolean contracts() { return Contracts.PRESENT; }
    private static final class Contracts {
        static final boolean PRESENT = available();
        private static boolean available() {
            if (!net.neoforged.fml.ModList.get().isLoaded("academy")) return false;
            try {
                Class.forName("cn.academy.port.api.ControlRelations", false, AcademyInterop.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException missing) { return false; }
        }
    }

    private AcademyInterop() {}

    private static boolean academyLoaded() {
        return ModList.get() != null && ModList.get().isLoaded("academy");
    }

    private static MethodHandle resolveNativeHurt() {
        if (!academyLoaded()) return null;
        try {
            Class<?> api = Class.forName("cn.academy.port.api.NativeCombatDamage");
            return MethodHandles.publicLookup().findStatic(api, "hurt",
                    MethodType.methodType(boolean.class, LivingEntity.class, DamageSource.class, float.class));
        } catch (ReflectiveOperationException | LinkageError ex) {
            LogUtils.getLogger().debug("Academy NativeCombatDamage unavailable", ex);
            return null;
        }
    }

    private static MethodHandle resolveHoldingShield() {
        if (!academyLoaded()) return null;
        try {
            Class<?> cls = Class.forName("cn.academy.port.highpower.PlayerShieldArmor");
            var signature = MethodType.methodType(boolean.class, Entity.class);
            try { return MethodHandles.publicLookup().findStatic(cls, "ignoresControl", signature); }
            catch (NoSuchMethodException olderAcademy) { return MethodHandles.publicLookup().findStatic(cls, "holding", signature); }
        } catch (ReflectiveOperationException | LinkageError ex) {
            LogUtils.getLogger().debug("Academy PlayerShieldArmor unavailable", ex);
            return null;
        }
    }

    public static boolean hasNativeShieldBridge() {
        return academyLoaded() && NATIVE_HURT != null;
    }

    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        if (NATIVE_HURT == null) {
            int previous = target.invulnerableTime;
            target.invulnerableTime = 0;
            boolean accepted = target.hurt(source, amount);
            if (!accepted) target.invulnerableTime = previous;
            return accepted;
        }
        try {
            return (boolean) NATIVE_HURT.invokeExact(target, source, amount);
        } catch (RuntimeException | Error err) {
            throw err;
        } catch (Throwable ex) {
            throw new IllegalStateException("Academy NativeCombatDamage invocation failed", ex);
        }
    }

    public static boolean holdingShieldArmor(Entity target) {
        if (HOLDING_SHIELD == null || target == null) return false;
        try {
            return (boolean) HOLDING_SHIELD.invokeExact(target);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void registerShieldBridge(IEventBus bus) {
        if (!academyLoaded()) return;
        try {
            ShieldBridge.register(bus);
        } catch (LinkageError | RuntimeException ex) {
            LogUtils.getLogger().warn("ACA shield bridge registration skipped", ex);
        }
    }

    static void releaseHeldAbility(ServerPlayer player) {
        if (!academyLoaded()) return;
        try {
            if (!resolvedRelease) {
                resolvedRelease = true;
                releaseMethod = Class.forName("cn.academy.port.AcademyGameplay")
                        .getMethod("disposeAbilityContexts", ServerPlayer.class);
            }
            if (releaseMethod != null) releaseMethod.invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError ex) {
            releaseMethod = null;
            LogUtils.getLogger().warn("ACA ability context could not be released", ex);
        }
    }

    public static boolean nativeCapacity(net.minecraft.world.entity.player.Player p) {
        return hasNativeShieldBridge() && (p.level().isClientSide || ShieldBridge.enrolled(p));
    }
    public static boolean adjustingCapacity(net.minecraft.world.entity.player.Player p) {
        return hasNativeShieldBridge() && ShieldBridge.writing(p);
    }

    static void changedVitals(ServerPlayer p, float oldHealth, float oldMax) {
        if (hasNativeShieldBridge()) ShieldBridge.changedVitals(p, oldHealth, oldMax);
    }

    private static final class ShieldBridge {
        static boolean enrolled(net.minecraft.world.entity.player.Player p) {
            return p.getPersistentData().contains(cn.academy.port.highpower.HighPowerCombat.DATA_KEY);
        }
        static boolean writing(net.minecraft.world.entity.player.Player p) {
            return cn.academy.port.highpower.CapacityProfile.writing(p);
        }
        static void changedVitals(ServerPlayer p, float oldHealth, float oldMax) {
            cn.academy.port.highpower.CapacityProfile.equipmentChanged(p, oldHealth, oldMax);
        }
        static void register(IEventBus bus) {
            cn.academy.port.highpower.CapacityProfile.registerEquipment(player -> {
                var w = player.hasData(Omnitrix.WRIST) ? Omnitrix.wrist(player) : null;
                return (w != null && (w.kind() == Kind.CELESTIAL || w.hasForm() && w.form() == Form.ALIEN_X))
                        || AscalonService.holdingAscalon(player) || AscalonService.isArmored(player);
            }, player -> {
                var w = player.hasData(Omnitrix.WRIST) ? Omnitrix.wrist(player) : null;
                return w != null && w.hasForm()
                        ? new cn.academy.port.highpower.CapacityProfile.WristShield(w.barrier,w.maxBarrier)
                        : cn.academy.port.highpower.CapacityProfile.WristShield.EMPTY;
            });
            bus.addListener(ShieldBridge::absorbShield);
            cn.academy.port.highpower.PlayerShieldArmor.registerWristShield(ShieldBridge::hasWristShield);
        }

        private static boolean hasWristShield(ServerPlayer player) {
            if (player.hasData(Omnitrix.WRIST)) {
                var wrist = Omnitrix.wrist(player);
                if (wrist.hasForm() && (wrist.barrier > 0 || wrist.form() == Form.DIAMONDHEAD
                        && wrist.crystalShellUntil > WristService.time(player))) return true;
            }
            return AscalonService.isArmored(player) && player.getAbsorptionAmount() > 0;
        }

        private static void absorbShield(cn.academy.port.api.NativeShieldEvent event) {
            var p = event.player();
            if (!p.hasData(Omnitrix.WRIST) || WristService.absoluteDamage(event.source())) return;
            var wrist = Omnitrix.wrist(p);
            if (!wrist.hasForm() || wrist.kind() == Kind.CLASSIC && wrist.form() == Form.RIPJAWS
                    && event.source().is(DamageTypes.DRY_OUT)) return;

            long now = WristService.time(p);
            if (wrist.form() == Form.DIAMONDHEAD && wrist.crystalShellUntil > now) {
                wrist.crystalShellUntil = 0;
                event.absorb(event.remaining());
                WristNetwork.cue(p, WristNetwork.CRYSTAL_SHELL_BREAK_CUE, p.position(), p.getEyePosition());
                WristNetwork.sync(p);
            } else if (wrist.barrier > 0) {
                var paid = event.absorb(BigDecimal.valueOf(wrist.barrier));
                if (paid.signum() > 0) {
                    wrist.barrier = Math.max(0, wrist.barrier - paid.doubleValue());
                    wrist.lastDamageAt = now;
                    WristNetwork.sync(p);
                }
            }
        }
    }
}
