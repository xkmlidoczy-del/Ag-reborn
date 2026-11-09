package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.DeflectionStrike;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.DamageMitigation;
import cn.academy.port.highpower.core.DefenseProfile;
import cn.academy.port.highpower.core.DeflectionStrikeRules;
import cn.academy.port.highpower.core.HighPowerLevel;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.minecraft.server.network.CommonListenerCookie;

/** NEW411 native input, geometry, exact budgets and actual vanilla combat event controls. */
@GameTestHolder("academy_highpower_vector")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerDeflectionStrikeRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_deflection_strike";
    private static final String ID = "deflection_strike", SKILL = "vecmanip.deflection_strike";
    private HighPowerDeflectionStrikeRuntimeTests() { }

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[] {32, 10, 32}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell);
        air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower_vector", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void two_native_hostiles_share_one_exact_budget_at_all_five_levels(GameTestHelper h) {
        double[] costs = {10, 5, 2, .5, .1}; int[] cooldowns = {10, 5, 4, 4, 4};
        for (int level = 1; level <= 5; level++) try (Fixture f = new Fixture(h, level)) {
            Zombie first = f.target(3, true), second = f.target(5, true), third = f.target(7, true);
            double cp = f.cp(); long tick = f.p.serverLevel().getGameTime();
            BigDecimal share = HighPowerLevel.forLevel(level).damage().divide(BigDecimal.valueOf(2));
            h.assertTrue(f.cast(), "authenticated Vector root cast at level " + level);
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(share), "nearest frozen hostile gets exactly half of one D");
            eq(h, hp(second), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(share), "second frozen hostile gets the other half");
            eq(h, hp(third), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "third collinear native hostile cannot receive another budget");
            near(h, f.cp(), cp - costs[level - 1], "the whole strike is charged only once");
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(ID, 0) == cooldowns[level - 1], "approved cooldown curve with four-tick physical minimum");
            h.assertTrue(f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_" + ID) == tick + cooldowns[level - 1],
                    "persistent cooldown stores the same curve without a second target resetting it");
            var receipts = DeflectionStrike.receipts(f.p);
            h.assertTrue(receipts.size() == 2 && receipts.get(0).victim().equals(first.getUUID())
                    && receipts.get(1).victim().equals(second.getUUID()), "bounded receipts retain front-to-back native identity");
            eq(h, receipts.get(0).damage().add(receipts.get(1).damage()), HighPowerLevel.forLevel(level).damage(), "receipt shares conserve the one-cast total");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void equal_entry_hostiles_keep_two_uuid_ordered_slots_for_strike_and_crouch(GameTestHelper h) {
        for (boolean crouch : List.of(false, true)) try (Fixture f = new Fixture(h, 1)) {
            f.p.setShiftKeyDown(crouch); Vec3 center = f.eye().add(0, 0, 5);
            // Deliberately reverse entity insertion order relative to the stable UUID tie order.
            Zombie third = f.targetAt(center, true, f.uuid(3)), first = f.targetAt(center, true, f.uuid(1)), second = f.targetAt(center, true, f.uuid(2));
            for (Zombie target : List.of(first, second, third)) if (crouch) target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            h.assertTrue(first.getBoundingBox().equals(second.getBoundingBox()) && first.getBoundingBox().equals(third.getBoundingBox()),
                    "three genuine native hostile bodies have exactly equal entry contacts");
            double cp = f.cp(); h.assertTrue(f.cast(), "equal-distance frozen hostile bodies still form one paid opportunity");
            BigDecimal share = HighPowerLevel.LV1.damage().divide(BigDecimal.valueOf(2));
            for (Zombie target : List.of(first, second)) {
                eq(h, hp(target), crouch ? cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth() : cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(share), "both equal-entry selected slots retain their original allocation");
                if (crouch) h.assertTrue(target.getDeltaMovement().z > 0, "both overlapping selected hostiles receive a native push");
            }
            eq(h, hp(third), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "UUID-last third body cannot obtain a damage share");
            vector(h, third.getDeltaMovement(), Vec3.ZERO, "UUID-last third body cannot obtain an extra push");
            var receipts = DeflectionStrike.receipts(f.p);
            h.assertTrue(receipts.size() == 2 && first.getUUID().equals(receipts.get(0).victim()) && second.getUUID().equals(receipts.get(1).victim()),
                    "stable UUID ordering survives callback revalidation of both equal-entry slots");
            vector(h, receipts.get(0).end(), receipts.get(1).end(), "equal native entries retain the same true contact point");
            near(h, f.cp(), cp - 10, "overlapping native bodies cost one cast");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void infinitesimal_native_entry_order_beats_reversed_uuid_order(GameTestHelper h) {
        for (boolean crouch : List.of(false, true)) try (Fixture f = new Fixture(h, 1)) {
            f.p.setShiftKeyDown(crouch); Vec3 center = f.eye().add(0, 0, 5);
            Zombie third = f.targetAt(center, true, f.uuid(1)), second = f.targetAt(center, true, f.uuid(2)), first = f.targetAt(center, true, f.uuid(3));
            double entry = entryZ(f, first), centerZ = center.z;
            for (Zombie target : List.of(second, third)) {
                double previous = entry;
                for (int step = 0; step < 8 && entryZ(f, target) <= previous; step++) {
                    centerZ = Math.nextUp(centerZ); f.center(target, new Vec3(center.x, center.y, centerZ));
                }
                entry = entryZ(f, target);
                h.assertTrue(entry > previous && entry - previous <= 8 * Math.ulp(previous), "next body differs by bounded native representable entry steps");
            }
            if (crouch) for (Zombie target : List.of(first, second, third)) target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            double cp = f.cp(); h.assertTrue(f.cast(), "infinitesimal distinct native contacts remain a paid bounded strike");
            BigDecimal share = HighPowerLevel.LV1.damage().divide(BigDecimal.valueOf(2));
            for (Zombie target : List.of(first, second)) {
                eq(h, hp(target), crouch ? cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth() : cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(share), "distance-first selected body keeps its share");
                if (crouch) h.assertTrue(target.getDeltaMovement().z > 0, "distance-first selected body gets native push");
            }
            eq(h, hp(third), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "UUID-first but microscopically rear body remains outside the two-target cap");
            vector(h, third.getDeltaMovement(), Vec3.ZERO, "rear third body cannot receive push through UUID tie logic");
            var receipts = DeflectionStrike.receipts(f.p);
            h.assertTrue(receipts.size() == 2 && first.getUUID().equals(receipts.get(0).victim()) && second.getUUID().equals(receipts.get(1).victim()),
                    "actual entry distance has strict priority over stable UUID tie breaking");
            h.assertTrue(receipts.get(0).end().z < receipts.get(1).end().z, "no distance epsilon collapses distinct native contacts into a tie");
            near(h, f.cp(), cp - 10, "near-coincident bodies still spend once");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void tied_nonhostile_and_solid_contacts_block_strike_and_crouch(GameTestHelper h) {
        for (boolean crouch : List.of(false, true)) for (boolean solid : List.of(false, true)) try (Fixture f = new Fixture(h, 1)) {
            f.p.setShiftKeyDown(crouch); Zombie first = f.targetAt(f.eye().add(0, 0, 5), true, f.uuid(1)), second = f.target(7, true);
            if (crouch) for (Zombie target : List.of(first, second)) target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            if (solid) {
                BlockPos wall = BlockPos.containing(f.eye().add(0, 0, 4)); f.block(Vec3.atCenterOf(wall));
                f.center(first, new Vec3(f.eye().x, f.eye().y, wall.getZ() + first.getBbWidth() / 2.0));
                double solidEntry = new net.minecraft.world.phys.AABB(wall).inflate(.035).clip(f.eye(), f.eye().add(0, 0, 8)).orElseThrow().z;
                h.assertTrue(entryZ(f, first) == solidEntry, "solid and hostile entry coincide exactly in native coordinates");
            } else {
                Cow cow = new Cow(EntityType.COW, h.getLevel()); cow.setUUID(f.uuid(3));
                f.add(cow, new Vec3(f.eye().x, f.eye().y, first.getBoundingBox().minZ + cow.getBbWidth() / 2.0));
                h.assertTrue(entryZ(f, cow) == entryZ(f, first), "native nonhostile and hostile first contacts are exactly tied");
            }
            double cp = f.cp(); h.assertFalse(f.cast(), "solid or nonhostile tie takes precedence over hostile admission for both gestures");
            for (Zombie target : List.of(first, second)) {
                eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "tied physical blocker prevents all damage");
                vector(h, target.getDeltaMovement(), Vec3.ZERO, "tied physical blocker prevents all pushes");
            }
            near(h, f.cp(), cp, "blocked equal-entry opportunity is free"); f.assertBlocks();
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void callback_introduced_tied_nonhostile_cannot_be_skipped_by_equal_entry_slots(GameTestHelper h) {
        for (boolean crouch : List.of(false, true)) try (Fixture f = new Fixture(h, 1)) {
            f.p.setShiftKeyDown(crouch); Vec3 center = f.eye().add(0, 0, 5);
            Zombie first = f.targetAt(center, true, f.uuid(1)), second = f.targetAt(center, true, f.uuid(2));
            if (crouch) for (Zombie target : List.of(first, second)) target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            boolean[] called = {false}; Runnable insert = () -> {
                if (called[0]) return; called[0] = true;
                Cow cow = new Cow(EntityType.COW, h.getLevel()); cow.setUUID(f.uuid(3));
                f.add(cow, new Vec3(center.x, center.y, first.getBoundingBox().minZ + cow.getBbWidth() / 2.0));
                h.assertTrue(entryZ(f, cow) == entryZ(f, first), "new callback body has exactly tied contact, not an earlier surrogate");
            };
            f.hook(new Object() {
                @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) { if (!crouch && event.getEntity() == first) insert.run(); }
                @SubscribeEvent public void knockback(LivingKnockBackEvent event) { if (crouch && event.getEntity() == first) insert.run(); }
            });
            double cp = f.cp(); h.assertTrue(f.cast() && called[0], "genuine native callback inserts a newly blocking tied body after payment");
            for (Zombie target : List.of(first, second)) {
                eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "callback body blocks current and remaining frozen damage shares");
                vector(h, target.getDeltaMovement(), Vec3.ZERO, "callback body blocks current and remaining native impulses");
            }
            near(h, f.cp(), cp - 10, "lost authority retains exactly one irreversible emission payment");
        }
        h.succeed();
    }
    private static double entryZ(Fixture fixture, LivingEntity target) {
        return target.getBoundingBox().inflate(.035).clip(fixture.eye(), fixture.eye().add(0, 0, 8)).orElseThrow().z;
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void thin_straight_segment_respects_radius_and_reach_endpoint(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 1)) {
            Zombie target = f.target(5, true);
            double half = target.getBbWidth() / 2.0;
            if (mode == 0 || mode == 1) f.center(target, f.eye().add(half + (mode == 0 ? .034 : .036), 0, 5));
            else f.center(target, f.eye().add(0, 0, DeflectionStrikeRules.RANGE + half + (mode == 2 ? -.01 : .05)));
            double cp = f.cp(); BigDecimal before = hp(target); boolean hit = mode == 0 || mode == 2;
            h.assertTrue(f.cast() == hit, "only actual thin-segment contact within configured reach is admitted, case " + mode);
            eq(h, hp(target), hit ? before.subtract(HighPowerLevel.LV1.damage()) : before, "no broad nearby-body targeting or extra range");
            near(h, f.cp(), cp - (hit ? 10 : 0), "geometric misses are free");
            if (hit) for (var receipt : DeflectionStrike.receipts(f.p)) {
                h.assertTrue(receipt.origin().distanceTo(receipt.end()) <= DeflectionStrikeRules.RANGE + .000001, "visible contact remains within approved straight range");
                h.assertTrue(Math.abs(receipt.end().x - f.eye().x) < .000001 && Math.abs(receipt.end().y - f.eye().y) < .000001,
                        "impact path is straight, without steering toward an offset body center");
            }
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void exact_closed_endpoint_survives_native_broadphase_but_next_outside_body_misses(GameTestHelper h) {
        for (boolean outside : List.of(false, true)) try (Fixture f = new Fixture(h, 1)) {
            Vec3 endpoint = f.eye().add(0, 0, DeflectionStrikeRules.RANGE); Zombie target = f.target(5, true);
            double centerZ = endpoint.z + target.getBbWidth() / 2.0 + .035;
            f.center(target, new Vec3(endpoint.x, endpoint.y, centerZ));
            // Align a genuine native body by representable positions, without changing its dimensions.
            for (int step = 0; step < 8 && target.getBoundingBox().inflate(.035).minZ != endpoint.z; step++) {
                double entry = target.getBoundingBox().inflate(.035).minZ;
                centerZ = Math.nextAfter(centerZ, entry < endpoint.z ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
                f.center(target, new Vec3(endpoint.x, endpoint.y, centerZ));
            }
            h.assertTrue(target.getBoundingBox().inflate(.035).minZ == endpoint.z,
                    "actual native inflated body begins at the exact reach endpoint at this world's coordinates");
            if (outside) {
                for (int step = 0; step < 8 && target.getBoundingBox().inflate(.035).minZ <= endpoint.z; step++) {
                    centerZ = Math.nextUp(centerZ); f.center(target, new Vec3(endpoint.x, endpoint.y, centerZ));
                }
                h.assertTrue(target.getBoundingBox().inflate(.035).minZ > endpoint.z, "first representable outside body has no legal contact");
            }
            int[] incoming = {0};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() == target) incoming[0]++;
            } });
            double cp = f.cp(); h.assertTrue(f.cast() != outside, "closed endpoint admits exactly its native contact, never an outward range epsilon");
            eq(h, hp(target), outside ? cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth() : cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(HighPowerLevel.LV1.damage()),
                    "exact endpoint remains valid through the real native callback and final commit");
            h.assertTrue(incoming[0] == (outside ? 0 : 1), "actual endpoint contact executes once through guarded native hurt");
            near(h, f.cp(), cp - (outside ? 0 : 10), "one-ULP broadphase recovery cannot expand narrowphase billing");
            if (!outside) vector(h, DeflectionStrike.receipts(f.p).getFirst().end(), endpoint, "receipt ends at the actual fixed closed boundary");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void diagonal_exact_radius_tangency_does_not_inherit_native_clip_tolerance(GameTestHelper h) {
        for (boolean outside : List.of(false, true)) try (Fixture f = new Fixture(h, 1)) {
            f.p.setYRot(-45); Vec3 axis = f.p.getLookAngle().normalize();
            Vec3 endpoint = boundedEndpoint(f.eye(), axis), segment = endpoint.subtract(f.eye());
            Zombie target = f.target(5, true); double half = target.getBbWidth() / 2.0;
            Vec3 touch = f.eye().add(segment.scale(.625));
            double centerZ = touch.z + half + .035, centerX = touch.x - half - .035;
            f.center(target, new Vec3(centerX, touch.y, centerZ));
            // A diagonal tangent keeps the body inside the broad query even when it moves
            // one representable step outside the real ray. Native clip's 1e-7 tolerance
            // must not silently widen the approved .035-radius narrow phase.
            double entryZ = target.getBoundingBox().inflate(.035).minZ;
            // The approved segment has a representable endpoint. At mixed coordinate
            // magnitudes its X/Z rounding differs; tangency belongs to that segment,
            // not to an idealized unit-vector slope that the server never traces.
            double rayX = f.eye().x + (entryZ - f.eye().z) / segment.z * segment.x;
            centerX = rayX - half - .035;
            f.center(target, new Vec3(centerX, touch.y, centerZ));
            for (int step = 0; step < 8 && target.getBoundingBox().inflate(.035).maxX != rayX; step++) {
                double face = target.getBoundingBox().inflate(.035).maxX;
                centerX = Math.nextAfter(centerX, face < rayX ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
                f.center(target, new Vec3(centerX, touch.y, centerZ));
            }
            h.assertTrue(target.getBoundingBox().inflate(.035).maxX == rayX, "genuine native body has exact closed diagonal radius tangency");
            if (outside) {
                for (int step = 0; step < 8 && target.getBoundingBox().inflate(.035).maxX >= rayX; step++) {
                    centerX = Math.nextDown(centerX); f.center(target, new Vec3(centerX, touch.y, centerZ));
                }
                h.assertTrue(target.getBoundingBox().inflate(.035).maxX < rayX, "first representable side step lies strictly outside radius");
            }
            double cp = f.cp(); h.assertTrue(f.cast() != outside, "exact diagonal tangency hits but a native clipping epsilon cannot reach the next body position: outside=" + outside
                    + " origin=" + f.eye() + " endpoint=" + endpoint + " rayX=" + rayX + " bounds=" + target.getBoundingBox().inflate(.035));
            eq(h, hp(target), outside ? cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth() : cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(HighPowerLevel.LV1.damage()), "native narrow-phase side boundary controls actual HP");
            near(h, f.cp(), cp - (outside ? 0 : 10), "an outside native clip candidate is free");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void recorded_far_and_mixed_precision_origins_keep_actual_native_diagonal_boundary(GameTestHelper h) {
        // Preserve the passing R4 origin and both recorded mixed-precision failures.
        for (Vec3 origin : List.of(new Vec3(12_310_359.5, -55.37999999523163, -11_307_672.5),
                new Vec3(8_168_111.5, -55.37999999523163, -3_134.5),
                new Vec3(-33_368.5, -55.37999999523163, -6_108_631.5))) {
            Vec3 endpoint = boundedEndpoint(origin, new Vec3(1, 0, 1).normalize()), segment = endpoint.subtract(origin);
            Vec3 touch = origin.add(segment.scale(.625)); double half = (double) .6F / 2;
            double centerZ = touch.z + half + .035, centerX = touch.x - half - .035;
            net.minecraft.world.phys.AABB body = boundaryBody(centerX, origin.y, centerZ, half);
            double entryZ = body.inflate(.035).minZ;
            double rayX = origin.x + (entryZ - origin.z) / segment.z * segment.x;
            centerX = rayX - half - .035;
            body = boundaryBody(centerX, origin.y, centerZ, half);
            for (int step = 0; step < 8 && body.inflate(.035).maxX != rayX; step++) {
                centerX = Math.nextAfter(centerX, body.inflate(.035).maxX < rayX ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
                body = boundaryBody(centerX, origin.y, centerZ, half);
            }
            h.assertTrue(body.inflate(.035).maxX == rayX, "recorded coordinate has exact actual-segment tangent");
            var tangent = nativeContact(body.inflate(.035), origin, endpoint);
            h.assertTrue(tangent.isPresent() && tangent.orElseThrow().x <= body.inflate(.035).maxX,
                    "actual native clip accepts the closed tangent at recorded origin " + origin);
            for (int step = 0; step < 8 && body.inflate(.035).maxX >= rayX; step++) {
                centerX = Math.nextDown(centerX); body = boundaryBody(centerX, origin.y, centerZ, half);
            }
            h.assertTrue(body.inflate(.035).maxX < rayX && nativeContact(body.inflate(.035), origin, endpoint).isEmpty(),
                    "first representable outside body is rejected at recorded origin " + origin);
        }
        h.succeed();
    }
    private static net.minecraft.world.phys.AABB boundaryBody(double x, double y, double z, double half) {
        return new net.minecraft.world.phys.AABB(x - half, y - 1, z - half, x + half, y + 1, z + half);
    }
    @SuppressWarnings("unchecked")
    private static java.util.Optional<Vec3> nativeContact(net.minecraft.world.phys.AABB box, Vec3 start, Vec3 end) {
        try {
            var method = DeflectionStrike.class.getDeclaredMethod("contact", net.minecraft.world.phys.AABB.class, Vec3.class, Vec3.class);
            method.setAccessible(true); return (java.util.Optional<Vec3>) method.invoke(null, box, start, end);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Native contact regression reflection failed", failure); }
    }
    private static Vec3 boundedEndpoint(Vec3 origin, Vec3 axis) {
        var result = DeflectionStrikeRules.endpoint(new DeflectionStrikeRules.Point(origin.x, origin.y, origin.z),
                new DeflectionStrikeRules.Point(axis.x, axis.y, axis.z));
        if (result == null) throw new AssertionError("Valid diagonal fixture has no bounded endpoint");
        return new Vec3(result.x(), result.y(), result.z());
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void solid_walls_and_nonhostile_or_unknown_bodies_block_without_terrain_changes(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 1)) {
            Zombie target = f.target(6, true); LivingEntity blocker = null;
            if (mode == 0) f.block(f.eye().add(0, 0, 3));
            else if (mode == 1) { Cow cow = new Cow(EntityType.COW, h.getLevel()); f.add(cow, f.eye().add(0, 0, 3)); blocker = cow; }
            else if (mode == 2) { Husk unknown = new Husk(EntityType.HUSK, h.getLevel()) { }; f.add(unknown, f.eye().add(0, 0, 3)); blocker = unknown; }
            else { blocker = f.target(3, false); blocker.setInvulnerable(true); }
            float blockHealth = blocker == null ? 0 : blocker.getHealth(); double cp = f.cp();
            h.assertFalse(f.cast(), "unapproved first obstruction refuses native hostile behind it, case " + mode);
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "no targeting through solid or protected body");
            near(h, f.cp(), cp, "zero admitted target spends no CP");
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(ID, 0) == 0 && AbilityStorage.get(f.p).exp(ID) == 0,
                    "blocked empty strike creates no cooldown or mastery");
            if (blocker != null) h.assertTrue(blocker.getHealth() == blockHealth, "blocker is not damaged or silently enrolled");
            f.assertBlocks();
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void intervening_nonhostile_ends_path_after_first_hostile_without_rebudgeting(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            Zombie first = f.target(2, true), hidden = f.target(6, true);
            Cow cow = new Cow(EntityType.COW, h.getLevel()); f.add(cow, f.eye().add(0, 0, 4));
            h.assertTrue(f.cast(), "front hostile is still a valid strike opportunity");
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(HighPowerLevel.LV1.damage()), "one genuinely selected target gets one whole D");
            eq(h, hp(hidden), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "nonhostile terminates the straight path before rear hostile");
            h.assertTrue(cow.getHealth() == cow.getMaxHealth() && DeflectionStrike.receipts(f.p).size() == 1, "no extra nonhostile or rear-target attack");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void later_invalidated_share_is_lost_and_never_reassigned_to_a_newcomer(GameTestHelper h) {
        for (boolean cancelFirst : List.of(false, true)) try (Fixture f = new Fixture(h, 1)) {
            Zombie first = f.target(3, true), second = f.target(5, true), third = f.target(7, true);
            Vec3 oldSecond = second.getBoundingBox().getCenter(); boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != first || called[0]) return; called[0] = true;
                f.center(second, oldSecond.add(3, 0, 0));
                if (cancelFirst) event.setCanceled(true);
            } });
            double cp = f.cp(); h.assertTrue(f.cast(), "frozen two-target cast reaches the first native callback");
            h.assertTrue(called[0], "actual first native incoming event invalidated the later frozen body");
            BigDecimal share = HighPowerLevel.LV1.damage().divide(BigDecimal.valueOf(2));
            eq(h, hp(first), cancelFirst ? cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth() : cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(share), "cancellation does not redistribute original share");
            eq(h, hp(second), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "moved frozen target cannot be hit by stale contact");
            eq(h, hp(third), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "third eligible body cannot replace an invalidated frozen slot");
            near(h, f.cp(), cp - 10, "native cancellation never refunds or multiplies paid emission cost");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void native_armor_toughness_protection_resistance_and_absorption_match_controls(GameTestHelper h) {
        defenseCase(h, true);
    }
    private static void defenseCase(GameTestHelper h, boolean exact) {
        Fixture f = new Fixture(h, 1); Zombie target = f.target(5, exact), control = f.targetAt(f.eye().add(6, 0, 5), false);
        defenses(h, target); defenses(h, control); f.discount(target); Observation hit = f.observe(target), nativeHit = f.observe(control);
        h.runAfterDelay(2, () -> {
            BigDecimal before = hp(target); Vec3 origin = f.eye();
            h.assertTrue(control.hurt(f.source(origin.add(control.position().subtract(target.position()))), 5), "ordinary native PLAYER_ATTACK control accepts");
            h.assertTrue(f.cast(), "authenticated NEW411 emits one actual native hit");
            h.assertTrue(hit.posts == 1 && nativeHit.posts == 1 && hit.armor > 0 && hit.effects > 0 && hit.enchantments > 0 && hit.absorption == .5F,
                    "real native pipeline applies every partial reducer exactly once");
            h.assertTrue(hit.armor == nativeHit.armor && hit.effects == nativeHit.effects && hit.enchantments == nativeHit.enchantments
                    && hit.absorption == nativeHit.absorption && Math.abs(hit.damage - nativeHit.damage) < .00001,
                    "same native reductions and residual as an ordinary player-attack source");
            if (exact) {
                BigDecimal residual = DamageMitigation.mitigate(n("5"), new DefenseProfile(BigDecimal.valueOf(target.getArmorValue()),
                        BigDecimal.valueOf(target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)), 1, 4)).subtract(n(".5"));
                eq(h, hp(target), before.subtract(residual), "exact target retains native mitigated damage without float scaling");
            } else near(h, target.getHealth(), control.getHealth(), "ordinary native target retains ordinary float damage");
            h.assertTrue(target.getAbsorptionAmount() == 0 && control.getAbsorptionAmount() == 0, "native absorption consumed once on both paths");
            assertSource(h, hit.source, f.p, origin); f.close();
            if (exact) defenseCase(h, false); else h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 50)
    public static void native_front_and_rear_held_shields_match_the_actual_eye_origin(GameTestHelper h) { shieldCase(h, 0); }
    private static void shieldCase(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h, 1); Zombie target = f.target(5, mode < 2), control = f.targetAt(f.eye().add(6, 0, 5), false);
        float yaw = mode % 2 == 0 ? 180 : 0; shield(target, yaw); shield(control, yaw); f.discount(target);
        Observation hit = f.observe(target), nativeHit = f.observe(control);
        h.runAfterDelay(7, () -> {
            face(target, yaw); face(control, yaw); h.assertTrue(target.isBlocking() && control.isBlocking(), "native shield raising delay completed");
            Vec3 origin = f.eye(); BigDecimal before = hp(target); boolean blocked = mode % 2 == 0;
            boolean accepted = control.hurt(f.source(origin.add(control.position().subtract(target.position()))), 5);
            double cp = f.cp(); h.assertTrue(f.cast(), "NEW411 pays before actual native shield decision");
            h.assertTrue(accepted != blocked && hit.blocked == blocked && nativeHit.blocked == blocked, "front shield blocks, rear shield permits actual eye-origin strike");
            eq(h, hp(target), blocked ? before : before.subtract(n("5")), "native shield direction governs both exact and ordinary HP");
            h.assertTrue(hit.blockedDamage == nativeHit.blockedDamage && hit.damage == nativeHit.damage, "native shield amounts match ordinary control");
            near(h, f.cp(), cp - 10, "full native shield block keeps already paid CP");
            assertSource(h, hit.source, f.p, origin); f.close();
            if (mode < 3) shieldCase(h, mode + 1); else h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void native_cancellation_zero_damage_and_full_absorption_never_become_exact_damage(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 1)) {
            Zombie target = f.target(5, exact); f.discount(target); int kind = mode; boolean[] called = {false};
            f.hook(new Object() {
                @SubscribeEvent public void skill(SkillAttackEvent event) { if (event.target == target && kind == 0) { called[0] = true; event.amount = 0; } }
                @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) { if (event.getEntity() == target && kind == 1) { called[0] = true; event.setCanceled(true); } }
                @SubscribeEvent public void pre(LivingDamageEvent.Pre event) { if (event.getEntity() == target && kind == 2) { called[0] = true; event.setNewDamage(0); } }
            });
            if (mode == 3) { target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 2)); target.setAbsorptionAmount(10); }
            BigDecimal before = hp(target); double cp = f.cp(); h.assertTrue(f.cast(), "real paid native cast enters protection case " + mode);
            eq(h, hp(target), before, "native protection cannot produce exact or ordinary HP damage");
            near(h, f.cp(), cp - 10, "paid native attempt is not refunded by a later veto");
            if (mode < 3) { h.assertTrue(called[0], "real requested native event executed"); h.assertTrue(AbilityStorage.get(f.p).exp(ID) == 0, "vetoed attempt cannot farm mastery"); }
            else h.assertTrue(target.getAbsorptionAmount() == 5, "full native absorption spends its real amount only once");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void real_native_iframes_reject_equal_strike_without_spending_absorption_twice(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) try (Fixture f = new Fixture(h, 1)) {
            Zombie target = f.target(5, exact); f.discount(target);
            target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 2)); target.setAbsorptionAmount(10);
            Observation hit = f.observe(target); BigDecimal before = hp(target);
            h.assertTrue(target.hurt(f.p.damageSources().playerAttack(f.p), 5), "ordinary native hit starts real hurt cooldown");
            h.assertTrue(target.getAbsorptionAmount() == 5 && target.invulnerableTime > 10, "native iframe and prior absorption are established");
            double cp = f.cp(); h.assertTrue(f.cast(), "paid controller attempt reaches iframe decision");
            eq(h, hp(target), before, "native iframe cannot be bypassed by exact bridge");
            h.assertTrue(target.getAbsorptionAmount() == 5 && hit.posts == 1, "rejected equal strike cannot re-run absorption or post damage");
            near(h, f.cp(), cp - 10, "native iframe does not refund the strike");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void crouch_uses_native_knockback_resistance_and_cancellation_with_zero_damage(GameTestHelper h) {
        for (int mode = 0; mode < 5; mode++) try (Fixture f = new Fixture(h, 1)) {
            f.p.setShiftKeyDown(true); Zombie target = f.target(5, true), control = f.targetAt(f.eye().add(6, 0, 5), false);
            double resistance = mode == 1 ? .5 : mode == 2 ? 1 : 0;
            Vec3 initial = new Vec3(.08, .03, -.02), changed = new Vec3(.31, -.04, .12);
            for (Zombie mob : List.of(target, control)) { mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(resistance); mob.setOnGround(mode != 4); mob.setDeltaMovement(initial); }
            int[] calls = {0}; final boolean cancel = mode == 3;
            f.hook(new Object() { @SubscribeEvent public void knockback(LivingKnockBackEvent event) {
                if (event.getEntity() != target && event.getEntity() != control) return; calls[0]++;
                near(h, event.getStrength(), .35, "the native knockback request is exactly the approved strength");
                if (cancel) { event.getEntity().setDeltaMovement(changed); event.setCanceled(true); }
            } });
            Observation hit = f.observe(target); BigDecimal before = hp(target); double cp = f.cp();
            control.knockback(.35, 0, -1); h.assertTrue(f.cast(), "authenticated crouch strike follows the same real body path");
            eq(h, hp(target), before, "crouch emits exactly zero HP damage");
            h.assertTrue(hit.incoming == 0 && hit.posts == 0 && hit.skills == 0, "push does not impersonate a zero-damage native attack");
            vector(h, target.getDeltaMovement(), control.getDeltaMovement(), "native knockback and resistance match ordinary control");
            h.assertTrue(calls[0] == 2, "both paths use native LivingKnockBackEvent exactly once");
            near(h, f.cp(), cp - 10, "crouch pays once even when native knockback is resisted or canceled");
            for (var receipt : DeflectionStrike.receipts(f.p)) eq(h, receipt.damage(), BigDecimal.ZERO, "crouch receipt never carries damage authority");
            if (cancel) vector(h, target.getDeltaMovement(), changed, "canceled event's external velocity write is never overwritten");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void crouch_blocked_paths_and_two_target_limit_use_the_same_geometry(GameTestHelper h) {
        for (boolean blocked : List.of(false, true)) try (Fixture f = new Fixture(h, 1)) {
            f.p.setShiftKeyDown(true); Zombie first = f.target(3, true), second = f.target(5, true), third = f.target(7, true);
            for (Zombie target : List.of(first, second, third)) target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            if (blocked) f.block(f.eye().add(0, 0, 2)); double cp = f.cp();
            h.assertTrue(f.cast() != blocked, "crouch obeys identical solid segment admission");
            for (Zombie target : List.of(first, second, third)) eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "crouch never debits any native target HP");
            h.assertTrue((first.getDeltaMovement().lengthSqr() > 0) != blocked && (second.getDeltaMovement().lengthSqr() > 0) != blocked,
                    "only the two admitted targets receive ordinary native push");
            vector(h, third.getDeltaMovement(), Vec3.ZERO, "third hostile receives no extra push");
            near(h, f.cp(), cp - (blocked ? 0 : 10), "one opportunity costs once; blocked crouch is free"); f.assertBlocks();
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void crouch_callbacks_cannot_amplify_reverse_or_reenter_the_native_push(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 1)) {
            f.p.setShiftKeyDown(true); Zombie first = f.target(3, true), second = f.target(5, true);
            first.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            second.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            int kind = mode; boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent public void knockback(LivingKnockBackEvent event) {
                if (event.getEntity() != first || called[0]) return; called[0] = true;
                switch (kind) {
                    case 0 -> DeflectionStrike.abort(f.p);
                    case 1 -> event.setStrength(.7F);
                    case 2 -> event.setRatioZ(-event.getRatioZ());
                    default -> {
                        h.assertFalse(f.cast(), "fresh input cannot recursively enter a native push callback");
                        first.knockback(.35, 0, -1);
                    }
                }
            } });
            double cp = f.cp(); h.assertTrue(f.cast() && called[0], "real paid crouch reaches native mutation callback");
            vector(h, first.getDeltaMovement(), Vec3.ZERO, "invalid native knockback callback cannot force a stale or expanded impulse");
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "failed push leaves exact health untouched");
            eq(h, hp(second), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "remaining push slot also remains zero-damage");
            if (mode == 0) vector(h, second.getDeltaMovement(), Vec3.ZERO, "abort retires every remaining native push slot");
            near(h, f.cp(), cp - 10, "callback invalidation preserves the single paid attempt");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void callbacks_cannot_reenter_or_replay_the_paid_frozen_emission(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            Zombie first = f.target(3, true), second = f.target(5, true); String wire = f.wire(); int[] callbacks = {0};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != first) return; callbacks[0]++;
                h.assertFalse(DeflectionStrike.tap(f.p, wire), "replayed actual wire cannot enter its own native callback");
                h.assertFalse(f.cast(), "fresh input identity cannot recursively enter a processing controller");
            } });
            double cp = f.cp(); h.assertTrue(DeflectionStrike.tap(f.p, wire), "outer native input remains authoritative");
            h.assertTrue(callbacks[0] == 1, "the first target's native pipeline executes once");
            BigDecimal share = HighPowerLevel.LV1.damage().divide(BigDecimal.valueOf(2));
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(share), "no reentrant first-target debit");
            eq(h, hp(second), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(share), "no reentrant second-target debit");
            near(h, f.cp(), cp - 10, "only one cast payment survives nested calls");
            h.assertFalse(DeflectionStrike.tap(f.p, wire), "same completed input cannot replay afterward");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void captured_source_cannot_recurse_change_victim_or_replay_after_completion(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) for (boolean wrongVictim : List.of(false, true)) try (Fixture f = new Fixture(h, 1)) {
            Zombie target = f.target(5, exact), other = f.targetAt(f.eye().add(5, 0, 5), exact);
            f.discount(target); BigDecimal before = hp(target), otherBefore = hp(other); DamageSource[] captured = {null};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != target || captured[0] != null) return; captured[0] = event.getSource();
                h.assertFalse((wrongVictim ? other : target).hurt(captured[0], 5), "captured native source grants neither recursive hurt nor another victim");
            } });
            double cp = f.cp(); h.assertTrue(f.cast() && captured[0] != null, "real paid strike exposes its source only inside an actual native callback");
            eq(h, hp(target), before, "nested hurt invalidates outer authority before exact or native HP commit");
            eq(h, hp(other), otherBefore, "captured source cannot transfer target-bound authority");
            near(h, f.cp(), cp - 10, "poisoned native attempt remains one paid cast");
            h.assertFalse(other.hurt(captured[0], 5), "completed source cannot replay against any later target");
            eq(h, hp(other), otherBefore, "late replay remains inert");
            h.assertTrue(other.hurt(f.p.damageSources().playerAttack(f.p), 5), "unrelated ordinary native combat still functions after refused nested source");
            eq(h, hp(other), otherBefore.subtract(n("5")), "ordinary combat uses its own five points and cleans up native frame state");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void amplification_and_post_absorption_callback_mutation_refuse_before_hp_commit(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) for (int mode = 0; mode < 5; mode++) try (Fixture f = new Fixture(h, 1)) {
            Zombie target = f.target(5, exact); if (mode != 0) f.discount(target);
            target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); target.setAbsorptionAmount(.5F);
            BigDecimal before = hp(target); int kind = mode; boolean[] called = {false};
            f.hook(new Object() {
                @SubscribeEvent public void skill(SkillAttackEvent event) {
                    if (event.target == target && kind == 0) { called[0] = true; event.amount *= 2; }
                }
                @SubscribeEvent(priority = EventPriority.LOWEST) public void incoming(LivingIncomingDamageEvent event) {
                    if (event.getEntity() != target) return;
                    if (kind == 1) { called[0] = true; event.setAmount(event.getAmount() * 2); }
                    if (kind == 3 || kind == 4) event.addReductionModifier(DamageContainer.Reduction.ABSORPTION, (container, reduction) -> {
                        called[0] = true;
                        if (kind == 3) container.setNewDamage(container.getNewDamage() * 2);
                        else target.setPos(target.position().add(3, 0, 0));
                        return reduction;
                    });
                }
                @SubscribeEvent(priority = EventPriority.LOWEST) public void pre(LivingDamageEvent.Pre event) {
                    if (event.getEntity() == target && kind == 2) { called[0] = true; event.setNewDamage(event.getNewDamage() * 2); }
                }
            });
            double cp = f.cp(); h.assertTrue(f.cast() && called[0], "genuine native callback reaches amplification or final-commit mutation case " + mode);
            eq(h, hp(target), before, "closed share cannot be amplified or moved after native absorption observers");
            near(h, f.cp(), cp - 10, "native observer refusal cannot refund or duplicate payment");
            h.assertTrue(target.getAbsorptionAmount() == .5F, "rejected final native commit cannot consume absorption after authority is lost");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void native_callback_owner_or_contact_invalidation_retires_remaining_authority(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) for (int mode = 0; mode < 9; mode++) try (Fixture f = new Fixture(h, 1)) {
            Zombie first = f.target(3, exact), second = f.target(5, exact); f.discount(first); f.discount(second);
            BigDecimal firstHp = hp(first), secondHp = hp(second); int kind = mode; boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != first || called[0]) return; called[0] = true;
                switch (kind) {
                    case 0 -> AbilityStorage.get(f.p).category = "electromaster";
                    case 1 -> AbilityStorage.get(f.p).presets.switchTo(1);
                    case 2 -> LegacySingleKeyOwnerEpoch.forget(f.p);
                    case 3 -> f.p.setHealth(0);
                    case 4 -> DeflectionStrike.abort(f.p);
                    case 5 -> AbilityStorage.remove(f.p);
                    case 6 -> first.setPos(first.position().add(3, 0, 0));
                    case 7 -> first.setBoundingBox(first.getBoundingBox().inflate(.05));
                    default -> f.block(f.eye().add(0, 0, 1));
                }
            } });
            h.assertTrue(f.cast(), "native admission pays before callback invalidation " + mode);
            h.assertTrue(called[0], "actual native incoming callback mutates authority");
            eq(h, hp(first), firstHp, "late guard protects current exact and ordinary body");
            // A target-only mutation can invalidate one share while retaining the second original slot.
            if (mode < 6 || mode == 8) eq(h, hp(second), secondHp, "owner or obstruction invalidation also fences later frozen slots");
            f.assertBlocks();
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void native_pre_damage_geometry_mutation_protects_exact_and_ordinary_health(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) try (Fixture f = new Fixture(h, 1)) {
            Zombie target = f.target(5, exact); f.discount(target); BigDecimal before = hp(target); boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent public void pre(LivingDamageEvent.Pre event) {
                if (event.getEntity() == target) { called[0] = true; target.setPos(target.position().add(3, 0, 0)); }
            } });
            h.assertTrue(f.cast() && called[0], "real final native damage event mutates captured body");
            eq(h, hp(target), before, "final native guard catches geometry changes after incoming stage");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void generic_named_source_and_forged_wire_cannot_mint_vector_damage(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            Zombie target = f.target(5, true); double cp = f.cp();
            h.assertFalse(HighPowerCombat.attack(f.p, SKILL, target, n("5")), "generic source text is not a live Vector emission permit");
            long epoch = LegacySingleKeyOwnerEpoch.current(f.p, f.p.connection, f.p.serverLevel(), AbilityStorage.get(f.p));
            h.assertFalse(DeflectionStrike.tap(f.p, new HighPowerInput.Input(0, 1, f.p.getId() + 100, epoch).wire()), "different network actor refuses");
            h.assertFalse(DeflectionStrike.tap(f.p, new HighPowerInput.Input(0, 2, f.p.getId(), epoch + 1).wire()), "different native session epoch refuses");
            h.assertFalse(DeflectionStrike.tap(f.p, new HighPowerInput.Input(1, 3, f.p.getId(), epoch).wire()), "unbound slot refuses");
            h.assertFalse(DeflectionStrike.tap(f.p, "0:0001:" + f.p.getId() + ":" + epoch), "noncanonical replay spelling refuses");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "all unauthenticated calls leave exact HP unchanged"); near(h, f.cp(), cp, "forgery cannot debit CP");
            h.assertTrue(target.hurt(f.p.damageSources().playerAttack(f.p), 5), "ordinary native player attack remains available");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(n("5")), "ordinary native damage retains its own units");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void level_five_physical_interval_survives_abort_native_nbt_and_owner_epoch_reload(GameTestHelper h) {
        Fixture f = new Fixture(h, 5); Zombie first = f.target(5, true); long tick = h.getLevel().getGameTime();
        h.assertTrue(f.cast(), "initial level-five strike"); DeflectionStrike.abort(f.p);
        AbilityStorage.save(f.p); CompoundTag saved = f.p.saveWithoutId(new CompoundTag());
        DeflectionStrike.remove(f.p); AbilityStorage.remove(f.p); LegacySingleKeyOwnerEpoch.forget(f.p); f.p.load(saved); f.place(); f.inputs = 0;
        first.discard(); Zombie next = f.target(5, true);
        h.runAfterDelay(1, () -> {
            f.p.doTick(); f.place(); double cp = f.cp();
            h.assertTrue(h.getLevel().getGameTime() - tick < 4, "attempt really precedes four-tick physical boundary");
            h.assertFalse(f.cast(), "a new owner epoch cannot erase the four-tick minimum");
            near(h, f.cp(), cp, "premature new-session attempt is free"); eq(h, hp(next), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "no physical replay after reload");
        });
        h.runAfterDelay(5, () -> {
            f.p.doTick(); f.place(); f.center(next, f.eye().add(0, 0, 5)); double cp = f.cp();
            h.assertTrue(h.getLevel().getGameTime() - tick >= 4 && f.cast(), "later authentic native input works after physical fence expires");
            near(h, f.cp(), cp - .1, "legitimate later strike pays once");
            eq(h, hp(next), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(HighPowerLevel.LV5.damage()), "reloaded owner retains one-D budget"); h.succeed();
        });
    }

    private static BigDecimal n(String text) { return new BigDecimal(text); }
    private static BigDecimal hp(LivingEntity entity) { return HighPowerCombat.enabled(entity) ? n(HighPowerCombat.healthText(entity)) : new BigDecimal(Float.toString(entity.getHealth())); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-5, why + ": " + actual + " != " + expected); }
    private static void vector(GameTestHelper h, Vec3 actual, Vec3 expected, String why) { h.assertTrue(actual.distanceToSqr(expected) < 1e-10, why + ": " + actual + " != " + expected); }
    private static void face(LivingEntity target, float yaw) { target.setYRot(yaw); target.setYHeadRot(yaw); target.yBodyRot = yaw; }
    private static void shield(LivingEntity target, float yaw) { face(target, yaw); target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); target.startUsingItem(InteractionHand.OFF_HAND); }
    private static void defenses(GameTestHelper h, LivingEntity target) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); target.setItemSlot(EquipmentSlot.HEAD, helmet);
        target.getAttribute(Attributes.ARMOR).setBaseValue(5); target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); target.setAbsorptionAmount(.5F);
    }
    private static void assertSource(GameTestHelper h, DamageSource source, ServerPlayer caster, Vec3 origin) {
        h.assertTrue(source != null && source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                && source.getEntity() == caster && source.getDirectEntity() == caster && origin.equals(source.sourcePositionRaw()) && origin.equals(source.getSourcePosition()),
                "native player-attack source retains the frozen eye origin and real player attribution");
        h.assertFalse(source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY), "NEW411 grants no native protection bypass tags");
    }
    public static final class Discount {
        final LivingEntity target; Discount(LivingEntity target) { this.target = target; }
        @SubscribeEvent(priority = EventPriority.HIGHEST) public void skill(SkillAttackEvent event) { if (event.target == target && event.skill.equals(SKILL)) event.amount = 5; }
    }
    public static final class Observation {
        final LivingEntity target; DamageSource source; int incoming, posts, skills; boolean blocked;
        float damage, armor, effects, enchantments, absorption, blockedDamage;
        Observation(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void skill(SkillAttackEvent event) { if (event.target == target && event.skill.equals(SKILL)) skills++; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) { if (event.getEntity() == target) { incoming++; source = event.getSource(); } }
        @SubscribeEvent public void shield(LivingShieldBlockEvent event) { if (event.getEntity() == target) { blocked = event.getBlocked(); blockedDamage = event.getBlockedDamage(); } }
        @SubscribeEvent public void post(LivingDamageEvent.Post event) {
            if (event.getEntity() != target) return; posts++; damage = event.getNewDamage();
            armor = event.getReduction(DamageContainer.Reduction.ARMOR); effects = event.getReduction(DamageContainer.Reduction.MOB_EFFECTS);
            enchantments = event.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = event.getReduction(DamageContainer.Reduction.ABSORPTION);
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final ServerPlayer p; final EmbeddedChannel channel; final Vec3 anchor;
        final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final Map<BlockPos, BlockState> previousBlocks = new LinkedHashMap<>(); long inputs; boolean closed, diagnosed;
        Fixture(GameTestHelper h, int level) {
            this.h = h; anchor = h.absoluteVec(new Vec3(8.5, 3, 5.5));
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "DS" + UUID.randomUUID().toString().substring(0, 10)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); acknowledgeLogin();
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); place();
            h.assertTrue(HighPowerCombat.enroll(p, level), "genuine connected caster enrollment");
            var state = AbilityStorage.get(p); state.selectCategory("vecmanip"); state.setLevel(level); state.learn(ID); state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "real learned Vector root input binding"); HighPowerResources.initializeBudget(p);
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5));
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) { }
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        void acknowledgeLogin() {
            channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); }
        }
        void place() { p.moveTo(anchor.x, anchor.y, anchor.z, 0, 0); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        Vec3 eye() { return p.getEyePosition(); }
        void center(LivingEntity entity, Vec3 point) { entity.setPos(point.add(0, -entity.getBbHeight() / 2, 0)); }
        void add(LivingEntity entity, Vec3 point) {
            if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
            entity.setNoGravity(true); entity.setSilent(true); center(entity, point);
            h.assertTrue(h.getLevel().addFreshEntity(entity), "native body admitted to actual level"); entities.add(entity);
        }
        Zombie target(double distance, boolean exact) { return targetAt(eye().add(0, 0, distance), exact); }
        UUID uuid(long order) { return new UUID(p.getUUID().getMostSignificantBits(), order); }
        Zombie targetAt(Vec3 point, boolean exact) { return targetAt(point, exact, UUID.randomUUID()); }
        Zombie targetAt(Vec3 point, boolean exact, UUID identity) {
            Zombie mob = EntityType.HUSK.create(h.getLevel()); mob.setUUID(identity); mob.getAttribute(Attributes.ARMOR).setBaseValue(0);
            mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1); add(mob, point);
            if (exact) h.assertTrue(HighPowerCombat.enroll(mob, 5), "declared exact native hostile enrollment"); return mob;
        }
        double cp() { return HighPowerResources.cp(p); }
        String wire() { return new HighPowerInput.Input(0, ++inputs, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        boolean cast() {
            String wire = wire(); boolean result = DeflectionStrike.tap(p, wire);
            if (!result && !diagnosed) { diagnosed = true; diagnose(wire); }
            return result;
        }
        void diagnose(String wire) {
            try {
                var canAct = DeflectionStrike.class.getDeclaredMethod("canAct", ServerPlayer.class); canAct.setAccessible(true);
                var scan = DeflectionStrike.class.getDeclaredMethod("scan", ServerPlayer.class, Vec3.class, Vec3.class); scan.setAccessible(true);
                var world = Class.forName("cn.academy.port.highpower.DisplacedProjectionWorld");
                var volume = world.getDeclaredMethod("volume", ServerPlayer.class, net.minecraft.world.phys.AABB.class); volume.setAccessible(true);
                var state = AbilityStorage.get(p); Vec3 end = eye().add(p.getLookAngle().normalize().scale(DeflectionStrikeRules.RANGE));
                Object fullScan = scan.invoke(null, p, eye(), end), firstScan = null;
                if (fullScan != null) {
                    var contactsAccessor = fullScan.getClass().getDeclaredMethod("contacts"); contactsAccessor.setAccessible(true);
                    List<?> contacts = (List<?>) contactsAccessor.invoke(fullScan);
                    if (!contacts.isEmpty()) {
                        Object first = contacts.getFirst(); var pointAccessor = first.getClass().getDeclaredMethod("point"); pointAccessor.setAccessible(true);
                        firstScan = scan.invoke(null, p, eye(), (Vec3) pointAccessor.invoke(first));
                    }
                }
                com.mojang.logging.LogUtils.getLogger().warn("NEW411 fixture diagnostic: canAct={} authenticated={} canUse={} ready={} unsupported={} category={} level={} cp={} eye={} axis={} volume={} scan={} firstContactScan={}",
                        canAct.invoke(null, p), cn.academy.port.highpower.HighPowerBracerSession.authenticated(p, HighPowerInput.parse(wire)),
                        state.canUse(ID), HighPowerResources.ready(p), HighPowerCombat.unsupportedReason(p), state.category, state.level, cp(),
                        eye(), p.getLookAngle(), volume.invoke(null, p, p.getBoundingBox()), fullScan, firstScan);
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Native diagnostic reflection failed", failure); }
        }
        DamageSource source(Vec3 origin) { return new DamageSource(p.damageSources().playerAttack(p).typeHolder(), p, p, origin); }
        void block(Vec3 point) { BlockPos position = BlockPos.containing(point); previousBlocks.putIfAbsent(position, h.getLevel().getBlockState(position)); h.getLevel().setBlockAndUpdate(position, Blocks.STONE.defaultBlockState()); }
        void assertBlocks() { for (BlockPos position : previousBlocks.keySet()) h.assertTrue(h.getLevel().getBlockState(position).is(Blocks.STONE), "strike never destroys or transforms terrain"); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void discount(LivingEntity target) { hook(new Discount(target)); }
        Observation observe(LivingEntity target) { Observation result = new Observation(target); hook(result); return result; }
        @Override public void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            DeflectionStrike.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p);
            entities.forEach(Entity::discard); previousBlocks.forEach((position, state) -> h.getLevel().setBlockAndUpdate(position, state)); channel.finishAndReleaseAll();
        }
    }
}
