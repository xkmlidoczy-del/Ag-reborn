package cn.aca.infinity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * 4-Layer Universal Modded Mob AI Hijack & Practical Stone Gameplay Engine (v0.5.0):
 * - Layer 1: Event Interception (LivingChangeTargetEvent + LivingIncomingDamageEvent contact/cast block + AttackEntityEvent projectile reflect)
 * - Layer 2: Brain Memory Override (MemoryModuleType.ATTACK_TARGET, ANGRY_AT, WALK_TARGET, LOOK_TARGET)
 * - Layer 3: GoalSelector Flag Lock & MoveControl Hijack (locks MOVE/JUMP/LOOK/TARGET for Cataclysm/ACA/custom mobs)
 * - Layer 4: Puppet Strike Fallback & Attribute Modifiers (forces physical lunge + melee swing if custom AI refuses faction attack;
 *            applies real ATTACK_DAMAGE / SCALE / ARMOR_TOUGHNESS modifiers on unarmed modded monsters)
 */
public final class StoneAbilities {
    private static final ResourceLocation MOD_SILENCE_DMG = AcaInfinity.id("pacified_attack_zero");
    private static final ResourceLocation MOD_DISARM_DMG = AcaInfinity.id("reality_disarm_weaken");
    private static final ResourceLocation MOD_DISARM_TOUGH = AcaInfinity.id("reality_disarm_toughness");
    private static final ResourceLocation MOD_DISARM_SCALE = AcaInfinity.id("reality_disarm_shrink");
    private static final ResourceLocation MOD_PUPPET_DMG = AcaInfinity.id("mind_puppet_boost");
    private static final ResourceLocation MOD_PUPPET_SPD = AcaInfinity.id("mind_puppet_haste");

    private record TransmutedBlock(BlockPos pos, BlockState original, long restoreAt) {}
    private record SpaceDecoy(int entityId, UUID ownerId, Vec3 pos, long expireTick) {}

    private static final Map<ServerLevel, List<TransmutedBlock>> TRANSMUTATIONS = new WeakHashMap<>();
    private static final Map<ServerLevel, List<SpaceDecoy>> ACTIVE_DECOYS = new WeakHashMap<>();
    private static boolean applyingTemporalReckoning = false;

