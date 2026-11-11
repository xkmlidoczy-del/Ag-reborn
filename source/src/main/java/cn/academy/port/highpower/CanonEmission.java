package cn.academy.port.highpower;

import static cn.academy.port.core.SkillTuning.integer;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.canon.CanonSkills;
import cn.academy.port.core.AbilityProgress;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Closed, synchronous emission for registered canon controllers. The controller must validate
 * its own targeting and medium before calling begin. The client never supplies targets, costs,
 * or damage; the session fixes the exact whole-cast damage budget and one hit per selected entity.
 */
public final class CanonEmission {
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Set<String> CONTROLLERS = Set.of("cn.academy.port.canon.CanonSkillsCombatA",
            "cn.academy.port.canon.CanonWaterSkills", "cn.academy.port.canon.CanonPaperSkills",
            "cn.academy.port.canon.CanonLiquidSkills", "cn.academy.port.canon.CanonExpansionCombatA",
            "cn.academy.port.canon.CanonWaterExpansion", "cn.academy.port.canon.CanonLiquidCombatExpansion",
            "cn.academy.port.canon.CanonOffense");
    private CanonEmission() {}

    private static boolean constructController(Class<?> caller) {
        return caller.getName().equals("cn.academy.port.canon.CanonSkillsCombatA")
                && caller.getClassLoader() == CanonEmission.class.getClassLoader();
    }

    /** A separate, bounded, paid capability for the lifetime of one dark construct. */
    public static ConstructCast beginConstruct(ServerPlayer player, BooleanSupplier lifecycle, Predicate<LivingEntity> targetGuard) {
        if (!constructController(WALKER.getCallerClass()) || player == null || lifecycle == null || targetGuard == null
                || AbilityConsumption.busy(player) || !player.server.isSameThread() || player.connection == null
                || player.hasDisconnected() || player.isRemoved() || !player.isAlive() || player.isSpectator()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player || !HighPowerResources.ready(player)
                || HighPowerCombat.unsupportedReason(player).isPresent()) return null;
        var state = AbilityStorage.get(player);
        String skill = "dark_construct", category = "dark_matter";
        var definition = CanonSkills.find(skill).orElse(null);
        if (definition == null || !category.equals(state.category) || !state.presets.currentContains(skill)
                || !HighPowerSkills.eligible(state, skill) || HighPowerResources.effectiveLevel(player) < definition.level()) return null;
        final BigDecimal share;
        final double cost;
        try {
            var growth = HighPowerGrowth.values(player);
            share = growth.damage().divide(BigDecimal.TEN, 40, RoundingMode.DOWN).stripTrailingZeros();
            cost = growth.arcCost() * 5;
        }
        catch (IllegalArgumentException | IllegalStateException invalid) { return null; }
        if (share.signum() <= 0 || !Double.isFinite(cost) || cost <= 0) return null;
        ServerLevel world = player.serverLevel();
        long tick = world.getGameTime();
        if (tick < 0 || tick > Long.MAX_VALUE - 240) return null;
        int preset = state.presets.current(), level = state.level;
        long revision = state.presets.revision();
        if (!HighPowerResources.trySpend(player, skill, cost, 240)) return null;
        if (player.serverLevel() != world || world.getGameTime() != tick || AbilityStorage.get(player) != state
                || state.presets.current() != preset || state.presets.revision() != revision || state.level != level) return null;
        return new ConstructCast(player, state, world, tick, preset, revision, level, share, lifecycle, targetGuard);
    }

