package cn.aca.infinity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class GauntletService {
    private GauntletService() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(GauntletService::onPlayerTick);
        gameBus.addListener(GauntletService::onPlayerLoggedIn);
        gameBus.addListener(GauntletService::onStartTracking);
        gameBus.addListener(GauntletService::onPlayerClone);
        gameBus.addListener(GauntletService::onDimensionChange);
        gameBus.addListener(EventPriority.HIGHEST, GauntletService::onLivingDeath);
    }

    public static void handleAction(ServerPlayer player, InfinityNetwork.ActionPayload msg) {
        if (player.isSpectator()) return;
        WearerState w = AcaInfinity.wearer(player);
        switch (msg.action()) {
            case InfinityNetwork.ACT_OPEN_WHEEL -> {
                if (w.equipped()) {
                    w.wheelOpen = true;
                    w.socketMode = msg.arg0() != 0;
                    InfinityNetwork.syncWearer(player);
                }
            }
            case InfinityNetwork.ACT_CLOSE_WHEEL -> {
                if (w.wheelOpen) {
                    w.wheelOpen = false;
                    w.socketMode = false;
                    InfinityNetwork.syncWearer(player);
                }
            }
            case InfinityNetwork.ACT_SELECT_STONE -> {
                if (w.equipped()) {
                    Stone chosen = Stone.byOrdinal(msg.arg0());
                    w.updateData(w.data().withSelected(chosen));
                    w.wheelOpen = false;
                    w.socketMode = false;
                    player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_STEP,
                            SoundSource.PLAYERS, 0.7f, 1.35f);
                    InfinityNetwork.syncWearer(player);
                }
            }
            case InfinityNetwork.ACT_UNEQUIP -> {
                if (w.equipped()) {
                    ItemStack taken = w.gauntlet.copy();
                    w.gauntlet = ItemStack.EMPTY;
                    w.wheelOpen = false;
                    w.socketMode = false;
                    w.revision++;
                    if (!player.getInventory().add(taken)) {
                        player.drop(taken, false);
                    }
                    player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                            SoundSource.PLAYERS, 0.85f, 0.9f);
                    player.displayClientMessage(Component.translatable("msg.aca_infinity.gauntlet_unequipped"), true);
                    InfinityNetwork.syncWearer(player);
                }
            }
            case InfinityNetwork.ACT_TOGGLE_SOCKET -> {
                if (w.equipped()) {
                    Stone stone = Stone.byOrdinal(msg.arg0());
                    GauntletData d = w.data();
                    if (d.has(stone)) {
                        w.updateData(d.withoutStone(stone));
                        if (!player.getAbilities().instabuild) {
                            ItemStack returned = new ItemStack(AcaInfinity.stoneItem(stone));
                            if (!player.getInventory().add(returned)) player.drop(returned, false);
                        }
                        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                                SoundSource.PLAYERS, 0.8f, 0.85f);
                    } else if (player.getAbilities().instabuild || consumeStoneFromInventory(player, stone)) {
                        w.updateData(d.withStone(stone).withSelected(stone));
                        player.level().playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL,
                                SoundSource.PLAYERS, 0.9f, 1.2f);
                    } else {
                        player.displayClientMessage(Component.translatable("msg.aca_infinity.missing_stone_item",
                                Component.translatable(stone.translationKey()).withStyle(stone.chatColor)), true);
                    }
                    InfinityNetwork.syncWearer(player);
                }
            }
            case InfinityNetwork.ACT_CAST_PRIMARY -> StoneAbilities.castPrimary(player, msg.arg0() != 0);
            case InfinityNetwork.ACT_CAST_SECONDARY -> StoneAbilities.castSecondary(player, msg.arg0() != 0);
            case InfinityNetwork.ACT_CAST_SNAP -> StoneAbilities.castSnap(player);
            case InfinityNetwork.ACT_SHIP_CONTROL -> {
                if (player.getVehicle() instanceof SanctuaryShipEntity ship) {
                    ship.acceptPilotInput(player, msg.arg0(), msg.arg1());
                }
            }
            case InfinityNetwork.ACT_TOGGLE_GAUNTLET -> {
                if (w.equipped()) {
                    ItemStack taken = w.gauntlet.copy();
                    w.gauntlet = ItemStack.EMPTY;
                    w.wheelOpen = false;
                    w.socketMode = false;
                    w.revision++;
                    if (!player.getInventory().add(taken)) {
                        player.drop(taken, false);
                    }
                    player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                            SoundSource.PLAYERS, 0.85f, 0.9f);
                    player.displayClientMessage(Component.translatable("msg.aca_infinity.gauntlet_unequipped"), true);
                    InfinityNetwork.syncWearer(player);
                } else {
                    ItemStack found = takeGauntletFromInventory(player);
                    if (!found.isEmpty()) {
                        w.gauntlet = found;
                        w.wheelOpen = false;
                        w.socketMode = false;
                        w.revision++;
                        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(),
                                SoundSource.PLAYERS, 0.9f, 1.05f);
                        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                                SoundSource.PLAYERS, 0.8f, 1.15f);
                        player.displayClientMessage(Component.translatable("msg.aca_infinity.gauntlet_equipped"), true);
                        InfinityNetwork.syncWearer(player);
                    } else {
                        player.displayClientMessage(Component.translatable("msg.aca_infinity.no_gauntlet_in_inv")
                                .withStyle(ChatFormatting.GRAY), true);
                    }
                }
            }
            default -> {}
        }
    }

    private static ItemStack takeGauntletFromInventory(ServerPlayer player) {
        var gauntletItem = AcaInfinity.INFINITY_GAUNTLET.get();
        ItemStack main = player.getMainHandItem();
        if (main.is(gauntletItem)) {
            ItemStack copy = main.copyWithCount(1);
            main.shrink(1);
            return copy;
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(gauntletItem)) {
            ItemStack copy = off.copyWithCount(1);
            off.shrink(1);
            return copy;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.is(gauntletItem)) {
                ItemStack copy = slot.copyWithCount(1);
                slot.shrink(1);
                return copy;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean consumeStoneFromInventory(ServerPlayer player, Stone stone) {
        StoneItem targetItem = AcaInfinity.stoneItem(stone);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.is(targetItem)) {
                slot.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        WearerState w = AcaInfinity.wearer(player);
        long now = player.level().getGameTime();
        if (w.equipped() && now % 10L == 0L) {
            w.recordSnapshot(now, player.position(), player.getYRot(), player.getXRot(),
                    player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel());
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        WearerState w = AcaInfinity.wearer(player);
        long now = player.level().getGameTime();
        if (w.equipped() && w.data().has(Stone.SOUL) && w.soulSaveReadyAt <= now) {
            event.setCanceled(true);
            w.soulSaveReadyAt = now + InfinityConfig.soulSaveCooldownTicks();
            player.setHealth(Math.max(10.0f, player.getMaxHealth() * 0.5f));
            player.removeAllEffects();
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 2));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
            player.invulnerableTime = 40;
            if (player.level() instanceof ServerLevel sl) {
                sl.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.9f, 1.18f);
                InfinityNetwork.broadcastCue(sl, player.getUUID(), InfinityNetwork.CUE_SOUL_SAVE, 0,
                        player.position(), player.position().add(0, 1.2, 0), 48.0);
            }
            player.displayClientMessage(Component.translatable("msg.aca_infinity.soul_saved")
                    .withStyle(ChatFormatting.GOLD), true);
            InfinityNetwork.syncWearer(player);
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            AcaInfinity.wearer(sp).clampCastCooldowns(sp.level().getGameTime());
            InfinityNetwork.syncWearer(sp);
        }
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)) return;
        Entity target = event.getTarget();
        if (target instanceof ServerPlayer actor) {
            InfinityNetwork.syncWearerTo(viewer, actor);
        }
        if (target.hasData(AcaInfinity.ENTITY_STATUS)) {
            InfinityNetwork.syncEntityMarkTo(viewer, target);
        }
    }

    private static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer next && event.getOriginal() instanceof ServerPlayer old) {
            WearerState oldW = AcaInfinity.wearer(old);
            WearerState newW = AcaInfinity.wearer(next);
            newW.deserializeNBT(next.registryAccess(), oldW.serializeNBT(old.registryAccess()));
            InfinityNetwork.syncWearer(next);
        }
    }

    private static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            AcaInfinity.wearer(sp).history.clear();
            InfinityNetwork.syncWearer(sp);
        }
    }
}
