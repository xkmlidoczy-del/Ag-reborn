package cn.aca.omnitrix;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 4-Layer Universal Modded Mob AI Hijack & Practical Control Engine for ACA-Omnitrix:
 * - Layer 1: Event Interception (LivingChangeTargetEvent retarget block + LivingIncomingDamageEvent friendly-fire & terror-contact cancel)
 * - Layer 2: 1.21.1 Brain<?> Memory Override (ATTACK_TARGET, ANGRY_AT, WALK_TARGET, CANT_REACH_WALK_TARGET_SINCE)
 * - Layer 3: GoalSelector / TargetSelector Flag Locks + Attribute Buffs/Debuffs + stopUsingItem()
 * - Layer 4: Post-aiStep (EntityTickEvent.Post) Deterministic Auto-Lunge & Melee Swing Fallback / Forced Flee / Nerve-Pin Lock
 */
public final class OmnitrixAiControl {
    private static final ResourceLocation MOD_PUPPET_ATK = Omnitrix.id("puppet_atk_boost");
    private static final ResourceLocation MOD_PUPPET_SPD = Omnitrix.id("puppet_spd_boost");
    private static final ResourceLocation MOD_OVERLOAD_WEAK = Omnitrix.id("overload_weakness");
    private static final ResourceLocation MOD_PARALYSIS_ARMOR = Omnitrix.id("nerve_pin_armor_break");

    private static final class ControlState {
        UUID masterId;
        UUID forcedVictimId;
        UUID previousTarget;
        long nextSearch;
        long puppetUntil = -1L;
        boolean celestialPuppet;
        boolean galvanicPuppet;
        long lastPuppetSwingTick = -100L;

        UUID terrorSourceId;
        long terrorUntil = -1L;

        long paralysisUntil = -1L;
        Vec3 pinPos;
        boolean goalsLocked;
    }

    private static final Map<LivingEntity, ControlState> STATES = new WeakHashMap<>();

    private OmnitrixAiControl() {}