    /** Returns null on a refused cast; no target, resource, or capability is exposed to packets. */
    public static Cast begin(ServerPlayer player, String skill, double cost, int cooldown, List<LivingEntity> targets,
                             Predicate<LivingEntity> targetGuard) {
        Class<?> caller = WALKER.getCallerClass();
        if (!CONTROLLERS.contains(caller.getName()) || caller.getClassLoader() != CanonEmission.class.getClassLoader()
                || player == null || skill == null || !CanonSkills.IDS.contains(skill) || targets == null || targetGuard == null
                || targets.isEmpty() || targets.size() > 8 && !cn.academy.port.macro.MacroSkillRules.ultimate(skill) || AbilityConsumption.busy(player)
                || !player.server.isSameThread() || player.connection == null || player.hasDisconnected()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player
                || !HighPowerResources.ready(player) || HighPowerCombat.unsupportedReason(player).isPresent()) return null;
        var state = AbilityStorage.get(player);
        var definition = CanonSkills.find(skill).orElse(null);
        if (definition == null || !definition.category().equals(state.category) || !state.presets.currentContains(skill)
                || !HighPowerSkills.eligible(state, skill) || HighPowerResources.effectiveLevel(player) < definition.level()) return null;
        // Whitelist binds each controller to its own category, rather than granting generic damage.
        if (!(caller.getSimpleName().equals("CanonOffense")
                    && cn.academy.port.canon.CanonOffenseSkills.find(skill)!=null
                    && cn.academy.port.canon.CanonOffenseSkills.find(skill).category().equals(state.category)
                || caller.getSimpleName().equals("CanonWaterSkills") && state.category.equals("hydro_hand")
                || caller.getSimpleName().equals("CanonWaterExpansion") && state.category.equals("hydro_hand")
                    && Set.of("hydro_pressure_jet","hydro_fan","hydro_eddy").contains(skill)
                || caller.getSimpleName().equals("CanonPaperSkills") && state.category.equals("paper_psychokinesis")
                || caller.getSimpleName().equals("CanonLiquidSkills") && state.category.equals("liquid_shadow")
                || caller.getSimpleName().equals("CanonLiquidCombatExpansion") && state.category.equals("liquid_shadow")
                    && Set.of("liquid_contact_cut","liquid_contact_sweep","liquid_contact_crush").contains(skill)
                || (caller.getSimpleName().equals("CanonSkillsCombatA") || caller.getSimpleName().equals("CanonExpansionCombatA")) && (state.category.equals("dark_matter")
                    || state.category.equals("nitrogen_armor") || state.category.equals("aero_hand")))) return null;
        var unique = new HashSet<UUID>();
        for (var target : targets) if (!targetAllowed(player, target,skill) || !guard(targetGuard, target)
                || !targetAllowed(player, target,skill) || !unique.add(target.getUUID())) return null;
        final BigDecimal total;
        try { total = HighPowerGrowth.values(player).damage(); }
        catch (IllegalArgumentException | IllegalStateException invalid) { return null; }
        BigDecimal share = total.multiply(levelFactor(definition.level())).divide(BigDecimal.valueOf(targets.size()), 40, RoundingMode.DOWN).stripTrailingZeros();
        if (share.signum() <= 0 || !Double.isFinite(cost) || cost <= 0 || cooldown < 1 || cooldown > 12000) return null;
        int preset = state.presets.current();
        long presetRevision = state.presets.revision();
        int level = state.level;
        long tick = player.serverLevel().getGameTime();
        if (tick < 0 || tick > Long.MAX_VALUE - cooldown || !HighPowerResources.trySpend(player, skill, cost, cooldown)) return null;
        if (AbilityStorage.get(player) != state || state.presets.current() != preset
                || state.presets.revision() != presetRevision || state.level != level) return null;
        var cast = new Cast(player, state, state.category, skill, player.serverLevel(), tick, tick + cooldown,
                preset, presetRevision, level, List.copyOf(targets), share, targetGuard);
        // The debit is already final. A changed target or controller condition never grants a permit or a refund.
        for (var target : targets) if (!cast.valid(target)) return null;
        return cast;
    }

    /** Higher-tier skills hit harder; LV3 keeps the shared growth budget unchanged (it used to be one flat total for every skill). */
    public static BigDecimal levelFactor(int skillLevel) {
        return switch (skillLevel) { case 1 -> new BigDecimal("0.6"); case 2 -> new BigDecimal("0.8"); case 4 -> new BigDecimal("1.3");
            case 5 -> new BigDecimal("1.7"); default -> BigDecimal.ONE; };
    }

    private static boolean targetAllowed(ServerPlayer player, LivingEntity target) {
        return targetAllowed(player,target,"");
    }
    private static boolean targetAllowed(ServerPlayer player, LivingEntity target,String skill) {
        if (target == null || target == player || target.level() != player.level() || !target.isAlive() || target.isRemoved()
                || !finite(player.position()) || !finite(target.position())
                || player.serverLevel().getEntity(target.getUUID()) != target || player.distanceToSqr(target) > (cn.academy.port.macro.MacroSkillRules.ultimate(skill)?192*192:96*96)
                || !player.hasLineOfSight(target) || player.isAlliedTo(target) || target.isAlliedTo(player)
                || HighPowerCombat.unsupportedReason(target).isPresent()) return false;
        return !(target instanceof Player other) || AcademyConfig.ATTACK_PLAYERS.get()
                && player.server.isPvpAllowed() && player.canHarmPlayer(other);
    }
    private static boolean finite(Vec3 position) {
        return position != null && Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
    }
    private static boolean guard(Predicate<LivingEntity> predicate, LivingEntity target) {
        try { return predicate.test(target); }
        catch (RuntimeException invalid) { return false; }
    }

