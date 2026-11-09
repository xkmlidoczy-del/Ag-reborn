/* AcademyCraft 1.0.7 ThreateningTeleport/TPSkillHelper adaptation. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AbilityDamage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Server-thread-only, release-triggered item teleport; this does not teleport the player. */
public final class ThreateningTeleport {
    public static final String ID = "threatening_teleport";
    private static final String DIM_FOLDING = "dim_folding_theorem", SPACE_FLUCT = "space_fluct";
    private static final ResourceLocation NEEDLE = ResourceLocation.fromNamespaceAndPath("academy", "needle");
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final LegacySingleKeyProtocol.NonceLedger INPUTS = new LegacySingleKeyProtocol.NonceLedger();
    private static long nextToken;

    private ThreateningTeleport() {}

    private record Session(int entityId, ResourceKey<Level> dimension, AbilityProgress state, double exp,
                           long startedAt, LegacySingleKeyServerIdentity identity) {}
    private record Trace(Vec3 position, Entity target) {}

    /** Capture mastery on key-down. Repeated key-down packets cannot restart an active context. */
    public static boolean start(ServerPlayer player) { return startInternal(player, 0); }

    public static boolean canAcceptInput(ServerPlayer player, long input) {
        return LegacySingleKeyServerIdentity.canClaim(INPUTS, player, input);
    }

    public static boolean start(ServerPlayer player, long input) {
        return input > 0 && startInternal(player, input);
    }

    private static boolean startInternal(ServerPlayer player, long input) {if(cn.academy.port.AbilityConsumption.busy(player))return false;
        if (!onServerThread(player)) return false;
        AbilityProgress state = AbilityStorage.get(player);
        if (!validPlayer(player) || !canUse(state) || player.getMainHandItem().isEmpty()) return false;
        Session previous = ACTIVE.get(player.getUUID());
        if (previous != null) {
            if (previous.identity().owner() == player) return false;
            abort(previous.identity().owner());
            if (ACTIVE.get(player.getUUID()) == previous) return false;
        }
        if (nextToken == Long.MAX_VALUE
                || input > 0 && !LegacySingleKeyServerIdentity.claim(INPUTS, player, input)) return false;
        Session session = new Session(player.getId(), player.level().dimension(), state, state.exp(ID),
                player.serverLevel().getGameTime(), new LegacySingleKeyServerIdentity(player, input, ++nextToken, state));
        ACTIVE.put(player.getUUID(), session);
        sendVisual(player,session,ID+"_start",rules(state.exp(ID)).range(),null,-1,false,-1);
        return true;
    }

    /** Original held context only checks for an item; there is no charge timer or automatic shot. */
    public static void tick(ServerPlayer player) {
        if (!onServerThread(player)) return;
        Session session = ACTIVE.get(player.getUUID());
        if (session != null && !validSession(player, session)) abort(player);
    }

    /** Execute at current server position/aim with the current main-hand item, then terminate. */
    public static boolean release(ServerPlayer player) { return releaseInternal(player, 0, false); }

    /** Accepted context terminals require all issued identity components. */
    public static boolean release(ServerPlayer player, long input, long token, long ownerEpoch) {
        if (!onServerThread(player)) return false;
        Session session = ACTIVE.get(player.getUUID());
        return session != null && session.identity().matches(player, input, token, ownerEpoch)
                && release(player, input);
    }

    public static boolean release(ServerPlayer player, long input) {
        return input > 0 && releaseInternal(player, input, true);
    }

