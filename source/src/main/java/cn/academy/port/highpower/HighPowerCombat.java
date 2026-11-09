package cn.academy.port.highpower;

import cn.academy.port.AbilityDamage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.PhaseVeilRules;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.DefenseProfile;
import cn.academy.port.highpower.core.DamageMitigation;
import cn.academy.port.highpower.core.HighPowerLevel;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.core.GroundingReturnRules;
import cn.academy.port.highpower.core.PotentialBufferRules;
import cn.academy.port.highpower.core.MagneticDomainShieldRules;
import cn.academy.port.highpower.core.ThermalShellRules;
import cn.academy.port.highpower.core.VectorParryRules;
import cn.academy.port.highpower.core.AutonomousVectorCircleRules;
import cn.academy.port.highpower.core.InertialAnchorRules;
import cn.academy.port.highpower.core.DissipationCurtainShieldRules;
import cn.academy.port.highpower.core.ScatterPlateShares;
import cn.academy.port.highpower.core.SharedShieldReserveShares;
import cn.academy.port.highpower.core.ZeroGapArmorShares;
import cn.academy.port.highpower.core.EmergencyReturnRules;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Server-owned exact HP, under the named reviewed VanillaCombatContract only.
 * Native hurt runs once for acceptance, shield/cooldown, durability, absorption and
 * death/totems. Known vanilla armor/toughness, Resistance and basic Protection are
 * computed on the exact raw residual; arbitrary native fractions are NEVER scaled.
 * Our Pre substitutes a bounded already-mitigated witness before native absorption,
 * allowing its real pool to be consumed once even after a tiny cooldown residual.
 *
 * Cancellation and zero protection remain authoritative. Unknown positive incoming,
 * Pre or reduction changes refuse this exact transaction, not ordinary combat.
 * A locked runtime/data/handler contract is required: matching observed values does
 * not prove absence of invisible, compensating or injected handlers. Unsupported
 * scopes need a separately reviewed adapter; bosses are not integrated.
 * A pinned comparison-only native seam orders active cooldown hits by logical raw
 * amounts; its existing reduction stage installs the true residual before armor.
 * No cooldown timer/lastHurt field reset, rank proxy or fake hit is used.
 * Durability/statistics remain bounded native
 * witnesses, not exact raw-scale simulation. Nonterminating armor math uses the
 * core's documented 80-digit precision. Native side effects cannot be rolled back.
 *
 * Post is after native HP subtraction but before the enclosing totem/death check.
 * Only the enrolled entity's normalized health adapter is synchronized here. No
 * global max-health edit, bypass source, synthetic death or generic forcefield.
 */
@EventBusSubscriber(modid = "academy")
public final class HighPowerCombat {
    public static final String DATA_KEY = "academy:highpower_combat_v1";
    public static final float MAX_NATIVE_WITNESS = 1_000_000F;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MathContext RATIO_CONTEXT = MathContext.DECIMAL128;
    private static final String PENDING_LETHAL_HEALTH = "pendingLethalHealth";
    private static final String CONTRACT = "adapterContract";
    private static final String GROWTH_CURVE = "growthCurve";
    private static final String GROWTH_POSITION = "growthPosition";
    private static final String EARNED_LEVEL = "earnedLevel";
    /** One immutable sample per live entity; saved amounts are still checked on every read. */
    private static final Map<LivingEntity, GrowthValidation> GROWTH_VALIDATION = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private record GrowthValidation(String position, ContinuousHighPowerGrowth.Snapshot sample) {}
    private static final Map<ServerPlayer, PendingGrowth> PENDING_GROWTH = new IdentityHashMap<>();
    private record PendingGrowth(cn.academy.port.core.AbilityProgress owner, String category, int nativeLevel, int certifiedLevel, long revision, BigDecimal position) {}
    private static final Map<LivingEntity, HitHistory> HISTORY = new IdentityHashMap<>();
    private static final Map<LivingEntity, NativeHurtFrame> FRAMES = new IdentityHashMap<>();
    private static final Map<LivingEntity, NativeTrace> TRACES = new IdentityHashMap<>();
    private static final Map<LivingEntity, ExactSkillDamageSource> ACTIVE = new IdentityHashMap<>();
    /** NEW411 owns its entrance before any event callback; a nested call cannot spend another share. */
    private static final Map<ServerPlayer, DeflectionStrike.EmissionPermit> DEFLECTION_ATTACKS = new IdentityHashMap<>();
    /** NEW421 holds its own one-use entrance through all validation and native callbacks. */
    private static final Map<ServerPlayer, ShearWindblade.EmissionPermit> SHEAR_WINDBLADE_ATTACKS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, YawPalm.EmissionPermit> YAW_PALM_ATTACKS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, VectorCage.EmissionPermit> VECTOR_CAGE_ATTACKS = new IdentityHashMap<>();
    /** NEW253 holds its one terminal emission through every native callback and final payment. */
    private static final Map<ServerPlayer, RemoteScatterRelay.EmissionPermit> REMOTE_SCATTER_RELAY_ATTACKS = new IdentityHashMap<>();
    /** NEW431's empty-snapshot hammer never borrows another vector emission's entrance. */
    private static final Map<ServerPlayer, TrajectoryReturn.AirHammerPermit> TRAJECTORY_RETURN_AIR_ATTACKS = new IdentityHashMap<>();
    private static final String[] FIELDS = {"version", "health", "maxHealth", "shield", "maxShield", "shieldEnabled"};
    private static final Map<LivingEntity, ExactCombatState> LETHAL = new IdentityHashMap<>();
    private static final Map<LivingEntity, LivingDeathEvent> DEATHS = new IdentityHashMap<>();
    private static final Map<LivingEntity, List<LivingHealEvent>> HEALS = new IdentityHashMap<>();

    private HighPowerCombat() {}

    /** Exact class checks also refuse subclasses that replace vanilla damage methods. */
    public static boolean supported(LivingEntity entity) { return VanillaCombatContract.supportedClass(entity); }
    /** Hostile targets for the classic controllers and their damage permits: any mod's hostile mob (vanilla Monster is an Enemy too),
     * or a tagged training dummy. Used to be Monster only, which refused slimes, ghasts, phantoms, modded Enemy mobs and dummies. */
    public static boolean hostileTarget(LivingEntity entity) {
        return entity != null && !(entity instanceof net.minecraft.world.entity.player.Player)
                && (entity instanceof net.minecraft.world.entity.monster.Enemy || cn.academy.port.canon.CanonTargetPolicy.trainingTarget(entity));
    }

    public static boolean needsContractApproval(LivingEntity entity) {
        return enabled(entity) && !VanillaCombatContract.ID.equals(tag(entity).getString(CONTRACT));
    }

    /** Fresh entities can be enrolled; old ledgers need explicit approval without refilling. */
    public static Optional<String> unsupportedReason(LivingEntity entity) {
        var reason = VanillaCombatContract.unsupportedReason(entity);
        if (reason.isPresent()) return reason;
        if (entity instanceof net.minecraft.world.entity.player.Player
                && !(entity instanceof NativeCooldownAccess.PlayerDamageCommit))
            return Optional.of("The reviewed Player damage-commit seam is unavailable; ordinary combat remains active");
        if (entity.getPersistentData().contains(DATA_KEY) && !enabled(entity)) return Optional.of("The saved high-power ledger is invalid");
        return needsContractApproval(entity) ? Optional.of("This older ledger needs explicit vanilla damage-contract approval") : Optional.empty();
    }

    public static boolean approveVanillaContract(LivingEntity entity) {
        if (!serverThread(entity) || !enabled(entity) || VanillaCombatContract.unsupportedReason(entity).isPresent()) return false;
        tag(entity).putString(CONTRACT, VanillaCombatContract.ID);
        HISTORY.remove(entity); // No history is invented for a pre-approval cooldown.
        return true;
    }

    /** Highest server-certified capacity coordinate; legacy ledgers retain their exact integer anchor. */
    public static BigDecimal earnedGrowthPosition(ServerPlayer player) {
        if (player == null || !serverThread(player) || read(player).isEmpty()) return BigDecimal.ZERO;
        CompoundTag data = tag(player);
        return data.contains(GROWTH_CURVE) ? ContinuousHighPowerGrowth.requirePosition(new BigDecimal(data.getString(GROWTH_POSITION)))
                : BigDecimal.valueOf(data.getInt("level"));
    }

    /** Raw administrative AbilityProgress edits never change this certification. */
    public static int earnedLevel(ServerPlayer player) {
        if (player == null || !serverThread(player) || read(player).isEmpty()) return 0;
        return tag(player).contains(GROWTH_CURVE) ? tag(player).getInt(EARNED_LEVEL) : tag(player).getInt("level");
    }

    /** First genuine enrollment may certify already-earned native progress once, without refill. */
    public static boolean synchronizeEarnedProgress(ServerPlayer player) {
        return player != null && synchronizeEarnedProgress(player, AbilityStorage.get(player).category);
    }

    /** Genuine first enrollment only; later reads/relearning cannot reinterpret saved XP. */
    public static boolean synchronizeEarnedProgress(ServerPlayer player, String expectedCategory) {
        if (player == null || !serverThread(player) || !enabled(player) || !HighPowerCategories.active(AbilityStorage.get(player))) return false;
        var ability = AbilityStorage.get(player);
        if (!ability.category.equals(expectedCategory) || ability.level > earnedLevel(player) || !HighPowerActiveGrowth.initial(player)) return false;
        HighPowerActiveGrowth.finishInitial(player);
        BigDecimal requested = ContinuousHighPowerGrowth.position(ability.level, ability.levelProgress(SkillCatalog.levelSkillCount(ability)));
        return scheduleActiveGrowth(player, requested);
    }

    /** The only post-enrollment growth entrance: one closed actual native float-XP receipt. */
    public static void earnedExperience(ServerPlayer player, cn.academy.port.core.AbilityProgress.ExperienceReceipt receipt) {
        if (player == null || receipt == null || !serverThread(player)) return;
        var ability = AbilityStorage.get(player);
        if (!receipt.claim(ability) || !enabled(player) || !HighPowerCategories.active(ability)
                || ability.level > earnedLevel(player) || unsupportedReason(player).isPresent()
                || !player.isAlive() && !FRAMES.containsKey(player)) return;
        try {
            HighPowerActiveGrowth.finishInitial(player);
            float threshold = (float)cn.academy.port.core.ClassicRules.levelThreshold(receipt.level(), receipt.count());
            if (!Float.isFinite(threshold) || threshold <= 0) return;
            float before = receipt.before() / threshold, after = receipt.after() / threshold;
            if (!Float.isFinite(before) || !Float.isFinite(after) || before < 0 || after <= before) return;
            // Widen the native floats exactly as ContinuousHighPowerGrowth.position does.
            // Unclamped quotients retain progress after an already-saturated category switch.
            BigDecimal delta = BigDecimal.valueOf((double)after).subtract(BigDecimal.valueOf((double)before));
            BigDecimal position = HighPowerActiveGrowth.position(player);
            PendingGrowth pending = PENDING_GROWTH.get(player);
            if (pending != null && pendingMatches(player, pending)) position = pending.position();
            BigDecimal ceiling = BigDecimal.valueOf(Math.min(5, Math.min(ability.level, earnedLevel(player)) + 1));
            scheduleActiveGrowth(player, position.add(delta).min(ceiling));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            // Corrupt/newer active metadata disables output growth, never rewrites HP or a save.
        }
    }

    private static boolean scheduleActiveGrowth(ServerPlayer player, BigDecimal requested) {
        if (!serverThread(player) || !enabled(player) || unsupportedReason(player).isPresent()
                || !player.isAlive() && !FRAMES.containsKey(player)) return false;
        var ability = AbilityStorage.get(player);
        BigDecimal position = ContinuousHighPowerGrowth.requirePosition(requested);
        if (position.compareTo(HighPowerActiveGrowth.position(player)) <= 0) return false;
        if (canChangeGrowth(player)) return applyActivePosition(player, position);
        PendingGrowth prior = PENDING_GROWTH.get(player);
        long revision = HighPowerActiveGrowth.revision(player);
        if (prior == null || !pendingMatches(player, prior) || position.compareTo(prior.position()) > 0)
            PENDING_GROWTH.put(player, new PendingGrowth(ability, ability.category, ability.level, earnedLevel(player), revision, position));
        return false;
    }

    private static boolean pendingMatches(ServerPlayer player, PendingGrowth pending) {
        try {
            return AbilityStorage.get(player) == pending.owner() && pending.category().equals(pending.owner().category)
                    && pending.nativeLevel() == pending.owner().level && earnedLevel(player) == pending.certifiedLevel()
                    && pending.revision() == HighPowerActiveGrowth.revision(player);
        } catch (IllegalArgumentException | IllegalStateException invalid) { return false; }
    }

    private static void flushEarnedGrowth(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        PendingGrowth pending = PENDING_GROWTH.get(player);
        if (pending == null || !canChangeGrowth(player)) return;
        PENDING_GROWTH.remove(player);
        if (!pendingMatches(player, pending)) return;
        applyActivePosition(player, pending.position());
    }

    private static boolean applyActivePosition(ServerPlayer player, BigDecimal requested) {
        if (!canChangeGrowth(player) || requested.compareTo(HighPowerActiveGrowth.position(player)) <= 0) return false;
        if (requested.compareTo(earnedGrowthPosition(player)) > 0) {
            if (!applyEarnedPosition(player, requested)) return false;
        } else HighPowerActiveGrowth.commit(player, requested);
        HighPowerGrowth.invalidate(player);
        return true;
    }

    /**
     * The finite developer completion seam. The caller must wrap only the real,
     * successfully paid level action. Generic LevelChangeEvent and login cannot
     * certify a raw setLevel mutation. Completion itself always runs exactly once.
     */
    public static void completeEarnedLevel(ServerPlayer player, Runnable completion) {
        java.util.Objects.requireNonNull(completion, "completion");
        if (player == null || !serverThread(player)) { completion.run(); return; }
        var ability = AbilityStorage.get(player);
        int oldLevel = ability.level;
        String originalCategory = ability.category;
        boolean eligible = canChangeGrowth(player) && HighPowerGrowth.available(player) && HighPowerCategories.active(ability)
                && oldLevel >= 1 && oldLevel < 5 && oldLevel <= earnedLevel(player)
                && ability.levelProgress(SkillCatalog.levelSkillCount(ability)) >= 1;
        // Capture legacy/current active output before native completion resets levelExperience.
        if (eligible) HighPowerActiveGrowth.position(player);
        completion.run();
        if (!eligible || AbilityStorage.get(player) != ability || !originalCategory.equals(ability.category)
                || ability.level != oldLevel + 1 || !canChangeGrowth(player)) return;
        migrateGrowth(player);
        int certified = Math.max(tag(player).getInt(EARNED_LEVEL), ability.level);
        tag(player).putInt(EARNED_LEVEL, certified);
        tag(player).putInt("level", certified);
        // A paid integer unlock widens the future ceiling; it never jumps lagging active output.
        HighPowerActiveGrowth.position(player);
    }

    /**
     * Explicit confirmed operator reset only. Both balances must fit the new caps;
     * no clamping, refill, injury erasure, or hidden reserve is permitted here.
     */
    public static boolean resetGrowthContract(ServerPlayer player) {
        if (!canChangeGrowth(player)) return false;
        var ability = AbilityStorage.get(player);
        if (!HighPowerCategories.active(ability)) return false;
        double progress = ability.levelProgress(SkillCatalog.levelSkillCount(ability));
        if (!Double.isFinite(progress) || progress < 0) return false;
        flushRecovery(player);
        var previous = read(player);
        if (previous.isEmpty() || previous.get().isDead()) return false;
        var capacity = ContinuousHighPowerGrowth.sample(ability.level, progress);
        var before = previous.get();
        if (before.health().compareTo(capacity.maxHealth()) > 0 || before.shield().compareTo(capacity.maxShield()) > 0) return false;
        var next = new ExactCombatState(before.health(), capacity.maxHealth(), before.shield(), capacity.maxShield(), false);
        if (!MagneticDomainReserve.canChangeCapacity(player, before, next)
                || !ScatterPlateStorage.canChangeCapacity(player, before, next)) return false;
        try {
            ZeroGapArmorStorage.guardParentWrite(player);
            GrowthChange growth = new GrowthChange(player, before, next, capacity.position(), ability.level,
                    GrowthKind.RESET, capacity.position());
            commitResources(player, next, null, null, growth);
        } catch (IllegalArgumentException | IllegalStateException invalid) { return false; }
        GROWTH_VALIDATION.remove(player);
        PENDING_GROWTH.remove(player);
        HighPowerNaturalRecovery.capacityChanged(player, before, next);
        synchronizeAdapter(player, next);
        HighPowerGrowth.invalidate(player);
        return true;
    }

    /** Compatibility hook; observing a level alone never awards a new capacity. */
    public static boolean promoteLevel(ServerPlayer player, int newLevel) {
        return false;
    }

    private static boolean canChangeGrowth(ServerPlayer player) {
        return player != null && serverThread(player) && enabled(player) && unsupportedReason(player).isEmpty()
                && player.isAlive() && !ACTIVE.containsKey(player) && !FRAMES.containsKey(player)
                && !TRACES.containsKey(player) && !HighPowerNaturalRecovery.healingInProgress(player);
    }

    /** Called only after read() admitted the original v9 integer ledger, never from untrusted capacities. */
    private static void migrateGrowth(ServerPlayer player) {
        CompoundTag data = tag(player);
        if (data.contains(GROWTH_CURVE)) return;
        data.putInt(GROWTH_CURVE, ContinuousHighPowerGrowth.CURVE_VERSION);
        data.putInt(EARNED_LEVEL, data.getInt("level"));
        data.putString(GROWTH_POSITION, BigDecimal.valueOf(data.getInt("level")).toPlainString());
    }

    private static boolean applyEarnedPosition(ServerPlayer player, BigDecimal requested) {
        if (!canChangeGrowth(player)) return false;
        flushRecovery(player);
        var previous = read(player);
        if (previous.isEmpty() || previous.get().isDead()) return false;
        BigDecimal position = ContinuousHighPowerGrowth.requirePosition(requested);
        BigDecimal highest = earnedGrowthPosition(player);
        if (position.compareTo(highest) <= 0) return false;
        if (position.compareTo(BigDecimal.valueOf(Math.min(5, earnedLevel(player) + 1))) > 0) return false;
        var capacity = ContinuousHighPowerGrowth.sample(position);
        ExactCombatState before = previous.get();
        if (capacity.maxHealth().compareTo(before.maxHealth()) < 0 || capacity.maxShield().compareTo(before.maxShield()) < 0) return false;
        ExactCombatState next = before.withMaximums(capacity.maxHealth(), capacity.maxShield());
        if (!MagneticDomainReserve.canChangeCapacity(player, before, next)
                || !ScatterPlateStorage.canChangeCapacity(player, before, next)) return false;
        // Observe the genuine active witness before taking the complete resource snapshot.
        HighPowerActiveGrowth.position(player);
        try {
            ZeroGapArmorStorage.guardParentWrite(player);
            GrowthChange growth = new GrowthChange(player, before, next, position, earnedLevel(player),
                    GrowthKind.EARNED, position);
            commitResources(player, next, null, null, growth);
        } catch (IllegalArgumentException | IllegalStateException invalid) { return false; }
        HighPowerNaturalRecovery.capacityChanged(player, before, next);
        synchronizeAdapter(player, next);
        return true;
    }

    /** Enrollment is an explicit one-time setup; repeated setup cannot refill either pool. */
    public static boolean enroll(LivingEntity entity, int level) {
        if (!serverThread(entity) || unsupportedReason(entity).isPresent() || !entity.isAlive()
                || entity.getPersistentData().contains(DATA_KEY)) return false;
        final HighPowerLevel preset;
        try { preset = HighPowerLevel.forLevel(level); } catch (IllegalArgumentException invalid) { return false; }
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", level);
        tag.putInt(EARNED_LEVEL, level);
        tag.putInt(GROWTH_CURVE, ContinuousHighPowerGrowth.CURVE_VERSION);
        tag.putString(GROWTH_POSITION, Integer.toString(level));
        tag.putString(CONTRACT, VanillaCombatContract.ID);
        tag.putBoolean("bracerActive", false);
        tag.putLong("lastShieldSpend", entity.level().getGameTime());
        tag.putLong("lastShieldTick", Long.MIN_VALUE);
        entity.getPersistentData().put(DATA_KEY, tag);
        ExactCombatState state = ExactCombatState.full(preset).withShieldEnabled(false);
        write(entity, state);
        synchronizeAdapter(entity, state);
        if (entity instanceof ServerPlayer player) HighPowerActiveGrowth.enrolled(player, level);
        return true;
    }

    public static boolean enabled(LivingEntity entity) { return read(entity).isPresent(); }

    /** City-only owner for the shared curve; restores balances or widens caps without refilling. */
    public static boolean synchronizeCityNpc(cn.academy.port.city.CityNpcEntity npc, BigDecimal position,
                                             CompoundTag saved, double legacyHealthFraction) {
        if (!serverThread(npc) || !supported(npc) || npc.esper() == cn.academy.port.city.CityEspers.Profile.NONE) return false;
        var capacity = ContinuousHighPowerGrowth.sample(position);
        if (position.compareTo(BigDecimal.valueOf(npc.esperLevel())) < 0
                || position.compareTo(BigDecimal.valueOf(Math.min(5, npc.esperLevel() + 1))) > 0) return false;
        ExactCombatState before = read(npc).orElse(null);
        if (saved != null && saved.isEmpty()) before = null; // one-time classic-save migration
        if (saved != null && !saved.isEmpty()) {
            try {
                Map<String, String> values = new HashMap<>();
                for (String field : FIELDS) {
                    if (!saved.contains(field, Tag.TAG_STRING)) return false;
                    values.put(field, saved.getString(field));
                }
                before = ExactCombatState.fromDecimalMap(values);
                var old = ContinuousHighPowerGrowth.sample(new BigDecimal(saved.getString(GROWTH_POSITION)));
                if (before.shieldEnabled() || before.maxHealth().compareTo(old.maxHealth()) != 0
                        || before.maxShield().compareTo(old.maxShield()) != 0) return false;
            } catch (IllegalArgumentException invalid) { return false; }
        }
        ExactCombatState next;
        if (before == null) {
            double fraction = Double.isFinite(legacyHealthFraction) ? Math.clamp(legacyHealthFraction, 0, 1) : 0;
            next = new ExactCombatState(capacity.maxHealth().multiply(BigDecimal.valueOf(fraction)),
                    capacity.maxHealth(), capacity.maxShield(), capacity.maxShield(), false);
        } else next = before.withMaximums(capacity.maxHealth(), capacity.maxShield());
        CompoundTag data = tag(npc);
        data.putInt("level", npc.esperLevel()); data.putInt(EARNED_LEVEL, npc.esperLevel());
        data.putInt(GROWTH_CURVE, ContinuousHighPowerGrowth.CURVE_VERSION);
        data.putString(GROWTH_POSITION, position.stripTrailingZeros().toPlainString());
        data.putString(CONTRACT, VanillaCombatContract.ID);
        if (!data.contains("lastShieldSpend")) data.putLong("lastShieldSpend", npc.level().getGameTime());
        npc.getPersistentData().put(DATA_KEY, data);
        GROWTH_VALIDATION.remove(npc); write(npc, next); synchronizeAdapter(npc, next);
        return true;
    }

    public static CompoundTag cityNpcCheckpoint(cn.academy.port.city.CityNpcEntity npc) {
        return enabled(npc) ? tag(npc).copy() : new CompoundTag();
    }

    /** A city heal is expressed in the same exact health units as every other heal. */
    public static void healCityNpc(cn.academy.port.city.CityNpcEntity npc, BigDecimal amount) {
        if (!serverThread(npc) || amount == null || amount.signum() <= 0) return;
        var before = read(npc).orElse(null); if (before == null || before.isDead()) return;
        var next = new ExactCombatState(before.health().add(amount).min(before.maxHealth()), before.maxHealth(),
                before.shield(), before.maxShield(), false);
        write(npc, next); synchronizeAdapter(npc, next);
    }

    /** Bounded field medical supply; uses the existing exact pool, never grants shield or capacity. */
    public static void healGuardian(cn.academy.port.city.defense.GuardianEntity guardian, BigDecimal fraction) {
        if(!serverThread(guardian)||fraction==null||fraction.signum()<=0||fraction.compareTo(new BigDecimal("0.12"))>0)return;
        var before=read(guardian).orElse(null);if(before==null||before.isDead())return;
        var next=new ExactCombatState(before.health().add(before.maxHealth().multiply(fraction)).min(before.maxHealth()),before.maxHealth(),before.shield(),before.maxShield(),false);
        write(guardian,next);synchronizeAdapter(guardian,next);
    }

    /** Only the validated one-time curve converter owns this cache/adapter reset. */
    static void curveMigrated(LivingEntity entity, ExactCombatState state) {
        GROWTH_VALIDATION.remove(entity); HISTORY.remove(entity);
        FRAMES.remove(entity); TRACES.remove(entity); ACTIVE.remove(entity);
        HEALS.remove(entity); LETHAL.remove(entity); DEATHS.remove(entity);
        if (entity instanceof ServerPlayer player) {
            PENDING_GROWTH.remove(player);
            HighPowerGrowth.invalidate(player);
        }
        synchronizeAdapter(entity, state);
    }
    /** True for the closed, server-created skill hits that carry their own exact accounting (read-only; no capability is exposed). */
    public static boolean isExactSkillSource(DamageSource source) { return source instanceof ExactSkillDamageSource; }
    /** Read-only snapshot for the server-owned natural-recovery adapter. */
    static Optional<ExactCombatState> exactState(LivingEntity entity) { return read(entity); }
    /** Identical ledger validation for observer-only geometry, without populating mutable caches. */
    static Optional<ExactCombatState> exactStateSnapshot(LivingEntity entity) { return read(entity, false); }
    /** Finish native events before checking exact missing HP; never awards a new budget. */
    static void settleRecovery(LivingEntity entity) { if (serverThread(entity)) { flushRecovery(entity); flushEarnedGrowth(entity); } }
    static void resynchronizeExact(LivingEntity entity) { if (serverThread(entity)) read(entity).ifPresent(state -> synchronizeAdapter(entity, state)); }
    /** Final, source-proven FoodData recovery. Other heal events retain their existing 1:1 units. */
    static boolean applyNaturalHealing(ServerPlayer player, BigDecimal amount) {
        if (!serverThread(player) || amount == null || amount.signum() <= 0) return false;
        var optional = read(player);
        if (optional.isEmpty() || optional.get().isDead()) return false;
        ExactCombatState state = optional.get();
        BigDecimal healed = amount.min(state.maxHealth().subtract(state.health()));
        if (healed.signum() <= 0) { synchronizeAdapter(player, state); return false; }
        var next = new ExactCombatState(state.health().add(healed), state.maxHealth(), state.shield(), state.maxShield(), false);
        write(player, next); synchronizeAdapter(player, next); return true;
    }
    public static int level(LivingEntity entity) { return enabled(entity) ? tag(entity).getInt("level") : 0; }
    public static String healthText(LivingEntity entity) {
        return read(entity).map(s -> CombatAmounts.decimalString(s.health())).orElse("unavailable");
    }
    public static String maxHealthText(LivingEntity entity) {
        return read(entity).map(s -> CombatAmounts.decimalString(s.maxHealth())).orElse("unavailable");
    }
    public static BigDecimal shield(LivingEntity entity) {
        return read(entity).map(ExactCombatState::shield).orElse(BigDecimal.ZERO);
    }
    public static String shieldText(LivingEntity entity) { return CombatAmounts.decimalString(shield(entity)); }
    public static String maxShieldText(LivingEntity entity) {
        return read(entity).map(s -> CombatAmounts.decimalString(s.maxShield())).orElse("unavailable");
    }
    /** Composite read-only defense activity; the setter below remains exclusively the bracer switch. */
    public static boolean shieldActive(LivingEntity entity) {
        if (!enabled(entity)) return false;
        return EsperShields.holding(entity) || CityNpcPower.holding(entity) || abilityShieldActive(entity);
    }
    /** Explicit paid defense activity blocks recovery; the passive category shield does not. */
    private static boolean abilityShieldActive(LivingEntity entity) {
        if (autonomousVectorCircleBenefitsActive(entity) && entity instanceof ServerPlayer player && AutonomousVectorCircle.protecting(player)) return true;
        if (circulationReliefBenefitsActive(entity) && entity instanceof ServerPlayer player && CirculationRelief.protecting(player)) return true;
        if (inertialAnchorBenefitsActive(entity) && entity instanceof ServerPlayer player && InertialAnchor.active(player)) return true;
        if (vectorParryBenefitsActive(entity) && entity instanceof ServerPlayer player && VectorParry.active(player)) return true;
        if (zeroGapBenefitsActive(entity) && entity instanceof ServerPlayer player && ZeroGapArmor.active(player)) return true;
        if (curtainBenefitsActive(entity) && entity instanceof ServerPlayer player && DissipationCurtain.active(player)) return true;
        if (scatterPlateBenefitsActive(entity) && entity instanceof ServerPlayer player && ScatterPlates.active(player)) return true;
        if (thermalShellBenefitsActive(entity) && entity instanceof ServerPlayer player && ThermalShell.active(player)) return true;
        return electromasterBenefitsActive(entity) && (tag(entity).getBoolean("bracerActive")
                || entity instanceof ServerPlayer player && (GroundingReturn.active(player) || PotentialBuffer.active(player)
                    || MagneticDomain.active(player)));
    }
    public static void shieldActive(LivingEntity entity, boolean active) {
        if (serverThread(entity) && enabled(entity)) tag(entity).putBoolean("bracerActive", active);
    }

    /** Category-shield trait cost: spends {@code fraction} of the maximum shield, all or nothing; counts as a shield spend. */
    public static boolean spendShieldFraction(LivingEntity entity, BigDecimal fraction) {
        if (!serverThread(entity)) return false;
        var state = read(entity);
        if (state.isEmpty()) return false;
        BigDecimal cost = state.get().maxShield().multiply(fraction);
        if (cost.signum() <= 0 || cost.compareTo(state.get().shield()) > 0 || state.get().isDead()) return false;
        var next = state.get().spendShield(cost);
        tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
        write(entity, next);
        synchronizeAdapter(entity, next);
        return true;
    }

    /** Category-shield trait gain (electromaster lightning): restores {@code fraction} of the maximum shield, saturating. */
    public static void rechargeShieldFraction(LivingEntity entity, BigDecimal fraction) {
        if (!serverThread(entity)) return;
        var state = read(entity);
        if (state.isEmpty() || state.get().isDead()) return;
        var next = state.get().regenerateShield(state.get().maxShield().multiply(fraction));
        write(entity, next);
        synchronizeAdapter(entity, next);
    }

    /** Category swaps suspend EM benefits while retaining exact HP accounting and all saved balances. */
    private static boolean electromasterBenefitsActive(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return true;
        var state = AbilityStorage.get(player);
        return HighPowerCategories.isElectromaster(state.category) && state.level >= 1 && state.level <= 5;
    }

    /** Learned NEW412 admits only existing base S; it never admits another category's reserve. */
    static boolean vectorParryBenefitsActive(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.VECMANIP.equals(ability.category) && ability.level >= 1 && ability.level <= 5
                && ability.learned(HighPowerSkills.VECTOR_PARRY)
                && SkillCatalog.enabled(HighPowerCategories.VECMANIP, HighPowerSkills.VECTOR_PARRY);
    }

    static boolean inertialAnchorBenefitsActive(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.VECMANIP.equals(ability.category) && ability.level >= 2 && ability.level <= 5
                && ability.learned(HighPowerSkills.INERTIAL_ANCHOR) && ability.learned(HighPowerSkills.VECTOR_PARRY)
                && SkillCatalog.enabled(HighPowerCategories.VECMANIP, HighPowerSkills.INERTIAL_ANCHOR);
    }