    public static final class Cast {
        private final ServerPlayer player;
        private final AbilityProgress ability;
        private final String category, skill;
        private final ServerLevel world;
        private final long tick, cooldownUntil;
        private final int preset, level;
        private final long presetRevision;
        private final List<LivingEntity> targets;
        private final BigDecimal share;
        private final Predicate<LivingEntity> targetGuard;
        private final Set<UUID> attempted = new HashSet<>();
        private boolean resolving,deferred,closed;
        private Cast(ServerPlayer player, AbilityProgress ability, String category, String skill, ServerLevel world, long tick,
                     long cooldownUntil, int preset, long presetRevision, int level, List<LivingEntity> targets, BigDecimal share,
                     Predicate<LivingEntity> targetGuard) {
            this.player = player; this.ability = ability; this.category = category; this.skill = skill; this.world = world;
            this.tick = tick; this.cooldownUntil = cooldownUntil; this.preset = preset;
            this.presetRevision = presetRevision; this.level = level; this.targets = targets; this.share = share; this.targetGuard = targetGuard;
        }
        /** Each originally selected target consumes its immutable share at most once, even on refusal. */
        public boolean attack(LivingEntity target) {
            if (!valid(target) || !targets.contains(target) || !attempted.add(target.getUUID())) return false;
            return HighPowerCombat.attackEmission(new Permit(this, target));
        }
        /** Preserve the frozen roster and each immutable share while spreading LV5 work over server ticks. */
        public void forEach(java.util.function.Consumer<LivingEntity> action,Runnable finished) {
            Class<?> caller=WALKER.getCallerClass();
            if(!CONTROLLERS.contains(caller.getName())||caller.getClassLoader()!=CanonEmission.class.getClassLoader()||resolving||closed)
                throw new IllegalStateException("Emission resolver ownership");
            resolving=true;
            if(!cn.academy.port.macro.MacroSkillRules.ultimate(skill)){targets.forEach(action);finished.run();return;}
            deferred=true;
            cn.academy.port.macro.MacroSettlement.submit(world,targets,
                    ()->!closed&&player.isAlive()&&!player.hasDisconnected()&&player.serverLevel()==world,
                    action,()->{try{finished.run();}finally{closed=true;}});
        }
        private boolean valid(LivingEntity target) {
            return contextValid(target) && guard(targetGuard, target) && contextValid(target);
        }
        private boolean contextValid(LivingEntity target) {
            if (player.isRemoved() || !player.isAlive() || player.isSpectator() || player.hasDisconnected()
                    || !player.server.isSameThread() || player.serverLevel() != world || closed || (deferred?world.getGameTime()<tick:world.getGameTime()!=tick)
                    || player.server.getPlayerList().getPlayer(player.getUUID()) != player
                    || !HighPowerResources.ready(player) || !targetAllowed(player, target,skill)) return false;
            var state = AbilityStorage.get(player);
            var budget = player.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
            return state == ability && state.level == level && state.presets.current() == preset
                    && state.presets.revision() == presetRevision && category.equals(state.category)
                    && state.activated && state.overloadFine && !state.interfering
                    && state.learned(skill) && state.presets.currentContains(skill) && SkillCatalog.enabled(category, skill)
                    && budget.contains("next_" + skill, Tag.TAG_LONG) && budget.getLong("next_" + skill) == cooldownUntil;
        }
    }

