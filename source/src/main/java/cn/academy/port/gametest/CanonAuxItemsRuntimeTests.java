package cn.academy.port.gametest;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.AbilityStorage;
import cn.academy.port.canon.CanonAuxItems;
import cn.academy.port.canon.CanonPuppets;
import cn.academy.port.canon.CanonExpansionA;
import cn.academy.port.canon.CanonSkillsCombatA;
import cn.academy.port.canon.CanonSkillsCombatB;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Canon auxiliary items. Every effect has a control run without the item, so the tests prove both the boost and that
 * nothing changes when the item is absent. Native connected-player fixtures; no live client.
 */
@GameTestHolder("academy_canon_aux")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class CanonAuxItemsRuntimeTests {
    private CanonAuxItemsRuntimeTests() {}

    @SubscribeEvent public static void template(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        register(level, "empty", 12, 12, 12);
        register(level, "wide", 40, 12, 40); // the harness fences each test with barriers at its template bounds; reach tests need room
    }
    private static void register(ServerLevel level, String name, int x, int y, int z) {
        var tag = new CompoundTag(); var size = new ListTag(); var position = new ListTag();
        for (int value : new int[]{x, y, z}) size.add(IntTag.valueOf(value));
        for (int i = 0; i < 3; i++) position.add(IntTag.valueOf(0));
        var air = new CompoundTag(); air.putString("Name", "minecraft:air");
        var cell = new CompoundTag(); cell.put("pos", position); cell.putInt("state", 0);
        var blocks = new ListTag(); blocks.add(cell); var palette = new ListTag(); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_canon_aux", name))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    // ------------------------------------------------------------------ origami
    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 100)
    public static void origami_paper_counts_as_one_sheet_for_paper_blade(GameTestHelper h) {
        var f = new Fixture(h, "paper_psychokinesis", "paper_blade");
        Zombie target = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 7.5));
        try {
            target.setNoAi(true); f.loadChunks();
            h.assertTrue(HighPowerCombat.enroll(target, 5), "target has an exact ledger");
            BigDecimal health = new BigDecimal(HighPowerCombat.healthText(target));
            f.p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CanonAuxItems.ORIGAMI_PAPER.get(), 2));
            h.assertTrue(CanonSkillsCombatB.cast(f.p, "paper_blade"), "origami paper accepted as the sheet");
            h.assertTrue(f.p.getMainHandItem().getCount() == 1, "exactly one origami sheet consumed");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).compareTo(health) < 0, "blade damage settled as with vanilla paper");
            h.succeed();
        } finally { target.discard(); f.close(); }
    }

    // ------------------------------------------------------------------ dark matter crystal
    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 330)
    public static void crystal_extends_dark_wings_by_half_and_is_spent(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_wings");
        try {
            f.loadChunks();
            f.p.getInventory().add(new ItemStack(CanonAuxItems.DARK_MATTER_CRYSTAL.get(), 2));
            h.assertTrue(CanonSkillsCombatA.cast(f.p, "dark_wings"), "wings accepted");
            h.assertTrue(countOf(f.p, CanonAuxItems.DARK_MATTER_CRYSTAL.get()) == 1, "exactly one crystal spent");
            int boosted = SkillTuning.integer("dark_wings", "boosted_duration_ticks");
            int plain = SkillTuning.integer("dark_wings", "duration_ticks");
            h.assertTrue(boosted*2==plain*3, "crystal extends flight by one half");
            h.runAtTickTime((plain+boosted)/2, () -> {
                h.assertTrue(CanonSkillsCombatA.ownsDarkWings(f.p), "wings still held after plain expiry");
                h.runAtTickTime(boosted+10, () -> { h.assertFalse(CanonSkillsCombatA.ownsDarkWings(f.p), "wings end by boosted expiry"); f.close(); h.succeed(); });
            });
        } catch (Throwable failure) { f.close(); throw failure; }
    }

    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 330)
    public static void dark_wings_without_crystal_end_at_plain_tuned_lifetime(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_wings");
        try {
            f.loadChunks();
            h.assertTrue(CanonSkillsCombatA.cast(f.p, "dark_wings"), "wings accepted");
            int duration = SkillTuning.integer("dark_wings", "duration_ticks");
            h.runAtTickTime(duration*3/4, () -> {
                h.assertTrue(CanonSkillsCombatA.ownsDarkWings(f.p), "wings held within plain duration");
                h.runAtTickTime(duration+10, () -> { h.assertFalse(CanonSkillsCombatA.ownsDarkWings(f.p), "wings gone after plain expiry without a crystal"); f.close(); h.succeed(); });
            });
        } catch (Throwable failure) { f.close(); throw failure; }
    }

    // ------------------------------------------------------------------ nitrogen canister
    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 100)
    public static void canister_pushes_dash_a_quarter_harder_and_is_spent(GameTestHelper h) {
        double plain = dash(h, false), boosted = dash(h, true);
        h.assertTrue(plain > .3, "control dash moved");
        h.assertTrue(Math.abs(boosted / plain - 1.25) < .02, "boosted push is 25% of the control push, got " + boosted / plain);
        h.succeed();
    }
    private static double dash(GameTestHelper h, boolean canister) {
        var f = new Fixture(h, "nitrogen_armor", "nitrogen_armor");
        try {
            f.loadChunks();
            h.assertTrue(CanonSkillsCombatA.cast(f.p, "nitrogen_armor"), "shell active");
            f.select("nitrogen_dash");
            h.setBlock(new BlockPos(4, 2, 2), Blocks.STONE); f.p.setOnGround(true);
            if (canister) f.p.getInventory().add(new ItemStack(CanonAuxItems.NITROGEN_CANISTER.get(), 3));
            h.assertTrue(CanonSkillsCombatA.cast(f.p, "nitrogen_dash"), "dash accepted");
            if (canister) h.assertTrue(countOf(f.p, CanonAuxItems.NITROGEN_CANISTER.get()) == 2, "exactly one canister spent");
            Vec3 v = f.p.getDeltaMovement();
            return Math.sqrt(v.x * v.x + v.z * v.z);
        } finally { f.close(); }
    }

    // ------------------------------------------------------------------ air pressure chip
    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 100)
    public static void chip_adds_a_seventh_ring_push_target_only_when_carried(GameTestHelper h) {
        int[] plain = pushed(h, false), boosted = pushed(h, true);
        h.assertTrue(plain[0] == 6, "control pushes the usual six, got " + plain[0] + " (chips left " + plain[1] + ")");
        h.assertTrue(plain[1] == 0, "control has no chip to spend");
        h.assertTrue(boosted[0] == 7, "chip pushes seven, got " + boosted[0] + " (chips left " + boosted[1] + ", los=" + LAST_LOS + ")");
        h.assertTrue(boosted[1] == 1, "exactly one chip spent for the extra target, left " + boosted[1]);
        h.succeed();
    }
    private static int LAST_LOS;
    private static int[] pushed(GameTestHelper h, boolean chip) {
        var f = new Fixture(h, "aero_hand", "aero_ring_push");
        var mobs = new ArrayList<Zombie>();
        try {
            f.p.moveTo(h.absoluteVec(new Vec3(6, 3, 6)), 0, 0); f.loadChunks();
            for (int i = 0; i < 9; i++) {
                double a = i * Math.PI * 2 / 9;
                Zombie z = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(6 + Math.cos(a) * 3.3, 3, 6 + Math.sin(a) * 3.3));
                z.setNoGravity(true); mobs.add(z);
            }
            if (chip) f.p.getInventory().add(new ItemStack(CanonAuxItems.AERO_PRESSURE_CHIP.get(), 2));
            h.assertTrue(CanonSkillsCombatA.cast(f.p, "aero_ring_push"), "ring push accepted");
            int n = 0, los = 0; for (Zombie z : mobs) { if (z.getDeltaMovement().lengthSqr() > .01) n++; if (f.p.hasLineOfSight(z)) los++; }
            LAST_LOS = los;
            return new int[]{n, countOf(f.p, CanonAuxItems.AERO_PRESSURE_CHIP.get())};
        } finally { for (Zombie z : mobs) z.discard(); f.close(); }
    }

    // ------------------------------------------------------------------ mind remote
    @GameTest(template = "wide", batch = "canon_aux_wide", timeoutTicks = 100)
    public static void remote_extends_scan_reach_to_the_tuned_distance(GameTestHelper h) {
        var f = new Fixture(h, "mental_out", "mental_intent_scan");
        double plain = SkillTuning.number("mental_intent_scan", "reach");
        double remote = SkillTuning.number("mental_intent_scan", "remote_reach");
        Zombie far = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5, 3, 2.5+(plain+remote)/2));
        try {
            far.setNoGravity(true); f.loadChunks();
            double cp = HighPowerResources.cp(f.p);
            h.assertFalse(CanonSkillsCombatA.cast(f.p, "mental_intent_scan"), "the target between the two tuning endpoints is outside plain reach");
            h.assertTrue(HighPowerResources.cp(f.p) == cp, "the refusal costs nothing");
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(CanonAuxItems.MENTAL_REMOTE.get()));
            boolean ok = CanonSkillsCombatA.cast(f.p, "mental_intent_scan");
            h.assertTrue(ok, "the remote in hand reaches the tuned endpoint [dist=" + Math.sqrt(f.p.distanceToSqr(far)) + " los=" + f.p.hasLineOfSight(far)
                    + " alive=" + far.isAlive() + " noAi=" + far.isNoAi() + " hand=" + f.p.getOffhandItem() + " ray="
                    + describe(f.p, cn.academy.port.skill.ClassicRaytrace.living(f.p, remote, net.minecraft.world.level.ClipContext.Fluid.NONE)) + "]");
            h.assertTrue(HighPowerResources.cp(f.p) < cp, "the scan pays its normal CP");
            h.assertTrue(f.p.getOffhandItem().is(CanonAuxItems.MENTAL_REMOTE.get()), "the remote is not consumed");
            h.succeed();
        } finally { far.discard(); f.close(); }
    }

    // ------------------------------------------------------------------ water flask
    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 100)
    public static void flask_places_a_real_water_source_and_spends_one_charge(GameTestHelper h) {
        var f = new Fixture(h, "hydro_hand", "hydro_whip");
        try {
            h.setBlock(new BlockPos(4, 2, 2), Blocks.STONE);
            BlockPos stone = h.absolutePos(new BlockPos(4, 2, 2)), above = stone.above();
            ItemStack flask = new ItemStack(CanonAuxItems.WATER_FLASK.get()); flask.setDamageValue(0);
            f.p.setItemInHand(InteractionHand.MAIN_HAND, flask);
            var hit = new BlockHitResult(Vec3.atCenterOf(stone).add(0, .5, 0), Direction.UP, stone, false);
            f.p.gameMode.useItemOn(f.p, f.p.serverLevel(), flask, InteractionHand.MAIN_HAND, hit);
            var fluid = f.p.serverLevel().getFluidState(above);
            h.assertTrue(fluid.is(net.minecraft.tags.FluidTags.WATER) && fluid.isSource(), "a real source block was placed");
            h.assertTrue(flask.getDamageValue() == 1, "one of four charges spent");
            ItemStack empty = new ItemStack(CanonAuxItems.WATER_FLASK.get());
            h.assertTrue(empty.getDamageValue() == CanonAuxItems.FLASK_CHARGES, "a crafted flask starts empty");
            f.p.setItemInHand(InteractionHand.MAIN_HAND, empty);
            BlockPos other = above.east(); h.setBlock(h.relativePos(other).below(), Blocks.STONE);
            f.p.gameMode.useItemOn(f.p, f.p.serverLevel(), empty, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(other.below()).add(0, .5, 0), Direction.UP, other.below(), false));
            h.assertTrue(f.p.serverLevel().getBlockState(other).isAir(), "an empty flask places nothing");
            h.succeed();
        } finally { f.close(); }
    }

    // ------------------------------------------------------------------ buoyancy float
    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 100)
    public static void float_marker_is_set_cleared_and_ignored_out_of_reach(GameTestHelper h) {
        var f = new Fixture(h, "float_dial", "float_field");
        try {
            h.setBlock(new BlockPos(4, 2, 2), Blocks.STONE);
            BlockPos stone = h.absolutePos(new BlockPos(4, 2, 2));
            ItemStack marker = new ItemStack(CanonAuxItems.BUOYANCY_FLOAT.get());
            f.p.setItemInHand(InteractionHand.MAIN_HAND, marker);
            h.assertTrue(CanonAuxItems.floatMarker(f.p) == null, "no marker yet");
            f.p.gameMode.useItemOn(f.p, f.p.serverLevel(), marker, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(stone).add(0, .5, 0), Direction.UP, stone, false));
            Vec3 at = CanonAuxItems.floatMarker(f.p);
            h.assertTrue(at != null && at.distanceToSqr(Vec3.atCenterOf(stone.above())) < .01, "marker stored above the clicked block");
            f.p.moveTo(f.p.position().add(0, 0, 40), 0, 0);
            h.assertTrue(CanonAuxItems.floatMarker(f.p) == null, "a marker out of the 16-block reach is ignored");
            f.p.moveTo(f.p.position().add(0, 0, -40), 0, 0);
            h.assertTrue(CanonAuxItems.floatMarker(f.p) != null, "and returns once the caster is back in reach");
            f.p.setShiftKeyDown(true);
            f.p.gameMode.useItem(f.p, f.p.serverLevel(), marker, InteractionHand.MAIN_HAND);
            h.assertTrue(CanonAuxItems.floatMarker(f.p) == null, "sneak-use clears the marker");
            h.succeed();
        } finally { f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 100)
    public static void float_field_without_a_marker_keeps_working_unchanged(GameTestHelper h) {
        var f = new Fixture(h, "float_dial", "float_field");
        Zombie z = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 7.5));
        try {
            z.setNoAi(true); f.loadChunks();
            double cp = HighPowerResources.cp(f.p);
            h.assertTrue(CanonSkillsCombatB.cast(f.p, "float_field"), "float field accepted exactly as before");
            h.assertTrue(HighPowerResources.cp(f.p) < cp, "real CP debit");
            h.succeed();
        } finally { z.discard(); f.close(); }
    }


    // ------------------------------------------------------------------ Dark Synthesis
    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 100)
    public static void synthesis_turns_one_item_into_two_with_every_component(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_synthesis");
        try {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Twin Edge"));
            sword.setDamageValue(37);
            var tag = new CompoundTag(); tag.putString("mark", "canon"); tag.putInt("n", 7);
            sword.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            f.p.setItemInHand(InteractionHand.MAIN_HAND, sword);
            f.p.getInventory().add(new ItemStack(CanonAuxItems.DARK_MATTER_CRYSTAL.get(), 2));
            double cp = HighPowerResources.cp(f.p);
            h.assertTrue(CanonSkillsCombatA.cast(f.p, "dark_synthesis"), "synthesis accepted");
            h.assertTrue(HighPowerResources.cp(f.p) < cp, "real CP debit");
            h.assertTrue(countOf(f.p, CanonAuxItems.DARK_MATTER_CRYSTAL.get()) == 1, "exactly one crystal spent");
            int copies = 0;
            var inv = f.p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack at = inv.getItem(i);
                if (at.is(Items.DIAMOND_SWORD)) { copies++; h.assertTrue(ItemStack.matches(at, sword), "copy " + i + " matches every component"); }
            }
            h.assertTrue(copies == 2, "one sword became two, found " + copies);
            h.succeed();
        } finally { f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 100)
    public static void synthesis_grows_a_stack_by_exactly_one(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_synthesis");
        try {
            f.p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 5));
            f.p.getInventory().add(new ItemStack(CanonAuxItems.DARK_MATTER_CRYSTAL.get(), 1));
            h.assertTrue(CanonSkillsCombatA.cast(f.p, "dark_synthesis"), "synthesis accepted");
            h.assertTrue(f.p.getMainHandItem().getCount() == 6, "five diamonds became six, got " + f.p.getMainHandItem().getCount());
            h.assertTrue(countOf(f.p, CanonAuxItems.DARK_MATTER_CRYSTAL.get()) == 0, "the crystal is spent");
            h.succeed();
        } finally { f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 100)
    public static void synthesis_refuses_without_crystal_on_crystals_or_without_room_and_pays_nothing(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_synthesis");
        try {
            double cp = HighPowerResources.cp(f.p);
            f.p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            h.assertFalse(CanonSkillsCombatA.cast(f.p, "dark_synthesis"), "no crystal, no synthesis");
            h.assertTrue(HighPowerResources.cp(f.p) == cp, "refusal pays nothing");
            f.p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CanonAuxItems.DARK_MATTER_CRYSTAL.get(), 2));
            f.p.getInventory().add(new ItemStack(CanonAuxItems.DARK_MATTER_CRYSTAL.get(), 1));
            h.assertFalse(CanonSkillsCombatA.cast(f.p, "dark_synthesis"), "crystals cannot be duplicated");
            h.assertTrue(countOf(f.p, CanonAuxItems.DARK_MATTER_CRYSTAL.get()) == 3, "no crystal lost");
            // Full inventory of unstackable items, crystal held in the off hand: a second sword has nowhere to go.
            var inv = f.p.getInventory();
            for (int i = 0; i < inv.items.size(); i++) inv.items.set(i, new ItemStack(Items.DIAMOND_PICKAXE));
            f.p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(CanonAuxItems.DARK_MATTER_CRYSTAL.get(), 1));
            double before = HighPowerResources.cp(f.p);
            h.assertFalse(CanonSkillsCombatA.cast(f.p, "dark_synthesis"), "no room, no synthesis");
            h.assertTrue(HighPowerResources.cp(f.p) == before, "no room costs nothing");
            h.assertTrue(f.p.getOffhandItem().is(CanonAuxItems.DARK_MATTER_CRYSTAL.get()), "the crystal is kept");
            h.succeed();
        } finally { f.close(); }
    }

    // ------------------------------------------------------------------ Mind Puppet
    private static Zombie armoured(GameTestHelper h, Vec3 at) {
        Zombie z = h.spawn(EntityType.ZOMBIE, at);
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)); // daylight must not decide the test
        return z;
    }
    private static Fixture puppetCaster(GameTestHelper h) {
        var f = new Fixture(h, "mental_out", "mental_puppet");
        f.p.moveTo(h.absoluteVec(new Vec3(6, 3, 2.5)), 0, 0); f.p.setYHeadRot(0); f.loadChunks(); return f;
    }

    // Keep nearby monsters from other fixtures out of nearest-enemy selection.
    @GameTest(template = "empty", batch = "canon_puppet_attack", timeoutTicks = 140)
    public static void puppet_spares_the_caster_and_fights_the_nearest_enemy(GameTestHelper h) {
        var f = puppetCaster(h);
        Zombie puppet = armoured(h, new Vec3(6, 3, 8.5)), enemy = armoured(h, new Vec3(9.5, 3, 8.5));
        try {
            double cp = HighPowerResources.cp(f.p);
            h.assertTrue(CanonSkillsCombatA.cast(f.p, "mental_puppet"), "puppet accepted");
            h.assertTrue(HighPowerResources.cp(f.p) < cp, "real CP debit");
            h.assertTrue(CanonPuppets.isPuppet(puppet) && CanonPuppets.owns(f.p), "the creature in sight is now the puppet");
            float health = enemy.getHealth();
            h.runAtTickTime(40, () -> {
                h.assertTrue(puppet.getTarget() != f.p, "the puppet never targets its caster");
                h.assertTrue(enemy.getHealth() < health || enemy.isDeadOrDying(), "the puppet hurt the other enemy");
                f.close(); puppet.discard(); enemy.discard(); h.succeed();
            });
        } catch (Throwable failure) { f.close(); puppet.discard(); enemy.discard(); throw failure; }
    }

    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 140)
    public static void puppet_can_be_any_creature_a_cow_follows_the_caster(GameTestHelper h) {
        var f = puppetCaster(h);
        var cow = h.spawn(EntityType.COW, new Vec3(6, 3, 10.5));
        try {
            double before = f.p.distanceTo(cow);
            h.assertTrue(CanonSkillsCombatA.cast(f.p, "mental_puppet"), "a passive cow can be a puppet");
            h.runAtTickTime(90, () -> {
                double after = f.p.distanceTo(cow);
                h.assertTrue(after < before - 2.5, "the cow walked toward the caster: " + before + " -> " + after);
                f.close(); cow.discard(); h.succeed();
            });
        } catch (Throwable failure) { f.close(); cow.discard(); throw failure; }
    }

    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 100)
    public static void puppet_refuses_players_and_pays_nothing(GameTestHelper h) {
        var f = puppetCaster(h); var other = new Fixture(h, "float_dial", "float_field");
        try {
            other.p.moveTo(h.absoluteVec(new Vec3(6, 3, 8.5)), 0, 0);
            double cp = HighPowerResources.cp(f.p);
            h.assertFalse(CanonSkillsCombatA.cast(f.p, "mental_puppet"), "a player is never a puppet");
            h.assertTrue(HighPowerResources.cp(f.p) == cp && !CanonPuppets.owns(f.p), "refusal costs nothing and starts nothing");
            h.succeed();
        } finally { f.close(); other.close(); }
    }

    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 2200)
    public static void puppet_ends_after_configured_hundred_seconds_and_only_one_at_a_time(GameTestHelper h) {
        var f = puppetCaster(h);
        // This test measures the lease clock. Combat and sunlight are covered separately, so use live passive AI witnesses.
        var puppet=h.spawn(EntityType.COW,new Vec3(6,3,8.5));var second=h.spawn(EntityType.COW,new Vec3(3,3,8.5));
        for(int x=1;x<=10;x++)for(int z=1;z<=11;z++)h.setBlock(new BlockPos(x,2,z),net.minecraft.world.level.block.Blocks.STONE);
        f.p.setNoGravity(true);
        puppet.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        second.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        f.p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,puppet.getBoundingBox().getCenter());
        try {
            h.assertTrue(CanonSkillsCombatA.cast(f.p, "mental_puppet"), "puppet accepted");
            h.assertFalse(CanonSkillsCombatA.cast(f.p, "mental_puppet"), "a second puppet while one is active is refused");
            int duration=cn.academy.port.core.SkillTuning.integer("mental_puppet","duration_ticks");
            h.assertTrue(duration==2000,"requested control time is 200 ticks times ten");
            h.runAfterDelay(215, () -> h.assertTrue(CanonPuppets.isPuppet(puppet), "still controlled beyond the old ten-second limit"));
            h.runAfterDelay(duration*3/4, () -> h.assertTrue(CanonPuppets.isPuppet(puppet), "still controlled at three quarters of the new duration"));
            h.runAfterDelay(duration+15, () -> {
                h.assertFalse(CanonPuppets.isPuppet(puppet) || CanonPuppets.owns(f.p), "released after the configured hundred seconds");
                f.close(); puppet.discard(); second.discard(); h.succeed();
            });
        } catch (Throwable failure) { f.close(); puppet.discard(); second.discard(); throw failure; }
    }

    @GameTest(template = "empty", batch = "canon_aux", timeoutTicks = 100)
    public static void puppet_is_released_when_the_caster_deactivates(GameTestHelper h) {
        var f = puppetCaster(h);
        Zombie puppet = armoured(h, new Vec3(6, 3, 8.5));
        try {
            h.assertTrue(CanonSkillsCombatA.cast(f.p, "mental_puppet"), "puppet accepted");
            AbilityStorage.get(f.p).setActivateState(false);
            h.runAtTickTime(5, () -> {
                h.assertFalse(CanonPuppets.isPuppet(puppet), "deactivating the ability releases the puppet");
                f.close(); puppet.discard(); h.succeed();
            });
        } catch (Throwable failure) { f.close(); puppet.discard(); throw failure; }
    }

    // ------------------------------------------------------------------ fixture
    private static String describe(ServerPlayer p, net.minecraft.world.phys.HitResult hit) {
        if (hit instanceof BlockHitResult b) return b.getType() + "@" + b.getBlockPos() + "=" + p.serverLevel().getBlockState(b.getBlockPos()) + " eye=" + p.getEyePosition();
        return String.valueOf(hit);
    }
    private static int countOf(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0; var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    private static final class Fixture {
        final ServerPlayer p; final EmbeddedChannel channel; boolean closed;
        Fixture(GameTestHelper h, String category, String skill) {
            var level = h.getLevel(); UUID uuid = UUID.randomUUID();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(uuid, "AX" + uuid.toString().substring(0, 8)), false);
            p = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
            p.setGameMode(GameType.SURVIVAL); p.getAbilities().instabuild = false;
            p.moveTo(h.absoluteVec(new Vec3(4.5, 3, 2.5)), 0, 0); p.setYHeadRot(0);
            var state = AbilityStorage.get(p); state.changeCategoryClassic(category); state.setLevel(5);
            switch (category) {
                case "mental_out" -> { state.learn("mental_probe"); state.learn("mental_disrupt"); }
                case "dark_matter" -> { state.learn("dark_blade"); state.learn("dark_wings"); }
                case "nitrogen_armor" -> state.learn("nitrogen_armor");
                case "aero_hand" -> { state.learn("aero_jet"); state.learn("aero_launch"); }
                case "float_dial" -> { state.learn("float_lift"); state.learn("float_rescue"); state.learn("float_field"); }
                case "hydro_hand" -> state.learn("hydro_whip");
                case "paper_psychokinesis" -> state.learn("paper_blade");
                default -> throw new IllegalStateException("unexpected category " + category);
            }
            for (var definition : CanonExpansionA.skills()) if (definition.category().equals(category)) state.learn(definition.id());
            state.setActivateState(true); select(skill);
            h.assertTrue(HighPowerCombat.enroll(p, 5), "actual exact-combat owner enrollment"); HighPowerResources.initializeBudget(p);
        }
        void loadChunks() {
            BlockPos at = p.blockPosition();
            for (int x = (at.getX() - 48) >> 4; x <= (at.getX() + 48) >> 4; x++)
                for (int z = (at.getZ() - 48) >> 4; z <= (at.getZ() + 48) >> 4; z++) p.serverLevel().getChunk(x, z);
        }
        void select(String id) { var state = AbilityStorage.get(p); state.presets.edit(0, 0, id, state::learned); }
        void close() {
            if (closed) return; closed = true;
            try { p.server.getPlayerList().remove(p); } finally { channel.finishAndReleaseAll(); }
        }
    }
}