    static boolean circulationReliefBenefitsActive(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.VECMANIP.equals(ability.category) && ability.level >= 3 && ability.level <= 5
                && ability.learned(HighPowerSkills.CIRCULATION_RELIEF) && ability.learned(HighPowerSkills.INERTIAL_ANCHOR)
                && ability.learned(HighPowerSkills.VECTOR_PARRY)
                && SkillCatalog.enabled(HighPowerCategories.VECMANIP, HighPowerSkills.CIRCULATION_RELIEF);
    }

    /** NEW442 grants no generic damage absorption: only its sealed native arrow transaction may spend. */
    static boolean multiAxisReflectionBenefitsActive(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.VECMANIP.equals(ability.category) && ability.level >= 4 && ability.level <= 5
                && ability.learned(HighPowerSkills.MULTI_AXIS_REFLECTION) && ability.learned(HighPowerSkills.VECTOR_CAGE)
                && SkillCatalog.enabled(HighPowerCategories.VECMANIP, HighPowerSkills.MULTI_AXIS_REFLECTION);
    }

    /** NEW452 alone admits both saved reserve tranches; learning never creates balances. */
    static boolean autonomousVectorCircleBenefitsActive(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.VECMANIP.equals(ability.category) && ability.level == 5
                && HighPowerResources.effectiveLevel(player) == 5 && HighPowerGrowth.available(player)
                && ability.learned(HighPowerSkills.AUTONOMOUS_VECTOR_CIRCLE)
                && ability.learned(HighPowerSkills.MULTI_AXIS_REFLECTION)
                && SkillCatalog.enabled(HighPowerCategories.VECMANIP, HighPowerSkills.AUTONOMOUS_VECTOR_CIRCLE);
    }

    public static BigDecimal multiAxisReflectionAvailable(ServerPlayer player) {
        if (!multiAxisReflectionBenefitsActive(player)) return BigDecimal.ZERO;
        return read(player).flatMap(base -> MagneticDomainReserve.savedShares(player, base)
                .map(shares -> base.shield().add(shares.lower()))).orElse(BigDecimal.ZERO);
    }

    public static BigDecimal multiAxisReflectionCapacity(ServerPlayer player) {
        if (!multiAxisReflectionBenefitsActive(player)) return BigDecimal.ZERO;
        return read(player).filter(base -> MagneticDomainReserve.savedShares(player, base).isPresent())
                .map(base -> base.maxShield().multiply(new BigDecimal("1.5"))).orElse(BigDecimal.ZERO);
    }

    /** Exact one-shot native arrow payment. All observers follow quota, denial mark and physical mutation. */
    static boolean commitMultiAxisReflection(MultiAxisReflectionArrowBridge.Payment payment) {
        if (payment == null) return false;
        ServerPlayer player = payment.player();
        if (player == null || !serverThread(player) || !multiAxisReflectionBenefitsActive(player) || !payment.valid()) return false;
        var base = read(player);
        if (base.isEmpty()) return false;
        var reserve = MagneticDomainReserve.savedShares(player, base.get());
        if (reserve.isEmpty()) return false;
        BigDecimal amount = payment.amount();
        if (amount == null || amount.signum() <= 0
                || amount.compareTo(MultiAxisReflection.capturedCeiling(payment.capture())) > 0
                || amount.compareTo(base.get().shield().add(reserve.get().lower())) > 0) return false;
        var paid = cn.academy.port.highpower.core.DissipationCurtainShieldRules.absorb(base.get(), reserve.get(), amount, false);
        if (paid.remaining().signum() != 0 || paid.spent().compareTo(amount) != 0 || !payment.valid()) return false;
        Runnable notifications = publishResources(player, paid.state(), null, paid.shares(), null);
        // From this point an uncertain callback must not replay payment or run the original impact as well.
        try { payment.commitAfterLedger(); }
        finally { tag(player).putLong("lastShieldSpend", player.level().getGameTime()); }
        try { notifications.run(); }
        catch (RuntimeException failure) { org.slf4j.LoggerFactory.getLogger("academy.multi_axis_reflection").warn("NEW442 observer failed after committed reflection", failure); }
        payment.finished();
        return true;
    }

    /** Only learned NEW212 enables MD use of the existing base shield and its existing recovery budget. */
    private static boolean scatterPlateBenefitsActive(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isMeltdowner(ability.category) && ability.level >= 1 && ability.level <= 5
                && ability.learned(HighPowerSkills.SCATTER_PLATES)
                && SkillCatalog.enabled(HighPowerCategories.MELTDOWNER, HighPowerSkills.SCATTER_PLATES);
    }

    /** NEW222 shares the existing MD base shield and the same single recovery budget. */
    private static boolean thermalShellBenefitsActive(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isMeltdowner(ability.category) && ability.level >= 2 && ability.level <= 5
                && ability.learned(HighPowerSkills.THERMAL_SHELL)
                && SkillCatalog.enabled(HighPowerCategories.MELTDOWNER, HighPowerSkills.THERMAL_SHELL);
    }

    /** NEW342 permits TP recovery of existing base S only; it enables no old defense or reserve. */
    static boolean emergencyReturnBenefitsActive(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isTeleporter(ability.category) && ability.level >= 4 && ability.level <= 5
                && ability.learned("emergency_return")
                && SkillCatalog.enabled(HighPowerCategories.TELEPORTER, "emergency_return")
                && ability.activated && ability.overloadFine && !ability.interfering
                && emergencyReturnSavedResourcesValid(player)
                && HighPowerResources.effectiveLevel(player) >= 4 && HighPowerResources.ready(player)
                && unsupportedReason(player).isEmpty();
    }

    /** Enhanced TP use must not enter the ordinary damage writer's corrupt-optional fallback. */
    private static boolean emergencyReturnSavedResourcesValid(ServerPlayer player) {
        var base = read(player, false);
        if (base.isEmpty() || base.get().isDead()) return false;
        CompoundTag saved = tag(player);
        if (!saved.contains("lastShieldSpend", Tag.TAG_LONG) || !saved.contains("lastShieldTick", Tag.TAG_LONG)) return false;
        if (ZeroGapArmorStorage.hasMetadata(player) && ZeroGapArmorStorage.read(player).isEmpty()) return false;
        if ((saved.contains(ScatterPlateStorage.KEY) || saved.contains(ScatterPlateStorage.INITIALIZED_KEY)
                || saved.contains(ScatterPlateStorage.INVALIDATED_KEY)) && ScatterPlateStorage.read(player).isEmpty()) return false;
        return MagneticDomainReserve.savedShares(player, base.get()).isPresent();
    }

