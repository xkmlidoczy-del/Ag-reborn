package cn.aca.infinity;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class AcademyCompat {
    private static final ResourceLocation CITY_DIM = ResourceLocation.fromNamespaceAndPath("academy", "story_city_map");
    private static final ResourceLocation NOTICE_BOARD = ResourceLocation.fromNamespaceAndPath("academy", "city_notice_board");
    private static final String COOLDOWN_TAG = "aca_infinity:notice_cooldown";
    public static final TagKey<Structure> COSMIC_VAULT_TAG = TagKey.create(Registries.STRUCTURE, AcaInfinity.id("cosmic_vault"));

    private static boolean checkedDispose;
    private static Method disposeMethod;

    static boolean contracts() { return Contracts.RELATIONS; }
    static boolean erasureAvailable() { return Contracts.ERASURE; }

    static boolean controlImmune(Entity target) {
        if (Contracts.CONTROL) return cn.academy.port.highpower.PlayerShieldArmor.ignoresControl(target);
        return Contracts.RELATIONS && cn.academy.port.highpower.PlayerShieldArmor.holding(target);
    }

    static boolean canControl(ServerPlayer caster, Entity target) {
        return !controlImmune(target) && (!Contracts.CONTROL
                || cn.academy.port.api.CombatControl.canAffect(caster, target));
    }

    static boolean needsDriver(LivingEntity target) {
        return Contracts.CONTROL && cn.academy.port.api.CombatControl.needsDriver(target);
    }

    static boolean held(ServerPlayer player) {
        return Contracts.CONTROL && cn.academy.port.api.CombatControl.held(player);
    }

    static void holdPlayer(LivingEntity target, long expiry) {
        if (target.isPassenger()) target.stopRiding();
        if (Contracts.CONTROL && target instanceof ServerPlayer)
            cn.academy.port.api.CombatControl.hold(target, expiry);
    }

    private static final class Contracts {
        static final boolean RELATIONS = available("ControlRelations");
        static final boolean ERASURE = available("CombatErasure");
        static final boolean CONTROL = available("CombatControl");

        private static boolean available(String type) {
            if (!ModList.get().isLoaded("academy")) return false;
            try {
                Class.forName("cn.academy.port.api." + type, false, AcademyCompat.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException | LinkageError missing) {
                return false;
            }
        }
    }

    private AcademyCompat() {}

    private static final class CombatBridge {
        private static final MethodHandle HURT = resolve("hurtSkill", "hurt");
        private static final MethodHandle PROJECTED = resolve("hurtProjected", "hurt");

        private static MethodHandle resolve(String current, String legacy) {
            if (!ModList.get().isLoaded("academy")) return null;
            try {
                var api = Class.forName("cn.academy.port.api.NativeCombatDamage");
                var signature = MethodType.methodType(boolean.class, LivingEntity.class, DamageSource.class, float.class);
                try { return MethodHandles.publicLookup().findStatic(api, current, signature); }
                catch (NoSuchMethodException olderAcademy) { return MethodHandles.publicLookup().findStatic(api, legacy, signature); }
            } catch (ReflectiveOperationException | LinkageError absent) {
                LogUtils.getLogger().debug("Academy native combat API unavailable", absent);
                return null;
            }
        }
    }

    static boolean hurt(Entity target, DamageSource original, float amount, boolean separatePulse) {
        return hurt(target, original, amount, separatePulse, CombatBridge.HURT);
    }

    static boolean hurtPuppet(LivingEntity target, DamageSource original, float amount) {
        return hurt(target, original, amount, false, CombatBridge.PROJECTED);
    }

    private static boolean hurt(Entity target, DamageSource original, float amount, boolean separatePulse, MethodHandle bridge) {
        DamageSource source = new StoneDamageSource(target.level(), original, separatePulse);
        if (bridge == null || !(target instanceof LivingEntity living)) {
            return target.hurt(source, amount);
        }
        try {
            return (boolean) bridge.invokeExact(living, source, amount);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Academy native combat call failed", failure);
        }
    }

    public static void releaseAcademyChannel(ServerPlayer player) {
        if (!ModList.get().isLoaded("academy")) return;
        try {
            if (!checkedDispose) {
                checkedDispose = true;
                disposeMethod = Class.forName("cn.academy.port.AcademyGameplay")
                        .getMethod("disposeAbilityContexts", ServerPlayer.class);
            }
            if (disposeMethod != null) disposeMethod.invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError ex) {
            disposeMethod = null;
            LogUtils.getLogger().debug("ACA ability context hook skipped", ex);
        }
    }

    public static void onNoticeBoardInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!ModList.get().isLoaded("academy")) return;
        if (!event.getItemStack().is(Items.COMPASS) || !event.getEntity().isSecondaryUseActive()) return;
        if (!event.getLevel().dimension().location().equals(CITY_DIM)) return;
        if (!BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()).equals(NOTICE_BOARD)) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            long now = player.level().getGameTime();
            var pData = player.getPersistentData();
            if (pData.getLong(COOLDOWN_TAG) <= now && player.mayInteract(player.level(), event.getPos())) {
                pData.putLong(COOLDOWN_TAG, now + 80L);
                var overworld = player.server.getLevel(Level.OVERWORLD);
                if (overworld != null) {
                    BlockPos origin = overworld.getSharedSpawnPos();
                    BlockPos found = overworld.findNearestMapStructure(COSMIC_VAULT_TAG, origin, 80, false);
                    if (found != null) {
                        var map = MapItem.create(overworld, found.getX(), found.getZ(), (byte) 2, true, true);
                        map.set(DataComponents.ITEM_NAME, Component.translatable("map.aca_infinity.cosmic_vault"));
                        MapItem.renderBiomePreviewMap(overworld, map);
                        MapItemSavedData.addTargetDecoration(map, found, "cosmic_vault", MapDecorationTypes.RED_X);
                        if (!player.getAbilities().instabuild) event.getItemStack().shrink(1);
                        if (!player.getInventory().add(map)) player.drop(map, false);
                        player.displayClientMessage(Component.translatable("msg.aca_infinity.city_map_granted"), true);
                    } else {
                        player.displayClientMessage(Component.translatable("msg.aca_infinity.city_map_missing"), true);
                    }
                }
            }
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
    }
}
