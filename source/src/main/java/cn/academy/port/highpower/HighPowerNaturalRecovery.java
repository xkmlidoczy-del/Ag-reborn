package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.ExactCombatState;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Ability-assisted rest for the explicit EM/MD/TP roots: forked_arc, dual_point
 * and safe_landing_probe. This is an approved food-only rest extension.
 * Only a scoped, admitted FoodData heal call can use proportional HP. Its attributable
 * native float gain preserves vanilla rounding and completion timing; no other
 * heal source is promoted into a percentage of the pool.
 * The native call posts one heal event; its final amount/cancellation is observed
 * after it returns. Nested heals never inherit the source marker.
 */
@EventBusSubscriber(modid = "academy")
public final class HighPowerNaturalRecovery {
    public static final String DATA_KEY = "academy:highpower_natural_recovery_v1";
    private static final MathContext PRECISION = new MathContext(80);
    private static final Map<FoodData, FoodFrame> FOOD = new IdentityHashMap<>();
    private static final Map<LivingEntity, HealFrame> ACTIVE = new IdentityHashMap<>();
    private static final Map<LivingHealEvent, HealFrame> OWNED = new IdentityHashMap<>();

    private HighPowerNaturalRecovery() {}

    /** All numeric text is bounded exact decimal; rate is scheduled, not a promise about future hooks. */
    public record Snapshot(String health, String maxHealth, String shield, String maxShield,
                           String status, String exactHpPerSecond,
                           int pulseTicks, int ticksUntilPulse, int pauseTicksRemaining) {}

    public static Optional<Snapshot> snapshot(ServerPlayer player) {
        if (!server(player) || !HighPowerCombat.enabled(player)) return Optional.empty();
        var ability = AbilityStorage.get(player);
        if (!HighPowerCategories.active(ability)) return Optional.empty();
        HighPowerCombat.settleRecovery(player);
        var state = HighPowerCombat.exactState(player).orElse(null);
        if (state == null) return Optional.empty();
        String status = inactiveReason(player);
        int pause = pauseTicksRemaining(player), interval = 0, next = 0;
        BigDecimal rate = BigDecimal.ZERO;
        FoodData food = player.getFoodData();
        if (status == null) {
            if (state.isDead() || !player.isAlive()) status = "dead";
            else if (state.health().compareTo(state.maxHealth()) >= 0) status = "full";
            else if (pause > 0) status = "paused";
            else if (!player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) status = "gamerule_off";
            else if (food.getFoodLevel() < 18) status = "food_low";
            else {
                boolean saturated = food.getFoodLevel() >= 20 && food.getSaturationLevel() > 0;
                interval = saturated ? 10 : 80;
                float pulse = saturated ? Math.min(food.getSaturationLevel(), 6F) / 6F : 1F;
                if (!Float.isFinite(pulse) || pulse <= 0) status = "unsupported";
                else {
                    status = saturated ? "saturated" : "slow";
                    int timer = ((NativeFoodRecoveryAccess) food).academy$naturalRecoveryTimer();
                    next = Math.max(1, interval - Math.max(0, Math.min(timer, interval - 1)));
                    float reference = referenceHealth(player, state, player.getMaxHealth());
                    rate = attributableBudget(reference, player.getMaxHealth(), pulse, state)
                            .multiply(BigDecimal.valueOf(20)).divide(BigDecimal.valueOf(interval), PRECISION);
                }
            }
        }
        return Optional.of(new Snapshot(text(state.health()), text(state.maxHealth()), text(state.shield()), text(state.maxShield()),
                status, rateText(rate), interval, next, pause));
    }

    /** Growth must not mutate the denominator inside an attributed native heal transaction. */
    static boolean healingInProgress(ServerPlayer player) { return ACTIVE.containsKey(player); }

    /**
     * Transport the admitted float-rounding shadow once when capacity really grows.
     * This does not heal, touch FoodData's timer/exhaustion, or reset combat pause.
     * Equal-cap level boundaries remain an exact no-op, including the saved shadow.
     */
    static void capacityChanged(ServerPlayer player, ExactCombatState before, ExactCombatState after) {
        if (!server(player) || before.maxHealth().compareTo(after.maxHealth()) == 0) return;
        float nativeMaximum = player.getMaxHealth();
        if (!Float.isFinite(nativeMaximum) || nativeMaximum <= 0) return;
        float reference = referenceHealth(player, before, nativeMaximum);
        float transported = new BigDecimal(Float.toString(reference)).multiply(before.maxHealth())
                .divide(after.maxHealth(), PRECISION).floatValue();
        transported = Math.max(0, Math.min(nativeMaximum, transported));
        rememberReference(player, after, nativeMaximum, transported);
    }

