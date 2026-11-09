package cn.academy.port.gametest;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.SpatialPincerCorridorRules;
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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW341 consent native acceptance: resident players, actual connections, native event callbacks,
 * real four-tick mark intervals and physical invitation expiry. Floors, caster learning/enrollment,
 * initial relocation and optional fault listeners are declared fixtures, not survival/client evidence. */
@GameTestHolder("academy_highpower_spatial_consent")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerSpatialPincerConsentRuntimeTests {
    private static final String TEMPLATE = "highpower_spatial_pincer_consent_empty", BATCH = "highpower_spatial_pincer_consent";
    private static final String ID = SpatialPincerCorridor.ID, SHARED = "academy:safe_landing_probe_fences";
    private static final TicketType<UUID> TICKET = TicketType.create("academy_spatial_consent_fixture", UUID::compareTo);
    private HighPowerSpatialPincerConsentRuntimeTests() {}
    @SubscribeEvent public static void install(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(); ListTag size = new ListTag();
        size.add(IntTag.valueOf(24)); size.add(IntTag.valueOf(12)); size.add(IntTag.valueOf(24)); tag.put("size", size);
        ListTag blocks = new ListTag(); CompoundTag first = new CompoundTag(); ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); first.put("pos", pos); first.putInt("state", 0); blocks.add(first);
        tag.put("blocks", blocks); tag.put("entities", new ListTag()); ListTag palette = new ListTag(); CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower_spatial_consent", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void notice_review_and_old_notice_accept_are_free_without_recipient_enrollment(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Snapshot before = f.snapshot(); Vec3 source = f.receiver.p.position(); f.invite();
            CompoundTag notice = SpatialPincerConsent.invitation(f.receiver.p);
            h.assertTrue(notice != null && notice.getString("phase").equals("invite")
                    && notice.getUUID("caster").equals(f.p().getUUID()) && notice.getUUID("recipient").equals(f.receiver.p.getUUID()), "optional notice names exact caster and recipient");
            h.assertFalse(f.action("invite", f.receiver.p.getUUID()), "one caster cannot replace or refresh its outstanding invitation");
            h.assertTrue(SpatialPincerConsent.invitation(f.receiver.p).equals(notice), "duplicate invitation keeps original token and expiry unchanged");
            var oldAccept = f.replyRequest("accept");
            h.assertFalse(SpatialPincerConsent.request(f.receiver.p, oldAccept), "team membership and incoming notice are not consent");
            h.assertTrue(f.reply("open_review"), "recipient explicitly requests review");
            h.assertFalse(SpatialPincerConsent.request(f.receiver.p, oldAccept), "notice sequence cannot accept the later review");
            h.assertTrue(SpatialPincerConsent.snapshot(f.receiver.p) == null && f.receiver.p.position().equals(source)
                    && f.receiver.positions().isEmpty(), "notice and review do not open an owner picker or move recipient");
            f.same(before, "notice and review"); f.noReceiverLedger(); h.succeed();
        });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void explicit_accept_moves_A_to_C_once_with_frozen_facing_and_both_native_fences(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.invite(); h.assertTrue(f.reply("open_review"), "explicit review"); Snapshot before = f.snapshot();
            Body body = Body.capture(f.receiver.p); Vec3 casterSource = f.p().position(); var request = f.replyRequest("accept");
            h.assertTrue(SpatialPincerConsent.request(f.receiver.p, request), "distinct explicit accept performs native transport");
            f.success(before); h.assertTrue(Body.capture(f.receiver.p).equals(body) && f.p().position().equals(casterSource), "native motion/fall/health/status/inventory and caster position are preserved");
            near(h, f.receiver.p.getYRot(), f.yaw, "frozen C yaw"); near(h, f.receiver.p.getXRot(), f.pitch, "frozen C pitch");
            var packets = f.receiver.positions(); h.assertValueEqual(packets.size(), 1, "exactly one native absolute-position packet");
            h.assertTrue(packets.getFirst().getRelativeArguments().isEmpty(), "absolute native packet, with its disclosed client velocity reset");
            Snapshot paid = f.snapshot(); h.assertFalse(SpatialPincerConsent.request(f.receiver.p, request), "accepted token cannot replay");
            f.same(paid, "accepted replay"); h.assertTrue(SpatialPincerCorridor.preparedPoints(f.p()) == 0, "admission consumes the original three-mark plan");
            f.noReceiverLedger(); h.succeed();
        });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void missing_different_or_changed_actual_team_refuses_free(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Snapshot before = f.snapshot(); var board = h.getLevel().getScoreboard();
            board.removePlayerFromTeam(f.receiver.p.getScoreboardName(), f.team);
            h.assertFalse(f.action("invite", f.receiver.p.getUUID()), "teamless player cannot receive invitation");
            PlayerTeam other = f.otherTeam(); board.addPlayerToTeam(f.receiver.p.getScoreboardName(), other);
            h.assertFalse(f.action("invite", f.receiver.p.getUUID()), "different actual team cannot receive invitation");
            board.removePlayerFromTeam(f.receiver.p.getScoreboardName(), other); board.addPlayerToTeam(f.receiver.p.getScoreboardName(), f.team);
            // Roster is server-published: request a fresh genuine-tick heartbeat after restoring membership.
            f.post(10, () -> { f.invite(); h.assertTrue(f.reply("open_review"), "same actual team can review");
                board.removePlayerFromTeam(f.receiver.p.getScoreboardName(), f.team);
                double cp = f.cp(); h.assertFalse(f.reply("accept"), "changed membership revokes reviewed consent");
                near(h, f.cp(), cp, "changed team does not charge"); f.noMove(); f.noReceiverLedger(); h.succeed(); });
            f.finish(11);
            near(h, f.cp(), before.cp, "unavailable recipients do not charge");
        });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void forged_recipient_nonce_plan_revision_or_sequence_cannot_accept(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.invite(); f.reply("open_review"); Snapshot before = f.snapshot(); var good = f.replyRequest("accept");
            h.assertFalse(SpatialPincerConsent.request(f.p(), good), "caster cannot use recipient capability");
            h.assertFalse(SpatialPincerConsent.request(f.receiver.p, new SpatialPincerConsent.Request("", good.token(), UUID.randomUUID(), good.sequence(), "accept", good.planToken(), good.revision(), null, good.actor(), good.owner())), "wrong nonce");
            h.assertFalse(SpatialPincerConsent.request(f.receiver.p, new SpatialPincerConsent.Request("", good.token(), good.nonce(), good.sequence(), "accept", good.planToken() + 1, good.revision(), null, good.actor(), good.owner())), "wrong plan token");
            h.assertFalse(SpatialPincerConsent.request(f.receiver.p, new SpatialPincerConsent.Request("", good.token(), good.nonce(), good.sequence(), "accept", good.planToken(), good.revision() + 1, null, good.actor(), good.owner())), "wrong revision");
            h.assertFalse(SpatialPincerConsent.request(f.receiver.p, new SpatialPincerConsent.Request("", good.token(), good.nonce(), good.sequence() + 1, "accept", good.planToken(), good.revision(), null, good.actor(), good.owner())), "wrong sequence");
            f.same(before, "forged binding"); f.noMove(); h.succeed();
        });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void reject_and_picker_close_cancel_optional_invitation_without_spending_plan(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Snapshot before = f.snapshot(); f.invite(); var rejected = f.replyRequest("accept");
            h.assertTrue(f.reply("reject"), "explicit recipient reject");
            h.assertFalse(SpatialPincerConsent.request(f.receiver.p, rejected), "rejected nonce cannot return");
            f.invite(); var canceled = f.replyRequest("accept");
            h.assertTrue(f.action("close", null), "caster closes picker and cancels outgoing invite");
            h.assertFalse(SpatialPincerConsent.request(f.receiver.p, canceled), "canceled nonce cannot return");
            h.assertTrue(SpatialPincerConsent.invitation(f.receiver.p) == null && SpatialPincerCorridor.preparedPoints(f.p()) == 3,
                    "optional refusal spends no prepared plan"); f.same(before, "reject/cancel"); f.noMove(); h.succeed();
        });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void outside_A_horizontal_radius_refuses_free_even_with_valid_C_route(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.receiver.stand(f.entrance.add(.751, 0, 0)); f.invite(); f.reply("open_review");
            Snapshot before = f.snapshot(); h.assertFalse(f.reply("accept"), "recipient beyond .75m entrance radius cannot enter");
            f.same(before, "outside entrance"); f.noMove(); f.noReceiverLedger(); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void exact_A_horizontal_boundary_remains_admissible(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.receiver.stand(f.entrance.add(.75, 0, 0)); f.invite(); f.reply("open_review");
            Snapshot before = f.snapshot(); h.assertTrue(f.reply("accept"), "exact .75m horizontal boundary is admitted"); f.success(before); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_nondefault_recipient_scale_uses_value_geometry_and_exact_destination_formula(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.receiver.p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).setBaseValue(.8);
            f.receiver.p.refreshDimensions(); f.receiver.stand(f.entrance);
            var dimensions = f.receiver.p.getDimensions(f.receiver.p.getPose()); float eyeHeight = f.receiver.p.getEyeHeight();
            h.assertTrue(dimensions.width() < .6F && dimensions.height() < 1.8F, "declared native non-default scale changed actual geometry");
            f.invite(); f.reply("open_review"); Snapshot before = f.snapshot();
            h.assertTrue(f.reply("accept"), "stable scaled geometry with fresh native attachment objects remains admissible"); f.success(before);
            h.assertTrue(f.receiver.p.getBoundingBox().equals(dimensions.makeBoundingBox(f.destination))
                    && f.receiver.p.getEyePosition().equals(new Vec3(f.destination.x, f.destination.y + (double)eyeHeight, f.destination.z)),
                    "scaled body and cached eye height use exact native destination arithmetic"); f.noReceiverLedger(); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void vertical_outside_A_and_airborne_recipient_cannot_bypass_source_guard(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.invite(); f.reply("open_review");
            f.receiver.p.setPos(f.entrance.add(0, .126, 0)); f.receiver.p.setOnGround(false); Snapshot before = f.snapshot();
            h.assertFalse(f.reply("accept"), "airborne or >.125m vertical source cannot accept"); f.same(before, "airborne"); f.noMove(); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void source_full_support_and_standing_headroom_cannot_be_bypassed_by_crouching(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.invite(); f.reply("open_review");
            f.receiver.p.setPose(Pose.CROUCHING); f.receiver.p.setShiftKeyDown(true);
            f.block(8, 6, 10, Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
            Snapshot before = f.snapshot(); h.assertFalse(f.reply("accept"), "native crouched fit never substitutes for full standing clearance");
            f.same(before, "standing clearance"); f.noMove(); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void recipient_route_and_destination_changes_are_rechecked_before_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.invite(); f.reply("open_review"); Snapshot before = f.snapshot();
            TeleportHook hook = new TeleportHook(f.receiver.p, event -> f.block(14, 5, 10, Blocks.STONE));
            f.hook(hook); h.assertFalse(f.reply("accept"), "new solid body in A-C route cannot be tunneled through");
            h.assertValueEqual(hook.calls, 1, "one native pre-payment callback"); f.same(before, "new route block"); f.noMove(); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_cancellation_is_free_and_preserves_the_prepared_plan(GameTestHelper h) { eventRefusal(h, false); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_redirection_is_free_and_cannot_change_C(GameTestHelper h) { eventRefusal(h, true); }
    private static void eventRefusal(GameTestHelper h, boolean redirect) {
        Fixture f = new Fixture(h); f.ready(() -> { f.invite(); f.reply("open_review"); Snapshot before = f.snapshot();
            TeleportHook hook = new TeleportHook(f.receiver.p, event -> { near(h, f.cp(), before.cp, "event precedes payment");
                f.p().getPersistentData().putInt("academy:spatial_consent_callback_sentinel", 7);
                if (redirect) event.setTargetX(event.getTargetX() + .1); else event.setCanceled(true); });
            f.hook(hook); h.assertFalse(f.reply("accept"), "native veto/redirect denies admission");
            h.assertValueEqual(hook.calls, 1, "one real native event"); f.same(before, "native veto"); f.noMove();
            h.assertTrue(SpatialPincerCorridor.preparedPoints(f.p()) == 3 && f.p().getPersistentData().getInt("academy:spatial_consent_callback_sentinel") == 7,
                    "free veto leaves plan and unrelated native callback changes intact"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void synchronous_recipient_position_change_refuses_without_payment_or_rollback(GameTestHelper h) {
        mutation(h, false, changed -> { changed.setPos(changed.position().add(.1, 0, 0)); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void synchronous_caster_position_change_refuses_without_payment_or_rollback(GameTestHelper h) {
        mutation(h, true, changed -> { changed.setPos(changed.position().add(.1, 0, 0)); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void synchronous_recipient_inventory_change_refuses_without_payment_or_rollback(GameTestHelper h) {
        mutation(h, false, changed -> { changed.getInventory().getItem(0).grow(1); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void synchronous_caster_inventory_change_refuses_without_payment_or_rollback(GameTestHelper h) {
        mutation(h, true, changed -> { changed.getInventory().getItem(0).grow(1); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void synchronous_recipient_native_health_change_refuses_without_payment_or_rollback(GameTestHelper h) {
        mutation(h, false, changed -> { changed.setHealth(changed.getHealth() - 1); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void synchronous_caster_native_health_change_refuses_without_payment_or_rollback(GameTestHelper h) {
        mutation(h, true, changed -> { changed.setHealth(changed.getHealth() - 1); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void synchronous_recipient_motion_and_fall_change_refuses_without_payment_or_rollback(GameTestHelper h) {
        mutation(h, false, changed -> { changed.setDeltaMovement(.1, -.0784, 0); changed.fallDistance = 2; });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void synchronous_caster_cached_ability_change_refuses_without_payment_or_rollback(GameTestHelper h) {
        mutation(h, true, changed -> { AbilityStorage.get(changed).overload += .125; });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void synchronous_recipient_cached_ability_change_refuses_without_payment_or_rollback(GameTestHelper h) {
        mutation(h, false, changed -> { AbilityStorage.get(changed).overload += .125; });
    }
    private static void mutation(GameTestHelper h, boolean sender, Consumer<ServerPlayer> mutate) {
        Fixture f = new Fixture(h); f.ready(() -> { f.invite(); f.reply("open_review"); double cp = f.cp(); long deadline = f.budget().getLong("next_" + ID);
            ServerPlayer changed = sender ? f.p() : f.receiver.p; Vec3[] position = {null}; Body[] after = {null};
            TeleportHook hook = new TeleportHook(f.receiver.p, event -> { mutate.accept(changed); position[0] = changed.position(); after[0] = Body.capture(changed); });
            f.hook(hook); h.assertFalse(f.reply("accept"), "synchronous participant mutation refuses before commit");
            h.assertTrue(hook.calls == 1 && position[0].equals(changed.position()) && after[0].equals(Body.capture(changed)), "native callback mutation is preserved without rollback");
            near(h, f.cp(), cp, "no additional transfer CP debit"); h.assertTrue(f.budget().getLong("next_" + ID) == deadline, "no transfer cooldown");
            f.noMove(); f.noReceiverLedger(); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void synchronous_CP_replacement_or_debit_is_not_hidden_or_restored(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.invite(); f.reply("open_review"); double cp = f.cp(); long deadline = f.budget().getLong("next_" + ID);
            TeleportHook hook = new TeleportHook(f.receiver.p, event -> { CompoundTag replacement = f.budget().copy(); replacement.putDouble("cp", cp - .125); f.p().getPersistentData().put(HighPowerResources.DATA_KEY, replacement); });
            f.hook(hook); h.assertFalse(f.reply("accept"), "changed ledger identity and balance deny movement"); near(h, f.cp(), cp - .125, "foreign debit remains, without extra transfer debit or refund");
            h.assertTrue(f.budget().getLong("next_" + ID) == deadline, "no transfer deadline after callback resource mutation"); f.noMove(); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void both_participant_locks_refuse_reentry_and_only_one_native_commit_pays(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.invite(); f.reply("open_review"); var accept = f.replyRequest("accept"); Snapshot before = f.snapshot();
            boolean[] nested = {true, true}; TeleportHook hook = new TeleportHook(f.receiver.p, event -> {
                h.assertTrue(SpatialPincerConsent.committing(f.p()) && SpatialPincerConsent.committing(f.receiver.p)
                        && AbilityConsumption.busy(f.p()) && AbilityConsumption.busy(f.receiver.p), "both exact native actors lock before event callbacks");
                nested[0] = SpatialPincerConsent.request(f.receiver.p, accept); nested[1] = SpatialPincerCorridor.press(f.p(), f.caster.wire(f.caster.inputs + 1));
            }); f.hook(hook); h.assertTrue(SpatialPincerConsent.request(f.receiver.p, accept), "outer reviewed commit succeeds");
            h.assertTrue(!nested[0] && !nested[1] && hook.calls == 1, "neither actor can recursively consume or emit a second event"); f.success(before); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_caster_resource_callback_cannot_accept_reentrantly(GameTestHelper h) { resourceCallback(h, true); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_recipient_resource_callback_cannot_accept_reentrantly(GameTestHelper h) { resourceCallback(h, false); }
    private static void resourceCallback(GameTestHelper h, boolean sender) {
        Fixture f = new Fixture(h); f.ready(() -> { f.invite(); f.reply("open_review"); var accept = f.replyRequest("accept"); Snapshot before = f.snapshot();
            ServerPlayer owner = sender ? f.p() : f.receiver.p; boolean[] nested = {true}; int[] calls = {0};
            Object hook = new Object() { @SubscribeEvent public void perform(cn.academy.port.api.SkillPerformEvent event) {
                if (event.player == owner && event.skill.equals("spatial_consent_fixture_resource_callback")) {
                    calls[0]++; nested[0] = SpatialPincerConsent.request(f.receiver.p, accept);
                }
            } };
            NeoForge.EVENT_BUS.register(hook);
            try { AbilityStorage.get(owner).consumeWithForceSkill("spatial_consent_fixture_resource_callback", 0, 0, false); }
            finally { NeoForge.EVENT_BUS.unregister(hook); }
            h.assertTrue(calls[0] == 1 && !nested[0], "actual SkillPerform callback cannot admit transfer while either actor is consuming");
            f.same(before, "native resource callback"); f.noMove(); f.noReceiverLedger(); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void existing_recipient_shared_fence_blocks_accept_without_creating_a_ledger(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.invite(); f.reply("open_review"); Snapshot before = f.snapshot();
            CompoundTag shared = new CompoundTag(); shared.putInt("version", 1); shared.putInt("preview", 0);
            shared.putInt("movement", 8); shared.putInt("cooldown", 0); shared.putInt("mastery", 0);
            f.receiver.p.getPersistentData().put(SHARED, shared);
            h.assertFalse(f.reply("accept"), "existing native shared fence wins before caster debit"); f.same(before, "recipient fence");
            h.assertTrue(f.receiver.positions().isEmpty() && f.shared(f.receiver.p).equals(shared) && f.shared(f.p()).getInt("movement") == 0,
                    "denial preserves original recipient fence and leaves caster fence untouched"); f.noReceiverLedger(); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void caster_connection_replacement_revokes_invitation(GameTestHelper h) { connectionChanged(h, true); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void recipient_connection_replacement_revokes_invitation(GameTestHelper h) { connectionChanged(h, false); }
    private static void connectionChanged(GameTestHelper h, boolean sender) {
        Fixture f = new Fixture(h); f.ready(() -> { f.invite(); f.reply("open_review"); Snapshot before = f.snapshot(); var request = f.replyRequest("accept");
            Actor changed = sender ? f.caster : f.receiver; changed.p.connection = new ServerGamePacketListenerImpl(changed.p.server, changed.connection, changed.p, changed.cookie);
            h.assertFalse(SpatialPincerConsent.request(f.receiver.p, request), "new native connection never inherits old review capability");
            f.same(before, "connection changed"); f.noMove(); f.noReceiverLedger(); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_logout_and_death_clear_old_consent_and_forbid_replay(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.invite(); f.reply("open_review"); var request = f.replyRequest("accept"); Snapshot before = f.snapshot();
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.receiver.p));
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(f.p(), f.p().damageSources().generic()));
            h.assertTrue(SpatialPincerConsent.invitation(f.receiver.p) == null && SpatialPincerConsent.snapshot(f.p()) == null, "native lifecycle clears both halves");
            h.assertFalse(SpatialPincerConsent.request(f.receiver.p, request), "pre-lifecycle review is inert");
            f.same(before, "lifecycle cancellation"); f.noMove(); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void unknown_paid_native_failure_before_motion_consumes_plan_without_retry_or_refund(GameTestHelper h) { nativeFailure(h, false); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void unknown_paid_native_failure_after_motion_reports_only_observed_move(GameTestHelper h) { nativeFailure(h, true); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_post_move_body_enlargement_is_paid_unconfirmed_and_never_restored(GameTestHelper h) { nativePostcondition(h, 0); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_post_move_caster_fence_clear_is_paid_unconfirmed_and_never_restored(GameTestHelper h) { nativePostcondition(h, 1); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_post_move_recipient_fence_replacement_is_paid_unconfirmed_and_nonreplayable(GameTestHelper h) { nativePostcondition(h, 2); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_post_move_dimension_change_is_paid_unconfirmed_and_preserved(GameTestHelper h) { nativePostcondition(h, 3); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_post_move_eye_only_change_is_paid_unconfirmed_at_unchanged_bounds(GameTestHelper h) { nativePostcondition(h, 4); }
    private static void nativePostcondition(GameTestHelper h, int mutation) {
        Fixture f = new Fixture(h); net.minecraft.world.phys.AABB[] changedBody = {null}; CompoundTag[] changedFence = {null};
        SizeHook sizeHook = new SizeHook(f.receiver.p); f.hook(sizeHook); float[] changedEye = {0};
        f.receiver.p.connection = new ServerGamePacketListenerImpl(f.receiver.p.server, f.receiver.connection, f.receiver.p, f.receiver.cookie) {
            @Override public void teleport(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> relative) {
                super.teleport(x, y, z, yaw, pitch, relative);
                if (mutation == 0) {
                    changedBody[0] = f.receiver.p.getBoundingBox().inflate(.01, 0, 0); f.receiver.p.setBoundingBox(changedBody[0]);
                } else if (mutation == 1) {
                    f.p().getPersistentData().remove(SHARED);
                } else if (mutation == 2) {
                    changedFence[0] = f.receiver.p.getPersistentData().getCompound(SHARED).copy();
                    f.receiver.p.getPersistentData().put(SHARED, changedFence[0]);
                } else if (mutation == 3) {
                    f.receiver.p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).setBaseValue(.8);
                    f.receiver.p.refreshDimensions(); changedBody[0] = f.receiver.p.getBoundingBox();
                } else {
                    changedBody[0] = f.receiver.p.getBoundingBox(); sizeHook.enabled = true;
                    try { f.receiver.p.refreshDimensions(); } finally { sizeHook.enabled = false; }
                    changedEye[0] = f.receiver.p.getEyeHeight();
                }
            }
        };
        f.ready(() -> { f.invite(); f.reply("open_review"); var request = f.replyRequest("accept"); Snapshot before = f.snapshot();
            h.assertFalse(SpatialPincerConsent.request(f.receiver.p, request), "changed post-native body/fence is not a confirmed success");
            var receipt = SpatialPincerConsent.receipt(f.p());
            h.assertTrue(receipt != null && receipt.paid() && receipt.moved() && receipt.reason().equals("commit_unconfirmed"),
                    "native postcondition violation records payment and observed movement without success");
            near(h, f.cp(), before.cp - f.cost, "paid native uncertainty has no refund");
            h.assertTrue(f.receiver.p.position().equals(f.destination) && SpatialPincerCorridor.preparedPoints(f.p()) == 0,
                    "observed destination and consumed plan remain terminal");
            if (mutation == 0) h.assertTrue(changedBody[0].equals(f.receiver.p.getBoundingBox()), "foreign enlarged body is not restored");
            else if (mutation == 1) h.assertFalse(f.p().getPersistentData().contains(SHARED), "foreign caster fence removal is not restored");
            else if (mutation == 2) h.assertTrue(f.receiver.p.getPersistentData().get(SHARED) == changedFence[0], "equal-valued foreign fence replacement is not overwritten");
            else if (mutation == 3) h.assertTrue(f.receiver.p.getDimensions(f.receiver.p.getPose()).width() < .6F
                    && changedBody[0].equals(f.receiver.p.getBoundingBox()), "native dimension change is preserved without restoration");
            else h.assertTrue(sizeHook.calls == 1 && changedBody[0].equals(f.receiver.p.getBoundingBox()) && f.receiver.p.getEyeHeight() == changedEye[0],
                    "one real native Size event changes only cached eye height; unchanged bounds and changed eye are preserved");
            Snapshot paid = f.snapshot(); h.assertFalse(SpatialPincerConsent.request(f.receiver.p, request), "paid postcondition failure cannot replay");
            f.same(paid, "postcondition replay"); f.noReceiverLedger(); h.succeed(); });
    }
    private static void nativeFailure(GameTestHelper h, boolean after) {
        Fixture f = new Fixture(h);
        f.receiver.p.connection = new ServerGamePacketListenerImpl(f.receiver.p.server, f.receiver.connection, f.receiver.p, f.receiver.cookie) {
            @Override public void teleport(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> relative) {
                if (after) super.teleport(x, y, z, yaw, pitch, relative);
                throw new IllegalStateException("Declared NEW341 native failure " + (after ? "after" : "before") + " movement");
            }
        };
        f.ready(() -> { f.invite(); f.reply("open_review"); var request = f.replyRequest("accept"); Snapshot before = f.snapshot();
            h.assertFalse(SpatialPincerConsent.request(f.receiver.p, request), "injected unknown native result is terminal");
            var receipt = SpatialPincerConsent.receipt(f.p()); h.assertTrue(receipt != null && receipt.paid() && receipt.moved() == after, "receipt distinguishes paid admission and observed movement");
            near(h, f.cp(), before.cp - f.cost, "unknown native failure never refunds committed CP");
            h.assertTrue(SpatialPincerCorridor.preparedPoints(f.p()) == 0 && f.shared(f.p()).getInt("movement") == 8 && f.shared(f.receiver.p).getInt("movement") == 8, "both physical fences and consumed plan remain terminal");
            Snapshot paid = f.snapshot(); h.assertFalse(SpatialPincerConsent.request(f.receiver.p, request), "unknown completion cannot replay"); f.same(paid, "unknown replay"); f.noReceiverLedger(); h.succeed(); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void normal_native_regeneration_keeps_original_quote_and_consent_valid(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.budget().putDouble("cp", 1); f.invite(); f.reply("open_review"); double quote = SpatialPincerConsent.invitation(f.receiver.p).getDouble("cost");
            f.post(2, () -> { h.assertTrue(f.cp() > 1, "real native ticks regenerate depleted caster CP while review waits");
                Snapshot before = f.snapshot(); h.assertTrue(f.reply("accept"), "normal waiting recovery preserves consent");
                near(h, before.cp - f.cp(), quote, "original frozen quote paid against current recovered balance"); f.success(before); f.noReceiverLedger(); }); f.finish(3); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=230)
    public static void invitation_200_physical_ticks_never_refresh_from_review_or_duplicate_tick_calls(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.invite(); var old = f.replyRequest("accept");
            h.assertValueEqual(SpatialPincerConsent.invitation(f.receiver.p).getInt("remaining"), 200, "default invitation capped at200");
            for (int n = 0; n < 50; n++) SpatialPincerConsent.physical(new ServerTickEvent.Pre(() -> true, f.p().server));
            h.assertTrue(SpatialPincerConsent.invitation(f.receiver.p) != null, "same genuine tick cannot age the invitation twice");
            f.post(100, () -> h.assertTrue(f.reply("open_review"), "late explicit review remains optional and cannot refresh lifetime"));
            f.post(199, () -> h.assertTrue(SpatialPincerConsent.invitation(f.receiver.p) != null, "invitation remains live through199 native ticks"));
            f.post(200, () -> { Snapshot before = f.snapshot(); h.assertTrue(SpatialPincerConsent.invitation(f.receiver.p) == null, "exact200th tick expires original invitation");
                h.assertFalse(SpatialPincerConsent.request(f.receiver.p, old), "expired token is inert"); f.same(before, "expiry"); f.noMove(); f.noReceiverLedger(); }); f.finish(201); });
    }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=550)
    public static void late_invitation_cannot_outlive_original_400_tick_plan(GameTestHelper h) {
        Fixture f = new Fixture(h);
        int lateInviteTick = SpatialPincerCorridorRules.PREPARATION_TICKS - SpatialPincerCorridorRules.INVITATION_TICKS / 2;
        f.ready(() -> f.post(lateInviteTick, () -> {
            f.invite(); f.reply("open_review"); var old = f.replyRequest("accept"); CompoundTag data = SpatialPincerConsent.invitation(f.receiver.p);
            int remaining = data.getInt("remaining"); h.assertTrue(remaining > 0 && remaining < SpatialPincerCorridorRules.INVITATION_TICKS && remaining == data.getInt("plan_remaining"),
                    "invitation uses the shorter remaining plan lifetime");
            f.post(remaining, () -> { Snapshot before = f.snapshot(); h.assertTrue(SpatialPincerConsent.invitation(f.receiver.p) == null && SpatialPincerCorridor.preparedPoints(f.p()) == 0, "original plan expiry retires shorter invitation");
                h.assertFalse(SpatialPincerConsent.request(f.receiver.p, old), "expired plan cannot replay reviewed movement"); f.same(before, "plan expiry"); f.noMove(); }); f.finish(remaining + 1);
        }));
    }

    private record Snapshot(double cp, long deadline, int cooldown, double mastery, double experience, BigDecimal q,
                            String health, String shield, double overload, List<String> inventory) {}
    private record Body(Vec3 motion, float fall, boolean grounded, boolean gravity, boolean flying, int hurt, int invulnerable,
                        int fire, float health, float absorption, List<String> inventory) {
        static Body capture(ServerPlayer p) { return new Body(p.getDeltaMovement(), p.fallDistance, p.onGround(), p.isNoGravity(), p.getAbilities().flying,
                p.hurtTime, p.invulnerableTime, p.getRemainingFireTicks(), p.getHealth(), p.getAbsorptionAmount(), HighPowerSpatialPincerConsentRuntimeTests.inventory(p)); }
    }
    private static List<String> inventory(ServerPlayer p) { List<String> result = new ArrayList<>();
        for (int n = 0; n < p.getInventory().getContainerSize(); n++) result.add(p.getInventory().getItem(n).toString());
        result.add(p.containerMenu.getCarried().toString()); return List.copyOf(result); }
    private static void near(GameTestHelper h, double actual, double expected, String reason) { h.assertTrue(Math.abs(actual - expected) < 1e-7, reason + ": " + actual + " != " + expected); }
    public static final class TeleportHook {
        final ServerPlayer owner; final Consumer<EntityTeleportEvent> action; int calls;
        TeleportHook(ServerPlayer owner, Consumer<EntityTeleportEvent> action) { this.owner = owner; this.action = action; }
        @SubscribeEvent public void teleport(EntityTeleportEvent event) { if (event.getEntity() == owner) { calls++; action.accept(event); } }
    }
    public static final class SizeHook {
        final ServerPlayer owner; boolean enabled; int calls;
        SizeHook(ServerPlayer owner) { this.owner = owner; }
        @SubscribeEvent public void size(net.neoforged.neoforge.event.entity.EntityEvent.Size event) {
            if (enabled && event.getEntity() == owner) {
                calls++; event.setNewSize(event.getNewSize().withEyeHeight(event.getNewSize().eyeHeight() + .125F));
            }
        }
    }
    public static final class PostCheck {
        final Fixture fixture; final int tick; final Runnable action; boolean ran;
        PostCheck(Fixture fixture, int delay, Runnable action) { this.fixture = fixture; this.tick = fixture.p().server.getTickCount() + delay; this.action = action; }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post event) {
            if (ran || event.getServer() != fixture.p().server || event.getServer().getTickCount() != tick) return; ran = true;
            try { action.run(); } catch (Throwable failure) { if (fixture.failure == null) fixture.failure = failure; }
        }
    }
    private static final class Actor implements AutoCloseable {
        final Fixture fixture; final ServerPlayer p; final Connection connection; final CommonListenerCookie cookie; final EmbeddedChannel channel; long inputs;
        Actor(Fixture fixture, Vec3 position, boolean enrolled) {
            this.fixture = fixture; var server = fixture.h.getLevel().getServer();
            cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "SP" + UUID.randomUUID().toString().substring(0, 10)), false);
            p = new ServerPlayer(server, fixture.h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            for (var packet : positions()) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
            p.setGameMode(GameType.SURVIVAL); p.getAbilities().invulnerable = false; p.getAbilities().instabuild = false; stand(position);
            if (enrolled) fixture.h.assertTrue(HighPowerCombat.enroll(p, 5), "declared native caster enrollment");
            p.getInventory().setItem(0, new ItemStack(Items.APPLE, 3)); p.getInventory().setItem(1, new ItemStack(Items.ARROW, 7));
            fixture.hook(new HighPowerPhaseSidestepRuntimeTests.NativePlayerPump(p, fixture.h.testInfo.getTimeoutTicks() + 5)); drain();
        }
        void stand(Vec3 position) { p.setPos(position); p.setPose(Pose.STANDING); p.setShiftKeyDown(false); p.setNoGravity(false); p.fallDistance = 0;
            p.setOnGround(true); p.move(MoverType.SELF, new Vec3(0, -.0784, 0)); p.setDeltaMovement(0, -.0784, 0); p.connection.resetPosition();
            fixture.h.assertTrue(p.onGround() && p.position().equals(position), "actual native floor collision establishes supported feet"); }
        void aim(Vec3 point) { Vec3 d = point.subtract(p.getEyePosition()); p.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
            p.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)))); }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(long input) { return new HighPowerInput.Input(0, input, p.getId(), epoch()).wire(); }
        List<Object> drain() { List<Object> result = new ArrayList<>(); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks();
            Object packet; while ((packet = channel.readOutbound()) != null) { result.add(packet); ReferenceCountUtil.release(packet); } return result; }
        List<ClientboundPlayerPositionPacket> positions() { List<ClientboundPlayerPositionPacket> result = new ArrayList<>();
            for (Object packet : drain()) if (packet instanceof ClientboundPlayerPositionPacket position) result.add(position); return result; }
        @Override public void close() { SpatialPincerConsent.remove(p); SpatialPincerCorridor.clear(p); SafeLandingProbe.remove(p);
            ReturnMark.remove(p); PhaseSidestep.remove(p); HighPowerBracerSession.remove(p); AbilityStorage.remove(p);
            p.server.getPlayerList().remove(p); channel.finishAndReleaseAll(); }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final Actor caster, receiver; final List<Object> hooks = new ArrayList<>(); final List<PostCheck> checks = new ArrayList<>();
        final Map<BlockPos, BlockState> changed = new LinkedHashMap<>(); final Set<ChunkPos> held = new LinkedHashSet<>(); final UUID ticket = UUID.randomUUID();
        final List<PlayerTeam> teams = new ArrayList<>(); final PlayerTeam team; Vec3 entrance, destination; float yaw, pitch; double cost; int cooldown;
        Throwable failure; boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h; for (int x = 4; x <= 20; x++) for (int z = 4; z <= 18; z++) block(x, 4, z, Blocks.STONE);
            caster = new Actor(this, absolute(8.5, 5, 6.5), true); var state = AbilityStorage.get(p()); state.selectCategory(HighPowerCategories.TELEPORTER);
            state.setLevel(5); for (var skill : HighPowerSkills.ALL) if (skill.category().equals(HighPowerCategories.TELEPORTER)) state.learn(skill.id());
            state.activated = true; state.overloadFine = true; h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "declared mapped native341 slot");
            HighPowerResources.initializeBudget(p()); caster.epoch(); caster.drain();
            receiver = new Actor(this, absolute(6.5, 5, 15.5), false); team = otherTeam();
            h.getLevel().getScoreboard().addPlayerToTeam(p().getScoreboardName(), team);
            h.getLevel().getScoreboard().addPlayerToTeam(receiver.p.getScoreboardName(), team);
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {} public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
            });
        }
        ServerPlayer p() { return caster.p; }
        Vec3 absolute(double x, double y, double z) { return h.absoluteVec(new Vec3(x, y, z)); }
        PlayerTeam otherTeam() { PlayerTeam result = h.getLevel().getScoreboard().addPlayerTeam("sp" + UUID.randomUUID().toString().substring(0, 10)); teams.add(result); return result; }
        void hold(BlockPos pos) { ChunkPos chunk = new ChunkPos(pos); if (held.add(chunk)) { h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket); h.getLevel().getChunk(chunk.x, chunk.z); } }
        void block(int x, int y, int z, Block block) { block(x, y, z, block.defaultBlockState()); }
        void block(int x, int y, int z, BlockState state) { BlockPos pos = h.absolutePos(new BlockPos(x, y, z)); hold(pos);
            changed.putIfAbsent(pos, h.getLevel().getBlockState(pos)); h.getLevel().setBlockAndUpdate(pos, state); }
        void mark(int index) { p().setShiftKeyDown(false); p().setPose(Pose.STANDING); caster.aim(absolute(8.5 + index * 4, 5, 10.5));
            String wire = caster.wire(++caster.inputs); h.assertTrue(SpatialPincerCorridor.press(p(), wire), "authenticated press for mark " + index);
            h.assertTrue(SpatialPincerCorridor.release(p(), wire), "native first-hit UP mark " + index + ": " + SpatialPincerCorridor.receipt(p()));
            h.assertValueEqual(SpatialPincerCorridor.preparedPoints(p()), index + 1, "actual prepared point count"); }
        void ready(Runnable work) {
            mark(0); post(4, () -> mark(1)); post(8, () -> mark(2));
            post(12, () -> { p().setShiftKeyDown(true); String wire = caster.wire(++caster.inputs);
                h.assertTrue(SpatialPincerCorridor.press(p(), wire) && SpatialPincerCorridor.release(p(), wire), "crouch short release after3 actual marks opens consent picker: " + SpatialPincerCorridor.receipt(p()));
                CompoundTag data = SpatialPincerConsent.snapshot(p()); h.assertTrue(data != null, "native named teammate picker publication");
                entrance = new Vec3(data.getDouble("a_x"), data.getDouble("a_y"), data.getDouble("a_z"));
                destination = new Vec3(data.getDouble("x"), data.getDouble("y"), data.getDouble("z")); yaw = data.getFloat("yaw"); pitch = data.getFloat("pitch");
                cost = data.getDouble("cost"); cooldown = data.getInt("cooldown"); receiver.stand(entrance); caster.drain(); receiver.drain(); work.run(); });
            h.runAfterDelay(14, this::verifyFailures);
            h.runAfterDelay(h.testInfo.getTimeoutTicks() - 2, this::verifyFailures);
        }
        SpatialPincerConsent.Request ownerRequest(String action, UUID recipient) { CompoundTag data = SpatialPincerConsent.snapshot(p());
            h.assertTrue(data != null, "owner publication exists for " + action); return new SpatialPincerConsent.Request(caster.wire(caster.inputs),
                    data.getLong("token"), data.getUUID("nonce"), data.getLong("sequence"), action, data.getLong("plan_token"), data.getLong("revision"), recipient, p().getId(), p().getUUID()); }
        boolean action(String action, UUID recipient) { return SpatialPincerConsent.request(p(), ownerRequest(action, recipient)); }
        void invite() { h.assertTrue(action("invite", receiver.p.getUUID()), "named recipient receives optional invitation: " + SpatialPincerConsent.snapshot(p())); caster.drain(); receiver.drain(); }
        SpatialPincerConsent.Request replyRequest(String action) { CompoundTag data = SpatialPincerConsent.invitation(receiver.p); h.assertTrue(data != null, "recipient publication exists for " + action);
            return new SpatialPincerConsent.Request("", data.getLong("token"), data.getUUID("nonce"), data.getLong("sequence"), action,
                    data.getLong("plan_token"), data.getLong("revision"), null, receiver.p.getId(), receiver.p.getUUID()); }
        boolean reply(String action) { return SpatialPincerConsent.invitation(receiver.p) != null && SpatialPincerConsent.request(receiver.p, replyRequest(action)); }
        CompoundTag budget() { return p().getPersistentData().getCompound(HighPowerResources.DATA_KEY); }
        CompoundTag shared(ServerPlayer player) { return player.getPersistentData().getCompound(SHARED).copy(); }
        double cp() { return HighPowerResources.cp(p()); }
        Snapshot snapshot() { var state = AbilityStorage.get(p()); return new Snapshot(cp(), budget().getLong("next_" + ID), state.cooldowns.getOrDefault(ID, 0), state.exp(ID), state.levelExperience,
                HighPowerGrowth.values(p()).position(), HighPowerCombat.healthText(p()), HighPowerCombat.shieldText(p()), state.overload, inventory(p())); }
        void same(Snapshot before, String reason) { h.assertTrue(snapshot().equals(before), reason + " preserves caster CP/cooldown/mastery/XP/q/health/shield/inventory"); }
        void success(Snapshot before) { var receipt = SpatialPincerConsent.receipt(p()); h.assertTrue(receipt != null && receipt.paid() && receipt.moved() && receipt.reason().isEmpty(), "one confirmed paid native transfer: " + receipt);
            h.assertTrue(receiver.p.position().equals(destination) && receipt.destination().equals(destination), "C is the exact native destination"); near(h, cp(), before.cp - cost, "exactly one frozen caster charge");
            h.assertTrue(shared(p()).getInt("movement") == 8 && shared(receiver.p).getInt("movement") == 8, "both actors acquire shared8 native movement fences");
            h.assertTrue(budget().getLong("next_" + ID) == p().serverLevel().getGameTime() + cooldown, "frozen native skill cooldown"); }
        void noMove() { h.assertTrue(receiver.positions().isEmpty(), "refused action emits no native transport");
            h.assertTrue(shared(p()).getInt("movement") == 0 && shared(receiver.p).getInt("movement") == 0, "refused action acquires no movement fence"); }
        void noReceiverLedger() { h.assertFalse(receiver.p.getPersistentData().contains(HighPowerCombat.DATA_KEY) || receiver.p.getPersistentData().contains(HighPowerResources.DATA_KEY), "ordinary recipient gets no HP/shield/CP enrollment ledger");
            var state = AbilityStorage.peek(receiver.p); h.assertTrue(state == null || !state.learned(ID) && !state.learned(TacticalCoordinateFormation.ID), "recipient does not learn341 or353"); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void post(int delay, Runnable action) { PostCheck check = new PostCheck(this, delay, action); checks.add(check); hook(check); }
        void verifyFailures() { if (failure instanceof RuntimeException problem) throw problem; if (failure instanceof Error problem) throw problem; if (failure != null) throw new AssertionError(failure); }
        void finish(int delay) { h.runAfterDelay(delay, () -> { verifyFailures(); h.assertTrue(checks.stream().allMatch(check -> check.ran), "all native tick observations executed"); h.succeed(); }); }
        @Override public void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); receiver.close(); caster.close();
            teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam); changed.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state));
            held.forEach(chunk -> h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket)); }
    }
}