    static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, OmnitrixAiControl::onChangeTarget);
        bus.addListener(EventPriority.HIGH, OmnitrixAiControl::onIncomingDamage);
        bus.addListener(OmnitrixAiControl::onEntityTickPre);
        bus.addListener(OmnitrixAiControl::onEntityTickPost);
    }

    private static ControlState state(LivingEntity entity, boolean create) {
        ControlState s = STATES.get(entity);
        if (s == null && create) {
            s = new ControlState();
            STATES.put(entity, s);
        }
        return s;
    }

    private static long worldTime(Entity e) {
        return e.level().getServer() != null
                ? e.level().getServer().overworld().getGameTime()
                : e.level().getGameTime();
    }

    public static boolean isPuppetOf(Entity entity, UUID masterId) {
        if (!(entity instanceof LivingEntity le) || masterId == null) return false;
        ControlState s = STATES.get(le);
        return s != null && masterId.equals(s.masterId) && s.puppetUntil > worldTime(le);
    }

    public static boolean isAnyPuppet(Entity entity) {
        if (!(entity instanceof LivingEntity le)) return false;
        ControlState s = STATES.get(le);
        return s != null && s.masterId != null && s.puppetUntil > worldTime(le);
    }

    public static boolean isParalyzed(Entity entity) {
        if (!(entity instanceof LivingEntity le)) return false;
        ControlState s = STATES.get(le);
        return s != null && s.paralysisUntil > worldTime(le);
    }

    public static boolean isTerrified(Entity entity) {
        if (!(entity instanceof LivingEntity le)) return false;
        ControlState s = STATES.get(le);
        return s != null && s.terrorUntil > worldTime(le);
    }

    /** Wipes target & Brain anger from nearby mobs when Ghostfreak cloaks! */
    public static int wipeNearbyAggro(ServerPlayer caster, double radius) {
        ServerLevel level = caster.serverLevel();
        int count = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class,
                AABB.ofSize(caster.position(), radius * 2, radius * 2, radius * 2),
                m -> m.isAlive() && m.distanceToSqr(caster) <= radius * radius && !isPuppetOf(m, caster.getUUID()))) {
            boolean hadAggro = mob.getTarget() == caster || mob.getLastHurtByMob() == caster;
            Brain<?> brain = mob.getBrain();
            try {
                Optional<LivingEntity> bt = brain.getMemory(MemoryModuleType.ATTACK_TARGET);
                if (bt != null && bt.isPresent() && bt.get() == caster) hadAggro = true;
            } catch (Exception ignored) {}
            if (hadAggro) {
                mob.setTarget(null);
                mob.setLastHurtByMob(null);
                mob.getNavigation().stop();
                clearBrainCombatMemories(mob);
                count++;
            }
        }
        return count;
    }

    /** A possessed mob guards its owner until a rival becomes available. */
    public static boolean hijackOrSubjugate(ServerPlayer master, LivingEntity target, int durationTicks,
                                            boolean celestial, boolean galvanic) {
        if (target == null || !target.isAlive() || target == master || target instanceof ServerPlayer) return false;
        if (target instanceof Mob mob && !canPossess(master, mob)) return false;
        long now = worldTime(target);
        ServerLevel level = master.serverLevel();

        // Check if there are other enemies nearby for a puppet to fight
        List<LivingEntity> rivals = level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(target.position(), 52.0, 28.0, 52.0),
                e -> e != target && e != master && e.isAlive()
                        && !isPuppetOf(e, master.getUUID())
                        && Abilities.validTarget(master, e, false));

        if (target instanceof Mob mob) {
            if (AcademyInterop.contracts() && !cn.academy.port.api.ControlRelations.bind(master, mob, now + durationTicks, "omnitrix")) return false;
            ControlState st = state(mob, true);
            st.previousTarget = mob.getTarget() == null ? null : mob.getTarget().getUUID();
            st.masterId = master.getUUID();
            st.puppetUntil = now + durationTicks;
            st.celestialPuppet = celestial;
            st.galvanicPuppet = galvanic;
            st.terrorUntil = -1L;
            st.paralysisUntil = -1L;
            unlockGoalsIfNeeded(mob, st);

            LivingEntity primaryRival = rivals.stream()
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(mob)))
                    .orElse(null);
            st.forcedVictimId = primaryRival != null ? primaryRival.getUUID() : null;

            applyModifier(mob, Attributes.ATTACK_DAMAGE, MOD_PUPPET_ATK,
                    celestial ? 0.75 : 0.40, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            applyModifier(mob, Attributes.MOVEMENT_SPEED, MOD_PUPPET_SPD,
                    0.28, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

            mob.setLastHurtByMob(primaryRival);
            mob.setTarget(primaryRival);
            overrideBrainTarget(mob, primaryRival);
            if (primaryRival != null) {
                mob.getNavigation().moveTo(primaryRival, 1.32);
            }
            WristNetwork.cueNear(level, master.getUUID(), celestial ? Kind.CELESTIAL.ordinal() : Kind.CLASSIC.ordinal(),
                    WristNetwork.PUPPET_CUE, galvanic ? WristNetwork.FLAG_LINKED : 0,
                    master.getEyePosition(), mob.position().add(0, mob.getBbHeight() + 0.35, 0), 64.0);
            return true;
        }

        // Living targets without a mob AI can still receive the form's disruption.
        if (galvanic) {
            inflictParalysis(master, target, celestial ? 95 : 70, true);
            OmnitrixDamage.hit(master, target, celestial ? 145_000f : 21f, true);
        } else {
            inflictTerror(master, target, celestial ? 90 : 65);
            OmnitrixDamage.hit(master, target, celestial ? 135_000f : 19f, true);
        }
        return true;
    }

    /** Commands all active puppets of `master` to focus-fire `focusTarget`! */
    public static int commandPuppetsFocus(ServerPlayer master, LivingEntity focusTarget) {
        if (focusTarget == null || !Abilities.validTarget(master, focusTarget, true)) return 0;
        long now = worldTime(master);
        int commanded = 0;
        for (Mob puppet : master.serverLevel().getEntitiesOfClass(Mob.class,
                AABB.ofSize(master.position(), 64.0, 32.0, 64.0),
                m -> m.isAlive() && isPuppetOf(m, master.getUUID()))) {
            ControlState st = STATES.get(puppet);
            if (st == null) continue;
            st.forcedVictimId = focusTarget.getUUID();
            st.puppetUntil = Math.max(st.puppetUntil, now + 100);
            if (AcademyInterop.contracts()) cn.academy.port.api.ControlRelations.bind(master, puppet, st.puppetUntil, "omnitrix");
            puppet.setTarget(focusTarget);
            overrideBrainTarget(puppet, focusTarget);
            puppet.getNavigation().moveTo(focusTarget, 1.35);
            commanded++;
        }
        return commanded;
    }

    /** Ectonurite Soul-Terror: wipes aggro, cancels contact/melee damage, and forces turn-around flee! */
    public static void inflictTerror(ServerPlayer caster, LivingEntity victim, int ticks) {
        if (victim == null || !victim.isAlive() || victim == caster || AcademyInterop.holdingShieldArmor(victim)) return;
        if (isPuppetOf(victim, caster.getUUID())) return;
        long now = worldTime(victim);
        ControlState st = state(victim, true);
        st.terrorSourceId = caster.getUUID();
        st.terrorUntil = Math.max(st.terrorUntil, now + ticks);
        applyModifier(victim, Attributes.ATTACK_DAMAGE, MOD_OVERLOAD_WEAK, -0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        victim.stopUsingItem();
        if (victim instanceof Mob mob) {
            mob.setAggressive(false);
            mob.setTarget(null);
            clearBrainCombatMemories(mob);
        }
        WristNetwork.cueNear(caster.serverLevel(), caster.getUUID(),
                Omnitrix.wrist(caster).kind() == Kind.CELESTIAL ? Kind.CELESTIAL.ordinal() : Kind.CLASSIC.ordinal(),
                WristNetwork.TERROR_CUE, 0, caster.getEyePosition(),
                victim.position().add(0, victim.getBbHeight() + 0.25, 0), 64.0);
    }

    /** Galvanic / Grey-Matter Acupressure Nerve-Pin: hard 4-layer paralysis locking even custom-aiStep modded monsters! */
    public static void inflictParalysis(ServerPlayer caster, LivingEntity victim, int ticks, boolean weakenAttack) {
        if (victim == null || !victim.isAlive() || victim == caster || AcademyInterop.holdingShieldArmor(victim)) return;
        if (isPuppetOf(victim, caster.getUUID())) return;
        long now = worldTime(victim);
        ControlState st = state(victim, true);
        st.paralysisUntil = Math.max(st.paralysisUntil, now + ticks);
        st.pinPos = victim.position();
        applyModifier(victim, Attributes.ARMOR, MOD_PARALYSIS_ARMOR, -0.40, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (weakenAttack) {
            applyModifier(victim, Attributes.ATTACK_DAMAGE, MOD_OVERLOAD_WEAK, -0.45, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        victim.stopUsingItem();
        victim.setDeltaMovement(0, Math.min(0, victim.getDeltaMovement().y), 0);
        victim.hurtMarked = true;
        if (victim instanceof Mob mob) {
            mob.setAggressive(false);
            mob.setTarget(null);
            mob.getNavigation().stop();
            clearBrainCombatMemories(mob);
            if (!st.goalsLocked) {
                for (Goal.Flag f : EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK, Goal.Flag.TARGET)) {
                    mob.goalSelector.disableControlFlag(f);
                    mob.targetSelector.disableControlFlag(f);
                }
                st.goalsLocked = true;
            }
        }
    }

    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        ControlState st = STATES.get(mob);
        if (st == null) return;
        long now = worldTime(mob);

        if (st.paralysisUntil > now || st.terrorUntil > now) {
            if (event.getNewAboutToBeSetTarget() != null) {
                event.setNewAboutToBeSetTarget(null);
            }
            return;
        }

        if (st.masterId != null && st.puppetUntil > now) {
            LivingEntity proposed = event.getNewAboutToBeSetTarget();
            LivingEntity rival = findBestRival(mob, st);
            if (proposed != rival) event.setNewAboutToBeSetTarget(rival);
        }
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity attacker = event.getSource().getEntity();
        LivingEntity victim = event.getEntity();

        if (attacker instanceof LivingEntity atkLiving) {
            ControlState atkState = STATES.get(atkLiving);
            if (atkState != null) {
                long now = worldTime(atkLiving);
                // Pacified / terrified / paralyzed enemies deal 0 melee/contact/ability damage!
                if (atkState.paralysisUntil > now || atkState.terrorUntil > now) {
                    event.setCanceled(true);
                    return;
                }
                // Puppets never hurt their master or fellow puppets!
                if (atkState.masterId != null && atkState.puppetUntil > now) {
                    var master = victim.level().getPlayerByUUID(atkState.masterId);
                    if (victim.getUUID().equals(atkState.masterId) || isPuppetOf(victim, atkState.masterId)
                            || master != null && (master.isAlliedTo(victim) || victim.isAlliedTo(master)
                            || victim instanceof net.minecraft.world.entity.TamableAnimal pet && master.getUUID().equals(pet.getOwnerUUID()))) {
                        event.setCanceled(true);
                        return;
                    }
                }
            }
            // Master never accidentally hurts their own possessed puppet!
            if (attacker instanceof ServerPlayer sp && isPuppetOf(victim, sp.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    private static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity le) || le.level().isClientSide || !(le.level() instanceof ServerLevel sl)) return;
        ControlState st = STATES.get(le);
        if (st == null) return;
        long now = worldTime(le);

        // Clean up expired modifiers
        if (st.puppetUntil > 0 && (now >= st.puppetUntil || sl.getPlayerByUUID(st.masterId) == null)) {
            if (le instanceof Mob mob) {
                var previous = st.previousTarget == null ? null : sl.getEntity(st.previousTarget);
                var restored = previous instanceof LivingEntity living && living.isAlive() ? living : null;
                mob.setTarget(restored);
                overrideBrainTarget(mob, restored);
            }
            if (AcademyInterop.contracts()) cn.academy.port.api.ControlRelations.release(le, "omnitrix");
            st.puppetUntil = -1L;
            st.masterId = null;
            st.forcedVictimId = null;
            removeModifier(le, Attributes.ATTACK_DAMAGE, MOD_PUPPET_ATK);
            removeModifier(le, Attributes.MOVEMENT_SPEED, MOD_PUPPET_SPD);
        }
        if (st.terrorUntil > 0 && now >= st.terrorUntil) {
            st.terrorUntil = -1L;
            st.terrorSourceId = null;
            if (st.paralysisUntil <= now) removeModifier(le, Attributes.ATTACK_DAMAGE, MOD_OVERLOAD_WEAK);
        }
        if (st.paralysisUntil > 0 && now >= st.paralysisUntil) {
            st.paralysisUntil = -1L;
            st.pinPos = null;
            removeModifier(le, Attributes.ARMOR, MOD_PARALYSIS_ARMOR);
            if (st.terrorUntil <= now) removeModifier(le, Attributes.ATTACK_DAMAGE, MOD_OVERLOAD_WEAK);
            if (le instanceof Mob mob) unlockGoalsIfNeeded(mob, st);
        }
        if (st.puppetUntil < 0 && st.terrorUntil < 0 && st.paralysisUntil < 0) {
            STATES.remove(le);
            return;
        }

        // 1. Nerve-Pin / Galvanic Overload Paralysis
        if (st.paralysisUntil > now) {
            le.stopUsingItem();
            le.setDeltaMovement(0, Math.min(0, le.getDeltaMovement().y), 0);
            if (le instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
                clearBrainCombatMemories(mob);
            }
            return;
        }

        // 2. Ectonurite Soul-Terror Forced Flee
        if (st.terrorUntil > now) {
            le.stopUsingItem();
            if (le instanceof Mob mob) {
                mob.setTarget(null);
                clearBrainCombatMemories(mob);
                Entity src = st.terrorSourceId != null ? sl.getEntity(st.terrorSourceId) : null;
                if (src != null) {
                    Vec3 away = mob.position().subtract(src.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                    Vec3 fleeGoal = mob.position().add(away.scale(9.0));
                    mob.getNavigation().moveTo(fleeGoal.x, fleeGoal.y, fleeGoal.z, 1.35);
                    try {
                        mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(fleeGoal, 1.35f, 1));
                    } catch (Exception ignored) {}
                }
            }
            return;
        }

        // 3. 4-Layer Puppet Subjugation & Deterministic Auto-Strike Fallback
        if (st.masterId != null && st.puppetUntil > now && le instanceof Mob mob) {
            Entity masterEnt = sl.getEntity(st.masterId);
            ServerPlayer master = masterEnt instanceof ServerPlayer sp ? sp : null;
            LivingEntity rival = findBestRival(mob, st);
            if (rival != null) {
                if (mob.getTarget() != rival) mob.setTarget(rival);
                overrideBrainTarget(mob, rival);
                if (now % 5 == 0) {
                    mob.getNavigation().moveTo(rival, 1.32);
                }
                double dist = mob.distanceTo(rival);
                double reach = Math.max(3.6, mob.getBbWidth() + rival.getBbWidth() + 2.2);
                if (dist > reach * 0.82 && mob.onGround() && now % 8 == 0) {
                    Vec3 toRival = rival.position().subtract(mob.position()).multiply(1, 0, 1);
                    if (toRival.lengthSqr() > 1e-4) {
                        Vec3 step = toRival.normalize().scale(0.34);
                        mob.setDeltaMovement(mob.getDeltaMovement().x * 0.4 + step.x, mob.getDeltaMovement().y,
                                mob.getDeltaMovement().z * 0.4 + step.z);
                    }
                }
                if (dist <= reach && now - st.lastPuppetSwingTick >= 14L) {
                    st.lastPuppetSwingTick = now;
                    mob.swing(InteractionHand.MAIN_HAND);
                    var attribute = mob.getAttribute(Attributes.ATTACK_DAMAGE);
                    double baseAtk = attribute == null ? 0 : attribute.getValue();
                    if (baseAtk <= 1.0) baseAtk = 6.0;
                    float strikeDmg = (float) Math.max(baseAtk * (st.celestialPuppet ? 3.5 : 1.55),
                            st.celestialPuppet ? 85_000f : 20f);
                    boolean hit = master != null
                            ? OmnitrixDamage.hit(master, rival, strikeDmg, st.celestialPuppet)
                            : AcademyInterop.hurt(rival, mob.damageSources().mobAttack(mob), strikeDmg);
                    if (hit) {
                        Vec3 kb = rival.position().subtract(mob.position());
                        if (kb.lengthSqr() > 1e-5) rival.knockback(0.55, -kb.x, -kb.z);
                        if (master != null && now % 2 == 0) {
                            WristNetwork.cueNear(sl, master.getUUID(),
                                    st.celestialPuppet ? Kind.CELESTIAL.ordinal() : Kind.CLASSIC.ordinal(),
                                    WristNetwork.PUPPET_CUE, WristNetwork.FLAG_HIT_ENTITY,
                                    mob.getEyePosition(), rival.getBoundingBox().getCenter(), 56.0);
                        }
                    }
                }
            } else {
                if (mob.getTarget() != null) mob.setTarget(null);
                clearBrainCombatMemories(mob);
                if (master != null && mob.distanceToSqr(master) > 36 && now % 10 == 0)
                    mob.getNavigation().moveTo(master, 1.15);
            }
        }
    }

    private static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity le) || le.level().isClientSide || !(le.level() instanceof ServerLevel sl)) return;
        ControlState st = STATES.get(le);
        if (st == null) return;
        long now = worldTime(le);

        // Post-aiStep hard enforcement so custom-aiStep modded bosses/mobs cannot override Pre-tick locks!
        if (st.paralysisUntil > now) {
            le.setDeltaMovement(0, Math.min(0, le.getDeltaMovement().y), 0);
            if (st.pinPos != null && le.position().distanceToSqr(st.pinPos) > 0.09) {
                le.setPos(st.pinPos.x, le.getY(), st.pinPos.z);
            }
            return;
        }

        if (st.terrorUntil > now && st.terrorSourceId != null) {
            Entity src = sl.getEntity(st.terrorSourceId);
            if (src != null && le.distanceToSqr(src) < 18.0 * 18.0) {
                Vec3 away = le.position().subtract(src.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                float fleeYaw = (float) (Mth.atan2(away.z, away.x) * (180.0 / Math.PI)) - 90.0f;
                le.setYRot(fleeYaw);
                le.setYHeadRot(fleeYaw);
                le.yBodyRot = fleeYaw;
                Vec3 step = new Vec3(away.x * 0.24, 0.0, away.z * 0.24);
                le.move(MoverType.SELF, step);
            }
        }
    }

    private static LivingEntity findBestRival(Mob puppet, ControlState st) {
        if (!(puppet.level() instanceof ServerLevel sl)) return null;
        if (!(sl.getEntity(st.masterId) instanceof ServerPlayer master)) return null;
        if (st.forcedVictimId != null) {
            Entity forced = sl.getEntity(st.forcedVictimId);
            if (forced instanceof LivingEntity le && le != puppet && Abilities.validTarget(master, le, true)) {
                return le;
            }
            st.forcedVictimId = null;
        }
        long now = worldTime(puppet);
        if (now < st.nextSearch) return null;
        st.nextSearch = now + 10;
        LivingEntity rival = sl.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(puppet.position(), 52.0, 28.0, 52.0),
                e -> e != puppet && Abilities.validTarget(master, e, false))
                .stream()
                .min(Comparator.comparingDouble(puppet::distanceToSqr))
                .orElse(null);
        if (rival != null) st.forcedVictimId = rival.getUUID();
        return rival;
    }

    static boolean canPossess(ServerPlayer master, Mob mob) {
        return mob.isAlive() && !mob.isRemoved() && !mob.isSpectator() && !mob.isNoAi()
                && mob.level() == master.level() && !mob.getType().is(PursuitBody.PROTECTED)
                && !(mob instanceof PursuitBody) && !isAnyPuppet(mob)
                && !master.isAlliedTo(mob) && !mob.isAlliedTo(master)
                && !(mob instanceof net.minecraft.world.entity.TamableAnimal pet && master.getUUID().equals(pet.getOwnerUUID()));
    }

    static void clearBrainCombatMemories(Mob mob) {
        Brain<?> brain = mob.getBrain();
        try { brain.eraseMemory(MemoryModuleType.ATTACK_TARGET); } catch (Exception ignored) {}
        try { brain.eraseMemory(MemoryModuleType.ANGRY_AT); } catch (Exception ignored) {}
        try { brain.eraseMemory(MemoryModuleType.WALK_TARGET); } catch (Exception ignored) {}
        try { brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE); } catch (Exception ignored) {}
    }

    private static void overrideBrainTarget(Mob mob, LivingEntity rival) {
        Brain<?> brain = mob.getBrain();
        try { brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE); } catch (Exception ignored) {}
        if (rival == null) {
            clearBrainCombatMemories(mob);
            return;
        }
        try { brain.setMemory(MemoryModuleType.ATTACK_TARGET, rival); } catch (Exception ignored) {}
        try { brain.setMemory(MemoryModuleType.ANGRY_AT, rival.getUUID()); } catch (Exception ignored) {}
        try { brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(rival, 1.30f, 2)); } catch (Exception ignored) {}
    }

    private static void unlockGoalsIfNeeded(Mob mob, ControlState st) {
        if (!st.goalsLocked) return;
        for (Goal.Flag f : EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK, Goal.Flag.TARGET)) {
            mob.goalSelector.enableControlFlag(f);
            mob.targetSelector.enableControlFlag(f);
        }
        st.goalsLocked = false;
    }

    private static void applyModifier(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                      ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount != 0.0) inst.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    private static void removeModifier(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                       ResourceLocation id) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }
}