    private StoneAbilities() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(StoneAbilities::onEntityTickPre);
        gameBus.addListener(StoneAbilities::onEntityTickPost);
        gameBus.addListener(StoneAbilities::onLevelTickPost);
        gameBus.addListener(StoneAbilities::onIncomingDamage);
        gameBus.addListener(StoneAbilities::onLivingChangeTarget);
        gameBus.addListener(StoneAbilities::onPlayerAttackEntity);
    }

    public static void castPrimary(ServerPlayer player) {
        castSkill(player, true, player.isShiftKeyDown());
    }

    public static void castPrimary(ServerPlayer player, boolean shiftMod) {
        castSkill(player, true, shiftMod || player.isShiftKeyDown());
    }

    public static void castSecondary(ServerPlayer player) {
        castSkill(player, false, player.isShiftKeyDown());
    }

    public static void castSecondary(ServerPlayer player, boolean shiftMod) {
        castSkill(player, false, shiftMod || player.isShiftKeyDown());
    }

    private static void castSkill(ServerPlayer player, boolean primary, boolean shiftMod) {
        WearerState w = AcaInfinity.wearer(player);
        if (!w.equipped()) return;
        GauntletData data = w.data();
        Stone stone = data.selected();
        if (!data.has(stone)) {
            player.displayClientMessage(Component.translatable("msg.aca_infinity.stone_not_socketed",
                    Component.translatable(stone.translationKey()).withStyle(stone.chatColor)), true);
            return;
        }
        long now = player.level().getGameTime();
        long readyAt = primary ? w.primaryReadyAt[stone.ordinal()] : w.secondaryReadyAt[stone.ordinal()];
        if (!player.getAbilities().instabuild && readyAt > now) {
            String sec = String.format("%.1f", (readyAt - now) / 20.0f);
            player.displayClientMessage(Component.translatable("msg.aca_infinity.skill_cooldown", sec)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        AcademyCompat.releaseAcademyChannel(player);
        ServerLevel level = player.serverLevel();
        int cd = primary ? stone.primaryCooldown : stone.secondaryCooldown;

        switch (stone) {
            case SPACE -> {
                if (primary) castSpaceWarp(player, level, now, shiftMod);
                else castSpaceEscape(player, level, now);
            }
            case REALITY -> {
                if (primary) castRealityWave(player, level, now);
                else castRealityConfiscate(player, level, now);
            }
            case POWER -> {
                if (primary) castPowerPunch(player, level, now);
                else castPowerWrath(player, level, now);
            }
            case MIND -> {
                if (primary) castMindBeam(player, level, now);
                else castMindRollCall(player, level, now);
            }
            case TIME -> {
                if (primary) castTimeDome(player, level, now);
                else castTimeRewind(player, level, now, w);
            }
            case SOUL -> {
                if (primary) castSoulHukouSingle(player, level, now);
                else castSoulHukouWave(player, level, now);
            }
        }

        if (primary) w.primaryReadyAt[stone.ordinal()] = now + cd;
        else w.secondaryReadyAt[stone.ordinal()] = now + cd;
        w.castAnimAt = now;
        w.castAnimStone = stone.ordinal();
        InfinityNetwork.syncWearer(player);
    }

    // ==================== 4-LAYER AI HIJACK HELPERS ====================
    private static void forceClearBrainAndGoals(Mob mob, boolean lockFlags) {
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.setLastHurtByPlayer(null);
        mob.setAggressive(false);
        mob.stopUsingItem();
        mob.getNavigation().stop();
        try {
            Brain<?> brain = mob.getBrain();
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        } catch (Exception ignored) {}

        if (lockFlags && !MIND_LOCKS.containsKey(mob)) {
            Goal lock = new Goal() {
                { setFlags(EnumSet.allOf(Goal.Flag.class)); }
                @Override public boolean canUse() { return true; }
                @Override public boolean isInterruptable() { return false; }
            };
            MIND_LOCKS.put(mob, lock);
            mob.goalSelector.addGoal(Integer.MIN_VALUE, lock);
            mob.targetSelector.addGoal(Integer.MIN_VALUE, lock);
        }
    }

    private static final Map<Mob, Goal> MIND_LOCKS = new WeakHashMap<>();

    private static void unlockGoalFlags(Mob mob) {
        Goal lock = MIND_LOCKS.remove(mob);
        if (lock != null) {
            mob.goalSelector.removeGoal(lock);
            mob.targetSelector.removeGoal(lock);
        }
    }

    private static void forceAssignAttackTarget(Mob puppet, LivingEntity victim) {
        unlockGoalFlags(puppet);
        puppet.setAggressive(true);
        puppet.setTarget(victim);
        puppet.setLastHurtByMob(victim);
        try {
            Brain<?> brain = puppet.getBrain();
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, victim);
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
        } catch (Exception ignored) {}
    }

    private static void syncTransientModifier(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                              ResourceLocation id, double amount, AttributeModifier.Operation op, boolean active) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) return;
        boolean has = inst.hasModifier(id);
        if (active && !has) {
            inst.addTransientModifier(new AttributeModifier(id, amount, op));
        } else if (!active && has) {
            inst.removeModifier(id);
        }
    }

    // ==================== 1. SPACE STONE ====================
    private static void castSpaceWarp(ServerPlayer player, ServerLevel level, long now, boolean shiftAbduct) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        // Shift + [J]: Hostile Spatial Abduction / Swap — yank distant enemies right into a singularity cluster in front of player!
        if (shiftAbduct) {
            Entity aimed = findAimedHostile(player, level, 34.0);
            Vec3 pullAnchor = player.position().add(look.scale(3.6));
            List<LivingEntity> abducted = new ArrayList<>();
            if (aimed instanceof LivingEntity mainTarget) {
                abducted.addAll(findHostilesNear(level, player, mainTarget.position(), 6.5));
                if (!abducted.contains(mainTarget)) abducted.add(mainTarget);
            } else {
                abducted.addAll(findHostilesAlongRay(level, player, eye, eye.add(look.scale(32.0)), 4.5));
            }

            if (!abducted.isEmpty()) {
                Vec3 from = abducted.getFirst().position();
                for (LivingEntity foe : abducted) {
                    foe.teleportTo(pullAnchor.x + (level.random.nextDouble() - 0.5) * 1.2,
                            pullAnchor.y + 0.25,
                            pullAnchor.z + (level.random.nextDouble() - 0.5) * 1.2);
                    foe.setDeltaMovement(Vec3.ZERO);
                    foe.hurtMarked = true;
                    EntityStatusData st = AcaInfinity.status(foe);
                    st.spaceTetherUntil = now + 120L;
                    st.spaceTetherAnchor = pullAnchor;
                    AcademyCompat.hurt(foe, level.damageSources().indirectMagic(player, player), 20.0f, false);
                    InfinityNetwork.syncEntityMark(foe);
                }
                level.playSound(null, BlockPos.containing(pullAnchor), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.15f, 0.72f);
                InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_SPACE_WARP, abducted.size(), from, pullAnchor, 64.0);
                return;
            }
        }

        Vec3 targetEnd = eye.add(look.scale(32.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, targetEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 dest = hit.getType() == HitResult.Type.MISS
                ? targetEnd
                : Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));

        BlockPos basePos = BlockPos.containing(dest);
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos check = basePos.above(dy);
            if (level.getBlockState(check).getCollisionShape(level, check).isEmpty()
                    && level.getBlockState(check.above()).getCollisionShape(level, check.above()).isEmpty()) {
                dest = new Vec3(check.getX() + 0.5, check.getY(), check.getZ() + 0.5);
                break;
            }
        }

        Vec3 from = player.position();
        player.teleportTo(level, dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
        player.resetFallDistance();

        List<LivingEntity> foes = findHostilesNear(level, player, dest, 8.5);
        for (LivingEntity foe : foes) {
            EntityStatusData st = AcaInfinity.status(foe);
            st.spaceTetherUntil = now + 120L;
            st.spaceTetherAnchor = dest;

            Vec3 pull = dest.subtract(foe.position()).scale(0.44);
            foe.setDeltaMovement(pull.x, 0.28, pull.z);
            foe.hurtMarked = true;
            AcademyCompat.hurt(foe, level.damageSources().playerAttack(player), 18.0f, false);
            InfinityNetwork.syncEntityMark(foe);
        }

        level.playSound(null, BlockPos.containing(dest), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.85f);
        level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 36, 0.45, 0.8, 0.45, 0.25);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + 1.0, dest.z, 42, 0.5, 0.85, 0.5, 0.18);
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_SPACE_WARP, foes.size(), from, dest, 64.0);
    }

    /** Space [K]: Spawns a physical Holographic Taunt Decoy that forces all nearby mobs (including Brain AI) to swarm it, exploding after 4s! */
    private static void castSpaceEscape(ServerPlayer player, ServerLevel level, long now) {
        Vec3 origin = player.position();
        Vec3 back = player.getLookAngle().multiply(-1.0, 0.0, -1.0);
        if (back.lengthSqr() < 0.01) back = new Vec3(0, 0, -1);
        back = back.normalize();
        Vec3 dest = origin.add(back.scale(10.5)).add(0, 1.2, 0);
        BlockPos check = BlockPos.containing(dest);
        if (!level.getBlockState(check).getCollisionShape(level, check).isEmpty()) {
            dest = origin.add(0, 2.5, 0);
        }

        ArmorStand decoy = EntityType.ARMOR_STAND.create(level);
        if (decoy != null) {
            decoy.moveTo(origin.x, origin.y, origin.z, player.getYRot(), 0f);
            decoy.setCustomName(Component.literal("✦ " + player.getScoreboardName() + " · 空间残影").withStyle(ChatFormatting.AQUA));
            decoy.setCustomNameVisible(true);
            decoy.setNoGravity(true);
            decoy.setGlowingTag(true);
            level.addFreshEntity(decoy);
            ACTIVE_DECOYS.computeIfAbsent(level, k -> new ArrayList<>())
                    .add(new SpaceDecoy(decoy.getId(), player.getUUID(), origin, now + 80L));

            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(18.0),
                    m -> m.isAlive() && !isFriendlyTo(player, m))) {
                forceAssignAttackTarget(mob, decoy);
            }
        }

        player.teleportTo(level, dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1));
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0));

        level.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y + 1.0, origin.z, 26, 0.35, 0.7, 0.35, 0.08);
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0f, 1.15f);
        player.displayClientMessage(Component.translatable("msg.aca_infinity.space_escape").withStyle(ChatFormatting.AQUA), true);
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_SPACE_ESCAPE, 0, origin, dest, 64.0);
    }

    // ==================== 2. REALITY STONE ====================
    private static void castRealityWave(ServerPlayer player, ServerLevel level, long now) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(16.0));
        BlockHitResult bHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 impact = bHit.getType() == HitResult.Type.MISS ? end : bHit.getLocation();

        // Crimson Aether Crystal Snare: roots & grounds flying/modded enemies at impact + shatters armor!
        Set<LivingEntity> affected = new HashSet<>(findHostilesAlongRay(level, player, eye, impact, 2.8));
        affected.addAll(findHostilesNear(level, player, impact, 4.8));
        for (LivingEntity foe : affected) {
            EntityStatusData st = AcaInfinity.status(foe);
            st.crystalSnareUntil = now + 80L;
            st.shatteredUntil = Math.max(st.shatteredUntil, now + 120L);
            foe.setDeltaMovement(0, -0.35, 0);
            foe.hurtMarked = true;
            AcademyCompat.hurt(foe, level.damageSources().indirectMagic(player, player), 28.0f, false);
            foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
            InfinityNetwork.syncEntityMark(foe);
        }

        BlockPos center = BlockPos.containing(impact);
        var list = TRANSMUTATIONS.computeIfAbsent(level, k -> new ArrayList<>());
        long restoreTick = now + 120L;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 1, 3))) {
            if (pos.distSqr(center) > 10.5) continue;
            BlockState cur = level.getBlockState(pos);
            if (cur.isAir() || cur.hasBlockEntity() || cur.getDestroySpeed(level, pos) < 0) continue;
            if (cur.is(Blocks.RED_STAINED_GLASS) || cur.is(Blocks.REDSTONE_BLOCK)) continue;
            if (list.stream().anyMatch(tb -> tb.pos.equals(pos))) continue;
            list.add(new TransmutedBlock(pos.immutable(), cur, restoreTick));
            Block replacement = ((pos.getX() + pos.getZ()) & 1) == 0 ? Blocks.RED_STAINED_GLASS : Blocks.CRIMSON_HYPHAE;
            level.setBlock(pos, replacement.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.65f);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, impact.x, impact.y + 0.6, impact.z, 45, 2.2, 0.8, 2.2, 0.05);
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_REALITY_WAVE, affected.size(), eye, impact, 64.0);
    }

    /** Reality [K]: Transmutes enemy projectiles into healing crimson butterflies & shrinks/weakens unarmed modded monsters! */
    private static void castRealityConfiscate(ServerPlayer player, ServerLevel level, long now) {
        int count = 0;
        int transmutedProj = 0;
        AABB box = player.getBoundingBox().inflate(14.0);

        for (Projectile proj : level.getEntitiesOfClass(Projectile.class, box)) {
            if (proj.getOwner() == player) continue;
            Vec3 p = proj.position();
            level.sendParticles(ParticleTypes.CHERRY_LEAVES, p.x, p.y, p.z, 10, 0.25, 0.25, 0.25, 0.05);
            level.sendParticles(ParticleTypes.HEART, p.x, p.y + 0.2, p.z, 2, 0.15, 0.15, 0.15, 0.02);
            InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_REALITY_BUTTERFLY_HEAL, 1,
                    p, player.position().add(0, 1.0, 0), 48.0);
            proj.discard();
            transmutedProj++;
            count++;
        }
        if (transmutedProj > 0) {
            player.heal(Math.min(16.0f, transmutedProj * 2.5f));
        }

        for (LivingEntity foe : findHostilesNear(level, player, player.position(), 14.0)) {
            EntityStatusData st = AcaInfinity.status(foe);
            if (foe instanceof Mob mob) {
                mob.stopUsingItem();
                if (!st.isDisarmed(now)) {
                    ItemStack main = mob.getMainHandItem();
                    ItemStack off = mob.getOffhandItem();
                    if (!main.isEmpty() || !off.isEmpty()) {
                        st.confiscatedMain = main.copy();
                        st.confiscatedOff = off.copy();
                        ItemStack slip = new ItemStack(Items.PAPER);
                        slip.set(DataComponents.CUSTOM_NAME, Component.literal("检讨书"));
                        mob.setItemInHand(InteractionHand.MAIN_HAND, slip);
                        mob.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    }
                }
            }
            // Law-grade weaken & 0.85x shrink works on ALL modded mobs even without held items!
            st.disarmedUntil = now + 200L;
            syncTransientModifier(foe, Attributes.ATTACK_DAMAGE, MOD_DISARM_DMG, -0.55, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
            syncTransientModifier(foe, Attributes.ARMOR_TOUGHNESS, MOD_DISARM_TOUGH, -0.60, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
            if (!isBossEntity(foe)) {
                syncTransientModifier(foe, Attributes.SCALE, MOD_DISARM_SCALE, -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
            }
            foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2));
            InfinityNetwork.syncEntityMark(foe);
            count++;
        }

        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.9f);
        player.displayClientMessage(Component.translatable("msg.aca_infinity.reality_confiscate", count)
                .withStyle(ChatFormatting.RED), true);
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_REALITY_CONFISCATE, count,
                player.position(), player.position().add(0, 1.2, 0), 64.0);
    }

    // ==================== 3. POWER STONE ====================
    private static void castPowerPunch(ServerPlayer player, ServerLevel level, long now) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(13.0));
        List<LivingEntity> targets = findHostilesAlongRay(level, player, eye, end, 2.6);

        float singularityBonus = consumeSingularityResonanceIfTethered(player, level, targets, 42.0f, now);
        boolean singularityTriggered = singularityBonus > 0.0f;

        for (LivingEntity foe : targets) {
            float baseDmg = Math.max(42.0f, foe.getMaxHealth() * 0.28f) + singularityBonus;
            applyTrueNameSuppressionIfHukou(player, level, foe, now, Stone.POWER);
            AcademyCompat.hurt(foe, level.damageSources().explosion(player, player), baseDmg, true);
            if (!singularityTriggered) {
                Vec3 kb = look.scale(1.65).add(0, 0.42, 0);
                foe.setDeltaMovement(kb);
                foe.hurtMarked = true;
            }
        }

        for (SanctuaryShipEntity ship : level.getEntitiesOfClass(SanctuaryShipEntity.class, player.getBoundingBox().inflate(16.0))) {
            if (ship.isHostileShip()) {
                AcademyCompat.hurt(ship, level.damageSources().playerAttack(player), 120.0f, false);
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2f, 0.78f);
        level.sendParticles(ParticleTypes.WITCH, eye.x + look.x * 3, eye.y + look.y * 3, eye.z + look.z * 3,
                40, 0.7, 0.7, 0.7, 0.18);
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_POWER_PUNCH, targets.size(), eye, end, 64.0);
    }

    private static void castPowerWrath(ServerPlayer player, ServerLevel level, long now) {
        Vec3 center = player.position();
        List<LivingEntity> targets = findHostilesNear(level, player, center, 13.0);

        float singularityBonus = consumeSingularityResonanceIfTethered(player, level, targets, 30.0f, now);
        boolean singularityTriggered = singularityBonus > 0.0f;

        for (LivingEntity foe : targets) {
            EntityStatusData st = AcaInfinity.status(foe);
            st.shatteredUntil = now + 200L;
            applyTrueNameSuppressionIfHukou(player, level, foe, now, Stone.POWER);
            AcademyCompat.hurt(foe, level.damageSources().playerAttack(player), 30.0f + singularityBonus, true);
            if (!singularityTriggered) {
                Vec3 away = foe.position().subtract(center);
                double d = Math.max(0.5, away.horizontalDistance());
                foe.setDeltaMovement(away.x / d * 1.15, 0.72, away.z / d * 1.15);
                foe.hurtMarked = true;
            }
            InfinityNetwork.syncEntityMark(foe);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.72f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.2, center.z, 2, 0.5, 0.1, 0.5, 0.0);
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_POWER_WRATH, targets.size(),
                center, center.add(0, 1.0, 0), 64.0);
    }

    private static float consumeSingularityResonanceIfTethered(ServerPlayer player, ServerLevel level,
                                                               List<LivingEntity> directTargets, float baseSkillDmg, long now) {
        LivingEntity tetheredSeed = null;
        Vec3 anchor = null;
        for (LivingEntity e : directTargets) {
            if (e.hasData(AcaInfinity.ENTITY_STATUS)) {
                EntityStatusData st = AcaInfinity.status(e);
                if (st.isSpaceTethered(now)) {
                    tetheredSeed = e;
                    anchor = st.spaceTetherAnchor.lengthSqr() > 0.01 ? st.spaceTetherAnchor : e.position();
                    break;
                }
            }
        }
        if (tetheredSeed == null) {
            for (LivingEntity e : findHostilesNear(level, player, player.position(), 13.5)) {
                if (e.hasData(AcaInfinity.ENTITY_STATUS)) {
                    EntityStatusData st = AcaInfinity.status(e);
                    if (st.isSpaceTethered(now)) {
                        tetheredSeed = e;
                        anchor = st.spaceTetherAnchor.lengthSqr() > 0.01 ? st.spaceTetherAnchor : e.position();
                        break;
                    }
                }
            }
        }
        if (tetheredSeed == null || anchor == null) return 0.0f;

        List<LivingEntity> victims = findHostilesNear(level, player, anchor, 9.5);
        float bonusCrush = baseSkillDmg * 0.45f + 35.0f;
        for (LivingEntity v : victims) {
            EntityStatusData vst = AcaInfinity.status(v);
            vst.spaceTetherUntil = 0L;
            Vec3 pull = anchor.subtract(v.position());
            v.setDeltaMovement(pull.x * 0.52, 0.36, pull.z * 0.52);
            v.hurtMarked = true;
            if (!directTargets.contains(v)) {
                AcademyCompat.hurt(v, level.damageSources().indirectMagic(player, player), bonusCrush, true);
            }
            InfinityNetwork.syncEntityMark(v);
        }

        level.playSound(null, BlockPos.containing(anchor), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.3f, 0.75f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, anchor.x, anchor.y + 1.0, anchor.z, 60, 0.8, 0.8, 0.8, 0.35);
        level.sendParticles(ParticleTypes.WITCH, anchor.x, anchor.y + 1.0, anchor.z, 40, 0.6, 0.6, 0.6, 0.2);
        player.displayClientMessage(Component.translatable("msg.aca_infinity.combo_singularity")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_COMBO_SINGULARITY, victims.size(),
                player.getEyePosition(), anchor, 72.0);
        return bonusCrush;
    }

    // ==================== 4. MIND STONE (4-LAYER FORCED PUPPET HIJACK) ====================
    private static void castMindBeam(ServerPlayer player, ServerLevel level, long now) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = level.clip(new ClipContext(eye, eye.add(look.scale(28)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        List<LivingEntity> hitFoes = findHostilesAlongRay(level, player, eye, end, 2.3);
        List<LivingEntity> nearbyPool = findHostilesNear(level, player, player.position(), 28.0);

        // Check if player already owns controlled puppets: aiming Mind Beam at a Boss/Target commands ALL puppets to focus-fire it!
        List<Mob> existingPuppets = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0),
                m -> m.isAlive() && m.hasData(AcaInfinity.ENTITY_STATUS)
                        && AcaInfinity.status(m).isMindControlled(now)
                        && player.getUUID().equals(AcaInfinity.status(m).mindMaster));

        Vec3 beamEnd = hitFoes.isEmpty() ? end : hitFoes.getFirst().getEyePosition();
        LivingEntity commandedFocus = hitFoes.isEmpty() ? null : hitFoes.getFirst();

        if (!existingPuppets.isEmpty() && commandedFocus != null) {
            for (Mob p : existingPuppets) {
                if (p == commandedFocus) continue;
                EntityStatusData pst = AcaInfinity.status(p);
                pst.mindFocus = commandedFocus.getUUID();
                forceAssignAttackTarget(p, commandedFocus);
            }
            // If commandedFocus is the last non-puppet enemy left, deal psionic disruption instead of converting the sole target!
            long nonPuppetHostiles = nearbyPool.stream()
                    .filter(e -> !(e.hasData(AcaInfinity.ENTITY_STATUS) && AcaInfinity.status(e).isMindControlled(now)))
                    .count();
            if (nonPuppetHostiles <= 1) {
                applyTrueNameSuppressionIfHukou(player, level, commandedFocus, now, Stone.MIND);
                AcademyCompat.hurt(commandedFocus, level.damageSources().indirectMagic(player, player), 26.0f, false);
                level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.45f);
                InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_MIND_BEAM, 1, eye, beamEnd, 64.0);
                return;
            }
        }

        int maxConvert = nearbyPool.size() >= 4 ? 2 : 1;
        int converted = 0;
        for (LivingEntity foe : hitFoes) {
            applyTrueNameSuppressionIfHukou(player, level, foe, now, Stone.MIND);
            if (foe instanceof Mob mob && converted < maxConvert) {
                converted++;
                EntityStatusData st = AcaInfinity.status(mob);
                st.mindStandUntil = 0L;
                st.shameUntil = 0L;
                st.mindMaster = player.getUUID();
                st.mindControlUntil = now + 300L; // 15s full Mind Subjugation!
                st.mindFocus = null;
                st.lastPuppetSwingTick = now - 20L; // Ready to swing immediately on first contact tick!
                syncTransientModifier(mob, Attributes.ATTACK_DAMAGE, MOD_PUPPET_DMG, 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
                syncTransientModifier(mob, Attributes.MOVEMENT_SPEED, MOD_PUPPET_SPD, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);

                LivingEntity initialVictim = nearbyPool.stream()
                        .filter(other -> other != mob && other.isAlive()
                                && !(other.hasData(AcaInfinity.ENTITY_STATUS) && AcaInfinity.status(other).isMindControlled(now)))
                        .min(Comparator.comparingDouble(mob::distanceToSqr))
                        .orElse(null);
                if (initialVictim != null) {
                    st.mindFocus = initialVictim.getUUID();
                    forceAssignAttackTarget(mob, initialVictim);
                } else {
                    forceClearBrainAndGoals(mob, false);
                }
                InfinityNetwork.syncEntityMark(mob);
            }
        }

        triggerPublicExecutionIfHukou(player, level, hitFoes, now);

        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.45f);
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_MIND_BEAM, hitFoes.size(), eye, beamEnd, 64.0);
    }

    private static void castMindRollCall(ServerPlayer player, ServerLevel level, long now) {
        Vec3 center = player.position();
        List<LivingEntity> foes = findHostilesNear(level, player, center, 14.0);
        foes.addAll(level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(14),
                mob -> mob.isAlive() && mob.distanceToSqr(center) <= 14 * 14 && mob.hasData(AcaInfinity.ENTITY_STATUS)
                        && AcaInfinity.status(mob).isMindControlled(now) && player.getUUID().equals(AcaInfinity.status(mob).mindMaster)));
        int count = 0;
        for (LivingEntity foe : foes) {
            if (!(foe instanceof Mob)) continue;
            EntityStatusData st = AcaInfinity.status(foe);
            st.mindControlUntil = 0L;
            st.mindMaster = null;
            st.mindFocus = null;
            syncTransientModifier(foe, Attributes.ATTACK_DAMAGE, MOD_PUPPET_DMG, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
            syncTransientModifier(foe, Attributes.MOVEMENT_SPEED, MOD_PUPPET_SPD, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
            st.mindStandUntil = now + 180L;
            foe.setDeltaMovement(Vec3.ZERO);
            foe.setXRot(0f);
            syncTransientModifier(foe, Attributes.ATTACK_DAMAGE, MOD_SILENCE_DMG, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
            if (foe instanceof Mob mob) {
                forceClearBrainAndGoals(mob, true);
            }
            InfinityNetwork.syncEntityMark(foe);
            count++;
        }

        int spread = triggerPublicExecutionIfHukou(player, level, foes, now);

        level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2f, 1.15f);
        if (spread == 0) {
            player.displayClientMessage(Component.translatable("msg.aca_infinity.mind_rollcall", count)
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_MIND_ROLLCALL, count,
                center, center.add(0, 1.5, 0), 64.0);
    }

    private static int triggerPublicExecutionIfHukou(ServerPlayer player, ServerLevel level,
                                                     List<LivingEntity> candidates, long now) {
        List<LivingEntity> hukouSeeds = new ArrayList<>();
        for (LivingEntity e : candidates) {
            if (e.hasData(AcaInfinity.ENTITY_STATUS) && AcaInfinity.status(e).hasHukou(now)) {
                hukouSeeds.add(e);
            }
        }
        if (hukouSeeds.isEmpty()) return 0;

        Set<Integer> infectedIds = new HashSet<>();
        int tagTicks = InfinityConfig.hukouTagTicks();
        int shameTicks = InfinityConfig.hukouShameTicks();

        for (LivingEntity seed : hukouSeeds) {
            for (Mob bystander : level.getEntitiesOfClass(Mob.class, seed.getBoundingBox().inflate(12.0),
                    m -> m != seed && m.isAlive() && !isFriendlyTo(player, m) && !isBossEntity(m))) {
                EntityStatusData bSt = AcaInfinity.status(bystander);
                boolean newlyInfected = !bSt.hasHukou(now) || !bSt.isAshamed(now);
                HukouNaming.Pair pair = HukouNaming.inspect(bystander);
                bSt.fatherName = pair.father();
                bSt.motherName = pair.mother();
                bSt.hukouUntil = Math.max(bSt.hukouUntil, now + tagTicks);
                bSt.shameUntil = Math.max(bSt.shameUntil, now + shameTicks);
                bSt.gawkUntil = 0L;
                bystander.addEffect(new MobEffectInstance(MobEffects.GLOWING, tagTicks, 0, false, false, true));
                forceClearBrainAndGoals(bystander, true);
                bystander.setXRot(44.0f);
                AcademyCompat.hurt(bystander, level.damageSources().indirectMagic(player, player), 22.0f, true);
                InfinityNetwork.syncEntityMark(bystander);
                if (newlyInfected) {
                    infectedIds.add(bystander.getId());
                }
            }
        }

        if (!infectedIds.isEmpty()) {
            Vec3 epic = hukouSeeds.getFirst().position();
            level.playSound(null, BlockPos.containing(epic), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.35f, 0.92f);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, epic.x, epic.y + 1.2, epic.z, 45, 2.5, 0.8, 2.5, 0.06);
            player.displayClientMessage(Component.translatable("msg.aca_infinity.combo_public_exec", infectedIds.size())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_COMBO_PUBLIC_EXEC, infectedIds.size(),
                    epic, epic.add(0, 1.6, 0), 72.0);
        }
        return infectedIds.size();
    }

    // ==================== 5. TIME STONE ====================
    private static void castTimeDome(ServerPlayer player, ServerLevel level, long now) {
        int duration = InfinityConfig.timeStopTicks();
        double radius = 18.0;
        Vec3 center = player.position();
        AABB area = player.getBoundingBox().inflate(radius);

        for (Entity entity : level.getEntities(player, area, e -> e.isAlive() && !isFriendlyTo(player, e))) {
            if (entity.distanceToSqr(center) > radius * radius) continue;
            EntityStatusData st = AcaInfinity.status(entity);
            if (!st.isTimeFrozen(now)) {
                st.frozenPos = entity.position();
                st.frozenDelta = entity.getDeltaMovement();
                st.frozenYaw = entity.getYRot();
                st.frozenPitch = entity.getXRot();
                st.frozenDamagePool = 0.0f;
                st.frozenAttacker = player.getUUID();
                st.frozenProjectileReflected = false;
            }
            st.timeFrozenUntil = now + duration;
            entity.setDeltaMovement(Vec3.ZERO);
            InfinityNetwork.syncEntityMark(entity);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.1f, 0.68f);
        player.displayClientMessage(Component.translatable("msg.aca_infinity.time_stop").withStyle(ChatFormatting.GREEN), true);
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_TIME_DOME, duration, center, center, 80.0);
    }

    private static void castTimeRewind(ServerPlayer player, ServerLevel level, long now, WearerState w) {
        Vec3 before = player.position();

        // Also rewind rushing nearby enemies 8m around back to their 3s-ago historical coordinates!
        for (LivingEntity foe : findHostilesNear(level, player, before, 8.5)) {
            if (foe.hasData(AcaInfinity.ENTITY_STATUS)) {
                EntityStatusData fst = AcaInfinity.status(foe);
                if (fst.pastPos3s.lengthSqr() > 0.01) {
                    Vec3 foeOld = foe.position();
                    foe.teleportTo(fst.pastPos3s.x, fst.pastPos3s.y, fst.pastPos3s.z);
                    foe.setDeltaMovement(Vec3.ZERO);
                    InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_TIME_REWIND, 1, foeOld, fst.pastPos3s, 48.0);
                }
            }
        }

        WearerState.TemporalSnapshot snap = w.oldestSnapshot();
        if (snap != null) {
            player.teleportTo(level, snap.pos().x, snap.pos().y, snap.pos().z, snap.yaw(), snap.pitch());
            player.setHealth(Math.max(player.getHealth() + 8.0f, snap.health()));
            player.getFoodData().setFoodLevel(Math.max(player.getFoodData().getFoodLevel(), snap.food()));
        } else {
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 10.0f));
        }
        player.resetFallDistance();
        player.clearFire();
        List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> harmful = player.getActiveEffects().stream()
                .map(MobEffectInstance::getEffect)
                .filter(h -> h.value().getCategory() == MobEffectCategory.HARMFUL)
                .toList();
        for (var h : harmful) player.removeEffect(h);

        level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.45f, 1.65f);
        player.displayClientMessage(Component.translatable("msg.aca_infinity.time_rewind").withStyle(ChatFormatting.GREEN), true);
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_TIME_REWIND, 0, before, player.position(), 64.0);
    }

    // ==================== 6. SOUL STONE ("查户口" + 禁碰撞 + 叫家长转身逃跑) ====================
    private static void castSoulHukouSingle(ServerPlayer player, ServerLevel level, long now) {
        Entity target = findHukouTarget(player, level, 32.0);
        if (target == null) {
            castSoulHukouWave(player, level, now);
            return;
        }
        applyHukouInspection(player, level, target, now, true);
    }

    private static void castSoulHukouWave(ServerPlayer player, ServerLevel level, long now) {
        List<Entity> candidates = new ArrayList<>();
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(14.0),
                ent -> ent.isAlive() && (ent instanceof LivingEntity || ent instanceof SanctuaryShipEntity) && ent != player)) {
            if (e instanceof Player) continue;
            candidates.add(e);
        }
        candidates.sort(Comparator.comparingDouble(player::distanceToSqr));
        int applied = 0;
        for (Entity target : candidates) {
            applyHukouInspection(player, level, target, now, applied == 0);
            player.heal(2.0f);
            if (++applied >= 8) break;
        }
        if (applied == 0) {
            InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_SOUL_HUKOU, 0,
                    player.getEyePosition(), player.position().add(player.getLookAngle().scale(8.0)), 48.0);
        }
    }

    public static void applyHukouInspection(ServerPlayer player, ServerLevel level, Entity target, long now, boolean announce) {
        EntityStatusData st = AcaInfinity.status(target);
        HukouNaming.Pair names = HukouNaming.inspect(target);
        boolean alreadyTagged = st.hasHukou(now);
        boolean boss = isBossEntity(target);

        st.fatherName = names.father();
        st.motherName = names.mother();
        st.hukouUntil = now + InfinityConfig.hukouTagTicks();
        st.hukouCalls++;

        if (target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, InfinityConfig.hukouTagTicks(), 0, false, false, true));
        }

        if (alreadyTagged && !boss) {
            int parentTicks = InfinityConfig.hukouParentSlowTicks();
            st.shameUntil = 0L;
            st.parentSlowUntil = now + parentTicks;
            st.parentFearOrigin = player.position();
            if (target instanceof Mob mob) {
                forceClearBrainAndGoals(mob, false);
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, parentTicks, 2));
            }
            if (announce) {
                player.displayClientMessage(Component.translatable("msg.aca_infinity.hukou_call_parents",
                        names.father(), names.mother(), target.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
            }
        } else if (boss) {
            AcademyCompat.hurt(target, level.damageSources().indirectMagic(player, player), 22.0f, false);
            if (announce) {
                player.displayClientMessage(Component.translatable("msg.aca_infinity.hukou_boss",
                        target.getDisplayName(), names.father(), names.mother()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            }
        } else {
            int shameTicks = InfinityConfig.hukouShameTicks();
            st.shameUntil = now + shameTicks;
            if (target instanceof Mob mob) {
                forceClearBrainAndGoals(mob, true);
                mob.setXRot(44.0f);
            }
            if (announce) {
                player.displayClientMessage(Component.translatable("msg.aca_infinity.hukou_broadcast",
                        target.getDisplayName(), names.father(), names.mother()).withStyle(ChatFormatting.GOLD), false);
            }
        }

        int gawkTicks = InfinityConfig.hukouGawkTicks();
        for (Mob bystander : level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(12.0),
                m -> m != target && m.isAlive() && !isBossEntity(m))) {
            EntityStatusData bSt = AcaInfinity.status(bystander);
            bSt.gawkTargetId = target.getId();
            bSt.gawkUntil = now + gawkTicks;
            forceClearBrainAndGoals(bystander, false);
            InfinityNetwork.syncEntityMark(bystander);
        }

        InfinityNetwork.syncEntityMark(target);
        level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 1.05f);
        level.playSound(null, target.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.85f, 1.35f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + target.getBbHeight() + 0.4, target.getZ(),
                26, 0.45, 0.35, 0.45, 0.03);
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_SOUL_HUKOU, target.getId(),
                player.getEyePosition(), target.position().add(0, target.getBbHeight() * 0.6, 0), 64.0);
    }

    // ==================== 7. ONE-KEY SIX-STONE SNAP ====================
    public static void castSnap(ServerPlayer player) {
        WearerState w = AcaInfinity.wearer(player);
        if (!w.equipped()) return;
        GauntletData data = w.data();
        if (!data.hasAllStones()) {
            player.displayClientMessage(Component.translatable("msg.aca_infinity.snap_need_all", data.count())
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        long now = player.level().getGameTime();
        if (!player.getAbilities().instabuild && w.snapReadyAt > now) {
            String sec = String.format("%.1f", (w.snapReadyAt - now) / 20.0f);
            player.displayClientMessage(Component.translatable("msg.aca_infinity.skill_cooldown", sec)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        AcademyCompat.releaseAcademyChannel(player);
        ServerLevel level = player.serverLevel();
        double radius = InfinityConfig.snapRadius();
        Vec3 center = player.position();
        AABB box = player.getBoundingBox().inflate(radius);

        int collapsedShips = 0;
        for (SanctuaryShipEntity ship : level.getEntitiesOfClass(SanctuaryShipEntity.class, box)) {
            if (ship.distanceToSqr(center) <= radius * radius && ship.isSnapVulnerableTo(player)) {
                ship.triggerSnapCollapse(player);
                collapsedShips++;
            }
        }

        List<LivingEntity> eligible = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e.distanceToSqr(center) > radius * radius) continue;
            if (isSnapProtected(player, e)) continue;
            eligible.add(e);
        }
        Collections.shuffle(eligible, new java.util.Random(now ^ player.getUUID().getLeastSignificantBits()));
        int toBanish = (eligible.size() + 1) / 2;
        int banished = 0;
        for (int i = 0; i < toBanish; i++) {
            LivingEntity victim = eligible.get(i);
            Vec3 vp = victim.position();
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, vp.x, vp.y + victim.getBbHeight() * 0.5, vp.z,
                    22, 0.35, 0.55, 0.35, 0.02);
            level.sendParticles(ParticleTypes.ASH, vp.x, vp.y + victim.getBbHeight() * 0.6, vp.z,
                    40, 0.45, 0.65, 0.45, 0.05);
            level.sendParticles(ParticleTypes.END_ROD, vp.x, vp.y + victim.getBbHeight() * 0.5, vp.z,
                    14, 0.35, 0.45, 0.35, 0.06);
            if (isBossEntity(victim)) {
                AcademyCompat.hurt(victim, level.damageSources().fellOutOfWorld(), Math.max(180.0f, victim.getMaxHealth() * 0.55f), true);
            } else {
                victim.discard();
            }
            banished++;
        }

        if (!player.getAbilities().instabuild) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0));
        }

        w.snapAnimAt = now;
        w.snapReadyAt = now + InfinityConfig.snapCooldownTicks();
        InfinityNetwork.syncWearer(player);

        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.4f, 0.85f);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 1.2f);
        player.displayClientMessage(Component.translatable("msg.aca_infinity.snap_executed", banished, collapsedShips)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        InfinityNetwork.broadcastCue(level, player.getUUID(), InfinityNetwork.CUE_SNAP, banished + collapsedShips,
                center.add(0, 1.2, 0), center, 96.0);
    }

    // ==================== EVENT LAYER INTERCEPTIONS ====================

    /** Layer 1A: Prevent ANY modded mob AI from retargeting the player while Mind-Controlled, Standing Attention, Ashamed, or Fleeing Home! */
    private static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !(entity instanceof Mob mob) || !entity.hasData(AcaInfinity.ENTITY_STATUS)) return;
        long now = entity.level().getGameTime();
        EntityStatusData st = AcaInfinity.status(entity);

        if (st.isStandingAttention(now) || st.isAshamed(now) || st.isParentSlowed(now) || st.isGawking(now)) {
            if (event.getNewAboutToBeSetTarget() != null) {
                event.setNewAboutToBeSetTarget(null);
            }
            return;
        }

        if (st.isMindControlled(now) && entity.level() instanceof ServerLevel sl) {
            LivingEntity proposed = event.getNewAboutToBeSetTarget();
            Player master = sl.getPlayerByUUID(st.mindMaster);
            boolean invalid = proposed == null || proposed == mob
                    || (master != null && isFriendlyTo(master, proposed))
                    || (proposed.hasData(AcaInfinity.ENTITY_STATUS) && AcaInfinity.status(proposed).isMindControlled(now)
                        && st.mindMaster.equals(AcaInfinity.status(proposed).mindMaster));
            if (invalid) {
                LivingEntity validFoe = findPuppetTarget(sl, mob, st, master, now);
                event.setNewAboutToBeSetTarget(validFoe);
            }
        }
    }

    /** Time-Stop Projectile Reflection: punching a frozen projectile redirects its launch vector where the player looks! */
    private static void onPlayerAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        Player player = event.getEntity();
        if (player.level().isClientSide || !(target instanceof Projectile proj)) return;
        if (!proj.hasData(AcaInfinity.ENTITY_STATUS)) return;
        long now = player.level().getGameTime();
        EntityStatusData st = AcaInfinity.status(proj);
        if (st.isTimeFrozen(now)) {
            double speed = Math.max(1.85, st.frozenDelta.length() * 1.45);
            Vec3 reflectVel = player.getLookAngle().normalize().scale(speed);
            st.frozenDelta = reflectVel;
            st.frozenYaw = player.getYRot();
            st.frozenPitch = player.getXRot();
            st.frozenProjectileReflected = true;
            proj.setOwner(player);
            proj.setYRot(player.getYRot());
            proj.setXRot(player.getXRot());
            player.level().playSound(null, proj.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.65f);
            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIT, proj.getX(), proj.getY(), proj.getZ(), 14, 0.2, 0.2, 0.2, 0.15);
            }
            event.setCanceled(true);
        }
    }

    private static LivingEntity findPuppetTarget(ServerLevel sl, Mob puppet, EntityStatusData st, Player master, long now) {
        if (master == null) return null;
        if (st.mindFocus != null && sl.getEntity(st.mindFocus) instanceof LivingEntity focused
                && focused.isAlive() && focused != puppet && !isFriendlyTo(master, focused)) {
            return focused;
        }
        st.mindFocus = null;
        if (now < st.nextMindSearch) return null;
        st.nextMindSearch = now + 10;
        LivingEntity selected = sl.getEntitiesOfClass(LivingEntity.class, puppet.getBoundingBox().inflate(28.0), e -> {
            if (!e.isAlive() || e == puppet || e instanceof ArmorStand) return false;
            if (isFriendlyTo(master, e)) return false;
            if (e.hasData(AcaInfinity.ENTITY_STATUS) && AcaInfinity.status(e).isMindControlled(now)
                    && st.mindMaster.equals(AcaInfinity.status(e).mindMaster)) return false;
            return e instanceof Enemy || e instanceof Mob;
        }).stream().min(Comparator.comparingDouble(puppet::distanceToSqr)).orElse(null);
        if (selected != null) st.mindFocus = selected.getUUID();
        return selected;
    }

    // ==================== TICK & DAMAGE HOOKS ====================
    private static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        long now = entity.level().getGameTime();

        // Record rolling 3s history on nearby hostile LivingEntities so Time Rewind can snap rushing mobs backward
        if (entity instanceof Mob && entity.isAlive() && now % 30L == 0L) {
            EntityStatusData hst = AcaInfinity.status(entity);
            if (hst.prevSnapshotPos.lengthSqr() > 0.01) {
                hst.pastPos3s = hst.prevSnapshotPos;
            } else {
                hst.pastPos3s = entity.position();
            }
            hst.prevSnapshotPos = entity.position();
            hst.lastHistorySampleTick = now;
        }

        if (!entity.hasData(AcaInfinity.ENTITY_STATUS)) return;
        EntityStatusData st = AcaInfinity.status(entity);

        // 1. Time Stone 10s Stasis enforcement + Projectile Reflect + 1.35x Temporal Reckoning on Thaw!
        if (st.timeFrozenUntil > 0L) {
            if (st.timeFrozenUntil > now) {
                entity.setPos(st.frozenPos.x, st.frozenPos.y, st.frozenPos.z);
                entity.setDeltaMovement(Vec3.ZERO);
                entity.setYRot(st.frozenYaw);
                entity.setXRot(st.frozenPitch);
                if (entity instanceof Mob mob) {
                    mob.getNavigation().stop();
                }
                event.setCanceled(true);
                return;
            } else {
                st.timeFrozenUntil = 0L;
                entity.setDeltaMovement(st.frozenDelta);
                entity.hurtMarked = true;

                float stored = st.frozenDamagePool;
                st.frozenDamagePool = 0.0f;
                InfinityNetwork.syncEntityMark(entity);

                if (stored > 0.01f && entity instanceof LivingEntity living && living.isAlive()
                        && entity.level() instanceof ServerLevel sl) {
                    float burst = stored * 1.35f;
                    ServerPlayer attacker = st.frozenAttacker != null && sl.getPlayerByUUID(st.frozenAttacker) instanceof ServerPlayer sp ? sp : null;
                    applyingTemporalReckoning = true;
                    try {
                        AcademyCompat.hurt(living, attacker != null ? sl.damageSources().indirectMagic(attacker, attacker) : sl.damageSources().magic(), burst, true);
                    } finally {
                        applyingTemporalReckoning = false;
                    }
                    sl.playSound(null, living.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.3f, 0.72f);
                    sl.sendParticles(ParticleTypes.END_ROD, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
                            35, 0.5, 0.6, 0.5, 0.22);
                    if (attacker != null) {
                        attacker.displayClientMessage(Component.translatable("msg.aca_infinity.combo_temporal_reckoning")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                        InfinityNetwork.broadcastCue(sl, attacker.getUUID(), InfinityNetwork.CUE_COMBO_TEMPORAL_RECKONING,
                                Math.round(burst), living.position().add(0, living.getBbHeight() * 0.5, 0), living.position(), 64.0);
                    }
                }
            }
        }

        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return;

        // 2. Reality Stone Disarm / Shrink / Weaken & Crimson Crystal Snare maintenance
        if (st.disarmedUntil > 0L && st.disarmedUntil <= now) {
            st.disarmedUntil = 0L;
            syncTransientModifier(living, Attributes.ATTACK_DAMAGE, MOD_DISARM_DMG, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
            syncTransientModifier(living, Attributes.ARMOR_TOUGHNESS, MOD_DISARM_TOUGH, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
            syncTransientModifier(living, Attributes.SCALE, MOD_DISARM_SCALE, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
            if (living instanceof Mob mob) {
                if (!st.confiscatedMain.isEmpty()) mob.setItemInHand(InteractionHand.MAIN_HAND, st.confiscatedMain);
                if (!st.confiscatedOff.isEmpty()) mob.setItemInHand(InteractionHand.OFF_HAND, st.confiscatedOff);
            }
            st.confiscatedMain = ItemStack.EMPTY;
            st.confiscatedOff = ItemStack.EMPTY;
            InfinityNetwork.syncEntityMark(living);
        }

        if (st.isCrystalSnared(now)) {
            living.setDeltaMovement(0, Math.min(0.0, living.getDeltaMovement().y - 0.08), 0);
            living.hurtMarked = true;
        }

        // Continuous gravitational inward pull while Space-Tethered!
        if (st.isSpaceTethered(now) && st.spaceTetherAnchor.lengthSqr() > 0.01 && now % 4L == 0L) {
            Vec3 toAnchor = st.spaceTetherAnchor.subtract(living.position());
            if (toAnchor.lengthSqr() > 1.2) {
                living.setDeltaMovement(living.getDeltaMovement().add(toAnchor.normalize().scale(0.14)));
                living.hurtMarked = true;
            }
        }

        if (!(living instanceof Mob mob)) return;

        boolean pacified = st.isStandingAttention(now) || st.isAshamed(now);
        syncTransientModifier(mob, Attributes.ATTACK_DAMAGE, MOD_SILENCE_DMG, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, pacified);
        if (!pacified && !st.isParentSlowed(now) && !st.isMindControlled(now)) {
            unlockGoalFlags(mob);
        }

        // 3. 4-Layer Mind Control Puppet Maintenance & Fallback Auto-Strike!
        if (st.mindControlUntil > 0L) {
            if (st.mindControlUntil <= now) {
                st.mindControlUntil = 0L;
                st.mindMaster = null;
                st.mindFocus = null;
                syncTransientModifier(mob, Attributes.ATTACK_DAMAGE, MOD_PUPPET_DMG, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
                syncTransientModifier(mob, Attributes.MOVEMENT_SPEED, MOD_PUPPET_SPD, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
                forceClearBrainAndGoals(mob, false);
                InfinityNetwork.syncEntityMark(mob);
            } else if (mob.level() instanceof ServerLevel sl) {
                Player master = sl.getPlayerByUUID(st.mindMaster);
                LivingEntity victim = findPuppetTarget(sl, mob, st, master, now);
                if (victim != null) {
                    if (mob.getTarget() != victim || now % 5L == 0L) {
                        forceAssignAttackTarget(mob, victim);
                    }
                    // Layer 4 Puppet Lunge & Strike Fallback: works even on modded mobs whose custom AI refuses faction combat!
                    double distSqr = mob.distanceToSqr(victim);
                    double reach = (mob.getBbWidth() + victim.getBbWidth()) * 0.65 + 1.85;
                    Vec3 dir = victim.position().subtract(mob.position());
                    float faceYaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0f;
                    mob.setYRot(Mth.rotLerp(0.35f, mob.getYRot(), faceYaw));
                    mob.setYHeadRot(mob.getYRot());
                    mob.yBodyRot = mob.getYRot();

                    if (distSqr > reach * reach) {
                        mob.getNavigation().moveTo(victim, 1.30);
                        if (mob.getDeltaMovement().horizontalDistanceSqr() < 0.015) {
                            Vec3 step = dir.multiply(1, 0, 1).normalize().scale(0.24);
                            mob.setDeltaMovement(step.x, mob.onGround() && mob.horizontalCollision ? 0.42 : mob.getDeltaMovement().y, step.z);
                        }
                    } else if (now - st.lastPuppetSwingTick >= 16L) {
                        st.lastPuppetSwingTick = now;
                        mob.swing(InteractionHand.MAIN_HAND);
                        var attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
                        float strike = attack == null ? 8f : (float) Math.max(1, attack.getValue());
                        victim.hurt(sl.damageSources().mobAttack(mob), strike);
                        sl.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                                8, 0.25, 0.25, 0.25, 0.12);
                    }
                } else {
                    // No hostiles left: guard master obediently without ever attacking master!
                    if (mob.getTarget() != null) forceClearBrainAndGoals(mob, false);
                    if (master != null && mob.distanceToSqr(master) > 36.0) {
                        mob.getNavigation().moveTo(master, 1.15);
                    }
                }
                return;
            }
        }

        // 4. Mind Stone Classroom Roll-Call ("真·全域静默罚站")
        if (st.isStandingAttention(now)) {
            forceClearBrainAndGoals(mob, true);
            mob.setDeltaMovement(Vec3.ZERO);
            mob.setXRot(0f);
            // Animation-driven bosses run attacks outside goals. Hold their tick until the world-time deadline.
            event.setCanceled(true);
            return;
        }

        // 5. Soul Stone "当场社死" (Hard pacify + bow head + zero contact damage)
        if (st.isAshamed(now)) {
            forceClearBrainAndGoals(mob, true);
            mob.setXRot(44.0f);
            mob.setYHeadRot(mob.getYRot() + Mth.sin(now * 0.45f) * 14.0f);
            mob.setDeltaMovement(0, Math.min(0, mob.getDeltaMovement().y), 0);
            if (now % 8L == 0L && entity.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SPLASH, mob.getX(), mob.getEyeY() + 0.15, mob.getZ(),
                        4, 0.2, 0.1, 0.2, 0.02);
            }
            return;
        }

        // 6. Soul Stone "叫家长" — Forced Turn-Around & Flee Home Vector!
        if (st.isParentSlowed(now)) {
            forceClearBrainAndGoals(mob, true);
            Vec3 away = mob.position().subtract(st.parentFearOrigin).multiply(1, 0, 1);
            if (away.lengthSqr() < 0.01) away = new Vec3(0, 0, 1);
            Vec3 fleeDir = away.normalize();
            float fleeYaw = (float) (Mth.atan2(fleeDir.z, fleeDir.x) * (180.0 / Math.PI)) - 90.0f;
            mob.setYRot(fleeYaw);
            mob.setYHeadRot(fleeYaw);
            mob.yBodyRot = fleeYaw;
            double wy = mob.onGround() && mob.horizontalCollision ? 0.42 : mob.getDeltaMovement().y;
            mob.setDeltaMovement(fleeDir.x * 0.28, wy, fleeDir.z * 0.28);
            if (now % 10L == 0L && entity.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.2, mob.getZ(), 2, 0.15, 0.05, 0.15, 0.02);
            }
            return;
        }

        // 7. Soul Stone Bystander Gawk ("吃瓜群众集体转头盯向社死目标")
        if (st.isGawking(now)) {
            Entity gawked = entity.level().getEntity(st.gawkTargetId);
            if (gawked != null && gawked.isAlive()) {
                forceClearBrainAndGoals(mob, false);
                mob.getLookControl().setLookAt(gawked, 65.0f, 65.0f);
                Vec3 dir = gawked.getEyePosition().subtract(mob.getEyePosition());
                float targetYaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0f;
                mob.setYRot(targetYaw);
                mob.setYHeadRot(targetYaw);
                mob.yBodyRot = targetYaw;
            }
        }
    }

    /** Post-tick enforcement for custom modded mobs that override aiStep()/customServerAiStep() directly! */
    private static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || !(entity instanceof Mob mob) || !mob.isAlive()) return;
        if (!mob.hasData(AcaInfinity.ENTITY_STATUS)) return;
        long now = mob.level().getGameTime();
        EntityStatusData st = AcaInfinity.status(mob);

        if (st.isStandingAttention(now) || st.isAshamed(now) || st.isCrystalSnared(now)) {
            mob.setDeltaMovement(0, Math.min(0, mob.getDeltaMovement().y), 0);
            if (st.isAshamed(now)) mob.setXRot(44.0f);
        } else if (st.isParentSlowed(now)) {
            Vec3 away = mob.position().subtract(st.parentFearOrigin).multiply(1, 0, 1);
            if (away.lengthSqr() < 0.01) away = new Vec3(0, 0, 1);
            Vec3 fleeDir = away.normalize();
            float fleeYaw = (float) (Mth.atan2(fleeDir.z, fleeDir.x) * (180.0 / Math.PI)) - 90.0f;
            mob.setYRot(fleeYaw);
            mob.setYHeadRot(fleeYaw);
            mob.yBodyRot = fleeYaw;
            mob.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(fleeDir.x * 0.22, 0.0, fleeDir.z * 0.22));
        }
    }

    private static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();

        // 1. Restore Reality-Transmuted Blocks
        var list = TRANSMUTATIONS.get(level);
        if (list != null && !list.isEmpty()) {
            Iterator<TransmutedBlock> it = list.iterator();
            while (it.hasNext()) {
                TransmutedBlock tb = it.next();
                if (tb.restoreAt <= now) {
                    level.setBlock(tb.pos, tb.original, Block.UPDATE_CLIENTS);
                    it.remove();
                }
            }
        }

        // 2. Maintain Space Holographic Decoys & Detonate on Expiry / Destruction!
        var decoys = ACTIVE_DECOYS.get(level);
        if (decoys != null && !decoys.isEmpty()) {
            Iterator<SpaceDecoy> dit = decoys.iterator();
            while (dit.hasNext()) {
                SpaceDecoy d = dit.next();
                Entity ent = level.getEntity(d.entityId);
                boolean expired = now >= d.expireTick || ent == null || !ent.isAlive();
                if (!expired && ent instanceof LivingEntity decoyLiv) {
                    if (now % 6L == 0L) {
                        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(d.ownerId);
                        for (Mob m : level.getEntitiesOfClass(Mob.class, decoyLiv.getBoundingBox().inflate(16.0),
                                mob -> mob.isAlive() && (owner == null || !isFriendlyTo(owner, mob)))) {
                            forceAssignAttackTarget(m, decoyLiv);
                        }
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, decoyLiv.getX(), decoyLiv.getY() + 1.0, decoyLiv.getZ(),
                                8, 0.3, 0.5, 0.3, 0.05);
                    }
                } else {
                    Vec3 burstPos = ent != null ? ent.position() : d.pos;
                    if (ent != null) ent.discard();
                    ServerPlayer owner = level.getServer().getPlayerList().getPlayer(d.ownerId);
                    for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(burstPos, 11.0, 8.0, 11.0), LivingEntity::isAlive)) {
                        if (foe instanceof ArmorStand) continue;
                        if (owner != null && isFriendlyTo(owner, foe)) continue;
                        Vec3 pull = burstPos.subtract(foe.position()).scale(0.45);
                        foe.setDeltaMovement(pull.x, 0.35, pull.z);
                        foe.hurtMarked = true;
                        AcademyCompat.hurt(foe, owner != null ? level.damageSources().explosion(owner, owner) : level.damageSources().magic(), 36.0f, true);
                    }
                    level.playSound(null, BlockPos.containing(burstPos), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.15f, 1.1f);
                    InfinityNetwork.broadcastCue(level, d.ownerId, InfinityNetwork.CUE_SPACE_DECOY_BURST, 0,
                            burstPos, burstPos.add(0, 1.2, 0), 64.0);
                    dit.remove();
                }
            }
        }
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        long now = victim.level().getGameTime();

        // Layer 1B: Block contact/skill damage emitted by Ashamed, Standing-Attention, or Friendly Mind-Controlled mobs!
        Entity attackerEnt = event.getSource().getEntity();
        if (attackerEnt != null && attackerEnt.hasData(AcaInfinity.ENTITY_STATUS)) {
            EntityStatusData ast = AcaInfinity.status(attackerEnt);
            if (ast.isStandingAttention(now) || ast.isAshamed(now) || ast.isParentSlowed(now)) {
                event.setCanceled(true);
                return;
            }
            if (ast.isMindControlled(now) && victim.level() instanceof ServerLevel sl) {
                Player master = sl.getPlayerByUUID(ast.mindMaster);
                if (victim.getUUID().equals(ast.mindMaster) || master != null && isFriendlyTo(master, victim)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }

        if (!victim.hasData(AcaInfinity.ENTITY_STATUS)) return;
        EntityStatusData st = AcaInfinity.status(victim);

        float mult = 1.0f;
        if (st.isAshamed(now)) mult += 0.35f;
        if (st.isShattered(now)) mult += 0.35f;
        float adjusted = event.getAmount() * mult;

        if (!applyingTemporalReckoning && st.isTimeFrozen(now) && adjusted > 0.0f) {
            st.frozenDamagePool += adjusted;
            if (attackerEnt instanceof Player p) {
                st.frozenAttacker = p.getUUID();
            }
            InfinityNetwork.syncEntityMark(victim);
            if (victim.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SCRAPE, victim.getX(), victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                        10, 0.25, 0.35, 0.25, 0.05);
                sl.playSound(null, victim.blockPosition(), SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 0.8f, 1.55f);
            }
            event.setCanceled(true);
            return;
        }

        if (mult > 1.01f) {
            event.setAmount(adjusted);
        }
    }

    private static void applyTrueNameSuppressionIfHukou(ServerPlayer player, ServerLevel level, LivingEntity foe, long now, Stone trigger) {
        if (!foe.hasData(AcaInfinity.ENTITY_STATUS)) return;
        EntityStatusData st = AcaInfinity.status(foe);
        if (st.hasHukou(now) && (st.isAshamed(now) || trigger == Stone.POWER || trigger == Stone.MIND)) {
            AcademyCompat.hurt(foe, level.damageSources().indirectMagic(player, player), 28.0f, true);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, foe.getX(), foe.getY() + foe.getBbHeight() * 0.6, foe.getZ(),
                    20, 0.35, 0.4, 0.35, 0.06);
            player.displayClientMessage(Component.translatable("msg.aca_infinity.hukou_true_name")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    // ==================== TARGETING & PROTECTION HELPERS ====================
    private static Entity findAimedHostile(ServerPlayer player, ServerLevel level, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 reach = eye.add(look.scale(range));
        AABB search = player.getBoundingBox().expandTowards(look.scale(range)).inflate(2.5);
        EntityHitResult eHit = ProjectileUtil.getEntityHitResult(level, player, eye, reach, search,
                e -> e.isAlive() && e instanceof LivingEntity && !isFriendlyTo(player, e) && !(e instanceof ArmorStand), 0.75f);
        return eHit != null ? eHit.getEntity() : null;
    }

    private static Entity findHukouTarget(ServerPlayer player, ServerLevel level, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 reach = eye.add(look.scale(range));
        AABB search = player.getBoundingBox().expandTowards(look.scale(range)).inflate(2.5);
        EntityHitResult eHit = ProjectileUtil.getEntityHitResult(level, player, eye, reach, search,
                e -> e.isAlive() && e != player && !(e instanceof Player) && !(e instanceof ArmorStand)
                        && (e instanceof LivingEntity || e instanceof SanctuaryShipEntity), 0.65f);
        if (eHit != null) return eHit.getEntity();

        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(24.0),
                ent -> ent.isAlive() && ent != player && !(ent instanceof Player) && !(ent instanceof ArmorStand)
                        && (ent instanceof LivingEntity || ent instanceof SanctuaryShipEntity))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist < 0.01 || dist > 24.0) continue;
            double dot = to.normalize().dot(look);
            if (dot < 0.55) continue;
            double score = dist * (1.2 - dot);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private static List<LivingEntity> findHostilesNear(ServerLevel level, ServerPlayer player, Vec3 center, double radius) {
        AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e instanceof ArmorStand) continue;
            if (e.distanceToSqr(center) > radius * radius) continue;
            if (isFriendlyTo(player, e)) continue;
            out.add(e);
        }
        return out;
    }

    private static List<LivingEntity> findHostilesAlongRay(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to, double thickness) {
        AABB box = new AABB(from, to).inflate(thickness);
        Map<LivingEntity, Double> hits = new HashMap<>();
        for (Entity hit : level.getEntities(player, box, Entity::isAlive)) {
            Entity body = hit instanceof PartEntity<?> part ? part.getParent() : hit;
            if (!(body instanceof LivingEntity living) || living instanceof ArmorStand || !living.isAlive()
                    || isFriendlyTo(player, living)) continue;
            var bounds = hit.getBoundingBox().inflate(thickness);
            var contact = bounds.contains(from) ? from : bounds.clip(from, to).orElse(null);
            if (contact != null) hits.merge(living, from.distanceToSqr(contact), Math::min);
        }
        return hits.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList();
    }

    public static boolean isFriendlyTo(Player player, Entity entity) {
        if (entity == player || player.isAlliedTo(entity) || entity.isAlliedTo(player)) return true;
        if (entity instanceof Player other) return player.isAlliedTo(other) || !player.canHarmPlayer(other);
        if (entity instanceof TamableAnimal pet && pet.isTame() && player.getUUID().equals(pet.getOwnerUUID())) return true;
        if (entity instanceof SanctuaryShipEntity ship && !ship.isHostileShip()) return true;
        if (entity.hasData(AcaInfinity.ENTITY_STATUS)) {
            long now = entity.level().getGameTime();
            EntityStatusData st = AcaInfinity.status(entity);
            if (st.isMindControlled(now) && player.getUUID().equals(st.mindMaster)) return true;
        }
        return entity.getType().is(AcaInfinity.PROTECTED) && !(entity instanceof Enemy);
    }

    public static boolean isSnapProtected(Player player, LivingEntity entity) {
        if (entity instanceof ArmorStand) return true;
        if (isFriendlyTo(player, entity)) return true;
        if (entity instanceof TamableAnimal pet && pet.isTame()) return true;
        return entity.getType().is(AcaInfinity.PROTECTED);
    }

    public static boolean isBossEntity(Entity entity) {
        if (entity instanceof SanctuaryShipEntity || entity instanceof EnderDragon
                || entity instanceof WitherBoss || entity instanceof Warden
                || entity instanceof ElderGuardian) {
            return true;
        }
        return entity.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES);
    }
}
