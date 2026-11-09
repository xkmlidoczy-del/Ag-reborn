package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.TacticalCoordinateRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NEW353 native server acceptance. Every traveler is an actual resident ServerPlayer
 * with a native connection and position acknowledgements, never a FakePlayer.
 * Enrollment, learned skills, safe floors and initial relocations are declared test
 * fixtures, not survival acquisition or end-to-end multiplayer client evidence.
 * Actual event-bus callbacks are always unregistered by try/finally or test cleanup.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class AcademyTacticalCoordinateFormationRuntimeTests {
    private static final String TEMPLATE = "highpower_tactical_coordinate_empty", BATCH = "highpower_tactical_coordinate";
    private static final String ID = TacticalCoordinateFormation.ID, DATA = TacticalCoordinateFormation.DATA_KEY;
    private static final String SHARED = "academy:safe_landing_probe_fences";
    private static final TicketType<UUID> TICKET = TicketType.create("academy_tactical_coordinate_fixture", UUID::compareTo);
    private AcademyTacticalCoordinateFormationRuntimeTests() {}

    /** Same native in-memory fixture convention as the retained high-power suite. */
    @SubscribeEvent public static void install(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(); ListTag size = new ListTag();
        size.add(IntTag.valueOf(88)); size.add(IntTag.valueOf(12)); size.add(IntTag.valueOf(24)); tag.put("size", size);
        ListTag blocks = new ListTag(); CompoundTag first = new CompoundTag(); ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); first.put("pos", pos); first.putInt("state", 0); blocks.add(first);
        tag.put("blocks", blocks); tag.put("entities", new ListTag()); ListTag palette = new ListTag(); CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void native_catalog_requires_lv5_learned_spatial_relay_and_authenticated_slot(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            var skill = SkillCatalog.find(HighPowerCategories.TELEPORTER, ID).orElseThrow();
            h.assertTrue(skill.level() == 5 && skill.requirements().equals(List.of(new SkillCatalog.Requirement(SpatialRelay.ID, 0))), "NEW353 native catalog is LV5 after learned343");
            Snapshot before = f.snapshot(); long epoch = f.caster.epoch();
            h.assertFalse(TacticalCoordinateFormation.press(f.p(), new HighPowerInput.Input(0, 1, f.p().getId() + 1, epoch).wire()), "another native entity cannot open owner menu");
            h.assertFalse(TacticalCoordinateFormation.press(f.p(), new HighPowerInput.Input(1, 1, f.p().getId(), epoch).wire()), "another mapped slot cannot open353");
            AbilityStorage.get(f.p()).setLevel(4);
            h.assertFalse(f.open(), "LV4 cannot use a fixture-learned LV5 skill");
            AbilityStorage.get(f.p()).setLevel(5); AbilityStorage.get(f.p()).unlearn(SpatialRelay.ID);
            h.assertFalse(f.open(), "missing actual343 prerequisite denies353");
            h.assertTrue(f.p().position().equals(f.anchor) && f.caster.positions().isEmpty(), "denied catalog routes never move");
            near(h, f.cp(), before.cp(), "denied catalog routes never charge"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void opening_only_is_free_and_captures_no_automatic_template_or_movement(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            Snapshot before = f.snapshot(); CompoundTag shared = f.shared(f.p());
            h.assertTrue(AcademyGameplay.requestFromClient(f.p(), new AcademyNetwork.Request("hp_tactical_coordinate_open", f.caster.wire(++f.caster.inputs))), "canonical native mapped key opens owner controls");
            h.assertTrue(TacticalCoordinateFormation.snapshot(f.p()) != null && TacticalCoordinateFormation.templates(f.p()).isEmpty(), "open publishes controls without capturing a template");
            h.assertTrue(TacticalCoordinateFormation.release(f.p(), f.caster.wire(f.caster.inputs)), "matching release does not confirm movement");
            f.same(before, "open and release"); h.assertTrue(f.shared(f.p()).equals(shared) && f.caster.positions().isEmpty(), "opening writes no physical fence or native transport");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void six_templates_capture_exact_native_feet_and_edits_are_free(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            Snapshot before = f.snapshot(); h.assertTrue(f.open(), "open native six-slot controls"); Set<UUID> ids = new HashSet<>();
            for (int index = 0; index < 6; index++) {
                Vec3 feet = f.absolute(8.125 + index * .375, 5, 8.375); f.caster.stand(feet); f.p().setYRot(37); f.p().setXRot(-23);
                h.assertTrue(f.action("select", index) && f.action("save", index), "explicit save captures slot " + index);
                var template = f.template(index); h.assertTrue(template.feet().equals(feet) && template.revision() > 0 && ids.add(template.id()), "template retains double feet, valid revision and unique stable UUID");
                near(h, template.yaw(), 37, "captured yaw"); near(h, template.pitch(), -23, "captured pitch");
            }
            h.assertValueEqual(TacticalCoordinateFormation.templates(f.p()).size(), 6, "native storage holds exactly six occupied templates");
            CompoundTag storage = f.storage(); boolean rejected = false; try { f.action("select", 6); } catch (IllegalArgumentException expected) { rejected = true; } h.assertTrue(rejected, "wire constructor rejects seventh slot index");
            h.assertTrue(f.storage().equals(storage), "out-of-bounds request cannot modify six-slot storage");
            f.same(before, "six capture edits"); h.assertTrue(f.caster.positions().isEmpty() && f.shared(f.p()).isEmpty(), "editing grants no movement or shared fence"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void draft_rotation_and_saved_revision_keep_exact_position_and_pitch(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.p().setYRot(-179); f.p().setXRot(31); f.prepare(); var original = f.template(0); Snapshot before = f.snapshot();
            h.assertTrue(f.action("rotate_left", 0), "explicit left rotation edits draft");
            h.assertTrue(f.template(0).equals(original), "unsaved rotation leaves persistent template inert");
            h.assertTrue(f.action("save", 0), "explicit save commits edited heading"); var edited = f.template(0);
            h.assertTrue(edited.id().equals(original.id()) && edited.revision() > original.revision() && edited.feet().equals(original.feet()), "heading edit advances revision but keeps identity and double position");
            near(h, edited.yaw(), 91, "negative heading wraps through ninety-degree edit"); near(h, edited.pitch(), 31, "editing preserves captured pitch");
            h.assertTrue(f.action("rotate_right", 0) && f.action("save", 0), "inverse explicit rotation saves"); near(h, f.template(0).yaw(), -179, "inverse edit returns exact normalized heading");
            f.same(before, "heading edits"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void replayed_template_revision_and_deleted_uuid_cannot_confirm(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.prepare(); f.caster.stand(f.source); var old = f.request("confirm", 0, null); var original = f.template(0);
            h.assertTrue(f.action("rotate_right", 0) && f.action("save", 0), "new persisted heading revision"); Snapshot before = f.snapshot();
            h.assertFalse(TacticalCoordinateFormation.request(f.p(), old), "old publication and template revision cannot move"); f.same(before, "stale template revision");
            h.assertTrue(f.action("delete", 0) && f.action("save", 0), "delete and explicit recapture create a new slot incarnation");
            h.assertFalse(f.template(0).id().equals(original.id()), "deleted UUID is never reused for replacement");
            h.assertFalse(TacticalCoordinateFormation.request(f.p(), old), "deleted UUID and old menu publication remain inert");
            h.assertTrue(f.p().position().equals(f.source) && f.caster.positions().isEmpty(), "stale requests never transport"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void unsafe_ground_air_fall_hazards_and_nonfinite_facing_never_save(GameTestHelper h) {
        for (int mode = 0; mode < 9; mode++) try (Fixture f = new Fixture(h)) {
            h.assertTrue(f.open(), "controls may be opened before unsafe capture fixture"); Snapshot before = f.snapshot();
            switch (mode) {
                case 0 -> f.p().setOnGround(false);
                case 1 -> f.p().fallDistance = 2;
                case 2 -> f.p().setDeltaMovement(0, -.4, 0);
                case 3 -> f.block(8, 4, 8, Blocks.MAGMA_BLOCK);
                case 4 -> f.block(8, 5, 8, Blocks.WATER);
                case 5 -> f.block(8, 6, 8, Blocks.STONE);
                case 6 -> malformedFacing(f.p(), false, Float.NaN);
                case 7 -> malformedFacing(f.p(), true, Float.POSITIVE_INFINITY);
                default -> { f.p().setShiftKeyDown(true); f.p().setPose(Pose.CROUCHING); }
            }
            h.assertFalse(f.action("save", 0), "unsafe native capture refuses case " + mode);
            h.assertTrue(TacticalCoordinateFormation.templates(f.p()).isEmpty(), "unsafe capture never creates saved destination");
            f.same(before, "unsafe free save"); h.assertTrue(f.caster.positions().isEmpty(), "unsafe capture never repairs feet");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void persisted_malformed_types_duplicates_and_revisions_fail_closed_unchanged(GameTestHelper h) {
        List<Consumer<CompoundTag>> corruptions = List.of(
                tag -> tag.putInt("schema", 2), tag -> tag.putString("generation", "1"), tag -> tag.putLong("generation", -1),
                tag -> tag.putInt("request", 9), tag -> tag.putInt("cooldown", -1), tag -> tag.putInt("cooldown", 61),
                tag -> rows(tag).getCompound(0).putString("x", "8.5"), tag -> rows(tag).getCompound(0).putDouble("x", Double.NaN),
                tag -> rows(tag).getCompound(0).putLong("revision", 0), tag -> rows(tag).getCompound(0).putLong("revision", Long.MAX_VALUE),
                tag -> rows(tag).getCompound(0).putFloat("yaw", 180), tag -> rows(tag).getCompound(0).putFloat("pitch", Float.NaN),
                tag -> rows(tag).add(rows(tag).getCompound(0).copy()),
                tag -> { var second = rows(tag).getCompound(0).copy(); second.putInt("index", 1); rows(tag).add(second); },
                tag -> { var row = rows(tag).getCompound(0).copy(); rows(tag).clear(); for (int i = 0; i < 7; i++) { var copy = row.copy(); copy.putInt("index", i); copy.putUUID("id", UUID.randomUUID()); rows(tag).add(copy); } });
        for (var corrupt : corruptions) try (Fixture f = new Fixture(h)) {
            f.prepare(); CompoundTag bad = f.storage(); corrupt.accept(bad); f.p().getPersistentData().put(DATA, bad); Snapshot before = f.snapshot();
            h.assertTrue(TacticalCoordinateFormation.templates(f.p()) == null, "malformed persisted data is rejected as a whole");
            h.assertFalse(f.action("confirm", 0) || f.action("save", 0), "malformed templates cannot move or overwrite themselves");
            f.same(before, "malformed data denial"); h.assertTrue(f.storage().equals(bad) && f.caster.positions().isEmpty(), "corruption remains unchanged rather than normalized into authority");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void actual_native_save_load_keeps_inert_templates_and_depleted_resources(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.prepare(); f.budget().putDouble("cp", 37); CompoundTag templates = f.storage(); String hp = HighPowerCombat.healthText(f.p()), shield = HighPowerCombat.shieldText(f.p());
            BigDecimal q = HighPowerGrowth.values(f.p()).position(); var old = f.request("confirm", 0, null);
            AbilityStorage.save(f.p()); CompoundTag saved = f.p().saveWithoutId(new CompoundTag()); TacticalCoordinateFormation.remove(f.p());
            f.p().load(saved); AbilityStorage.remove(f.p()); AbilityStorage.get(f.p()); f.caster.stand(f.source); f.caster.epoch();
            h.assertTrue(f.storage().equals(templates) && TacticalCoordinateFormation.templates(f.p()).size() == 1, "actual player NBT load preserves exact inert saved record");
            h.assertTrue(TacticalCoordinateFormation.snapshot(f.p()) == null && TacticalCoordinateFormation.invitation(f.p()) == null, "reload carries no menu or invitation authority");
            h.assertFalse(TacticalCoordinateFormation.request(f.p(), old), "preload publication nonce cannot authorize movement");
            near(h, f.cp(), 37, "reload never refills depleted shared CP");
            h.assertTrue(HighPowerGrowth.values(f.p()).position().compareTo(q) == 0 && HighPowerCombat.healthText(f.p()).equals(hp) && HighPowerCombat.shieldText(f.p()).equals(shield), "reload retains exact growth HP and shield");
            h.assertTrue(f.caster.positions().isEmpty(), "saved template never causes automatic return"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void category_roundtrip_keeps_templates_but_clears_live_authority_without_refill(GameTestHelper h) {
        Fixture f = new Fixture(h); f.prepare(); Actor recipient = f.recipient(); f.invite(recipient); f.budget().putDouble("cp", 37);
        CompoundTag persisted = f.storage(); String hp = HighPowerCombat.healthText(f.p()), shield = HighPowerCombat.shieldText(f.p()); BigDecimal q = HighPowerGrowth.values(f.p()).position();
        var old = f.inviteRequest(recipient, "open_review"); var state = AbilityStorage.get(f.p()); state.changeCategoryClassic(HighPowerCategories.MELTDOWNER); state.changeCategoryClassic(HighPowerCategories.TELEPORTER); f.learn();
        f.post(1, () -> {
            h.assertTrue(rows(f.storage()).equals(rows(persisted)), "category roundtrip retains inert templates");
            h.assertTrue(TacticalCoordinateFormation.invitation(recipient.p) == null && !TacticalCoordinateFormation.request(recipient.p, old), "old category invitation cannot return");
            // Natural native regeneration may proceed on a real tick; compare persistent
            // capacities and health separately rather than mistaking normal CP recovery for refill.
            h.assertTrue(f.cp() < HighPowerGrowth.values(f.p()).cpMaximum() && HighPowerGrowth.values(f.p()).position().compareTo(q) <= 0, "category changes neither fill CP nor raise active growth");
            h.assertTrue(HighPowerCombat.healthText(f.p()).equals(hp) && HighPowerCombat.shieldText(f.p()).equals(shield), "category roundtrip does not heal or refill shield");
        }); f.finish(2);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void exact_native_self_confirm_pays_once_and_keeps_every_nonmovement_pool(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.p().setYRot(73); f.p().setXRot(-31); f.prepare(); f.caster.stand(f.source); f.p().setYRot(-20); f.p().setXRot(17); Snapshot before = f.snapshot(); Body body = Body.of(f.p());
            var request = f.request("confirm", 0, null); h.assertTrue(TacticalCoordinateFormation.request(f.p(), request), "explicit native owner confirmation moves");
            f.success(before, f.caster, f.anchor); h.assertTrue(Body.of(f.p()).equals(body), "movement preserves actual motion/fall/ground/gravity/status/inventory/hurt fields");
            near(h, f.p().getYRot(), 73, "self transport uses captured heading"); near(h, f.p().getXRot(), -31, "self transport preserves approved captured pitch");
            var packets = f.caster.positions(); h.assertValueEqual(packets.size(), 1, "exactly one native position packet"); var packet = packets.getFirst();
            h.assertTrue(new Vec3(packet.getX(), packet.getY(), packet.getZ()).equals(f.anchor) && packet.getRelativeArguments().isEmpty(), "absolute transport packet carries exact destination and facing");
            f.p().connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId())); Snapshot paid = f.snapshot();
            h.assertFalse(TacticalCoordinateFormation.request(f.p(), request), "paid confirmation is nonreplayable"); f.same(paid, "paid confirmation replay");
            h.assertTrue(f.caster.positions().isEmpty() && TacticalCoordinateFormation.templates(f.p()).size() == 1, "ack and duplicate never move again and saved template remains inert"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void native_destination_revalidates_floor_full_body_wall_and_fluid(GameTestHelper h) {
        for (int mode = 0; mode < 8; mode++) try (Fixture f = new Fixture(h)) {
            f.prepare(); f.caster.stand(f.source); Snapshot before = f.snapshot();
            switch (mode) {
                case 0 -> f.block(8, 4, 8, Blocks.AIR);
                case 1 -> f.block(8, 4, 8, Blocks.MAGMA_BLOCK);
                case 2 -> f.block(8, 6, 8, Blocks.STONE);
                case 3 -> f.block(10, 6, 8, Blocks.GLASS_PANE);
                case 4 -> f.block(10, 5, 8, Blocks.WATER);
                case 5 -> f.block(8, 5, 8, Blocks.COBWEB);
                case 6 -> f.block(8, 5, 8, Blocks.POWDER_SNOW);
                default -> f.block(10, 5, 8, Blocks.STONE);
            }
            h.assertFalse(f.action("confirm", 0), "current native route rejects unsafe case " + mode); f.same(before, "unsafe current destination");
            h.assertTrue(f.p().position().equals(f.source) && f.caster.positions().isEmpty(), "old safe template grants no stale movement authority");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void full_native_geometry_accepts_exact_sixty_four_metres_and_rejects_next_double(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.caster.stand(f.absolute(8.5, 5, 8.5)); f.prepare(); Vec3 exact = f.anchor.add(64, 0, 0); f.floor(71, 7); f.hold(new AABB(f.anchor, exact).inflate(2)); f.caster.stand(exact);
            var assessment = SafeLandingProbeWorld.tacticalDestination(f.p(), h.getLevel(), f.anchor);
            h.assertTrue(assessment.valid() && assessment.candidate().destination().equals(f.anchor), "native standing body route accepts exact64m");
            f.caster.stand(new Vec3(Math.nextUp(exact.x), exact.y, exact.z)); Snapshot before = f.snapshot();
            h.assertFalse(f.action("confirm", 0), "one representable native double beyond64m refuses"); f.same(before, "over64m native request");
            h.assertTrue(f.caster.positions().isEmpty(), "overrange confirmation emits no packet");
        }
        try (Fixture f = new Fixture(h)) {
            f.prepare(); Vec3 exact = f.anchor.add(64, 0, 0); f.floor(71, 7); f.hold(new AABB(f.anchor, exact).inflate(2)); f.caster.stand(exact); Snapshot before = f.snapshot();
            h.assertTrue(f.action("confirm", 0), "exact64m owner confirmation succeeds"); f.success(before, f.caster, f.anchor); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void ordinary_recipient_geometry_never_enrolls_and_crouch_cannot_bypass_standing_clearance(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            Actor receiver = f.recipient(); f.caster.stand(f.source); CompoundTag before = receiver.p.getPersistentData().copy();
            var valid = SafeLandingProbeWorld.tacticalDestination(receiver.p, h.getLevel(), f.anchor);
            h.assertTrue(valid.valid(), "ordinary nonenrolled player may have an actual safe destination: " + valid.reason());
            h.assertTrue(receiver.p.getPersistentData().equals(before), "read-only geometry creates no ordinary recipient ledgers");
            receiver.p.setPose(Pose.CROUCHING); receiver.p.setShiftKeyDown(true); f.block(12, 6, 12, Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
            h.assertTrue(h.getLevel().noCollision(receiver.p, receiver.p.getBoundingBox()), "actual crouched body fits below ceiling slab while full standing body does not");
            var denied = SafeLandingProbeWorld.tacticalDestination(receiver.p, h.getLevel(), f.anchor);
            h.assertFalse(denied.valid(), "crouched body under low ceiling cannot bypass full standing source clearance");
            f.noReceiverLedger(receiver); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void invitation_team_membership_is_eligibility_and_never_consent(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); Snapshot before = f.snapshot(); Vec3 source = receiver.p.position(); f.invite(receiver);
            var invitation = TacticalCoordinateFormation.invitation(receiver.p);
            h.assertTrue(invitation != null && invitation.getUUID("caster").equals(f.p().getUUID()) && invitation.getString("caster_name").equals(f.p().getGameProfile().getName()), "incoming invitation identifies actual named caster");
            h.assertTrue(TacticalCoordinateFormation.snapshot(receiver.p) == null && receiver.p.position().equals(source), "invitation never opens review or moves recipient automatically");
            var forged = f.inviteRequest(receiver, "accept"); h.assertFalse(TacticalCoordinateFormation.request(receiver.p, forged), "accept before explicit review refuses");
            h.assertTrue(f.reply(receiver, "open_review"), "recipient explicitly opens exact invitation review");
            f.same(before, "invitation and review"); f.noReceiverLedger(receiver);
            h.assertTrue(receiver.p.position().equals(source) && receiver.positions().isEmpty(), "opening review is not movement consent"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void explicit_review_accept_moves_ordinary_teammate_and_only_sender_pays(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.p().setYRot(-143); f.p().setXRot(29); f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "named teammate explicitly reviews exact destination");
            Snapshot before = f.snapshot(); Body body = Body.of(receiver.p); Vec3 sender = f.p().position(); var accept = f.inviteRequest(receiver, "accept");
            h.assertTrue(TacticalCoordinateFormation.request(receiver.p, accept), "reviewed named teammate explicitly accepts"); f.success(before, receiver, f.anchor);
            h.assertTrue(f.p().position().equals(sender) && Body.of(receiver.p).equals(body), "sender stays put and receiver physical/status/inventory state is preserved");
            boolean senderNotified = false;
            for (Object packet : f.caster.drain()) if (packet instanceof ClientboundCustomPayloadPacket payload && payload.payload() instanceof TacticalCoordinateFormation.ClientData data) {
                CompoundTag view = data.data(); if (view.getString("phase").equals("end") && view.getString("reason").equals("recipient_moved") && !view.getBoolean("moved")) senderNotified = true;
            }
            h.assertTrue(senderNotified, "native sender terminal packet reports recipient success without pretending the sender moved");
            near(h, receiver.p.getYRot(), -143, "recipient receives exact approved yaw"); near(h, receiver.p.getXRot(), 29, "recipient receives captured approved pitch");
            h.assertValueEqual(f.shared(receiver.p).getInt("movement"), 8, "recipient owns shared eight-tick movement fence"); f.noReceiverLedger(receiver);
            Snapshot paid = f.snapshot(); h.assertFalse(TacticalCoordinateFormation.request(receiver.p, accept), "old accepted nonce cannot replay"); f.same(paid, "recipient accept replay");
            h.assertTrue(TacticalCoordinateFormation.invitation(receiver.p) == null && receiver.positions().size() == 1, "accepted invitation is retired after one actual native packet"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void missing_different_and_changed_team_refuse_without_consent_or_payment(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); Snapshot before = f.snapshot(); var board = h.getLevel().getScoreboard();
            if (mode < 2) { board.removePlayerFromTeam(receiver.p.getScoreboardName(), f.team); if (mode == 1) board.addPlayerToTeam(receiver.p.getScoreboardName(), f.otherTeam());
                h.assertFalse(f.action("invite", 0, receiver.p.getUUID()), "nonteammate or teamless recipient cannot be invited"); }
            else { f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "same-team review before changed membership"); board.removePlayerFromTeam(receiver.p.getScoreboardName(), f.team);
                h.assertFalse(f.reply(receiver, "accept"), "team membership change after review revokes invitation"); }
            f.same(before, "team eligibility refusal"); f.noReceiverLedger(receiver); h.assertTrue(receiver.positions().isEmpty(), "team refusal never moves recipient");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void wrong_receiver_nonce_template_and_review_sequence_cannot_accept(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(), stranger = f.actor(f.absolute(16.5, 5, 12.5), false); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "legitimate review");
            var good = f.inviteRequest(receiver, "accept"); Snapshot before = f.snapshot();
            h.assertFalse(TacticalCoordinateFormation.request(stranger.p, good), "another resident connection cannot use recipient capability");
            var wrongNonce = new TacticalCoordinateFormation.Request(good.wire(), good.token(), UUID.randomUUID(), good.sequence(), good.action(), good.index(), good.templateId(), good.revision(), good.recipient(), good.actor(), good.owner());
            var wrongTemplate = new TacticalCoordinateFormation.Request(good.wire(), good.token(), good.nonce(), good.sequence(), good.action(), good.index(), UUID.randomUUID(), good.revision(), good.recipient(), good.actor(), good.owner());
            var wrongRevision = new TacticalCoordinateFormation.Request(good.wire(), good.token(), good.nonce(), good.sequence(), good.action(), good.index(), good.templateId(), good.revision() + 1, good.recipient(), good.actor(), good.owner());
            var wrongSequence = new TacticalCoordinateFormation.Request(good.wire(), good.token(), good.nonce(), good.sequence() - 1, good.action(), good.index(), good.templateId(), good.revision(), good.recipient(), good.actor(), good.owner());
            h.assertFalse(TacticalCoordinateFormation.request(receiver.p, wrongNonce) || TacticalCoordinateFormation.request(receiver.p, wrongTemplate) || TacticalCoordinateFormation.request(receiver.p, wrongRevision) || TacticalCoordinateFormation.request(receiver.p, wrongSequence), "forged or stale exact review bindings all refuse");
            f.same(before, "forged accept"); f.noReceiverLedger(receiver); h.assertTrue(receiver.positions().isEmpty(), "forgery emits no transport"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void one_outgoing_invitation_cannot_fan_out_after_request_fence_expires(GameTestHelper h) {
        Fixture f = new Fixture(h); f.prepare(); Actor first = f.recipient(), second = f.actor(f.absolute(16.5, 5, 12.5), false); f.team(second); f.invite(first);
        f.post(8, () -> {
            h.assertValueEqual(f.storage().getInt("request"), 0, "eight actual ticks expire request throttle before testing outgoing exclusivity"); Snapshot before = f.snapshot();
            h.assertFalse(f.action("invite", 0, second.p.getUUID()), "same sender cannot create second concurrent outgoing invitation after request throttle expires");
            h.assertTrue(TacticalCoordinateFormation.invitation(second.p) == null && TacticalCoordinateFormation.invitation(first.p) != null, "original named recipient retains only original invitation");
            f.same(before, "true outgoing exclusivity denial"); f.noReceiverLedger(first); f.noReceiverLedger(second);
        }); f.finish(9);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void native_heartbeat_gap_accepts_last_reviewed_action_once_and_never_replays_edits(GameTestHelper h) {
        Fixture f = new Fixture(h); f.prepare(); TacticalCoordinateFormation.Request rotate = f.request("rotate_right", 0, null), save = f.request("save", 0, null), select = f.request("select", 0, null), close = f.request("close", 0, null);
        var original = f.template(0); Snapshot before = f.snapshot();
        f.post(10, () -> {
            h.assertTrue(TacticalCoordinateFormation.snapshot(f.p()).getLong("sequence") > rotate.sequence(), "genuine ten-tick heartbeat publishes a newer sequence");
            h.assertTrue(TacticalCoordinateFormation.request(f.p(), rotate), "still-current action shown before heartbeat remains eligible once");
            h.assertFalse(TacticalCoordinateFormation.request(f.p(), rotate), "same pre-action rotation cannot replay after accepted edit");
            h.assertFalse(TacticalCoordinateFormation.request(f.p(), save) || TacticalCoordinateFormation.request(f.p(), select), "old save and select are invalid after an action publication advances the floor");
            h.assertTrue(f.template(0).equals(original), "stale save does not persist an unreviewed draft or advance template revision");
            h.assertTrue(f.action("save", 0), "newly published reviewed draft may be saved explicitly"); near(h, f.template(0).yaw(), 90, "only one ninety-degree draft edit is saved");
            h.assertTrue(TacticalCoordinateFormation.request(f.p(), close), "authenticated close stays available across harmless publication races");
            f.same(before, "heartbeat and explicit heading edit"); h.assertTrue(f.caster.positions().isEmpty(), "heartbeat and edits never move automatically");
        }); f.finish(11);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void declared_valid_active_growth_change_cannot_reprice_reviewed_invitation(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            // Deliberate valid progression fixture, not a survival-acquisition claim:
            // earned capacity and native unlock remain LV5; only active q is lowered.
            CompoundTag active = f.p().getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound("activeGrowth");
            active.putString("position", "2"); active.putBoolean("initial", false); HighPowerGrowth.invalidate(f.p());
            h.assertTrue(HighPowerGrowth.values(f.p()).position().compareTo(BigDecimal.valueOf(2)) == 0 && HighPowerResources.effectiveLevel(f.p()) == 5, "declared q2 fixture retains genuine native LV5 unlock and earned capacity");
            f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); var invitation = TacticalCoordinateFormation.invitation(receiver.p);
            near(h, invitation.getDouble("cost"), 4, "invitation publishes frozen q2 price"); h.assertValueEqual(invitation.getInt("cooldown"), 30, "invitation publishes frozen q2 cooldown");
            h.assertTrue(f.reply(receiver, "open_review"), "recipient explicitly reviews original price and destination");
            active.putString("position", "4"); HighPowerGrowth.invalidate(f.p()); h.assertTrue(HighPowerGrowth.values(f.p()).position().compareTo(BigDecimal.valueOf(4)) == 0, "declared valid current q4 differs from quoted q2");
            Snapshot before = f.snapshot(); h.assertTrue(f.reply(receiver, "accept"), "exact previously reviewed invitation remains accepted at its original price");
            near(h, f.cp(), before.cp() - 4, "sender pays frozen invitation price once despite changed current q");
            h.assertTrue(f.budget().getLong("next_" + ID) == f.p().serverLevel().getGameTime() + 30 && receiver.p.position().equals(f.anchor), "frozen cooldown and exact approved destination both commit");
            h.assertTrue(HighPowerGrowth.values(f.p()).position().compareTo(BigDecimal.valueOf(4)) == 0 && TacticalCoordinateFormation.receipt(f.p()).paid(), "353 does not restore old growth or refund payment"); f.noReceiverLedger(receiver); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void reject_and_sender_cancel_are_free_and_old_nonce_stays_dead(GameTestHelper h) {
        for (boolean reject : new boolean[]{true, false}) try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "review before free retirement"); var old = f.inviteRequest(receiver, "accept"); Snapshot before = f.snapshot();
            if (reject) h.assertTrue(f.reply(receiver, "reject"), "explicit recipient rejection"); else h.assertTrue(f.action("close", 0), "sender cancel closes outbound authority");
            h.assertTrue(TacticalCoordinateFormation.invitation(receiver.p) == null && !TacticalCoordinateFormation.request(receiver.p, old), "cancelled invitation and exact old nonce remain dead");
            f.same(before, "reject or cancel"); h.assertTrue(receiver.positions().isEmpty(), "free refusal never moves"); f.noReceiverLedger(receiver);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=250)
    public static void invitation_expires_on_its_real_tick_boundary_and_direct_calls_cannot_age_it(GameTestHelper h) {
        Fixture f = new Fixture(h); f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "explicit review before expiry"); var old = f.inviteRequest(receiver, "accept");
        for (int i = 0; i < 500; i++) TacticalCoordinateFormation.tick(f.p());
        h.assertTrue(TacticalCoordinateFormation.invitation(receiver.p) != null, "same-tick calls do not spend real invitation lifetime");
        f.post(TacticalCoordinateRules.INVITE_TICKS - 1, () -> h.assertTrue(TacticalCoordinateFormation.invitation(receiver.p) != null, "invitation remains live through its last genuine tick"));
        f.post(TacticalCoordinateRules.INVITE_TICKS, () -> { Snapshot before = f.snapshot(); h.assertTrue(TacticalCoordinateFormation.invitation(receiver.p) == null, "exact native boundary retires invitation");
            h.assertFalse(TacticalCoordinateFormation.request(receiver.p, old), "expired reviewed nonce cannot move"); f.same(before, "expired accept"); f.noReceiverLedger(receiver);
            h.assertTrue(receiver.positions().isEmpty(), "expiry never automatically transports"); }); f.finish(TacticalCoordinateRules.INVITE_TICKS + 1);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void either_original_connection_change_revokes_reviewed_invitation(GameTestHelper h) {
        for (boolean sender : new boolean[]{true, false}) try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "review before connection replacement"); var old = f.inviteRequest(receiver, "accept"); Snapshot before = f.snapshot();
            Actor changed = sender ? f.caster : receiver; changed.p.connection = new ServerGamePacketListenerImpl(changed.p.server, changed.connection, changed.p, changed.cookie);
            h.assertFalse(TacticalCoordinateFormation.request(receiver.p, old), "either replacement native listener invalidates original connection capability");
            f.same(before, "connection replacement"); f.noReceiverLedger(receiver); h.assertTrue(receiver.positions().isEmpty(), "reconnected identity does not inherit prior consent");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void native_logout_and_death_events_retire_invites_without_erasing_templates(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "review before lifecycle event"); var old = f.inviteRequest(receiver, "accept"); Snapshot before = f.snapshot(); ListTag saved = rows(f.storage()).copy();
            ServerPlayer changed = mode < 2 ? f.p() : receiver.p;
            if ((mode & 1) == 0) NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(changed)); else NeoForge.EVENT_BUS.post(new LivingDeathEvent(changed, changed.damageSources().generic()));
            h.assertTrue(TacticalCoordinateFormation.invitation(receiver.p) == null && !TacticalCoordinateFormation.request(receiver.p, old), "native lifecycle bus clears both participants' old invitation");
            h.assertTrue(rows(f.storage()).equals(saved), "native lifecycle leaves saved templates inert"); f.same(before, "lifecycle retirement"); f.noReceiverLedger(receiver);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void cancelled_or_redirected_native_event_is_free_and_preserves_callback_changes(GameTestHelper h) {
        for (boolean redirect : new boolean[]{false, true}) try (Fixture f = new Fixture(h)) {
            f.prepare(); f.caster.stand(f.source); Snapshot before = f.snapshot(); TeleportHook hook = new TeleportHook(f.p(), event -> {
                near(h, f.cp(), before.cp(), "native event occurs before sender payment");
                f.p().getPersistentData().putInt("academy:tactical_callback_sentinel", 7);
                if (redirect) event.setTargetX(event.getTargetX() + .1); else event.setCanceled(true);
            });
            NeoForge.EVENT_BUS.register(hook); try { h.assertFalse(f.action("confirm", 0), "native cancelled or redirected movement refuses"); }
            finally { NeoForge.EVENT_BUS.unregister(hook); }
            f.same(before, "native event refusal"); h.assertTrue(hook.calls == 1 && f.caster.positions().isEmpty() && f.p().getPersistentData().getInt("academy:tactical_callback_sentinel") == 7, "one real native callback, no movement or rollback of unrelated callback state");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void participant_mutations_during_native_callback_refuse_before_sender_payment(GameTestHelper h) {
        for (int mode = 0; mode < 8; mode++) try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "review before hostile native callback"); Snapshot before = f.snapshot(); int mutation = mode;
            TeleportHook hook = new TeleportHook(receiver.p, event -> {
                switch (mutation) {
                    case 0 -> receiver.p.setPos(receiver.p.position().add(.1, 0, 0));
                    case 1 -> f.p().setPos(f.p().position().add(.1, 0, 0));
                    case 2 -> receiver.p.setPose(Pose.CROUCHING);
                    case 3 -> receiver.p.setDeltaMovement(.1, -.0784, 0);
                    case 4 -> receiver.p.fallDistance = 2;
                    case 5 -> h.getLevel().getScoreboard().removePlayerFromTeam(receiver.p.getScoreboardName(), f.team);
                    case 6 -> f.block(10, 6, 10, Blocks.STONE);
                    default -> receiver.p.connection = new ServerGamePacketListenerImpl(receiver.p.server, receiver.connection, receiver.p, receiver.cookie);
                }
            }); NeoForge.EVENT_BUS.register(hook);
            try { h.assertFalse(f.reply(receiver, "accept"), "native callback mutation denies movement case " + mode); }
            finally { NeoForge.EVENT_BUS.unregister(hook); }
            f.same(before, "mutated participant or route"); f.noReceiverLedger(receiver); h.assertTrue(receiver.positions().isEmpty(), "changed callback state never reaches native transport");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void reentry_locks_both_actual_participants_before_event_and_cannot_double_pay(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "review before event reentry"); Snapshot before = f.snapshot(); var accept = f.inviteRequest(receiver, "accept"); boolean[] nested = {true, true};
            TeleportHook hook = new TeleportHook(receiver.p, event -> {
                h.assertTrue(TacticalCoordinateFormation.committing(f.p()) && TacticalCoordinateFormation.committing(receiver.p), "sender and actual recipient are both locked throughout native callback");
                nested[0] = TacticalCoordinateFormation.request(receiver.p, accept); nested[1] = f.action("confirm", 0);
            }); NeoForge.EVENT_BUS.register(hook);
            try { TacticalCoordinateFormation.request(receiver.p, accept); }
            finally { NeoForge.EVENT_BUS.unregister(hook); }
            h.assertTrue(!nested[0] && !nested[1] && hook.calls == 1, "neither participant can recursively create a second native event");
            var receipt = TacticalCoordinateFormation.receipt(f.p());
            if (receipt != null && receipt.paid()) f.success(before, receiver, f.anchor); else f.same(before, "reentry fail-closed outer attempt");
            h.assertTrue(receiver.positions().size() <= 1 && !TacticalCoordinateFormation.committing(f.p()) && !TacticalCoordinateFormation.committing(receiver.p), "one possible native move and both locks release after transaction"); f.noReceiverLedger(receiver); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void callback_ledger_replacement_or_resource_change_cannot_buy_with_stale_price(GameTestHelper h) {
        for (boolean replace : new boolean[]{false, true}) try (Fixture f = new Fixture(h)) {
            f.prepare(); f.caster.stand(f.source); Snapshot before = f.snapshot(); TeleportHook hook = new TeleportHook(f.p(), event -> {
                if (replace) f.p().getPersistentData().put(HighPowerResources.DATA_KEY, f.budget().copy()); else f.budget().putDouble("cp", f.cp() - 1);
            }); NeoForge.EVENT_BUS.register(hook);
            try { h.assertFalse(f.action("confirm", 0), "native resource mutation cannot pass stale sender reservation"); }
            finally { NeoForge.EVENT_BUS.unregister(hook); }
            near(h, f.cp(), before.cp() - (replace ? 0 : 1), "foreign resource edit remains without own353 debit or rollback");
            h.assertTrue(f.budget().getLong("next_" + ID) == before.deadline() && f.caster.positions().isEmpty(), "resource mutation writes no353 deadline or movement");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void exception_before_or_after_native_transport_is_paid_terminal_and_nonreplayable(GameTestHelper h) {
        for (boolean after : new boolean[]{false, true}) try (Fixture f = new Fixture(h)) {
            f.p().connection = new ServerGamePacketListenerImpl(f.p().server, f.caster.connection, f.p(), f.caster.cookie) {
                @Override public void teleport(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> relative) {
                    if (after) super.teleport(x, y, z, yaw, pitch, relative);
                    throw new IllegalStateException("Declared NEW353 native transport fault " + (after ? "after" : "before") + " move");
                }
            }; f.caster.epoch(); f.prepare(); f.caster.stand(f.source); var request = f.request("confirm", 0, null); Snapshot before = f.snapshot(); TacticalCoordinateFormation.request(f.p(), request);
            var receipt = TacticalCoordinateFormation.receipt(f.p()); h.assertTrue(receipt != null && receipt.paid() && receipt.moved() == after, "uncertain native transport reports paid and only observed movement facts");
            near(h, f.cp(), before.cp() - TacticalCoordinateRules.cpCost(before.q()), "native transport fault never refunds committed sender payment");
            h.assertTrue(f.p().position().equals(after ? f.anchor : f.source) && f.shared(f.p()).getInt("movement") == 8, "paid uncertainty retains observed source and physical fence");
            Snapshot paid = f.snapshot(); h.assertFalse(TacticalCoordinateFormation.request(f.p(), request), "unknown paid completion cannot retry original request"); f.same(paid, "unknown paid replay");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void shared_eight_tick_fence_blocks_new353_but_ordinary_native_movement_keeps_working(GameTestHelper h) {
        Fixture f = new Fixture(h); f.prepare(); f.caster.stand(f.source); h.assertTrue(f.action("confirm", 0), "first native self relocation");
        h.assertValueEqual(f.shared(f.p()).getInt("movement"), 8, "actual movement starts shared eight-tick fence");
        Vec3 old = f.p().position(); f.p().move(MoverType.SELF, new Vec3(.1, 0, 0)); h.assertTrue(f.p().position().x > old.x, "movement fence never freezes ordinary native walking");
        f.p().jumpFromGround(); h.assertTrue(f.p().getDeltaMovement().y > 0, "movement fence never intercepts ordinary native jump"); f.caster.stand(f.source);
        f.post(1, () -> { h.assertValueEqual(f.shared(f.p()).getInt("movement"), 7, "one real server tick ages shared fence once"); f.open(); Snapshot before = f.snapshot(); h.assertFalse(f.action("confirm", 0), "new353 cannot cross prior physical fence after economic cooldown"); f.same(before, "shared fence denial"); });
        f.post(8, () -> { h.assertValueEqual(f.shared(f.p()).getInt("movement"), 0, "eighth genuine tick expires shared movement fence"); f.caster.stand(f.source); if (TacticalCoordinateFormation.snapshot(f.p()) == null) h.assertTrue(f.open(), "fresh control after8ticks");
            h.assertTrue(f.action("confirm", 0), "native353 can move after exact eight-tick boundary"); h.assertValueEqual(f.shared(f.p()).getInt("movement"), 8, "next admitted movement writes same shared fence"); }); f.finish(9);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void native_unloaded_guard_never_loads_chunk_or_creates_recipient_ledgers(GameTestHelper h) {
        Vec3 remote = new Vec3(25_000_000.5, 90, 25_000_000.5); ChunkPos chunk = new ChunkPos(BlockPos.containing(remote)); var server = h.getLevel().getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "TCUnloaded"), false); var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection); p.connection = new ServerGamePacketListenerImpl(server, connection, p, cookie);
        try { h.assertTrue(!p.isAddedToLevel() && h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null, "guard-only actual native player begins outside loaded chunks");
            p.setPose(Pose.STANDING); p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.setPos(remote); p.connection.resetPosition(); CompoundTag before = p.getPersistentData().copy();
            var capture = SafeLandingProbeWorld.tacticalCapture(p); var destination = SafeLandingProbeWorld.tacticalDestination(p, h.getLevel(), remote.add(1, 0, 0));
            h.assertTrue(!capture.valid() && !destination.valid(), "native unloaded source refuses capture and transport candidate");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null && p.getPersistentData().equals(before) && p.position().equals(remote), "read-only rejection performs no chunk load, enrollment or corrective movement"); h.succeed();
        } finally { p.discard(); channel.finishAndReleaseAll(); }
    }

    @GameTest(template=TEMPLATE, batch="highpower_tactical_coordinate_world", timeoutTicks=10)
    public static void changed_native_world_border_revokes_old_safe_template(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.prepare(); f.caster.stand(f.source); Snapshot before = f.snapshot(); var border = h.getLevel().getWorldBorder();
            double x = border.getCenterX(), z = border.getCenterZ(), size = border.getSize();
            try {
                border.setCenter(f.source.x, f.source.z); border.setSize(5);
                h.assertFalse(f.action("confirm", 0), "current native border rejects formerly valid saved feet");
                f.same(before, "changed native border"); h.assertTrue(f.caster.positions().isEmpty(), "border denial never transports");
            } finally { border.setCenter(x, z); border.setSize(size); }
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch="highpower_tactical_coordinate_world", timeoutTicks=10)
    public static void recipient_native_world_change_after_review_cannot_move_in_old_world(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "review before native dimension transition");
            var old = f.inviteRequest(receiver, "accept"); Snapshot before = f.snapshot(); var other = f.p().server.getLevel(Level.NETHER); h.assertTrue(other != null, "native other world exists");
            h.assertFalse(SafeLandingProbeWorld.tacticalDestination(receiver.p, other, f.anchor).valid(), "world helper never treats another native world as same-world candidate");
            receiver.p.teleportTo(other, receiver.p.getX(), receiver.p.getY(), receiver.p.getZ(), Set.of(), 0, 0); receiver.drain();
            h.assertFalse(TacticalCoordinateFormation.request(receiver.p, old), "old reviewed invitation cannot act after actual world change");
            f.same(before, "recipient world change"); f.noReceiverLedger(receiver); h.assertTrue(receiver.p.serverLevel() == other && receiver.positions().isEmpty(), "foreign native world change is retained without corrective353 return"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void both_participants_must_still_be_within_sixty_four_metres_on_accept(GameTestHelper h) {
        for (boolean sender : new boolean[]{true, false}) try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "review before either participant leaves64m budget");
            f.floor(73, 7); Actor moved = sender ? f.caster : receiver; moved.stand(f.absolute(74.5, 5, 8.5)); Snapshot before = f.snapshot();
            h.assertFalse(f.reply(receiver, "accept"), "moved " + (sender ? "sender" : "recipient") + " beyond destination budget revokes acceptance");
            f.same(before, "either actor overrange"); f.noReceiverLedger(receiver); h.assertTrue(receiver.positions().isEmpty(), "overrange accept never moves");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void native_source_candidate_revalidation_binds_original_body_and_connection(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h)) {
            Actor receiver = f.recipient(); f.caster.stand(f.source); var candidate = SafeLandingProbeWorld.tacticalDestination(receiver.p, h.getLevel(), f.anchor);
            h.assertTrue(candidate.valid(), "ordinary native recipient obtains source-bound candidate before mutation: " + candidate.reason()); CompoundTag data = receiver.p.getPersistentData().copy();
            switch (mode) {
                case 0 -> receiver.p.setPos(receiver.p.position().add(.01, 0, 0));
                case 1 -> receiver.p.setPose(Pose.CROUCHING);
                case 2 -> receiver.p.setYRot(37);
                default -> receiver.p.connection = new ServerGamePacketListenerImpl(receiver.p.server, receiver.connection, receiver.p, receiver.cookie);
            }
            h.assertFalse(SafeLandingProbeWorld.tacticalRevalidate(receiver.p, candidate.candidate()).valid(), "native candidate refuses changed body/position/facing/connection case " + mode);
            h.assertTrue(receiver.p.getPersistentData().equals(data) && receiver.positions().isEmpty(), "read-only candidate revalidation has no persistent or transport effects"); f.noReceiverLedger(receiver);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void receiver_existing_shared_fence_refuses_accept_without_any_receiver_resource_ledger(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "review before intervening ordinary recipient movement fence");
            CompoundTag shared = new CompoundTag(); shared.putInt("version", 1); shared.putInt("preview", 0); shared.putInt("movement", 8); shared.putInt("cooldown", 0); shared.putInt("mastery", 0);
            receiver.p.getPersistentData().put(SHARED, shared); Snapshot before = f.snapshot();
            h.assertFalse(f.reply(receiver, "accept"), "recipient's current shared313 fence wins before sender payment");
            f.same(before, "receiver physical cooldown"); f.noReceiverLedger(receiver); h.assertTrue(f.shared(receiver.p).equals(shared) && receiver.positions().isEmpty(), "denial preserves existing recipient fence without normalizing or moving"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void native_resource_callback_reentry_from_either_actor_refuses_before_payment(GameTestHelper h) {
        for (boolean sender : new boolean[]{true, false}) try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "review before actual native resource callback");
            ServerPlayer owner = sender ? f.p() : receiver.p; var accept = f.inviteRequest(receiver, "accept"); Snapshot before = f.snapshot(); boolean[] nested = {true}; int[] calls = {0};
            Object hook = new Object() { @SubscribeEvent public void perform(cn.academy.port.api.SkillPerformEvent event) {
                if (event.player == owner && event.skill.equals("tactical_fixture_resource_callback")) { calls[0]++; nested[0] = TacticalCoordinateFormation.request(receiver.p, accept); }
            } };
            NeoForge.EVENT_BUS.register(hook);
            try { AbilityStorage.get(owner).consumeWithForceSkill("tactical_fixture_resource_callback", 0, 0, false); }
            finally { NeoForge.EVENT_BUS.unregister(hook); }
            h.assertTrue(calls[0] == 1 && !nested[0], "genuine SkillPerform callback cannot accept353 while either actor consumes resources");
            f.same(before, "native resource callback denial"); f.noReceiverLedger(receiver); h.assertTrue(receiver.positions().isEmpty(), "busy resource callback reaches no native transport");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void ordinary_native_regeneration_while_review_waits_remains_eligible_at_frozen_quote(GameTestHelper h) {
        Fixture f = new Fixture(h); f.prepare(); Actor receiver = f.recipient(); f.budget().putDouble("cp", 1); f.invite(receiver);
        h.assertTrue(f.reply(receiver, "open_review"), "recipient reviews before ordinary waiting regeneration"); double quoted = TacticalCoordinateFormation.invitation(receiver.p).getDouble("cost");
        f.post(2, () -> {
            h.assertTrue(f.cp() > 1 && f.cp() < HighPowerGrowth.values(f.p()).cpMaximum(), "genuine native ticks recover depleted CP normally while invitation waits");
            Snapshot before = f.snapshot(); h.assertTrue(f.reply(receiver, "accept"), "waiting resource regeneration does not invalidate exact reviewed invitation");
            f.success(before, receiver, f.anchor); near(h, before.cp() - f.cp(), quoted, "sender pays original quoted cost against current regenerated balance"); f.noReceiverLedger(receiver);
        }); f.finish(3);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void synchronous_native_inventory_health_and_status_mutations_of_either_actor_refuse_free(GameTestHelper h) {
        for (boolean sender : new boolean[]{true, false}) for (int mode = 0; mode < 10; mode++) try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "review before synchronous native participant-state callback");
            ServerPlayer changed = sender ? f.p() : receiver.p; int mutation = mode;
            if (mode == 8) changed.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(4);
            double cp = f.cp(); long deadline = f.budget().getLong("next_" + ID); Body[] afterCallback = {null}; int[] selected = {-1};
            TeleportHook hook = new TeleportHook(receiver.p, event -> {
                switch (mutation) {
                    case 0 -> changed.getInventory().getItem(0).grow(1);
                    case 1 -> changed.containerMenu.setCarried(new ItemStack(Items.STICK, 2));
                    case 2 -> changed.getInventory().selected = 1;
                    case 3 -> changed.hurtTime = 7;
                    case 4 -> changed.invulnerableTime = 11;
                    case 5 -> changed.setRemainingFireTicks(123);
                    case 6 -> changed.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 91));
                    case 7 -> changed.setNoGravity(true);
                    case 8 -> { changed.setAbsorptionAmount(1); near(h, changed.getAbsorptionAmount(), 1, "native callback actually changes absorption within declared fixture capacity"); }
                    default -> changed.setHealth(Math.max(1, changed.getHealth() - 1));
                }
                afterCallback[0] = Body.of(changed); selected[0] = changed.getInventory().selected;
            }); NeoForge.EVENT_BUS.register(hook);
            try { h.assertFalse(f.reply(receiver, "accept"), "changed " + (sender ? "sender" : "recipient") + " state refuses case " + mode); }
            finally { NeoForge.EVENT_BUS.unregister(hook); }
            h.assertTrue(hook.calls == 1 && afterCallback[0] != null && Body.of(changed).equals(afterCallback[0]) && changed.getInventory().selected == selected[0], "one actual callback retains its own inventory/body/status mutation without snapshot rollback");
            near(h, f.cp(), cp, "synchronous participant mutation adds no353 CP debit"); h.assertTrue(f.budget().getLong("next_" + ID) == deadline && receiver.positions().isEmpty(), "changed native participant writes no353 deadline or transport"); f.noReceiverLedger(receiver);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void synchronous_caster_combat_ledger_replacement_health_and_shield_changes_refuse_free(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h)) {
            f.prepare(); Actor receiver = f.recipient(); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "review before synchronous exact combat-ledger callback");
            int mutation = mode; double cp = f.cp(); long deadline = f.budget().getLong("next_" + ID); CompoundTag[] afterCallback = {null};
            TeleportHook hook = new TeleportHook(receiver.p, event -> {
                var ledger = f.p().getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
                if (mutation == 0) f.p().getPersistentData().put(HighPowerCombat.DATA_KEY, ledger.copy());
                else { String key = mutation == 1 ? "health" : "shield"; ledger.putString(key, new BigDecimal(ledger.getString(key)).subtract(BigDecimal.ONE).toPlainString()); }
                afterCallback[0] = f.p().getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();
            }); NeoForge.EVENT_BUS.register(hook);
            try { h.assertFalse(f.reply(receiver, "accept"), "combat-ledger identity or balance mutation refuses case " + mode); }
            finally { NeoForge.EVENT_BUS.unregister(hook); }
            h.assertTrue(hook.calls == 1 && f.p().getPersistentData().getCompound(HighPowerCombat.DATA_KEY).equals(afterCallback[0]), "exact foreign combat-ledger change remains without restore or overwrite");
            near(h, f.cp(), cp, "combat-ledger mutation adds no353 debit"); h.assertTrue(f.budget().getLong("next_" + ID) == deadline && receiver.positions().isEmpty(), "combat-ledger mutation buys no native move or cooldown"); f.noReceiverLedger(receiver);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=75)
    public static void actual_native_injury_and_attached_status_receive_no_invitation_heal_or_fall_immunity(GameTestHelper h) {
        Fixture f = new Fixture(h); f.prepare(); Actor receiver = f.recipient();
        f.post(65, () -> {
            h.assertTrue(receiver.p.hurt(receiver.p.damageSources().generic(), 4), "ordinary recipient actual native login protection expires before injury"); receiver.stand(f.absolute(12.5, 5, 12.5));
            receiver.p.setRemainingFireTicks(80); receiver.p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140));
            // Refresh owner publication after normal clock aging; the original inert template remains.
            h.assertTrue(f.open(), "fresh owner controls after ordinary real ticks"); f.invite(receiver); h.assertTrue(f.reply(receiver, "open_review"), "injured recipient explicitly reviews");
            Body before = Body.of(receiver.p); Snapshot payer = f.snapshot(); h.assertTrue(f.reply(receiver, "accept"), "injured grounded recipient explicitly accepts"); f.success(payer, receiver, f.anchor);
            h.assertTrue(Body.of(receiver.p).equals(before) && receiver.p.isOnFire() && receiver.p.hasEffect(MobEffects.WEAKNESS), "native transport preserves injury hurtframes fire status motion and inventory exactly");
            float hp = receiver.p.getHealth(); receiver.p.setOnGround(false); receiver.p.fallDistance = 8;
            h.assertTrue(receiver.p.causeFallDamage(8, 1, receiver.p.damageSources().fall()) && receiver.p.getHealth() < hp, "actual subsequent native fall still damages ordinary recipient");
            f.noReceiverLedger(receiver);
        }); f.finish(66);
    }

    private record Snapshot(double cp, long deadline, int cooldown, double mastery, double levelXp, BigDecimal q, String hp, String shield, double overload, List<String> inventory) {}
    private record Body(Vec3 motion, float fall, boolean ground, boolean noGravity, boolean flying, int hurt, int invulnerable, int fire, float health, float absorption, Map<Object, String> effects, List<String> inventory) {
        static Body of(ServerPlayer p) { Map<Object, String> effects = new HashMap<>(); p.getActiveEffectsMap().forEach((key, effect) -> effects.put(key, effect.getAmplifier() + ":" + effect.getDuration() + ":" + effect.isAmbient() + ":" + effect.isVisible()));
            return new Body(p.getDeltaMovement(), p.fallDistance, p.onGround(), p.isNoGravity(), p.getAbilities().flying, p.hurtTime, p.invulnerableTime, p.getRemainingFireTicks(), p.getHealth(), p.getAbsorptionAmount(), Map.copyOf(effects), AcademyTacticalCoordinateFormationRuntimeTests.inventory(p)); }
    }
    private static List<String> inventory(ServerPlayer p) { List<String> result = new ArrayList<>(); for (int i = 0; i < p.getInventory().getContainerSize(); i++) result.add(p.getInventory().getItem(i).toString()); result.add(p.containerMenu.getCarried().toString()); return List.copyOf(result); }
    private static ListTag rows(CompoundTag data) { return data.getList("templates", Tag.TAG_COMPOUND); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " != " + expected); }
    private static void malformedFacing(ServerPlayer p, boolean pitch, float value) { try { Field field = Entity.class.getDeclaredField(pitch ? "xRot" : "yRot"); field.setAccessible(true); field.setFloat(p, value); } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); } }
    public static final class TeleportHook {
        final ServerPlayer owner; final Consumer<EntityTeleportEvent> action; int calls;
        TeleportHook(ServerPlayer owner, Consumer<EntityTeleportEvent> action) { this.owner = owner; this.action = action; }
        @SubscribeEvent public void teleport(EntityTeleportEvent event) { if (event.getEntity() == owner) { calls++; action.accept(event); } }
    }
    public static final class PostCheck {
        final Fixture f; final int tick; final Runnable action; boolean ran;
        PostCheck(Fixture f, int delay, Runnable action) { this.f = f; tick = f.p().server.getTickCount() + delay; this.action = action; }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post event) { if (ran || event.getServer() != f.p().server || event.getServer().getTickCount() != tick) return; ran = true;
            try { action.run(); } catch (Throwable failure) { if (f.failure == null) f.failure = failure; } }
    }
    private static final class Actor implements AutoCloseable {
        final Fixture fixture; final ServerPlayer p; final Connection connection; final CommonListenerCookie cookie; final EmbeddedChannel channel; long inputs;
        Actor(Fixture f, Vec3 at, boolean enrolled) {
            fixture = f; var server = f.h.getLevel().getServer(); cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "TC" + UUID.randomUUID().toString().substring(0, 10)), false);
            p = new ServerPlayer(server, f.h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            for (var packet : positions()) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
            p.setGameMode(GameType.SURVIVAL); p.getAbilities().invulnerable = false; p.getAbilities().instabuild = false; p.setNoGravity(false); stand(at);
            if (enrolled) f.h.assertTrue(HighPowerCombat.enroll(p, 5), "declared first native LV5 enrollment: " + HighPowerCombat.unsupportedReason(p));
            p.getInventory().setItem(0, new ItemStack(Items.APPLE, 3)); p.getInventory().setItem(1, new ItemStack(Items.ARROW, 7));
            f.hook(new HighPowerPhaseSidestepRuntimeTests.NativePlayerPump(p, f.h.testInfo.getTimeoutTicks() + 5)); drain();
        }
        void stand(Vec3 at) { p.setPos(at); p.setPose(Pose.STANDING); p.setShiftKeyDown(false); p.setNoGravity(false); p.fallDistance = 0; p.setOnGround(true); p.move(MoverType.SELF, new Vec3(0, -.0784, 0)); p.setDeltaMovement(0, -.0784, 0); p.connection.resetPosition(); fixture.h.assertTrue(p.onGround() && p.position().equals(at), "native floor collision establishes genuine safe feet"); }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(long input) { return new HighPowerInput.Input(0, input, p.getId(), epoch()).wire(); }
        List<Object> drain() { List<Object> result = new ArrayList<>(); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet; while ((packet = channel.readOutbound()) != null) { result.add(packet); ReferenceCountUtil.release(packet); } return result; }
        List<ClientboundPlayerPositionPacket> positions() { List<ClientboundPlayerPositionPacket> result = new ArrayList<>(); for (Object packet : drain()) if (packet instanceof ClientboundPlayerPositionPacket position) result.add(position); return result; }
        @Override public void close() { TacticalCoordinateFormation.remove(p); SafeLandingProbe.remove(p); ReturnMark.remove(p); PhaseSidestep.remove(p); HighPowerBracerSession.remove(p); AbilityStorage.remove(p); p.server.getPlayerList().remove(p); channel.finishAndReleaseAll(); }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final Vec3 anchor, source; final Actor caster; final List<Actor> actors = new ArrayList<>(); final List<Object> hooks = new ArrayList<>(); final List<PostCheck> checks = new ArrayList<>();
        final Map<BlockPos, BlockState> changed = new LinkedHashMap<>(); final Set<ChunkPos> held = new LinkedHashSet<>(); final UUID ticket = UUID.randomUUID(); final List<PlayerTeam> teams = new ArrayList<>(); PlayerTeam team; Throwable failure; boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h; anchor = absolute(8.5, 5, 8.5); source = absolute(12.5, 5, 8.5);
            for (int x = 4; x <= 20; x++) for (int z = 4; z <= 16; z++) block(x, 4, z, Blocks.STONE);
            caster = actor(anchor, true); learn(); HighPowerResources.initializeBudget(p()); caster.epoch(); caster.drain();
            h.testInfo.addListener(new GameTestListener() { public void testStructureLoaded(GameTestInfo info) {} public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); } public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); } public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); } });
        }
        ServerPlayer p() { return caster.p; }
        void learn() { var s = AbilityStorage.get(p()); s.selectCategory(HighPowerCategories.TELEPORTER); s.setLevel(5); for (var skill : HighPowerSkills.ALL) if (skill.category().equals(HighPowerCategories.TELEPORTER)) s.learn(skill.id()); s.activated = true; s.overloadFine = true; h.assertTrue(s.presets.edit(0, 0, ID, s::learned), "declared mapped native353 slot"); }
        Actor actor(Vec3 at, boolean enrolled) { Actor actor = new Actor(this, at, enrolled); actors.add(actor); return actor; }
        Actor recipient() { Actor recipient = actor(absolute(12.5, 5, 12.5), false); team(caster); team(recipient); return recipient; }
        void team(Actor actor) { if (team == null) { team = h.getLevel().getScoreboard().addPlayerTeam("tc" + UUID.randomUUID().toString().substring(0, 10)); teams.add(team); } h.getLevel().getScoreboard().addPlayerToTeam(actor.p.getScoreboardName(), team); }
        PlayerTeam otherTeam() { var other = h.getLevel().getScoreboard().addPlayerTeam("tc" + UUID.randomUUID().toString().substring(0, 10)); teams.add(other); return other; }
        Vec3 absolute(double x, double y, double z) { return h.absoluteVec(new Vec3(x, y, z)); }
        void hold(BlockPos pos) { ChunkPos chunk = new ChunkPos(pos); if (held.add(chunk)) { h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket); h.getLevel().getChunk(chunk.x, chunk.z); } }
        void hold(AABB box) { for (int x = ((int)Math.floor(box.minX)) >> 4; x <= ((int)Math.floor(box.maxX)) >> 4; x++) for (int z = ((int)Math.floor(box.minZ)) >> 4; z <= ((int)Math.floor(box.maxZ)) >> 4; z++) hold(new BlockPos(x << 4, 4, z << 4)); }
        void block(int x, int y, int z, Block block) { block(x, y, z, block.defaultBlockState()); }
        void block(int x, int y, int z, BlockState state) { BlockPos pos = h.absolutePos(new BlockPos(x, y, z)); h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)), "NEW353 fixture remains inside dedicated native structure"); hold(pos); changed.putIfAbsent(pos, h.getLevel().getBlockState(pos)); h.getLevel().setBlockAndUpdate(pos, state); }
        void floor(int x, int z) { for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) block(x + dx, 4, z + dz, Blocks.STONE); }
        boolean open() { return TacticalCoordinateFormation.press(p(), caster.wire(++caster.inputs)); }
        void prepare() { h.assertTrue(open(), "authenticated native353 menu opens"); h.assertTrue(action("save", 0), "explicit save captures actual current standing feet"); caster.drain(); }
        TacticalCoordinateFormation.Template template(int index) { var templates = TacticalCoordinateFormation.templates(p()); if (templates == null) return null; return templates.stream().filter(row -> row.index() == index).findFirst().orElse(null); }
        TacticalCoordinateFormation.Request request(String action, int index, UUID recipient) { CompoundTag view = TacticalCoordinateFormation.snapshot(p()); h.assertTrue(view != null, "live owner publication exists for " + action); var row = template(index);
            return new TacticalCoordinateFormation.Request(caster.wire(caster.inputs), view.getLong("token"), view.getUUID("nonce"), view.getLong("sequence"), action, index, row == null ? null : row.id(), row == null ? 0 : row.revision(), recipient, p().getId(), p().getUUID()); }
        boolean action(String action, int index) { return action(action, index, null); }
        boolean action(String action, int index, UUID recipient) { if (TacticalCoordinateFormation.snapshot(p()) == null) return false; return TacticalCoordinateFormation.request(p(), request(action, index, recipient)); }
        void invite(Actor recipient) { caster.stand(source); h.assertTrue(action("select", 0), "server refreshes named-recipient choices before invitation"); h.assertTrue(action("invite", 0, recipient.p.getUUID()), "caster explicitly invites named same-team recipient"); caster.drain(); recipient.drain(); }
        TacticalCoordinateFormation.Request inviteRequest(Actor recipient, String action) { var view = TacticalCoordinateFormation.invitation(recipient.p); h.assertTrue(view != null, "incoming invitation publication exists for " + action);
            return new TacticalCoordinateFormation.Request("", view.getLong("token"), view.getUUID("nonce"), view.getLong("sequence"), action, view.getInt("selected"), view.getUUID("template"), view.getLong("revision"), null, recipient.p.getId(), recipient.p.getUUID()); }
        boolean reply(Actor recipient, String action) { if (TacticalCoordinateFormation.invitation(recipient.p) == null) return false; return TacticalCoordinateFormation.request(recipient.p, inviteRequest(recipient, action)); }
        CompoundTag storage() { return p().getPersistentData().getCompound(DATA).copy(); }
        CompoundTag budget() { return p().getPersistentData().getCompound(HighPowerResources.DATA_KEY); }
        CompoundTag shared(ServerPlayer actor) { return actor.getPersistentData().getCompound(SHARED).copy(); }
        double cp() { return HighPowerResources.cp(p()); }
        Snapshot snapshot() { var state = AbilityStorage.get(p()); return new Snapshot(cp(), budget().getLong("next_" + ID), state.cooldowns.getOrDefault(ID, 0), state.exp(ID), state.levelExperience, HighPowerGrowth.values(p()).position(), HighPowerCombat.healthText(p()), HighPowerCombat.shieldText(p()), state.overload, inventory(p())); }
        void same(Snapshot before, String why) { h.assertTrue(snapshot().equals(before), why + " preserves CP/deadline/cooldown/mastery/XP/q/HP/shield/overload/inventory"); }
        void success(Snapshot before, Actor traveler, Vec3 destination) { var receipt = TacticalCoordinateFormation.receipt(p()); h.assertTrue(receipt != null && receipt.paid() && receipt.moved(), "one paid native353 movement: " + receipt);
            h.assertTrue(traveler.p.position().equals(destination) && receipt.destination().equals(destination), "exact saved native double destination"); near(h, cp(), before.cp() - TacticalCoordinateRules.cpCost(before.q()), "sender pays one current continuous353 cost"); Snapshot after = snapshot();
            h.assertTrue(after.mastery() == before.mastery() && after.levelXp() == before.levelXp() && after.q().compareTo(before.q()) == 0 && after.hp().equals(before.hp()) && after.shield().equals(before.shield()) && after.overload() == before.overload() && after.inventory().equals(before.inventory()), "movement grants no XP, growth, HP, shield, overload reset or inventory change");
            h.assertValueEqual(shared(traveler.p).getInt("movement"), 8, "actual traveler receives full shared8 fence"); h.assertTrue(budget().getLong("next_" + ID) == p().serverLevel().getGameTime() + TacticalCoordinateRules.cooldownTicks(before.q()), "sender economic deadline uses exact continuous cooldown"); }
        void noReceiverLedger(Actor receiver) { h.assertFalse(receiver.p.getPersistentData().contains(HighPowerCombat.DATA_KEY) || receiver.p.getPersistentData().contains(HighPowerResources.DATA_KEY) || HighPowerCombat.enabled(receiver.p), "ordinary recipient never gets high-power enrollment or HP/shield/CP ledgers"); h.assertFalse(AbilityStorage.get(receiver.p).learned(ID), "recipient does not learn353 or need its mapping"); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void post(int delay, Runnable action) { var check = new PostCheck(this, delay, action); checks.add(check); hook(check); }
        void verify() { if (failure instanceof RuntimeException problem) throw problem; if (failure instanceof Error problem) throw problem; if (failure != null) throw new AssertionError(failure); h.assertTrue(checks.stream().allMatch(check -> check.ran), "all genuine native Post observations executed"); }
        void finish(int delay) { h.runAfterDelay(delay, () -> { verify(); h.succeed(); }); }
        @Override public void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); actors.forEach(Actor::close); teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam); changed.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state)); held.forEach(chunk -> h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket)); }
    }
}
