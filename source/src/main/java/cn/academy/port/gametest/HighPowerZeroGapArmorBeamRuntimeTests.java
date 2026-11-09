package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry;
import cn.academy.port.highpower.core.DisintegrationSweepGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static cn.academy.port.gametest.ZeroGapArmorNativeFixture.*;

/** Only genuine paid211/221/231 entrypoints supply beam permits. Five-point CalcEvent amounts
 * are declared native fixtures, preserving all source, body, mitigation and callback validation.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerZeroGapArmorBeamRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_zero_gap_beams";
    private static final BigDecimal FIVE = new BigDecimal("5");

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void genuine_211_hand_origin_spends_one_plate_at_one_to_one_and_partial_residual_goes_directly_to_hp(GameTestHelper h) {
        var full = new Pair(h, 0, DualPoint.ID); var partial = new Pair(h, 28, DualPoint.ID);
        ready(h, () -> {
            for (var pair : List.of(full, partial)) {
                var f = pair.defender; f.seed(copies("100"), copies("100")); pair.front();
                if (pair == partial) f.seed(copies("2"), copies("1"));
                var before = f.armor(); BigDecimal health = f.hp(); double mastery = f.mastery(); Vec3 origin = DualPoint.hand(pair.caster.p, 0);
                h.assertTrue(pair.dual(1), "real authenticated211 cast reaches the live defender");
                pair.assertSource(origin);
                eq(h, before.total().subtract(f.armor().total()), pair == full ? FIVE : new BigDecimal("3"), "one actual contacted plate pays1:1");
                eq(h, f.hp(), pair == full ? health : health.subtract(new BigDecimal("2")), "partial remainder reaches HP without another MD layer");
                long changed = java.util.stream.IntStream.range(0, 6).filter(i -> !before.plate(i).equals(f.armor().plate(i))).count();
                h.assertTrue(changed == 1, "real hand beam changes exactly one numbered child");
                h.assertTrue(f.mastery() > mastery, "positive committed native absorption earns productive252mastery");
                if (pair == partial) h.assertTrue(f.timing().breakTicks() == 4, "true211 child exhaustion opens global debt");
            }
            h.succeed();
        }, full.defender, partial.defender);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void genuine_211_from_physical_gap_or_rear_keeps_ordinary_native_body_damage(GameTestHelper h) {
        var gap = new Pair(h, 0, DualPoint.ID); var rear = new Pair(h, 28, DualPoint.ID);
        ready(h, () -> {
            for (var pair : List.of(gap, rear)) {
                var f = pair.defender; var plate = f.front();
                double angle = ZeroGapArmorGeometry.plateYawDegrees(f.timing().phaseTick(), plate.id()) + (pair == gap ? 30 : 180);
                Vec3 direction = new Vec3(-Math.sin(Math.toRadians(angle)), 0, Math.cos(Math.toRadians(angle)));
                pair.placeCaster(f.p.position().add(direction.scale(8))); pair.aim();
                BigDecimal shield = f.shield(), health = f.hp(); var before = f.armor().toDecimalMap(); Vec3 origin = DualPoint.hand(pair.caster.p, 0);
                h.assertTrue(pair.dual(1), "real211 reaches the native body through gap or rear"); pair.assertSource(origin);
                eq(h, f.hp(), health.subtract(FIVE), "missed finite plate leaves all five native damage"); eq(h, f.shield(), shield, "physical miss cannot debit shared base");
                h.assertTrue(f.armor().toDecimalMap().equals(before), "missed geometry does not select a replacement child");
            }
            h.succeed();
        }, gap.defender, rear.defender);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=110)
    public static void actual_handheld_shield_direction_and_native_armor_precede_genuine_211_armor_debit(GameTestHelper h) {
        var shield = new Pair(h, 0, DualPoint.ID); var armored = new Pair(h, 28, DualPoint.ID);
        ready(h, () -> {
            shield.defender.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); shield.defender.p.startUsingItem(InteractionHand.OFF_HAND);
            armor(h, armored.defender.p); armored.defender.p.doTick();
            h.runAfterDelay(7, () -> {
                for (var pair : List.of(shield, armored)) {
                    pair.front(); var f = pair.defender; BigDecimal before = f.shield(), health = f.hp();
                    if (pair == shield) {
                        Vec3 face = pair.caster.p.position().subtract(f.p.position()); f.p.setYRot((float)Math.toDegrees(Math.atan2(-face.x, face.z)));
                        h.assertTrue(f.p.isBlocking(), "real native shield raise delay completed");
                    }
                    Vec3 origin = DualPoint.hand(pair.caster.p, 0); h.assertTrue(pair.dual(1), "genuine211 runs the native defensive pipeline");
                    eq(h, f.hp(), health, "native shield or remaining252armor protects exact HP");
                    if (pair == shield) {
                        h.assertTrue(pair.observation.blocked && pair.observation.blockedDamage > 0, "actual current hand origin reaches the native facing shield");
                        eq(h, f.shield(), before, "native shield block consumes no252balance");
                    } else {
                        pair.assertSource(origin);
                        h.assertTrue(pair.observation.armor > 0 && pair.observation.effects > 0 && pair.observation.enchantments > 0 && pair.observation.absorption == .5F,
                                "native armor, resistance, enchantment and absorption execute once");
                        BigDecimal spent = before.subtract(f.shield());
                        h.assertTrue(spent.signum() > 0 && spent.compareTo(FIVE) < 0, "only post-native mitigation remainder is charged1:1");
                        h.assertTrue(Math.abs(spent.doubleValue() - pair.observation.damage) < .00001,
                                "exact debit agrees with the float-native final remainder within its native conversion precision");
                    }
                }
                h.succeed();
            });
        }, shield.defender, armored.defender);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void real_211_incoming_cancel_turn_and_movement_revoke_capture_before_any_debit(GameTestHelper h) {
        var pairs = List.of(new Pair(h, 0, DualPoint.ID), new Pair(h, 28, DualPoint.ID), new Pair(h, 56, DualPoint.ID));
        ready(h, () -> {
            for (int i = 0; i < pairs.size(); i++) {
                var pair = pairs.get(i); pair.front(); final int mode = i; boolean[] called = {false};
                pair.defender.hook(new Object() { @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent event) {
                    if (event.getEntity() != pair.defender.p || event.getSource().getEntity() != pair.caster.p) return;
                    called[0] = true;
                    if (mode == 0) event.setCanceled(true);
                    else if (mode == 1) pair.defender.p.setYRot(pair.defender.p.getYRot() + 180);
                    else { pair.defender.p.setPos(pair.defender.p.position().add(.1, 0, 0)); pair.defender.p.connection.resetPosition(); }
                }});
                BigDecimal before = pair.defender.shield(), health = pair.defender.hp(); double xp = pair.defender.mastery();
                h.assertTrue(pair.dual(1), "paid211 enters actual native callback"); h.assertTrue(called[0], "native callback really changed/canceled the target");
                eq(h, pair.defender.shield(), before, "capture is rechecked after mutable native callback " + mode);
                eq(h, pair.defender.hp(), mode == 0 ? health : health.subtract(FIVE), "ordinary native cancellation/body damage survives revocation " + mode);
                near(h, pair.defender.mastery(), xp, "revoked capture cannot reward");
            }
            h.succeed();
        }, pairs.stream().map(p -> p.defender).toArray(ZeroGapArmorNativeFixture[]::new));
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=110)
    public static void genuine_221_fixed_reflection_uses_last_real_segment_origin_for_finite_252_contact(GameTestHelper h) {
        var pair = new Pair(h, 0, FoldedMirrorArray.ID);
        ready(h, () -> {
            var f = pair.defender;
            pair.placeCaster(f.p.position().add(-1.5, 0, -4)); pair.caster.p.setYRot(0); pair.caster.p.setXRot(0); pair.caster.p.setShiftKeyDown(true);
            String place = pair.caster.wire(0, 1);
            h.assertTrue(FoldedMirrorArray.start(pair.caster.p, place), "real crouch press pins a physical mirror center");
            pair.aim(); pair.caster.p.setShiftKeyDown(true);
            h.assertTrue(pair.pvp(() -> FoldedMirrorArray.release(pair.caster.p, place)), "real crouch release fixes mirror toward the body");
            h.assertTrue(FoldedMirrorArray.plates(pair.caster.p).size() == 1, "one actual fixed mirror exists");
            Vec3 mirror = FoldedMirrorArray.plates(pair.caster.p).getFirst().center();
            h.assertTrue(mirror.z > f.p.getZ() && pair.caster.p.getZ() < f.p.getZ(), "reflected origin lies opposite the original caster");
            pair.caster.p.setShiftKeyDown(false);
            int placedAt = pair.caster.p.server.getTickCount(), phase = f.timing().phaseTick();
            h.runAfterDelay(1, () -> {
                h.assertTrue(pair.caster.p.server.getTickCount() == placedAt + 1 && f.timing().phaseTick() == (phase + 1) % 120,
                        "one genuine physical boundary elapses after actual221placement");
                h.assertTrue(AbilityStorage.get(pair.caster.p).cooldowns.getOrDefault(FoldedMirrorArray.ID, 0) == 0,
                        "the genuine native player stage clears its one-tick placement cooldown before fire");
                BigDecimal shield = f.shield(), health = f.hp(); String fire = pair.caster.wire(0, 2);
                h.assertTrue(pair.pvp(() -> FoldedMirrorArray.start(pair.caster.p, fire)), "genuine221fresh press starts after native placement cooldown");
                h.assertTrue(pair.pvp(() -> FoldedMirrorArray.release(pair.caster.p, fire)), "genuine paid221 fires through fixed mirror");
                var receipt = FoldedMirrorArray.receipt(pair.caster.p);
                h.assertTrue(receipt != null && receipt.crossings() == 1 && receipt.path().size() == 2 && receipt.path().getLast().start().equals(mirror), "native221receipt certifies one reflection and exact last-leg origin");
                pair.assertSource(mirror); eq(h, f.shield(), shield.subtract(FIVE), "reflected final native remainder pays252at1:1"); eq(h, f.hp(), health, "real reflected front contact protects HP"); h.succeed();
            });
        }, pair.defender);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=120)
    public static void genuine_231_charge_and_sweep_use_current_real_hand_origin_and_current_rotating_plate(GameTestHelper h) {
        sweepAt(h, List.of(), -1);
    }

    @GameTest(template=TEMPLATE, batch="highpower_zero_gap_sweep_fixed", timeoutTicks=140)
    public static void genuine_231_retains_the_previously_failed_large_world_coordinate(GameTestHelper h) {
        // Exact defender feet from the failed structure origin (13632664,-60,262601).
        sweepAt(h, List.of(new Vec3(13632672.5, -42, 262609.5)), -1);
    }

    @GameTest(template=TEMPLATE, batch="highpower_zero_gap_sweep_translations", timeoutTicks=140)
    public static void genuine_231_origin_border_translations_and_intervening_wall_keep_native_contract(GameTestHelper h) {
        sweepAt(h, List.of(new Vec3(8.5, 18, 8.5), new Vec3(-29998991.5, -42, -29998991.5),
                new Vec3(29999008.5, -42, 29999008.5), new Vec3(13632800.5, -42, 262609.5)), 3);
    }

    private static void sweepAt(GameTestHelper h, List<Vec3> fixedFeet, int blockedIndex) {
        int count = fixedFeet.isEmpty() ? 1 : fixedFeet.size();
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var pair = new Pair(h, i * 28, DisintegrationSweep.ID); pairs.add(pair);
            if (!fixedFeet.isEmpty()) {
                Vec3 feet = fixedFeet.get(i);
                h.getLevel().getChunk(BlockPos.containing(feet));
                pair.defender.p.setPos(feet); pair.defender.p.setDeltaMovement(Vec3.ZERO); pair.defender.p.connection.resetPosition();
                // Residency must cover the original61 real ticks too: native ServerPlayer.tick,
                // not the doTick pump, decrements the native login protection counter.
                pair.placeCaster(feet.add(0, 0, 8)); pair.aim(); pair.holdSweepChunks();
            }
        }
        pairs.getFirst().acquirePvp(); // One owner restores the single server setting after all cases finish.
        ready(h, () -> {
            List<SweepDiagnostic> diagnostics = new ArrayList<>(); List<String> inputs = new ArrayList<>();
            for (int i = 0; i < pairs.size(); i++) {
                var pair = pairs.get(i); var f = pair.defender;
                pair.placeCaster(f.p.position().add(0, 0, 8)); pair.aim(); pair.caster.p.setShiftKeyDown(false);
                if (i == blockedIndex) for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) f.wall(f.p.position().add(x, y, 4));
                var diagnostic = new SweepDiagnostic(pair); diagnostics.add(diagnostic); pair.caster.hook(diagnostic);
                if (!fixedFeet.isEmpty()) {
                    diagnostic.traceSample("setup-held fixture tickets");
                }
                int spawnProtection = nativeSpawnProtection(f.p);
                h.assertTrue(spawnProtection <= 0, "genuine native entity ticks expire login protection before charge; remaining=" + spawnProtection);
                String input = pair.caster.wire(0, 1); inputs.add(input);
                h.assertTrue(DisintegrationSweep.start(pair.caster.p, input), "real231combat charge starts " + i); diagnostic.sample("started");
            }
            h.runAfterDelay(8, () -> {
                List<BigDecimal> shields = new ArrayList<>(), health = new ArrayList<>(); List<Vec3> hands = new ArrayList<>();
                for (int i = 0; i < pairs.size(); i++) {
                    var pair = pairs.get(i);
                    h.assertTrue(DisintegrationSweep.release(pair.caster.p, inputs.get(i)), "eight real charged ticks release the genuine sweep " + i);
                    diagnostics.get(i).sample("released"); shields.add(pair.defender.shield()); health.add(pair.defender.hp()); hands.add(DualPoint.hand(pair.caster.p, 0));
                }
                h.runAfterDelay(11, () -> {
                    for (int i = 0; i < pairs.size(); i++) {
                        var pair = pairs.get(i); var f = pair.defender;
                        h.assertTrue(pair.observation.posts == (i == blockedIndex ? 0 : 1),
                                "one actually swept body reaches the native damage pipeline unless a real nearer wall blocks it; case=" + i + "; " + diagnostics.get(i).report());
                        if (i != blockedIndex) pair.assertSource(hands.get(i));
                        eq(h, f.shield(), i == blockedIndex ? shields.get(i) : shields.get(i).subtract(FIVE),
                                "current finite252plate pays one genuine231remainder at1:1, and a nearer native wall prevents all spending " + i);
                        eq(h, f.hp(), health.get(i), "actual source geometry and native wall retain exact HP " + i);
                    }
                    h.succeed();
                });
            });
        }, pairs.stream().map(p -> p.defender).toArray(ZeroGapArmorNativeFixture[]::new));
    }

    private static int nativeSpawnProtection(ServerPlayer player) {
        try {
            var field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime"); field.setAccessible(true);
            return field.getInt(player); // Read only: native tick may decrement the expired counter below zero.
        } catch (ReflectiveOperationException invalid) { throw new AssertionError("Missing native login-protection witness", invalid); }
    }

    /** Bounded read-only failure evidence. This observer never invokes a controller validator,
     * changes PvP/geometry/timing, creates permits, or calls any damage entrypoint.
     */
    public static final class SweepDiagnostic {
        final Pair pair; final List<String> samples = new ArrayList<>(); final int started;
        int skills, incoming, pre;
        SweepDiagnostic(Pair pair) { this.pair = pair; started = pair.caster.p.server.getTickCount(); }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void tick(ServerTickEvent.Post event) {
            if (event.getServer() == pair.caster.p.server) sample("post");
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void skill(SkillAttackEvent event) {
            if (event.player == pair.caster.p && event.skill.equals("meltdowner.disintegration_sweep")) {
                skills++; if (samples.size() < 24) samples.add("skillTarget=" + (event.target == null ? -1 : event.target.getId()) + ",wanted=" + (event.target == pair.defender.p) + ",amount=" + event.amount);
            }
        }
        @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true) public void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() == pair.defender.p && event.getSource().getEntity() == pair.caster.p) incoming++;
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void pre(LivingDamageEvent.Pre event) {
            if (event.getEntity() == pair.defender.p && event.getSource().getEntity() == pair.caster.p) pre++;
        }
        void sample(String stage) {
            if (samples.size() >= 24) return;
            ServerPlayer caster = pair.caster.p, target = pair.defender.p;
            int elapsed = caster.server.getTickCount() - started;
            String gates = "t=" + elapsed + ",stage=" + stage + ",pvp=" + caster.server.isPvpAllowed()
                    + ",allowPlayers=" + AcademyConfig.ATTACK_PLAYERS.get() + ",targetAlive=" + target.isAlive()
                    + ",targetInv=" + target.isInvulnerable() + ",cd=" + AbilityStorage.get(caster).cooldowns.getOrDefault(DisintegrationSweep.ID, 0);
            try {
                var mapField = DisintegrationSweep.class.getDeclaredField("CONTEXTS"); mapField.setAccessible(true);
                Object context = ((Map<?, ?>)mapField.get(null)).get(caster.getUUID());
                if (context == null) { samples.add(gates + ",context=ended,posts=" + pair.observation.posts); return; }
                var targets = (List<?>)field(context, "targets"); List<String> ids = new ArrayList<>(); boolean acquired = false;
                for (Object slot : targets) {
                    Object entity = field(slot, "entity"); acquired |= entity == target;
                    if (ids.size() < 8) ids.add(field(slot, "id") + ":" + field(slot, "attempted"));
                }
                var receipt = DisintegrationSweep.receipt(caster);
                String receiptText = receipt == null ? "none" : receipt.attempts() + "/" + receipt.successes() + "/paid=" + receipt.paid()
                        + "/statuses=" + receipt.path().stream().map(p -> Integer.toString(p.status())).toList();
                samples.add(gates + ",released=" + field(context, "released") + ",acquisitionPrefixes=" + field(context, "acquisitionNativePrefixes")
                        + ",targets=" + ids + ",wantedAcquired=" + acquired + ",receipt=" + receiptText);
                if (stage.equals("released") && samples.size() < 24) {
                    var plane = (DisintegrationSweepGeometry.FrozenPlane)field(context, "plane"); Vec3 hand = DualPoint.hand(caster, 0); var box = target.getBoundingBox();
                    var bounds = new Bounds(new cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector(box.minX, box.minY, box.minZ),
                            new cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector(box.maxX, box.maxY, box.maxZ));
                    var contact = plane == null ? Optional.empty() : DisintegrationSweepGeometry.firstContact(plane, vector(hand), bounds,
                            -DisintegrationSweepGeometry.HALF_ANGLE, DisintegrationSweepGeometry.HALF_ANGLE);
                    var los = caster.serverLevel().clip(new ClipContext(hand, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                    samples.add("caster=" + caster.position() + ",target=" + target.position() + ",hand=" + hand + ",look=" + caster.getLookAngle()
                            + ",yaw=" + caster.getYRot() + ",pitch=" + caster.getXRot() + ",analyticContact=" + contact + ",bodyLOS=" + los.getType() + ":" + los.getLocation());
                    traceSample("release native trace");
                }
            } catch (ReflectiveOperationException invalid) { samples.add(gates + ",diagnosticReadFailure=" + invalid.getClass().getSimpleName()); }
        }
        String report() { return "skills=" + skills + ",incoming=" + incoming + ",pre=" + pre + ",posts=" + pair.observation.posts + "; " + String.join(" | ", samples); }
        void traceSample(String label) {
            if (samples.size() >= 24) return;
            ServerPlayer caster = pair.caster.p, target = pair.defender.p; Vec3 hand = DualPoint.hand(caster, 0); var box = target.getBoundingBox();
            var plane = DisintegrationSweepGeometry.freeze(vector(hand), vector(caster.getLookAngle()), Math.toRadians(caster.getYRot()), false).orElseThrow();
            var bounds = new Bounds(new cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector(box.minX, box.minY, box.minZ),
                    new cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector(box.maxX, box.maxY, box.maxZ));
            var contact = DisintegrationSweepGeometry.firstContact(plane, vector(hand), bounds, -DisintegrationSweepGeometry.HALF_ANGLE, DisintegrationSweepGeometry.HALF_ANGLE);
            if (contact.isEmpty()) { samples.add(label + ": no analytic contact"); return; }
            try {
                var contexts = DisintegrationSweep.class.getDeclaredField("CONTEXTS"); contexts.setAccessible(true);
                Object context = ((Map<?, ?>)contexts.get(null)).get(caster.getUUID());
                if (context != null && Boolean.TRUE.equals(field(context, "freezing"))) { samples.add(label + ": skipped during source acquisition"); return; }
                Object prefixes = context == null ? null : field(context, "acquisitionNativePrefixes");
                Object emitting = context == null ? null : field(context, "emitting"), digging = context == null ? null : field(context, "digging");
                var casterSaved = caster.getPersistentData().copy(); var targetSaved = target.getPersistentData().copy();
                var trace = DisintegrationSweep.class.getDeclaredMethod("nativeTrace", ServerPlayer.class, Vec3.class, Vec3.class); trace.setAccessible(true);
                var candidate = DisintegrationSweepGeometry.nativeInteriorCandidate(plane, vector(hand), bounds, contact.get(), DisintegrationSweepGeometry.HALF_ANGLE, 0);
                StringBuilder result = new StringBuilder(label);
                for (double angle : candidate.isPresent() ? new double[]{contact.get().angle(), candidate.getAsDouble()} : new double[]{contact.get().angle()}) {
                    var endpoint = DisintegrationSweepGeometry.rayEndpoint(plane, vector(hand), angle);
                    if (endpoint.isEmpty()) { result.append("; no endpoint"); continue; }
                    Vec3 end = vec(endpoint.get()); Object hit = trace.invoke(null, caster, hand, end);
                    result.append("; angle=").append(angle).append(",endLoaded=").append(caster.serverLevel().hasChunkAt(BlockPos.containing(end)))
                            .append(",permitted=").append(field(hit, "permitted")).append(",wantedTarget=").append(field(hit, "target") == target)
                            .append(",blocker=").append(field(hit, "blocker")).append(",point=").append(field(hit, "point"));
                }
                pair.h.assertTrue(caster.getPersistentData().equals(casterSaved) && target.getPersistentData().equals(targetSaved)
                                && (context == null || Objects.equals(prefixes, field(context, "acquisitionNativePrefixes"))
                                    && emitting == field(context, "emitting") && digging == field(context, "digging")),
                        "read-only native trace diagnostics change no acquisition counters, permits or persistent resources");
                samples.add(result.toString());
            } catch (ReflectiveOperationException invalid) { samples.add(label + ": diagnosticReadFailure=" + invalid.getClass().getSimpleName()); }
        }
        private static Object field(Object owner, String name) throws ReflectiveOperationException {
            var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
        }
    }

    private static final class Pair {
        final GameTestHelper h; final ZeroGapArmorNativeFixture defender, caster; final Observation observation;
        final List<ChunkPos> fixtureChunks = new ArrayList<>();
        boolean ownsPvp, priorPvp;
        Pair(GameTestHelper h, double offset, String skill) {
            this.h = h; defender = new ZeroGapArmorNativeFixture(h, offset); caster = new ZeroGapArmorNativeFixture(h, offset + 7);
            var s = AbilityStorage.get(caster.p); s.learn(FoldedMirrorArray.ID); s.learn(DisintegrationSweep.ID);
            // Clear the existing211slot before rebinding it to slot0 for one consistent native wire fixture.
            h.assertTrue(s.presets.edit(0, 3, "", ignored -> true) && s.presets.edit(0, 0, skill, s::learned), "bind genuine closed beam skill");
            observation = new Observation(defender.p); defender.hook(observation);
            caster.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void amount(SkillAttackEvent event) {
                if (event.player == caster.p && event.target == defender.p && event.skill.equals("meltdowner." + skill)) event.amount = 5;
            }});
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
            });
        }
        void front() { placeCaster(defender.p.position().add(vec(defender.front().normal()).scale(8))); aim(); }
        void placeCaster(Vec3 position) { caster.p.setPos(position); caster.p.setDeltaMovement(Vec3.ZERO); caster.p.connection.resetPosition(); }
        void aim() {
            Vec3 d = defender.p.getBoundingBox().getCenter().subtract(caster.p.getEyePosition());
            caster.p.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z))); caster.p.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))); caster.p.setShiftKeyDown(false);
        }
        boolean dual(long input) { return pvp(() -> DualPoint.tap(caster.p, caster.wire(0, input))); }
        boolean pvp(BooleanSupplier action) { boolean previous = caster.p.server.isPvpAllowed(); caster.p.server.setPvpAllowed(true); try { return action.getAsBoolean(); } finally { caster.p.server.setPvpAllowed(previous); } }
        void acquirePvp() { if (!ownsPvp) { ownsPvp = true; priorPvp = caster.p.server.isPvpAllowed(); caster.p.server.setPvpAllowed(true); } }
        void holdSweepChunks() {
            Vec3 hand = DualPoint.hand(caster.p, 0);
            var plane = DisintegrationSweepGeometry.freeze(vector(hand), vector(caster.p.getLookAngle()), Math.toRadians(caster.p.getYRot()), false).orElseThrow();
            var query = DisintegrationSweepGeometry.queryBounds(plane);
            int minX = ((int)Math.floor(query.min().x())) >> 4, maxX = ((int)Math.floor(query.max().x())) >> 4;
            int minZ = ((int)Math.floor(query.min().z())) >> 4, maxZ = ((int)Math.floor(query.max().z())) >> 4;
            h.assertTrue((maxX - minX + 1) * (maxZ - minZ + 1) <= 25, "fixture tickets cover only the bounded48m analytic fan");
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                var chunk = new ChunkPos(x, z);
                if (!h.getLevel().getForcedChunks().contains(chunk.toLong())) { h.getLevel().setChunkForced(x, z, true); fixtureChunks.add(chunk); }
                h.getLevel().getChunk(x, z);
            }
        }
        void assertSource(Vec3 origin) {
            DamageSource source = observation.source;
            h.assertTrue(observation.posts == 1 && source != null && source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() == caster.p
                    && source.getDirectEntity() == caster.p && origin.equals(source.getSourcePosition()), "one actual native PLAYER_ATTACK preserves exact real segment origin and caster attribution");
        }
        void close() {
            FoldedMirrorArray.remove(caster.p); DisintegrationSweep.remove(caster.p);
            fixtureChunks.forEach(chunk -> h.getLevel().setChunkForced(chunk.x, chunk.z, false)); fixtureChunks.clear();
            if (ownsPvp) { ownsPvp = false; caster.p.server.setPvpAllowed(priorPvp); }
        }
    }
    public static final class Observation {
        final ServerPlayer target; int posts; DamageSource source; boolean blocked; float blockedDamage, damage, armor, effects, enchantments, absorption;
        Observation(ServerPlayer target) { this.target = target; }
        @SubscribeEvent public void shield(LivingShieldBlockEvent event) { if (event.getEntity() == target) { blocked = event.getBlocked(); blockedDamage = event.getBlockedDamage(); } }
        @SubscribeEvent public void post(LivingDamageEvent.Post event) {
            if (event.getEntity() != target) return; posts++; source = event.getSource(); damage = event.getNewDamage();
            armor = event.getReduction(DamageContainer.Reduction.ARMOR); effects = event.getReduction(DamageContainer.Reduction.MOB_EFFECTS);
            enchantments = event.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = event.getReduction(DamageContainer.Reduction.ABSORPTION);
        }
    }
    private static void armor(GameTestHelper h, ServerPlayer player) {
        var chest = new ItemStack(Items.DIAMOND_CHESTPLATE); chest.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
        chest.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        player.setItemSlot(EquipmentSlot.CHEST, chest); player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0)); player.setAbsorptionAmount(.5F);
    }
}