    static boolean zeroGapBenefitsActive(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isMeltdowner(ability.category) && ability.level == 5
                && ability.learned("zero_gap_armor") && ability.learned(HighPowerSkills.DISSIPATION_CURTAIN)
                && ability.learned(HighPowerSkills.SCATTER_PLATES)
                && SkillCatalog.enabled(HighPowerCategories.MELTDOWNER, "zero_gap_armor")
                && ZeroGapArmorStorage.read(player).isPresent();
    }
    public static BigDecimal zeroGapReserve(ServerPlayer player) {
        return zeroGapBenefitsActive(player) ? ZeroGapArmorStorage.read(player).map(a -> a.reserves().total()).orElse(BigDecimal.ZERO) : BigDecimal.ZERO;
    }
    public static BigDecimal zeroGapReserveCapacity(ServerPlayer player) {
        return zeroGapBenefitsActive(player) ? read(player).map(ExactCombatState::maxShield).orElse(BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    /** NEW242 exposes only the first persisted half-reserve to its MD owner. */
    static boolean curtainBenefitsActive(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isMeltdowner(ability.category) && ability.level >= 4 && ability.level <= 5
                && ability.learned(HighPowerSkills.DISSIPATION_CURTAIN) && ability.learned(HighPowerSkills.SCATTER_PLATES)
                && SkillCatalog.enabled(HighPowerCategories.MELTDOWNER, HighPowerSkills.DISSIPATION_CURTAIN);
    }
    public static BigDecimal curtainReserve(ServerPlayer player) {
        if (!curtainBenefitsActive(player)) return BigDecimal.ZERO;
        return read(player).flatMap(base -> MagneticDomainReserve.savedShares(player, base))
                .map(shares -> shares.lower()).orElse(BigDecimal.ZERO);
    }
    public static BigDecimal curtainReserveCapacity(ServerPlayer player) {
        if (!curtainBenefitsActive(player)) return BigDecimal.ZERO;
        return read(player).filter(base -> MagneticDomainReserve.savedShares(player, base).isPresent())
                .map(base -> base.maxShield().divide(BigDecimal.TWO)).orElse(BigDecimal.ZERO);
    }

    /** Called only after an eligible directional projectile deflection has been identified. */
    public static boolean spendShield(LivingEntity entity, BigDecimal amount) {
        if (!serverThread(entity) || !electromasterBenefitsActive(entity)) return false;
        var state = read(entity);
        if (state.isEmpty()) return false;
        try {
            write(entity, state.get().spendShield(amount));
            if (amount.signum() > 0) tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
            return true;
        } catch (IllegalArgumentException | IllegalStateException invalid) { return false; }
    }

    /** Closed NEW152 arrow charge; no callbacks between either tranche's validation and debit. */
    static boolean spendDomainRefraction(ServerPlayer player) {
        if (!serverThread(player) || !player.isAlive() || !MagneticDomainReserve.available(player)) return false;
        var optional = read(player);
        if (optional.isEmpty()) return false;
        ExactCombatState before = optional.get();
        var reserve = MagneticDomainReserve.saved(player, before);
        if (reserve.isEmpty()) return false;
        var paid = MagneticDomainShieldRules.spend(before, reserve.get(), before.maxShield().divide(BigDecimal.valueOf(100)));
        if (paid == null) return false;
        var shares = MagneticDomainReserve.savedShares(player, before).orElseThrow().withTotal(paid.state(), paid.reserve());
        commitResources(player, paid.state(), null, shares);
        tag(player).putLong("lastShieldSpend", player.level().getGameTime());
        return true;
    }

    /** A sealed controller capability pays fixed 10%S from base alone, never retained reserves. */
    static EmergencyReturnBudget.Result spendEmergencyReturn(ServerPlayer player, EmergencyReturnBudget.Payment payment) {
        if (player == null || payment == null || !serverThread(player) || !emergencyReturnBenefitsActive(player))
            return EmergencyReturnBudget.Result.NOT_PAID;
        var optional = read(player);
        if (optional.isEmpty()) return EmergencyReturnBudget.Result.NOT_PAID;
        ExactCombatState before = optional.get(), after = EmergencyReturnRules.automaticSpend(before);
        if (after == null) return EmergencyReturnBudget.Result.NOT_PAID;
        try {
            if (!payment.claim(player, before)) return EmergencyReturnBudget.Result.NOT_PAID;
            // Both timing and controller fences precede commitResources: its terminal observer
            // notifications may throw after publishing all parents/children. Never retry/refund.
            tag(player).putLong("lastShieldSpend", player.level().getGameTime());
            commitResources(player, after, null, null);
            return EmergencyReturnBudget.Result.PAID;
        } catch (RuntimeException uncertain) {
            if (!payment.attempted()) return EmergencyReturnBudget.Result.NOT_PAID;
            LOGGER.warn("Emergency return resource commit may have completed for {}; the claimed payment is terminal",
                    player.getUUID(), uncertain);
            return EmergencyReturnBudget.Result.PAID_OR_UNCERTAIN;
        }
    }

    /** Provisional bracer recovery: inactive for 60 ticks after spending, then 1% capacity/tick. */
    public static void tickShield(LivingEntity entity) {
        if (!serverThread(entity) || !(EsperShields.eligible(entity) || CityNpcPower.active(entity) && CityNpcPower.baseShieldEnabled() || electromasterBenefitsActive(entity) || scatterPlateBenefitsActive(entity)
                || thermalShellBenefitsActive(entity) || curtainBenefitsActive(entity) || zeroGapBenefitsActive(entity)
                || emergencyReturnBenefitsActive(entity) || vectorParryBenefitsActive(entity) || inertialAnchorBenefitsActive(entity) || circulationReliefBenefitsActive(entity) || multiAxisReflectionBenefitsActive(entity) || autonomousVectorCircleBenefitsActive(entity))) return;
        var optional = read(entity);
        if (optional.isEmpty()) return;
        long now = entity.level().getGameTime();
        CompoundTag tag = tag(entity);
        if (tag.getLong("lastShieldTick") == now) return;
        tag.putLong("lastShieldTick", now);
        if (abilityShieldActive(entity) || entity instanceof ServerPlayer player
                && (electromasterBenefitsActive(player) && MagneticDomain.blocksRecovery(player)
                    || scatterPlateBenefitsActive(player) && ScatterPlates.blocksRecovery(player)
                    || thermalShellBenefitsActive(player) && ThermalShell.blocksRecovery(player)
                    || curtainBenefitsActive(player) && DissipationCurtain.blocksRecovery(player)
                    || zeroGapBenefitsActive(player) && ZeroGapArmor.blocksRecovery(player)
                    || vectorParryBenefitsActive(player) && VectorParry.blocksRecovery(player)
                    || inertialAnchorBenefitsActive(player) && InertialAnchor.blocksRecovery(player)
                    || circulationReliefBenefitsActive(player) && CirculationRelief.blocksRecovery(player)
                    || multiAxisReflectionBenefitsActive(player) && MultiAxisReflection.blocksRecovery(player)
                    || autonomousVectorCircleBenefitsActive(player) && AutonomousVectorCircle.blocksRecovery(player))
                || now - tag.getLong("lastShieldSpend") < 60) return;
        ExactCombatState state = optional.get();
        // One unchanged 20%S/second budget; base, then eligible lower, then domain/armor upper.
        if (entity instanceof ServerPlayer player) {
            boolean domain = electromasterBenefitsActive(player) && MagneticDomainReserve.available(player);
            boolean curtain = curtainBenefitsActive(player), armor = zeroGapBenefitsActive(player);
            boolean reflection = multiAxisReflectionBenefitsActive(player);
            boolean circle = autonomousVectorCircleBenefitsActive(player);
            if (domain || curtain || armor || reflection || circle) {
                var recovery = MagneticDomainReserve.recoverShared(player, state,
                        state.maxShield().divide(BigDecimal.valueOf(100)), domain || curtain || armor || reflection || circle, domain || armor || circle);
                if (recovery != null) {
                    commitResources(entity, recovery.state(), null, recovery.shares());
                    return;
                }
            }
        }
        if (state.shield().compareTo(state.maxShield()) < 0) {
            write(entity, state.regenerateShield(state.maxShield().divide(BigDecimal.valueOf(100))));
        }
    }

    /** Exact transaction for opted-in targets; bounded ordinary damage for listed vanilla targets. */
    public static boolean attack(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage) {
        return attackWithOrigin(player, skill, target, rawDamage, null);
    }

    /** Only a sealed, one-use server capability admits these vector attacks; the legacy generic entrance is unchanged. */
    public static boolean attackEmission(AbilityEmission permit) {
        if(permit==null || !permit.claim() || !permit.valid()) return false;
        return attackWithOrigin(permit.player(),permit.skill(),permit.target(),permit.damage(),null,permit::valid,
                false,false,false,false,false,false,
                null,null,null,null,null,null,null,null,null,null,null,null,permit);
    }

    /**
     * NEW211's only damage entrance. Only its live server controller can construct a
     * single-use permit; the amount, target and hand origin never come from callers
     * or packets. Each beam retains its own native shield and hurt-cooldown decision.
     */
    public static boolean attackDualPoint(DualPoint.EmissionPermit permit) {
        if (permit == null || !permit.consume() || !permit.valid()) return false;
        ServerPlayer player = permit.player();
        java.util.function.BooleanSupplier guard = () -> permit.valid() && dualPointBenefitsActive(player);
        if (!validAttackGuard(guard)) return false;
        return attackWithOrigin(player, "meltdowner.dual_point", permit.target(), permit.damage(), permit.origin(), guard, true, false, false, false, permit.endpoint());
    }

    /**
     * NEW222's sole outgoing entrance. An opaque, single-use controller permit fixes
     * the actual pulse origin, victim and share of the committed absorption budget.
     * PLAYER_ATTACK preserves native shields; the distinct type cannot claim NEW211
     * beam contact or recursively qualify as ordinary NEW222 melee intake.
     */
    public static boolean attackThermalShell(ThermalShell.EmissionPermit permit) {
        if (permit == null || !permit.claim() || !permit.valid()) return false;
        ServerPlayer player = permit.player();
        java.util.function.BooleanSupplier guard = () -> permit.valid() && thermalShellBenefitsActive(player)
                && HighPowerResources.ready(player);
        if (!validAttackGuard(guard)) return false;
        return attackWithOrigin(player, "meltdowner.thermal_shell", permit.target(), permit.damage(), permit.origin(),
                guard, false, true, false, false, permit.endpoint());
    }

    /** NEW221 only: fixed, revalidated reflected path with the actual final contact as damage origin. */
    public static boolean attackFoldedMirrorArray(FoldedMirrorArray.EmissionPermit permit) {
        if (permit == null || !permit.consume() || !permit.valid()) return false;
        ServerPlayer player = permit.player();
        java.util.function.BooleanSupplier guard = () -> permit.valid() && foldedMirrorBenefitsActive(player);
        if (!validAttackGuard(guard)) return false;
        return attackWithOrigin(player, "meltdowner.folded_mirror_array", permit.target(), permit.damage(),
                permit.finalOrigin(), guard, false, false, true, false, permit.endpoint());
    }
    /** NEW253 alone: the whole relay route carries one unpaid terminal allocation into native hurt. */
    public static boolean attackRemoteScatterRelay(RemoteScatterRelay.EmissionPermit permit) {
        if (permit == null || permit.player() == null || !serverThread(permit.player())) return false;
        ServerPlayer player = permit.player();
        if (REMOTE_SCATTER_RELAY_ATTACKS.putIfAbsent(player, permit) != null) return false;
        try {
            if (!permit.consume()) return false;
            java.util.function.BooleanSupplier guard = () -> REMOTE_SCATTER_RELAY_ATTACKS.get(player) == permit
                    && remoteScatterRelayBenefitsActive(player) && permit.valid()
                    && REMOTE_SCATTER_RELAY_ATTACKS.get(player) == permit;
            if (!validAttackGuard(guard) || !AnchorDamagePostProof.available()) return false;
            return attackWithOrigin(player, "meltdowner.remote_scatter_relay", permit.target(), permit.damage(),
                    permit.finalOrigin(), guard, false, false, false, false, false, false, permit.endpoint(),
                    null, null, null, null, null, null, null, null, null, null, permit);
        } finally { REMOTE_SCATTER_RELAY_ATTACKS.remove(player, permit); }
    }

    /** NEW231 only: one immutable allocated share at an actual swept-angle native ray contact. */
    public static boolean attackDisintegrationSweep(DisintegrationSweep.EmissionPermit permit) {
        if (permit == null || !permit.consume() || !permit.valid()) return false;
        ServerPlayer player = permit.player();
        java.util.function.BooleanSupplier guard = () -> permit.valid() && disintegrationSweepBenefitsActive(player);
        if (!validAttackGuard(guard)) return false;
        return attackWithOrigin(player, "meltdowner.disintegration_sweep", permit.target(), permit.damage(),
                permit.finalOrigin(), guard, false, false, false, true, permit.endpoint());
    }
    /** NEW241 only: one frozen share at a real fixed corridor emitter-to-body contact. */
    public static boolean attackParticleLaminate(ParticleLaminate.EmissionPermit permit) {
        if (permit == null || !permit.consume() || !permit.valid()) return false;
        ServerPlayer player = permit.player();
        java.util.function.BooleanSupplier guard = () -> permit.valid() && particleLaminateBenefitsActive(player);
        if (!validAttackGuard(guard)) return false;
        return attackWithOrigin(player, "meltdowner.particle_laminate", permit.target(), permit.damage(),
                permit.finalOrigin(), guard, false, false, false, false, true, permit.endpoint());
    }
    /** NEW251 only: a frozen share from one actual deployed core, never a legacy barrage. */
    public static boolean attackMulticoreCanopy(MulticoreCanopy.EmissionPermit permit) {
        if (permit == null || !permit.consume() || !permit.valid()) return false;
        ServerPlayer player = permit.player();
        java.util.function.BooleanSupplier guard = () -> permit.valid() && multicoreCanopyBenefitsActive(player);
        if (!validAttackGuard(guard)) return false;
        return attackWithOrigin(player, "meltdowner.multicore_canopy", permit.target(), permit.damage(),
                permit.finalOrigin(), guard, false, false, false, false, false, true, permit.endpoint());
    }

    /** NEW311 only: one claimed native needle contact carries the frozen whole-cast damage. */
    public static boolean attackDisplacedProjection(DisplacedProjection.EmissionPermit permit) {
        if (permit == null || !permit.consume()) return false;
        java.util.function.BooleanSupplier guard = () -> validDisplacedProjection(permit);
        if (!validAttackGuard(guard)) return false;
        return attackWithOrigin(permit.player(), "teleporter.displaced_projection", permit.target(), permit.damage(),
                permit.origin(), guard, false, false, false, false, false, false, null, permit, null);
    }

    /** NEW321 only: one claimed native cross-needle contact carries its frozen allocated share. */
    public static boolean attackCrossSubstitution(CrossSubstitution.EmissionPermit permit) {
        if (permit == null || !permit.consume()) return false;
        java.util.function.BooleanSupplier guard = () -> validCrossSubstitution(permit);
        if (!validAttackGuard(guard)) return false;
        return attackWithOrigin(permit.player(), "teleporter.cross_substitution", permit.target(), permit.damage(),
                permit.origin(), guard, false, false, false, false, false, false, null, null, permit);
    }

    private static void tpContactDiagnostic(String skill,String stage,boolean accepted) {
        if(Boolean.getBoolean("academy.qa.tpContactTrace")) com.mojang.logging.LogUtils.getLogger().info("QA_TP_CONTACT skill={} stage={} accepted={}",skill,stage,accepted);
    }

    /** NEW341 only: one claimed corridor needle contact carries its frozen allocated share. */
    public static boolean attackSpatialPincerCorridor(SpatialPincerCorridor.EmissionPermit permit) {
        if (permit == null || !permit.consume()) return false;
        java.util.function.BooleanSupplier guard = () -> validSpatialPincerCorridor(permit);
        if (!validAttackGuard(guard)) { tpContactDiagnostic("spatial_pincer_corridor","initial_guard",false); return false; }
        return attackWithOrigin(permit.player(), "teleporter.spatial_pincer_corridor", permit.target(), permit.damage(),
                permit.origin(), guard, false, false, false, false, false, false, null, null, null, null, permit);
    }

    /** NEW351 only: exactly one actual first-collision lease carries its preallocated whole-round share. */
    public static boolean attackMultidimensionalLandingArray(MultidimensionalLandingArray.ClosedEmissionPermit permit) {
        if (permit == null || !permit.consume()) return false;
        java.util.function.BooleanSupplier guard = () -> validMultidimensionalLandingArray(permit);
        if (!validAttackGuard(guard)) { tpContactDiagnostic("multidimensional_landing_array","initial_guard",false); return false; }
        return attackWithOrigin(permit.player(), "teleporter.multidimensional_landing_array", permit.target(), permit.damage(),
                permit.origin(), guard, false, false, false, false, false, false, null, null, null, null, null, permit);
    }

    /**
     * NEW411 only: a single-use controller allocation carries the actual strike origin.
     * Native PLAYER_ATTACK retains armor, directional shields and hurt cooldowns;
     * this closed vector identity is neither a projectile nor a defense receipt.
     */
    public static boolean attackDeflectionStrike(DeflectionStrike.EmissionPermit permit) {
        if (permit == null || permit.player() == null || !serverThread(permit.player()) || !permit.consume()) return false;
        ServerPlayer player = permit.player();
        if (DEFLECTION_ATTACKS.putIfAbsent(player, permit) != null) return false;
        try {
            java.util.function.BooleanSupplier guard = () -> validDeflectionStrike(permit);
            if (!validAttackGuard(guard)) return false;
            return attackWithOrigin(player, "vecmanip.deflection_strike", permit.target(), permit.damage(),
                    permit.origin(), guard, false, false, false, false, false, false,
                    null, null, null, null, null, null, permit);
        } finally { DEFLECTION_ATTACKS.remove(player, permit); }
    }

    private static boolean deflectionStrikeBenefitsActive(ServerPlayer player) {
        if (player == null || !HighPowerResources.ready(player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isVector(ability.category) && ability.learned(HighPowerSkills.DEFLECTION_STRIKE)
                && SkillCatalog.enabled(HighPowerCategories.VECMANIP, HighPowerSkills.DEFLECTION_STRIKE);
    }

    private static boolean validDeflectionStrike(DeflectionStrike.EmissionPermit permit) {
        ServerPlayer player = permit.player(); LivingEntity target = permit.target();
        return player != null && target != null && serverThread(player)
                && DEFLECTION_ATTACKS.get(player) == permit && permit.valid()
                && deflectionStrikeBenefitsActive(player) && unsupportedReason(player).isEmpty()
                && player.isAlive() && !player.isRemoved()
                && player.serverLevel().getEntity(player.getUUID()) == player
                && hostileTarget(target)
                && !(target instanceof net.minecraft.world.entity.player.Player) && supported(target)
                && DeflectionStrike.hostile(player, target) && DisplacedProjectionWorld.finite(permit.origin());
    }

    /**
     * NEW421 only: a frozen whole-cast share from the actual incoming blade front.
     * Native PLAYER_ATTACK keeps armor, directional shields and hurt cooldowns;
     * this source has no projectile, planar-beam or player-defense authority.
     */
    public static boolean attackShearWindblade(ShearWindblade.EmissionPermit permit) {
        if (permit == null || permit.player() == null || !serverThread(permit.player())) return false;
        ServerPlayer player = permit.player();
        if (SHEAR_WINDBLADE_ATTACKS.putIfAbsent(player, permit) != null) return false;
        try {
            if (!permit.consume()) return false;
            java.util.function.BooleanSupplier guard = () -> validShearWindblade(permit);
            if (!validAttackGuard(guard)) return false;
            return attackWithOrigin(player, "vecmanip.shear_windblade", permit.target(), permit.damage(),
                    permit.origin(), guard, false, false, false, false, false, false,
                    null, null, null, null, null, null, null, permit);
        } finally { SHEAR_WINDBLADE_ATTACKS.remove(player, permit); }
    }

    private static boolean shearWindbladeBenefitsActive(ServerPlayer player) {
        if (player == null || !HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 2) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isVector(ability.category) && ability.learned(HighPowerSkills.DEFLECTION_STRIKE)
                && ability.learned(HighPowerSkills.SHEAR_WINDBLADE)
                && SkillCatalog.enabled(HighPowerCategories.VECMANIP, HighPowerSkills.SHEAR_WINDBLADE);
    }

    private static boolean validShearWindblade(ShearWindblade.EmissionPermit permit) {
        ServerPlayer player = permit.player(); LivingEntity target = permit.target();
        return player != null && target != null && serverThread(player)
                && SHEAR_WINDBLADE_ATTACKS.get(player) == permit
                && shearWindbladeBenefitsActive(player) && unsupportedReason(player).isEmpty()
                && player.isAlive() && !player.isRemoved()
                && player.serverLevel().getEntity(player.getUUID()) == player
                && hostileTarget(target)
                && !(target instanceof net.minecraft.world.entity.player.Player) && supported(target)
                && ShearWindblade.hostile(player, target) && DisplacedProjectionWorld.finite(permit.origin())
                // Live hostile/permission checks may call out; the controller checks its frozen state last.
                && permit.valid() && SHEAR_WINDBLADE_ATTACKS.get(player) == permit;
    }

    /**
     * NEW423 only: a frozen whole-cast share from the actual owner hand origin.
     * Native PLAYER_ATTACK keeps armor, directional shields and hurt cooldowns;
     * this source has no projectile, planar-beam or player-defense authority.
     */
    public static boolean attackYawPalm(YawPalm.EmissionPermit permit) {
        if (permit == null || permit.player() == null || !serverThread(permit.player())) return false;
        ServerPlayer player = permit.player();
        if (YAW_PALM_ATTACKS.putIfAbsent(player, permit) != null) return false;
        try {
            if (!permit.consume()) return false;
            java.util.function.BooleanSupplier guard = () -> validYawPalm(permit);
            if (!validAttackGuard(guard)) return false;
            return attackWithOrigin(player, "vecmanip.yaw_palm", permit.target(), permit.damage(),
                    permit.origin(), guard, false, false, false, false, false, false,
                    null, null, null, null, null, null, null, null, permit);
        } finally { YAW_PALM_ATTACKS.remove(player, permit); }
    }

    private static boolean yawPalmBenefitsActive(ServerPlayer player) {
        if (player == null || !HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 2) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isVector(ability.category) && ability.learned(HighPowerSkills.BRAKING_TOUCH)
                && ability.learned(HighPowerSkills.YAW_PALM)
                && SkillCatalog.enabled(HighPowerCategories.VECMANIP, HighPowerSkills.YAW_PALM);
    }

    private static boolean validYawPalm(YawPalm.EmissionPermit permit) {
        ServerPlayer player = permit.player(); LivingEntity target = permit.target();
        return player != null && target != null && serverThread(player)
                && YAW_PALM_ATTACKS.get(player) == permit
                && yawPalmBenefitsActive(player) && unsupportedReason(player).isEmpty()
                && player.isAlive() && !player.isRemoved()
                && player.serverLevel().getEntity(player.getUUID()) == player
                && hostileTarget(target)
                && !(target instanceof net.minecraft.world.entity.player.Player) && supported(target)
                && YawPalm.hostile(player, target) && DisplacedProjectionWorld.finite(permit.origin())
                // Live hostile/permission checks may call out; the controller checks its frozen state last.
                && permit.valid() && YAW_PALM_ATTACKS.get(player) == permit;
    }

    /** NEW441 accepts only one retired wall-contact share from its live closed controller. */
    public static boolean attackVectorCage(VectorCage.EmissionPermit permit) {
        if (permit == null || permit.player() == null || !serverThread(permit.player())) return false;
        ServerPlayer player = permit.player();
        if (VECTOR_CAGE_ATTACKS.putIfAbsent(player, permit) != null) return false;
        try {
            if (!permit.consume()) return false;
            java.util.function.BooleanSupplier guard = () -> validVectorCage(permit);
            if (!validAttackGuard(guard)) return false;
            return attackWithOrigin(player, "vecmanip.vector_cage", permit.target(), permit.damage(),
                    permit.origin(), guard, false, false, false, false, false, false,
                    null, null, null, null, null, null, null, null, null, null, permit);
        } finally { VECTOR_CAGE_ATTACKS.remove(player, permit); }
    }

    private static boolean vectorCageBenefitsActive(ServerPlayer player) {
        if (player == null || !HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 4) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isVector(ability.category) && ability.learned(HighPowerSkills.TRAJECTORY_RETURN)
                && ability.learned(HighPowerSkills.VECTOR_CAGE)
                && SkillCatalog.enabled(HighPowerCategories.VECMANIP, HighPowerSkills.TRAJECTORY_RETURN)
                && SkillCatalog.enabled(HighPowerCategories.VECMANIP, HighPowerSkills.VECTOR_CAGE);
    }

    private static boolean validVectorCage(VectorCage.EmissionPermit permit) {
        ServerPlayer player = permit.player(); LivingEntity target = permit.target();
        return player != null && target != null && serverThread(player) && VECTOR_CAGE_ATTACKS.get(player) == permit
                && vectorCageBenefitsActive(player) && unsupportedReason(player).isEmpty()
                && player.isAlive() && !player.isRemoved()
                && player.serverLevel().getEntity(player.getUUID()) == player
                && VectorCageWorld.hostile(player, target) && DisplacedProjectionWorld.finite(permit.origin())
                && DisplacedProjectionWorld.finite(permit.inward())
                && permit.valid() && VECTOR_CAGE_ATTACKS.get(player) == permit;
    }

    /** NEW431's closed empty-snapshot hammer retains native directional melee defenses. */
    public static boolean attackTrajectoryReturnAirHammer(TrajectoryReturn.AirHammerPermit permit) {
        if (permit == null || permit.player() == null || !serverThread(permit.player())) return false;
        ServerPlayer player = permit.player();
        if (TRAJECTORY_RETURN_AIR_ATTACKS.putIfAbsent(player, permit) != null) return false;
        try {
            if (!permit.consume()) return false;
            java.util.function.BooleanSupplier guard = () -> validTrajectoryReturnAirHammer(permit);
            if (!validAttackGuard(guard)) return false;
            return attackWithOrigin(player, "vecmanip.trajectory_return_air", permit.target(), permit.damage(),
                    permit.origin(), guard, false, false, false, false, false, false,
                    null, null, null, null, null, null, null, null, null, permit);
        } finally { TRAJECTORY_RETURN_AIR_ATTACKS.remove(player, permit); }
    }

    private static boolean trajectoryReturnBenefitsActive(ServerPlayer player) {
        if (player == null || !enabled(player) || !HighPowerResources.ready(player)
                || HighPowerResources.effectiveLevel(player) < 3) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isVector(ability.category) && ability.learned(HighPowerSkills.YAW_PALM)
                && ability.learned(HighPowerSkills.TRAJECTORY_RETURN)
                && SkillCatalog.enabled(HighPowerCategories.VECMANIP, HighPowerSkills.YAW_PALM)
                && SkillCatalog.enabled(HighPowerCategories.VECMANIP, HighPowerSkills.TRAJECTORY_RETURN);
    }

    private static boolean validTrajectoryReturnAirHammer(TrajectoryReturn.AirHammerPermit permit) {
        ServerPlayer player = permit.player(); LivingEntity target = permit.target();
        return player != null && target != null && serverThread(player)
                && TRAJECTORY_RETURN_AIR_ATTACKS.get(player) == permit
                && trajectoryReturnBenefitsActive(player) && unsupportedReason(player).isEmpty()
                && player.isAlive() && !player.isRemoved()
                && player.serverLevel().getEntity(player.getUUID()) == player
                && DisplacedProjectionWorld.hostile(player, target)
                && DisplacedProjectionWorld.finite(permit.origin()) && DisplacedProjectionWorld.finite(permit.endpoint())
                && permit.origin().distanceToSqr(permit.endpoint()) <= 40D * 40D
                // The controller rechecks its frozen current line and first body after all live callbacks.
                && permit.valid() && TRAJECTORY_RETURN_AIR_ATTACKS.get(player) == permit;
    }

    /** NEW331 empty-snapshot fallback has distinct custody and the frozen per-shot contact amount. */
    public static boolean attackShortInterceptionFallback(ShortInterception.FallbackPermit permit) {
        if (permit == null || !permit.consume()) return false;
        java.util.function.BooleanSupplier guard = () -> validShortInterceptionFallback(permit);
        if (!validAttackGuard(guard)) return false;
        return attackWithOrigin(permit.player(), "teleporter.short_interception", permit.target(), permit.damage(),
                permit.origin(), guard, false, false, false, false, false, false, null, null, null, permit);
    }

    private static boolean shortInterceptionBenefitsActive(ServerPlayer player) {
        if (player == null || !HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 3) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isTeleporter(ability.category)
                && ability.learned(HighPowerSkills.SAFE_LANDING_PROBE) && ability.learned(HighPowerSkills.DISPLACED_PROJECTION)
                && ability.learned(HighPowerSkills.CROSS_SUBSTITUTION) && ability.learned("short_interception") && SkillCatalog.enabled(HighPowerCategories.TELEPORTER, "short_interception");
    }

    private static boolean displacedProjectionBenefitsActive(ServerPlayer player) {
        if (player == null || !HighPowerResources.ready(player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isTeleporter(ability.category)
                && ability.learned(HighPowerSkills.SAFE_LANDING_PROBE) && ability.learned(HighPowerSkills.DISPLACED_PROJECTION)
                && SkillCatalog.enabled(HighPowerCategories.TELEPORTER, HighPowerSkills.DISPLACED_PROJECTION);
    }

    private static boolean crossSubstitutionBenefitsActive(ServerPlayer player) {
        if (player == null || !HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 2) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isTeleporter(ability.category)
                && ability.learned(HighPowerSkills.SAFE_LANDING_PROBE) && ability.learned(HighPowerSkills.DISPLACED_PROJECTION)
                && ability.learned(HighPowerSkills.CROSS_SUBSTITUTION)
                && SkillCatalog.enabled(HighPowerCategories.TELEPORTER, HighPowerSkills.CROSS_SUBSTITUTION);
    }

    private static boolean spatialPincerCorridorBenefitsActive(ServerPlayer player) {
        if (player == null || !HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 4) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isTeleporter(ability.category)
                && ability.learned(HighPowerSkills.SAFE_LANDING_PROBE) && ability.learned(HighPowerSkills.DISPLACED_PROJECTION)
                && ability.learned(HighPowerSkills.CROSS_SUBSTITUTION) && ability.learned(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.enabled(HighPowerCategories.TELEPORTER, HighPowerSkills.SPATIAL_PINCER_CORRIDOR);
    }

    private static boolean multidimensionalLandingArrayBenefitsActive(ServerPlayer player) {
        if (player == null || !HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 5) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isTeleporter(ability.category) && ability.learned(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && ability.learned(MultidimensionalLandingArray.ID)
                && SkillCatalog.enabled(HighPowerCategories.TELEPORTER, MultidimensionalLandingArray.ID);
    }

    public static boolean supportedMultidimensionalLandingArrayTarget(LivingEntity target) {
        return hostileTarget(target) && !(target instanceof ServerPlayer) && supported(target);
    }

    private static boolean validMultidimensionalLandingArray(MultidimensionalLandingArray.ClosedEmissionPermit permit) {
        ServerPlayer player = permit.player(); LivingEntity target = permit.target(); BorrowedNeedleEntity needle = permit.needle();
        return player != null && target != null && needle != null && serverThread(player) && permit.valid()
                && multidimensionalLandingArrayBenefitsActive(player) && supportedMultidimensionalLandingArrayTarget(target)
                && unsupportedReason(target).isEmpty() && target.isAlive() && !target.isRemoved()
                && !target.isSpectator() && !target.isInvulnerable() && target.level() == player.level()
                && !player.isAlliedTo(target) && !target.isAlliedTo(player)
                && player.serverLevel().getEntity(target.getUUID()) == target && player.serverLevel().getEntity(target.getId()) == target
                && needle.getClass() == BorrowedNeedleEntity.class && needle.getType() == TeleporterNeedleEntities.BORROWED_NEEDLE.get()
                && needle.level() == player.level() && !needle.isRemoved() && needle.isAdmitted() && needle.isLeaseActive()
                && needle.leaseOwner() == player && player.serverLevel().getEntity(needle.getUUID()) == needle
                && player.serverLevel().getEntity(needle.getId()) == needle && !needle.isOnFire()
                && !needle.isPassenger() && !needle.isVehicle() && needle.noPhysics && needle.isNoGravity()
                && DisplacedProjectionWorld.finite(permit.origin()) && DisplacedProjectionWorld.finite(permit.endpoint())
                && permit.endpoint().equals(needle.position());
    }

    /** Mutable projectile properties belong only to live pre-commit checks, never post-death classification. */
    private static boolean validDisplacedProjection(DisplacedProjection.EmissionPermit permit) {
        ServerPlayer player = permit.player();
        LivingEntity target = permit.target();
        DisplacedNeedleEntity needle = permit.needle();
        if (player == null || target == null || needle == null || !serverThread(player)
                || !permit.valid() || !displacedProjectionBenefitsActive(player)
                || !(hostileTarget(target)) || !supported(target)
                || unsupportedReason(target).isPresent() || !target.isAlive() || target.isRemoved()
                || target.isSpectator() || target.isInvulnerable() || target.level() != player.level()
                || player.isAlliedTo(target) || target.isAlliedTo(player)
                || player.serverLevel().getEntity(target.getUUID()) != target
                || player.serverLevel().getEntity(target.getId()) != target
                || needle.getClass() != DisplacedNeedleEntity.class
                || needle.getType() != TeleporterNeedleEntities.DISPLACED_NEEDLE.get()
                || needle.level() != player.level() || needle.isRemoved() || !needle.isAdmitted()
                || needle.getOwner() != player || player.serverLevel().getEntity(needle.getUUID()) != needle
                || player.serverLevel().getEntity(needle.getId()) != needle
                || needle.isOnFire() || needle.isCritArrow() || needle.getPierceLevel() != 0
                || needle.getBaseDamage() != 0 || needle.getWeaponItem() != null
                || needle.isNoPhysics() || needle.noPhysics || needle.isNoGravity() || needle.inGround
                || needle.isPassenger() || needle.isVehicle()
                || !DisplacedNeedleEntity.isPlainNeedle(needle.mediumSnapshot())
                || !DisplacedProjectionWorld.finite(permit.origin()) || !DisplacedProjectionWorld.finite(permit.endpoint())
                || !permit.origin().equals(needle.contactSegmentOrigin())
                || !permit.endpoint().equals(needle.position())) return false;
        return true;
    }

    /** NEW321 retains its own opaque authority and exact entity type, independent of NEW311 custody. */
    private static boolean validCrossSubstitution(CrossSubstitution.EmissionPermit permit) {
        ServerPlayer player = permit.player();
        LivingEntity target = permit.target();
        DisplacedNeedleEntity needle = permit.needle();
        if (player == null || target == null || needle == null || !serverThread(player)
                || !permit.valid() || !crossSubstitutionBenefitsActive(player)
                || !(hostileTarget(target)) || !supported(target)
                || unsupportedReason(target).isPresent() || !target.isAlive() || target.isRemoved()
                || target.isSpectator() || target.isInvulnerable() || target.level() != player.level()
                || player.isAlliedTo(target) || target.isAlliedTo(player)
                || player.serverLevel().getEntity(target.getUUID()) != target
                || player.serverLevel().getEntity(target.getId()) != target
                || needle.getClass() != DisplacedNeedleEntity.class
                || needle.getType() != TeleporterNeedleEntities.CROSS_NEEDLE.get()
                || needle.level() != player.level() || needle.isRemoved() || !needle.isAdmitted()
                || needle.getOwner() != player || player.serverLevel().getEntity(needle.getUUID()) != needle
                || player.serverLevel().getEntity(needle.getId()) != needle
                || needle.isOnFire() || needle.isCritArrow() || needle.getPierceLevel() != 0
                || needle.getBaseDamage() != 0 || needle.getWeaponItem() != null
                || needle.isNoPhysics() || needle.noPhysics || needle.isNoGravity() || needle.inGround
                || needle.isPassenger() || needle.isVehicle()
                || !DisplacedNeedleEntity.isPlainNeedle(needle.mediumSnapshot())
                || !DisplacedProjectionWorld.finite(permit.origin()) || !DisplacedProjectionWorld.finite(permit.endpoint())
                || !permit.origin().equals(needle.contactSegmentOrigin())
                || !permit.endpoint().equals(needle.position())) return false;
        return true;
    }

    /** NEW341 revalidates live native custody; post-death classification uses only immutable source identity. */
    private static boolean validSpatialPincerCorridor(SpatialPincerCorridor.EmissionPermit permit) {
        ServerPlayer player = permit.player();
        LivingEntity target = permit.target();
        DisplacedNeedleEntity needle = permit.needle();
        if (player == null || target == null || needle == null || !serverThread(player)
                || !permit.valid() || !spatialPincerCorridorBenefitsActive(player)
                || !(hostileTarget(target)) || !supported(target)
                || unsupportedReason(target).isPresent() || !target.isAlive() || target.isRemoved()
                || target.isSpectator() || target.isInvulnerable() || target.level() != player.level()
                || player.isAlliedTo(target) || target.isAlliedTo(player)
                || player.serverLevel().getEntity(target.getUUID()) != target
                || player.serverLevel().getEntity(target.getId()) != target
                || needle.getClass() != DisplacedNeedleEntity.class
                || needle.getType() != TeleporterNeedleEntities.CORRIDOR_NEEDLE.get()
                || needle.level() != player.level() || needle.isRemoved() || !needle.isAdmitted()
                || needle.getOwner() != player || player.serverLevel().getEntity(needle.getUUID()) != needle
                || player.serverLevel().getEntity(needle.getId()) != needle
                || needle.isOnFire() || needle.isCritArrow() || needle.getPierceLevel() != 0
                || needle.getBaseDamage() != 0 || needle.getWeaponItem() != null
                || needle.isNoPhysics() || needle.noPhysics || needle.isNoGravity() || needle.inGround
                || needle.isPassenger() || needle.isVehicle()
                || !DisplacedNeedleEntity.isPlainNeedle(needle.mediumSnapshot())
                || !DisplacedProjectionWorld.finite(permit.origin()) || !DisplacedProjectionWorld.finite(permit.endpoint())
                || !permit.origin().equals(needle.contactSegmentOrigin())
                || !permit.endpoint().equals(needle.position())) return false;
        return true;
    }

    /** Native fallback validates the same ordinary needle mechanics under separate NEW331 custody. */
    private static boolean validShortInterceptionFallback(ShortInterception.FallbackPermit permit) {
        ServerPlayer player = permit.player();
        LivingEntity target = permit.target();
        DisplacedNeedleEntity needle = permit.needle();
        if (player == null || target == null || needle == null || !serverThread(player)
                || !permit.valid() || !shortInterceptionBenefitsActive(player)
                || !(hostileTarget(target)) || !supported(target)
                || unsupportedReason(target).isPresent() || !target.isAlive() || target.isRemoved()
                || target.isSpectator() || target.isInvulnerable() || target.level() != player.level()
                || player.isAlliedTo(target) || target.isAlliedTo(player)
                || player.serverLevel().getEntity(target.getUUID()) != target
                || player.serverLevel().getEntity(target.getId()) != target
                || needle.getClass() != DisplacedNeedleEntity.class
                || needle.getType() != TeleporterNeedleEntities.INTERCEPTION_NEEDLE.get()
                || needle.level() != player.level() || needle.isRemoved() || !needle.isAdmitted()
                || needle.getOwner() != player || player.serverLevel().getEntity(needle.getUUID()) != needle
                || player.serverLevel().getEntity(needle.getId()) != needle
                || needle.isOnFire() || needle.isCritArrow() || needle.getPierceLevel() != 0
                || needle.getBaseDamage() != 0 || needle.getWeaponItem() != null
                || needle.isNoPhysics() || needle.noPhysics || needle.isNoGravity() || needle.inGround
                || needle.isPassenger() || needle.isVehicle()
                || !DisplacedNeedleEntity.isPlainNeedle(needle.mediumSnapshot())
                || !DisplacedProjectionWorld.finite(permit.origin()) || !DisplacedProjectionWorld.finite(permit.endpoint())
                || !permit.origin().equals(needle.contactSegmentOrigin())
                || !permit.endpoint().equals(needle.position())) return false;
        return true;
    }

    public static boolean supportedMulticoreCanopyTarget(LivingEntity target) {
        return target instanceof net.minecraft.world.entity.monster.Enemy && !(target instanceof ServerPlayer)
                && supported(target);
    }
    private static boolean multicoreCanopyBenefitsActive(ServerPlayer player) {
        if (!HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 5) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isMeltdowner(ability.category)
                && ability.learned(HighPowerSkills.PARTICLE_LAMINATE) && ability.learned(HighPowerSkills.MULTICORE_CANOPY)
                && SkillCatalog.enabled(HighPowerCategories.MELTDOWNER, HighPowerSkills.MULTICORE_CANOPY);
    }

    private static boolean particleLaminateBenefitsActive(ServerPlayer player) {
        if (!HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 4) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isMeltdowner(ability.category)
                && ability.learned(HighPowerSkills.DISINTEGRATION_SWEEP) && ability.learned(HighPowerSkills.PARTICLE_LAMINATE)
                && SkillCatalog.enabled(HighPowerCategories.MELTDOWNER, HighPowerSkills.PARTICLE_LAMINATE);
    }

    private static boolean disintegrationSweepBenefitsActive(ServerPlayer player) {
        if (!HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 3) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isMeltdowner(ability.category) && ability.learned(HighPowerSkills.DUAL_POINT)
                && ability.learned(HighPowerSkills.FOLDED_MIRROR_ARRAY) && ability.learned("disintegration_sweep")
                && SkillCatalog.enabled(HighPowerCategories.MELTDOWNER, "disintegration_sweep");
    }

    private static boolean remoteScatterRelayBenefitsActive(ServerPlayer player) {
        if (!HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 5) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isMeltdowner(ability.category)
                && ability.learned(HighPowerSkills.FOLDED_MIRROR_ARRAY) && ability.learned(RemoteScatterRelay.ID)
                && SkillCatalog.enabled(HighPowerCategories.MELTDOWNER, RemoteScatterRelay.ID);
    }

    private static boolean foldedMirrorBenefitsActive(ServerPlayer player) {
        if (!HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 2) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isMeltdowner(ability.category) && ability.learned(HighPowerSkills.DUAL_POINT)
                && ability.learned(HighPowerSkills.FOLDED_MIRROR_ARRAY)
                && SkillCatalog.enabled(HighPowerCategories.MELTDOWNER, HighPowerSkills.FOLDED_MIRROR_ARRAY);
    }

    private static boolean dualPointBenefitsActive(ServerPlayer player) {
        if (!HighPowerResources.ready(player)) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerCategories.isMeltdowner(ability.category) && ability.learned(HighPowerSkills.DUAL_POINT)
                && SkillCatalog.enabled(HighPowerCategories.MELTDOWNER, HighPowerSkills.DUAL_POINT);
    }

    /** NEW131 only: the server controller supplies its actual incoming segment, never a client position/type. */
    public static boolean attackIronSandLance(ServerPlayer player, LivingEntity target, BigDecimal rawDamage,
                                               net.minecraft.world.phys.Vec3 actualSegmentOrigin) {
        if (actualSegmentOrigin == null || !Double.isFinite(actualSegmentOrigin.x)
                || !Double.isFinite(actualSegmentOrigin.y) || !Double.isFinite(actualSegmentOrigin.z)
                || target == null || !target.getBoundingBox().inflate(4).contains(actualSegmentOrigin)) return false;
        return attackWithOrigin(player, "electromaster.iron_sand_lance", target, rawDamage, actualSegmentOrigin);
    }

    /**
     * NEW151 only: projected rails carry the actual snapshotted server emission origin,
     * without inventing a projectile entity. The alternative net retains the reviewed
     * ACA electrical PLAYER_ATTACK source. Neither origin nor type comes from a packet.
     */
    public static boolean attackRailThunderArray(ServerPlayer player, LivingEntity target, BigDecimal rawDamage,
                                                  boolean physical, net.minecraft.world.phys.Vec3 actualSegmentOrigin,
                                                  java.util.function.BooleanSupplier stillValid) {
        if (stillValid == null || !validRailArrayContext(player, target, physical, actualSegmentOrigin)
                || !validAttackGuard(stillValid)) return false;
        java.util.function.BooleanSupplier guardedContext = () ->
                validRailArrayContext(player, target, physical, actualSegmentOrigin) && stillValid.getAsBoolean();
        return attackWithOrigin(player, "electromaster.rail_thunder_array", target, rawDamage,
                physical ? actualSegmentOrigin : null, guardedContext);
    }
    private static boolean validRailArrayContext(ServerPlayer player, LivingEntity target, boolean physical,
                                                 net.minecraft.world.phys.Vec3 origin) {
        if (!(player != null && target != null && origin != null && serverThread(player)
                && player.level() == target.level() && player != target && player.isAlive() && target.isAlive()
                && !player.isRemoved() && !target.isRemoved()
                && Double.isFinite(origin.x) && Double.isFinite(origin.y) && Double.isFinite(origin.z)
                && player.position().distanceToSqr(origin) <= 96 * 96)) return false;
        var box = target.getBoundingBox();
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) return false;
        // Range belongs to the captured emission, never the caster's later feet position.
        // The closed backend guard certifies the actual ray intersection or net corridor.
        var point = physical ? new net.minecraft.world.phys.Vec3(Mth.clamp(origin.x, box.minX, box.maxX),
                Mth.clamp(origin.y, box.minY, box.maxY), Mth.clamp(origin.z, box.minZ, box.maxZ)) : box.getCenter();
        return origin.distanceToSqr(point) <= 96 * 96;
    }

    /** NEW141-only post-hook guard supplied by its closed server controller, never by a client packet. */
    public static boolean attackThunderNet(ServerPlayer player, LivingEntity target, BigDecimal rawDamage,
                                           java.util.function.BooleanSupplier stillValid) {
        if (stillValid == null || !validAttackGuard(stillValid)) return false;
        return attackWithOrigin(player, "electromaster.thunder_net", target, rawDamage, null, stillValid);
    }
    private static boolean validAttackGuard(java.util.function.BooleanSupplier guard) {
        try { return guard == null || guard.getAsBoolean(); }
        catch (RuntimeException refused) { return false; }
    }

    /**
     * Called after every ABSORPTION modifier, before native absorption/HP subtraction.
     * Only our private, target-bound controller sources participate; ordinary damage is untouched.
     * Keep the container/frame on their native stacks: enclosing hurt still owns all cleanup,
     * lastHurt/timer writes and already-completed durability or shield effects.
     */
    public static boolean vetoClosedDamageCommit(LivingEntity entity, DamageSource source, DamageContainer container) {
        if (!serverThread(entity) || !(source instanceof ExactSkillDamageSource exact)
                || exact.target != entity && exact.deflectionStrikePermit == null && exact.shearWindbladePermit == null && exact.yawPalmPermit == null && exact.vectorCagePermit == null
                    && exact.trajectoryReturnContact == null && exact.trajectoryReturnAirHammerPermit == null && exact.remoteScatterRelayPermit == null
                || !guardedControllerSkill(exact)) return false;
        boolean allowed = !exact.rejected && exact.target == entity && ACTIVE.get(entity) == exact && container != null
                && container.getSource() == exact && container.getOriginalDamage() == exact.witness
                && standardSource(exact) && exact.attackGuard != null && validAttackGuard(exact.attackGuard)
                && (exact.displacedProjectionPermit == null && exact.crossSubstitutionPermit == null && exact.shortInterceptionFallbackPermit == null
                    && exact.spatialPincerCorridorPermit == null && exact.multidimensionalLandingArrayPermit == null
                    && exact.deflectionStrikePermit == null && exact.shearWindbladePermit == null && exact.yawPalmPermit == null && exact.vectorCagePermit == null && exact.shortInterceptionContact == null
                    && exact.trajectoryReturnContact == null && exact.trajectoryReturnAirHammerPermit == null && exact.remoteScatterRelayPermit == null
                    || validNeedleCommit(exact, container))
                && (exact.shortInterceptionContact == null && exact.deflectionStrikePermit == null && exact.shearWindbladePermit == null && exact.yawPalmPermit == null && exact.vectorCagePermit == null
                    && exact.trajectoryReturnContact == null && exact.trajectoryReturnAirHammerPermit == null && exact.remoteScatterRelayPermit == null
                    || validInterceptionFrame(entity, exact))
                // Guard geometry can invoke native callbacks; loss of ownership remains a veto.
                && !exact.rejected && ACTIVE.get(entity) == exact;
        if (allowed && exact.remoteScatterRelayPermit != null) {
            NativeHurtFrame frame = FRAMES.get(entity);
            // Zero/canceled/fully held-shield-blocked hits are free. A positive incoming amount
            // wholly consumed by native absorption still buys one genuine committed attempt.
            allowed = !exact.remoteScatterRelayPaymentAttempted && !exact.remoteScatterRelayCommitAuthorized
                    && exact.beforeAbsorption > 0 && frame != null && frame.anchorPostGuard
                    && AnchorDamagePostProof.available() && exact.remoteScatterRelayPermit.paymentReady()
                    && validNeedleCommit(exact, container) && validInterceptionFrame(entity, exact)
                    && !exact.rejected && ACTIVE.get(entity) == exact;
            if (allowed) {
                exact.remoteScatterRelayPaymentAttempted = true;
                try {
                    allowed = exact.remoteScatterRelayPermit.commitPayment();
                    exact.remoteScatterRelayCommitAuthorized = allowed && exact.remoteScatterRelayPermit.paid();
                    allowed = exact.remoteScatterRelayCommitAuthorized;
                } catch (RuntimeException | LinkageError failure) {
                    // Payment may already have committed. Never refund, retry or run another native hurt.
                    LOGGER.warn("NEW253 final payment is unconfirmed; the terminal emission is retired", failure);
                    allowed = false;
                }
            }
        }
        if (allowed) {
            // NEW431 receipts require this final verified seam, including genuine full absorption.
            if (exact.trajectoryReturnContact != null || exact.trajectoryReturnAirHammerPermit != null)
                exact.trajectoryReturnCommitAuthorized = true;
            if (exact.yawPalmPermit != null || exact.vectorCagePermit != null) {
                // This final native pre-HP seam ran the original alive/controller guard above.
                // A matching Post alone never authorizes the temporary HP0 continuation.
                float before = entity.getHealth(), damage = container.getNewDamage();
                exact.yawCommitAuthorized = Float.isFinite(before) && before > 0 && Float.isFinite(damage) && damage >= 0;
                if (enabled(entity)) {
                    var prior = read(entity);
                    BigDecimal residual = exact.exactBeforeAbsorption.subtract(decimal(exact.expectedAbsorption)).max(BigDecimal.ZERO);
                    exact.yawExpectedDeath = exact.yawCommitAuthorized && prior.isPresent() && !prior.get().isDead()
                            && residual.compareTo(prior.get().health()) >= 0;
                } else exact.yawExpectedDeath = exact.yawCommitAuthorized && damage >= before;
            }
            return false;
        }
        exact.rejected = true;
        exact.nativeStageReached = true;
        // Canceling actuallyHurt alone leaves hurt's later container reads seeing stale damage.
        if (container != null && container.getSource() == exact) container.setNewDamage(0);
        return true;
    }

    private static boolean validInterceptionFrame(LivingEntity entity, ExactSkillDamageSource source) {
        NativeHurtFrame frame = FRAMES.get(entity);
        return frame != null && frame.source == source && frame.parent == null && !frame.poisoned
                && frame.incoming != null && !frame.incoming.isCanceled();
    }

    /** Closed contact budgets also refuse a rewrite after their ABSORPTION observer, before any native HP write. */
    private static boolean validNeedleCommit(ExactSkillDamageSource source, DamageContainer container) {
        float damage = container.getNewDamage();
        return source.absorptionObserved && source.prepared != source.ordinaryPrepared
                && Float.isFinite(damage) && damage >= 0
                && container.getReduction(DamageContainer.Reduction.ABSORPTION) == source.expectedAbsorption
                && damage == source.beforeAbsorption - source.expectedAbsorption;
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, false, false, false, false, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam,
                thermalShellPulse, foldedMirrorBeam, disintegrationSweepBeam, false, certifiedBeamEndpoint);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam,
                thermalShellPulse, foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, false, certifiedBeamEndpoint);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam,
                thermalShellPulse, foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam,
                certifiedBeamEndpoint, null, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                           DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                           CrossSubstitution.EmissionPermit crossSubstitutionPermit) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam,
                thermalShellPulse, foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam,
                certifiedBeamEndpoint, displacedProjectionPermit, crossSubstitutionPermit, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                           DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                           CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                           ShortInterception.FallbackPermit shortInterceptionFallbackPermit) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam,
                thermalShellPulse, foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam,
                certifiedBeamEndpoint, displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                           DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                           CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                           ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                           SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam,
                thermalShellPulse, foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam,
                certifiedBeamEndpoint, displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit,
                spatialPincerCorridorPermit, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                           DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                           CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                           ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                           SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                           MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam,
                thermalShellPulse, foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam,
                certifiedBeamEndpoint, displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit,
                spatialPincerCorridorPermit, multidimensionalLandingArrayPermit, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                           DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                           CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                           ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                           SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                           MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                           DeflectionStrike.EmissionPermit deflectionStrikePermit) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam,
                thermalShellPulse, foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam,
                certifiedBeamEndpoint, displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit,
                spatialPincerCorridorPermit, multidimensionalLandingArrayPermit, deflectionStrikePermit, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                           DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                           CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                           ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                           SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                           MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                           DeflectionStrike.EmissionPermit deflectionStrikePermit,
                                           ShearWindblade.EmissionPermit shearWindbladePermit) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam,
                thermalShellPulse, foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam,
                certifiedBeamEndpoint, displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit,
                spatialPincerCorridorPermit, multidimensionalLandingArrayPermit, deflectionStrikePermit, shearWindbladePermit, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                           DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                           CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                           ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                           SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                           MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                           DeflectionStrike.EmissionPermit deflectionStrikePermit,
                                           ShearWindblade.EmissionPermit shearWindbladePermit,
                                           YawPalm.EmissionPermit yawPalmPermit) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam,
                thermalShellPulse, foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam,
                certifiedBeamEndpoint, displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit,
                spatialPincerCorridorPermit, multidimensionalLandingArrayPermit, deflectionStrikePermit,
                shearWindbladePermit, yawPalmPermit, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                           DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                           CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                           ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                           SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                           MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                           DeflectionStrike.EmissionPermit deflectionStrikePermit,
                                           ShearWindblade.EmissionPermit shearWindbladePermit,
                                           YawPalm.EmissionPermit yawPalmPermit,
                                           TrajectoryReturn.AirHammerPermit trajectoryReturnAirHammerPermit) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam, thermalShellPulse,
                foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit, spatialPincerCorridorPermit,
                multidimensionalLandingArrayPermit, deflectionStrikePermit, shearWindbladePermit, yawPalmPermit, trajectoryReturnAirHammerPermit, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                           DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                           CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                           ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                           SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                           MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                           DeflectionStrike.EmissionPermit deflectionStrikePermit,
                                           ShearWindblade.EmissionPermit shearWindbladePermit,
                                           YawPalm.EmissionPermit yawPalmPermit,
                                           TrajectoryReturn.AirHammerPermit trajectoryReturnAirHammerPermit,
                                           VectorCage.EmissionPermit vectorCagePermit) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam, thermalShellPulse,
                foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit, spatialPincerCorridorPermit,
                multidimensionalLandingArrayPermit, deflectionStrikePermit, shearWindbladePermit, yawPalmPermit,
                trajectoryReturnAirHammerPermit, vectorCagePermit, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                           DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                           CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                           ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                           SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                           MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                           DeflectionStrike.EmissionPermit deflectionStrikePermit,
                                           ShearWindblade.EmissionPermit shearWindbladePermit,
                                           YawPalm.EmissionPermit yawPalmPermit,
                                           TrajectoryReturn.AirHammerPermit trajectoryReturnAirHammerPermit,
                                           VectorCage.EmissionPermit vectorCagePermit,
                                           RemoteScatterRelay.EmissionPermit remoteScatterRelayPermit) {
        return attackWithOrigin(player, skill, target, rawDamage, projectileOrigin, stillValid, dualPointBeam, thermalShellPulse,
                foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit, spatialPincerCorridorPermit,
                multidimensionalLandingArrayPermit, deflectionStrikePermit, shearWindbladePermit, yawPalmPermit,
                trajectoryReturnAirHammerPermit, vectorCagePermit, remoteScatterRelayPermit, null);
    }
    private static boolean attackWithOrigin(ServerPlayer player, String skill, LivingEntity target, BigDecimal rawDamage,
                                           net.minecraft.world.phys.Vec3 projectileOrigin,
                                           java.util.function.BooleanSupplier stillValid, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                           net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                           DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                           CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                           ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                           SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                           MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                           DeflectionStrike.EmissionPermit deflectionStrikePermit,
                                           ShearWindblade.EmissionPermit shearWindbladePermit,
                                           YawPalm.EmissionPermit yawPalmPermit,
                                           TrajectoryReturn.AirHammerPermit trajectoryReturnAirHammerPermit,
                                           VectorCage.EmissionPermit vectorCagePermit,
                                           RemoteScatterRelay.EmissionPermit remoteScatterRelayPermit,
                                           AbilityEmission abilityEmission) {
        if (player == null || target == null || !serverThread(player) || !enabled(player)
                || (dualPointBeam ? 1 : 0) + (thermalShellPulse ? 1 : 0) + (foldedMirrorBeam ? 1 : 0) + (disintegrationSweepBeam ? 1 : 0) + (particleLaminateBeam ? 1 : 0) + (multicoreCanopyBeam ? 1 : 0) + (displacedProjectionPermit != null ? 1 : 0) + (crossSubstitutionPermit != null ? 1 : 0) + (shortInterceptionFallbackPermit != null ? 1 : 0) + (spatialPincerCorridorPermit != null ? 1 : 0) + (multidimensionalLandingArrayPermit != null ? 1 : 0) + (deflectionStrikePermit != null ? 1 : 0) + (shearWindbladePermit != null ? 1 : 0) + (yawPalmPermit != null ? 1 : 0) + (vectorCagePermit != null ? 1 : 0) + (trajectoryReturnAirHammerPermit != null ? 1 : 0) + (remoteScatterRelayPermit != null ? 1 : 0) + (abilityEmission != null ? 1 : 0) > 1
                || !(dualPointBeam ? dualPointBenefitsActive(player) : thermalShellPulse
                    ? thermalShellBenefitsActive(player) && HighPowerResources.ready(player) : foldedMirrorBeam
                    ? foldedMirrorBenefitsActive(player) : disintegrationSweepBeam
                    ? disintegrationSweepBenefitsActive(player) : particleLaminateBeam
                    ? particleLaminateBenefitsActive(player) : multicoreCanopyBeam
                    ? multicoreCanopyBenefitsActive(player) : displacedProjectionPermit != null
                    ? displacedProjectionBenefitsActive(player) : crossSubstitutionPermit != null
                    ? crossSubstitutionBenefitsActive(player) : shortInterceptionFallbackPermit != null
                    ? shortInterceptionBenefitsActive(player) : spatialPincerCorridorPermit != null
                    ? spatialPincerCorridorBenefitsActive(player) : multidimensionalLandingArrayPermit != null
                    ? multidimensionalLandingArrayBenefitsActive(player) : deflectionStrikePermit != null
                    ? deflectionStrikeBenefitsActive(player) : shearWindbladePermit != null
                    ? shearWindbladeBenefitsActive(player) : yawPalmPermit != null
                    ? yawPalmBenefitsActive(player) : vectorCagePermit != null
                    ? vectorCageBenefitsActive(player) : trajectoryReturnAirHammerPermit != null
                    ? trajectoryReturnBenefitsActive(player) : remoteScatterRelayPermit != null
                    ? remoteScatterRelayBenefitsActive(player) : abilityEmission != null ? abilityEmission.valid() : electromasterBenefitsActive(player))
                || player.level() != target.level() || player == target
                || !player.isAlive() || !target.isAlive() || !supported(target) || skill == null || skill.isBlank()) return false;
        if (skill.equals("meltdowner.remote_scatter_relay") && remoteScatterRelayPermit == null) return false;
        if (skill.equals("teleporter.displaced_projection") && displacedProjectionPermit == null) return false;
        if (skill.equals("teleporter.cross_substitution") && crossSubstitutionPermit == null) return false;
        if (skill.equals("teleporter.short_interception") && shortInterceptionFallbackPermit == null) return false;
        if (skill.equals("teleporter.spatial_pincer_corridor") && spatialPincerCorridorPermit == null) return false;
        if (skill.equals("teleporter.multidimensional_landing_array") && multidimensionalLandingArrayPermit == null) return false;
        if (skill.equals("vecmanip.deflection_strike") && deflectionStrikePermit == null) return false;
        if (skill.equals("vecmanip.shear_windblade") && shearWindbladePermit == null) return false;
        if (skill.equals("vecmanip.yaw_palm") && yawPalmPermit == null) return false;
        if (skill.equals("vecmanip.vector_cage") && vectorCagePermit == null) return false;
        if (skill.equals("vecmanip.trajectory_return_air") && trajectoryReturnAirHammerPermit == null) return false;
        if (skill.equals("vecmanip.trajectory_return_native")) return false;
        if ((skill.equals(ConstructRuntime.DAMAGE_KEY) || skill.equals(NewSkillRuntime.Kind.ZENITH_DOMINION.damageKey))
                && abilityEmission == null) return false;
        if (target instanceof ServerPlayer && !AcademyConfig.ATTACK_PLAYERS.get()) return false;
        if (unsupportedReason(player).isPresent() || unsupportedReason(target).isPresent()
                || ACTIVE.containsKey(target) || TRACES.containsKey(target)
                || (deflectionStrikePermit != null || shearWindbladePermit != null || yawPalmPermit != null || vectorCagePermit != null || trajectoryReturnAirHammerPermit != null || remoteScatterRelayPermit != null || abilityEmission != null) && FRAMES.containsKey(target)) return false;
        if (target.getPersistentData().contains(DATA_KEY) && !enabled(target)) return false;
        BigDecimal raw;
        try { raw = CombatAmounts.requirePositiveAmount(CombatAmounts.overkillClamp(rawDamage), "prototype raw damage"); }
        catch (IllegalArgumentException | NullPointerException invalid) { return false; }
        // Keep the exact input when the historical double-valued hook is unchanged.
        // Hook changes retain their legacy double precision; non-positive amounts deny the hit.
        double hookInput = raw.doubleValue();
        var hook = new SkillAttackEvent(player, skill, target, hookInput);
        NeoForge.EVENT_BUS.post(hook);
        if (!Double.isFinite(hook.amount) || hook.amount <= 0) return false;
        // Closed immutable whole-cast allocations cannot be amplified, even by compensating modifiers.
        if ((deflectionStrikePermit != null || shearWindbladePermit != null || yawPalmPermit != null || vectorCagePermit != null || trajectoryReturnAirHammerPermit != null || remoteScatterRelayPermit != null || abilityEmission != null) && hook.amount > hookInput) return false;
        if (Double.compare(hook.amount, hookInput) != 0) {
            raw = raw.multiply(BigDecimal.valueOf(hook.amount)).divide(BigDecimal.valueOf(hookInput), RATIO_CONTEXT);
        }
        double globalScale = AcademyConfig.DAMAGE_SCALE.get();
        float localScale = 1;
        int separator = skill.indexOf('.');
        if (separator > 0) localScale = AcademyConfig.consumptionConfig()
                .optionalFloat(skill.substring(0, separator), skill.substring(separator + 1), "damage_scale");
        if (!Double.isFinite(globalScale) || globalScale <= 0 || !Float.isFinite(localScale) || localScale <= 0) return false;
        if ((deflectionStrikePermit != null || shearWindbladePermit != null || yawPalmPermit != null || vectorCagePermit != null || trajectoryReturnAirHammerPermit != null || remoteScatterRelayPermit != null || abilityEmission != null) && (globalScale > 1 || localScale > 1)) return false;
        if (globalScale != 1) raw = raw.multiply(BigDecimal.valueOf(globalScale));
        if (localScale != 1) raw = raw.multiply(decimal(localScale));
        // A callback/config may reduce or cancel a pulse. Amplifying its fixed share
        // of the conserved NEW222 pulse or NEW221/NEW231 whole-cast budget refuses this attempt, never silently
        // clamps it or redistributes the unused allocation to another target.
        if ((thermalShellPulse || foldedMirrorBeam || disintegrationSweepBeam || particleLaminateBeam || multicoreCanopyBeam
                || displacedProjectionPermit != null || crossSubstitutionPermit != null || shortInterceptionFallbackPermit != null
                || spatialPincerCorridorPermit != null || multidimensionalLandingArrayPermit != null
                || deflectionStrikePermit != null || shearWindbladePermit != null || yawPalmPermit != null || vectorCagePermit != null || trajectoryReturnAirHammerPermit != null || remoteScatterRelayPermit != null || abilityEmission != null) && raw.compareTo(rawDamage) > 0) return false;
        try { raw = CombatAmounts.requirePositiveAmount(CombatAmounts.overkillClamp(raw.stripTrailingZeros()), "scaled raw damage"); }
        catch (IllegalArgumentException invalid) { return false; }
        flushRecovery(target);
        if (target instanceof cn.academy.port.city.CityNpcEntity npc) {
            raw = npc.combat().sparringIncoming(player, raw);
            if (raw.signum() <= 0) return false;
        }
        float witness = raw.min(BigDecimal.valueOf(MAX_NATIVE_WITNESS)).floatValue();
        if (!(witness > 0) || !Float.isFinite(witness)) return false;
        var source = new ExactSkillDamageSource(player, skill, target, raw, witness, projectileOrigin, stillValid,
                dualPointBeam, thermalShellPulse, foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit, spatialPincerCorridorPermit,
                multidimensionalLandingArrayPermit, deflectionStrikePermit, shearWindbladePermit, yawPalmPermit, trajectoryReturnAirHammerPermit, vectorCagePermit, remoteScatterRelayPermit);
        if (!standardSource(source)) return false;
        HighPowerNaturalRecovery.offensiveAction(player);
        // SkillAttackEvent, config scaling and recovery may have changed the captured net/positions.
        // Closed controller guards never bypass native defenses.
        if (!validAttackGuard(stillValid)) return false;
        BigDecimal cageHealthBefore = vectorCagePermit == null ? null : read(target).map(ExactCombatState::health).orElseGet(() -> decimal(target.getHealth()));
        float cageAbsorptionBefore = vectorCagePermit == null ? 0 : target.getAbsorptionAmount();
        ACTIVE.put(target, source);
        boolean capAccepted = false;
        CapBreak.arm(target);
        try {
            boolean accepted = target.hurt(source, witness);
            capAccepted = accepted;
            if (accepted && !source.rejected) TrainingTargetReadout.hit(player, skill, target, raw);
            if(source.spatialPincerCorridorPermit!=null||source.multidimensionalLandingArrayPermit!=null)
                tpContactDiagnostic(source.skill,"native_hurt accepted="+accepted+" rejected="+source.rejected+" ordinaryPrepared="+source.ordinaryPrepared+" armorObserved="+source.ordinaryArmorObserved+" beforeArmor="+source.ordinaryBeforeArmor+" preLimit="+source.ordinaryPreLimit,!source.rejected&&accepted);
            if (source.yawPalmPermit != null && yawPalmSettledDeath(source)) source.yawPalmPermit.completedLethal();
            if (source.vectorCagePermit != null && vectorCageSettledDeath(source)) source.vectorCagePermit.completedLethal();
            if (vectorCagePermit != null) {
                BigDecimal after = read(target).map(ExactCombatState::health).orElseGet(() -> decimal(target.getHealth()));
                return accepted && !source.rejected && source.yawCommitAuthorized && source.absorptionObserved
                        && source.beforeAbsorption > 0 && (source.accounted || source.ordinaryPrepared)
                        && (after.compareTo(cageHealthBefore) < 0 || target.getAbsorptionAmount() < cageAbsorptionBefore);
            }
            if (remoteScatterRelayPermit != null)
                return accepted && !source.rejected && source.remoteScatterRelayCommitAuthorized
                        && source.remoteScatterRelayPostObserved && remoteScatterRelayPermit.paid();
            return accepted && !source.rejected;
        }
        finally {
            ACTIVE.remove(target, source);
            if (source.rejected && source.nativeStageReached) HISTORY.remove(target);
            NativeHurtFrame abandoned = FRAMES.get(target);
            if (abandoned != null && abandoned.source == source) {
                if (abandoned.parent == null) FRAMES.remove(target); else FRAMES.put(target, abandoned.parent);
                HISTORY.remove(target);
            }
            flushRecovery(target);
            flushEarnedGrowth(target);
            if (source.rejected) CapBreak.afterHit(null, skill, target, source, witness, false); // disarm only
            else CapBreak.afterHit(player, skill, target, source, witness, capAccepted);
        }
    }

    /** NEW331 substitutes one closed amount at the existing native Entity.hurt operand.
     * Native arrows retain their existing LivingIncoming/Pre hooks only; no new SkillAttackEvent is invented.
     * The distinct fallback remains an ACA skill-created needle and keeps its normal skill event/config path.
     */
    static boolean interceptNativeArrow(ShortInterceptionArrowBridge.Contact contact, float originalAmount,
                                        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        var permit = contact.permit();
        LivingEntity target = permit.target();
        if (!validInterceptionContact(contact) || ACTIVE.containsKey(target) || TRACES.containsKey(target)
                || FRAMES.containsKey(target)) return original.call(target, contact.original, originalAmount);
        BigDecimal raw;
        try { raw = CombatAmounts.requirePositiveAmount(permit.rawDamage(), "NEW331 frozen native arrow amount"); }
        catch (IllegalArgumentException | NullPointerException invalid) { return original.call(target, contact.original, originalAmount); }
        flushRecovery(target);
        if (!validInterceptionContact(contact)) return original.call(target, contact.original, originalAmount);
        float witness = raw.min(BigDecimal.valueOf(MAX_NATIVE_WITNESS)).floatValue();
        if (!(witness > 0) || !Float.isFinite(witness)) return original.call(target, contact.original, originalAmount);
        var source = new ExactSkillDamageSource(contact, raw, witness);
        if (!standardSource(source)) return original.call(target, contact.original, originalAmount);
        ACTIVE.put(target, source);
        try {
            // No second hurt, recursive bridge, baseDamage edit or manufactured success.
            boolean accepted = original.call(target, source, witness);
            contact.confirmed = accepted && !source.rejected && source.absorptionObserved
                    && source.beforeAbsorption > source.expectedAbsorption
                    && (source.accounted || source.ordinaryPrepared);
            return accepted;
        } finally {
            ACTIVE.remove(target, source);
            if (source.rejected && source.nativeStageReached) HISTORY.remove(target);
            NativeHurtFrame abandoned = FRAMES.get(target);
            if (abandoned != null && abandoned.source == source) {
                if (abandoned.parent == null) FRAMES.remove(target); else FRAMES.put(target, abandoned.parent);
                HISTORY.remove(target);
            }
            flushRecovery(target);
            flushEarnedGrowth(target);
        }
    }

    /** NEW431 only replaces the existing native arrow operand with the frozen 2D budget. */
    static boolean returnNativeArrow(TrajectoryReturnArrowBridge.Contact contact, float originalAmount,
                                     com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        var permit = contact.permit();
        LivingEntity target = contact.target();
        if (!validTrajectoryReturnContact(contact) || ACTIVE.containsKey(target) || TRACES.containsKey(target)
                || FRAMES.containsKey(target)) return original.call(target, contact.original, originalAmount);
        BigDecimal raw;
        try { raw = CombatAmounts.requirePositiveAmount(permit.rawDamage(), "NEW431 frozen native arrow amount"); }
        catch (IllegalArgumentException | NullPointerException invalid) { return original.call(target, contact.original, originalAmount); }
        flushRecovery(target);
        if (!validTrajectoryReturnContact(contact)) return original.call(target, contact.original, originalAmount);
        float witness = raw.min(BigDecimal.valueOf(MAX_NATIVE_WITNESS)).floatValue();
        if (!(witness > 0) || !Float.isFinite(witness)) return original.call(target, contact.original, originalAmount);
        var source = new ExactSkillDamageSource(contact, raw, witness);
        if (!standardSource(source)) return original.call(target, contact.original, originalAmount);
        ACTIVE.put(target, source);
        try {
            boolean accepted = original.call(target, source, witness);
            // The bridge separately requires an actual HP/shield/absorption decrease before reporting success.
            contact.confirmed = accepted && !source.rejected && source.trajectoryReturnCommitAuthorized
                    && source.absorptionObserved && source.beforeAbsorption > 0
                    && source.beforeAbsorption >= source.expectedAbsorption
                    && (source.accounted || source.ordinaryPrepared);
            return accepted;
        } finally {
            ACTIVE.remove(target, source);
            if (source.rejected && source.nativeStageReached) HISTORY.remove(target);
            NativeHurtFrame abandoned = FRAMES.get(target);
            if (abandoned != null && abandoned.source == source) {
                if (abandoned.parent == null) FRAMES.remove(target); else FRAMES.put(target, abandoned.parent);
                HISTORY.remove(target);
            }
            flushRecovery(target);
            flushEarnedGrowth(target);
        }
    }

    private static boolean validTrajectoryReturnContact(TrajectoryReturnArrowBridge.Contact contact) {
        if (!TrajectoryReturnArrowBridge.validContact(contact)) return false;
        LivingEntity target = contact.target(); ServerPlayer controller = contact.permit().controller();
        return trajectoryReturnBenefitsActive(controller) && unsupportedReason(controller).isEmpty()
                && controller.isAlive() && !controller.isRemoved()
                && controller.serverLevel().getEntity(controller.getUUID()) == controller
                // The original hostile shooter may itself be this current first body.
                && DisplacedProjectionWorld.hostile(controller, target)
                && TrajectoryReturnArrowBridge.validContact(contact);
    }

    private static boolean validInterceptionContact(ShortInterceptionArrowBridge.Contact contact) {
        if (!ShortInterceptionArrowBridge.validContact(contact)) return false;
        var permit = contact.permit();
        LivingEntity target = permit.target(); ServerPlayer controller = permit.controller();
        return hostileTarget(target) && supported(target)
                && target.level() == controller.level() && target.isAlive() && !target.isRemoved()
                && !target.isSpectator() && !target.isInvulnerable() && !controller.isAlliedTo(target) && !target.isAlliedTo(controller)
                && unsupportedReason(target).isEmpty() && unsupportedReason(controller).isEmpty()
                && controller.serverLevel().getEntity(target.getUUID()) == target && controller.serverLevel().getEntity(target.getId()) == target;
    }

    /** Called by the verified native seam, including ordinary hits on an enrolled entity. */
    public static void beginNativeHurt(LivingEntity entity, DamageSource source, float previousNative) {
        if (!serverThread(entity) || !enabled(entity)
                && !(source instanceof ExactSkillDamageSource exact && (exact.shortInterceptionContact != null
                    || exact.deflectionStrikePermit != null || exact.shearWindbladePermit != null || exact.yawPalmPermit != null || exact.vectorCagePermit != null
                    || exact.trajectoryReturnContact != null || exact.trajectoryReturnAirHammerPermit != null || exact.remoteScatterRelayPermit != null))
                && !(ACTIVE.get(entity) != null && (ACTIVE.get(entity).shortInterceptionContact != null
                    || ACTIVE.get(entity).deflectionStrikePermit != null || ACTIVE.get(entity).shearWindbladePermit != null || ACTIVE.get(entity).yawPalmPermit != null || ACTIVE.get(entity).vectorCagePermit != null
                    || ACTIVE.get(entity).trajectoryReturnContact != null || ACTIVE.get(entity).trajectoryReturnAirHammerPermit != null
                    || ACTIVE.get(entity).remoteScatterRelayPermit != null))) return;
        NativeHurtFrame parent = FRAMES.get(entity);
        NativeHurtFrame frame = new NativeHurtFrame(source, HISTORY.get(entity), parent);
        FRAMES.put(entity, frame);
        if (entity instanceof ServerPlayer player) {
            frame.anchorFall = BrakingTouchLandingProof.bindLandingHurt(player, source);
            frame.anchorPostGuard = frame.anchorFall != null || autonomousVectorCircleBenefitsActive(player);
        }
        // NEW253 exact settlement requires the authenticated native Post object, including ordinary targets.
        if (source instanceof ExactSkillDamageSource relay && relay.remoteScatterRelayPermit != null)
            frame.anchorPostGuard = true;
        // NEW342 records evidence only; it never changes this native hurt operation.
        if (entity instanceof ServerPlayer player)
            frame.emergencyReturn = EmergencyReturn.captureBeforeHurt(player, source);
        if (parent != null) {
            parent.poisoned = true; frame.poisoned = true;
            if (parent.source instanceof ExactSkillDamageSource exact) exact.rejected = true;
        }
    }

    /**
     * Native hurt may already have reduced the normalized adapter to zero before Post.
     * Only this exact accepted, outer hurt frame may retain its captured NEW212 contact
     * until the authoritative exact HP/share transaction settles. Ordinary observation
     * continues to require a living owner; this reads no state beyond the live frame.
     */
    static boolean scatterNativeFrame(ServerPlayer player, ScatterPlates.Capture capture) {
        if (player == null || capture == null || !serverThread(player)) return false;
        NativeHurtFrame frame = FRAMES.get(player);
        return frame != null && frame.parent == null && !frame.poisoned && frame.scatterPlates == capture
                && frame.incoming != null && !frame.incoming.isCanceled()
                && frame.source == frame.incoming.getSource();
    }

    static boolean zeroGapNativeFrame(ServerPlayer player, ZeroGapArmor.Capture capture) {
        if (player == null || capture == null || !serverThread(player)) return false;
        NativeHurtFrame frame = FRAMES.get(player);
        return frame != null && frame.parent == null && !frame.poisoned && frame.zeroGap == capture
                && frame.incoming != null && !frame.incoming.isCanceled()
                && frame.source == frame.incoming.getSource();
    }

    static boolean curtainNativeFrame(ServerPlayer player, DissipationCurtain.Capture capture) {
        if (player == null || capture == null || !serverThread(player)) return false;
        NativeHurtFrame frame = FRAMES.get(player);
        return frame != null && frame.parent == null && !frame.poisoned && frame.curtain == capture
                && frame.incoming != null && !frame.incoming.isCanceled()
                && frame.source == frame.incoming.getSource();
    }

    /** Native temporary HP0 may retain only this outer frame's authenticated shell capture. */
    static boolean thermalNativeFrame(ServerPlayer player, ThermalShell.Capture capture) {
        if (player == null || capture == null || !serverThread(player)) return false;
        NativeHurtFrame frame = FRAMES.get(player);
        return frame != null && frame.parent == null && !frame.poisoned && frame.thermalShell == capture
                && frame.incoming != null && !frame.incoming.isCanceled()
                && frame.source == frame.incoming.getSource();
    }

    /** Temporary native HP0 and later native impulse share this exact outer, uncanceled frame. */
    static boolean vectorParryNativeFrame(ServerPlayer player, VectorParry.Capture capture) {
        if (player == null || capture == null || !serverThread(player)) return false;
        NativeHurtFrame frame = FRAMES.get(player);
        return frame != null && frame.parent == null && !frame.poisoned && frame.vectorParry == capture
                && frame.incoming != null && !frame.incoming.isCanceled()
                && frame.source == frame.incoming.getSource();
    }

    static boolean circulationReliefNativeFrame(ServerPlayer player, CirculationRelief.Capture capture) {
        if (player == null || capture == null || !serverThread(player)) return false;
        NativeHurtFrame frame = FRAMES.get(player);
        return frame != null && frame.parent == null && !frame.poisoned && frame.circulationRelief == capture
                && frame.anchorPostGuard && frame.incoming != null && !frame.incoming.isCanceled()
                && frame.source == frame.incoming.getSource();
    }

    static boolean autonomousVectorCircleNativeFrame(ServerPlayer player, AutonomousVectorCircle.Capture capture) {
        if (player == null || capture == null || !serverThread(player)) return false;
        NativeHurtFrame frame = FRAMES.get(player);
        return frame != null && frame.parent == null && !frame.poisoned && frame.autonomousVectorCircle == capture
                && frame.anchorPostGuard && frame.incoming != null && !frame.incoming.isCanceled()
                && frame.source == frame.incoming.getSource();
    }

    static YawPalm.EmissionPermit yawPalmNativeSource(LivingEntity target) {
        if (target == null || !serverThread(target)) return null;
        ExactSkillDamageSource source = ACTIVE.get(target); NativeHurtFrame frame = FRAMES.get(target);
        return source != null && source.target == target && source.yawPalmPermit != null
                && frame != null && frame.source == source ? source.yawPalmPermit : null;
    }

    static boolean yawPalmNativeShieldBlocked(LivingEntity target, YawPalm.EmissionPermit permit) {
        if (target == null || permit == null || !serverThread(target)) return false;
        ExactSkillDamageSource source = ACTIVE.get(target); NativeHurtFrame frame = FRAMES.get(target);
        return source != null && source.yawPalmPermit == permit && source.target == target && !source.rejected
                && YAW_PALM_ATTACKS.get(source.player) == permit && frame != null && frame.source == source
                && frame.parent == null && !frame.poisoned && frame.incoming != null && !frame.incoming.isCanceled()
                && frame.incoming.getSource() == source && standardSource(source)
                && target instanceof NativeCooldownAccess access && access.academy$yawPalmShieldBlocked(source)
                && ACTIVE.get(target) == source && FRAMES.get(target) == frame;
    }

    private static boolean yawPalmSettledDeath(ExactSkillDamageSource source) {
        if (source == null || source.yawPalmPermit == null || source.rejected || !source.yawCommitAuthorized
                || !source.yawExpectedDeath || ACTIVE.get(source.target) != source || source.target.isAlive()
                || source.target.getHealth() != 0 || source.target.isRemoved()) return false;
        return enabled(source.target) ? source.accounted && read(source.target).filter(ExactCombatState::isDead).isPresent()
                : source.ordinaryPrepared && source.absorptionObserved;
    }

    static YawPalm.EmissionPermit yawPalmNativePermit(LivingEntity target) {
        if (target == null || !serverThread(target)) return null;
        ExactSkillDamageSource source = ACTIVE.get(target);
        NativeHurtFrame frame = FRAMES.get(target);
        if (source == null || source.yawPalmPermit == null || source.target != target || source.rejected
                || frame == null || frame.source != source || frame.parent != null || frame.poisoned
                || frame.incoming == null || frame.incoming.isCanceled() || frame.incoming.getSource() != source
                || !standardSource(source)) return null;
        boolean ownLethal = yawPalmSettledDeath(source);
        if (ownLethal) source.yawPalmPermit.nativeLethal = true;
        if (!(ownLethal ? source.yawPalmPermit.nativeValid() : validAttackGuard(source.attackGuard))
                || ACTIVE.get(target) != source || FRAMES.get(target) != frame || source.rejected) return null;
        return source.yawPalmPermit;
    }

    static VectorCage.EmissionPermit vectorCageNativeSource(LivingEntity target) {
        if (target == null || !serverThread(target)) return null;
        ExactSkillDamageSource source = ACTIVE.get(target); NativeHurtFrame frame = FRAMES.get(target);
        return source != null && source.target == target && source.vectorCagePermit != null
                && frame != null && frame.source == source ? source.vectorCagePermit : null;
    }

    static boolean vectorCageNativeShieldBlocked(LivingEntity target, VectorCage.EmissionPermit permit) {
        if (target == null || permit == null || !serverThread(target)) return false;
        ExactSkillDamageSource source = ACTIVE.get(target); NativeHurtFrame frame = FRAMES.get(target);
        return source != null && source.vectorCagePermit == permit && source.target == target && !source.rejected
                && VECTOR_CAGE_ATTACKS.get(source.player) == permit && frame != null && frame.source == source
                && frame.parent == null && !frame.poisoned && frame.incoming != null && !frame.incoming.isCanceled()
                && frame.incoming.getSource() == source && standardSource(source)
                && target instanceof NativeCooldownAccess access && access.academy$yawPalmShieldBlocked(source)
                && ACTIVE.get(target) == source && FRAMES.get(target) == frame;
    }

    private static boolean vectorCageSettledDeath(ExactSkillDamageSource source) {
        if (source == null || source.vectorCagePermit == null || source.rejected || !source.yawCommitAuthorized
                || !source.yawExpectedDeath || ACTIVE.get(source.target) != source || source.target.isAlive()
                || source.target.getHealth() != 0 || source.target.isRemoved()) return false;
        return enabled(source.target) ? source.accounted && read(source.target).filter(ExactCombatState::isDead).isPresent()
                : source.ordinaryPrepared && source.absorptionObserved;
    }

    static VectorCage.EmissionPermit vectorCageNativePermit(LivingEntity target) {
        if (target == null || !serverThread(target)) return null;
        ExactSkillDamageSource source = ACTIVE.get(target);
        NativeHurtFrame frame = FRAMES.get(target);
        if (source == null || source.vectorCagePermit == null || source.target != target || source.rejected
                || frame == null || frame.source != source || frame.parent != null || frame.poisoned
                || frame.incoming == null || frame.incoming.isCanceled() || frame.incoming.getSource() != source
                || !standardSource(source)) return null;
        boolean ownLethal = vectorCageSettledDeath(source);
        if (ownLethal) source.vectorCagePermit.nativeLethal = true;
        if (!(ownLethal ? source.vectorCagePermit.nativeValid() : validAttackGuard(source.attackGuard))
                || ACTIVE.get(target) != source || FRAMES.get(target) != frame || source.rejected) return null;
        return source.vectorCagePermit;
    }

    static boolean inertialAnchorNativeFrame(ServerPlayer player, InertialAnchor.Capture capture) {
        if (player == null || capture == null || !serverThread(player)) return false;
        NativeHurtFrame frame = FRAMES.get(player);
        return frame != null && frame.parent == null && !frame.poisoned && frame.inertialAnchor == capture
                && frame.incoming != null && !frame.incoming.isCanceled()
                && frame.source == frame.incoming.getSource();
    }

    /** Scoped to the already bound real landing hurt; ordinary combat has no additional gate. */
    public static boolean anchorFallPending(LivingEntity entity) {
        NativeHurtFrame frame = FRAMES.get(entity);
        return frame != null && frame.anchorFall != null;
    }

    public static boolean anchorPostGuardPending(LivingEntity entity) {
        NativeHurtFrame frame = FRAMES.get(entity);
        return frame != null && frame.anchorPostGuard;
    }

    /** Only an unavailable identity observer releases this extra accounting gate, never a mere defense refusal. */
    public static void abandonAnchorPostGuard(LivingEntity entity) {
        abandonAnchorFall(entity);
        for (NativeHurtFrame frame = FRAMES.get(entity); frame != null; frame = frame.parent)
            frame.anchorPostGuard = false;
    }

    public static void abandonAnchorFall(LivingEntity entity) {
        for (NativeHurtFrame frame = FRAMES.get(entity); frame != null; frame = frame.parent) {
            frame.circulationRelief = null;
            frame.autonomousVectorCircle = null;
            if (frame.anchorFall != null) {
                frame.anchorFall.close(); frame.anchorFall = null;
            }
        }
    }

    /** Read-only deferred-emission fence; never bypasses an active native hurt frame. */
    static boolean nativeHurtInProgress(LivingEntity entity) { return FRAMES.containsKey(entity); }

    /**
     * Only the first comparison operand is replaced. Returning current encodes FALSE
     * for current > previous; returning zero encodes TRUE for a verified positive
     * current. Neither value is stored in lastHurt or used as damage/reduction.
     * The native timer branch, shield ordering and later lastHurt writes are intact.
     */
    public static float nativeCooldownComparison(LivingEntity entity, DamageSource damageSource, float current,
                                                 float previousNative, DamageContainer container) {
        NativeHurtFrame frame = FRAMES.get(entity);
        if (frame == null || frame.source != damageSource) return previousNative;
        if (!enabled(entity)) return previousNative;
        frame.compared = true;
        frame.nativeIncoming = current;
        frame.nativePrevious = previousNative;
        frame.comparisonAccepted = current > previousNative;
        ExactSkillDamageSource exact = damageSource instanceof ExactSkillDamageSource candidate && candidate.target == entity ? candidate : null;
        HitHistory previous = frame.previousHistory;
        if (!Float.isFinite(current) || current < 0 || !Float.isFinite(previousNative) || previousNative < 0
                || frame.poisoned || previous == null || previous.nativeIncoming != previousNative) {
            if (exact != null) { exact.rejected = true; frame.comparisonAccepted = false; return current; }
            return previousNative; // Unknown ordinary history keeps ordinary native behavior.
        }
        if (exact == null && !previous.exactBridge) return previousNative;
        if (exact != null) {
            float blocked = container == null ? Float.NaN : container.getBlockedDamage();
            if (container == null || container.getSource() != exact || container.getOriginalDamage() != exact.witness
                    || !guardedStandardSource(exact) || unsupportedReason(entity).isPresent() || unsupportedReason(exact.player).isPresent()
                    || blocked != 0 && blocked != exact.witness || current != exact.witness - blocked) {
                exact.rejected = true; frame.comparisonAccepted = false; return current;
            }
            frame.logicalIncoming = blocked == exact.witness ? BigDecimal.ZERO : exact.raw;
        } else {
            // This float has already passed ordinary incoming hooks, shield, freezing
            // and helmet transforms. It is in native raw units, not a scaled witness.
            frame.logicalIncoming = decimal(current);
        }
        if (frame.logicalIncoming.compareTo(previous.rawIncoming) <= 0) {
            frame.comparisonAccepted = false;
            return current;
        }
        frame.logicalResidual = frame.logicalIncoming.subtract(previous.rawIncoming);
        frame.residualWitness = exact == null ? frame.logicalResidual.floatValue()
                : frame.logicalResidual.min(BigDecimal.valueOf(MAX_NATIVE_WITNESS)).floatValue();
        if (!Float.isFinite(frame.residualWitness) || exact != null && !(frame.residualWitness > 0)) {
            if (exact != null) exact.rejected = true;
            frame.comparisonAccepted = false;
            return current; // Explicit native residual representability limit, never a guessed rank.
        }
        frame.adaptCooldown = true;
        frame.comparisonAccepted = true;
        return 0; // Verified current > 0; branch-only encoding of the exact comparison.
    }

    /** Commit mapping only after native lastHurt writes, including the differential branch after Post. */
    public static void endNativeHurt(LivingEntity entity, DamageSource source, float nativeLastHurt, boolean completed) {
        NativeHurtFrame frame = FRAMES.get(entity);
        if (frame == null || frame.source != source) return;
        // Ordinary native entities have no exact ledger: flushRecovery below clears their ACTIVE entry.
        // Seal only this completed, nonreentrant source's real final death before that cleanup.
        if (completed && frame.parent == null && !frame.poisoned && source instanceof ExactSkillDamageSource exact
                && exact.yawPalmPermit != null && yawPalmSettledDeath(exact)) exact.yawPalmPermit.completedLethal();
        if (completed && frame.parent == null && !frame.poisoned && source instanceof ExactSkillDamageSource cage
                && cage.vectorCagePermit != null && vectorCageSettledDeath(cage)) cage.vectorCagePermit.completedLethal();
        if (frame.parent == null) FRAMES.remove(entity); else FRAMES.put(entity, frame.parent);
        NativeTrace trace = TRACES.get(entity);
        if (trace != null && trace.frame == frame) {
            if (trace.previousTrace == null) TRACES.remove(entity); else TRACES.put(entity, trace.previousTrace);
        }
        if (!completed) {
            HISTORY.remove(entity);
            if (entity instanceof ServerPlayer player)
                EmergencyReturn.sealAfterHurt(player, frame.emergencyReturn, false);
            return;
        }
        if (!frame.poisoned && frame.pendingHistory != null && frame.pendingHistory.nativeIncoming == nativeLastHurt) {
            HISTORY.put(entity, frame.pendingHistory);
        } else if (frame.poisoned || frame.pendingHistory != null || frame.sawArmor
                || frame.incoming != null && !frame.incoming.isCanceled() && (!frame.compared || frame.comparisonAccepted)) {
            HISTORY.remove(entity);
        }
        flushRecovery(entity);
        flushEarnedGrowth(entity);
        if (entity instanceof ServerPlayer player)
            EmergencyReturn.sealAfterHurt(player, frame.emergencyReturn,
                    !frame.poisoned && frame.parent == null && frame.incoming != null && !frame.incoming.isCanceled());
    }

    /**
     * Finish a preceding native hurt call before this call changes adapter HP.
     * In particular, vanilla consumes the totem after our previous Post event. A
     * second, stronger ordinary hit can beat the hurt cooldown in the same game
     * tick. Waiting until its Post would observe already-subtracted adapter HP
     * and could incorrectly overwrite the native rescue with the old zero ledger.
     * Reconciliation also runs when this new hit is canceled: it belongs to the
     * completed preceding hit and must not depend on acceptance of this one.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void beforeDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!serverThread(entity)) return;
        // Capturing this private source in a native callback grants no second target or later replay.
        if (event.getSource() instanceof ExactSkillDamageSource strike
                && (strike.deflectionStrikePermit != null || strike.shearWindbladePermit != null || strike.yawPalmPermit != null || strike.vectorCagePermit != null
                    || strike.trajectoryReturnContact != null || strike.trajectoryReturnAirHammerPermit != null || strike.remoteScatterRelayPermit != null)
                && (strike.target != entity || ACTIVE.get(entity) != strike || strike.rejected)) {
            strike.rejected = true;
            event.setCanceled(true);
            return;
        }
        // Claim the one-use historical arrow crossing before any recovery or mutable observer.
        ZeroGapArmor.Capture armorCapture = entity instanceof ServerPlayer player
                ? ZeroGapArmor.captureIncoming(player, event.getSource()) : null;
        DissipationCurtain.Capture curtainCapture = entity instanceof ServerPlayer player
                ? DissipationCurtain.captureIncoming(player, event.getSource()) : null;
        ScatterPlates.Capture scatterCapture = entity instanceof ServerPlayer player
                ? ScatterPlates.captureIncoming(player, event.getSource()) : null;
        ThermalShell.Capture thermalCapture = entity instanceof ServerPlayer player
                ? ThermalShell.capture(player, event.getSource()) : null;
        VectorParry.Capture parryCapture = entity instanceof ServerPlayer player
                ? VectorParry.capture(player, event.getSource()) : null;
        InertialAnchor.Capture anchorCapture = entity instanceof ServerPlayer player
                ? InertialAnchor.capture(player, event.getSource()) : null;
        CirculationRelief.Capture circulationCapture = entity instanceof ServerPlayer player
                ? CirculationRelief.capture(player, event.getSource()) : null;
        AutonomousVectorCircle.Capture circleCapture = entity instanceof ServerPlayer player
                ? AutonomousVectorCircle.capture(player, event.getSource()) : null;
        var activeBefore = ACTIVE.get(entity);
        if (activeBefore != null && event.getSource() != activeBefore) activeBefore.rejected = true;
        if (!enabled(entity)) {
            NativeHurtFrame ordinaryFrame = FRAMES.get(entity);
            if (ordinaryFrame != null && ordinaryFrame.source == event.getSource()) ordinaryFrame.incoming = event;
            if (event.getSource() instanceof ExactSkillDamageSource source && boundedClosedBudget(source) && source.target == entity)
                observeOrdinaryFoldedDamage(event, source);
            return;
        }
        flushRecovery(entity);
        var active = ACTIVE.get(entity);
        if (active != null && event.getSource() != active) active.rejected = true;
        NativeHurtFrame frame = FRAMES.get(entity);
        if (frame != null && frame.source == event.getSource()) {
            frame.incoming = event;
            // NEW452's one native Post identity belongs to the hurt stack even when
            // inactive, rear-facing, unsupported, exhausted, or inside a nested hurt.
            // Refusing defense must not expose ordinary HP to same-source Post replay.
            if (autonomousVectorCircleBenefitsActive(entity)) frame.anchorPostGuard = true;
            if (!frame.poisoned && frame.parent == null && entity instanceof ServerPlayer player) {
                frame.zeroGap = armorCapture;
                frame.zeroGapOwnsHit = zeroGapBenefitsActive(player) && reviewedZeroGapSource(player, event.getSource())
                        && ZeroGapArmor.blocksOtherDefense(player);
                frame.curtain = curtainCapture;
                frame.scatterPlates = scatterCapture;
                frame.thermalShell = thermalCapture;
                frame.vectorParry = parryCapture;
                frame.inertialAnchor = anchorCapture;
                frame.circulationRelief = circulationCapture;
                frame.autonomousVectorCircle = circleCapture;
                if (circleCapture != null) frame.anchorPostGuard = true;
                if (circulationCapture != null) frame.anchorPostGuard = true;
                if (event.getSource() instanceof ExactSkillDamageSource exact && exact.target == entity
                        && (exact.dualPointBeam && exact.skill.equals("meltdowner.dual_point")
                            || exact.foldedMirrorBeam && exact.skill.equals("meltdowner.folded_mirror_array")
                            || exact.remoteScatterRelayPermit != null && exact.skill.equals("meltdowner.remote_scatter_relay")
                            || exact.disintegrationSweepBeam && exact.skill.equals("meltdowner.disintegration_sweep")
                            || exact.particleLaminateBeam && exact.skill.equals("meltdowner.particle_laminate")))
                    frame.scatterPlates = ScatterPlates.captureBeam(player, exact.player, exact.projectileOrigin,
                            exact.certifiedBeamEndpoint, exact.target);
                if (event.getSource() instanceof ExactSkillDamageSource exact && exact.target == entity
                        && (exact.dualPointBeam && exact.skill.equals("meltdowner.dual_point")
                            || exact.foldedMirrorBeam && exact.skill.equals("meltdowner.folded_mirror_array")
                            || exact.remoteScatterRelayPermit != null && exact.skill.equals("meltdowner.remote_scatter_relay")
                            || exact.disintegrationSweepBeam && exact.skill.equals("meltdowner.disintegration_sweep")))
                {
                    frame.curtain = DissipationCurtain.captureBeam(player, exact.player, exact.projectileOrigin,
                            exact.certifiedBeamEndpoint, exact.target);
                    frame.zeroGap = ZeroGapArmor.captureBeam(player, exact.player, exact.projectileOrigin,
                            exact.certifiedBeamEndpoint, exact.target);
                }
            }
            if (!frame.poisoned && frame.parent == null && entity instanceof ServerPlayer player
                    && reviewedElectricalSource(entity, event.getSource())) frame.grounding = GroundingReturn.capture(player);
            if (!frame.poisoned && frame.parent == null && entity instanceof ServerPlayer player
                    && reviewedPotentialBufferSource(entity, event.getSource()))
                frame.potentialBuffer = PotentialBuffer.capture(player, event.getSource().getSourcePosition(), player.getLookAngle());
            if (!frame.poisoned && frame.parent == null && entity instanceof ServerPlayer player
                    && reviewedMagneticDomainSource(entity, event.getSource()))
                frame.magneticDomain = MagneticDomain.capture(player, event.getSource().getSourcePosition(), player.getLookAngle());
        } else frame = null;
        NativeTrace trace = new NativeTrace(event.getSource(), HISTORY.get(entity), frame);
        // This callback is reached only after native comparison accepted. NeoForge's
        // patched actuallyHurt reads this container rather than its obsolete float arg.
        event.addReductionModifier(DamageContainer.Reduction.INVULNERABILITY, (container, reduction) -> {
            NativeHurtFrame transaction = trace.frame;
            if (transaction == null || !transaction.adaptCooldown) return reduction;
            if (reduction != transaction.nativePrevious || transaction.poisoned) {
                transaction.poisoned = true;
                if (event.getSource() instanceof ExactSkillDamageSource exact) exact.rejected = true;
                return reduction;
            }
            container.setNewDamage(transaction.residualWitness);
            transaction.residualInstalled = true;
            return 0; // Transport reset only for this reduction; lastHurt/timer fields are untouched.
        });
        // The observer runs after actual armor wear, before the recorded ARMOR reduction.
        // Ordinary hits also need a mapping: originalDamage is wrong after incoming edits.
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
            trace.beforeArmor = container.getNewDamage();
            trace.invulnerability = container.getReduction(DamageContainer.Reduction.INVULNERABILITY);
            if (trace.frame != null) trace.frame.sawArmor = true;
            if (event.getSource() instanceof ExactSkillDamageSource exact) exact.nativeStageReached = true;
            trace.previousTrace = TRACES.put(entity, trace);
            return reduction;
        });
        if (event.getSource() instanceof ExactSkillDamageSource source && source.target == entity) {
            event.addReductionModifier(DamageContainer.Reduction.ABSORPTION, (container, reduction) -> {
                source.absorptionObserved = true;
                source.beforeAbsorption = container.getNewDamage();
                source.absorptionPool = entity.getAbsorptionAmount();
                if (!source.prepared || source.rejected || !Float.isFinite(source.beforeAbsorption)
                        || source.beforeAbsorption < 0 || !Float.isFinite(source.absorptionPool)
                        || source.absorptionPool < 0 || source.absorptionPool > 2048
                        || source.beforeAbsorption != 0 && source.beforeAbsorption != source.nativePrepared
                        || reduction != Math.min(source.absorptionPool, source.beforeAbsorption)) {
                    source.rejected = true;
                    source.beforeAbsorption = 0;
                    container.setNewDamage(0);
                    return 0; // setReduction subtracts our result after this callback.
                }
                if (source.beforeAbsorption == 0) source.exactBeforeAbsorption = BigDecimal.ZERO; // Final late veto.
                source.expectedAbsorption = reduction;
                return reduction;
            });
        }
    }

    /** Bounded closed attempts on ordinary native targets are observed without enrolling those targets. */
    private static void observeOrdinaryFoldedDamage(LivingIncomingDamageEvent event, ExactSkillDamageSource source) {
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
            source.ordinaryBeforeArmor = container.getNewDamage();
            source.ordinaryArmorObserved = true;
            return reduction;
        });
        event.addReductionModifier(DamageContainer.Reduction.ABSORPTION, (container, reduction) -> {
            float before = container.getNewDamage(), pool = event.getEntity().getAbsorptionAmount();
            if (source.rejected || !source.ordinaryPrepared || !Float.isFinite(before) || before < 0
                    || before > source.ordinaryPreLimit || !Float.isFinite(pool) || pool < 0 || pool > 2048
                    || reduction != Math.min(pool, before)) {
                source.rejected = true; container.setNewDamage(0); return 0;
            }
            if (source.displacedProjectionPermit != null || source.crossSubstitutionPermit != null || source.shortInterceptionFallbackPermit != null
                    || source.spatialPincerCorridorPermit != null || source.multidimensionalLandingArrayPermit != null
                    || source.deflectionStrikePermit != null || source.shearWindbladePermit != null || source.yawPalmPermit != null || source.vectorCagePermit != null || source.shortInterceptionContact != null
                    || source.trajectoryReturnContact != null || source.trajectoryReturnAirHammerPermit != null || source.remoteScatterRelayPermit != null) {
                source.absorptionObserved = true;
                source.beforeAbsorption = before;
                source.expectedAbsorption = reduction;
            }
            return reduction;
        });
    }
    /** Validate pure native reductions, never replay armor wear, effect callbacks or enchantment hooks. */
    private static void prepareOrdinaryFoldedDamage(LivingDamageEvent.Pre event, ExactSkillDamageSource source) {
        LivingEntity entity = event.getEntity();
        try {
            float input = source.ordinaryBeforeArmor, pre = event.getNewDamage();
            if (source.rejected || source.ordinaryPrepared || !source.ordinaryArmorObserved
                    || !guardedStandardSource(source) || unsupportedReason(entity).isPresent()
                    || !Float.isFinite(input) || input < 0 || input > source.witness
                    || !Float.isFinite(pre) || pre < 0) throw new IllegalArgumentException("unsupported native folded stage");
            int armor = entity.getArmorValue();
            float toughness = (float)entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            int resistance = entity.hasEffect(MobEffects.DAMAGE_RESISTANCE) ? entity.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1 : 0;
            int protection = VanillaCombatContract.protectionPoints(entity);
            float afterArmor = input * (1F - Mth.clamp(armor - input / (2F + toughness / 4F), armor * .2F, 20F) / 25F);
            float armorReduction = input - afterArmor, afterArmorRecorded = input - armorReduction;
            float afterResistance = resistance == 0 ? afterArmorRecorded : Math.max(afterArmorRecorded * (25 - resistance * 5) / 25F, 0);
            float resistanceReduction = afterArmorRecorded - afterResistance, afterResistanceRecorded = afterArmorRecorded - resistanceReduction;
            float enchantReduction = protection == 0 || afterResistance <= 0 ? 0
                    : afterResistanceRecorded - afterResistance * (1F - Math.min(protection, 20) / 25F);
            float expected = afterResistanceRecorded - enchantReduction;
            var container = event.getContainer();
            if (container.getReduction(DamageContainer.Reduction.ARMOR) != armorReduction
                    || container.getReduction(DamageContainer.Reduction.MOB_EFFECTS) != resistanceReduction
                    || container.getReduction(DamageContainer.Reduction.ENCHANTMENTS) != enchantReduction
                    || container.getReduction(DamageContainer.Reduction.INNATE_RESISTANCE) != 0 || pre > expected)
                throw new IllegalArgumentException("positive native folded rewrite");
            source.ordinaryPreLimit = pre;
            source.ordinaryPrepared = true;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            source.rejected = true; event.setNewDamage(0);
        }
    }

    /** Reviewed controller geometry/context veto, including ordinary un-enrolled mobs. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void guardThunderNetIncoming(LivingIncomingDamageEvent event) {
        if (event.getSource() instanceof ExactSkillDamageSource source && boundedClosedBudget(source) && source.target == event.getEntity()
                && (!Float.isFinite(event.getAmount()) || event.getAmount() < 0 || event.getAmount() > source.witness)) {
            source.rejected = true; event.setCanceled(true); return;
        }
        if (event.getSource() instanceof ExactSkillDamageSource source && source.target == event.getEntity()
                && guardedControllerSkill(source) && source.attackGuard != null
                && !validAttackGuard(source.attackGuard)) {
            source.rejected = true;
            event.setCanceled(true);
        }
    }
    /** Final pre-HP veto; native side effects already completed are never replayed or refunded. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void guardThunderNetPre(LivingDamageEvent.Pre event) {
        if (event.getSource() instanceof ExactSkillDamageSource source && boundedClosedBudget(source) && source.target == event.getEntity()
                && !enabled(event.getEntity()) && (source.rejected || !source.ordinaryPrepared
                    || !Float.isFinite(event.getNewDamage()) || event.getNewDamage() < 0 || event.getNewDamage() > source.ordinaryPreLimit)) {
            source.rejected = true; event.setNewDamage(0); return;
        }
        if (event.getSource() instanceof ExactSkillDamageSource source && source.target == event.getEntity()
                && guardedControllerSkill(source) && source.attackGuard != null
                && !validAttackGuard(source.attackGuard)) {
            source.rejected = true;
            event.setNewDamage(0);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void prepareExactDamage(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (!(event.getSource() instanceof ExactSkillDamageSource source) || source.target != entity) return;
        if (!enabled(entity)) {
            if (boundedClosedBudget(source)) prepareOrdinaryFoldedDamage(event, source);
            return;
        }
        NativeTrace trace = TRACES.get(entity);
        try {
            if (source.rejected || source.prepared || trace == null || trace.source != source
                    || event.getOriginalDamage() != source.witness || unsupportedReason(entity).isPresent()
                    || unsupportedReason(source.player).isPresent() || !guardedStandardSource(source)) throw new IllegalArgumentException("unsupported stage");
            var container = event.getContainer();
            float blocked = container.getBlockedDamage(), cooldown = trace.invulnerability;
            if (!Float.isFinite(blocked) || blocked < 0 || blocked != 0 && blocked != source.witness
                    || !Float.isFinite(cooldown) || cooldown < 0 || !Float.isFinite(trace.beforeArmor) || trace.beforeArmor < 0) {
                throw new IllegalArgumentException("unsupported block/cooldown");
            }
            NativeHurtFrame frame = trace.frame;
            if (frame == null || frame.poisoned) throw new IllegalArgumentException("missing native transaction");
            boolean adapted = frame.adaptCooldown;
            float expectedInput;
            BigDecimal residual = source.raw;
            if (adapted) {
                if (!frame.residualInstalled || cooldown != 0 || !frame.comparisonAccepted
                        || frame.logicalIncoming == null || frame.logicalResidual == null) throw new IllegalArgumentException("invalid native residual seam");
                source.nativeIncoming = frame.nativeIncoming;
                source.rawForHistory = frame.logicalIncoming;
                expectedInput = frame.residualWitness;
                residual = frame.logicalResidual;
                source.historyValidated = trace.beforeArmor == expectedInput;
            } else {
                source.nativeIncoming = trace.beforeArmor + cooldown;
                source.rawForHistory = source.nativeIncoming == 0 ? BigDecimal.ZERO : source.raw;
                expectedInput = (source.witness - blocked) - cooldown;
                // Exact differential hits must have passed the admitted comparator seam.
                source.historyValidated = trace.beforeArmor == expectedInput && cooldown == 0;
                if (cooldown != 0) throw new IllegalArgumentException("unadapted exact cooldown");
                if (blocked != 0) residual = BigDecimal.ZERO;
            }
            float pre = event.getNewDamage();
            if (pre == 0) {
                source.exactBeforeAbsorption = BigDecimal.ZERO;
                source.nativePrepared = 0;
                source.prepared = true;
                return; // Complete native resistance or an earlier zero protection remains final.
            }
            if (trace.beforeArmor != expectedInput || !Float.isFinite(pre) || pre < 0) throw new IllegalArgumentException("incoming rewrite");
            int armor = entity.getArmorValue();
            float toughness = (float)entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            int resistance = entity.hasEffect(MobEffects.DAMAGE_RESISTANCE) ? entity.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1 : 0;
            int protection = VanillaCombatContract.protectionPoints(entity);
            // Replay only pure float arithmetic to validate native observations, never a
            // mutable EnchantmentHelper, reduction callback or damage hook a second time.
            float afterArmor = expectedInput * (1F - Mth.clamp(armor - expectedInput / (2F + toughness / 4F), armor * .2F, 20F) / 25F);
            float armorReduction = expectedInput - afterArmor;
            float afterArmorRecorded = expectedInput - armorReduction;
            float afterResistance = resistance == 0 ? afterArmorRecorded : Math.max(afterArmorRecorded * (25 - resistance * 5) / 25F, 0);
            float resistanceReduction = afterArmorRecorded - afterResistance;
            float afterResistanceRecorded = afterArmorRecorded - resistanceReduction;
            float enchantReduction = protection == 0 || afterResistance <= 0 ? 0
                    : afterResistanceRecorded - afterResistance * (1F - Math.min(protection, 20) / 25F);
            float expectedPre = afterResistanceRecorded - enchantReduction;
            if (container.getReduction(DamageContainer.Reduction.ARMOR) != armorReduction
                    || container.getReduction(DamageContainer.Reduction.MOB_EFFECTS) != resistanceReduction
                    || container.getReduction(DamageContainer.Reduction.ENCHANTMENTS) != enchantReduction
                    || container.getReduction(DamageContainer.Reduction.INNATE_RESISTANCE) != 0 || pre != expectedPre) {
                throw new IllegalArgumentException("positive native rewrite");
            }
            source.exactBeforeAbsorption = DamageMitigation.mitigate(residual,
                    new DefenseProfile(BigDecimal.valueOf(armor), decimal(toughness).stripTrailingZeros(), resistance, protection));
            source.nativePrepared = source.exactBeforeAbsorption.min(BigDecimal.valueOf(MAX_NATIVE_WITNESS)).floatValue();
            if (source.exactBeforeAbsorption.signum() > 0 && !(source.nativePrepared > 0)) throw new IllegalArgumentException("witness underflow");
            source.prepared = true;
            event.setNewDamage(source.nativePrepared);
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            if(Boolean.getBoolean("academy.qa.tpContactTrace")&&(source.spatialPincerCorridorPermit!=null||source.multidimensionalLandingArrayPermit!=null))
                tpContactDiagnostic(source.skill,"exact_pre_refused reason="+invalid.getMessage()+" priorRejected="+source.rejected+" prepared="+source.prepared+" trace="+(trace!=null)+" traceSource="+(trace!=null&&trace.source==source)+" frame="+(trace!=null&&trace.frame!=null)+" poisoned="+(trace!=null&&trace.frame!=null&&trace.frame.poisoned)+" beforeArmor="+(trace==null?Float.NaN:trace.beforeArmor)+" witness="+source.witness+" pre="+event.getNewDamage()+" armorReduction="+event.getContainer().getReduction(DamageContainer.Reduction.ARMOR),false);
            source.rejected = true;
            event.setNewDamage(0); // Refuse only this exact skill attempt before native absorption.
        }
    }

    private static boolean guardedStandardSource(DamageSource source) {
        return standardSource(source) && (!(source instanceof ExactSkillDamageSource exact) || validAttackGuard(exact.attackGuard));
    }
    private static boolean guardedControllerSkill(ExactSkillDamageSource source) {
        return source.remoteScatterRelayPermit != null && source.skill.equals("meltdowner.remote_scatter_relay")
                || source.shortInterceptionContact != null || source.trajectoryReturnContact != null
                || source.trajectoryReturnAirHammerPermit != null && source.skill.equals("vecmanip.trajectory_return_air")
                || source.skill.equals("electromaster.thunder_net") || source.skill.equals("electromaster.rail_thunder_array")
                || source.dualPointBeam && source.skill.equals("meltdowner.dual_point")
                || source.thermalShellPulse && source.skill.equals("meltdowner.thermal_shell")
                || source.foldedMirrorBeam && source.skill.equals("meltdowner.folded_mirror_array")
                || source.disintegrationSweepBeam && source.skill.equals("meltdowner.disintegration_sweep")
                || source.particleLaminateBeam && source.skill.equals("meltdowner.particle_laminate")
                || source.multicoreCanopyBeam && source.skill.equals("meltdowner.multicore_canopy")
                || source.displacedProjectionPermit != null && source.skill.equals("teleporter.displaced_projection")
                || source.crossSubstitutionPermit != null && source.skill.equals("teleporter.cross_substitution")
                || source.spatialPincerCorridorPermit != null && source.skill.equals("teleporter.spatial_pincer_corridor")
                || source.multidimensionalLandingArrayPermit != null && source.skill.equals("teleporter.multidimensional_landing_array")
                || source.shortInterceptionFallbackPermit != null && source.skill.equals("teleporter.short_interception")
                || source.deflectionStrikePermit != null && source.skill.equals("vecmanip.deflection_strike")
                || source.shearWindbladePermit != null && source.skill.equals("vecmanip.shear_windblade")
                || source.yawPalmPermit != null && source.skill.equals("vecmanip.yaw_palm")
                || source.vectorCagePermit != null && source.skill.equals("vecmanip.vector_cage");
    }
    private static boolean boundedPlanarBeam(ExactSkillDamageSource source) {
        return source.foldedMirrorBeam || source.disintegrationSweepBeam || source.particleLaminateBeam || source.multicoreCanopyBeam
                || source.remoteScatterRelayPermit != null;
    }
    /** Budget policing grants no planar beam identity or player-defense compatibility to a closed contact. */
    private static boolean boundedClosedBudget(ExactSkillDamageSource source) {
        return boundedPlanarBeam(source) || source.displacedProjectionPermit != null || source.crossSubstitutionPermit != null
                || source.shortInterceptionFallbackPermit != null || source.spatialPincerCorridorPermit != null || source.multidimensionalLandingArrayPermit != null
                || source.deflectionStrikePermit != null || source.shearWindbladePermit != null || source.yawPalmPermit != null || source.vectorCagePermit != null || source.shortInterceptionContact != null
                    || source.trajectoryReturnContact != null || source.trajectoryReturnAirHammerPermit != null;
    }
    /** Structural classification is also used after native death; never re-run a live-position guard there. */
    private static boolean standardSource(DamageSource source) {
        if (!ordinaryDefenseTags(source)) return false;
        if (source instanceof ExactSkillDamageSource relay && relay.remoteScatterRelayPermit != null) {
            var permit = relay.remoteScatterRelayPermit;
            return !relay.dualPointBeam && !relay.thermalShellPulse && !relay.foldedMirrorBeam
                    && !relay.disintegrationSweepBeam && !relay.particleLaminateBeam && !relay.multicoreCanopyBeam
                    && relay.displacedProjectionPermit == null && relay.crossSubstitutionPermit == null
                    && relay.shortInterceptionFallbackPermit == null && relay.spatialPincerCorridorPermit == null
                    && relay.multidimensionalLandingArrayPermit == null && relay.deflectionStrikePermit == null
                    && relay.shearWindbladePermit == null && relay.yawPalmPermit == null && relay.vectorCagePermit == null
                    && relay.trajectoryReturnAirHammerPermit == null && relay.shortInterceptionContact == null
                    && relay.trajectoryReturnContact == null && relay.skill.equals("meltdowner.remote_scatter_relay")
                    && relay.attackGuard != null && relay.player == permit.player() && relay.target == permit.target()
                    && relay.raw.signum() > 0 && relay.raw.compareTo(permit.damage()) <= 0
                    && DisplacedProjectionWorld.finite(relay.projectileOrigin)
                    && DisplacedProjectionWorld.finite(relay.certifiedBeamEndpoint)
                    && relay.projectileOrigin.equals(permit.finalOrigin()) && relay.certifiedBeamEndpoint.equals(permit.endpoint())
                    && source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                    && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_EXPLOSION)
                    && source.getDirectEntity() == relay.player && source.getEntity() == relay.player
                    && relay.projectileOrigin.equals(source.getSourcePosition())
                    && relay.projectileOrigin.equals(source.sourcePositionRaw());
        }
        if (source instanceof ExactSkillDamageSource returned && returned.trajectoryReturnContact != null) {
            var contact = returned.trajectoryReturnContact;
            var permit = contact.permit(); DamageSource nativeSource = contact.original;
            if (!exclusiveTrajectoryReturnSource(returned) || returned.trajectoryReturnAirHammerPermit != null
                    || !returned.skill.equals("vecmanip.trajectory_return_native") || returned.attackGuard == null
                    || returned.player != permit.controller() || returned.target != contact.target()
                    || !(hostileTarget(returned.target)) || !supported(returned.target)
                    || returned.projectileOrigin != null || returned.certifiedBeamEndpoint != null
                    || returned.raw.compareTo(permit.rawDamage()) != 0
                    || !source.is(DamageTypes.ARROW) || !source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                    || source.is(DamageTypeTags.IS_LIGHTNING)
                    || source.typeHolder() != nativeSource.typeHolder() || source.getDirectEntity() != permit.arrow()
                    || source.getEntity() != permit.shooter() || source.sourcePositionRaw() != nativeSource.sourcePositionRaw()
                    || !java.util.Objects.equals(source.getSourcePosition(), nativeSource.getSourcePosition())) return false;
            var resource = returned.target.getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/arrow.json"));
            return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                    && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
        }
        if (source instanceof ExactSkillDamageSource hammer && hammer.trajectoryReturnAirHammerPermit != null) {
            var permit = hammer.trajectoryReturnAirHammerPermit;
            if (!exclusiveTrajectoryReturnSource(hammer) || hammer.trajectoryReturnContact != null
                    || !hammer.skill.equals("vecmanip.trajectory_return_air") || hammer.attackGuard == null
                    || hammer.player != permit.player() || hammer.target != permit.target()
                    || !(hostileTarget(hammer.target)) || !supported(hammer.target)
                    || !DisplacedProjectionWorld.finite(hammer.projectileOrigin)
                    || !hammer.projectileOrigin.equals(permit.origin()) || hammer.certifiedBeamEndpoint != null
                    || !DisplacedProjectionWorld.finite(permit.endpoint())
                    || permit.origin().distanceToSqr(permit.endpoint()) > 40D * 40D
                    || hammer.raw.compareTo(permit.damage()) > 0
                    || !source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                    || source.is(DamageTypeTags.IS_LIGHTNING)
                    || source.getDirectEntity() != hammer.player || source.getEntity() != hammer.player
                    || !hammer.projectileOrigin.equals(source.getSourcePosition())
                    || !hammer.projectileOrigin.equals(source.sourcePositionRaw())) return false;
            var resource = hammer.target.getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/player_attack.json"));
            return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                    && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
        }
        if (source instanceof ExactSkillDamageSource strike && strike.deflectionStrikePermit != null) {
            var permit = strike.deflectionStrikePermit;
            if (strike.dualPointBeam || strike.thermalShellPulse || strike.foldedMirrorBeam || strike.disintegrationSweepBeam
                    || strike.particleLaminateBeam || strike.multicoreCanopyBeam || strike.displacedProjectionPermit != null
                    || strike.crossSubstitutionPermit != null || strike.shortInterceptionFallbackPermit != null
                    || strike.spatialPincerCorridorPermit != null || strike.multidimensionalLandingArrayPermit != null
                    || strike.shearWindbladePermit != null || strike.yawPalmPermit != null || strike.vectorCagePermit != null || strike.shortInterceptionContact != null || strike.certifiedBeamEndpoint != null
                    || !strike.skill.equals("vecmanip.deflection_strike") || strike.attackGuard == null
                    || strike.player != permit.player() || strike.target != permit.target()
                    || !(hostileTarget(strike.target))
                    || strike.target instanceof net.minecraft.world.entity.player.Player || !supported(strike.target)
                    || !DisplacedProjectionWorld.finite(strike.projectileOrigin) || !strike.projectileOrigin.equals(permit.origin())
                    || strike.raw.compareTo(permit.damage()) > 0
                    || !source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                    || source.is(DamageTypeTags.IS_LIGHTNING)
                    || source.getDirectEntity() != strike.player || source.getEntity() != strike.player
                    || !strike.projectileOrigin.equals(source.getSourcePosition())
                    || !strike.projectileOrigin.equals(source.sourcePositionRaw())) return false;
            var resource = strike.target.getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/player_attack.json"));
            return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                    && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
        }
        if (source instanceof ExactSkillDamageSource blade && blade.shearWindbladePermit != null) {
            var permit = blade.shearWindbladePermit;
            if (blade.dualPointBeam || blade.thermalShellPulse || blade.foldedMirrorBeam || blade.disintegrationSweepBeam
                    || blade.particleLaminateBeam || blade.multicoreCanopyBeam || blade.displacedProjectionPermit != null
                    || blade.crossSubstitutionPermit != null || blade.shortInterceptionFallbackPermit != null
                    || blade.spatialPincerCorridorPermit != null || blade.multidimensionalLandingArrayPermit != null
                    || blade.deflectionStrikePermit != null || blade.yawPalmPermit != null || blade.vectorCagePermit != null || blade.shortInterceptionContact != null || blade.certifiedBeamEndpoint != null
                    || !blade.skill.equals("vecmanip.shear_windblade") || blade.attackGuard == null
                    || blade.player != permit.player() || blade.target != permit.target()
                    || !(hostileTarget(blade.target))
                    || blade.target instanceof net.minecraft.world.entity.player.Player || !supported(blade.target)
                    || !DisplacedProjectionWorld.finite(blade.projectileOrigin) || !blade.projectileOrigin.equals(permit.origin())
                    || blade.raw.compareTo(permit.damage()) > 0
                    || !source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                    || source.is(DamageTypeTags.IS_LIGHTNING)
                    || source.getDirectEntity() != blade.player || source.getEntity() != blade.player
                    || !blade.projectileOrigin.equals(source.getSourcePosition())
                    || !blade.projectileOrigin.equals(source.sourcePositionRaw())) return false;
            var resource = blade.target.getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/player_attack.json"));
            return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                    && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
        }
        if (source instanceof ExactSkillDamageSource palm && palm.yawPalmPermit != null) {
            var permit = palm.yawPalmPermit;
            if (palm.dualPointBeam || palm.thermalShellPulse || palm.foldedMirrorBeam || palm.disintegrationSweepBeam
                    || palm.particleLaminateBeam || palm.multicoreCanopyBeam || palm.displacedProjectionPermit != null
                    || palm.crossSubstitutionPermit != null || palm.shortInterceptionFallbackPermit != null
                    || palm.spatialPincerCorridorPermit != null || palm.multidimensionalLandingArrayPermit != null
                    || palm.vectorCagePermit != null || palm.shearWindbladePermit != null || palm.deflectionStrikePermit != null || palm.shortInterceptionContact != null || palm.certifiedBeamEndpoint != null
                    || !palm.skill.equals("vecmanip.yaw_palm") || palm.attackGuard == null
                    || palm.player != permit.player() || palm.target != permit.target()
                    || !(hostileTarget(palm.target))
                    || palm.target instanceof net.minecraft.world.entity.player.Player || !supported(palm.target)
                    || !DisplacedProjectionWorld.finite(palm.projectileOrigin) || !palm.projectileOrigin.equals(permit.origin())
                    || palm.raw.compareTo(permit.damage()) > 0
                    || !source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                    || source.is(DamageTypeTags.IS_LIGHTNING)
                    || source.getDirectEntity() != palm.player || source.getEntity() != palm.player
                    || !palm.projectileOrigin.equals(source.getSourcePosition())
                    || !palm.projectileOrigin.equals(source.sourcePositionRaw())) return false;
            var resource = palm.target.getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/player_attack.json"));
            return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                    && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
        }
        if (source instanceof ExactSkillDamageSource cage && cage.vectorCagePermit != null) {
            var permit = cage.vectorCagePermit;
            if (cage.dualPointBeam || cage.thermalShellPulse || cage.foldedMirrorBeam || cage.disintegrationSweepBeam
                    || cage.particleLaminateBeam || cage.multicoreCanopyBeam || cage.displacedProjectionPermit != null
                    || cage.crossSubstitutionPermit != null || cage.shortInterceptionFallbackPermit != null
                    || cage.spatialPincerCorridorPermit != null || cage.multidimensionalLandingArrayPermit != null
                    || cage.yawPalmPermit != null || cage.trajectoryReturnContact != null || cage.trajectoryReturnAirHammerPermit != null
                    || cage.shearWindbladePermit != null || cage.deflectionStrikePermit != null || cage.shortInterceptionContact != null || cage.certifiedBeamEndpoint != null
                    || !cage.skill.equals("vecmanip.vector_cage") || cage.attackGuard == null
                    || cage.player != permit.player() || cage.target != permit.target()
                    || !(hostileTarget(cage.target))
                    || cage.target instanceof net.minecraft.world.entity.player.Player || !supported(cage.target)
                    || !DisplacedProjectionWorld.finite(cage.projectileOrigin) || !cage.projectileOrigin.equals(permit.origin())
                    || cage.raw.compareTo(permit.damage()) > 0
                    || !source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                    || source.is(DamageTypeTags.IS_LIGHTNING)
                    || source.getDirectEntity() != cage.player || source.getEntity() != cage.player
                    || !cage.projectileOrigin.equals(source.getSourcePosition())
                    || !cage.projectileOrigin.equals(source.sourcePositionRaw())) return false;
            var resource = cage.target.getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/player_attack.json"));
            return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                    && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
        }
        if (source instanceof ExactSkillDamageSource interception && interception.shortInterceptionContact != null) {
            var nativeSource = interception.shortInterceptionContact.original;
            var permit = interception.shortInterceptionContact.permit();
            if (!interception.skill.equals("teleporter.short_interception_native") || interception.player != permit.controller()
                    || interception.target != permit.target() || interception.attackGuard == null
                    || interception.displacedProjectionPermit != null || interception.crossSubstitutionPermit != null || interception.shortInterceptionFallbackPermit != null
                    || interception.spatialPincerCorridorPermit != null || interception.multidimensionalLandingArrayPermit != null
                    || interception.projectileOrigin != null || interception.certifiedBeamEndpoint != null
                    || !source.is(DamageTypes.ARROW) || !source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                    || source.typeHolder() != nativeSource.typeHolder() || source.getDirectEntity() != permit.arrow()
                    || source.getEntity() != permit.shooter() || source.sourcePositionRaw() != nativeSource.sourcePositionRaw()
                    || !java.util.Objects.equals(source.getSourcePosition(), nativeSource.getSourcePosition())) return false;
            var resource = interception.target.getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/arrow.json"));
            return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                    && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
        }
        if (source instanceof ExactSkillDamageSource projection && projection.displacedProjectionPermit != null) {
            DisplacedProjection.EmissionPermit permit = projection.displacedProjectionPermit;
            DisplacedNeedleEntity needle = permit.needle();
            if (projection.dualPointBeam || projection.thermalShellPulse || projection.foldedMirrorBeam
                    || projection.disintegrationSweepBeam || projection.particleLaminateBeam || projection.multicoreCanopyBeam
                    || projection.crossSubstitutionPermit != null || projection.spatialPincerCorridorPermit != null || projection.multidimensionalLandingArrayPermit != null
                    || !projection.skill.equals("teleporter.displaced_projection") || projection.attackGuard == null
                    || projection.projectileOrigin == null || projection.certifiedBeamEndpoint != null
                    || needle == null || needle.getClass() != DisplacedNeedleEntity.class
                    || needle.getType() != TeleporterNeedleEntities.DISPLACED_NEEDLE.get()
                    || projection.player != permit.player() || projection.target != permit.target()
                    || !projection.projectileOrigin.equals(permit.origin())
                    || !source.is(DamageTypes.THROWN) || !source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                    || source.getDirectEntity() != needle || source.getEntity() != projection.player
                    || !projection.projectileOrigin.equals(source.getSourcePosition())
                    || !projection.projectileOrigin.equals(source.sourcePositionRaw())) return false;
            var resource = projection.target.getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/thrown.json"));
            return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                    && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
        }
        if (source instanceof ExactSkillDamageSource cross && cross.crossSubstitutionPermit != null) {
            CrossSubstitution.EmissionPermit permit = cross.crossSubstitutionPermit;
            DisplacedNeedleEntity needle = permit.needle();
            if (cross.dualPointBeam || cross.thermalShellPulse || cross.foldedMirrorBeam
                    || cross.disintegrationSweepBeam || cross.particleLaminateBeam || cross.multicoreCanopyBeam
                    || cross.displacedProjectionPermit != null || cross.spatialPincerCorridorPermit != null || cross.multidimensionalLandingArrayPermit != null
                    || !cross.skill.equals("teleporter.cross_substitution") || cross.attackGuard == null
                    || cross.projectileOrigin == null || cross.certifiedBeamEndpoint != null
                    || needle == null || needle.getClass() != DisplacedNeedleEntity.class
                    || needle.getType() != TeleporterNeedleEntities.CROSS_NEEDLE.get()
                    || cross.player != permit.player() || cross.target != permit.target()
                    || !cross.projectileOrigin.equals(permit.origin())
                    || !source.is(DamageTypes.THROWN) || !source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                    || source.getDirectEntity() != needle || source.getEntity() != cross.player
                    || !cross.projectileOrigin.equals(source.getSourcePosition())
                    || !cross.projectileOrigin.equals(source.sourcePositionRaw())) return false;
            var resource = cross.target.getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/thrown.json"));
            return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                    && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
        }
        if (source instanceof ExactSkillDamageSource cross && cross.shortInterceptionFallbackPermit != null) {
            ShortInterception.FallbackPermit permit = cross.shortInterceptionFallbackPermit;
            DisplacedNeedleEntity needle = permit.needle();
            if (cross.dualPointBeam || cross.thermalShellPulse || cross.foldedMirrorBeam
                    || cross.disintegrationSweepBeam || cross.particleLaminateBeam || cross.multicoreCanopyBeam
                    || cross.displacedProjectionPermit != null || cross.crossSubstitutionPermit != null || cross.shortInterceptionContact != null
                    || cross.spatialPincerCorridorPermit != null || cross.multidimensionalLandingArrayPermit != null
                    || !cross.skill.equals("teleporter.short_interception") || cross.attackGuard == null
                    || cross.projectileOrigin == null || cross.certifiedBeamEndpoint != null
                    || needle == null || needle.getClass() != DisplacedNeedleEntity.class
                    || needle.getType() != TeleporterNeedleEntities.INTERCEPTION_NEEDLE.get()
                    || cross.player != permit.player() || cross.target != permit.target()
                    || !cross.projectileOrigin.equals(permit.origin())
                    || !source.is(DamageTypes.THROWN) || !source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                    || source.getDirectEntity() != needle || source.getEntity() != cross.player
                    || !cross.projectileOrigin.equals(source.getSourcePosition())
                    || !cross.projectileOrigin.equals(source.sourcePositionRaw())) return false;
            var resource = cross.target.getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/thrown.json"));
            return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                    && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
        }
        if (source instanceof ExactSkillDamageSource corridor && corridor.spatialPincerCorridorPermit != null) {
            SpatialPincerCorridor.EmissionPermit permit = corridor.spatialPincerCorridorPermit;
            DisplacedNeedleEntity needle = permit.needle();
            if (corridor.dualPointBeam || corridor.thermalShellPulse || corridor.foldedMirrorBeam
                    || corridor.disintegrationSweepBeam || corridor.particleLaminateBeam || corridor.multicoreCanopyBeam
                    || corridor.displacedProjectionPermit != null || corridor.crossSubstitutionPermit != null
                    || corridor.shortInterceptionFallbackPermit != null || corridor.shortInterceptionContact != null || corridor.multidimensionalLandingArrayPermit != null
                    || !corridor.skill.equals("teleporter.spatial_pincer_corridor") || corridor.attackGuard == null
                    || corridor.projectileOrigin == null || corridor.certifiedBeamEndpoint != null
                    || needle == null || needle.getClass() != DisplacedNeedleEntity.class
                    || needle.getType() != TeleporterNeedleEntities.CORRIDOR_NEEDLE.get()
                    || corridor.player != permit.player() || corridor.target != permit.target()
                    || !corridor.projectileOrigin.equals(permit.origin())
                    || !source.is(DamageTypes.THROWN) || !source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                    || source.getDirectEntity() != needle || source.getEntity() != corridor.player
                    || !corridor.projectileOrigin.equals(source.getSourcePosition())
                    || !corridor.projectileOrigin.equals(source.sourcePositionRaw())) return false;
            var resource = corridor.target.getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/thrown.json"));
            return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                    && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
        }
        if (source instanceof ExactSkillDamageSource array && array.multidimensionalLandingArrayPermit != null) {
            var permit = array.multidimensionalLandingArrayPermit; BorrowedNeedleEntity needle = permit.needle();
            if (array.dualPointBeam || array.thermalShellPulse || array.foldedMirrorBeam || array.disintegrationSweepBeam
                    || array.particleLaminateBeam || array.multicoreCanopyBeam || array.displacedProjectionPermit != null
                    || array.crossSubstitutionPermit != null || array.shortInterceptionFallbackPermit != null
                    || array.spatialPincerCorridorPermit != null || array.shortInterceptionContact != null
                    || !array.skill.equals("teleporter.multidimensional_landing_array") || array.attackGuard == null
                    || array.projectileOrigin == null || array.certifiedBeamEndpoint != null
                    || needle == null || needle.getClass() != BorrowedNeedleEntity.class
                    || needle.getType() != TeleporterNeedleEntities.BORROWED_NEEDLE.get()
                    || array.player != permit.player() || array.target != permit.target() || !array.projectileOrigin.equals(permit.origin())
                    || !source.is(DamageTypes.THROWN) || !source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                    || source.getDirectEntity() != needle || source.getEntity() != array.player
                    || !array.projectileOrigin.equals(source.getSourcePosition()) || !array.projectileOrigin.equals(source.sourcePositionRaw())) return false;
            var resource = array.target.getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/thrown.json"));
            return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                    && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
        }
        if (source instanceof ExactSkillDamageSource canopy && canopy.multicoreCanopyBeam) {
            return !canopy.dualPointBeam && !canopy.thermalShellPulse && !canopy.foldedMirrorBeam
                    && !canopy.disintegrationSweepBeam && !canopy.particleLaminateBeam
                    && canopy.skill.equals("meltdowner.multicore_canopy") && canopy.attackGuard != null
                    && canopy.projectileOrigin != null && canopy.certifiedBeamEndpoint != null
                    && source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                    && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_EXPLOSION)
                    && source.getDirectEntity() == canopy.player && source.getEntity() == canopy.player
                    && canopy.projectileOrigin.equals(source.getSourcePosition())
                    && canopy.projectileOrigin.equals(source.sourcePositionRaw());
        }
        if (source instanceof ExactSkillDamageSource laminate && laminate.particleLaminateBeam) {
            return !laminate.dualPointBeam && !laminate.thermalShellPulse && !laminate.foldedMirrorBeam && !laminate.disintegrationSweepBeam
                    && laminate.skill.equals("meltdowner.particle_laminate") && laminate.attackGuard != null
                    && laminate.projectileOrigin != null && laminate.certifiedBeamEndpoint != null
                    && source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                    && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_EXPLOSION)
                    && source.getDirectEntity() == laminate.player && source.getEntity() == laminate.player
                    && laminate.projectileOrigin.equals(source.getSourcePosition())
                    && laminate.projectileOrigin.equals(source.sourcePositionRaw());
        }
        if (source instanceof ExactSkillDamageSource sweep && sweep.disintegrationSweepBeam) {
            return !sweep.dualPointBeam && !sweep.thermalShellPulse && !sweep.foldedMirrorBeam
                    && sweep.skill.equals("meltdowner.disintegration_sweep") && sweep.attackGuard != null
                    && sweep.projectileOrigin != null && sweep.certifiedBeamEndpoint != null
                    && source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                    && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_EXPLOSION)
                    && source.getDirectEntity() == sweep.player && source.getEntity() == sweep.player
                    && sweep.projectileOrigin.equals(source.getSourcePosition())
                    && sweep.projectileOrigin.equals(source.sourcePositionRaw());
        }
        if (source instanceof ExactSkillDamageSource folded && folded.foldedMirrorBeam) {
            return !folded.dualPointBeam && !folded.thermalShellPulse
                    && folded.skill.equals("meltdowner.folded_mirror_array") && folded.attackGuard != null
                    && folded.projectileOrigin != null && folded.certifiedBeamEndpoint != null
                    && source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                    && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_EXPLOSION)
                    && source.getDirectEntity() == folded.player && source.getEntity() == folded.player
                    && folded.projectileOrigin.equals(source.getSourcePosition())
                    && folded.projectileOrigin.equals(source.sourcePositionRaw());
        }
        if (source instanceof ExactSkillDamageSource pulse && pulse.thermalShellPulse) {
            return !pulse.dualPointBeam && pulse.skill.equals("meltdowner.thermal_shell") && pulse.attackGuard != null
                    && pulse.projectileOrigin != null && pulse.certifiedBeamEndpoint != null
                    && source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                    && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_EXPLOSION)
                    && source.getDirectEntity() == pulse.player && source.getEntity() == pulse.player
                    && pulse.projectileOrigin.equals(source.getSourcePosition())
                    && pulse.projectileOrigin.equals(source.sourcePositionRaw());
        }
        if (source instanceof ExactSkillDamageSource beam && beam.dualPointBeam) {
            return beam.skill.equals("meltdowner.dual_point") && beam.attackGuard != null && beam.projectileOrigin != null
                    && source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                    && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_EXPLOSION)
                    && source.getDirectEntity() == beam.player && source.getEntity() == beam.player
                    && beam.projectileOrigin.equals(source.getSourcePosition());
        }
        if (source.is(DamageTypes.PLAYER_ATTACK)) return !(source instanceof ExactSkillDamageSource exact)
                || exact.projectileOrigin == null && !exact.skill.equals("meltdowner.dual_point")
                    && !exact.skill.equals("meltdowner.thermal_shell")
                    && !exact.skill.equals("meltdowner.folded_mirror_array")
                    && !exact.skill.equals("meltdowner.remote_scatter_relay")
                    && !exact.skill.equals("meltdowner.disintegration_sweep")
                    && !exact.skill.equals("meltdowner.particle_laminate")
                    && !exact.skill.equals("meltdowner.multicore_canopy")
                    && !exact.skill.equals("teleporter.displaced_projection")
                    && !exact.skill.equals("teleporter.cross_substitution")
                    && !exact.skill.equals("teleporter.spatial_pincer_corridor")
                    && !exact.skill.equals("teleporter.multidimensional_landing_array")
                    && !exact.skill.equals("teleporter.short_interception")
                    && !exact.skill.equals("teleporter.short_interception_native")
                    && !exact.skill.equals("vecmanip.deflection_strike")
                    && !exact.skill.equals("vecmanip.shear_windblade")
                    && !exact.skill.equals("vecmanip.yaw_palm")
                    && !exact.skill.equals("vecmanip.vector_cage")
                    && !exact.skill.equals("vecmanip.trajectory_return_native")
                    && !exact.skill.equals("vecmanip.trajectory_return_air")
                    && (!exact.skill.equals("electromaster.rail_thunder_array") || exact.attackGuard != null);
        if (!(source instanceof ExactSkillDamageSource exact) || exact.projectileOrigin == null
                || !(exact.skill.equals("electromaster.iron_sand_lance")
                    || exact.skill.equals("electromaster.rail_thunder_array") && exact.attackGuard != null)
                || !source.is(DamageTypes.THROWN)
                || !source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_EXPLOSION) || source.getDirectEntity() != null
                || source.getEntity() != exact.player || !exact.projectileOrigin.equals(source.getSourcePosition())) return false;
        var resource = exact.target.getServer().getResourceManager().getResource(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/thrown.json"));
        return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                && resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")));
    }
    /** Neither NEW431 branch may inherit another skill's damage or defense authority. */
    private static boolean exclusiveTrajectoryReturnSource(ExactSkillDamageSource source) {
        return !source.dualPointBeam && !source.thermalShellPulse && !source.foldedMirrorBeam
                && !source.disintegrationSweepBeam && !source.particleLaminateBeam && !source.multicoreCanopyBeam
                && source.shortInterceptionContact == null && source.displacedProjectionPermit == null
                && source.crossSubstitutionPermit == null && source.shortInterceptionFallbackPermit == null
                && source.spatialPincerCorridorPermit == null && source.multidimensionalLandingArrayPermit == null
                && source.deflectionStrikePermit == null && source.shearWindbladePermit == null && source.yawPalmPermit == null && source.vectorCagePermit == null;
    }
    private static boolean ordinaryDefenseTags(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_ARMOR)
                && !source.is(DamageTypeTags.BYPASSES_RESISTANCE) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
                && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && !source.is(DamageTypeTags.BYPASSES_SHIELD)
                && !source.is(DamageTypeTags.BYPASSES_COOLDOWN) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !source.is(DamageTypeTags.IS_FREEZING) && !source.is(DamageTypeTags.DAMAGES_HELMET);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (!serverThread(entity)) return;
        boolean relayPost = event.getSource() instanceof ExactSkillDamageSource relay && relay.remoteScatterRelayPermit != null;
        if (relayPost) {
            ExactSkillDamageSource relay = (ExactSkillDamageSource)event.getSource();
            NativeHurtFrame frame = FRAMES.get(entity);
            if (!relay.remoteScatterRelayCommitAuthorized || !relay.remoteScatterRelayPermit.paid()
                    || relay.remoteScatterRelayPostObserved || relay.rejected || relay.target != entity
                    || ACTIVE.get(entity) != relay || frame == null || frame.source != relay
                    || frame.parent != null || frame.poisoned || !frame.anchorPostGuard
                    || frame.incoming == null || frame.incoming.isCanceled()
                    || !AnchorDamagePostProof.matches(event)) return;
            relay.remoteScatterRelayPostObserved = true;
        }
        // NEW431 may settle only after the authenticated native pre-HP commit seam.
        // Ignore premature notifications before touching healing, traces, or resource balances.
        if (event.getSource() instanceof ExactSkillDamageSource exact
                && (exact.trajectoryReturnContact != null || exact.trajectoryReturnAirHammerPermit != null)
                && !exact.trajectoryReturnCommitAuthorized) return;
        // Learned NEW432 scopes this additional entry proof independently of toggle/UI state.
        // Every legitimate enrolled hurt (including unsupported source types) owns a real frame.
        // Late or fabricated notifications outside that stack cannot revive a spent receipt.
        if (circulationReliefBenefitsActive(entity) || autonomousVectorCircleBenefitsActive(entity)) {
            NativeHurtFrame current = FRAMES.get(entity);
            if (current == null || current.source != event.getSource()) return;
        }
        // A synthetic same-source Post must neither spend this landing receipt nor consume its trace.
        if (!relayPost && anchorPostGuardPending(entity) && !AnchorDamagePostProof.matches(event) && anchorPostGuardPending(entity)) return;
        flushHealing(entity);
        var optional = read(entity);
        if (optional.isEmpty()) return;
        ExactCombatState previous = optional.get();
        float nativeDamage = event.getNewDamage();
        NativeTrace trace = TRACES.get(entity);
        if (trace != null && trace.source == event.getSource()) {
            if (trace.previousTrace == null) TRACES.remove(entity); else TRACES.put(entity, trace.previousTrace);
        } else trace = null;
        // An unbounded hit is an overkill (held at the ceiling below); only NaN and negative amounts are not damage at all.
        if (Float.isNaN(nativeDamage) || nativeDamage < 0) { rejectPostDamage(entity, event, previous); return; }
        BigDecimal exactDamage = Float.isInfinite(nativeDamage) ? CombatAmounts.CAP : decimal(nativeDamage);
        if (event.getSource() instanceof ExactSkillDamageSource source && source.target == entity) {
            float absorbed = event.getReduction(DamageContainer.Reduction.ABSORPTION);
            if (!source.prepared || !source.absorptionObserved || source.accounted || source.rejected
                    || !Float.isFinite(absorbed) || absorbed != source.expectedAbsorption
                    || nativeDamage != source.beforeAbsorption - absorbed) {
                rejectPostDamage(entity, event, previous); return;
            }
            source.accounted = true;
            exactDamage = source.exactBeforeAbsorption.subtract(decimal(absorbed)).max(BigDecimal.ZERO);
        }
        // Native /kill explicitly requests death, rather than a scalable combat amount.
        // Its vanilla GENERIC_KILL source still goes through native cancellation and death hooks.
        if (event.getSource().is(DamageTypes.GENERIC_KILL) && nativeDamage > 0) exactDamage = previous.health();
        // Reject rather than silently truncate an amplification beyond the per-hit cap.
        // Native armor/absorption/stat side effects have already happened and cannot be rolled back.
        // Beyond the ceiling is overkill, not an invalid hit: hold it at the ceiling, which defeats any unprotected health pool.
        try { exactDamage = CombatAmounts.requireAmount(CombatAmounts.overkillClamp(exactDamage.stripTrailingZeros()), "native adapted damage"); }
        catch (IllegalArgumentException invalid) { rejectPostDamage(entity, event, previous); return; }
        GroundingReturnRules.Result grounding = null;
        PotentialBufferRules.Result potentialBuffer = null;
        MagneticDomainShieldRules.Result magneticDomain = null;
        ScatterPlateStorage.Contact scatterPlates = null;
        ThermalShellRules.Result thermalShell = null;
        VectorParryRules.Result vectorParry = null;
        VectorParryRules.Result circulationRelief = null;
        AutonomousVectorCircleRules.Result autonomousCircle = null;
        boolean circleAttempted = false;
        Runnable circleNotifications = null, circleVisual = null;
        Runnable circulationNotifications = null, circulationVisual = null;
        InertialAnchorRules.Result inertialAnchor = null;
        Runnable anchorNotifications = null, anchorVisual = null;
        DissipationCurtainShieldRules.Result curtain = null;
        ZeroGapArmorShares.Contact zeroGap = null;
        ZeroGapArmorStorage.Capture zeroGapStorage = null;
        NativeHurtFrame groundedFrame = trace == null ? null : trace.frame;
        if (entity instanceof ServerPlayer player && electromasterBenefitsActive(player)
                && groundedFrame != null && !groundedFrame.poisoned
                && groundedFrame.source == event.getSource() && groundedFrame.incoming != null
                && !groundedFrame.incoming.isCanceled() && reviewedElectricalSource(entity, event.getSource())) {
            grounding = GroundingReturn.plan(player, groundedFrame.grounding, previous, exactDamage);
        }
        // Grounding has sole precedence. A hit never debits the shared pool twice.
        if (grounding == null && entity instanceof ServerPlayer player && electromasterBenefitsActive(player)
                && groundedFrame != null && !groundedFrame.poisoned
                && groundedFrame.source == event.getSource() && groundedFrame.incoming != null
                && !groundedFrame.incoming.isCanceled())
            potentialBuffer = PotentialBuffer.plan(player, groundedFrame.potentialBuffer, previous, exactDamage);
        // Grounding > Potential Buffer > Domain. A higher defense's partial exhaustion never falls through.
        if (grounding == null && potentialBuffer == null && entity instanceof ServerPlayer player && electromasterBenefitsActive(player)
                && groundedFrame != null && !groundedFrame.poisoned && groundedFrame.source == event.getSource()
                && groundedFrame.incoming != null && !groundedFrame.incoming.isCanceled()
                && reviewedMagneticDomainSource(entity, event.getSource())
                && MagneticDomain.matches(player, groundedFrame.magneticDomain))
            magneticDomain = MagneticDomainReserve.plan(player, previous, exactDamage);
        boolean armorOwnsHit = groundedFrame != null && groundedFrame.zeroGapOwnsHit;
        if (grounding == null && potentialBuffer == null && magneticDomain == null
                && entity instanceof ServerPlayer player && zeroGapBenefitsActive(player)
                && groundedFrame != null && !groundedFrame.poisoned && groundedFrame.source == event.getSource()
                && groundedFrame.incoming != null && !groundedFrame.incoming.isCanceled()
                && reviewedZeroGapSource(player, event.getSource()) && ZeroGapArmor.matches(player, groundedFrame.zeroGap)) {
            zeroGapStorage = ZeroGapArmorStorage.capture(player).orElse(null);
            if (zeroGapStorage != null) zeroGap = ZeroGapArmor.plan(player, groundedFrame.zeroGap, zeroGapStorage, exactDamage);
        }
        // A genuine NEW242 crossing owns this hit; exhausted remainder never falls through.
        if (!armorOwnsHit && zeroGap == null && grounding == null && potentialBuffer == null && magneticDomain == null
                && entity instanceof ServerPlayer player && curtainBenefitsActive(player)
                && groundedFrame != null && !groundedFrame.poisoned && groundedFrame.source == event.getSource()
                && groundedFrame.incoming != null && !groundedFrame.incoming.isCanceled()
                && DissipationCurtain.matches(player, groundedFrame.curtain))
            curtain = DissipationCurtain.plan(player, groundedFrame.curtain, previous, exactDamage);
        // NEW212 is MD-only. Its contacted share is the entire shield allowance for this transaction.
        if (!armorOwnsHit && zeroGap == null && grounding == null && potentialBuffer == null && magneticDomain == null && curtain == null
                && entity instanceof ServerPlayer player && scatterPlateBenefitsActive(player)
                && groundedFrame != null && !groundedFrame.poisoned && groundedFrame.source == event.getSource()
                && groundedFrame.incoming != null && !groundedFrame.incoming.isCanceled()
                && ScatterPlates.matches(player, groundedFrame.scatterPlates))
            scatterPlates = ScatterPlateStorage.plan(player, previous, groundedFrame.scatterPlates.plateIndex(),
                    exactDamage, groundedFrame.scatterPlates.ceiling());
        // NEW212 and NEW222 have disjoint source contracts. Explicit precedence also
        // prevents any future overlap from debiting the shared base S a second time.
        if (!armorOwnsHit && zeroGap == null && grounding == null && potentialBuffer == null && magneticDomain == null && curtain == null && scatterPlates == null
                && entity instanceof ServerPlayer player && thermalShellBenefitsActive(player)
                && groundedFrame != null && !groundedFrame.poisoned && groundedFrame.source == event.getSource()
                && groundedFrame.incoming != null && !groundedFrame.incoming.isCanceled()
                && ThermalShell.matches(player, groundedFrame.thermalShell))
            thermalShell = ThermalShell.plan(player, groundedFrame.thermalShell, previous, exactDamage);
        // NEW412 is vector-only and cannot fall through to another category's shield layer.
        if (entity instanceof ServerPlayer player && vectorParryBenefitsActive(player)
                && groundedFrame != null && !groundedFrame.poisoned && groundedFrame.parent == null
                && groundedFrame.source == event.getSource() && groundedFrame.incoming != null
                && !groundedFrame.incoming.isCanceled() && VectorParry.matches(player, groundedFrame.vectorParry))
            vectorParry = VectorParry.plan(player, groundedFrame.vectorParry, previous, exactDamage);
        if (entity instanceof ServerPlayer player && inertialAnchorBenefitsActive(player)
                && groundedFrame != null && groundedFrame.parent == null && !groundedFrame.poisoned
                && groundedFrame.source == event.getSource() && groundedFrame.incoming != null
                && !groundedFrame.incoming.isCanceled() && groundedFrame.anchorFall != null
                && groundedFrame.anchorFall.valid(player, event.getSource()) && groundedFrame.anchorFall.claim())
            inertialAnchor = InertialAnchor.planFall(player, previous, exactDamage);
        if (vectorParry == null && inertialAnchor == null
                && entity instanceof ServerPlayer player && circulationReliefBenefitsActive(player)
                && groundedFrame != null && groundedFrame.parent == null && !groundedFrame.poisoned
                && groundedFrame.source == event.getSource() && groundedFrame.incoming != null
                && !groundedFrame.incoming.isCanceled() && CirculationRelief.matches(player, groundedFrame.circulationRelief))
            circulationRelief = CirculationRelief.plan(player, groundedFrame.circulationRelief, previous, exactDamage);
        if (!armorOwnsHit && grounding == null && potentialBuffer == null && magneticDomain == null
                && zeroGap == null && curtain == null && scatterPlates == null && thermalShell == null
                && vectorParry == null && inertialAnchor == null && circulationRelief == null
                && entity instanceof ServerPlayer player && autonomousVectorCircleBenefitsActive(player)
                && groundedFrame != null && groundedFrame.parent == null && !groundedFrame.poisoned
                && groundedFrame.source == event.getSource() && groundedFrame.incoming != null
                && !groundedFrame.incoming.isCanceled()) {
            circleAttempted = true;
            autonomousCircle = AutonomousVectorCircle.plan(player, groundedFrame.autonomousVectorCircle, previous, exactDamage);
            if (autonomousCircle == null) {
                // Visibility callbacks may settle nested damage or revoke the capture. Never
                // republish the earlier ledger over those already committed native changes.
                var current = read(entity);
                if (current.isEmpty()) return;
                previous = current.get();
            }
        }
        ExactCombatState next = grounding != null ? grounding.state() : potentialBuffer != null ? potentialBuffer.state()
                : magneticDomain != null ? magneticDomain.state()
                : zeroGap != null ? zeroGap.base().withShieldEnabled(false).applyMitigatedDamage(zeroGap.residual()).state()
                : curtain != null ? curtain.state().withShieldEnabled(false).applyMitigatedDamage(curtain.remaining()).state()
                : scatterPlates != null ? scatterPlates.state()
                : thermalShell != null ? thermalShell.state()
                : autonomousCircle != null ? autonomousCircle.state()
                : circulationRelief != null ? circulationRelief.state()
                : inertialAnchor != null ? inertialAnchor.state()
                : vectorParry != null ? vectorParry.state()
                : plainSettlement(entity, previous, exactDamage, event.getSource());
        // Specialized defenses retain their direction/type/quota and reserve benefits. Their remainder is then
        // caught by the category's base shield as well; no ordinary hit can remove HP while base S remains.
        BigDecimal specialAbsorbed = grounding != null ? grounding.absorbed() : potentialBuffer != null ? potentialBuffer.absorbed()
                : magneticDomain != null ? magneticDomain.absorbed() : zeroGap != null ? zeroGap.absorbed()
                : curtain != null ? curtain.absorbed() : scatterPlates != null ? scatterPlates.absorbed()
                : thermalShell != null ? thermalShell.absorbed() : autonomousCircle != null ? autonomousCircle.absorbed()
                : circulationRelief != null ? circulationRelief.absorbed() : inertialAnchor != null ? inertialAnchor.absorbed()
                : vectorParry != null ? vectorParry.absorbed() : null;
        BigDecimal specialistPrevented = circulationRelief != null ? circulationRelief.prevented()
                : inertialAnchor != null ? inertialAnchor.prevented()
                : vectorParry != null ? vectorParry.prevented() : BigDecimal.ZERO;
        if (EsperShields.eligible(entity) && specialAbsorbed != null && grounding == null)
            next = cn.academy.port.highpower.core.CategoryShieldRules.finish(previous, next,
                    exactDamage.subtract(specialistPrevented).subtract(specialAbsorbed).max(BigDecimal.ZERO));
        // Parents, optional children and break debt commit before any observer or controller callback.
        SharedShieldReserveShares nextReserve = curtain != null ? curtain.shares()
                : magneticDomain != null ? MagneticDomainReserve.savedShares(entity, previous).orElseThrow()
                    .withTotal(next, magneticDomain.reserve()) : null;
        if (zeroGap != null) {
            var transaction = ZeroGapArmor.authorizeSettlement((ServerPlayer)entity, groundedFrame.zeroGap, zeroGap, next);
            var prepared = ZeroGapArmorStorage.prepare(zeroGapStorage, transaction);
            if (!ZeroGapArmorStorage.apply(prepared)) throw new IllegalStateException("Stale authenticated252 contact");
            ZeroGapArmor.resourcesChanged((ServerPlayer)entity, transaction.before(), transaction.after());
            DissipationCurtain.resourcesChanged((ServerPlayer)entity);
            ScatterPlates.baseSharesChanged((ServerPlayer)entity, transaction.before().scatter(), transaction.after().scatter());
        } else if (autonomousCircle != null) {
            circleNotifications = publishResources(entity, next, null, autonomousCircle.shares(), null);
            circleVisual = AutonomousVectorCircle.committed((ServerPlayer)entity, groundedFrame.autonomousVectorCircle, autonomousCircle);
        } else if (circulationRelief != null) {
            circulationNotifications = publishResources(entity, next, null, null, null);
            circulationVisual = CirculationRelief.committed((ServerPlayer)entity, groundedFrame.circulationRelief, circulationRelief);
        } else if (inertialAnchor != null) {
            anchorNotifications = publishResources(entity, next, null, null, null);
            // Claim quota before any resource observers can cause a nested admission.
            anchorVisual = InertialAnchor.committedFall((ServerPlayer)entity, inertialAnchor, groundedFrame.anchorFall.point());
        } else if (circleAttempted) {
            // A refused circle still settles ordinary damage, preserving current partitions
            // and deferring observers until outer HP and lethal bookkeeping are complete.
            circleNotifications = publishResources(entity, next, null, null, null);
        } else commitResources(entity, next, scatterPlates == null ? null : scatterPlates.shares().afterBaseSpend(next.shield()), nextReserve);
        if (entity instanceof cn.academy.port.city.CityNpcEntity npc && npc.ambient()
                && previous.health().compareTo(next.health())>0 && next.health().compareTo(BigDecimal.ONE)<=0)
            npc.combat().yieldAfterHit();
        if (EsperShields.eligible(entity) && (previous.shield().compareTo(next.shield()) > 0
                || specialAbsorbed != null && specialAbsorbed.signum() > 0)) {
            tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
            EsperShields.absorbed((ServerPlayer)entity, event.getSource(), next.shield().signum() == 0);
        }
        if (inertialAnchor != null && inertialAnchor.absorbed().signum() > 0)
            tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
        if (grounding != null && grounding.absorbed().signum() > 0) {
            tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
            groundedFrame.groundingKnockbackFactor = grounding.knockbackFactor();
        }
        if (potentialBuffer != null && potentialBuffer.absorbed().signum() > 0)
            tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
        if (magneticDomain != null && magneticDomain.absorbed().signum() > 0)
            tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
        if (zeroGap != null && zeroGap.absorbed().signum() > 0)
            tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
        if (curtain != null && curtain.spent().signum() > 0)
            tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
        if (scatterPlates != null && scatterPlates.absorbed().signum() > 0)
            tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
        if (thermalShell != null && thermalShell.absorbed().signum() > 0)
            tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
        if (vectorParry != null && vectorParry.absorbed().signum() > 0)
            tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
        if (circulationRelief != null && circulationRelief.absorbed().signum() > 0)
            tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
        if (autonomousCircle != null && autonomousCircle.absorbed().signum() > 0)
            tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
        if (exactDamage.signum() > 0) HighPowerNaturalRecovery.damageTaken(entity);
        if (trace != null && trace.frame != null) {
            NativeHurtFrame frame = trace.frame;
            float nativeIncoming = frame.compared ? frame.nativeIncoming : trace.beforeArmor + trace.invulnerability;
            if (Float.isFinite(nativeIncoming) && nativeIncoming >= 0 && !frame.poisoned
                    && (!(event.getSource() instanceof ExactSkillDamageSource exact) || exact.historyValidated)) {
                boolean exactBridge = event.getSource() instanceof ExactSkillDamageSource;
                BigDecimal rawIncoming = exactBridge ? ((ExactSkillDamageSource)event.getSource()).rawForHistory : decimal(nativeIncoming);
                frame.pendingHistory = new HitHistory(nativeIncoming, rawIncoming, exactBridge);
            }
        }
        if (!previous.isDead() && next.isDead()) {
            // Native death veto restores HP, not resources already consumed by this defense.
            // The persisted fallback below likewise retains the committed current shield.
            LETHAL.put(entity, new ExactCombatState(previous.health(), previous.maxHealth(),
                    next.shield(), next.maxShield(), false));
            // Entity saving can happen after vanilla restores a totem but before our
            // next callback. Persist the marker alongside the exact zero ledger.
            tag(entity).putString(PENDING_LETHAL_HEALTH, CombatAmounts.decimalString(previous.health()));
        }
        synchronizeAdapter(entity, next);
        // NEW422 optional notifications cannot interrupt already committed HP/lethal/recovery bookkeeping.
        if (anchorNotifications != null) {
            try { anchorNotifications.run(); }
            catch (RuntimeException | LinkageError failure) { LOGGER.warn("NEW422 post-settlement resource notification failed; exact damage remains committed", failure); }
        }
        if (anchorVisual != null) {
            try { anchorVisual.run(); }
            catch (RuntimeException | LinkageError failure) { LOGGER.warn("NEW422 landing visual failed; exact damage remains committed", failure); }
        }
        if (circulationNotifications != null) {
            try { circulationNotifications.run(); }
            catch (RuntimeException | LinkageError failure) { LOGGER.warn("NEW432 resource notification failed after exact settlement", failure); }
        }
        if (circulationVisual != null) {
            try { circulationVisual.run(); }
            catch (RuntimeException | LinkageError failure) { LOGGER.warn("NEW432 visual failed after exact settlement", failure); }
        }
        if (circleNotifications != null) {
            try { circleNotifications.run(); }
            catch (RuntimeException | LinkageError failure) { LOGGER.warn("NEW452 resource notification failed after exact settlement", failure); }
        }
        if (circleVisual != null) {
            try { circleVisual.run(); }
            catch (RuntimeException | LinkageError failure) { LOGGER.warn("NEW452 visual failed after exact settlement", failure); }
        }
        // Mutable XP observers run only after all ledger/history/recovery/HP work. Reentry cannot be overwritten afterward.
        if (grounding != null && grounding.absorbed().signum() > 0)
            GroundingReturn.committed((ServerPlayer)entity, groundedFrame.grounding, grounding);
        else if (potentialBuffer != null && potentialBuffer.absorbed().signum() > 0)
            PotentialBuffer.committed((ServerPlayer)entity, groundedFrame.potentialBuffer, potentialBuffer);
        else if (magneticDomain != null && magneticDomain.absorbed().signum() > 0)
            MagneticDomain.committed((ServerPlayer)entity, groundedFrame.magneticDomain, magneticDomain.absorbed());
        else if (zeroGap != null && zeroGap.absorbed().signum() > 0)
            ZeroGapArmor.committed((ServerPlayer)entity, groundedFrame.zeroGap, zeroGap);
        else if (curtain != null && curtain.absorbed().signum() > 0)
            DissipationCurtain.committed((ServerPlayer)entity, groundedFrame.curtain, curtain.absorbed(), curtain.spent());
        else if (scatterPlates != null && scatterPlates.absorbed().signum() > 0)
            ScatterPlates.committed((ServerPlayer)entity, groundedFrame.scatterPlates, scatterPlates, scatterPlates.shares().afterBaseSpend(next.shield()));
        else if (thermalShell != null && thermalShell.absorbed().signum() > 0)
            ThermalShell.committed((ServerPlayer)entity, groundedFrame.thermalShell, thermalShell);
        else if (vectorParry != null && vectorParry.absorbed().signum() > 0)
            VectorParry.committed((ServerPlayer)entity, groundedFrame.vectorParry, vectorParry);
        // Only already committed positive HP or base-shield loss can arm an escape. Native lethal/totem
        // handling remains authoritative and never creates an emergency-return receipt.
        if (groundedFrame != null && !groundedFrame.poisoned && groundedFrame.parent == null
                && groundedFrame.incoming != null && !groundedFrame.incoming.isCanceled()
                && nativeDamage > 0 && exactDamage.signum() > 0)
            EmergencyReturn.settled(groundedFrame.emergencyReturn,
                    previous.health().subtract(next.health()).add(previous.shield().subtract(next.shield())).max(BigDecimal.ZERO), !next.isDead());
    }

    private static boolean reviewedZeroGapSource(ServerPlayer defender, DamageSource source) {
        if (source instanceof ExactSkillDamageSource exact) return exact.target == defender
                && (exact.dualPointBeam && exact.skill.equals("meltdowner.dual_point")
                    || exact.foldedMirrorBeam && exact.skill.equals("meltdowner.folded_mirror_array")
                    || exact.remoteScatterRelayPermit != null && exact.skill.equals("meltdowner.remote_scatter_relay")
                    || exact.disintegrationSweepBeam && exact.skill.equals("meltdowner.disintegration_sweep"));
        return ZeroGapArmorArrowReceipts.eligibleSource(defender, source);
    }

    /** NEW152 reuses only named, already reviewed native and ACA source contracts, in every direction.
     * Projected rail/thunder-array paths retain their own server emission contract. Unknown lasers,
     * ordinary environmental/bypass damage and other ACA beam families receive no new compatibility.
     */
    private static boolean reviewedMagneticDomainSource(LivingEntity target, DamageSource source) {
        return reviewedPotentialBufferSource(target, source) || reviewedElectricalSource(target, source);
    }

    /** NEW342 observes only the closed ordinary PvE hit contract; it never mitigates its damage. */
    static boolean reviewedEmergencyReturnSource(ServerPlayer target, DamageSource source) {
        if (target == null || source == null || source.getClass() != DamageSource.class
                || !(source.getEntity() instanceof net.minecraft.world.entity.monster.Monster attacker)
                || !supported(attacker) || attacker.level() != target.level()
                || attacker.isAlliedTo(target) || target.isAlliedTo(attacker)
                || !(source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                    || source.is(DamageTypes.ARROW))) return false;
        if (source.is(DamageTypes.ARROW)) {
            if (!(source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Arrow arrow)) return false;
            var item = arrow.getPickupItemStackOrigin(); var weapon = arrow.getWeaponItem();
            if (item.getCount() != 1 || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(item,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW))) return false;
            if (weapon != null && !weapon.isEmpty() && (weapon.getCount() != 1
                    || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(weapon,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW)))) return false;
        }
        return reviewedPotentialBufferSource(target, source);
    }

    /** NEW142 closed source contract. Refusal only skips this layer; ordinary damage still proceeds. */
    private static boolean reviewedPotentialBufferSource(LivingEntity target, DamageSource source) {
        if (source == null || !ordinaryDefenseTags(source) || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        net.minecraft.world.phys.Vec3 origin = source.getSourcePosition();
        if (origin == null || !Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)) return false;
        if (source instanceof ExactSkillDamageSource exact) {
            return standardSource(source) && (exact.skill.equals("electromaster.forked_arc")
                    || exact.skill.equals("electromaster.conductive_circuit") || exact.skill.equals("electromaster.thunder_net")
                    || exact.skill.equals("electromaster.iron_sand_lance") || exact.skill.equals("electromaster.rail_thunder_array"));
        }
        if (source instanceof AbilityDamage.SkillDamageSource) return reviewedElectricalSource(target, source);
        if (source.getClass() != DamageSource.class) return false;
        String nativeType;
        if (source.is(DamageTypes.PLAYER_ATTACK)) nativeType = "player_attack";
        else if (source.is(DamageTypes.MOB_ATTACK)) nativeType = "mob_attack";
        else if (source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) nativeType = "mob_attack_no_aggro";
        else if (source.is(DamageTypes.ARROW)) nativeType = "arrow";
        else return false;
        var resource = target.getServer().getResourceManager().getResource(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("damage_type/" + nativeType + ".json"));
        if (resource.isEmpty() || !resource.get().sourcePackId().equals("vanilla")
                || !resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")))) return false;
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == target || !supported(attacker)
                || attacker.level() != target.level() || !attacker.isAlive() || attacker.isRemoved()) return false;
        if (!nativeType.equals("arrow")) {
            return source.getDirectEntity() == attacker && origin.equals(attacker.position()) && !source.is(DamageTypeTags.IS_PROJECTILE)
                    && (!nativeType.equals("player_attack") || attacker.getClass() == ServerPlayer.class);
        }
        if (!(source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Arrow arrow)
                || arrow.getClass() != net.minecraft.world.entity.projectile.Arrow.class
                || arrow.getType() != net.minecraft.world.entity.EntityType.ARROW || arrow.getOwner() != attacker || !origin.equals(arrow.position())
                || !arrow.getType().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy", "magnetic_projectiles")))
                || !source.is(DamageTypeTags.IS_PROJECTILE) || arrow.level() != target.level()
                || arrow.isRemoved() || arrow.isOnFire() || arrow.isCritArrow() || arrow.getPierceLevel() != 0
                || arrow.inGround || arrow.isNoGravity() || arrow.isNoPhysics() || arrow.noPhysics) return false;
        var stack = arrow.getPickupItemStackOrigin(); var weapon = arrow.getWeaponItem();
        return stack.is(net.minecraft.world.item.Items.ARROW) && stack.getCount() == 1
                && stack.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    net.minecraft.world.item.alchemy.PotionContents.EMPTY).equals(net.minecraft.world.item.alchemy.PotionContents.EMPTY)
                && stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                    net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty()
                && (weapon == null || weapon.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                    net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty());
    }

    /** Literal identities from final ACA source classes; no category/name substring or universal electrical hook. */
    private static boolean reviewedElectricalSource(LivingEntity target, DamageSource source) {
        if (source.getClass() == DamageSource.class && source.is(DamageTypes.LIGHTNING_BOLT)
                && source.getEntity() == null && source.getDirectEntity() == null
                && source.is(DamageTypeTags.IS_LIGHTNING) && source.is(DamageTypeTags.NO_KNOCKBACK)
                && !source.is(DamageTypeTags.IS_FIRE) && ordinaryDefenseTags(source)) {
            // Reviewed environmental lightning only. Ignition in thunderHit remains separate native fire damage.
            for (String path : List.of("damage_type/lightning_bolt.json", "tags/damage_type/is_lightning.json")) {
                var resource = target.getServer().getResourceManager().getResource(net.minecraft.resources.ResourceLocation.withDefaultNamespace(path));
                if (resource.isEmpty() || !resource.get().sourcePackId().equals("vanilla")
                        || !resource.get().knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")))) return false;
            }
            return reviewedLightningKnockbackTag(target);
        }
        if (!standardSource(source)) return false;
        if (source instanceof ExactSkillDamageSource exact) {
            return exact.skill.equals("electromaster.forked_arc") || exact.skill.equals("electromaster.conductive_circuit")
                    || exact.skill.equals("electromaster.thunder_net")
                    || exact.skill.equals("electromaster.rail_thunder_array") && exact.projectileOrigin == null
                        && source.is(DamageTypes.PLAYER_ATTACK);
        }
        if (source instanceof AbilityDamage.SkillDamageSource legacy) {
            return legacy.skill.equals("electromaster.arc_gen") || legacy.skill.equals("electromaster.thunder_bolt")
                    || legacy.skill.equals("electromaster.thunder_clap");
        }
        return false; // Other ordinary and external-mod sources remain outside this bounded defense.
    }

    /** NeoForge's reviewed additive poison entry overlays this vanilla tag without changing lightning. */
    private static boolean reviewedLightningKnockbackTag(LivingEntity target) {
        var stack = target.getServer().getResourceManager().getResourceStack(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("tags/damage_type/no_knockback.json"));
        boolean vanilla = false, neoforge = false;
        if (stack.isEmpty() || stack.size() > 2) return false;
        for (var resource : stack) {
            if (resource.sourcePackId().equals("vanilla")
                    && resource.knownPackInfo().equals(Optional.of(net.minecraft.server.packs.repository.KnownPack.vanilla("core")))
                    && !vanilla) {
                vanilla = true;
            } else if (resource.sourcePackId().equals("mod/neoforge") && !neoforge) {
                // Exact official 21.1.252 payload; no replacement flag, custom membership or permissive mod/tag registration.
                try (var reader = resource.openAsReader()) {
                    if (!com.google.gson.JsonParser.parseReader(reader).equals(
                            com.google.gson.JsonParser.parseString("{\"values\":[\"#neoforge:is_poison\"]}"))) return false;
                } catch (java.io.IOException | RuntimeException invalid) { return false; }
                neoforge = true;
            } else return false;
        }
        return vanilla;
    }

    /** Only the later native knockback belonging to this exact hurt frame may consume the receipt. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void groundingKnockback(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();
        if (!serverThread(entity)) return;
        NativeHurtFrame frame = FRAMES.get(entity);
        if (frame == null || frame.poisoned || frame.groundingKnockbackUsed || frame.groundingKnockbackFactor >= 1
                || !reviewedElectricalSource(entity, frame.source)) return;
        frame.groundingKnockbackUsed = true;
        if (!(entity instanceof ServerPlayer player) || !GroundingReturn.matches(player, frame.grounding)) return;
        float strength = event.getStrength();
        if (Float.isFinite(strength) && strength > 0)
            event.setStrength((float)(strength * frame.groundingKnockbackFactor));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void death(LivingDeathEvent event) {
        if (serverThread(event.getEntity()) && enabled(event.getEntity())) DEATHS.put(event.getEntity(), event);
    }

    /** Capture a preceding totem's one native HP before native healing can increase it. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void beforeHealing(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (serverThread(entity) && enabled(entity)) {
            flushRecovery(entity);
            HighPowerNaturalRecovery.capture(event);
        }
    }

    /** Inspect the final event later so a subsequent cancellation never grants healing. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void healing(LivingHealEvent event) {
        if (serverThread(event.getEntity()) && enabled(event.getEntity()) && event.getEntity().getHealth() > 0
                && !HighPowerNaturalRecovery.owns(event)) {
            HEALS.computeIfAbsent(event.getEntity(), ignored -> new ArrayList<>()).add(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void beforeTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity entity) HighPowerCurveMigration.migrate(entity);
        if (event.getEntity() instanceof LivingEntity entity && serverThread(entity) && enabled(entity)) { flushRecovery(entity); flushEarnedGrowth(entity); }
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity && serverThread(entity) && enabled(entity)) { flushRecovery(entity); flushEarnedGrowth(entity); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void clonePlayer(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer replacement) || !serverThread(replacement)) return;
        flushRecovery(event.getOriginal());
        flushEarnedGrowth(event.getOriginal());
        var previous = read(event.getOriginal());
        if (previous.isEmpty()) return;
        replacement.getPersistentData().put(DATA_KEY, tag(event.getOriginal()).copy());
        ExactCombatState achieved = previous.get();
        BigDecimal clonedActive = null;
        BigDecimal clonedCapacity = tag(replacement).contains(GROWTH_CURVE)
                ? new BigDecimal(tag(replacement).getString(GROWTH_POSITION)) : BigDecimal.valueOf(tag(replacement).getInt("level"));
        if (event.isWasDeath() && event.getOriginal() instanceof ServerPlayer original) {
            PendingGrowth pending = PENDING_GROWTH.get(original);
            if (pending != null && pendingMatches(original, pending)
                    && pending.position().compareTo(BigDecimal.valueOf(Math.min(5, pending.certifiedLevel() + 1))) <= 0) {
                if (pending.position().compareTo(earnedGrowthPosition(original)) > 0) {
                    var capacity = ContinuousHighPowerGrowth.sample(pending.position());
                    achieved = achieved.withMaximums(capacity.maxHealth(), capacity.maxShield());
                    clonedCapacity = pending.position();
                }
                // A rollback may leave q below Q; genuine pending active progress still survives death.
                clonedActive = pending.position();
            }
        }
        ExactCombatState state = event.isWasDeath()
                ? new ExactCombatState(achieved.maxHealth(), achieved.maxHealth(), achieved.maxShield(), achieved.maxShield(), false)
                : achieved.withShieldEnabled(false);
        // Base death policy is unchanged. Copied reserve currents receive no death refill.
        ZeroGapArmorStorage.guardParentWrite(replacement);
        GrowthChange growth = new GrowthChange(replacement, previous.get(), state, clonedCapacity,
                tag(replacement).getInt("level"), GrowthKind.CLONE, clonedActive);
        commitResources(replacement, state, null, null, growth);
        synchronizeAdapter(replacement, state);
        clearTransient(event.getOriginal());
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            if (serverThread(entity)) { flushRecovery(entity); flushEarnedGrowth(entity); }
            clearTransient(entity);
        }
    }

    private static void flushRecovery(LivingEntity entity) {
        var optional = read(entity);
        if (optional.isEmpty()) { clearTransient(entity); return; }
        ExactCombatState state = optional.get();
        LivingDeathEvent death = DEATHS.get(entity);
        ExactCombatState beforeLethal = LETHAL.get(entity);
        if (beforeLethal == null && tag(entity).contains(PENDING_LETHAL_HEALTH, Tag.TAG_STRING)) {
            // read() has already validated this bounded persisted amount.
            beforeLethal = new ExactCombatState(CombatAmounts.parse(tag(entity).getString(PENDING_LETHAL_HEALTH), "pending lethal health"),
                    state.maxHealth(), state.shield(), state.maxShield(), false);
        }
        boolean resolved = beforeLethal == null;
        if (death != null && death.isCanceled() && beforeLethal != null) {
            // Preserve a native death veto. No drops/death call is fabricated by this bridge.
            write(entity, beforeLethal);
            synchronizeAdapter(entity, beforeLethal);
            resolved = true;
        } else if (beforeLethal != null && state.isDead() && entity.getHealth() > 0) {
            // Vanilla totem (or an explicit native resurrection) already restored adapter HP.
            BigDecimal restored = state.maxHealth().multiply(decimal(entity.getHealth()))
                    .divide(decimal(entity.getMaxHealth()), RATIO_CONTEXT).min(state.maxHealth());
            state = new ExactCombatState(restored, state.maxHealth(), state.shield(), state.maxShield(), false);
            write(entity, state);
            synchronizeAdapter(entity, state);
            resolved = true;
        } else if (!state.isDead() || death != null) {
            resolved = true;
        }
        // If native HP is still zero and no final death event has happened, a
        // reentrant callback may be inside hurt before the totem/death decision.
        // Keep the marker instead of consuming the only recovery evidence early.
        if (resolved) {
            DEATHS.remove(entity);
            LETHAL.remove(entity);
            tag(entity).remove(PENDING_LETHAL_HEALTH);
        }
        flushHealing(entity);
    }

    private static void flushHealing(LivingEntity entity) {
        List<LivingHealEvent> heals = HEALS.remove(entity);
        if (heals == null) return;
        var optional = read(entity);
        if (optional.isEmpty()) return;
        ExactCombatState state = optional.get();
        if (state.isDead()) return;
        BigDecimal hp = state.health();
        for (LivingHealEvent heal : heals) {
            if (!heal.isCanceled() && Float.isFinite(heal.getAmount()) && heal.getAmount() > 0) {
                hp = hp.add(decimal(heal.getAmount())).min(state.maxHealth());
            }
        }
        state = new ExactCombatState(hp, state.maxHealth(), state.shield(), state.maxShield(), false);
        write(entity, state);
        synchronizeAdapter(entity, state);
    }

    /**
     * Ordinary settlement. An active esper's base shield takes the hit first and health only what the shield cannot hold
     * ({@link EsperShields}: category shields with super armor and a category trait). Without category shields (config off, or the
     * legacy GameTest bypass) the whole hit reaches health, except that a teleporter's phase veil takes a thin share.
     */
    private static ExactCombatState plainSettlement(LivingEntity entity, ExactCombatState previous, BigDecimal damage, net.minecraft.world.damagesource.DamageSource source) {
        if (CityNpcPower.active(entity)) {
            var next = previous.withShieldEnabled(CityNpcPower.baseShieldEnabled()).applyMitigatedDamage(damage).state().withShieldEnabled(false);
            if (previous.shield().compareTo(next.shield()) > 0) tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
            return entity instanceof cn.academy.port.city.CityNpcEntity npc?CityNpcPower.nonlethal(npc,next):next;
        }
        if (entity instanceof ServerPlayer player && EsperShields.eligible(player) && previous.shield().signum() > 0 && damage.signum() > 0) {
            var result = previous.withShieldEnabled(true).applyMitigatedDamage(damage);
            if (result.shieldAbsorbed().signum() > 0) {
                tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
            }
            return result.state().withShieldEnabled(false);
        }
        if (!EsperLegacyBypass.standAside() && entity instanceof ServerPlayer player && HighPowerCategories.isTeleporter(AbilityStorage.get(player).category)
                && HighPowerCategories.active(AbilityStorage.get(player))) {
            var veil = PhaseVeilRules.absorb(damage, previous.shield());
            if (veil.absorbed().signum() > 0) {
                tag(entity).putLong("lastShieldSpend", entity.level().getGameTime());
                return previous.withShieldEnabled(false).spendShield(veil.absorbed()).applyMitigatedDamage(veil.remaining()).state();
            }
        }
        return previous.withShieldEnabled(false).applyMitigatedDamage(damage).state();
    }

    private static void rejectPostDamage(LivingEntity entity, LivingDamageEvent.Post event, ExactCombatState previous) {
        HISTORY.remove(entity);
        if (event.getSource() instanceof ExactSkillDamageSource source) source.rejected = true;
        tag(entity).putString("lastRejected", "Invalid native stage or exact amount outside supported bounds; native side effects may already have occurred");
        LOGGER.warn("Rejected high-power damage for {}: unsupported native stage or exact amount; native pipeline side effects are not reversible",
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        synchronizeAdapter(entity, previous);
    }

    private static Optional<ExactCombatState> read(LivingEntity entity) {
        return read(entity, true);
    }
    private static Optional<ExactCombatState> read(LivingEntity entity, boolean memoizeGrowth) {
        if (!supported(entity) || !entity.getPersistentData().contains(DATA_KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag tag = tag(entity);
        try {
            HighPowerLevel level = HighPowerLevel.forLevel(tag.getInt("level"));
            Map<String, String> strings = new HashMap<>();
            for (String key : FIELDS) {
                if (!tag.contains(key, Tag.TAG_STRING)) return Optional.empty();
                strings.put(key, tag.getString(key));
            }
            ExactCombatState state = ExactCombatState.fromDecimalMap(strings);
            if (state.shieldEnabled()) return Optional.empty();
            if (tag.contains(GROWTH_CURVE) || tag.contains(GROWTH_POSITION) || tag.contains(EARNED_LEVEL)) {
                if (!tag.contains(GROWTH_CURVE, Tag.TAG_INT) || tag.getInt(GROWTH_CURVE) != ContinuousHighPowerGrowth.CURVE_VERSION
                        || !tag.contains(EARNED_LEVEL, Tag.TAG_INT) || tag.getInt(EARNED_LEVEL) != level.level()
                        || !tag.contains(GROWTH_POSITION, Tag.TAG_STRING)) return Optional.empty();
                String encodedPosition = tag.getString(GROWTH_POSITION);
                if (encodedPosition.length() > 96) return Optional.empty();
                BigDecimal position = ContinuousHighPowerGrowth.requirePosition(new BigDecimal(encodedPosition));
                if (!position.stripTrailingZeros().toPlainString().equals(encodedPosition)
                        || position.compareTo(BigDecimal.valueOf(Math.min(5, level.level() + 1))) > 0) return Optional.empty();
                GrowthValidation cached = GROWTH_VALIDATION.get(entity);
                if (cached == null || !cached.position().equals(encodedPosition)) {
                    cached = new GrowthValidation(encodedPosition, ContinuousHighPowerGrowth.sample(position));
                    if (memoizeGrowth) GROWTH_VALIDATION.put(entity, cached);
                }
                var capacity = cached.sample();
                if (state.maxHealth().compareTo(capacity.maxHealth()) != 0 || state.maxShield().compareTo(capacity.maxShield()) != 0) return Optional.empty();
            } else if (state.maxHealth().compareTo(level.maxHealth()) != 0 || state.maxShield().compareTo(level.maxShield()) != 0) return Optional.empty();
            if (tag.contains(PENDING_LETHAL_HEALTH)) {
                if (!tag.contains(PENDING_LETHAL_HEALTH, Tag.TAG_STRING)) return Optional.empty();
                BigDecimal pending = CombatAmounts.parse(tag.getString(PENDING_LETHAL_HEALTH), "pending lethal health");
                if (pending.signum() <= 0 || pending.compareTo(state.maxHealth()) > 0) return Optional.empty();
            }
            return Optional.of(state);
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    private enum GrowthKind { EARNED, RESET, CLONE }

    /** Closed lifecycle capability: only this adapter can create a q/Q/capacity proposal. */
    static final class GrowthChange {
        private final ServerPlayer owner;
        private final CompoundTag source, snapshot, metadata, active;
        private final ExactCombatState before, after;
        private final BigDecimal position;
        private final int certified;
        private final GrowthKind kind;
        private final cn.academy.port.core.AbilityProgress ability;
        private final String category;
        private final int nativeLevel;
        private final double nativeXp;

        private GrowthChange(ServerPlayer owner, ExactCombatState before, ExactCombatState after,
                             BigDecimal position, int certified, GrowthKind kind, BigDecimal activePosition) {
            this.owner = owner; this.before = before; this.after = after.withShieldEnabled(false);
            this.position = ContinuousHighPowerGrowth.requirePosition(position); this.certified = certified; this.kind = kind;
            this.ability = AbilityStorage.get(owner); this.category = ability.category;
            this.nativeLevel = ability.level; this.nativeXp = ability.levelExperience;
            this.source = tag(owner); this.snapshot = source.copy();
            this.metadata = new CompoundTag();
            // A legacy clone gains modern Q metadata only when genuine pending growth raises Q.
            if (kind != GrowthKind.CLONE || source.contains(GROWTH_CURVE)
                    || this.position.compareTo(BigDecimal.valueOf(source.getInt("level"))) > 0) {
                metadata.putInt("level", certified); metadata.putInt(EARNED_LEVEL, certified);
                metadata.putInt(GROWTH_CURVE, ContinuousHighPowerGrowth.CURVE_VERSION);
                metadata.putString(GROWTH_POSITION, this.position.stripTrailingZeros().toPlainString());
            }
            if (kind == GrowthKind.CLONE) {
                metadata.putBoolean("bracerActive", false);
                metadata.putLong("lastShieldTick", Long.MIN_VALUE);
            }
            if (activePosition == null) active = null;
            else {
                BigDecimal q = ContinuousHighPowerGrowth.requirePosition(activePosition);
                if (q.compareTo(this.position) > 0) throw new IllegalArgumentException("Active growth exceeds proposed capacity");
                if (kind != GrowthKind.RESET && !source.contains("activeGrowth", Tag.TAG_COMPOUND))
                    throw new IllegalArgumentException("Missing witnessed active growth");
                active = new CompoundTag();
                active.putString("position", q.stripTrailingZeros().toPlainString()); active.putBoolean("initial", false);
                if (kind == GrowthKind.RESET) {
                    active.putInt("version", 1); active.putString("category", category);
                    active.putInt("nativeLevel", nativeLevel); active.putDouble("nativeXp", nativeXp);
                    active.putLong("revision", 0);
                }
            }
            validate(owner, before, this.after);
        }

        void validate(LivingEntity entity, ExactCombatState oldBase, ExactCombatState nextBase) {
            if (entity != owner || !serverThread(owner) || tag(owner) != source || !source.equals(snapshot)
                    || !before.equals(oldBase) || !after.equals(nextBase) || AbilityStorage.get(owner) != ability
                    || !category.equals(ability.category) || nativeLevel != ability.level
                    || Double.compare(nativeXp, ability.levelExperience) != 0)
                throw new IllegalStateException("Stale growth lifecycle draft");
            HighPowerLevel.forLevel(certified);
            var sample = ContinuousHighPowerGrowth.sample(position);
            if (nextBase.maxHealth().compareTo(sample.maxHealth()) != 0
                    || nextBase.maxShield().compareTo(sample.maxShield()) != 0
                    || position.compareTo(BigDecimal.valueOf(Math.min(5, certified + 1))) > 0)
                throw new IllegalArgumentException("Uncertified proposed growth capacities");
            int oldCertified = snapshot.contains(GROWTH_CURVE) ? snapshot.getInt(EARNED_LEVEL) : snapshot.getInt("level");
            BigDecimal oldPosition = snapshot.contains(GROWTH_CURVE)
                    ? new BigDecimal(snapshot.getString(GROWTH_POSITION)) : BigDecimal.valueOf(oldCertified);
            if (kind == GrowthKind.RESET) {
                if (!HighPowerCategories.active(ability) || certified != nativeLevel
                        || position.compareTo(ContinuousHighPowerGrowth.position(nativeLevel,
                            ability.levelProgress(SkillCatalog.levelSkillCount(ability)))) != 0)
                    throw new IllegalArgumentException("Reset differs from the current native growth witness");
            } else if (certified != oldCertified || position.compareTo(oldPosition) < 0
                    || kind == GrowthKind.EARNED && position.compareTo(oldPosition) <= 0)
                throw new IllegalArgumentException("Growth cannot replace its certified high-water");
        }

        CompoundTag metadata() { return metadata.copy(); }
        CompoundTag activeMetadata() { return active == null ? null : active.copy(); }
    }

    private static void write(LivingEntity entity, ExactCombatState state) {
        commitResources(entity, state, null, null);
    }

    /** Explicit NEW212 supplies its already-changed parents; reserve-only changes use the same boundary. */
    static void commitResources(LivingEntity entity, ExactCombatState state, ScatterPlateShares explicitScatter,
                                SharedShieldReserveShares explicitReserve) {
        commitResources(entity, state, explicitScatter, explicitReserve, null);
    }

    private static void commitResources(LivingEntity entity, ExactCombatState state, ScatterPlateShares explicitScatter,
                                         SharedShieldReserveShares explicitReserve, GrowthChange growth) {
        publishResources(entity, state, explicitScatter, explicitReserve, growth).run();
    }

    /** All detached validation precedes publication; returned internal notifications run afterwards. */
    private static Runnable publishResources(LivingEntity entity, ExactCombatState state, ScatterPlateShares explicitScatter,
                                              SharedShieldReserveShares explicitReserve, GrowthChange growth) {
        if (!serverThread(entity)) throw new IllegalStateException("Off-thread exact resource write");
        state = state.withShieldEnabled(false);
        ZeroGapArmorStorage.guardParentWrite(entity);
        var captured = ZeroGapArmorStorage.hasMetadata(entity) ? ZeroGapArmorStorage.capture(entity) : Optional.<ZeroGapArmorStorage.Capture>empty();
        ScatterPlateStorage.BaseChange change = null;
        ZeroGapArmorShares armorBefore = null, armorAfter = null;
        if (captured.isPresent() && captured.get().armor().isPresent()) {
            var capture = captured.get();
            ScatterPlateShares scatter = explicitScatter;
            if (scatter == null) {
                scatter = capture.scatter().withMaximum(state.maxShield());
                scatter = state.shield().compareTo(capture.base().shield()) < 0 ? scatter.afterBaseSpend(state.shield())
                        : scatter.afterBaseRecovery(state.shield());
                change = new ScatterPlateStorage.BaseChange(capture.scatter(), scatter);
            }
            SharedShieldReserveShares reserve = explicitReserve == null ? capture.reserves() : explicitReserve;
            var transaction = capture.armor().orElseThrow().reconcileParents(state, scatter, reserve);
            var prepared = ZeroGapArmorStorage.prepare(capture, transaction, growth);
            if (!ZeroGapArmorStorage.apply(prepared)) throw new IllegalStateException("Stale resource commit");
            armorBefore = transaction.before(); armorAfter = transaction.after();
        } else {
            // Preserve the legacy path when NEW252 is absent or latched unavailable. All codecs
            // still finish detached before the first parent write; optional corruption cannot block HP.
            CompoundTag live = tag(entity), base = new CompoundTag();
            state.toDecimalMap().forEach(base::putString);
            ExactCombatState before = read(entity, false).orElse(null);
            if (growth != null) growth.validate(entity, before, state);
            boolean scatterInvalid = false;
            if (explicitScatter == null) {
                try { change = ScatterPlateStorage.planBaseChange(entity, state); }
                catch (IllegalArgumentException | IllegalStateException invalid) { scatterInvalid = true; }
            }
            ScatterPlateShares scatter = explicitScatter == null ? change == null ? null : change.after()
                    : explicitScatter.requireMatches(state.shield(), state.maxShield());
            CompoundTag scatterTag = scatter == null ? null : ScatterPlateStorage.serialize(scatter);
            SharedShieldReserveShares reserve = explicitReserve;
            boolean reserveInvalid = false;
            if (growth != null && reserve == null && live.contains(MagneticDomainReserve.KEY)) {
                reserve = MagneticDomainReserve.savedShares(entity, before).orElse(null);
                reserveInvalid = reserve == null;
            }
            CompoundTag reserveTag = reserve == null ? null : MagneticDomainReserve.serialize(state, reserve);
            CompoundTag metadata = growth == null ? null : growth.metadata();
            CompoundTag active = growth == null ? null : growth.activeMetadata();
            if (scatterInvalid) live.putBoolean(ScatterPlateStorage.INVALIDATED_KEY, true);
            if (reserveInvalid) live.putBoolean("magneticDomainReserveUnavailable", true);
            for (String key : base.getAllKeys()) live.put(key, base.get(key));
            if (scatterTag != null) {
                live.put(ScatterPlateStorage.KEY, scatterTag);
                live.putBoolean(ScatterPlateStorage.INITIALIZED_KEY, true);
            }
            if (reserveTag != null) live.put(MagneticDomainReserve.KEY, reserveTag);
            if (metadata != null) for (String key : metadata.getAllKeys()) live.put(key, metadata.get(key));
            if (active != null) {
                if (live.contains("activeGrowth", Tag.TAG_COMPOUND)) {
                    CompoundTag target = live.getCompound("activeGrowth");
                    for (String key : active.getAllKeys()) target.put(key, active.get(key));
                } else live.put("activeGrowth", active);
            }
        }
        // No resource writes follow these notifications. Reentrant observers own their fresh state.
        final ZeroGapArmorShares notifyBefore = armorBefore, notifyAfter = armorAfter;
        final ScatterPlateStorage.BaseChange notifyChange = change;
        return () -> {
            if (entity instanceof ServerPlayer player) {
                if (notifyBefore != null) ZeroGapArmor.resourcesChanged(player, notifyBefore, notifyAfter);
                DissipationCurtain.resourcesChanged(player);
                if (explicitScatter == null && notifyChange != null)
                    ScatterPlates.baseSharesChanged(player, notifyChange.before(), notifyChange.after());
            }
        };
    }

    /** Closed NEW422 publication. No callbacks occur between resource debit and the native strength write. */
    static Runnable commitAnchorKnockback(ServerPlayer player, InertialAnchorRules.KnockbackPlan plan) {
        if (!serverThread(player) || !inertialAnchorBenefitsActive(player) || plan == null
                || !read(player).filter(plan.before()::equals).isPresent()) return null;
        Runnable notifications = publishResources(player, plan.state(), null, null, null);
        tag(player).putLong("lastShieldSpend", player.level().getGameTime());
        return notifications;
    }

    private static void writeBase(LivingEntity entity, ExactCombatState state) {
        write(entity, state);
    }
    private static CompoundTag tag(LivingEntity entity) { return entity.getPersistentData().getCompound(DATA_KEY); }
    private static BigDecimal decimal(float value) { return new BigDecimal(Float.toString(value)); }
    private static boolean serverThread(LivingEntity entity) {
        return !entity.level().isClientSide && entity.getServer() != null && entity.getServer().isSameThread();
    }
    private static void clearTransient(LivingEntity entity) {
        GROWTH_VALIDATION.remove(entity);
        if (entity instanceof ServerPlayer player) PENDING_GROWTH.remove(player);
        LETHAL.remove(entity); DEATHS.remove(entity); HEALS.remove(entity);
        HISTORY.remove(entity); TRACES.remove(entity); ACTIVE.remove(entity); FRAMES.remove(entity);
    }

    /** This assignment is solely the opted-in float adapter; all damage arithmetic above is BigDecimal. */
    private static void synchronizeAdapter(LivingEntity entity, ExactCombatState state) {
        float normalized = state.isDead() ? 0 : state.health().divide(state.maxHealth(), RATIO_CONTEXT)
                .multiply(decimal(entity.getMaxHealth())).floatValue();
        if (!state.isDead()) normalized = Math.max(Float.MIN_NORMAL, normalized);
        entity.setHealth(Math.min(entity.getMaxHealth(), normalized));
    }

    private record HitHistory(float nativeIncoming, BigDecimal rawIncoming, boolean exactBridge) {}
    private static final class NativeHurtFrame {
        final DamageSource source;
        final HitHistory previousHistory;
        final NativeHurtFrame parent;
        LivingIncomingDamageEvent incoming;
        HitHistory pendingHistory;
        boolean poisoned, compared, comparisonAccepted, adaptCooldown, residualInstalled, sawArmor;
        float nativeIncoming, nativePrevious, residualWitness;
        BigDecimal logicalIncoming, logicalResidual;
        GroundingReturn.Capture grounding;
        PotentialBuffer.Capture potentialBuffer;
        MagneticDomain.AbsorptionSnapshot magneticDomain;
        ZeroGapArmor.Capture zeroGap;
        boolean zeroGapOwnsHit;
        DissipationCurtain.Capture curtain;
        ScatterPlates.Capture scatterPlates;
        ThermalShell.Capture thermalShell;
        CirculationRelief.Capture circulationRelief;
        AutonomousVectorCircle.Capture autonomousVectorCircle;
        VectorParry.Capture vectorParry;
        InertialAnchor.Capture inertialAnchor;
        BrakingTouchLandingProof.LandingHurt anchorFall;
        boolean anchorPostGuard;
        EmergencyReturn.Capture emergencyReturn;
        double groundingKnockbackFactor = 1;
        boolean groundingKnockbackUsed;
        NativeHurtFrame(DamageSource source, HitHistory previousHistory, NativeHurtFrame parent) {
            this.source = source; this.previousHistory = previousHistory; this.parent = parent;
        }
    }
    private static final class NativeTrace {
        final DamageSource source;
        final HitHistory previousHistory;
        final NativeHurtFrame frame;
        NativeTrace previousTrace;
        float beforeArmor, invulnerability;
        NativeTrace(DamageSource source, HitHistory previousHistory, NativeHurtFrame frame) {
            this.source = source; this.previousHistory = previousHistory; this.frame = frame;
        }
    }

    private static final class ExactSkillDamageSource extends DamageSource {
        private final ServerPlayer player;
        private final ShortInterceptionArrowBridge.Contact shortInterceptionContact;
        private final TrajectoryReturnArrowBridge.Contact trajectoryReturnContact;
        private final String skill;
        private final LivingEntity target;
        private final BigDecimal raw;
        private final float witness;
        private final net.minecraft.world.phys.Vec3 projectileOrigin;
        /** NEW211 uses a real hand origin with PLAYER_ATTACK semantics, never projectile flags. */
        private final boolean dualPointBeam;
        /** NEW222 retains a separate closed source identity, never a NEW211 beam permit. */
        private final boolean thermalShellPulse;
        /** NEW221 has its own closed beam identity and final reflected contact. */
        private final boolean foldedMirrorBeam;
        /** NEW231 is a conserved direct sweep share, never a reflected mirror permit. */
        private final boolean disintegrationSweepBeam;
        /** NEW241 closed, fixed-emitter corridor permit. */
        private final boolean particleLaminateBeam;
        /** NEW251 immutable deployed-core source; not a player-defense compatibility grant. */
        private final boolean multicoreCanopyBeam;
        /** NEW311 owns a real needle source and cannot claim a beam endpoint or defense whitelist. */
        private final DisplacedProjection.EmissionPermit displacedProjectionPermit;
        /** NEW321 retains a distinct closed contact permit and an exact cross-needle entity type. */
        private final CrossSubstitution.EmissionPermit crossSubstitutionPermit;
        private final ShortInterception.FallbackPermit shortInterceptionFallbackPermit;
        /** NEW341 retains separate corridor custody and never certifies a NEW212 beam contact. */
        private final SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit;
        /** NEW351 six-slot retained original authority, never an arrow or planar-beam permit. */
        private final MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit;
        /** NEW411 has a native melee origin, but no projectile, electrical or MD beam authority. */
        private final DeflectionStrike.EmissionPermit deflectionStrikePermit;
        /** NEW421 has its own frozen front origin and whole-cast allocation, never NEW411 authority. */
        private final ShearWindblade.EmissionPermit shearWindbladePermit;
        private final YawPalm.EmissionPermit yawPalmPermit;
        private final VectorCage.EmissionPermit vectorCagePermit;
        /** NEW253 has one terminal planar beam, separate from every earlier beam identity. */
        private final RemoteScatterRelay.EmissionPermit remoteScatterRelayPermit;
        private boolean remoteScatterRelayPaymentAttempted, remoteScatterRelayCommitAuthorized, remoteScatterRelayPostObserved;
        private final TrajectoryReturn.AirHammerPermit trajectoryReturnAirHammerPermit;
        /** Only explicitly reviewed beam identities may present their actual final endpoint to defenses. */
        private final net.minecraft.world.phys.Vec3 certifiedBeamEndpoint;
        private final java.util.function.BooleanSupplier attackGuard;
        private boolean rejected, absorptionObserved, accounted, prepared, historyValidated, nativeStageReached;
        private boolean yawCommitAuthorized, yawExpectedDeath;
        private boolean trajectoryReturnCommitAuthorized;
        private float beforeAbsorption, absorptionPool, nativePrepared, expectedAbsorption, nativeIncoming;
        private boolean ordinaryArmorObserved, ordinaryPrepared;
        private float ordinaryBeforeArmor = Float.NaN, ordinaryPreLimit;
        private BigDecimal exactBeforeAbsorption = BigDecimal.ZERO, rawForHistory = BigDecimal.ZERO;
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint) {
            this(player, skill, target, raw, witness, projectileOrigin, attackGuard, dualPointBeam,
                    thermalShellPulse, foldedMirrorBeam, disintegrationSweepBeam, false, certifiedBeamEndpoint);
        }
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint) {
            this(player,skill,target,raw,witness,projectileOrigin,attackGuard,dualPointBeam,thermalShellPulse,
                    foldedMirrorBeam,disintegrationSweepBeam,particleLaminateBeam,false,certifiedBeamEndpoint);
        }
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint) {
            this(player, skill, target, raw, witness, projectileOrigin, attackGuard, dualPointBeam, thermalShellPulse,
                    foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint, null, null);
        }
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                       DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                       CrossSubstitution.EmissionPermit crossSubstitutionPermit) {
            this(player, skill, target, raw, witness, projectileOrigin, attackGuard, dualPointBeam, thermalShellPulse,
                    foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                    displacedProjectionPermit, crossSubstitutionPermit, null);
        }
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                       DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                       CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                       ShortInterception.FallbackPermit shortInterceptionFallbackPermit) {
            this(player, skill, target, raw, witness, projectileOrigin, attackGuard, dualPointBeam, thermalShellPulse,
                    foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                    displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit, null);
        }
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                       DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                       CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                       ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                       SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit) {
            this(player, skill, target, raw, witness, projectileOrigin, attackGuard, dualPointBeam, thermalShellPulse,
                    foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                    displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit, spatialPincerCorridorPermit, null);
        }
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                       DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                       CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                       ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                       SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                       MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit) {
            this(player, skill, target, raw, witness, projectileOrigin, attackGuard, dualPointBeam, thermalShellPulse,
                    foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                    displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit,
                    spatialPincerCorridorPermit, multidimensionalLandingArrayPermit, null);
        }
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                       DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                       CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                       ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                       SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                       MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                       DeflectionStrike.EmissionPermit deflectionStrikePermit) {
            this(player, skill, target, raw, witness, projectileOrigin, attackGuard, dualPointBeam, thermalShellPulse,
                    foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                    displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit,
                    spatialPincerCorridorPermit, multidimensionalLandingArrayPermit, deflectionStrikePermit, null);
        }
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                       DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                       CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                       ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                       SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                       MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                       DeflectionStrike.EmissionPermit deflectionStrikePermit,
                                       ShearWindblade.EmissionPermit shearWindbladePermit) {
            this(player, skill, target, raw, witness, projectileOrigin, attackGuard, dualPointBeam, thermalShellPulse,
                    foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                    displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit,
                    spatialPincerCorridorPermit, multidimensionalLandingArrayPermit, deflectionStrikePermit, shearWindbladePermit, null);
        }
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                       DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                       CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                       ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                       SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                       MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                       DeflectionStrike.EmissionPermit deflectionStrikePermit,
                                       ShearWindblade.EmissionPermit shearWindbladePermit,
                                       YawPalm.EmissionPermit yawPalmPermit) {
            this(player, skill, target, raw, witness, projectileOrigin, attackGuard, dualPointBeam, thermalShellPulse,
                    foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                    displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit,
                    spatialPincerCorridorPermit, multidimensionalLandingArrayPermit, deflectionStrikePermit,
                    shearWindbladePermit, yawPalmPermit, null);
        }
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                       DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                       CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                       ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                       SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                       MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                       DeflectionStrike.EmissionPermit deflectionStrikePermit,
                                       ShearWindblade.EmissionPermit shearWindbladePermit,
                                       YawPalm.EmissionPermit yawPalmPermit,
                                       TrajectoryReturn.AirHammerPermit trajectoryReturnAirHammerPermit) {
            this(player, skill, target, raw, witness, projectileOrigin, attackGuard, dualPointBeam, thermalShellPulse,
                foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit, spatialPincerCorridorPermit,
                multidimensionalLandingArrayPermit, deflectionStrikePermit, shearWindbladePermit, yawPalmPermit, trajectoryReturnAirHammerPermit, null);
        }
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                       DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                       CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                       ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                       SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                       MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                       DeflectionStrike.EmissionPermit deflectionStrikePermit,
                                       ShearWindblade.EmissionPermit shearWindbladePermit,
                                       YawPalm.EmissionPermit yawPalmPermit,
                                       TrajectoryReturn.AirHammerPermit trajectoryReturnAirHammerPermit,
                                           VectorCage.EmissionPermit vectorCagePermit) {
            this(player, skill, target, raw, witness, projectileOrigin, attackGuard, dualPointBeam, thermalShellPulse,
                    foldedMirrorBeam, disintegrationSweepBeam, particleLaminateBeam, multicoreCanopyBeam, certifiedBeamEndpoint,
                    displacedProjectionPermit, crossSubstitutionPermit, shortInterceptionFallbackPermit, spatialPincerCorridorPermit,
                    multidimensionalLandingArrayPermit, deflectionStrikePermit, shearWindbladePermit, yawPalmPermit,
                    trajectoryReturnAirHammerPermit, vectorCagePermit, null);
        }
        private ExactSkillDamageSource(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw, float witness,
                                       net.minecraft.world.phys.Vec3 projectileOrigin,
                                       java.util.function.BooleanSupplier attackGuard, boolean dualPointBeam, boolean thermalShellPulse, boolean foldedMirrorBeam, boolean disintegrationSweepBeam, boolean particleLaminateBeam, boolean multicoreCanopyBeam,
                                       net.minecraft.world.phys.Vec3 certifiedBeamEndpoint,
                                       DisplacedProjection.EmissionPermit displacedProjectionPermit,
                                       CrossSubstitution.EmissionPermit crossSubstitutionPermit,
                                       ShortInterception.FallbackPermit shortInterceptionFallbackPermit,
                                       SpatialPincerCorridor.EmissionPermit spatialPincerCorridorPermit,
                                       MultidimensionalLandingArray.ClosedEmissionPermit multidimensionalLandingArrayPermit,
                                       DeflectionStrike.EmissionPermit deflectionStrikePermit,
                                       ShearWindblade.EmissionPermit shearWindbladePermit,
                                       YawPalm.EmissionPermit yawPalmPermit,
                                       TrajectoryReturn.AirHammerPermit trajectoryReturnAirHammerPermit,
                                       VectorCage.EmissionPermit vectorCagePermit,
                                       RemoteScatterRelay.EmissionPermit remoteScatterRelayPermit) {
            super(displacedProjectionPermit != null ? player.damageSources().thrown(displacedProjectionPermit.needle(), player).typeHolder()
                    : crossSubstitutionPermit != null ? player.damageSources().thrown(crossSubstitutionPermit.needle(), player).typeHolder()
                    : shortInterceptionFallbackPermit != null ? player.damageSources().thrown(shortInterceptionFallbackPermit.needle(), player).typeHolder()
                    : spatialPincerCorridorPermit != null ? player.damageSources().thrown(spatialPincerCorridorPermit.needle(), player).typeHolder()
                    : multidimensionalLandingArrayPermit != null ? player.damageSources().thrown(multidimensionalLandingArrayPermit.needle(), player).typeHolder()
                    : projectileOrigin == null || dualPointBeam || thermalShellPulse || foldedMirrorBeam || disintegrationSweepBeam || particleLaminateBeam || multicoreCanopyBeam || deflectionStrikePermit != null || shearWindbladePermit != null || yawPalmPermit != null || vectorCagePermit != null || trajectoryReturnAirHammerPermit != null || remoteScatterRelayPermit != null ? player.damageSources().playerAttack(player).typeHolder()
                    : player.damageSources().thrown(null, player).typeHolder(), displacedProjectionPermit != null ? displacedProjectionPermit.needle()
                    : crossSubstitutionPermit != null ? crossSubstitutionPermit.needle()
                    : shortInterceptionFallbackPermit != null ? shortInterceptionFallbackPermit.needle()
                    : spatialPincerCorridorPermit != null ? spatialPincerCorridorPermit.needle()
                    : multidimensionalLandingArrayPermit != null ? multidimensionalLandingArrayPermit.needle()
                    : projectileOrigin == null || dualPointBeam || thermalShellPulse || foldedMirrorBeam || disintegrationSweepBeam || particleLaminateBeam || multicoreCanopyBeam || deflectionStrikePermit != null || shearWindbladePermit != null || yawPalmPermit != null || vectorCagePermit != null || trajectoryReturnAirHammerPermit != null || remoteScatterRelayPermit != null ? player : null, player, projectileOrigin);
            this.shortInterceptionContact = null;
            this.trajectoryReturnContact = null;
            this.projectileOrigin = projectileOrigin;
            this.dualPointBeam = dualPointBeam;
            this.thermalShellPulse = thermalShellPulse;
            this.foldedMirrorBeam = foldedMirrorBeam;
            this.disintegrationSweepBeam = disintegrationSweepBeam;
            this.particleLaminateBeam = particleLaminateBeam;
            this.multicoreCanopyBeam = multicoreCanopyBeam;
            this.displacedProjectionPermit = displacedProjectionPermit;
            this.crossSubstitutionPermit = crossSubstitutionPermit;
            this.shortInterceptionFallbackPermit = shortInterceptionFallbackPermit;
            this.spatialPincerCorridorPermit = spatialPincerCorridorPermit;
            this.multidimensionalLandingArrayPermit = multidimensionalLandingArrayPermit;
            this.deflectionStrikePermit = deflectionStrikePermit;
            this.shearWindbladePermit = shearWindbladePermit;
            this.yawPalmPermit = yawPalmPermit;
            this.vectorCagePermit = vectorCagePermit;
            this.remoteScatterRelayPermit = remoteScatterRelayPermit;
            this.trajectoryReturnAirHammerPermit = trajectoryReturnAirHammerPermit;
            this.certifiedBeamEndpoint = certifiedBeamEndpoint;
            this.attackGuard = attackGuard;
            this.player = player; this.skill = skill; this.target = target; this.raw = raw; this.witness = witness;
        }
        /** Copies native attribution and raw position; controller is never installed as the shooter. */
        private ExactSkillDamageSource(ShortInterceptionArrowBridge.Contact contact, BigDecimal raw, float witness) {
            super(contact.original.typeHolder(), contact.original.getDirectEntity(), contact.original.getEntity(), contact.original.sourcePositionRaw());
            shortInterceptionContact = contact; trajectoryReturnContact = null; trajectoryReturnAirHammerPermit = null;
            player = contact.permit().controller(); skill = "teleporter.short_interception_native";
            target = contact.permit().target(); this.raw = raw; this.witness = witness;
            projectileOrigin = null; certifiedBeamEndpoint = null;
            dualPointBeam = false; thermalShellPulse = false; foldedMirrorBeam = false; disintegrationSweepBeam = false;
            particleLaminateBeam = false; multicoreCanopyBeam = false;
            displacedProjectionPermit = null; crossSubstitutionPermit = null; shortInterceptionFallbackPermit = null;
            spatialPincerCorridorPermit = null; multidimensionalLandingArrayPermit = null; deflectionStrikePermit = null;
            shearWindbladePermit = null; yawPalmPermit = null; vectorCagePermit = null; remoteScatterRelayPermit = null;
            attackGuard = () -> validInterceptionContact(contact);
        }
        /** NEW431 preserves the native arrow's original attribution even when its shooter is the victim. */
        private ExactSkillDamageSource(TrajectoryReturnArrowBridge.Contact contact, BigDecimal raw, float witness) {
            super(contact.original.typeHolder(), contact.original.getDirectEntity(), contact.original.getEntity(), contact.original.sourcePositionRaw());
            trajectoryReturnContact = contact; shortInterceptionContact = null; trajectoryReturnAirHammerPermit = null;
            player = contact.permit().controller(); skill = "vecmanip.trajectory_return_native";
            target = contact.target(); this.raw = raw; this.witness = witness;
            projectileOrigin = null; certifiedBeamEndpoint = null;
            dualPointBeam = false; thermalShellPulse = false; foldedMirrorBeam = false; disintegrationSweepBeam = false;
            particleLaminateBeam = false; multicoreCanopyBeam = false;
            displacedProjectionPermit = null; crossSubstitutionPermit = null; shortInterceptionFallbackPermit = null;
            spatialPincerCorridorPermit = null; multidimensionalLandingArrayPermit = null; deflectionStrikePermit = null;
            shearWindbladePermit = null; yawPalmPermit = null; vectorCagePermit = null; remoteScatterRelayPermit = null;
            attackGuard = () -> validTrajectoryReturnContact(contact);
        }
        @Override public Component getLocalizedDeathMessage(LivingEntity target) {
            if (trajectoryReturnContact != null) return trajectoryReturnContact.original.getLocalizedDeathMessage(target);
            if (shortInterceptionContact != null) return shortInterceptionContact.original.getLocalizedDeathMessage(target);
            return new AbilityDamage.SkillDamageSource(player, skill, false).getLocalizedDeathMessage(target);
        }
    }
}