    /** A real offensive action, including a paid cast that misses, postpones rest. */
    public static void offensiveAction(ServerPlayer player) { combatActivity(player); }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void ordinaryAttack(AttackEntityEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) offensiveAction(player);
    }

    /** New native weapon projectiles count at launch, including misses; utility throws do not. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void weaponLaunched(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.loadedFromDisk() || event.getLevel().isClientSide
                || !(event.getEntity() instanceof AbstractArrow arrow)) return;
        Class<?> type = arrow.getClass();
        if (type != Arrow.class && type != SpectralArrow.class && type != ThrownTrident.class) return;
        if (arrow.getOwner() instanceof ServerPlayer player) offensiveAction(player);
    }

    /** Named admitted damage calculations count even if the target later resists the hit. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void legacySkillAttack(SkillAttackEvent event) {
        if (event.skill != null && (event.skill.startsWith("electromaster.") || event.skill.equals("meltdowner.dual_point"))
                && Double.isFinite(event.amount) && event.amount > 0) offensiveAction(event.player);
    }

    /** Successful attributed outgoing damage also covers already-airborne projectiles and native attacks. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void outgoingDamage(LivingDamageEvent.Post event) {
        if (Float.isFinite(event.getNewDamage()) && event.getNewDamage() > 0
                && event.getSource().getEntity() instanceof ServerPlayer player && player != event.getEntity()) offensiveAction(player);
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer replacement && server(replacement)
                && event.getOriginal().getPersistentData().contains(DATA_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            replacement.getPersistentData().put(DATA_KEY, event.getOriginal().getPersistentData().getCompound(DATA_KEY).copy());
        }
    }

    public static void damageTaken(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) combatActivity(player);
    }

    private static void combatActivity(ServerPlayer player) {
        if (!server(player) || !HighPowerCombat.enabled(player)) return;
        var ability = AbilityStorage.get(player);
        if (!HighPowerCategories.active(ability) || !ability.learned(HighPowerCategories.recoveryRoot(ability.category))) return;
        CompoundTag data = player.getPersistentData().getCompound(DATA_KEY);
        data.putLong("lastCombatTick", player.level().getGameTime());
        player.getPersistentData().put(DATA_KEY, data);
    }

    public static int pauseTicksRemaining(ServerPlayer player) {
        int configured = HighPowerRecoveryConfig.COMBAT_PAUSE_TICKS.get();
        CompoundTag data = player.getPersistentData().getCompound(DATA_KEY);
        if (configured == 0 || !data.contains("lastCombatTick", net.minecraft.nbt.Tag.TAG_LONG)) return 0;
        long now = player.level().getGameTime(), last = data.getLong("lastCombatTick");
        if (now < last) return configured; // A clock rollback never creates negative/overflowing HUD values.
        long elapsed = now - last;
        return elapsed >= configured || elapsed < 0 ? 0 : configured - (int) elapsed;
    }

    /** Called only by the admitted native tick wrapper; ordinary FoodData stays unchanged. */
    public static void foodTick(FoodData food, Player player, Runnable vanillaTick) {
        if (!(player instanceof ServerPlayer server) || server.getFoodData() != food || !eligible(server)) { vanillaTick.run(); return; }
        FoodFrame prior = FOOD.get(food);
        FoodFrame frame = new FoodFrame(player, true);
        FOOD.put(food, frame);
        try { vanillaTick.run(); }
        finally { if (prior == null) FOOD.remove(food); else FOOD.put(food, prior); }
    }

    /** Runs in each of the two original native branch predicates, never globally on Player.isHurt. */
    public static boolean foodIsHurt(FoodData food, Player player, boolean nativeHurt) {
        if (!(player instanceof ServerPlayer owner) || !server(owner)) return nativeHurt;
        FoodFrame frame = FOOD.get(food);
        if (frame == null || frame.player != player || !frame.managed || !(player instanceof ServerPlayer server)) return nativeHurt;
        HighPowerCombat.settleRecovery(server);
        return pauseTicksRemaining(server) == 0 && HighPowerCombat.exactState(server)
                .map(s -> !s.isDead() && s.health().compareTo(s.maxHealth()) < 0).orElse(false);
    }

    /** One native heal, one final observation. This source marker is never carried into a nested heal. */
    public static void foodHeal(FoodData food, Player player, float amount, Runnable vanillaHeal) {
        if (!(player instanceof ServerPlayer owner) || !server(owner)) { vanillaHeal.run(); return; }
        FoodFrame frame = FOOD.get(food);
        if (frame == null || frame.player != player || !frame.managed || !(player instanceof ServerPlayer server)) {
            vanillaHeal.run(); return;
        }
        if (!eligible(server) || pauseTicksRemaining(server) > 0 || !Float.isFinite(amount) || amount <= 0) return;
        var state = HighPowerCombat.exactState(server).orElse(null);
        if (state == null || state.isDead() || state.health().compareTo(state.maxHealth()) >= 0) return;
        HealFrame source = new HealFrame(server, state.health(), state.maxHealth(), server.getMaxHealth(), referenceHealth(server, state, server.getMaxHealth()));
        HealFrame previous = ACTIVE.put(server, source);
        try {
            vanillaHeal.run();
            if (source.event != null) {
                HighPowerCombat.settleRecovery(server);
                LivingHealEvent event = source.event;
                if (!event.isCanceled() && Float.isFinite(event.getAmount()) && event.getAmount() > 0) {
                    var current = HighPowerCombat.exactState(server).orElse(null);
                    float nativeMaximum = server.getMaxHealth();
                    if (current != null && Float.isFinite(nativeMaximum) && nativeMaximum > 0) {
                        // Nested ordinary healing is already reconciled in exact units. If it
                        // changed that baseline, remap before the attributable OUTER pulse;
                        // never attribute the total temporary native before/after HP to it.
                        boolean unchanged = current.health().compareTo(source.initialHealth) == 0
                                && current.maxHealth().compareTo(source.exactMaximum) == 0
                                && Float.compare(nativeMaximum, source.nativeMaximum) == 0;
                        float reference = unchanged ? source.referenceHealth : referenceHealth(server, current, nativeMaximum);
                        float nextReference = Math.min(nativeMaximum, reference + event.getAmount());
                        BigDecimal scaled = attributableBudget(reference, nativeMaximum, event.getAmount(), current);
                        HighPowerCombat.applyNaturalHealing(server, scaled);
                        HighPowerCombat.exactState(server).ifPresent(after -> rememberReference(server, after, nativeMaximum, nextReference));
                    }
                }
            }
        } finally {
            if (source.event != null) OWNED.remove(source.event);
            if (previous == null) ACTIVE.remove(server); else ACTIVE.put(server, previous);
            HighPowerCombat.settleRecovery(server);
            HighPowerCombat.resynchronizeExact(server); // Invalid/nonpositive final event never poisons the float adapter.
        }
    }

    /** Scoped at native heal entry, before any event subscriber can cause reentrant healing. */
    public static void nativeHeal(LivingEntity entity, Runnable vanillaHeal) {
        if (entity.level().isClientSide || entity.getServer() == null || !entity.getServer().isSameThread()) { vanillaHeal.run(); return; }
        HealFrame source = ACTIVE.get(entity);
        if (source == null) { vanillaHeal.run(); return; }
        source.depth++;
        try { vanillaHeal.run(); }
        finally { source.depth--; }
    }

    /** Called at the existing high-priority heal observer; identity ties provenance to exactly one event. */
    static void capture(LivingHealEvent event) {
        HealFrame source = ACTIVE.get(event.getEntity());
        if (source != null && source.depth == 1 && source.event == null) {
            source.event = event;
            OWNED.put(event, source);
        }
    }
    static boolean owns(LivingHealEvent event) { return OWNED.containsKey(event); }

    private static boolean eligible(ServerPlayer player) { return inactiveReason(player) == null && player.isAlive(); }
    private static String inactiveReason(ServerPlayer player) {
        if (!server(player) || !HighPowerCombat.enabled(player)) return "inactive";
        var ability = AbilityStorage.get(player);
        if (!HighPowerCategories.active(ability)) return "inactive";
        String root = HighPowerCategories.recoveryRoot(ability.category);
        if (!ability.learned(root)) return "unlearned";
        if (!HighPowerRecoveryConfig.ENABLED.get() || !SkillCatalog.enabled(ability.category, root)) return "inactive";
        if (!(player instanceof NativeNaturalHealAccess) || player.getFoodData().getClass() != FoodData.class || !(player.getFoodData() instanceof NativeFoodRecoveryAccess) || HighPowerCombat.unsupportedReason(player).isPresent()
                || !Float.isFinite(player.getMaxHealth()) || player.getMaxHealth() <= 0
                || !Float.isFinite(player.getFoodData().getSaturationLevel()) || player.getFoodData().getSaturationLevel() < 0) return "unsupported";
        return null;
    }

    /**
     * Persisted reference is only a vanilla float-rounding clock. Exact HP remains authoritative.
     * Any damage, ordinary healing, or capacity/native-maximum change remaps it without healing.
     */
    private static float referenceHealth(ServerPlayer player, ExactCombatState state, float nativeMaximum) {
        CompoundTag data = player.getPersistentData().getCompound(DATA_KEY);
        float reference = data.getFloat("referenceHealth");
        if (Float.isFinite(reference) && reference >= 0 && reference <= nativeMaximum
                && data.contains("referenceHealth", net.minecraft.nbt.Tag.TAG_FLOAT)
                && Float.compare(data.getFloat("referenceMaximum"), nativeMaximum) == 0
                && text(state.health()).equals(data.getString("mappedHealth"))
                && text(state.maxHealth()).equals(data.getString("mappedMaximum"))) return reference;
        return Math.max(0, Math.min(nativeMaximum, state.health().divide(state.maxHealth(), PRECISION)
                .multiply(new BigDecimal(Float.toString(nativeMaximum))).floatValue()));
    }

    private static void rememberReference(ServerPlayer player, ExactCombatState state, float nativeMaximum, float reference) {
        if (!Float.isFinite(reference) || reference < 0 || reference > nativeMaximum) return;
        CompoundTag data = player.getPersistentData().getCompound(DATA_KEY);
        data.putFloat("referenceHealth", reference);data.putFloat("referenceMaximum", nativeMaximum);
        data.putString("mappedHealth", text(state.health()));data.putString("mappedMaximum", text(state.maxHealth()));
        player.getPersistentData().put(DATA_KEY, data);
    }

    /** Identical prospective/commit calculation, including the actual missing-health clamp. */
    private static BigDecimal attributableBudget(float reference, float maximum, float amount, ExactCombatState state) {
        if (!Float.isFinite(reference) || !Float.isFinite(amount) || amount <= 0 || state.isDead()) return BigDecimal.ZERO;
        BigDecimal missing = state.maxHealth().subtract(state.health());
        // The native proxy can already round full while exact HP has a sub-ULP deficit.
        // Only this real positive pulse may award its bounded nominal amount in that case.
        if (reference >= maximum) return scaledAmount(amount, maximum, state.maxHealth()).min(missing);
        float next = Math.min(maximum, reference + amount), gain = next - reference;
        if (!(gain > 0)) return BigDecimal.ZERO;
        // A genuine positive reference pulse crossing full may complete exact HP;
        // merely deriving or loading a full reference never reaches this operation.
        if (next >= maximum) return missing;
        return scaledAmount(gain, maximum, state.maxHealth()).min(missing);
    }

    public static BigDecimal scaledAmount(float nativeHeal, float nativeMaximum, BigDecimal exactMaximum) {
        if (!Float.isFinite(nativeHeal) || nativeHeal <= 0 || !Float.isFinite(nativeMaximum) || nativeMaximum <= 0) return BigDecimal.ZERO;
        CombatAmounts.requirePositiveAmount(exactMaximum, "natural-recovery maximum");
        return new BigDecimal(Float.toString(nativeHeal)).divide(new BigDecimal(Float.toString(nativeMaximum)), PRECISION)
                .multiply(exactMaximum).min(exactMaximum);
    }
    private static String text(BigDecimal amount) { return CombatAmounts.decimalString(amount); }
    private static String rateText(BigDecimal amount) {
        if (amount.signum() < 0 || amount.compareTo(CombatAmounts.CAP.multiply(BigDecimal.TWO)) > 0
                || amount.precision() > CombatAmounts.MAX_DECIMAL_PRECISION
                || Math.abs((long)amount.scale()) > CombatAmounts.MAX_ABSOLUTE_SCALE) throw new IllegalArgumentException("Invalid natural recovery rate");
        return amount.signum() == 0 ? "0" : amount.stripTrailingZeros().toPlainString();
    }
    private static boolean server(ServerPlayer player) { return player != null && !player.level().isClientSide && player.server.isSameThread(); }
    private static final class FoodFrame {
        final Player player; final boolean managed;
        FoodFrame(Player player, boolean managed) { this.player = player; this.managed = managed; }
    }
    private static final class HealFrame {
        final ServerPlayer player; final BigDecimal initialHealth, exactMaximum; final float nativeMaximum, referenceHealth; LivingHealEvent event; int depth;
        HealFrame(ServerPlayer player, BigDecimal initialHealth, BigDecimal maximum, float nativeMaximum, float referenceHealth) { this.player = player; this.initialHealth = initialHealth; this.exactMaximum = maximum; this.nativeMaximum = nativeMaximum; this.referenceHealth = referenceHealth; }
    }
}
