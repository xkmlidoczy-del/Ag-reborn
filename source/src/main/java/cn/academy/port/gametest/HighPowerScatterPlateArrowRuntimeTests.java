package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real vanilla arrow ticks and body impacts, without resetting native hurt cooldowns. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class HighPowerScatterPlateArrowRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_scatter_arrows";
    private static final TicketType<UUID> SATURATION_TICKET = TicketType.create("academy_scatter_saturation_fixture", UUID::compareTo);
    @SubscribeEvent
    public static void installCorridor(net.neoforged.neoforge.event.level.LevelEvent.Load e) {
        if (!net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()
                || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        installCorridor(level, "academy_highpower");
    }
    public static void installCorridor(net.minecraft.server.level.ServerLevel level, String namespace) {
        var tag = new net.minecraft.nbt.CompoundTag();
        var size = new net.minecraft.nbt.ListTag();
        for (int n : new int[] {16, 24, 72}) size.add(net.minecraft.nbt.IntTag.valueOf(n));
        tag.put("size", size); tag.put("blocks", new net.minecraft.nbt.ListTag());
        tag.put("palette", new net.minecraft.nbt.ListTag()); tag.put("entities", new net.minecraft.nbt.ListTag());
        level.getStructureManager().getOrCreate(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, "scatter_saturation_corridor"))
                .load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), tag);
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_native_same_tick_front_contact_preserves_arrow_disposition(GameTestHelper h) {
        Fixture f = new Fixture(h, 0), control = new Fixture(h, 12);
        h.runAfterDelay(61, () -> { f.activate();
        BigDecimal shield = f.shield(), hp = f.hp(), controlHp = control.hp(); double cp = f.cp();
        Arrow a = f.arrow(0, 1, 3, new Vec3(0, 0, -3)), b = control.arrow(0, 1, 3, new Vec3(0, 0, -3));
        a.tick(); b.tick();
        BigDecimal nativeDamage = controlHp.subtract(control.hp());
        h.assertTrue(nativeDamage.signum() > 0, "unshielded paired native arrow genuinely hurt");
        eq(h, f.shield(), shield.subtract(nativeDamage), "one contacted share pays exactly native damage"); eq(h, f.hp(), hp, "front plate protects HP");
        h.assertTrue(a.isRemoved() == b.isRemoved() && a.getDeltaMovement().equals(b.getDeltaMovement()) && a.pickup == b.pickup, "native return/disposition/velocity/pickup stay identical");
        h.assertTrue(a.getOwner() == f.shooter && b.getOwner() == control.shooter, "native owners preserved");
        h.assertTrue(f.cp() == cp && ScatterPlateArrowReceipts.receiptCount(f.p) == 0, "impact does not charge extra CP and consumes receipt"); h.succeed();
            });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_slow_crossing_survives_ordinary_movement_and_turning(GameTestHelper h) {
        Fixture f = new Fixture(h, 0);
        h.runAfterDelay(61, () -> { f.activate(); BigDecimal shield = f.shield(), hp = f.hp();
        Arrow arrow = f.arrow(0, 1, 1.2, new Vec3(0, 0, -.3)); arrow.tick();
        h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 1, "slow real arrow crosses plate without body contact"); eq(h, f.shield(), shield, "crossing alone never spends");
        f.p.setPos(f.p.position().add(.1, 0, 0)); f.p.setYRot(90); f.p.connection.resetPosition();
        h.runAfterDelay(4, () -> { h.assertTrue(arrow.isRemoved(), "native slow arrow eventually reaches body"); h.assertTrue(f.shield().compareTo(shield) < 0, "historical same-activation contact absorbs after movement/turn"); eq(h, f.hp(), hp, "historical contact protects HP"); h.succeed(); });
            });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 93)
    public static void scatter_graze_has_no_hurt_debit_or_idle_mastery_and_expires(GameTestHelper h) {
        Fixture f = new Fixture(h, 0);
        h.runAfterDelay(61, () -> { f.activate(); BigDecimal shield = f.shield(), hp = f.hp(); double xp = AbilityStorage.get(f.p).exp(ScatterPlates.ID);
        Arrow arrow = f.arrow(.8, 1, 1.2, new Vec3(0, 0, -.3)); arrow.tick();
        h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 1, "outer ellipse graze records crossing"); eq(h, f.shield(), shield, "graze gives no damage callback/debit");
        h.runAfterDelay(21, () -> { eq(h, f.shield(), shield, "expired graze cannot spend any shield"); eq(h, f.hp(), hp, "graze does not synthesize hurt"); h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 0, "fixed20tick receipt expires"); h.assertTrue(AbilityStorage.get(f.p).exp(ScatterPlates.ID) == xp, "standing/graze gives no idle mastery"); h.succeed(); });
            });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_gap_rear_side_and_inside_arrows_remain_native_vulnerabilities(GameTestHelper h) {
        Fixture[] fixtures = new Fixture[4];
        for (int actor = 0; actor < fixtures.length; actor++) fixtures[actor] = new Fixture(h, actor * 12);
        h.runAfterDelay(61, () -> {
        double[][] starts = {{.4, 1, 3}, {0, 1, -3}, {3, 1, 0}, {0, 1, .8}};
        Vec3[] velocity = {new Vec3(0, 0, -3), new Vec3(0, 0, 3), new Vec3(-3, 0, 0), new Vec3(0, 0, -1)};
        for (int i = 0; i < starts.length; i++) {
            Fixture f = fixtures[i]; f.activate(); BigDecimal shield = f.shield(), hp = f.hp();
            f.arrow(starts[i][0], starts[i][1], starts[i][2], velocity[i]).tick();
            eq(h, f.shield(), shield, "physical vulnerability does not spend shield " + i); h.assertTrue(f.hp().compareTo(hp) < 0, "native body damage through vulnerability " + i);
        }
        h.succeed();
            });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_nearest_wall_body_and_ambiguous_tie_refuse_crossing(GameTestHelper h) {
        Fixture[] fixtures = new Fixture[3];
        for (int actor = 0; actor < fixtures.length; actor++) fixtures[actor] = new Fixture(h, actor * 12);
        h.runAfterDelay(61, () -> {
        for (int mode = 0; mode < 3; mode++) {
            Fixture f = fixtures[mode]; f.activate(); BigDecimal shield = f.shield();
            if (mode == 0) { BlockPos wall = BlockPos.containing(f.p.position().add(0, 1, 2)); h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState()); f.blocks.add(wall); }
            else if (mode == 1) f.mob(f.p.position().add(0, 0, 2));
            else f.mob(f.p.position());
            f.arrow(0, 1, 3, new Vec3(0, 0, -3)).tick();
            eq(h, f.shield(), shield, "first native wall/body/tie never creates shield interception " + mode);
            h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 0, "no pending receipt after blocked/ambiguous native contact");
        }
        h.succeed();
            });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_cancel_zero_and_native_iframe_consume_without_reward(GameTestHelper h) {
        Fixture[] fixtures = new Fixture[3];
        for (int actor = 0; actor < fixtures.length; actor++) fixtures[actor] = new Fixture(h, actor * 12);
        h.runAfterDelay(61, () -> {
        for (int mode = 0; mode < 3; mode++) {
            Fixture f = fixtures[mode]; f.activate(); BigDecimal shield = f.shield(); double xp = AbilityStorage.get(f.p).exp(ScatterPlates.ID);
            final int kind = mode;
            if (mode < 2) f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == f.p) { if (kind == 0) e.setCanceled(true); else e.setAmount(0); } } });
            else { h.assertTrue(f.p.hurt(f.p.damageSources().mobAttack(f.shooter), 10), "real first hit establishes vanilla iframe"); shield = f.shield(); }
            Arrow arrow = f.arrow(0, 1, 3, new Vec3(0, 0, -3)); arrow.tick();
            eq(h, f.shield(), shield, "cancel/zero/iframe gives no shield debit " + mode); h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 0, "native failed hurt consumed receipt " + mode);
            h.assertTrue(AbilityStorage.get(f.p).exp(ScatterPlates.ID) == xp, "failed native hurt gives no mastery");
        }
        h.succeed();
            });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_forged_arrow_source_and_reentrant_callback_cannot_claim_twice(GameTestHelper h) {
        Fixture f = new Fixture(h, 0);
        h.runAfterDelay(61, () -> { f.activate(); BigDecimal shield = f.shield();
        Arrow arrow = f.arrow(0, 1, 1.2, new Vec3(0, 0, -.3)); arrow.tick();
        h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 1, "genuine historical crossing pending");
        h.assertTrue(f.p.hurt(f.p.damageSources().arrow(arrow, f.shooter), .1F), "constructed arrow source takes ordinary native path");
        eq(h, f.shield(), shield, "constructed matching arrow source cannot claim physical receipt");
        boolean[] reentered = {false};
        f.hook(new Object() { @SubscribeEvent(priority = EventPriority.LOWEST) public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == f.p && e.getSource().getDirectEntity() == arrow && !reentered[0]) { reentered[0] = true; f.p.hurt(e.getSource(), .05F); } } });
        // Let the genuine slow arrow finish its remaining native steps before the first ordinary
        // hurt's native knockback has moved the player out of that historical flight corridor.
        arrow.tick(); arrow.tick();
        h.assertTrue(reentered[0], "real native hurt callback attempted reentry"); h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 0, "receipt retired once despite reentrant same source"); h.assertTrue(shield.subtract(f.shield()).compareTo(BigDecimal.ONE) <= 0, "reentry cannot charge receipt twice"); h.succeed();
            });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_owner_base_weapon_critical_and_competing_lease_changes_invalidate(GameTestHelper h) {
        Fixture[] fixtures = new Fixture[5];
        for (int actor = 0; actor < fixtures.length; actor++) fixtures[actor] = new Fixture(h, actor * 12);
        h.runAfterDelay(61, () -> {
        for (int mode = 0; mode < 5; mode++) {
            Fixture f = fixtures[mode]; f.activate(); BigDecimal shield = f.shield();
            ItemStack bow = new ItemStack(Items.BOW); Arrow arrow = f.arrowWithWeapon(0, 1, 1.2, new Vec3(0, 0, -.3), bow); arrow.tick();
            h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 1, "plain bow crossing admitted " + mode);
            switch (mode) {
                case 0 -> arrow.setOwner(f.mob(f.p.position().add(5, 0, 0)));
                case 1 -> arrow.setBaseDamage(3);
                case 2 -> arrow.getWeaponItem().setCount(2);
                case 3 -> arrow.setCritArrow(true);
                default -> arrow.getPersistentData().putLong("academy:ground_step_arrow_until", f.p.server.overworld().getGameTime() + 20);
            }
            ScatterPlateArrowReceipts.prune(f.p.server); h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 0, "source mutation invalidates before claim " + mode); eq(h, f.shield(), shield, "mutation itself cannot charge shield");
        }
        h.succeed();
            });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_projectile_impact_cancel_and_native_teleport_clear_pending(GameTestHelper h) {
        Fixture f = new Fixture(h, 0);
        h.runAfterDelay(61, () -> { f.activate(); BigDecimal shield = f.shield();
        Arrow arrow = f.arrow(0, 1, 1.2, new Vec3(0, 0, -.3)); arrow.tick();
        f.hook(new Object() { @SubscribeEvent public void impact(ProjectileImpactEvent e) { if (e.getProjectile() == arrow) e.setCanceled(true); } });
        h.runAfterDelay(4, () -> { eq(h, f.shield(), shield, "canceled native impact never debits"); h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 0, "impact cancellation discards receipt permanently");
            f.p.connection.teleport(f.p.getX() + .1, f.p.getY(), f.p.getZ(), 0, 0); h.assertFalse(ScatterPlates.active(f.p), "even a short explicit native teleport retires controller"); h.succeed(); });
            });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 95)
    public static void scatter_retract_redeploy_and_reload_always_keep_paid_fences(GameTestHelper h) {
        Fixture f = new Fixture(h, 0);
        h.runAfterDelay(61, () -> {
            PlateClientBinding client = new PlateClientBinding(f); double initial = f.cp(); client.tick(true);
            h.assertTrue(client.accepted && ScatterPlates.active(f.p), "real input binding starts paid LV1 toggle through authenticated ingress");
            near(h, f.cp(), initial - 4, "LV1 activation exact CP");
            h.assertTrue(client.state.cooldowns.getOrDefault(ScatterPlates.ID, 0) == 1, "real paid snapshot has only a one-tick payment guard");
            h.runAfterDelay(1, () -> {
                client.tick(true);
                h.assertTrue(client.aborts == 0 && ScatterPlates.active(f.p), "next client poll with held key does not turn the paid cooldown into an abort");
                client.tick(false);
                h.assertTrue(client.releases == 1 && client.aborts == 0 && ScatterPlates.active(f.p), "ordinary release stays inert after the real paid snapshot");
                double beforeRetract = f.cp(); client.tick(true);
                h.assertTrue(client.accepted && !ScatterPlates.active(f.p), "next physical press freely retracts the active toggle");
                near(h, f.cp(), beforeRetract, "authenticated retract has no payment");
                h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(ScatterPlates.ID, 0) == 10, "LV1 finish retains the listed ten-tick cooldown");
                var fence = f.p.getPersistentData().getCompound(ScatterPlates.PAYMENT_KEY);
                h.assertTrue(fence.getLong("stopUntil") - fence.getLong("observed") == 10, "persistent stop fence retains all ten LV1 ticks");
                double after = f.cp();
                h.assertFalse(ScatterPlates.toggle(f.p, f.wire(3)), "same-tick redeploy denied"); near(h, f.cp(), after, "denied redeploy has no charge");
                AbilityStorage.save(f.p); var saved = new net.minecraft.nbt.CompoundTag(); f.p.saveWithoutId(saved); ScatterPlates.remove(f.p); AbilityStorage.remove(f.p); LegacySingleKeyOwnerEpoch.forget(f.p); f.p.load(saved); f.place(); f.epoch();
                h.assertFalse(ScatterPlates.toggle(f.p, f.wire(1)), "relog cannot clear persistent cooldown");
                h.runAfterDelay(9, () -> {
                    double before = f.cp(); h.assertFalse(ScatterPlates.toggle(f.p, f.wire(2)), "reloaded LV1 deployment is still blocked one tick before the stop boundary");
                    near(h, f.cp(), before, "early post-reload input has no payment");
                    h.runAfterDelay(1, () -> { double ready = f.cp(); h.assertTrue(ScatterPlates.toggle(f.p, f.wire(3)), "listed LV1 cooldown allows fresh activation at its boundary before old20tick lease ends"); near(h, f.cp(), ready - 4, "fresh activation pays full cost again"); h.succeed(); });
                });
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void scatter_productive_mastery_stamps_before_reentry_and_survives_relog(GameTestHelper h) {
        Fixture f = new Fixture(h, 0);
        h.runAfterDelay(61, () -> { f.activate(); double initialXp = AbilityStorage.get(f.p).exp(ScatterPlates.ID); boolean[] callback = {false}, accepted = {true};
        f.hook(new Object() { @SubscribeEvent public void experience(cn.academy.port.api.SkillExpAddedEvent e) {
            if (e.getEntity() == f.p && e.skill.equals(ScatterPlates.ID) && !callback[0]) {
                callback[0] = true; accepted[0] = ScatterPlates.toggle(f.p, f.wire(2));
                h.assertTrue(f.p.getPersistentData().contains(ScatterPlates.MASTERY_KEY), "productive fence exists before XP callback");
            }
        } });
        f.arrow(0, 1, 3, new Vec3(0, 0, -3)).tick();
        near(h, AbilityStorage.get(f.p).exp(ScatterPlates.ID), initialXp + .005, "one productive absorption earns provisional mastery");
        h.assertTrue(callback[0] && !accepted[0] && ScatterPlates.active(f.p), "mutable award callback cannot replace resolving controller");
        f.arrow(0, 1, 4, new Vec3(0, 0, -6)).tick(); double earned = AbilityStorage.get(f.p).exp(ScatterPlates.ID);
        near(h, earned, initialXp + .005, "second stronger real hit inside20ticks cannot farm mastery");
        h.assertTrue(ScatterPlates.toggle(f.p, f.wire(2)), "refused callback did not poison next legitimate input");
        AbilityStorage.save(f.p); var saved = new net.minecraft.nbt.CompoundTag(); f.p.saveWithoutId(saved); ScatterPlates.remove(f.p); AbilityStorage.remove(f.p); LegacySingleKeyOwnerEpoch.forget(f.p); f.p.load(saved); f.place(); f.epoch();
        h.runAfterDelay(11, () -> {
            h.assertTrue(ScatterPlates.toggle(f.p, f.wire(1)), "fresh paid deployment after real cooldown");
            BigDecimal before = f.shield(); Arrow actual = f.arrow(0, 1, 4, new Vec3(0, 0, -8)); actual.tick();
            h.assertTrue(actual.isRemoved() && f.shield().compareTo(before) < 0, "genuine positive native absorption after reload still occurs inside award fence");
            near(h, AbilityStorage.get(f.p).exp(ScatterPlates.ID), earned, "serialized productive fence survives redeploy/relog");
        });
        h.runAfterDelay(23, () -> {
            h.assertTrue(ScatterPlates.active(f.p), "same paid post-reload controller remains active at later productive contact");
            BigDecimal before = f.shield(); Arrow actual = f.arrow(0, 1, 4, new Vec3(0, 0, -10)); actual.tick();
            h.assertTrue(actual.isRemoved() && f.shield().compareTo(before) < 0, "later native contact actually absorbs before mastery assertion");
            near(h, AbilityStorage.get(f.p).exp(ScatterPlates.ID), earned + .005, "next positive native absorption after real20ticks earns one award"); h.succeed();
        });
            });
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_unloaded", timeoutTicks = 201)
    public static void scatter_unloaded_native_arrow_retires_receipt_without_changing_motion(GameTestHelper h) {
        // Retain the actual failed origin as a native retirement negative. Only the launch
        // chunk receives a fixture ticket; the third chunk north must remain inaccessible.
        Fixture f = new Fixture(h, 0, new Vec3(12109636.5, -42, 7650681.5));
        // Prime FULL generation before asking vanilla to promote the launch chunk to
        // ENTITY_TICKING. That promotion depends on the complete 5x5 FULL neighbourhood;
        // a FULL cache hit for just the launch chunk does not await those dependencies.
        f.prepareLaunchChunks(new ChunkPos(f.p.blockPosition()));
        f.hold(new ChunkPos(f.p.blockPosition()));
        // FULL generation alone does not await native accessibility/entity-ticking promotion.
        // Demand only chunks already covered by the launch ticket. In particular z=478164
        // stays outside this 5x5 area and never receives a request or an extra ticket.
        ChunkPos unloaded = new ChunkPos(756852, 478164);
        // Native FULL promotion and entity-storage completion are separate asynchronous steps.
        // Keep the original login wait, then allow source readiness through test tick 200.
        // Only this setup is polled; the original native-arrow body is attempted exactly once.
        boolean[] attempted = {false};
        boolean[] prepared = {false};
        h.onEachTick(() -> {
            if (attempted[0] || h.getTick() < 61) return;
            // A cached launch FULL chunk can return before the new region ticket is
            // propagated. Wait for normal server ticks before demanding its dependants.
            if (!prepared[0]) { f.prepareHeldChunks(); prepared[0] = true; }
            // A loaded destination fails immediately even while sources are pending: never
            // wait for it to unload, add a destination ticket, or request destination blocks.
            h.assertFalse(h.getLevel().areEntitiesLoaded(unloaded.toLong()),
                    "original northbound destination has no loaded entities during source setup");
            boolean launchLoaded = h.getLevel().areEntitiesLoaded(ChunkPos.asLong(f.p.blockPosition()));
            boolean launchTicking = h.getLevel().isPositionEntityTicking(f.p.blockPosition());
            boolean sourceReady = launchLoaded && launchTicking;
            StringBuilder residency = new StringBuilder("tick=").append(h.getTick())
                    .append(", launchLoaded=").append(launchLoaded).append(", launchTicking=").append(launchTicking);
            for (int z = 478165; z <= 478167; z++) {
                boolean loaded = h.getLevel().areEntitiesLoaded(ChunkPos.asLong(756852, z));
                sourceReady &= loaded;
                residency.append(", source[").append(z).append("]={entitiesLoaded=").append(loaded)
                        .append(", chunkNow=").append(h.getLevel().getChunkSource().getChunkNow(756852, z) != null).append('}');
            }
            if (!sourceReady) {
                if (h.getTick() == 199) {
                    var log = com.mojang.logging.LogUtils.getLogger();
                    log.info("SCATTER_SOURCE_SETUP player={} held={} launch={} detail={}", f.p.position(), f.held,
                            h.getLevel().getChunkSource().getChunkDebugData(new ChunkPos(f.p.blockPosition())), residency);
                }
                h.assertTrue(h.getTick() < 200, "negative source residency did not become ready by setup tick 200; " + residency);
                return;
            }
            attempted[0] = true;
            h.assertTrue(h.getLevel().areEntitiesLoaded(ChunkPos.asLong(f.p.blockPosition()))
                    && h.getLevel().isPositionEntityTicking(f.p.blockPosition()), "negative launch chunk resident");
            for (int z = 478165; z <= 478167; z++) h.assertTrue(h.getLevel().areEntitiesLoaded(ChunkPos.asLong(756852, z)),
                    "original first15step source corridor has loaded entities in chunk " + z);
            // Read entity-load state only: querying destination blocks/getChunk would alter this precondition.
            h.assertFalse(h.getLevel().areEntitiesLoaded(unloaded.toLong()), "original northbound destination has no loaded entities");
            f.activate(); BigDecimal shield = f.shield(), hp = f.hp();
            Arrow arrow = f.arrow(.8, 1, 1.2, new Vec3(0, 0, -3));
            for (int step = 1; step <= 15; step++) {
                arrow.tick();
                h.assertTrue(h.getLevel().getEntity(arrow.getId()) == arrow && h.getLevel().getEntity(arrow.getUUID()) == arrow,
                        "native indexes retain the first15steps " + step);
                h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 1 && ScatterPlateArrowReceipts.hasEvidence(arrow),
                        "genuine graze receipt stays pending before native unload " + step);
            }
            Vec3 before = arrow.position(), velocity = arrow.getDeltaMovement();
            h.assertTrue(new ChunkPos(BlockPos.containing(before.add(velocity))).equals(unloaded), "native step16 enters original unloaded chunk");
            h.assertFalse(h.getLevel().areEntitiesLoaded(unloaded.toLong()), "destination remains unloaded immediately before native movement");
            arrow.tick();
            h.assertTrue(!arrow.isRemoved() && !arrow.inGround && arrow.position().equals(before.add(velocity))
                    && arrow.getDeltaMovement().equals(velocity.scale((double).99F).add(0, -.05, 0)),
                    "native unloaded transition preserves exact motion and disposition");
            h.assertTrue(h.getLevel().getEntity(arrow.getId()) == null && h.getLevel().getEntity(arrow.getUUID()) == null,
                    "native entity manager withdraws both indexes in inaccessible chunk");
            h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 0 && !ScatterPlateArrowReceipts.hasEvidence(arrow),
                    "native index loss retires the historical receipt");
            eq(h, f.shield(), shield, "native unloaded graze spends no shield"); eq(h, f.hp(), hp, "native unloaded graze does not hurt");
            h.succeed();
        });
    }

    @GameTest(template = "scatter_saturation_corridor", batch = BATCH, timeoutTicks = 80)
    public static void scatter_controller_receipt_saturation_never_changes_native_arrow_tick(GameTestHelper h) {
        // The seventeen-step trajectory is ~47 blocks long. Keep it inside its own
        // structure: the default spawn at z=8 crossed GameTest's invisible north wall.
        Fixture f = new Fixture(h, 0, h.absoluteVec(new Vec3(8.5, 18, 60.5)));
        // Every prior arrow advances once per loop, so the oldest completes 17 native steps.
        // Hold that complete corridor before the existing setup delay; template chunks alone
        // do not cover it at every randomized GameTest origin/chunk alignment.
        List<BlockPos> nativePath = f.holdSaturationPath();
        h.runAfterDelay(61, () -> {
        for (BlockPos pos : nativePath) h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)) && h.getLevel().areEntitiesLoaded(ChunkPos.asLong(pos))
                && h.getLevel().isPositionEntityTicking(pos), "full17step native saturation corridor resident at " + pos
                + "; held=" + f.held + "; " + h.getLevel().getChunkSource().getChunkDebugData(new ChunkPos(pos)));
        f.activate(); BigDecimal shield = f.shield();
        // Temporary failure-only observer: preserve every native tick and the original assertions.
        // Fixed 17-arrow loop bounds this trace; the extra cap also bounds unexpected impact callbacks.
        class SaturationTrace {
            final List<String> lines = new ArrayList<>();
            int round = -1, dropped;
            void add(String line) { if (lines.size() < 512) lines.add(line); else dropped++; }
            String state(Arrow arrow) {
                var level = h.getLevel(); var pos = arrow.blockPosition();
                Entity owner = ((NativeScatterArrowOwnerAccess) arrow).academy$scatterCachedOwner();
                return "id=" + arrow.getId() + ",uuid=" + arrow.getUUID() + ",tick=" + arrow.tickCount
                        + ",pos=" + arrow.position() + ",velocity=" + arrow.getDeltaMovement()
                        + ",level=" + (arrow.level() == level)
                        + ",nativeId=" + (level.getEntity(arrow.getId()) == arrow)
                        + ",nativeUuid=" + (level.getEntity(arrow.getUUID()) == arrow)
                        + ",chunk=" + (pos.getX() >> 4) + "/" + (pos.getZ() >> 4)
                        + ",entitiesLoaded=" + level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(pos))
                        + ",entityTicking=" + level.isPositionEntityTicking(pos)
                        + ",inGround=" + arrow.inGround + ",removed=" + arrow.isRemoved()
                        + ",removalReason=" + arrow.getRemovalReason()
                        + ",evidence=" + ScatterPlateArrowReceipts.hasEvidence(arrow)
                        + ",owner=" + (owner == null ? "null" : owner.getId() + "/" + owner.getUUID())
                        + ",ownerId=" + (owner != null && level.getEntity(owner.getId()) == owner)
                        + ",ownerUuid=" + (owner != null && level.getEntity(owner.getUUID()) == owner);
            }
            @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
            public void impact(ProjectileImpactEvent event) {
                if (!(event.getProjectile() instanceof Arrow arrow) || !f.entities.contains(arrow)) return;
                var hit = event.getRayTraceResult();
                String target = hit instanceof net.minecraft.world.phys.EntityHitResult entity
                        ? "entity=" + entity.getEntity().getType() + "/" + entity.getEntity().getId() + "/" + entity.getEntity().getUUID()
                        : hit instanceof net.minecraft.world.phys.BlockHitResult block
                        ? "block=" + block.getBlockPos() + ",face=" + block.getDirection() : "none";
                add("round=" + round + " impact type=" + hit.getType() + ",at=" + hit.getLocation()
                        + "," + target + ",canceledAtLowest=" + event.isCanceled()
                        + ",count=" + ScatterPlateArrowReceipts.receiptCount(f.p) + "," + state(arrow));
            }
            void failure(Throwable failure) {
                var logger = com.mojang.logging.LogUtils.getLogger();
                logger.error("[NEW212 saturation diagnostic] failure={}, rows={}, dropped={}", failure.toString(), lines.size(), dropped);
                for (String line : lines) logger.error("[NEW212 saturation diagnostic] {}", line);
            }
        }
        SaturationTrace trace = new SaturationTrace(); f.hook(trace);
        trace.add("start serverTick=" + f.p.server.getTickCount() + ",gameTime=" + h.getLevel().getGameTime()
                + ",player=" + f.p.getUUID() + ",pos=" + f.p.position() + ",yaw=" + f.p.getYRot()
                + ",pitch=" + f.p.getXRot() + ",shield=" + shield);
        try {
        for (int i = 0; i < 17; i++) {
            trace.round = i;
            trace.add("round=" + i + " begin count=" + ScatterPlateArrowReceipts.receiptCount(f.p));
            for (Entity prior : new ArrayList<>(f.entities)) if (prior instanceof Arrow previous) {
                int count = ScatterPlateArrowReceipts.receiptCount(f.p); String beforeState = trace.state(previous);
                boolean skipped = previous.isRemoved();
                try { if (!skipped) previous.tick(); }
                finally { trace.add("round=" + i + " old skipped=" + skipped + ",count=" + count + "->"
                        + ScatterPlateArrowReceipts.receiptCount(f.p) + ",before={" + beforeState + "},after={" + trace.state(previous) + "}"); }
            }
            Arrow a = f.arrow(.8, 1, 1.2, new Vec3(0, 0, -3)); Vec3 before = a.position();
            int count = ScatterPlateArrowReceipts.receiptCount(f.p); String beforeState = trace.state(a);
            try { a.tick(); }
            finally { trace.add("round=" + i + " new count=" + count + "->" + ScatterPlateArrowReceipts.receiptCount(f.p)
                    + ",before={" + beforeState + "},after={" + trace.state(a) + "}"); }
            h.assertTrue(!a.isRemoved() && a.position().equals(before.add(0, 0, -3)), "overflow leaves exact native motion/disposition " + i);
        }
        trace.add("final count=" + ScatterPlateArrowReceipts.receiptCount(f.p) + ",shield=" + f.shield());
        h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 16, "controller admits at most16pending physical receipts");
        eq(h, f.shield(), shield, "all seventeen grazes cost no shield"); h.succeed();
        } catch (RuntimeException | Error failure) { trace.failure(failure); throw failure; }
            });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_arrow_removal_category_preset_and_plate_break_retire_receipts(GameTestHelper h) {
        Fixture[] fixtures = new Fixture[4];
        for (int actor = 0; actor < fixtures.length; actor++) fixtures[actor] = new Fixture(h, actor * 12);
        h.runAfterDelay(61, () -> {
        for (int mode = 0; mode < 4; mode++) {
            Fixture f = fixtures[mode]; f.activate(); Arrow a = f.arrow(0, 1, 1.2, new Vec3(0, 0, -.3)); a.tick();
            h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 1, "pending native receipt before lifecycle " + mode);
            switch (mode) {
                case 0 -> a.discard();
                case 1 -> AbilityStorage.get(f.p).selectCategory("electromaster");
                case 2 -> AbilityStorage.get(f.p).presets.edit(0, 0, "", id -> true);
                default -> { var state = AbilityStorage.get(f.p); String category = state.category; state.category = "electromaster"; try { h.assertTrue(HighPowerCombat.spendShield(f.p, f.shield()), "declared ordinary EM setup debit exhausts shared base"); } finally { state.category = category; } }
            }
            ScatterPlates.tick(f.p); ScatterPlateArrowReceipts.prune(f.p.server);
            h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 0, "lifecycle/break invalidates pending receipt " + mode);
        }
        h.succeed();
            });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_native_candidate_saturation_refuses_evidence_only(GameTestHelper h) {
        Fixture f = new Fixture(h, 0);
        h.runAfterDelay(61, () -> {
            f.activate(); BigDecimal shield = f.shield(), hp = f.hp();
            for (int i = 0; i < 65; i++) f.mob(f.p.position().add(1, 0, 1.5));
            Arrow a = f.arrow(0, 1, 3, new Vec3(0, 0, -3)); a.tick();
            eq(h, f.shield(), shield, "saturated native broadphase refuses incomplete contact proof");
            h.assertTrue(f.hp().compareTo(hp) < 0 && a.isRemoved(), "native nearest player still receives real arrow impact");
            h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 0, "saturation never admits partial receipt set"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 95)
    public static void scatter_renewal_samples_current_q_once_per_real_paid_boundary(GameTestHelper h) {
        Fixture f = new Fixture(h, 0);
        h.runAfterDelay(61, () -> {
            f.activate(); BigDecimal initialQ = HighPowerGrowth.values(f.p).position(); int[] renewals = {0};
            h.runAfterDelay(10, () -> { AbilityStorage.get(f.p).addExperience(DualPoint.ID, .1); h.assertTrue(HighPowerGrowth.values(f.p).position().compareTo(initialQ) > 0, "real native XP advances effective q during paid window"); });
            f.hook(new Object() { @SubscribeEvent(priority = EventPriority.HIGHEST) public void pre(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre e) {
                if (e.getServer() != f.p.server || !ScatterPlates.renewalPending(f.p)) return;
                renewals[0]++; double expected = cn.academy.port.highpower.core.ScatterPlateGeometry.sustainCost(HighPowerGrowth.values(f.p).position()), before = f.cp();
                h.assertTrue(expected < 1, "renewal uses advanced current-q sustain cost");
                ScatterPlates.tick(f.p); near(h, f.cp(), before - expected, "one closed current-q upkeep debit");
                double paid = f.cp(); AbilityStorage.get(f.p).addExperience(DualPoint.ID, .1); ScatterPlates.tick(f.p); near(h, f.cp(), paid, "same-tick q advancement cannot cause a duplicate debit");
            } });
            h.runAfterDelay(24, () -> { h.assertTrue(renewals[0] == 1 && ScatterPlates.active(f.p), "exactly one real20tick renewal stays active"); h.succeed(); });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_observation_never_rebinds_callbacks_or_writes_player_data(GameTestHelper h) {
        Fixture f = new Fixture(h, 0);
        h.runAfterDelay(61, () -> {
            f.activate(); var state = AbilityStorage.get(f.p); var saved = f.p.getPersistentData().copy();
            String[] names = {"calculation", "consumptionValidity", "activationChanged", "categoryChanged", "beforeEarnedMutation", "earnedExperience", "skillExpChanged", "skillExpAdded"};
            Map<String, Object> before = new HashMap<>();
            try {
                for (String name : names) { var field = state.getClass().getDeclaredField(name); field.setAccessible(true); before.put(name, field.get(state)); }
                f.arrow(.8, 1, 1.2, new Vec3(0, 0, -.3)).tick();
                h.assertTrue(ScatterPlateArrowReceipts.receiptCount(f.p) == 1, "observer performed a real outer-plate crossing");
                for (String name : names) { var field = state.getClass().getDeclaredField(name); field.setAccessible(true); h.assertTrue(field.get(state) == before.get(name), "observer getter never rebinds " + name); }
                h.assertTrue(f.p.getPersistentData().equals(saved), "observation does not mutate any player persistent data");
            } catch (ReflectiveOperationException invalid) { throw new AssertionError(invalid); }
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_native_temporary_hp_zero_does_not_bypass_captured_plate(GameTestHelper h) {
        Fixture f = new Fixture(h, 0);
        h.runAfterDelay(61, () -> {
            f.activate(); BigDecimal shield = f.shield(), hp = f.hp(); double xp = AbilityStorage.get(f.p).exp(ScatterPlates.ID); boolean[] nativeZero = {false};
            f.hook(new Object() { @SubscribeEvent(priority = EventPriority.HIGHEST) public void post(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e) {
                if (e.getEntity() == f.p) nativeZero[0] = f.p.getHealth() == 0;
            } });
            Arrow actual = f.arrow(0, 1, 3, new Vec3(0, 0, -12)); actual.tick();
            h.assertTrue(nativeZero[0], "real native24damage temporarily exhausts normalized vanilla health before adapter Post");
            eq(h, f.hp(), hp, "authenticated contacted share prevents raw exact HP loss despite temporary native zero");
            eq(h, f.shield(), shield.subtract(BigDecimal.valueOf(24)), "single authenticated plate absorbs native24damage");
            near(h, AbilityStorage.get(f.p).exp(ScatterPlates.ID), xp + .005, "productive native-zero contact earns its one bounded award");
            h.assertTrue(actual.isRemoved() && f.p.isAlive(), "native successful-hit arrow disposition and live defender retained"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void scatter_after_claim_incoming_and_pre_source_mutations_revoke_only_absorption(GameTestHelper h) {
        Fixture[] fixtures = new Fixture[10];
        for (int actor = 0; actor < fixtures.length; actor++) fixtures[actor] = new Fixture(h, actor * 12);
        h.runAfterDelay(61, () -> {
            for (int mode = 0; mode < fixtures.length; mode++) {
                Fixture f = fixtures[mode]; f.activate(); BigDecimal shield = f.shield(), hp = f.hp(); double xp = AbilityStorage.get(f.p).exp(ScatterPlates.ID);
                Arrow arrow = f.arrowWithWeapon(0, 1, 3, new Vec3(0, 0, -3), new ItemStack(Items.BOW)); final int mutation = mode % 5; final boolean pre = mode >= 5; boolean[] changed = {false};
                Runnable change = () -> {
                    if (changed[0]) return; changed[0] = true;
                    switch (mutation) {
                        case 0 -> arrow.setOwner(f.mob(f.p.position().add(5, 0, -5)));
                        case 1 -> arrow.setBaseDamage(3);
                        case 2 -> arrow.getWeaponItem().setCount(2);
                        case 3 -> arrow.setCritArrow(true);
                        default -> arrow.getPersistentData().putLong("academy:ground_step_arrow_until", f.p.server.overworld().getGameTime() + 20);
                    }
                };
                f.hook(new Object() {
                    @SubscribeEvent(priority = EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) { if (!pre && e.getEntity() == f.p && e.getSource().getDirectEntity() == arrow) change.run(); }
                    @SubscribeEvent(priority = EventPriority.NORMAL) public void before(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre e) { if (pre && e.getEntity() == f.p && e.getSource().getDirectEntity() == arrow) change.run(); }
                });
                arrow.tick();
                h.assertTrue(changed[0], "real callback mutated source after crossing claim " + mode);
                eq(h, f.shield(), shield, "late source mutation invalidates already claimed allowance " + mode);
                eq(h, f.hp(), hp.subtract(BigDecimal.valueOf(6)), "legitimate native6damage still proceeds after refused allowance " + mode);
                near(h, AbilityStorage.get(f.p).exp(ScatterPlates.ID), xp, "refused late source grants no mastery " + mode);
                h.assertTrue(arrow.isRemoved(), "ordinary native successful-hit disposition preserved " + mode);
            }
            h.succeed();
        });
    }

    private static void eq(GameTestHelper h, BigDecimal a, BigDecimal b, String why) { h.assertTrue(a.compareTo(b) == 0, why + ": " + a + " / " + b); }
    private static void near(GameTestHelper h, double a, double b, String why) { h.assertTrue(Math.abs(a - b) < 1e-7, why + ": " + a + " / " + b); }
    /** Real classic key coordinator and network snapshots; no Minecraft client or renderer is required. */
    private static final class PlateClientBinding implements cn.academy.port.client.ClassicClientInputBinding.Host {
        final Fixture f;
        final cn.academy.port.client.ClassicClientInputBinding binding;
        final cn.academy.port.client.ScatterPlateInputState inputs = new cn.academy.port.client.ScatterPlateInputState();
        cn.academy.port.core.AbilityProgress state;
        boolean held, accepted; long nextInput; int aborts, releases;
        PlateClientBinding(Fixture f) { this.f = f; snapshot(); binding = new cn.academy.port.client.ClassicClientInputBinding(this); binding.runtime().updateDefaultGroup(); }
        void snapshot() { state = AbilityStorage.decode(AbilityStorage.encode(AbilityStorage.get(f.p))); }
        void tick(boolean down) { held = down; state.tickCooldowns(); binding.tickStart(); }
        public boolean available() { return true; }
        public boolean inGame() { return f.p.isAlive() && !f.p.isRemoved() && !f.p.isSpectator(); }
        public boolean categoryActivated() { return state.isActivated(); }
        public boolean canUseAbility() { return state.activated && state.overloadFine && !state.interfering && !state.consumptionInProgress(); }
        public boolean terminalOpen() { return false; }
        public int actorId() { return f.p.getId(); }
        public long ownerEpoch() { return f.epoch(); }
        public boolean physicalDown(int key) { return held; }
        public boolean registeredMapping(String skill) { return cn.academy.port.preset.PresetSkills.registeredMapping(state, skill); }
        public boolean eligible(cn.academy.port.client.ClassicClientInputBinding.Node node) { return inGame() && state.canUse(node.skill) && cn.academy.port.preset.PresetSkills.mappedUsable(state, node.skill) && cn.academy.port.SkillCatalog.enabled(state.category, node.skill); }
        public String mappedSkill(int slot) { return state.presets.currentSkill(slot); }
        public int mappedKey(int slot) { return 33; }
        public int slotCount() { return 1; }
        public void rawActivation(boolean desired) { throw new AssertionError("No activation edge expected"); }
        public void replaceOverrides(int[] keys) {}
        public long down(cn.academy.port.client.ClassicClientInputBinding.Node node) {
            var input = new HighPowerInput.Input(node.slot, ++nextInput, node.actorId(), node.ownerEpoch()); inputs.tap(input);
            accepted = cn.academy.port.AcademyGameplay.requestFromClient(f.p, new cn.academy.port.AcademyNetwork.Request("hp_scatter_plates_toggle", input.wire())); snapshot(); return input.input();
        }
        public void up(cn.academy.port.client.ClassicClientInputBinding.Node node, long input) { releases++; inputs.release(new HighPowerInput.Input(node.slot, input, node.actorId(), node.ownerEpoch())); }
        public void abort(cn.academy.port.client.ClassicClientInputBinding.Node node, long input) {
            aborts++; var cancelled = inputs.cancel(new HighPowerInput.Input(node.slot, input, node.actorId(), node.ownerEpoch()));
            if (cancelled != null) { cn.academy.port.AcademyGameplay.requestFromClient(f.p, new cn.academy.port.AcademyNetwork.Request("hp_scatter_plates_abort", cancelled.wire())); snapshot(); }
        }
        public boolean cancelPending(cn.academy.port.client.ClassicClientInputBinding.Node node, long input) { return false; }
        public void direction(cn.academy.port.client.ClassicClientInputBinding.ContextIdentity context, int direction, cn.academy.port.client.ClassicClientInputBinding.Edge edge, boolean silent) {}
        public void localContextEnd(cn.academy.port.client.ClassicClientInputBinding.ContextIdentity context) {}
        public void contextAbort(cn.academy.port.core.TargetedContextTermination.Request request) {}
        public void modernCleanup(Collection<cn.academy.port.client.ClassicClientInputBinding.Node> defaults) {}
    }
    private static final class Fixture {
        final GameTestHelper h; final ServerPlayer p; final Zombie shooter; final EmbeddedChannel channel; final double offset; final Vec3 fixedAt;
        final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>(); final List<BlockPos> blocks = new ArrayList<>(); boolean closed;
        final UUID chunkTicket = UUID.randomUUID(); final Set<ChunkPos> held = new LinkedHashSet<>();
        Fixture(GameTestHelper h, double offset) { this(h, offset, null); }
        Fixture(GameTestHelper h, double offset, Vec3 fixedAt) {
            this.h = h; this.offset = offset; this.fixedAt = fixedAt; var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "SP" + UUID.randomUUID().toString().substring(0, 10)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); p.getAbilities().instabuild = false; place(); h.assertTrue(HighPowerCombat.enroll(p, 1), "reviewed connected defender");
            var state = AbilityStorage.get(p); state.selectCategory("meltdowner"); state.setLevel(1); state.learn(DualPoint.ID); state.learn(ScatterPlates.ID); state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, ScatterPlates.ID, id -> true), "bind learned212"); HighPowerResources.initializeBudget(p); ScatterPlateStorage.ensure(p); epoch(); shooter = mob(p.position().add(5, 0, 5));
            h.testInfo.addListener(new GameTestListener() { public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); } public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); } });
            h.onEachTick(() -> { if (!closed) p.doTick(); });
        }
        void place() { Vec3 at = fixedAt == null ? h.absoluteVec(new Vec3(8.5, 18 + offset, 8.5)) : fixedAt; p.moveTo(at.x, at.y, at.z, 0, 0); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        void hold(ChunkPos chunk) {
            if (held.add(chunk)) {
                h.assertTrue(held.size() <= 4, "bounded native saturation fixture holds at most four trajectory chunks");
                h.getLevel().getChunkSource().addRegionTicket(SATURATION_TICKET, chunk, 2, chunkTicket);
                h.getLevel().getChunk(chunk.x, chunk.z);
            }
        }
        void prepareHeldChunks() {
            for (ChunkPos center : held) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                h.assertTrue(h.getLevel().getChunkSource().getChunk(center.x + x, center.z + z,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null,
                        "launch ticket propagated before demanding its FULL dependencies: " + (center.x + x) + "/" + (center.z + z));
            }
        }
        void prepareLaunchChunks(ChunkPos center) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                // These are ordinary bounded FULL reads (temporary vanilla UNKNOWN
                // tickets), not additional persistent fixture tickets. The unloaded
                // destination is three chunks north and is never queried here.
                h.getLevel().getChunk(center.x + x, center.z + z);
            }
        }
        List<BlockPos> holdSaturationPath() {
            List<BlockPos> path = new ArrayList<>(18);
            Vec3 pos = p.position().add(.8, 1, 1.2), velocity = new Vec3(0, 0, -3);
            for (int step = 0; step <= 17; step++) {
                BlockPos at = BlockPos.containing(pos); path.add(at); hold(new ChunkPos(at));
                // Pinned AbstractArrow air step: position first, then 0.99F drag and 0.05 gravity.
                pos = pos.add(velocity); velocity = velocity.scale((double).99F).add(0, -.05, 0);
            }
            return List.copyOf(path);
        }
        Zombie mob(Vec3 at) { Zombie mob = EntityType.HUSK.create(h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true); mob.setSilent(true); mob.getAttribute(Attributes.ARMOR).setBaseValue(0); mob.setPos(at); h.getLevel().addFreshEntity(mob); entities.add(mob); return mob; }
        Arrow arrow(double x, double y, double z, Vec3 velocity) { return arrowWithWeapon(x, y, z, velocity, null); }
        Arrow arrowWithWeapon(double x, double y, double z, Vec3 velocity, ItemStack weapon) { Arrow arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), weapon); arrow.setPos(p.position().add(x, y, z)); arrow.setDeltaMovement(velocity); h.getLevel().addFreshEntity(arrow); entities.add(arrow); return arrow; }
        void activate() { h.assertTrue(ScatterPlates.toggle(p, wire(1)), "paid live controller activated"); }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(long input) { return new HighPowerInput.Input(0, input, p.getId(), epoch()).wire(); }
        BigDecimal shield() { return HighPowerCombat.shield(p); } BigDecimal hp() { return new BigDecimal(HighPowerCombat.healthText(p)); } double cp() { return HighPowerResources.cp(p); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); blocks.forEach(pos -> h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())); ScatterPlates.remove(p); AbilityStorage.remove(p); h.getLevel().getServer().getPlayerList().remove(p); entities.forEach(Entity::discard); held.forEach(chunk -> h.getLevel().getChunkSource().removeRegionTicket(SATURATION_TICKET, chunk, 2, chunkTicket)); held.clear(); channel.finishAndReleaseAll(); }
    }
}
