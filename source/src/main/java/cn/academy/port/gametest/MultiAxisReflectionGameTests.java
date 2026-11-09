package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.api.SkillPerformEvent;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.MultiAxisReflectionGeometry;
import cn.academy.port.highpower.core.MultiAxisReflectionRules;
import cn.academy.port.highpower.core.VectorParryRules;
import cn.academy.port.machine.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW442: actual Arrow.tick, authenticated controls, ordinary native damage and persisted resources. */
@GameTestHolder("academy_multi_axis_reflection")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class MultiAxisReflectionGameTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_multi_axis_reflection", ID = MultiAxisReflection.ID;
    private MultiAxisReflectionGameTests() {}

    @SubscribeEvent public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), pos = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[]{64, 12, 64}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) pos.add(IntTag.valueOf(0));
        cell.put("pos", pos); cell.putInt("state", 0); blocks.add(cell); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_multi_axis_reflection", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void lv4_after_441_uses_complete_advanced_learning_without_refill_or_experience(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10, false);
        f.ready(() -> {
            var state = AbilityStorage.get(p); var skill = SkillCatalog.find(HighPowerCategories.VECMANIP, ID).orElseThrow();
            h.assertTrue(skill.level() == 4 && skill.requirements().equals(List.of(new SkillCatalog.Requirement(VectorCage.ID, 0)))
                    && HighPowerSkills.MULTI_AXIS_REFLECTION_PROPOSAL == 442, "canonical NEW442 is LV4 after learned NEW441");
            int stimulations = ClassicRules.learningStimulations(4), ticks = stimulations * DeveloperType.ADVANCED.ticksPerStimulation();
            double cost = DeveloperType.ADVANCED.actualConsumption(stimulations);
            state.unlearn(VectorCage.ID); var machine = f.advanced(p, cost);
            h.assertFalse(f.learnRequest(p), "other learned branches do not replace NEW441");
            near(h, machine.battery().getEnergy(), cost, "missing parent consumes no machine IF");
            state.learn(VectorCage.ID); state.setLevel(3);
            h.assertFalse(f.learnRequest(p), "paid parent does not bypass integer LV4"); state.setLevel(4);
            f.shield(p, n("17.25")); f.reserve(p, "3", "5");
            HighPowerGrowth.values(p); p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", 37);
            CompoundTag combat = f.combat(p).copy(), resource = f.budget(p).copy(); double xp = state.levelExperience;
            h.assertFalse(state.presets.edit(0, 0, ID, state::learned), "unlearned reflection cannot bind");
            h.assertTrue(f.learnRequest(p), "real bound Advanced machine starts development");
            f.develop(p, ticks - 1); h.assertFalse(state.learned(ID), "last physical paid tick is mandatory");
            near(h, machine.battery().getEnergy(), DeveloperType.ADVANCED.energyPerTick(), "one native IF payment remains");
            f.develop(p, 1); h.assertTrue(state.learned(ID) && state.exp(ID) == 0 && state.levelExperience == xp,
                    "complete native learning grants no skill or level XP");
            near(h, machine.battery().getEnergy(), 0, "all finite native machine IF was paid");
            h.assertTrue(combat.equals(f.combat(p)) && resource.equals(f.budget(p)), "learning never refills HP, S, reserves, CP or growth");
            h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "genuinely learned reflection binds its ordinary slot"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void all_four_native_sweeps_reverse_at_actual_position_preserving_arrow_identity_and_damage(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = f.players(4);
        f.ready(() -> {
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i); f.arm(p, "30"); Arrow a = f.arrow(p, i, 2.2, 2, i % 2 == 0 ? null : new ItemStack(Items.BOW));
                a.setBaseDamage(2.25); a.pickup = AbstractArrow.Pickup.ALLOWED;
                Vec3 from = a.position(), velocity = a.getDeltaMovement(); Entity shooter = a.getOwner(); UUID uuid = a.getUUID();
                ItemStack item = a.getPickupItemStackOrigin().copy(), weapon = a.getWeaponItem() == null ? null : a.getWeaponItem().copy();
                var metadata = (ShortInterceptionArrowBridge.MetadataAccess)a; int age = metadata.academy$interceptionLife(), ticks = a.tickCount;
                BigDecimal before = hp(p), cost = raw(a); Watch watch = f.watch(p); a.tick();
                h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(a) && !a.isRemoved(), "actual native selected-body sweep reflects facet " + i);
                vector(h, a.position(), from.subtract(velocity), "vanilla tail moves once from actual tick-start position");
                vector(h, a.getDeltaMovement(), velocity.scale(-(double).99F).add(0, -.05, 0), "exact -v reversal precedes ordinary drag and gravity");
                h.assertTrue(a.getUUID().equals(uuid) && a.getOwner() == shooter && a.getBaseDamage() == 2.25
                        && ItemStack.matches(item, a.getPickupItemStackOrigin()) && a.pickup == AbstractArrow.Pickup.ALLOWED
                        && (weapon == null ? a.getWeaponItem() == null : ItemStack.matches(weapon, a.getWeaponItem()))
                        && metadata.academy$interceptionLife() >= age && a.tickCount >= ticks, "same arrow preserves owner, base, item and age");
                eq(h, shield(p), n("30").subtract(cost), "raw native float/ceil amount pays once"); eq(h, hp(p), before, "replaced pending body impact never hurts defender");
                h.assertTrue(watch.incoming == 0 && watch.posts == 0 && watch.skills == 0 && MultiAxisReflection.remainingHits(p) == 3,
                        "reflection bypasses body hurt, creates no skill attack, and claims one quota");
                h.assertTrue(!TrajectoryReturnArrowBridge.leased(a) && !ShortInterceptionArrowBridge.leased(a)
                        && !a.getPersistentData().contains("academy:trajectory_return_used") && !a.getPersistentData().contains("academy:short_interception_used"),
                        "NEW442 creates no NEW431 or NEW331 damage lease");
                h.assertTrue(AbilityStorage.get(p).exp(ID) == 0, "actual reflection grants zero experience");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void completed_native_flight_crossing_can_qualify_a_later_native_body_hit(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "20"); Arrow a = f.arrow(p, 1, 2.1, .7, null); BigDecimal before = hp(p);
            a.tick(); a.tick(); h.assertFalse(MultiAxisReflectionArrowBridge.reflecting(a), "crossing alone does not charge or turn an arrow");
            eq(h, shield(p), n("20"), "completed free flight crossing is still free");
            Vec3 from = a.position(), velocity = a.getDeltaMovement(); BigDecimal cost = raw(a); a.tick();
            h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(a), "later actual original native defender hit consumes the historical crossing");
            vector(h, a.position(), from.subtract(velocity), "historical receipt never teleports to its old crossing point");
            eq(h, shield(p), n("20").subtract(cost), "only pending native body contact pays"); eq(h, hp(p), before, "actual defendant remains unharmed"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void four_facets_pay_four_hits_and_true_twenty_online_posts_restore_only_quota(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "100"); BigDecimal cost = BigDecimal.ZERO;
            for (int facet = 0; facet < 4; facet++) { Arrow a = f.arrow(p, facet, 2.2, 2, null); cost = cost.add(raw(a)); a.tick(); h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(a), "distinct facet admits one native contact"); a.discard(); }
            BigDecimal after = n("100").subtract(cost); eq(h, shield(p), after, "all four native contacts debit exactly");
            h.assertTrue(MultiAxisReflection.remainingHits(p) == 0, "all four rolling entries are occupied");
            Arrow fifth = f.arrow(p, 1, 2.2, 2, null); BigDecimal health = hp(p); fifth.tick();
            h.assertFalse(MultiAxisReflectionArrowBridge.reflecting(fifth), "fifth native contact cannot turn");
            h.assertTrue(hp(p).compareTo(health) < 0, "fifth native contact keeps native HP damage"); eq(h, shield(p), after, "fifth contact is resource-free");
            for (int i = 0; i < 40; i++) { MultiAxisReflection.tick(p); MultiAxisReflection.playerTick(new PlayerTickEvent.Post(p)); }
            h.assertTrue(MultiAxisReflection.remainingHits(p) == 0, "duplicate same-physical-tick maintenance cannot age quota");
            PlayerPosts clock = f.posts(p);
            clock.at(19, () -> h.assertTrue(MultiAxisReflection.remainingHits(p) == 0, "nineteen completed real owner Posts do not replenish quota"));
            clock.at(20, () -> { h.assertTrue(MultiAxisReflection.remainingHits(p) == 4, "twenty completed real online owner Posts retire the four entries"); eq(h, shield(p), after, "quota aging never restores S"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void one_facet_per_physical_tick_refuses_duplicate_but_next_native_post_reopens_it(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "100"); Arrow first = f.arrow(p, 1, 2.2, 2, null); first.tick(); first.discard();
            Arrow second = f.arrow(p, 1, 2.2, 2, null); second.tick();
            h.assertFalse(MultiAxisReflectionArrowBridge.reflecting(second), "same facet refuses a second same-tick arrow");
            h.assertTrue(MultiAxisReflection.remainingHits(p) == 3, "refusal consumes no rolling entry");
            f.posts(p).at(1, () -> { p.invulnerableTime = 0; Arrow next = f.arrow(p, 1, 2.2, 2, null); next.tick(); h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(next) && MultiAxisReflection.remainingHits(p) == 2, "new genuine player tick clears only the per-facet fence"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void atomic_whole_debit_uses_base_then_lower_and_never_upper(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(3);
        f.ready(() -> {
            String[] base = {"1.25", "1.25", "1"}, lower = {"10", "2.74", "0"};
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.reserve(p, lower[i], "20"); f.arm(p, base[i]);
                Arrow a = f.arrow(p, 1, 2.2, 2, new ItemStack(Items.BOW)); BigDecimal hp = hp(p); a.tick();
                if (i == 0) { h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(a), "whole four-point amount is affordable across eligible pools"); eq(h, shield(p), BigDecimal.ZERO, "base pays first"); eq(h, share(p, "lower"), n("7.25"), "lower pays exactly the remainder"); eq(h, hp(p), hp, "successful payment suppresses body hurt"); }
                else { h.assertFalse(MultiAxisReflectionArrowBridge.reflecting(a), "insufficient base plus lower refuses even with upper funds"); eq(h, shield(p), n(base[i]), "refusal spends no partial base"); eq(h, share(p, "lower"), n(lower[i]), "refusal spends no partial lower"); h.assertTrue(hp(p).compareTo(hp) < 0 && MultiAxisReflection.remainingHits(p) == 4, "ordinary native hurt proceeds and quota is free"); }
                eq(h, share(p, "upper"), n("20"), "upper share is never eligible");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void ordinary_melee_magic_and_direct_arrow_hurt_have_no_generic_absorption(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.reserve(p, "10", "10"); f.arm(p, "20"); Skeleton attacker = f.shooter(p, f.normal(p, 1));
            Arrow a = f.arrow(p, 1, 2.2, 2, null); BigDecimal health = hp(p);
            for (DamageSource source : List.of(p.damageSources().mobAttack(attacker), p.damageSources().magic(), p.damageSources().arrow(a, attacker))) {
                p.invulnerableTime = 0; h.assertTrue(p.hurt(source, 3), "actual native hurt remains supported");
            }
            eq(h, hp(p), health.subtract(n("9")), "all three ordinary native hits reach exact HP"); eq(h, shield(p), n("20"), "toggle creates no general hurt absorption");
            eq(h, share(p, "lower"), n("10"), "direct damage cannot spend reserve"); h.assertTrue(MultiAxisReflection.remainingHits(p) == 4 && !MultiAxisReflectionArrowBridge.reflecting(a), "direct arrow damage cannot forge the native flight frame"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void canceled_and_owner_base_position_velocity_mutated_impact_callbacks_never_pay(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(8);
        f.ready(() -> {
            for (int mode = 0; mode < ps.size(); mode++) {
                ServerPlayer p = ps.get(mode); f.arm(p, "20"); Arrow a = f.arrow(p, 1, 2.2, 2, new ItemStack(Items.BOW)); ImpactGate gate = new ImpactGate(a, mode, f.shooter(p, new Vec3(1, 0, 0)), p); f.hook(gate);
                a.tick(); h.assertTrue(gate.calls == 1, "real native impact callback is observed for mode " + mode);
                eq(h, shield(p), n("20"), "canceled or rewritten impact never debits S");
                h.assertTrue(MultiAxisReflection.remainingHits(p) == 4 && !MultiAxisReflectionArrowBridge.reflecting(a), "canceled or rewritten impact never claims quota or spent mark");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void wall_before_facet_and_first_other_body_keep_original_native_collision(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(2);
        f.ready(() -> {
            ServerPlayer wall = ps.get(0); f.arm(wall, "20"); Arrow blocked = f.arrow(wall, 1, 3.8, 3.5, null);
            f.block(f.center(wall).add(f.normal(wall, 1).scale(2.6)), Blocks.STONE.defaultBlockState()); blocked.tick();
            h.assertTrue(blocked.inGround && !MultiAxisReflectionArrowBridge.reflecting(blocked), "actual native block ray wins before the facet"); eq(h, shield(wall), n("20"), "wall rejection is free");
            ServerPlayer body = ps.get(1); f.arm(body, "20"); Arrow a = f.arrow(body, 1, 3.8, 3.5, null);
            Skeleton first = f.mob(f.center(body).add(f.normal(body, 1).scale(2))); Watch watch = f.watch(first); a.tick();
            h.assertTrue(watch.incoming == 1 && !MultiAxisReflectionArrowBridge.reflecting(a), "first real other body wins original native findHitEntity");
            eq(h, shield(body), n("20"), "another native victim cannot authorize owner reflection"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void front_gap_rear_and_body_miss_graze_are_free(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(3);
        f.ready(() -> {
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.arm(p, "20"); Vec3 direction = i == 1 ? new Vec3(0, 0, -1) : new Vec3(0, 0, 1);
                Arrow a;
                if (i < 2) a = f.arrowAt(p, f.center(p).add(direction.scale(2.2)), direction.scale(-2), null, direction);
                else { Vec3 normal = f.normal(p, 1), right = new Vec3(normal.z, 0, -normal.x); a = f.arrowAt(p, f.center(p).add(normal.scale(1.5)).subtract(right), normal.scale(-.5).add(right.scale(2)), null, normal); }
                a.tick(); if (i == 2) fly(a, 2); h.assertFalse(MultiAxisReflectionArrowBridge.reflecting(a), "gap, rear or actual oblique facet graze without a native body hit cannot reflect");
                eq(h, shield(p), n("20"), "geometrical near miss is wholly free"); h.assertTrue(MultiAxisReflection.remainingHits(p) == 4, "near miss consumes no quota");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void blocked_original_reverse_step_by_solid_fluid_or_body_refuses_without_teleport(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(3);
        f.ready(() -> {
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.arm(p, "20"); Arrow a = f.arrow(p, 1, 2.2, 2, null); Vec3 obstruction = f.center(p).add(f.normal(p, 1).scale(3.5));
                if (i == 2) f.mob(obstruction); else f.block(obstruction, (i == 0 ? Blocks.STONE : Blocks.WATER).defaultBlockState());
                BigDecimal health = hp(p); a.tick(); h.assertFalse(MultiAxisReflectionArrowBridge.reflecting(a), "unclear first reverse step refuses");
                eq(h, shield(p), n("20"), "reverse-path refusal is wholly free"); h.assertTrue(hp(p).compareTo(health) < 0 && MultiAxisReflection.remainingHits(p) == 4, "incoming original native body hit proceeds");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void public_capture_and_bridge_method_forgery_cannot_manufacture_a_native_frame(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "20"); Vec3 normal = f.normal(p, 1), from = f.center(p).add(normal.scale(2.1)), to = f.center(p).add(normal.scale(.8));
            Arrow a = f.arrowAt(p, to, normal.scale(-.7), null, normal);
            var pose = MultiAxisReflectionGeometry.pose(v(f.center(p)), v(p.getLookAngle())); var cross = MultiAxisReflectionGeometry.crossing(pose, v(from), v(to)).orElseThrow();
            var capture = MultiAxisReflection.capture(p, a, cross.facetIndex(), vec(cross.point()), from, to);
            h.assertFalse(MultiAxisReflectionArrowBridge.validFrame(p, a, capture), "a public geometric capture is not native impact authority");
            CompoundTag ledger = f.combat(p).copy(); Vec3 at = a.position(), velocity = a.getDeltaMovement(); EntityHitResult fake = new EntityHitResult(p);
            var forged = MultiAxisReflectionArrowBridge.beginTick(a);
            MultiAxisReflectionArrowBridge.observe(a, at, at.add(velocity), fake);
            MultiAxisReflectionArrowBridge.beforeImpact(a, fake); MultiAxisReflectionArrowBridge.afterImpactEvent(a, fake, false);
            h.assertFalse(MultiAxisReflectionArrowBridge.reflect(a, fake), "public method sequence cannot forge an original native findHitEntity/event frame");
            MultiAxisReflectionArrowBridge.endTick(forged, true);
            h.assertTrue(ledger.equals(f.combat(p)) && !MultiAxisReflectionArrowBridge.reflecting(a) && MultiAxisReflection.remainingHits(p) == 4,
                    "forgery neither changes resources nor quota or spent state"); vector(h, a.position(), at, "forgery leaves position"); vector(h, a.getDeltaMovement(), velocity, "forgery leaves velocity"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void subsequent_native_shooter_hit_keeps_ordinary_damage_source_and_credit(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "20"); Arrow a = f.arrow(p, 1, 2.2, 2, new ItemStack(Items.BOW)); LivingEntity shooter = (LivingEntity)a.getOwner();
            h.assertTrue(HighPowerCombat.enroll(shooter, 5), "original hostile shooter has an exact native ledger"); Watch watch = f.watch(shooter); BigDecimal before = hp(shooter);
            a.tick(); BigDecimal ordinary = raw(a); fly(a, 4);
            h.assertTrue(a.isRemoved() && watch.incoming == 1 && watch.posts == 1 && watch.skills == 0, "one actual return collision uses one ordinary native damage path");
            eq(h, hp(shooter), before.subtract(ordinary), "reflection never multiplies ordinary native arrow damage");
            h.assertTrue(watch.source != null && watch.source.getDirectEntity() == a && watch.source.getEntity() == shooter
                    && watch.source.getLocalizedDeathMessage(shooter).equals(shooter.damageSources().arrow(a, shooter).getLocalizedDeathMessage(shooter)), "native original shooter attribution and death credit remain intact"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void returned_arrow_uses_native_armor_resistance_enchantment_and_absorption_once(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "20"); Arrow a = f.arrow(p, 1, 2.2, 2, new ItemStack(Items.BOW)); LivingEntity shooter = (LivingEntity)a.getOwner(); HighPowerCombat.enroll(shooter, 5); defenses(h, shooter);
            Watch watch = f.watch(shooter); BigDecimal before = hp(shooter); a.tick(); fly(a, 4);
            h.assertTrue(watch.posts == 1 && watch.armor > 0 && watch.effects > 0 && watch.enchantments > 0 && watch.absorption > 0,
                    "actual returned arrow traverses all ordinary native mitigation stages once");
            h.assertTrue(hp(shooter).compareTo(before) < 0 && before.subtract(hp(shooter)).compareTo(n("4")) < 0, "ordinary defended native damage is below unmitigated arrow damage"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void returned_arrow_respects_native_shield_block_and_invulnerability_frames(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(4);
        f.ready(() -> {
            List<Skeleton> shooters = new ArrayList<>(); List<Watch> watches = new ArrayList<>();
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); if (i % 2 == 0) f.arm(p, "20"); Skeleton shooter = f.shooter(p, f.normal(p, 1)); HighPowerCombat.enroll(shooter, 5);
                f.block(shooter.position().add(0, 3, 0), Blocks.STONE.defaultBlockState());
                if (i < 2) { Vec3 normal = f.normal(p, 1); face(shooter, (float)Math.toDegrees(Math.atan2(normal.x, -normal.z))); shooter.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); shooter.startUsingItem(InteractionHand.OFF_HAND); }
                shooters.add(shooter); watches.add(f.watch(shooter));
            }
            h.runAfterDelay(6, () -> {
                for (int pair = 0; pair < 2; pair++) {
                    int index = pair * 2; ServerPlayer p = ps.get(index), control = ps.get(index + 1);
                    Skeleton actualShooter = shooters.get(index), nativeShooter = shooters.get(index + 1);
                    for (Skeleton shooter : List.of(actualShooter, nativeShooter)) { shooter.clearFire(); shooter.invulnerableTime = 0; }
                    if (pair == 0) h.assertTrue(actualShooter.isBlocking() && nativeShooter.isBlocking(), "both actual native shields finished their use delay");
                    else for (Skeleton shooter : List.of(actualShooter, nativeShooter)) { shooter.invulnerableTime = 0; shooter.hurt(shooter.damageSources().magic(), 10); }
                    BigDecimal actualHp = hp(actualShooter), nativeHp = hp(nativeShooter);
                    Watch actual = watches.get(index), nativeWatch = watches.get(index + 1); int actualPosts = actual.posts, nativePosts = nativeWatch.posts, actualIncoming = actual.incoming, nativeIncoming = nativeWatch.incoming, actualShields = actual.shieldEvents, nativeShields = nativeWatch.shieldEvents;
                    Arrow reflected = f.arrow(p, 1, 2.2, 2, null, actualShooter); reflected.tick();
                    h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(reflected), "actual arrow was reflected before native defense comparison");
                    Vec3 relative = reflected.position().subtract(f.center(p));
                    Arrow ordinary = f.arrowAt(f.center(control).add(relative), reflected.getDeltaMovement(), null, nativeShooter);
                    String nativeBefore = "pair=" + pair + ", reflected=" + reflected.position().subtract(f.center(p)) + "/" + reflected.getDeltaMovement()
                            + ", ordinary=" + ordinary.position().subtract(f.center(control)) + "/" + ordinary.getDeltaMovement()
                            + ", owner=" + actualShooter.position().subtract(f.center(p)) + "/" + nativeShooter.position().subtract(f.center(control))
                            + ", yaw=" + actualShooter.getYRot() + "/" + nativeShooter.getYRot() + ", blocking=" + actualShooter.isBlocking() + "/" + nativeShooter.isBlocking();
                    fly(reflected, 4); fly(ordinary, 4);
                    eq(h, hp(actualShooter), actualHp, "returned arrow respects native shield or higher existing iframe hit");
                    eq(h, hp(nativeShooter), nativeHp, "paired ordinary unreflected arrow establishes the same native defense result");
                    h.assertTrue(actual.posts - actualPosts == nativeWatch.posts - nativePosts && actual.incoming - actualIncoming == nativeWatch.incoming - nativeIncoming
                            && actual.shieldEvents - actualShields == nativeWatch.shieldEvents - nativeShields && actual.skills == 0 && nativeWatch.skills == 0,
                            "reflected arrow matches ordinary native callbacks: actual incoming/Post/shield=" + (actual.incoming - actualIncoming) + "/" + (actual.posts - actualPosts) + "/" + (actual.shieldEvents - actualShields)
                            + ", control=" + (nativeWatch.incoming - nativeIncoming) + "/" + (nativeWatch.posts - nativePosts) + "/" + (nativeWatch.shieldEvents - nativeShields)
                            + ", actual HP=" + actualHp + "→" + hp(actualShooter) + ", control HP=" + nativeHp + "→" + hp(nativeShooter) + "; " + nativeBefore);
                }
                h.assertTrue(watches.get(0).shieldEvents > 0, "real native shield callback executes"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void permanent_spent_arrow_survives_serialization_and_cannot_loop_between_two_owners(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(2);
        f.ready(() -> {
            ServerPlayer first = ps.get(0), second = ps.get(1); f.arm(first, "20"); f.arm(second, "20");
            Arrow a = f.arrow(first, 1, 2.2, 2, null); a.tick(); h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(a), "first actual native reflection pays");
            CompoundTag saved = a.saveWithoutId(new CompoundTag()); a.discard();
            Arrow loaded = new Arrow(EntityType.ARROW, h.getLevel()); loaded.load(saved); f.entity(loaded);
            h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(loaded), "native entity save/load preserves permanent denial marker");
            Vec3 normal = f.normal(second, 1); loaded.setPos(f.center(second).add(normal.scale(2.2))); loaded.setDeltaMovement(normal.scale(-2));
            BigDecimal secondHealth = hp(second); loaded.tick();
            eq(h, shield(second), n("20"), "second owner never pays for an already reflected arrow");
            h.assertTrue(hp(second).compareTo(secondHealth) < 0 && MultiAxisReflection.remainingHits(second) == 4, "spent arrow has ordinary native damage and cannot become a reflection loop");
            h.assertTrue(MultiAxisReflectionArrowBridge.leased(loaded), "permanent spent mark also denies other steering controllers"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void prior_431_331_spent_marks_and_nonplain_arrows_never_obtain_a_442_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(6);
        f.ready(() -> {
            for (int mode = 0; mode < ps.size(); mode++) {
                ServerPlayer p = ps.get(mode); f.arm(p, "20"); Arrow a = f.arrow(p, 1, 2.2, 2, new ItemStack(Items.BOW));
                switch (mode) {
                    case 0 -> a.getPersistentData().putBoolean("academy:trajectory_return_used", true);
                    case 1 -> a.getPersistentData().putBoolean("academy:short_interception_used", true);
                    case 2 -> a.setCritArrow(true);
                    case 3 -> { CompoundTag saved = a.saveWithoutId(new CompoundTag()); saved.putByte("PierceLevel", (byte)1); a.load(saved); }
                    case 4 -> a.setRemainingFireTicks(100);
                    case 5 -> a.getWeaponItem().enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), 1);
                    default -> throw new IllegalStateException();
                }
                a.tick(); eq(h, shield(p), n("20"), "foreign spent or nonplain native arrow is free: " + mode);
                h.assertTrue(!MultiAxisReflectionArrowBridge.reflecting(a) && MultiAxisReflection.remainingHits(p) == 4,
                        "unsupported native arrow creates no NEW442 mark, direction fence or quota entry");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void relog_preserves_quota_and_pending_payment_debt_without_offline_aging_or_fill(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "20"); Arrow a = f.arrow(p, 1, 2.2, 2, null); a.tick(); a.discard();
            CompoundTag quota = p.getPersistentData().getCompound(MultiAxisReflection.QUOTA_KEY).copy(); BigDecimal before = shield(p); double cp = HighPowerResources.cp(p);
            f.reload(p); h.assertFalse(MultiAxisReflection.active(p), "reload drops only the live controller");
            h.assertTrue(quota.equals(p.getPersistentData().getCompound(MultiAxisReflection.QUOTA_KEY)) && MultiAxisReflection.remainingHits(p) == 3,
                    "reload preserves unexpired quota and same-tick facet debt");
            eq(h, shield(p), before, "reload never refills S"); near(h, HighPowerResources.cp(p), cp, "reload never refills CP");
            h.assertFalse(f.start(p), "persisted restart debt refuses immediate reactivation");
            f.posts(p).at(20, () -> { h.assertTrue(MultiAxisReflection.remainingHits(p) == 4, "inactive real online Posts age retained quota normally"); eq(h, shield(p), before, "quota expiry and logout do not refill S"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void toggle_cost_release_and_native_perform_reentry_keep_one_continuous_vector_receipt(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.shield(p, n("20")); BigDecimal q = HighPowerGrowth.values(p).position(); double cp = HighPowerResources.cp(p); int[] calls = {0}; boolean[] nested = {true};
            f.hook(new Object() { @SubscribeEvent public void perform(SkillPerformEvent event) { if (event.player == p && event.skill.equals(ID)) { calls[0]++; nested[0] = f.start(p); MultiAxisReflection.tick(p); } } });
            h.assertTrue(AbilityStorage.get(p).consumeSkill(ID, 1, 0, false), "public native CPData perform invokes the genuine same-skill event");
            h.assertTrue(calls[0] == 1 && !nested[0] && !MultiAxisReflection.active(p), "native same-skill perform observer cannot reenter a controller payment");
            near(h, HighPowerResources.cp(p), cp, "rejected observer reentry cannot touch the separate high-power CP ledger");
            h.assertTrue(f.start(p), "outer authenticated toggle pays once");
            near(h, HighPowerResources.cp(p), cp - VectorParryRules.activationCost(q), "activation reuses exact continuous vector-parry cost");
            h.assertTrue(calls[0] == 1 && MultiAxisReflection.active(p), "high-power payment uses its one existing receipt without inventing another native perform");
            h.assertTrue(MultiAxisReflection.release(p, f.held.get(p)) && MultiAxisReflection.active(p), "release does not end a persistent toggle");
            double paid = HighPowerResources.cp(p); h.assertTrue(f.start(p) && !MultiAxisReflection.active(p), "fresh authenticated second press ends the toggle");
            near(h, HighPowerResources.cp(p), paid, "toggle off pays nothing"); eq(h, shield(p), n("20"), "toggle cycle never fills or spends S");
            h.assertFalse(f.start(p), "restart fence cannot be bypassed by a new input"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void insufficient_activation_cp_and_malformed_persistence_are_free(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(3);
        f.ready(() -> {
            for (int i = 0; i < ps.size(); i++) {
                ServerPlayer p = ps.get(i); f.shield(p, n("20"));
                if (i == 0) f.budget(p).putDouble("cp", 0);
                if (i == 1) { CompoundTag quota = new CompoundTag(); quota.putInt("version", 1); quota.putIntArray("remaining", new int[]{0,20,0,0}); quota.putInt("facets", 0); p.getPersistentData().put(MultiAxisReflection.QUOTA_KEY, quota); }
                if (i == 2) { CompoundTag payment = new CompoundTag(); payment.putInt("version", 999); p.getPersistentData().put(MultiAxisReflection.PAYMENT_KEY, payment); }
                BigDecimal before = shield(p); double cp = HighPowerResources.cp(p); h.assertFalse(f.start(p), "unaffordable or malformed setup refuses");
                eq(h, shield(p), before, "refused setup does not alter S"); near(h, HighPowerResources.cp(p), cp, "refused setup does not spend CP"); h.assertFalse(MultiAxisReflection.active(p), "no unpaid or corrupt live controller");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void sustain_failure_ends_pending_toggle_without_new_s_or_quota_debit(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            f.arm(p, "24"); Arrow spent = f.arrow(p, 1, 2.2, 2, null); spent.tick();
            h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(spent), "genuine native reflection establishes the existing spend-recovery fence");
            eq(h, shield(p), n("20"), "actual four-point native debit leaves twenty S before renewal"); spent.discard();
            long receipt = p.getPersistentData().getCompound(MultiAxisReflection.PAYMENT_KEY).getLong("receipt");
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(ServerTickEvent.Pre event) { if (event.getServer() == p.server) f.budget(p).putDouble("cp", 0); } });
            PlayerPosts clock = f.posts(p);
            clock.at(20, () -> h.assertTrue(MultiAxisReflection.renewalPending(p) && !MultiAxisReflection.protecting(p), "completed twentieth native Post exposes unpaid renewal pending with no protection: " + p.getPersistentData().getCompound(MultiAxisReflection.PAYMENT_KEY) + ", cp=" + HighPowerResources.cp(p)));
            clock.at(21, () -> { h.assertFalse(MultiAxisReflection.active(p) || MultiAxisReflection.renewalPending(p), "real unavailable CP ends the pending toggle at renewal: " + p.getPersistentData().getCompound(MultiAxisReflection.PAYMENT_KEY) + ", cp=" + HighPowerResources.cp(p)); h.assertTrue(p.getPersistentData().getCompound(MultiAxisReflection.PAYMENT_KEY).getLong("receipt") == receipt, "unaffordable renewal never creates another payment receipt"); eq(h, shield(p), n("20"), "failed CP renewal cannot spend or restore S"); h.assertTrue(MultiAxisReflection.remainingHits(p) == 4, "failed sustain creates no reflection quota entry"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unique_one_percent_recovery_fills_base_then_lower_without_upper_or_active_recovery(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(2);
        f.ready(() -> {
            ServerPlayer inactive = ps.get(0), active = ps.get(1); BigDecimal maximum = n(HighPowerCombat.maxShieldText(inactive)), budget = maximum.movePointLeft(2);
            for (ServerPlayer p : ps) { f.shield(p, maximum.subtract(budget.divide(n("2")))); f.reserve(p, "0", "7"); f.combat(p).putLong("lastShieldSpend", p.level().getGameTime() - 61); f.combat(p).putLong("lastShieldTick", p.level().getGameTime() - 1); }
            h.assertTrue(f.start(active), "paid active toggle blocks ordinary S recovery");
            HighPowerCombat.tickShield(inactive); BigDecimal base = shield(inactive), lower = share(inactive, "lower");
            eq(h, base, maximum, "one recovery budget finishes base first"); eq(h, lower, budget.divide(n("2")), "only remainder reaches fixed lower share");
            for (int i = 0; i < 20; i++) HighPowerCombat.tickShield(inactive);
            eq(h, shield(inactive), base, "duplicate same-tick recovery is not another budget"); eq(h, share(inactive, "lower"), lower, "all learned paths share one lower recovery budget");
            HighPowerCombat.tickShield(active); eq(h, shield(active), maximum.subtract(budget.divide(n("2"))), "active paid reflection blocks base recovery"); eq(h, share(active, "lower"), BigDecimal.ZERO, "active paid reflection blocks reserve recovery");
            for (ServerPlayer p : ps) eq(h, share(p, "upper"), n("7"), "NEW442 never recovers upper reserve"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void actual_player_tick_recovers_learned_442_attack_branch_without_412_and_unlearned_control_does_not(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(2);
        f.ready(() -> {
            ServerPlayer learned = ps.get(0), unlearned = ps.get(1);
            BigDecimal maximum = n(HighPowerCombat.maxShieldText(learned)), budget = maximum.movePointLeft(2);
            for (ServerPlayer p : ps) {
                var state = AbilityStorage.get(p);
                state.unlearn(VectorParry.ID); state.unlearn(InertialAnchor.ID); state.unlearn(CirculationRelief.ID);
                f.shield(p, maximum.subtract(budget.divide(n("2")))); f.reserve(p, "0", "7");
                f.combat(p).putLong("lastShieldSpend", p.level().getGameTime() - 61);
                f.combat(p).putLong("lastShieldTick", p.level().getGameTime());
            }
            AbilityStorage.get(unlearned).unlearn(ID);
            f.posts(unlearned).at(1, () -> {
                eq(h, shield(learned), maximum, "real native PlayerTick reaches NEW442 recovery without learned NEW412");
                eq(h, share(learned, "lower"), budget.divide(n("2")), "one native player budget finishes base then lower");
                eq(h, share(learned, "upper"), n("7"), "native player route cannot fill upper share");
                eq(h, shield(unlearned), maximum.subtract(budget.divide(n("2"))), "unlearned attack-branch control receives no recovery entitlement");
                eq(h, share(unlearned, "lower"), BigDecimal.ZERO, "unlearned control receives no lower reserve budget"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void retained_md_child_views_reconcile_and_v1_reserve_migrates_only_on_real_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(2);
        f.ready(() -> {
            ServerPlayer child = ps.get(0); f.shield(child, n("20")); f.reserve(child, "5", "7");
            h.assertTrue(ScatterPlateStorage.ensure(child).isPresent(), "retained MD plate allocation exists before category-independent spend");
            var captured = ZeroGapArmorStorage.capture(child).orElseThrow(); h.assertTrue(ZeroGapArmorStorage.apply(ZeroGapArmorStorage.prepareInitialization(captured)), "retained six child views are initialized from existing parents");
            h.assertTrue(f.start(child), "Vector reflection starts with retained MD metadata"); Arrow a = f.arrow(child, 1, 2.2, 2, null); a.tick();
            var after = ZeroGapArmorStorage.read(child).orElseThrow(); eq(h, after.base().shield(), n("16"), "one debit reconciles exact base parent");
            eq(h, after.scatter().shield(), n("16"), "old three-plate allocation follows base debit"); eq(h, after.reserves().upper(), n("7"), "old six-child upper identity stays exact");
            eq(h, after.total(), after.base().shield().add(after.reserves().total()), "old six child currents sum to existing parents without refill");
            ServerPlayer legacy = ps.get(1); f.shield(legacy, n("1")); CompoundTag reserve = new CompoundTag(); reserve.putInt("version", 1); reserve.putString("amount", "9"); reserve.putString("capacity", HighPowerCombat.maxShieldText(legacy)); f.combat(legacy).put(MagneticDomainReserve.KEY, reserve);
            CompoundTag before = f.combat(legacy).copy(); HighPowerCombat.multiAxisReflectionAvailable(legacy); h.assertTrue(before.equals(f.combat(legacy)), "read-only eligibility does not rewrite a legacy reserve");
            h.assertTrue(f.start(legacy), "valid legacy existing funds can arm reflection"); Arrow migrated = f.arrow(legacy, 1, 2.2, 2, null); migrated.tick();
            h.assertTrue(f.combat(legacy).getCompound(MagneticDomainReserve.KEY).getInt("version") == 2, "real resource publication writes current reserve schema");
            eq(h, shield(legacy), BigDecimal.ZERO, "legacy base is consumed first"); eq(h, share(legacy, "lower"), n("6"), "legacy amount migrates once and pays only actual remainder"); eq(h, share(legacy, "upper"), BigDecimal.ZERO, "migration manufactures no upper reserve"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void owner_close_retires_native_crossing_and_flight_observations_without_erasing_spent(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(2);
        f.ready(() -> {
            for (ServerPlayer p : ps) f.arm(p, "20");
            Arrow historical = f.arrow(ps.get(0), 1, 2.1, .7, null); historical.tick(); historical.tick();
            Arrow reflected = f.arrow(ps.get(1), 1, 2.2, 2, null); reflected.tick();
            h.assertTrue(tracked("RECEIPTS", historical) && tracked("FLIGHTS", reflected), "real native ticks establish historical and reflected-flight observations");
            f.freeze(historical, reflected); Vec3 historicalAt = historical.position(), reflectedAt = reflected.position();
            BigDecimal firstS = shield(ps.get(0)), secondS = shield(ps.get(1)); f.combat(ps.get(0)).putLong("lastShieldSpend", ps.get(0).level().getGameTime());
            for (ServerPlayer p : ps) MultiAxisReflection.abort(p);
            h.assertFalse(tracked("FLIGHTS", reflected), "owner close immediately retires its reflected-flight observation");
            f.posts(ps.get(1)).at(1, () -> {
                h.assertFalse(tracked("RECEIPTS", historical) || tracked("TICKS", historical) || tracked("FLIGHTS", reflected) || tracked("TICKS", reflected), "native maintenance frees all closed-owner observation slots");
                h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(reflected), "closing controller cannot erase permanent spent evidence");
                eq(h, shield(ps.get(0)), firstS, "crossing cleanup does not spend or refill S"); eq(h, shield(ps.get(1)), secondS, "flight cleanup does not refund S");
                vector(h, historical.position(), historicalAt, "cleanup does not move unreflected arrow"); vector(h, reflected.position(), reflectedAt, "cleanup does not move reflected arrow"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_arrow_unload_and_discard_retire_observation_slots_without_clearing_tombstones(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(2);
        f.ready(() -> {
            for (ServerPlayer p : ps) f.arm(p, "20");
            Arrow historical = f.arrow(ps.get(0), 1, 2.1, .7, null); historical.tick(); historical.tick();
            Arrow reflected = f.arrow(ps.get(1), 1, 2.2, 2, null); reflected.tick();
            h.assertTrue(tracked("RECEIPTS", historical) && tracked("FLIGHTS", reflected), "actual native arrows occupy separate receipt and flight slots");
            BigDecimal before = shield(ps.get(1)); CompoundTag saved = reflected.saveWithoutId(new CompoundTag());
            historical.discard(); reflected.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertFalse(tracked("RECEIPTS", historical) || tracked("FLIGHTS", reflected) || tracked("TICKS", historical) || tracked("TICKS", reflected), "native EntityLeave callbacks immediately free observed arrow slots");
            h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(reflected) && saved.getCompound("NeoForgeData").contains(MultiAxisReflectionArrowBridge.SPENT), "native unload retains the permanent spent field in actual entity NBT");
            eq(h, shield(ps.get(1)), before, "unload never refunds the committed reflection"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void native_background_prune_expires_receipts_and_flights_without_touching_arrow_or_resources(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(2);
        f.ready(() -> {
            for (ServerPlayer p : ps) f.arm(p, "20");
            Arrow historical = f.arrow(ps.get(0), 1, 2.1, .7, null); historical.tick(); historical.tick();
            Arrow reflected = f.arrow(ps.get(1), 1, 2.2, 2, null); reflected.tick();
            h.assertTrue(tracked("RECEIPTS", historical) && tracked("FLIGHTS", reflected), "actual native movement creates observations before timeout");
            f.freeze(historical, reflected); Vec3 historicalAt = historical.position(), historicalV = historical.getDeltaMovement(), reflectedAt = reflected.position(), reflectedV = reflected.getDeltaMovement();
            BigDecimal firstS = shield(ps.get(0)), secondS = shield(ps.get(1)); PlayerPosts posts = f.posts(ps.get(1));
            posts.at(20, () -> h.assertTrue(tracked("RECEIPTS", historical) && tracked("FLIGHTS", reflected), "observation remains bounded but available through its closed twenty-tick TTL"));
            posts.at(21, () -> {
                h.assertFalse(tracked("RECEIPTS", historical) || tracked("FLIGHTS", reflected) || tracked("TICKS", historical) || tracked("TICKS", reflected), "genuine controller ServerTick.Pre prunes expired observations without another arrow tick");
                vector(h, historical.position(), historicalAt, "background observation pruning leaves historical arrow position"); vector(h, historical.getDeltaMovement(), historicalV, "background pruning leaves historical arrow velocity");
                vector(h, reflected.position(), reflectedAt, "background pruning leaves spent arrow position"); vector(h, reflected.getDeltaMovement(), reflectedV, "background pruning leaves spent arrow velocity");
                eq(h, shield(ps.get(0)), firstS, "receipt expiry never spends or recovers active S"); eq(h, shield(ps.get(1)), secondS, "flight expiry never refunds active S");
                h.assertTrue(!MultiAxisReflectionArrowBridge.reflecting(historical) && MultiAxisReflectionArrowBridge.reflecting(reflected), "expiry cannot create or clear a permanent spent mark"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void preset_death_clone_and_dimension_lifecycle_end_active_ownership_without_resetting_quota(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> ps = f.players(4);
        f.ready(() -> {
            for (int mode = 0; mode < ps.size(); mode++) {
                ServerPlayer p = ps.get(mode); f.arm(p, "20"); Arrow a = f.arrow(p, 1, 2.2, 2, null); a.tick();
                h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(a), "native reflection establishes spent state and real quota before lifecycle change");
                CompoundTag quota = p.getPersistentData().getCompound(MultiAxisReflection.QUOTA_KEY).copy();
                CompoundTag payment = p.getPersistentData().getCompound(MultiAxisReflection.PAYMENT_KEY).copy();
                BigDecimal before = shield(p);
                switch (mode) {
                    case 0 -> { var state = AbilityStorage.get(p); h.assertTrue(state.presets.edit(0, 0, VectorCage.ID, state::learned), "actual preset revision rebinds the native slot"); MultiAxisReflection.tick(p); }
                    case 1 -> { f.combat(p).putString("health", "1"); p.invulnerableTime = 0; h.assertTrue(p.hurt(p.damageSources().magic(), 2) && !p.isAlive(), "genuine native lethal hurt executes death lifecycle"); }
                    case 2 -> {
                        ServerPlayer replacement = new ServerPlayer(p.server, h.getLevel(), p.getGameProfile(), p.clientInformation());
                        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, p, false));
                        h.assertTrue(quota.equals(replacement.getPersistentData().getCompound(MultiAxisReflection.QUOTA_KEY))
                                && payment.equals(replacement.getPersistentData().getCompound(MultiAxisReflection.PAYMENT_KEY)), "standard native clone event copies exact remaining quota and payment debt");
                        h.assertFalse(MultiAxisReflection.active(replacement), "new clone identity receives no active controller");
                    }
                    case 3 -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, h.getLevel().dimension(), net.minecraft.world.level.Level.NETHER));
                    default -> throw new IllegalStateException();
                }
                h.assertFalse(MultiAxisReflection.active(p) || tracked("FLIGHTS", a), "lifecycle ends old ownership and observed flight: " + mode);
                h.assertTrue(quota.equals(p.getPersistentData().getCompound(MultiAxisReflection.QUOTA_KEY)) && MultiAxisReflection.remainingHits(p) == 3,
                        "lifecycle never resets the committed reflection quota: " + mode);
                h.assertTrue(MultiAxisReflectionArrowBridge.reflecting(a), "lifecycle cannot clear permanent arrow-once evidence"); eq(h, shield(p), before, "lifecycle never refunds committed S");
                a.discard();
            }
            f.succeed();
        });
    }

    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static BigDecimal hp(LivingEntity entity) { return n(HighPowerCombat.healthText(entity)); }
    private static BigDecimal shield(LivingEntity entity) { return HighPowerCombat.shield(entity); }
    private static BigDecimal share(ServerPlayer p, String name) { return n(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(MagneticDomainReserve.KEY).getString(name)); }
    private static BigDecimal raw(Arrow a) { return MultiAxisReflectionRules.nativeArrowDamage(a.getDeltaMovement().length(), a.getBaseDamage(), a.getWeaponItem() != null); }
    /** Read-only diagnostics; no test writes bridge authority, receipts or flight frames. */
    private static boolean tracked(String map, Arrow arrow) {
        try { var field = MultiAxisReflectionArrowBridge.class.getDeclaredField(map); field.setAccessible(true); return ((Map<?, ?>)field.get(null)).containsKey(arrow); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static MultiAxisReflectionGeometry.Vector v(Vec3 v) { return new MultiAxisReflectionGeometry.Vector(v.x, v.y, v.z); }
    private static Vec3 vec(MultiAxisReflectionGeometry.Vector v) { return new Vec3(v.x(), v.y(), v.z()); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-6, why + ": " + actual + " != " + expected); }
    private static void vector(GameTestHelper h, Vec3 actual, Vec3 expected, String why) { h.assertTrue(actual.distanceToSqr(expected) < 1e-10, why + ": " + actual + " != " + expected); }
    private static void fly(Arrow arrow, int ticks) { for (int i = 0; i < ticks && !arrow.isRemoved(); i++) arrow.tick(); }
    private static void face(LivingEntity entity, float yaw) { entity.setYRot(yaw); entity.setYHeadRot(yaw); entity.yBodyRot = yaw; }
    private static void defenses(GameTestHelper h, LivingEntity entity) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET); helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4); helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        entity.setItemSlot(EquipmentSlot.HEAD, helmet); entity.getAttribute(Attributes.ARMOR).setBaseValue(5); entity.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0)); entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); entity.setAbsorptionAmount(.1F);
    }
    public static final class ImpactGate {
        final Arrow arrow; final int mode; final Entity replacement; final ServerPlayer defender; int calls;
        ImpactGate(Arrow arrow, int mode, Entity replacement, ServerPlayer defender) { this.arrow = arrow; this.mode = mode; this.replacement = replacement; this.defender = defender; }
        @SubscribeEvent public void impact(ProjectileImpactEvent e) { if (e.getProjectile() != arrow) return; calls++; switch (mode) {
            case 0 -> e.setCanceled(true); case 1 -> arrow.setOwner(replacement); case 2 -> arrow.setBaseDamage(3);
            case 3 -> arrow.setPos(arrow.position().add(.01, 0, 0)); case 4 -> arrow.setDeltaMovement(arrow.getDeltaMovement().scale(.9));
            case 5 -> arrow.getWeaponItem().setDamageValue(1); case 6 -> arrow.setOwner(arrow.getOwner());
            case 7 -> defender.setYRot(defender.getYRot() + 1); default -> throw new IllegalStateException();
        } }
    }
    public static final class Watch {
        final LivingEntity target; int incoming, posts, skills, shieldEvents; float damage, armor, effects, enchantments, absorption; DamageSource source;
        Watch(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == target) { incoming++; source = e.getSource(); } }
        @SubscribeEvent public void post(LivingDamageEvent.Post e) { if (e.getEntity() != target) return; posts++; damage = e.getNewDamage(); armor = e.getReduction(DamageContainer.Reduction.ARMOR); effects = e.getReduction(DamageContainer.Reduction.MOB_EFFECTS); enchantments = e.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = e.getReduction(DamageContainer.Reduction.ABSORPTION); }
        @SubscribeEvent public void shield(LivingShieldBlockEvent e) { if (e.getEntity() == target) shieldEvents++; }
        @SubscribeEvent public void skill(SkillAttackEvent e) { if (e.target == target) skills++; }
    }
    public static final class PlayerPosts {
        final Fixture f; final GameTestHelper h; final ServerPlayer p; final Map<Integer, Runnable> checks = new HashMap<>(); int last, completed;
        PlayerPosts(Fixture f, ServerPlayer p) { this.f = f; this.h = f.h; this.p = p; last = p.server.getTickCount(); }
        void at(int count, Runnable action) { checks.put(count, action); }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(PlayerTickEvent.Post e) {
            if (f.terminalQueued || h.testInfo.isDone() || e.getEntity() != p || p.server.getTickCount() <= last) return;
            last = p.server.getTickCount(); Runnable check = checks.remove(++completed);
            if (check != null) try { check.run(); } catch (Throwable failure) { System.err.println("NEW442 native Post " + completed + " failed: " + failure); f.fail(failure); }
        }
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>(); final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>(); final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>(); final Map<ServerPlayer, String> held = new IdentityHashMap<>();
        final Map<BlockPos, BlockState> previousBlocks = new LinkedHashMap<>(); MachineDeveloperBlockEntity machine; int sequence;
        boolean closed, terminalQueued; Throwable terminalFailure;
        Fixture(GameTestHelper h) {
            this.h = h;
            // Native PlayerPost checks run outside GameTestInfo.tickInternal. Queue only their
            // terminal result; vanilla must observe the transition inside its own scheduled tick
            // to notify completion listeners and start the next batch. Register before iteration.
            h.onEachTick(this::finishQueuedTerminal);
            h.testInfo.addListener(new GameTestListener() { public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); } public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); } });
        }
        void succeed() { if (!closed && !terminalQueued) terminalQueued = true; }
        void fail(Throwable failure) { if (!closed && !terminalQueued) { terminalFailure = failure; terminalQueued = true; } }
        void finishQueuedTerminal() {
            if (closed || !terminalQueued || h.testInfo.isDone()) return;
            if (terminalFailure == null) h.succeed(); else h.testInfo.fail(terminalFailure);
        }
        List<ServerPlayer> players(int count) { List<ServerPlayer> result = new ArrayList<>(); for (int i = 0; i < count; i++) result.add(player(10 + i % 3 * 18, 10 + i / 3 * 20)); return result; }
        ServerPlayer player(double x, double z) { return player(x, z, true); }
        ServerPlayer player(double x, double z, boolean learned) {
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "MAR" + UUID.randomUUID().toString().substring(0, 9)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel); NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(p);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) h.setBlock(BlockPos.containing(x + dx, 1, z + dz), Blocks.STONE);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x + .5, 2, z + .5))); place(p);
            h.assertTrue(HighPowerCombat.enroll(p, 4), "connected LV4 native exact enrollment"); var state = AbilityStorage.get(p); state.selectCategory(HighPowerCategories.VECMANIP); state.setLevel(4);
            for (String skill : List.of(HighPowerSkills.DEFLECTION_STRIKE, VectorParry.ID, BrakingTouch.ID, InertialAnchor.ID, CirculationRelief.ID, TrajectoryReturn.ID, CirculationListening.ID, VectorCage.ID)) state.learn(skill);
            if (learned) state.learn(ID); state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, learned ? ID : HighPowerSkills.DEFLECTION_STRIKE, state::learned), "real learned ordinary input binding");
            HighPowerResources.initializeBudget(p); hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5)); return p;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { for (ServerPlayer p : players) { p.doTick(); place(p); } action.run(); }); }
        void place(ServerPlayer p) { Vec3 at = anchors.get(p); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0; p.setShiftKeyDown(false); p.setPose(Pose.STANDING); p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        Vec3 center(ServerPlayer p) { return p.getBoundingBox().getCenter(); }
        Vec3 normal(ServerPlayer p, int facet) { return vec(MultiAxisReflectionGeometry.facetNormal(MultiAxisReflectionGeometry.pose(v(center(p)), v(p.getLookAngle())), facet)); }
        String wire(ServerPlayer p) { long input = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, input); return new HighPowerInput.Input(0, input, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        boolean start(ServerPlayer p) { String wire = wire(p); boolean result = AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request("multi_axis_reflection_start", wire)); if (result && MultiAxisReflection.active(p)) held.put(p, wire); return result; }
        void arm(ServerPlayer p, String amount) { shield(p, n(amount)); h.assertTrue(start(p), "authenticated paid NEW442 toggle starts: seam=" + MultiAxisReflectionArrowBridge.available() + ", unsupported=" + HighPowerCombat.unsupportedReason(p) + ", payment=" + p.getPersistentData().getCompound(MultiAxisReflection.PAYMENT_KEY)); }
        void shield(ServerPlayer p, BigDecimal amount) { combat(p).putString("shield", amount.toPlainString()); }
        CompoundTag combat(ServerPlayer p) { return p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); }
        CompoundTag budget(ServerPlayer p) { return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); }
        void reserve(ServerPlayer p, String lower, String upper) { CompoundTag r = new CompoundTag(); r.putInt("version", MagneticDomainReserve.VERSION); r.putString("capacity", HighPowerCombat.maxShieldText(p)); r.putString("lower", lower); r.putString("upper", upper); r.putString("amount", n(lower).add(n(upper)).toPlainString()); combat(p).put(MagneticDomainReserve.KEY, r); }
        Skeleton mob(Vec3 center) { Skeleton mob = new Skeleton(EntityType.SKELETON, h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true); mob.setSilent(true); mob.setPos(center.add(0, -mob.getBbHeight() / 2, 0)); mob.getAttribute(Attributes.ARMOR).setBaseValue(0); mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1); entity(mob); return mob; }
        Skeleton shooter(ServerPlayer p, Vec3 direction) { return mob(center(p).add(direction.scale(6))); }
        Arrow arrow(ServerPlayer p, int facet, double distance, double speed, ItemStack weapon) { return arrow(p, facet, distance, speed, weapon, shooter(p, normal(p, facet))); }
        Arrow arrow(ServerPlayer p, int facet, double distance, double speed, ItemStack weapon, Skeleton shooter) { Vec3 normal = normal(p, facet); return arrowAt(center(p).add(normal.scale(distance)), normal.scale(-speed), weapon, shooter); }
        Arrow arrowAt(ServerPlayer p, Vec3 at, Vec3 velocity, ItemStack weapon, Vec3 direction) { return arrowAt(at, velocity, weapon, shooter(p, direction)); }
        Arrow arrowAt(Vec3 at, Vec3 velocity, ItemStack weapon, Skeleton shooter) {
            Arrow arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), weapon); arrow.setPos(at); arrow.setDeltaMovement(velocity.normalize().scale(-.01)); entity(arrow);
            arrow.tick(); h.assertTrue(!arrow.isRemoved() && ((NativeScatterArrowOwnerAccess)arrow).academy$scatterLeftOwner(), "ordinary native launch tick establishes left-owner evidence");
            arrow.setPos(at); arrow.setDeltaMovement(velocity); return arrow;
        }
        void entity(Entity entity) { h.assertTrue(h.getLevel().addFreshEntity(entity), "actual native entity joins server level"); entities.add(entity); }
        void block(Vec3 point, BlockState state) { BlockPos pos = BlockPos.containing(point); previousBlocks.putIfAbsent(pos, h.getLevel().getBlockState(pos)); h.getLevel().setBlockAndUpdate(pos, state); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void freeze(Arrow... arrows) { Set<Arrow> selected = Set.of(arrows); hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(EntityTickEvent.Pre event) { if (selected.contains(event.getEntity())) event.setCanceled(true); } }); }
        Watch watch(LivingEntity entity) { Watch watch = new Watch(entity); hook(watch); return watch; }
        PlayerPosts posts(ServerPlayer p) { PlayerPosts posts = new PlayerPosts(this, p); hook(posts); return posts; }
        void drain(ServerPlayer p) { EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet; while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); } }
        void reload(ServerPlayer p) { AbilityStorage.save(p); CompoundTag saved = p.saveWithoutId(new CompoundTag()); MultiAxisReflection.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.load(saved); place(p); inputs.put(p, 0L); AbilityStorage.get(p); }
        MachineDeveloperBlockEntity advanced(ServerPlayer p, double amount) {
            BlockPos base = h.absolutePos(new BlockPos(30, 1, 45)); if (machine == null) { var item = MachineDevelopers.ADVANCED_ITEM.get(); ItemStack stack = new ItemStack(item); p.setItemInHand(InteractionHand.MAIN_HAND, stack); h.assertTrue(item.place(new BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(base), Direction.UP, base, false))).consumesAction(), "actual complete Advanced developer placed"); machine = (MachineDeveloperBlockEntity)h.getLevel().getBlockEntity(base); }
            machine.endUsers(); machine.battery().load(amount); p.moveTo(base.getX() + .5, base.getY(), base.getZ() - 1.5, 0, 0); h.assertTrue(machine.use(p), "real bound Advanced developer session opens"); return machine;
        }
        boolean learnRequest(ServerPlayer p) { return MachineDeveloperSessions.request(p, new AcademyNetwork.Request("machine_hp_learn", MachineDeveloperSessions.activeToken(p).orElseThrow() + ":" + new HighPowerLearningProtocol.Request(ID, ++sequence).wire())); }
        void develop(ServerPlayer p, int ticks) { for (int i = 0; i < ticks; i++) DevelopmentController.tick(p, ignored -> {}); }
        void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); if (machine != null) machine.endUsers();
            for (ServerPlayer p : players) { MultiAxisReflection.remove(p); VectorCage.remove(p); CirculationRelief.remove(p); InertialAnchor.remove(p); VectorParry.remove(p); BrakingTouch.remove(p); DevelopmentController.remove(p); MachineDeveloperSessions.close(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); }
            entities.forEach(Entity::discard); previousBlocks.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state)); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}