    /** One server-owned construct, one paid budget, and at most ten spaced settlement attempts. */
    public static final class ConstructCast {
        private final ServerPlayer player;
        private final AbilityProgress ability;
        private final ServerLevel world;
        private final long start, expires, cooldownUntil, revision;
        private final int preset, level;
        private final BigDecimal share;
        private final BooleanSupplier lifecycle;
        private final Predicate<LivingEntity> targetGuard;
        private long nextAttempt;
        private int attempts;
        private boolean canceled, inFlight;
        private ConstructCast(ServerPlayer player, AbilityProgress ability, ServerLevel world, long start,
                              int preset, long revision, int level, BigDecimal share,
                              BooleanSupplier lifecycle, Predicate<LivingEntity> targetGuard) {
            this.player = player; this.ability = ability; this.world = world; this.start = start;
            this.expires = start + integer("dark_construct", "duration_ticks"); this.cooldownUntil = start + 240; this.nextAttempt = start;
            this.preset = preset; this.revision = revision; this.level = level; this.share = share;
            this.lifecycle = lifecycle; this.targetGuard = targetGuard;
        }
        public void cancel() { if (constructController(WALKER.getCallerClass())) canceled = true; }
        public boolean attack(LivingEntity target) {
            if (!constructController(WALKER.getCallerClass()) || inFlight || canceled || attempts >= 10
                    || !player.server.isSameThread() || world.getGameTime() < nextAttempt) return false;
            inFlight = true;
            try {
                if (!valid(target)) return false;
                long tick = world.getGameTime();
                attempts++; nextAttempt = tick + 20;
                return HighPowerCombat.attackEmission(new ConstructPermit(this, target, attempts));
            } finally { inFlight = false; }
        }
        private boolean valid(LivingEntity target) {
            return contextValid(target) && lifecycleValid() && contextValid(target)
                    && guard(targetGuard, target) && contextValid(target) && lifecycleValid() && contextValid(target);
        }
        private boolean lifecycleValid() {
            try { return lifecycle.getAsBoolean(); }
            catch (RuntimeException invalid) { return false; }
        }
        private boolean contextValid(LivingEntity target) {
            long tick = world.getGameTime();
            if (canceled || !inFlight || tick < start || tick >= expires || player.isRemoved() || !player.isAlive()
                    || player.isSpectator() || player.hasDisconnected() || !player.server.isSameThread()
                    || player.serverLevel() != world || player.server.getPlayerList().getPlayer(player.getUUID()) != player
                    || !HighPowerResources.ready(player) || HighPowerResources.effectiveLevel(player) < 5
                    || HighPowerCombat.unsupportedReason(player).isPresent() || !targetAllowed(player, target)) return false;
            var state = AbilityStorage.get(player);
            var budget = player.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
            return state == ability && state.level == level && state.presets.current() == preset
                    && state.presets.revision() == revision && "dark_matter".equals(state.category)
                    && state.activated && state.overloadFine && !state.interfering
                    && state.learned("dark_construct") && state.presets.currentContains("dark_construct")
                    && SkillCatalog.enabled("dark_matter", "dark_construct")
                    && budget.contains("next_dark_construct", Tag.TAG_LONG)
                    && budget.getLong("next_dark_construct") == cooldownUntil;
        }
    }

    /** One non-reusable exact-damage attempt originating from a live construct. */
    public static final class ConstructPermit implements AbilityEmission {
        private final ConstructCast cast;
        private final LivingEntity target;
        private final int attempt;
        private boolean claimed;
        private ConstructPermit(ConstructCast cast, LivingEntity target, int attempt) {
            this.cast = cast; this.target = target; this.attempt = attempt;
        }
        public ServerPlayer player() { return cast.player; }
        public LivingEntity target() { return target; }
        public String skill() { return "dark_matter.dark_construct"; }
        public BigDecimal damage() { return cast.share; }
        public boolean claim() { if (claimed || !valid()) return false; claimed = true; return true; }
        public boolean valid() { return cast.attempts == attempt && cast.valid(target); }
    }

    /** A permit is created only by the paid, frozen server-side cast; one claim is permitted. */
    public static final class Permit implements AbilityEmission {
        private final Cast cast;
        private final LivingEntity target;
        private boolean claimed;
        private Permit(Cast cast, LivingEntity target) { this.cast = cast; this.target = target; }
        public ServerPlayer player() { return cast.player; }
        public LivingEntity target() { return target; }
        public String skill() { return cast.category + "." + cast.skill; }
        public BigDecimal damage() { return cast.share; }
        public boolean claim() { if (claimed || !valid()) return false; claimed = true; return true; }
        public boolean valid() { return cast.targets.contains(target) && cast.attempted.contains(target.getUUID()) && cast.valid(target); }
    }
}
