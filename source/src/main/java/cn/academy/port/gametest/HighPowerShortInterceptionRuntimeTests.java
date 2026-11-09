package cn.academy.port.gametest;

import cn.academy.port.highpower.core.ShortInterceptionRules;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
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
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NEW331 controller acceptance through authenticated native request/release edges.
 * Resident mocked-network players, learned flags, stationary native hostiles and a
 * bounded arena are explicit fixtures. Time-sensitive assertions use real server
 * Post callbacks. No test manufactures a combat permit or calls projectile contact.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerShortInterceptionRuntimeTests {
    public static final String TEMPLATE = "highpower_short_interception_empty";
    private static final String BATCH = "highpower_short_interception", ID = "short_interception";
    private static final String OWN = "academy:short_interception_fences";
    private static final TicketType<UUID> TICKET = TicketType.create("academy_short_interception_fixture", UUID::compareTo);
    private HighPowerShortInterceptionRuntimeTests() {}

    /** The 48m cases stay within a separate native structure, away from adjacent tests. */
    @SubscribeEvent public static void install(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(); ListTag size = new ListTag();
        size.add(IntTag.valueOf(32)); size.add(IntTag.valueOf(12)); size.add(IntTag.valueOf(72)); tag.put("size", size);
        ListTag blocks = new ListTag(); CompoundTag first = new CompoundTag(); ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); first.put("pos", pos); first.putInt("state", 0); blocks.add(first);
        tag.put("blocks", blocks); tag.put("entities", new ListTag()); ListTag palette = new ListTag(); CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void native_catalog_and_authenticated_release_make_one_free_target_mark(GameTestHelper h) {
        Fixture f = new Fixture(h); var skill = SkillCatalog.find(HighPowerCategories.TELEPORTER, ID).orElseThrow();
        h.assertTrue(skill.level() == 3 && skill.requirements().equals(List.of(new SkillCatalog.Requirement(CrossSubstitution.ID, 0))),
                "native LV3 entry requires learned cross substitution without an invented mastery threshold");
        Snapshot before = f.snapshot(); f.aim(f.target.getBoundingBox().getCenter());
        h.assertTrue(f.press(), "canonical authenticated press begins the gesture");
        h.assertFalse(ShortInterception.marked(f.p), "press never captures target before release");
        for (int i = 0; i < 128; i++) ShortInterception.tick(f.p);
        h.assertTrue(ShortInterception.heldTicks(f.p) == 0 && !ShortInterception.marked(f.p), "duplicate same-tick ticks do not recognize a hold or lock");
        h.assertFalse(ShortInterception.release(f.p, f.wire(f.inputs + 1)), "different physical input cannot release this gesture");
        h.assertTrue(f.release() && ShortInterception.marked(f.p), "matching short release records one real visible hostile");
        h.assertValueEqual(ShortInterception.remainingTicks(f.p), ShortInterceptionRules.MARK_TICKS, "first target mark freezes full mark lifetime");
        h.assertFalse(f.release() || ShortInterception.press(f.p, f.wire(f.inputs)), "duplicate release and replayed press are inert");
        f.same(before, "free target capture"); h.assertTrue(f.spawns == 0 && f.p.position().equals(f.source), "lock emits and moves nothing"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void held_input_waits_six_real_ticks_and_release_never_becomes_a_tap(GameTestHelper h) {
        Fixture f = new Fixture(h); f.aim(f.target.getBoundingBox().getCenter()); Snapshot before = f.snapshot(); h.assertTrue(f.press(), "native held key starts");
        for (int tick = 1; tick <= 5; tick++) { int expected = tick; f.post(tick, () -> {
            for (int i = 0; i < 64; i++) ShortInterception.tick(f.p);
            h.assertTrue(ShortInterception.heldTicks(f.p) == expected && !ShortInterception.marked(f.p), "real hold tick " + expected + " performs no target capture");
            f.same(before, "unrecognized held input");
        }); }
        f.post(6, () -> { h.assertFalse(ShortInterception.marked(f.p) || ShortInterception.active(f.p), "sixth real tick clears empty controller");
            f.release(); h.assertFalse(ShortInterception.marked(f.p), "release after hold clear never falls through to target capture"); f.same(before, "six-tick clear"); }); f.finish(7);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=515)
    public static void target_mark_expires_on_its_real_tick_boundary_without_idle_growth(GameTestHelper h) {
        Fixture f = new Fixture(h); f.lock(); double cp = f.cp();
        for (int i = 0; i < 512; i++) ShortInterception.tick(f.p);
        h.assertValueEqual(ShortInterception.remainingTicks(f.p), ShortInterceptionRules.MARK_TICKS, "manual same-tick calls cannot age mark");
        f.post(ShortInterceptionRules.MARK_TICKS - 1, () -> h.assertTrue(ShortInterception.marked(f.p) && ShortInterception.remainingTicks(f.p) == 1, "mark survives its final actual tick"));
        f.post(ShortInterceptionRules.MARK_TICKS, () -> { h.assertFalse(ShortInterception.marked(f.p) || ShortInterception.active(f.p), "the full number of actual ticks retire mark");
            near(h, f.cp(), cp, "expiry has no CP cost"); near(h, AbilityStorage.get(f.p).exp(ID), 0, "idle lock grants no mastery");
            h.assertTrue(f.spawns == 0 && f.needles() == 3, "idle mark never escrows a needle"); }); f.finish(ShortInterceptionRules.MARK_TICKS + 1);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void empty_second_release_without_real_offhand_medium_is_free(GameTestHelper h) {
        Fixture f = new Fixture(h); f.p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); f.ready(() -> {
            Snapshot before = f.snapshot(); h.assertFalse(f.tap(), "no qualifying arrow and no original offhand needle refuses");
            f.same(before, "empty second release"); h.assertTrue(f.spawns == 0, "empty second release cannot synthesize a projectile");
            h.assertFalse(f.release(), "empty terminal release cannot replay");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void same_native_arrow_is_relocated_without_owner_or_payload_rewrite(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); Vec3 source = arrow.position(); UUID uuid = arrow.getUUID(); int entityId = arrow.getId();
            CompoundTag payload = payload(arrow); double cp = f.cp(); int stock = f.needles();
            h.assertTrue(f.tap(), "second short release intercepts the real plain flying arrow: " + ShortInterception.receipt(f.p));
            h.assertTrue(h.getLevel().getEntity(entityId) == arrow && h.getLevel().getEntity(uuid) == arrow && !arrow.position().equals(source),
                    "actual same native entity moves, with stable runtime id and UUID");
            h.assertTrue(arrow.getOwner() == f.shooter && payload(arrow).equals(payload), "native original owner, base damage, pickup, item and weapon payload remain exact");
            h.assertTrue(f.needles() == stock && f.spawns == 0, "existing arrow path consumes no needle and creates no replacement");
            h.assertTrue(f.cp() < cp, "one real relocation pays its own CP"); near(h, AbilityStorage.get(f.p).exp(ID), 0, "relocation grants no mastery");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void bounded_release_snapshot_relocates_only_four_current_arrows_at_fixed_exits(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            List<Arrow> arrows = new ArrayList<>(); List<Vec3> initial = new ArrayList<>();
            for (int i = 0; i < 5; i++) { Arrow arrow = f.arrow(2 + .7 * i, 0); arrows.add(arrow); initial.add(arrow.position()); }
            Vec3 center = f.target.getBoundingBox().getCenter(), axis = center.subtract(f.p.getEyePosition()).normalize();
            Vec3 right = new Vec3(-axis.z, 0, axis.x).normalize(); double cp = f.cp();
            h.assertTrue(f.tap(), "five real arrows expose one bounded four-arrow snapshot");
            double[] offsets = {-.45, -.15, .15, .45};
            for (int i = 0; i < 4; i++) {
                Arrow arrow = arrows.get(i); Vec3 exit = center.subtract(axis.scale(2)).add(right.scale(offsets[i]));
                h.assertTrue(arrow.position().distanceTo(exit) < 1e-6, "snapshot slot " + i + " uses its fixed 2m-front exit");
                near(h, arrow.getDeltaMovement().length(), 1.5, "snapshot slot preserves native speed");
                h.assertTrue(arrow.getDeltaMovement().normalize().distanceTo(center.subtract(exit).normalize()) < 1e-6, "one initial direction faces the frozen target point");
            }
            h.assertTrue(arrows.get(4).position().equals(initial.get(4)), "fifth current arrow remains completely untouched");
            h.assertValueEqual(ShortInterception.receipt(f.p).admitted(), 4, "receipt observes four actual admissions");
            near(h, f.cp(), cp - 4 * 4.5, "LV3 pays four independent 4.5CP admissions");
            h.assertTrue(f.needles() == 3 && f.spawns == 0, "bounded existing-arrow snapshot never consumes fallback stock");
            Arrow late = f.arrow(2, 0); Vec3 lateStart = late.position(); ShortInterception.tick(f.p);
            h.assertTrue(late.position().equals(lateStart), "an arrow arriving after the release snapshot receives no queued admission");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void no_current_qualified_arrow_uses_one_real_offhand_fallback(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Arrow outside = f.arrow(3, 1.2); Vec3 start = outside.position(); double cp = f.cp();
            h.assertTrue(f.tap(), "empty narrow corridor permits one actual offhand needle");
            var receipt = ShortInterception.receipt(f.p);
            h.assertTrue(receipt.paid() && receipt.fallback() && receipt.admitted() == 1, "fallback is exactly one paid admission");
            h.assertTrue(f.spawns == 1 && f.needles() == 2 && outside.position().equals(start), "one real offhand original is consumed while off-corridor arrow stays untouched");
            near(h, f.cp(), cp - 4.5, "LV3 fallback pays one per-projectile cost");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void canceled_and_redirected_native_teleport_are_free_without_late_fallback(GameTestHelper h) { canceled(h, false); }
    private static void canceled(GameTestHelper h, boolean redirected) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); Vec3 start = arrow.position(), motion = arrow.getDeltaMovement(); Snapshot before = f.snapshot();
            TeleportHook hook = f.onTeleport(event -> { if (redirected) event.setTargetX(event.getTargetX() + .1); else event.setCanceled(true); });
            h.assertFalse(f.tap(), "native callback refuses the only snapshot arrow"); f.same(before, "native callback refusal");
            h.assertTrue(hook.calls == 1 && arrow.position().equals(start) && arrow.getDeltaMovement().equals(motion), "canceled/redirected arrow keeps exact native source and motion");
            h.assertTrue(f.spawns == 0 && f.needles() == 3 && !ShortInterception.receipt(f.p).fallback(), "nonempty snapshot cannot switch to offhand fallback after callback refusal");
            h.assertFalse(f.release() || ShortInterception.press(f.p, f.wire(f.inputs)), "consumed physical input cannot replay refused callback");
        }); h.runAfterDelay(6, () -> { f.verify(); f.close(); if (!redirected) canceled(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void callback_foreign_move_and_cp_edit_are_retained_without_rollback_or_own_charge(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); Vec3 foreign = arrow.position().add(.1, 0, 0); double cp = f.cp();
            f.onTeleport(event -> { arrow.setPos(foreign); f.budget().putDouble("cp", cp - 1); event.setCanceled(true); });
            h.assertFalse(f.tap(), "callback-canceled altered native arrow cannot be admitted");
            near(h, f.cp(), cp - 1, "foreign debit remains with no rollback or own charge");
            h.assertTrue(arrow.position().equals(foreign) && f.spawns == 0 && f.needles() == 3, "foreign native movement remains and cannot enable fallback");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void callback_reentry_and_matching_abort_retire_one_attempt_before_payment(GameTestHelper h) { reentry(h, false); }
    private static void reentry(GameTestHelper h, boolean abort) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); Vec3 start = arrow.position(); Snapshot before = f.snapshot();
            TeleportHook hook = f.onTeleport(event -> { if (abort) ShortInterception.abort(f.p, f.wire(f.inputs)); else f.tap(); });
            h.assertFalse(f.tap(), "synchronous reentry or abort poisons current attempt");
            f.same(before, "reentrant/aborted release"); h.assertTrue(hook.calls == 1 && arrow.position().equals(start) && f.spawns == 0, "one native callback cannot recurse or publish a fallback");
            h.assertFalse(f.release() || ShortInterception.press(f.p, f.wire(f.inputs)), "latest consumed physical input remains nonreplayable");
        }); h.runAfterDelay(6, () -> { f.verify(); f.close(); if (!abort) reentry(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void partial_batch_admission_pays_only_the_first_actual_move_and_never_falls_back(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Arrow first = f.arrow(2, 0), second = f.arrow(3, 0); Vec3 firstStart = first.position(), secondStart = second.position(); double cp = f.cp();
            f.onTeleport(event -> { if (event.getEntity() == second) event.setCanceled(true); });
            h.assertFalse(f.tap(), "canceled second callback terminates the partial batch without replay");
            h.assertTrue(!first.position().equals(firstStart) && second.position().equals(secondStart), "native first move remains while refused second stays in place");
            near(h, f.cp(), cp - 4.5, "partial batch pays only its one admitted projectile");
            h.assertTrue(ShortInterception.receipt(f.p).admitted() == 1 && !ShortInterception.receipt(f.p).fallback() && f.spawns == 0 && f.needles() == 3,
                    "partial native batch cannot replace missing slots with offhand media");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void target_and_remaining_arrow_changes_during_callback_cannot_retarget_the_snapshot(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Arrow first = f.arrow(2, 0), second = f.arrow(3, 0); Vec3 start = first.position(); Snapshot before = f.snapshot();
            f.onTeleport(event -> { f.target.setPos(f.target.position().add(1, 0, 0)); second.setOwner(f.p); });
            h.assertFalse(f.tap(), "changed frozen target invalidates before first own mutation"); f.same(before, "callback target change");
            h.assertTrue(first.position().equals(start) && second.getOwner() == f.p && f.spawns == 0, "no replacement target, rollback of foreign ownership, or fallback is invented");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=35)
    public static void legal_levels_pay_continuous_per_projectile_cost_and_native_cooldown(GameTestHelper h) { economics(h, 3); }
    private static void economics(GameTestHelper h, int level) {
        Fixture f = new Fixture(h, level); f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); Snapshot before = f.snapshot(); h.assertTrue(f.tap(), "legal LV" + level + " real arrow admission");
            double[] costs = {4.5, 1.125, .1125}; int[] cooldowns = {16, 6, 1};
            near(h, f.cp(), before.cp - costs[level - 3], "legal level per-projectile CP anchor");
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(ID, 0) == cooldowns[level - 3]
                    && f.budget().getLong("next_" + ID) == h.getLevel().getGameTime() + cooldowns[level - 3], "native economic deadline and UI cooldown agree");
            h.assertTrue(arrow.getOwner() == f.shooter && f.needles() == before.needles && AbilityStorage.get(f.p).exp(ID) == before.mastery
                    && AbilityStorage.get(f.p).levelExperience == before.levelXp, "paid use preserves original owner and all XP");
        }); h.runAfterDelay(6, () -> { f.verify(); f.close(); if (level < 5) economics(h, level + 1); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void insufficient_cp_refuses_nonempty_arrow_snapshot_without_consuming_offhand(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); Vec3 start = arrow.position(); f.budget().putDouble("cp", .001); Snapshot before = f.snapshot();
            h.assertFalse(f.tap(), "unaffordable real arrow has no actual mutation"); f.same(before, "insufficient CP");
            h.assertTrue(arrow.position().equals(start) && f.spawns == 0 && f.needles() == 3, "unaffordable nonempty snapshot cannot become a fallback");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void native_save_reload_and_logout_clear_target_without_refilling_depleted_cp(GameTestHelper h) {
        Fixture f = new Fixture(h); f.lock(); f.budget().putDouble("cp", 37); CompoundTag fences = f.own();
        AbilityStorage.save(f.p); CompoundTag saved = f.p.saveWithoutId(new CompoundTag());
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));
        h.assertFalse(ShortInterception.marked(f.p) || ShortInterception.active(f.p), "real logout event retires target preparation");
        f.p.load(saved); AbilityStorage.remove(f.p); AbilityStorage.get(f.p); f.p.setOnGround(true); f.p.setDeltaMovement(0, -.0784, 0); f.p.connection.resetPosition(); f.wire(2);
        ShortInterception.tick(f.p); near(h, f.cp(), 37, "declared native NBT reload cannot refill shared CP");
        h.assertTrue(f.own().equals(fences) && !ShortInterception.marked(f.p), "native reload restores no target and preserves exact request fences"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void native_context_changes_retire_mark_and_stale_release_without_ammunition(GameTestHelper h) { contextChange(h, 0); }
    private static void contextChange(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); f.lock(); Snapshot before = f.snapshot(); String stale = f.wire(f.inputs); var state = AbilityStorage.get(f.p);
        switch (mode) {
            case 0 -> state.activated = false;
            case 1 -> state.presets.edit(0, 0, SafeLandingProbe.ID, state::learned);
            case 2 -> state.interfering = true;
            default -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));
        }
        ShortInterception.tick(f.p);
        h.assertFalse(ShortInterception.marked(f.p) || ShortInterception.active(f.p) || ShortInterception.release(f.p, stale), "native context change closes saved mark and captured release " + mode);
        f.same(before, "context retirement"); h.assertTrue(f.spawns == 0, "context retirement never publishes a projectile");
        f.close(); if (mode < 3) contextChange(h, mode + 1); else h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void visible_hostile_lock_accepts_long_range_but_refuses_beyond_forty_eight_metres(GameTestHelper h) {
        Fixture f = new Fixture(h); f.target.setPos(f.source.add(0, 0, 46)); f.aim(f.target.getBoundingBox().getCenter()); Snapshot before = f.snapshot();
        h.assertTrue(f.tap() && ShortInterception.marked(f.p), "native hostile at46m remains lockable"); f.same(before, "long visible lock"); f.close();
        Fixture beyond = new Fixture(h); beyond.target.setPos(beyond.source.add(0, 0, 49)); beyond.aim(beyond.target.getBoundingBox().getCenter()); before = beyond.snapshot();
        h.assertFalse(beyond.tap() || ShortInterception.marked(beyond.p), "hostile beyond48m cannot be locked or replaced"); beyond.same(before, "beyond-range lock"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void native_wall_blocks_initial_lock_and_later_relocation_without_alternate_exit(GameTestHelper h) {
        Fixture f = new Fixture(h); f.aim(f.target.getBoundingBox().getCenter());
        for (int y = 5; y <= 7; y++) f.block(h.absolutePos(new BlockPos(10, y, 12)), Blocks.STONE.defaultBlockState());
        Snapshot before = f.snapshot(); h.assertFalse(f.tap() || ShortInterception.marked(f.p), "native solid wall occludes first target lock"); f.same(before, "occluded lock"); f.close();
        Fixture changed = new Fixture(h); changed.ready(() -> {
            Arrow arrow = changed.arrow(3, 0); Vec3 start = arrow.position();
            for (int y = 5; y <= 7; y++) changed.block(h.absolutePos(new BlockPos(10, y, 12)), Blocks.STONE.defaultBlockState());
            Snapshot snapshot = changed.snapshot(); h.assertFalse(changed.tap(), "wall inserted after lock refuses current physical path"); changed.same(snapshot, "later wall");
            h.assertTrue(arrow.position().equals(start) && changed.spawns == 0 && changed.needles() == 3, "blocked path chooses no alternate exit or fallback");
        }); changed.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=35)
    public static void real_four_admissions_keep_rolling_quota_until_exact_twentieth_tick(GameTestHelper h) {
        Fixture f = new Fixture(h, 5); f.ready(() -> {
            List<Arrow> first = List.of(f.arrow(2, 0), f.arrow(3, 0), f.arrow(4, 0), f.arrow(5, 0));
            h.assertTrue(f.tap() && ShortInterception.receipt(f.p).admitted() == 4, "first LV5 cast admits four current native arrows");
            h.assertTrue(Arrays.equals(f.own().getIntArray("quota"), new int[] {20, 20, 20, 20}) && f.own().getInt("cast") == 8,
                    "four independent20tick quotas and physical8tick fence persist at payment"); first.forEach(Entity::discard);
            f.post(4, f::lock);
            f.post(7, () -> h.assertTrue(f.own().getInt("cast") == 1, "one-tick economic CD never erases seven-tick physical remainder"));
            f.post(8, () -> { Arrow blocked = f.arrow(3, 0); Vec3 at = blocked.position(); Snapshot before = f.snapshot();
                h.assertFalse(f.tap(), "fifth projectile refuses after physical fence but before rolling20tick quota expires");
                f.same(before, "rolling quota refusal"); h.assertTrue(blocked.position().equals(at) && f.spawns == 0, "quota refusal cannot relocate or fall back"); blocked.discard();
                h.assertTrue(f.own().getInt("cast") == 0 && f.own().getIntArray("quota").length == 4, "quota persists independently of expired8tick cast fence"); });
            f.post(19, () -> h.assertTrue(Arrays.equals(f.own().getIntArray("quota"), new int[] {1, 1, 1, 1}), "all four admissions remain counted through tick19"));
            f.post(20, () -> { Arrow next = f.arrow(3, 0); double cp = f.cp();
                h.assertTrue(f.tap() && ShortInterception.receipt(f.p).admitted() == 1, "actual twentieth tick releases quota for one fresh snapshot");
                near(h, f.cp(), cp - .1125, "new LV5 projectile pays one independent admission");
                h.assertTrue(Arrays.equals(f.own().getIntArray("quota"), new int[] {20}), "new projectile creates only its own new20tick entry"); next.discard(); });
        }); f.finish(25);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void paid_native_logout_reload_preserves_fences_quota_and_one_use_original(GameTestHelper h) {
        Fixture f = new Fixture(h, 5); f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); h.assertTrue(f.tap(), "paid native arrow before logout"); double cp = f.cp(); CompoundTag fences = f.own();
            h.assertTrue(ShortInterceptionArrowBridge.leased(arrow), "controller arms this actual native flight");
            AbilityStorage.save(f.p); CompoundTag saved = f.p.saveWithoutId(new CompoundTag());
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));
            h.assertFalse(ShortInterception.marked(f.p) || ShortInterception.active(f.p) || ShortInterceptionArrowBridge.leased(arrow), "logout retires target and boost while leaving the native arrow entity alive");
            h.assertTrue(!arrow.isRemoved() && arrow.getOwner() == f.shooter, "logout retains exact original physical projectile and shooter");
            f.p.load(saved); AbilityStorage.remove(f.p); AbilityStorage.get(f.p); f.p.setOnGround(true); f.p.setDeltaMovement(0, -.0784, 0); f.p.connection.resetPosition(); f.wire(++f.inputs);
            h.assertTrue(f.own().equals(fences), "declared native NBT reload retains exact request/cooldown/cast/quota values"); near(h, f.cp(), cp, "relog never refunds its paid original");
            h.assertFalse(ShortInterceptionArrowBridge.eligibleArrow(f.p, arrow), "persisted denial prevents re-admitting previously paid original after relog");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void held_clear_retires_live_boost_without_rewriting_native_arrow_or_refunding(GameTestHelper h) {
        Fixture f = new Fixture(h, 5); f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); arrow.setDeltaMovement(arrow.getDeltaMovement().normalize().scale(.01));
            h.assertTrue(f.tap() && ShortInterceptionArrowBridge.leased(arrow), "slow actual native arrow has a paid live20tick permit");
            h.assertTrue(f.press(), "new held gesture starts even while prior physical cast fence remains");
            f.post(5, () -> h.assertTrue(ShortInterceptionArrowBridge.leased(arrow), "five genuine held ticks cannot clear the paid flight early"));
            f.post(6, () -> { h.assertFalse(ShortInterceptionArrowBridge.leased(arrow) || ShortInterception.marked(f.p), "sixth held tick retires every current NEW331 boost");
                h.assertTrue(!arrow.isRemoved() && arrow.getOwner() == f.shooter && f.needles() == 3 && f.spawns == 0, "clear keeps native flying original and creates no refund or replacement");
                f.release(); h.assertFalse(ShortInterception.marked(f.p), "release after clear cannot record a new mark");
                h.assertTrue(f.own().getInt("cast") == 2 && Arrays.equals(f.own().getIntArray("quota"), new int[] {14}), "held clear ages but never clears paid physical fences"); });
        }); f.finish(11);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void removed_locked_target_is_never_replaced_by_a_new_hostile_at_same_location(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Vec3 point = f.target.position(); f.target.discard(); Zombie replacement = f.mob(point); Arrow arrow = f.arrow(3, 0); Vec3 entry = arrow.position(); Snapshot before = f.snapshot();
            h.assertFalse(f.tap(), "dead original target cannot transfer its saved identity to a replacement hostile"); f.same(before, "lost locked target");
            h.assertTrue(arrow.position().equals(entry) && replacement.getHealth() == replacement.getMaxHealth() && f.spawns == 0 && f.needles() == 3,
                    "lost target produces no auto-selection, movement, hit or late fallback");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void ordinary_nonhostile_body_occludes_hostile_lock_without_selecting_through_it(GameTestHelper h) {
        Fixture f = new Fixture(h); var cow = EntityType.COW.create(h.getLevel()); cow.setNoAi(true); cow.setNoGravity(true); cow.setPos(f.source.add(0, 0, 5));
        h.assertTrue(h.getLevel().addFreshEntity(cow), "native nonhostile body admitted in sight corridor"); f.entities.add(cow); f.aim(f.target.getBoundingBox().getCenter()); Snapshot before = f.snapshot();
        h.assertFalse(f.tap() || ShortInterception.marked(f.p), "actual nearer nonhostile body cannot become a damage target or be ignored for lock"); f.same(before, "nonhostile occlusion"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void five_tick_short_release_uses_current_server_sight_and_remains_free(GameTestHelper h) {
        Fixture f = new Fixture(h); f.aim(f.source.add(8, 2, 0)); Snapshot before = f.snapshot(); h.assertTrue(f.press(), "press starts without capturing an initial target");
        f.post(5, () -> { h.assertFalse(ShortInterception.marked(f.p), "five real held ticks still have no target preparation");
            f.aim(f.target.getBoundingBox().getCenter()); h.assertTrue(f.release() && ShortInterception.marked(f.p), "last short release locks current server-visible target");
            h.assertValueEqual(ShortInterception.remainingTicks(f.p), ShortInterceptionRules.MARK_TICKS, "TTL begins at actual short release"); f.same(before, "five-tick free mark"); }); f.finish(6);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void fractional_actual_growth_pays_smooth_per_projectile_curve_without_xp(GameTestHelper h) {
        Fixture f = new Fixture(h); AbilityStorage.get(f.p).addExperience(SafeLandingProbe.ID, .01); BigDecimal q = HighPowerGrowth.values(f.p).position();
        h.assertTrue(q.compareTo(BigDecimal.valueOf(3)) > 0 && q.compareTo(BigDecimal.valueOf(4)) < 0, "real prerequisite experience supplies fractional native growth");
        f.ready(() -> { f.arrow(3, 0); Snapshot before = f.snapshot(); h.assertTrue(f.tap(), "fractional-growth real arrow is admitted");
            double expected = ContinuousHighPowerGrowth.curve("18", "9", "4.5", "1.125", ".1125").value(before.q).doubleValue();
            near(h, f.cp(), before.cp - expected, "actual per-arrow debit follows continuous approved curve");
            h.assertTrue(expected > 1.125 && expected < 4.5 && AbilityStorage.get(f.p).exp(ID) == before.mastery
                    && AbilityStorage.get(f.p).levelExperience == before.levelXp, "fractional cast neither snaps to level nor grants growth");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void exact_forty_eight_metre_center_locks_but_nearer_face_of_outside_center_does_not(GameTestHelper h) {
        Fixture exact = new Fixture(h);
        exact.target.setPos(exact.source.x, exact.p.getEyeY() - exact.target.getBbHeight() / 2.0, exact.source.z + 48);
        exact.aim(exact.target.getBoundingBox().getCenter()); Snapshot before = exact.snapshot();
        near(h, exact.p.getEyePosition().distanceTo(exact.target.getBoundingBox().getCenter()), 48, "declared native target center is at exact48m");
        h.assertTrue(exact.tap() && ShortInterception.marked(exact.p), "inclusive48m center is genuinely acquired"); exact.same(before, "exact-range free lock"); exact.close();
        Fixture beyond = new Fixture(h);
        beyond.target.setPos(beyond.source.x, beyond.p.getEyeY() - beyond.target.getBbHeight() / 2.0, beyond.source.z + 48.1);
        beyond.aim(beyond.target.getBoundingBox().getCenter()); before = beyond.snapshot();
        Vec3 eye = beyond.p.getEyePosition(); var face = beyond.target.getBoundingBox().clip(eye, eye.add(beyond.p.getLookAngle().scale(48)));
        h.assertTrue(face.isPresent() && eye.distanceTo(face.get()) < 48 && eye.distanceTo(beyond.target.getBoundingBox().getCenter()) > 48,
                "native near body face is inside48m while actual center is outside");
        h.assertFalse(beyond.tap() || ShortInterception.marked(beyond.p), "near face cannot grant a briefly valid mark for an outside center");
        beyond.same(before, "outside-center face refusal"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void callback_inserted_full_exit_or_target_wall_refuses_before_native_payment(GameTestHelper h) { callbackClearance(h, false); }
    private static void callbackClearance(GameTestHelper h, boolean targetWall) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); Vec3 source = arrow.position(), velocity = arrow.getDeltaMovement(); Snapshot before = f.snapshot();
            TeleportHook hook = f.onTeleport(event -> {
                BlockPos blocked = targetWall ? BlockPos.containing(f.target.getBoundingBox().getCenter())
                        : BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ());
                f.block(blocked, Blocks.STONE.defaultBlockState());
            });
            h.assertFalse(f.tap(), "new solid " + (targetWall ? "target-body" : "full-exit") + " obstruction is revalidated after the native callback");
            f.same(before, "callback clearance refusal");
            h.assertTrue(hook.calls == 1 && arrow.position().equals(source) && arrow.getDeltaMovement().equals(velocity)
                    && f.spawns == 0 && f.needles() == 3, "new obstruction gives no payment, offset exit, arrow rewrite or late fallback");
        }); h.runAfterDelay(6, () -> { f.verify(); f.close(); if (!targetWall) callbackClearance(h, true); else h.succeed(); });
    }

    /** Only location, rotation and native motion are relocation-owned NBT fields. */
    private static CompoundTag payload(Arrow arrow) {
        CompoundTag tag = arrow.saveWithoutId(new CompoundTag()); tag.remove("Pos"); tag.remove("Motion"); tag.remove("Rotation");
        CompoundTag data = tag.getCompound("NeoForgeData"); data.remove("academy:short_interception_used");
        if (data.isEmpty()) tag.remove("NeoForgeData"); else tag.put("NeoForgeData", data); return tag;
    }

    public static void near(GameTestHelper h, double actual, double expected, String why) {
        h.assertTrue(Math.abs(actual - expected) < 1e-6, why + ": " + actual + " != " + expected);
    }
    public record Snapshot(double cp, long deadline, int cooldown, double mastery, double levelXp, BigDecimal q,
                           String hp, String shield, double overload, int needles) {}

    public static final class PostCheck {
        final Fixture f; final int tick; final Runnable action; boolean ran;
        PostCheck(Fixture f, int delay, Runnable action) { this.f = f; tick = f.p.server.getTickCount() + delay; this.action = action; }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post event) {
            if (ran || event.getServer() != f.p.server || event.getServer().getTickCount() != tick) return;
            ran = true; try { action.run(); } catch (Throwable problem) { if (f.failure == null) f.failure = problem; }
        }
    }
    public static final class SpawnHook {
        final Fixture f; SpawnHook(Fixture f) { this.f = f; }
        @SubscribeEvent public void join(EntityJoinLevelEvent event) {
            if (!(event.getEntity() instanceof DisplacedNeedleEntity medium) || medium.getOwner() != f.p || !medium.isInterceptionNeedle()) return;
            f.spawns++; f.entities.add(medium);
        }
    }
    public static final class TeleportHook {
        final Fixture f; final Consumer<EntityTeleportEvent> action; public int calls;
        TeleportHook(Fixture f, Consumer<EntityTeleportEvent> action) { this.f = f; this.action = action; }
        @SubscribeEvent public void teleport(EntityTeleportEvent event) {
            if (!(event.getEntity() instanceof Arrow arrow) || !f.entities.contains(arrow)) return;
            calls++; action.accept(event);
        }
    }
    /** Shared declared native fixture for the separate combat acceptance suite. */
    public static final class Fixture implements AutoCloseable {
        public final GameTestHelper h; public final ServerPlayer p; public final EmbeddedChannel channel;
        public final Vec3 source; public final Zombie target, shooter;
        public final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>(); final List<PostCheck> checks = new ArrayList<>();
        final Map<BlockPos, BlockState> changed = new LinkedHashMap<>(); final Set<ChunkPos> held = new LinkedHashSet<>(); final UUID ticket = UUID.randomUUID();
        public long inputs; public int spawns; boolean closed; Throwable failure;
        public Fixture(GameTestHelper h) { this(h, 3); }
        public Fixture(GameTestHelper h, int level) {
            this.h = h; source = h.absoluteVec(new Vec3(10.5, 5, 6.5));
            for (int x = 7; x <= 13; x++) for (int z = 4; z <= 9; z++) block(h.absolutePos(new BlockPos(x, 4, z)), Blocks.STONE.defaultBlockState());
            for (int x : new int[] {8, 12}) for (int z = 6; z <= 65; z += 8) hold(new ChunkPos(BlockPos.containing(h.absoluteVec(new Vec3(x, 5, z)))));
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "SIT" + UUID.randomUUID().toString().substring(0, 9)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); acknowledgeLogin();
            p.setGameMode(GameType.SURVIVAL); p.getAbilities().invulnerable = false; p.getAbilities().instabuild = false;
            p.moveTo(source.x, source.y, source.z, 0, 0); p.setPose(Pose.STANDING); p.setShiftKeyDown(false); p.setNoGravity(false); p.fallDistance = 0; p.setOnGround(true);
            p.move(MoverType.SELF, new Vec3(0, -.0784, 0)); p.setDeltaMovement(0, -.0784, 0); p.connection.resetPosition();
            h.assertTrue(p.onGround() && p.position().equals(source), "declared source has genuine downward native floor contact");
            h.assertTrue(HighPowerCombat.enroll(p, level), "declared real caster enrollment"); var state = AbilityStorage.get(p);
            state.selectCategory(HighPowerCategories.TELEPORTER); state.setLevel(level);
            for (String learned : List.of(SafeLandingProbe.ID, PhaseSidestep.ID, DisplacedProjection.ID, CrossSubstitution.ID, ID)) state.learn(learned);
            state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "real learned skill occupies authenticated slot zero");
            HighPowerResources.initializeBudget(p); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 3));
            target = mob(h.absoluteVec(new Vec3(10.5, 5, 18.5))); shooter = mob(source.add(6, 0, 0));
            hook(new SpawnHook(this)); hook(new HighPowerPhaseSidestepRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5));
            h.testInfo.addListener(new GameTestListener() { public void testStructureLoaded(GameTestInfo info) {} public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); } public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); } });
        }
        public Zombie mob(Vec3 at) {
            Zombie mob = EntityType.HUSK.create(h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true); mob.setSilent(true);
            mob.getAttribute(Attributes.ARMOR).setBaseValue(0); mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1); mob.setPos(at);
            h.assertTrue(h.getLevel().addFreshEntity(mob), "native stationary hostile admitted"); entities.add(mob); return mob;
        }
        public Arrow arrow(double along, double side) {
            Vec3 forward = p.getLookAngle().normalize(), right = new Vec3(forward.z, 0, -forward.x).normalize();
            Arrow arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            arrow.setPos(p.getEyePosition().add(forward.scale(along)).add(right.scale(side))); arrow.setDeltaMovement(forward.scale(1.5));
            h.assertTrue(h.getLevel().addFreshEntity(arrow), "plain native flying arrow admitted before the release snapshot"); entities.add(arrow);
            // A real native tick establishes leftOwner; the declared fixture then places the entry.
            Vec3 entry = arrow.position(), velocity = arrow.getDeltaMovement(); arrow.tick(); arrow.setPos(entry); arrow.setDeltaMovement(velocity); return arrow;
        }
        public void aim(Vec3 at) { Vec3 d = at.subtract(p.getEyePosition()); p.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z))); p.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x*d.x + d.z*d.z)))); }
        public String wire(long input) { return new HighPowerInput.Input(0, input, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        public boolean press() { return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request("hp_short_interception_start", wire(++inputs))); }
        public boolean release() { return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request("hp_short_interception_release", wire(inputs))); }
        public boolean tap() { return press() && release(); }
        public void lock() { aim(target.getBoundingBox().getCenter()); h.assertTrue(tap() && ShortInterception.marked(p), "genuine first short release locks native hostile: " + ShortInterception.receipt(p)); }
        public void ready(Runnable action) { lock(); post(4, action); }
        public double cp() { return HighPowerResources.cp(p); }
        public int needles() { int count = 0; for (int i = 0; i < p.getInventory().getContainerSize(); i++) { ItemStack stack = p.getInventory().getItem(i); if (stack.is(AcademyCraft.NEEDLE.get())) count += stack.getCount(); } return count; }
        public CompoundTag budget() { return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); }
        public CompoundTag own() { return p.getPersistentData().getCompound(OWN).copy(); }
        public Snapshot snapshot() { var s = AbilityStorage.get(p); return new Snapshot(cp(), budget().getLong("next_" + ID), s.cooldowns.getOrDefault(ID, 0), s.exp(ID), s.levelExperience,
                HighPowerGrowth.values(p).position(), HighPowerCombat.healthText(p), HighPowerCombat.shieldText(p), s.overload, needles()); }
        public void same(Snapshot before, String why) { h.assertTrue(snapshot().equals(before), why + " preserves CP/deadline/cooldown/mastery/XP/growth/HP/shield/overload/stock"); }
        public void block(BlockPos pos, BlockState state) { h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)), "fixture block stays in declared native arena"); hold(new ChunkPos(pos));
            changed.putIfAbsent(pos, h.getLevel().getBlockState(pos)); h.getLevel().setBlockAndUpdate(pos, state); }
        void hold(ChunkPos chunk) { if (held.add(chunk)) { h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket); h.getLevel().getChunk(chunk.x, chunk.z); } }
        public TeleportHook onTeleport(Consumer<EntityTeleportEvent> action) { TeleportHook hook = new TeleportHook(this, action); hook(hook); return hook; }
        public void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        public void post(int delay, Runnable action) { PostCheck check = new PostCheck(this, delay, action); checks.add(check); hook(check); }
        public void verify() { if (failure instanceof RuntimeException problem) throw problem; if (failure instanceof Error problem) throw problem; if (failure != null) throw new AssertionError(failure);
            h.assertTrue(checks.stream().allMatch(check -> check.ran), "all requested genuine server Post observations ran"); }
        public void finish(int delay) { h.runAfterDelay(delay, () -> { verify(); h.succeed(); }); }
        public void acknowledgeLogin() { channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); } }
        @Override public void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); ShortInterception.clear(p); HighPowerBracerSession.remove(p);
            AbilityStorage.remove(p); p.server.getPlayerList().remove(p); entities.forEach(Entity::discard); changed.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state));
            held.forEach(chunk -> h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket)); channel.finishAndReleaseAll(); }
    }
}