    private static boolean releaseInternal(ServerPlayer player, long input, boolean correlated) {
        if (!onServerThread(player)) return false;
        Session owned = ACTIVE.get(player.getUUID());
        if (owned == null || owned.identity().owner() != player) return false;
        if (correlated && (owned == null || !owned.identity().matches(player, input))) return false;
        Session session = ACTIVE.remove(player.getUUID());
        if(session==null)return false;
        if(!validSession(player,session)){sendVisual(player,session,ID+"_abort",0,null,-1,false,-1);return false;}
        AbilityProgress state = session.state();
        ClassicRules.SkillCost cost = rules(session.exp());
        Trace trace = trace(player, cost.range());
        if (trace == null) {sendVisual(player,session,ID+"_abort",0,null,-1,false,-1);return false;} // Never load chunks or accept non-finite targeting data.
        ItemStack held = player.getMainHandItem();
        // Legacy permits copying a zero-count stack after decrement. Modern ItemStack does not:
        // capture one item first so the last item is correctly returned on a miss.
        ItemStack drop = held.copyWithCount(1);
        double rawDamage = damage(state.exp(ID), isNeedle(held));
        if (!state.consumeSkill(ID,cost.cp(),cost.overload(),player.getAbilities().instabuild,()->player.getMainHandItem()==held&&!held.isEmpty()&&(trace.target()==null||!trace.target().isRemoved()&&trace.target().level()==player.level()))) {sendVisual(player,session,ID+"_abort",0,null,-1,false,-1);return false;}

        boolean entityHit = trace.target() != null;
        int critical=-1;
        if (entityHit) {
            int tier = criticalTier(state, () -> player.serverLevel().random.nextFloat());
            critical=tier;
            if (tier >= 0) {
                rawDamage = cn.academy.port.core.ClassicPassiveSkills.criticalDamage(rawDamage,tier);
                cn.academy.port.achievements.ClassicAchievements.trigger(player,"teleporter.critical_attack");
                recordCritical(state, tier);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new FleshRipping.CriticalHitEvent(player,trace.target(),tier));
                player.getPersistentData().putBoolean("ac_teleporter_critical_attack",true);
                player.sendSystemMessage(Component.translatable("ac.ability.teleporter.crithit",Float.valueOf((float)criticalRate(tier))));
            }
            // Mutable skill/config pipeline plus PvP/team/invulnerability and NeoForge hooks.
            // The original skill damage was armor-bypassing, not magic.
            AbilityDamage.attack(player, "teleporter." + ID, trace.target(), rawDamage, true);
            cn.academy.port.achievements.ClassicAchievements.trigger(player,"teleporter.threatening_teleport");
        }
        if (!player.getAbilities().instabuild) held.shrink(1);
        if (player.serverLevel().random.nextDouble() < dropProbability(entityHit)) {
            Vec3 position = trace.position();
            player.serverLevel().addFreshEntity(new ItemEntity(player.serverLevel(), position.x, position.y, position.z, drop));
        }
        // A traced target counts even when its damage hook rejects the attack, as in 1.0.7.
        state.addExperience(ID, experienceIncrement(entityHit));
        state.setCooldown(ID, cost.cooldown());
        AbilityStorage.save(player);
        sendVisual(player,session,ID,cost.range(),trace.position(),trace.target()==null?-1:trace.target().getId(),true,critical);
        return true;
    }

    /** Convenience for a server-authoritative instant press/release path, with all the same gates. */
    public static boolean perform(ServerPlayer player) {
        return start(player) && release(player);
    }

    public static void abort(ServerPlayer player) {
        if (!onServerThread(player)) return;
        Session session = ACTIVE.get(player.getUUID());
        if (session == null || session.identity().owner() != player) return;
        ACTIVE.remove(player.getUUID());
        sendVisual(player,session,ID+"_abort",0,null,-1,false,-1);
    }
    public static boolean abort(ServerPlayer player, long input, long token, long ownerEpoch) {
        if (!onServerThread(player)) return false;
        Session session = ACTIVE.get(player.getUUID());
        return session != null && session.identity().matches(player, input, token, ownerEpoch)
                && abort(player, input);
    }

    public static boolean abort(ServerPlayer player, long input) {
        if (!onServerThread(player) || input <= 0) return false;
        Session session = ACTIVE.get(player.getUUID());
        if (session == null || !session.identity().matches(player, input)) return false;
        abort(player);
        return true;
    }
    public static void remove(ServerPlayer player) {
        if (!onServerThread(player)) return;
        abort(player);
        INPUTS.forget(player);
    }
    public static void clear() { ACTIVE.clear(); INPUTS.clear(); }

    private static void sendVisual(ServerPlayer player,Session session,String kind,double range,Vec3 position,int target,boolean success,int critical) {
        var tag=new CompoundTag();tag.putString("kind",kind);session.identity().write(tag);tag.putLong("tick",Math.max(0,session.identity().level().getGameTime()-session.startedAt()));tag.putDouble("range",range);tag.putDouble("length",range);tag.putInt("target",target);tag.putBoolean("success",success);tag.putInt("critical_tier",critical);
        Vec3 at=position==null?player.getEyePosition():position;tag.putDouble("x",at.x);tag.putDouble("y",at.y);tag.putDouble("z",at.z);
        PacketDistributor.sendToPlayersNear(player.serverLevel(),null,player.getX(),player.getY(),player.getZ(),32,new AcademyNetwork.ClientData(tag));
    }

    /** Default-config curves, shared with deterministic tests; clamp malformed mastery defensively. */
    public static ClassicRules.SkillCost rules(double exp) {
        return new ClassicRules.SkillCost(ClassicRules.lerp(35, 100, exp), ClassicRules.lerp(18, 10, exp),
                ClassicRules.lerp(3, 6, exp), ClassicRules.lerp(SkillTuning.number("threatening_teleport", "reach_novice"), SkillTuning.number("threatening_teleport", "reach_mastered"), exp), (int)ClassicRules.lerp(30, 15, exp));
    }

    public static double damage(double exp, boolean needle) {
        return ClassicRules.lerp(3, 6, exp) * (needle ? 1.5 : 1);
    }

    public static double experienceIncrement(boolean entityHit) { return entityHit ? .003 : .0006; }
    public static double dropProbability(boolean entityHit) { return entityHit ? .3 : 1; }

    public static double criticalProbability(AbilityProgress state, int tier) {
        return cn.academy.port.core.ClassicPassiveSkills.criticalProbability(state,tier);
    }

    /** Three independent, sequential rolls; the first successful tier wins, not a weighted pick. */
    public static int criticalTier(AbilityProgress state, DoubleSupplier random) {
        for (int tier = 0; tier < 3; tier++) if (random.getAsDouble() < criticalProbability(state, tier)) return tier;
        return -1;
    }

    public static double criticalRate(int tier) {
        return cn.academy.port.core.ClassicPassiveSkills.criticalRate(tier);
    }

    /** Original addSkillExp also learns an unlearned passive, without its tree prerequisites. */
    public static void recordCritical(AbilityProgress state, int tier) {
        criticalRate(tier); // Validate the tier before changing either passive.
        if (!"teleporter".equals(state.category)) return;
        addLegacyPassiveExperience(state, DIM_FOLDING, (tier + 1) * .005f);
        addLegacyPassiveExperience(state, SPACE_FLUCT, .0001f);
    }

    private static void addLegacyPassiveExperience(AbilityProgress state, String id, float increment) {
        if (SkillCatalog.find(state.category, id).isEmpty()) return;
        state.addPassiveExperienceRaw(id,increment);
    }

    public static boolean canUse(AbilityProgress state) {
        return state != null && "teleporter".equals(state.category) && state.canUse(ID)
                && state.level <= 5
                && SkillCatalog.find(state.category, ID).filter(skill -> skill.controllable() && state.level >= skill.level()).isPresent();
    }

    private static boolean validPlayer(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator();
    }

    private static boolean onServerThread(ServerPlayer player) {
        return player != null && player.serverLevel().getServer().isSameThread();
    }

    private static boolean validSession(ServerPlayer player, Session session) {
        return session.identity().owner() == player && validPlayer(player)
                && (session.identity().input() == 0 || session.identity().owns(player))
                && player.getId() == session.entityId()
                && player.level().dimension().equals(session.dimension())
                && AbilityStorage.get(player) == session.state() && canUse(session.state())
                && !player.getMainHandItem().isEmpty();
    }

    private static boolean isNeedle(ItemStack stack) {
        return NEEDLE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static Trace trace(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition(), direction = player.getLookAngle();
        if (!finite(start) || !finite(direction) || direction.lengthSqr() < .99 || direction.lengthSqr() > 1.01) return null;
        Vec3 end = start.add(direction.scale(range));
        // Conservative, bounded loaded-chunk check before either querying entities or clipping blocks.
        // Inflate to include the target broad-phase boxes; this never asks getChunk to generate/load.
        AABB search = new AABB(start, end).inflate(1);
        BlockPos min = BlockPos.containing(search.minX, start.y, search.minZ);
        BlockPos max = BlockPos.containing(search.maxX, start.y, search.maxZ);
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++)
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++)
                if (!player.serverLevel().hasChunk(x, z)) return null;

        Entity nearest = null;
        double best = 0;
        // The classic first trace uses BlockSelectors.filEverything (reject all blocks):
        // a living target is preferred even behind a wall. Do not add a line-of-sight gate here.
        for (var candidate : player.serverLevel().getEntities(player, search,
                entity -> (entity instanceof LivingEntity || entity instanceof EnderDragonPart part && part.parentMob.isAlive())
                        && entity.isAlive() && !entity.isSpectator() && entity.isPickable())) {
            double distance = classicEntityDistanceSquared(candidate.getBoundingBox(), start, end);
            // Preserve LambdaLib's zero-distance tie quirk rather than changing target ordering.
            if (Double.isFinite(distance) && (distance < best || best == 0)) { best = distance; nearest = candidate; }
        }
        if (nearest != null) return new Trace(nearest.position().add(0, nearest.getBbHeight(), 0), nearest);
        var block = player.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return new Trace(block.getType() == HitResult.Type.MISS ? end : block.getLocation(), null);
    }

    /** LambdaLib 1.2.3 expands by float 0.3 and uses six-plane legacy calculateIntercept.
     * Unlike modern AABB.clip, starting inside the box selects its exit surface (if in range).
     * A wholly contained segment has no intercept. Infinity means a miss.
     */
    public static double classicEntityDistanceSquared(AABB originalBounds, Vec3 start, Vec3 end) {
        AABB bounds = originalBounds.inflate((double).3F);
        double best = Double.POSITIVE_INFINITY;
        double[] from = {start.x, start.y, start.z}, to = {end.x, end.y, end.z};
        double[] min = {bounds.minX, bounds.minY, bounds.minZ}, max = {bounds.maxX, bounds.maxY, bounds.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            double delta = to[axis] - from[axis];
            if (delta * delta < 1.0000000116860974E-7) continue;
            for (double plane : new double[]{min[axis], max[axis]}) {
                double fraction = (plane - from[axis]) / delta;
                if (fraction < 0 || fraction > 1) continue;
                int a = (axis + 1) % 3, b = (axis + 2) % 3;
                double coordinateA = from[a] + (to[a] - from[a]) * fraction;
                double coordinateB = from[b] + (to[b] - from[b]) * fraction;
                if (coordinateA < min[a] || coordinateA > max[a] || coordinateB < min[b] || coordinateB > max[b]) continue;
                best = Math.min(best, start.distanceToSqr(start.lerp(end, fraction)));
            }
        }
        return best;
    }

}
