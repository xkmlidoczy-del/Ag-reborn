package cn.academy.port.gametest;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.highpower.ElectromasterPrototype;
import cn.academy.port.story.UrbanCalibrator;
import cn.academy.port.canon.CanonSkillsCombatA;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.MagneticItemProvenance;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real server entities and mock-connected players; no separate client or authentic keyboard is claimed. */
@GameTestHolder("academy_canon_a")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class CanonSkillsARuntimeTests {
    private CanonSkillsARuntimeTests() {}
    @SubscribeEvent public static void template(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag(); var size = new ListTag(); var position = new ListTag();
        for (int value : new int[]{12, 12, 12}) size.add(IntTag.valueOf(value));
        for (int i = 0; i < 3; i++) position.add(IntTag.valueOf(0));
        var air = new CompoundTag(); air.putString("Name", "minecraft:air");
        var cell = new CompoundTag(); cell.put("pos", position); cell.putInt("state", 0);
        var blocks = new ListTag(); blocks.add(cell); var palette = new ListTag(); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_canon_a", "empty"))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void probe_reads_real_hostile_state_and_awards_training_once(GameTestHelper h) {
        var f = new Fixture(h, "mental_out", "mental_probe");
        Zombie target = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 7.5));
        try {
            f.loadChunks(); target.setNoGravity(true); target.setTarget(f.player);
            double cp = HighPowerResources.cp(f.player), experience = AbilityStorage.get(f.player).exp("mental_probe");
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "mental_probe"), "visible real mob is probed");
            h.assertTrue(HighPowerResources.cp(f.player) < cp, "probe debits shared high-power CP");
            h.assertTrue(AbilityStorage.get(f.player).exp("mental_probe") > experience, "real probe awards training");
            double paid = HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatA.cast(f.player, "mental_probe"), "cooldown rejects duplicate");
            h.assertTrue(HighPowerResources.cp(f.player) == paid, "duplicate has no second debit");
            h.succeed();
        } finally { target.discard(); f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void disrupt_changes_real_hostile_ai_and_does_not_touch_unrelated_entity(GameTestHelper h) {
        var f = new Fixture(h, "mental_out", "mental_disrupt");
        Zombie target = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 7.5));
        Zombie other = h.spawn(EntityType.ZOMBIE, new Vec3(8.5, 3, 7.5));
        try {
            f.loadChunks(); target.setNoGravity(true); other.setNoGravity(true);
            target.setTarget(f.player); other.setTarget(f.player);
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "mental_disrupt"), "visible mob disturbed");
            h.assertTrue(target.getTarget() == null, "primary AI pursuit actually interrupted");
            h.assertTrue(other.getTarget() == f.player, "unselected mob remains unaffected");
            h.succeed();
        } finally { target.discard(); other.discard(); f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void crowd_assigns_nearby_hostile_to_visible_hostile_target(GameTestHelper h) {
        var f = new Fixture(h, "mental_out", "mental_out_crowd");
        Zombie victim = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 7.5));
        Zombie actor = h.spawn(EntityType.ZOMBIE, new Vec3(6.5, 3, 6.5));
        try {
            f.loadChunks(); victim.setNoGravity(true); actor.setNoGravity(true); actor.setTarget(f.player);
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "mental_out_crowd"), "real group action accepted");
            h.assertTrue(actor.getTarget() == victim, "hostile actually receives the aimed mob as target");
            h.succeed();
        } finally { victim.discard(); actor.discard(); f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void jet_requires_ground_and_applies_bounded_server_motion(GameTestHelper h) {
        var f = new Fixture(h, "aero_hand", "aero_jet");
        try {
            f.loadChunks(); f.player.setOnGround(false);
            double cp = HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatA.cast(f.player, "aero_jet"), "airborne start is rejected");
            h.assertTrue(HighPowerResources.cp(f.player) == cp, "rejected start is free");
            f.player.serverLevel().setBlock(f.player.blockPosition().below(), Blocks.STONE.defaultBlockState(), 3);
            f.player.setOnGround(true); f.player.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "aero_jet"), "ground jet accepted");
            h.assertTrue(f.player.getDeltaMovement().z > .6 && f.player.getDeltaMovement().y > .2, "server applied real directional impulse");
            h.succeed();
        } finally { f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void air_launch_moves_owned_actual_drop_without_duplicating_it(GameTestHelper h) {
        var f = new Fixture(h, "aero_hand", "aero_launch");
        ItemEntity item = null;
        try {
            f.loadChunks();
            item = f.player.drop(new ItemStack(Items.STONE, 4), false);
            h.assertTrue(item != null && MagneticItemProvenance.ownedBy(item, f.player.getUUID()),
                    "genuine native player toss carries verified owner");
            item.setPos(f.player.getEyePosition().add(0, -.1, 2)); item.setDeltaMovement(Vec3.ZERO); item.setNoGravity(true);
            int before = item.getItem().getCount(); double cp = HighPowerResources.cp(f.player);
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "aero_launch"), "owned nearby visible block drop is launched");
            h.assertTrue(item.getDeltaMovement().z > 1, "real item entity receives forward velocity");
            h.assertTrue(item.getItem().getCount() == before && !item.isRemoved(), "no deletion or duplicate stack");
            h.assertTrue(HighPowerResources.cp(f.player) < cp, "real high-power CP paid");
            h.succeed();
        } finally { if (item != null) item.discard(); f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void nitrogen_shell_deflects_real_arrow_contact_and_spends_exact_shield(GameTestHelper h) {
        var f = new Fixture(h, "nitrogen_armor", "nitrogen_armor");
        Arrow arrow = new Arrow(EntityType.ARROW, f.player.serverLevel());
        try {
            f.loadChunks(); h.assertTrue(CanonSkillsCombatA.cast(f.player, "nitrogen_armor"), "shell activated");
            Vec3 hit = f.player.position().add(0, 1, -.3);
            arrow.setPos(hit.add(0, 0, -.5)); arrow.setDeltaMovement(0, 0, 1);
            f.player.serverLevel().addFreshEntity(arrow);
            BigDecimal before = HighPowerCombat.shield(f.player);
            BigDecimal maximum = new BigDecimal(HighPowerCombat.maxShieldText(f.player));
            var impact = new ProjectileImpactEvent(arrow, new EntityHitResult(f.player, hit));
            NeoForge.EVENT_BUS.post(impact);
            h.assertTrue(impact.isCanceled(), "registered impact handler intercepted actual arrow object");
            h.assertTrue(arrow.getDeltaMovement().z < 0, "native arrow velocity reversed");
            h.assertTrue(before.subtract(HighPowerCombat.shield(f.player)).compareTo(maximum.multiply(new BigDecimal("0.04"))) == 0,
                    "actual exact shield paid four percent once");
            h.succeed();
        } finally { arrow.discard(); f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void dark_blade_debits_cp_and_hits_real_exact_target(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_blade");
        Zombie target = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 7.0));
        try {
            f.loadChunks(); target.setNoGravity(true); target.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(target, 5), "exact target ledger enrolled");
            BigDecimal before = total(target); double cp = HighPowerResources.cp(f.player);
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "dark_blade"), "real short blade accepted");
            h.assertTrue(HighPowerResources.cp(f.player) < cp, "sealed cast debited CP");
            h.assertTrue(total(target).compareTo(before) < 0, "native hit committed exact high-power damage");
            h.succeed();
        } finally { target.discard(); f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void dark_wings_grants_and_revokes_ordinary_flight_permission(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_wings");
        try {
            f.loadChunks(); h.assertFalse(f.player.getAbilities().mayfly, "survival begins without flight");
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "dark_wings"), "wings paid and started");
            h.assertTrue(f.player.getAbilities().mayfly, "real vanilla flight permission granted");
            AbilityStorage.get(f.player).setActivateState(false);
            CanonSkillsCombatA.tickPlayer(new PlayerTickEvent.Post(f.player));
            h.assertFalse(f.player.getAbilities().mayfly, "deactivation revokes granted permission");
            h.assertFalse(f.player.getPersistentData().getBoolean("academy:dark_wings_owned_flight"), "owned marker removed");
            h.succeed();
        } finally { f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void dark_construct_intercepts_a_real_arrow_crossing_one_shard(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_construct");
        Arrow arrow = new Arrow(EntityType.ARROW, f.player.serverLevel());
        try {
            f.loadChunks(); h.assertTrue(CanonSkillsCombatA.cast(f.player, "dark_construct"), "construct started");
            Vec3 shard = CanonSkillsCombatA.constructPosition(f.player, 0);
            h.assertTrue(shard != null, "server shard exists");
            arrow.setPos(shard.add(-.8, 0, 0)); arrow.setDeltaMovement(1, 0, 0);
            f.player.serverLevel().addFreshEntity(arrow);
            BigDecimal before = HighPowerCombat.shield(f.player);
            CanonSkillsCombatA.tickPlayer(new PlayerTickEvent.Post(f.player));
            h.assertTrue(arrow.getDeltaMovement().x < 0, "real arrow crossing shard reflects");
            BigDecimal maximum = new BigDecimal(HighPowerCombat.maxShieldText(f.player));
            h.assertTrue(before.subtract(HighPowerCombat.shield(f.player)).compareTo(maximum.multiply(new BigDecimal("0.02"))) == 0,
                    "interception debits exactly two percent maximum shield");
            BigDecimal paid = HighPowerCombat.shield(f.player);
            arrow.setPos(shard.add(-.8, 0, 0)); arrow.setDeltaMovement(1, 0, 0);
            CanonSkillsCombatA.tickPlayer(new PlayerTickEvent.Post(f.player));
            h.assertTrue(arrow.getDeltaMovement().x == 1 && HighPowerCombat.shield(f.player).compareTo(paid) == 0,
                    "same tick replay cannot redirect or debit twice");
            h.succeed();
        } finally { arrow.discard(); f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void dark_construct_autonomously_hits_hostile_using_paid_exact_budget(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_construct");
        Zombie target = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 5.5));
        try {
            f.loadChunks(); target.setNoGravity(true); target.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(target, 5), "hostile exact ledger enrolled");
            BigDecimal before = total(target); double cp = HighPowerResources.cp(f.player);
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "dark_construct"), "paid construct created");
            double paid = HighPowerResources.cp(f.player);
            h.assertTrue(Math.abs(cp - paid - HighPowerGrowth.values(f.player).arcCost() * 5) < 1.0e-8,
                    "activation consumes the existing scaled shared CP cost once");
            CanonSkillsCombatA.tickPlayer(new PlayerTickEvent.Post(f.player));
            h.assertTrue(total(target).compareTo(before) < 0, "construct independently settles exact hostile damage");
            h.assertTrue(HighPowerResources.cp(f.player) == paid, "autonomous attack cannot debit activation twice");
            BigDecimal after = total(target);
            CanonSkillsCombatA.tickPlayer(new PlayerTickEvent.Post(f.player));
            h.assertTrue(total(target).compareTo(after) == 0, "same-tick replay cannot attack twice");
            h.succeed();
        } finally { target.discard(); f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void dark_construct_respects_terrain_occlusion_and_nonhostile_targets(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_construct");
        Zombie target = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 5.5));
        var villager = h.spawn(EntityType.VILLAGER, new Vec3(7.5, 3, 3.5));
        try {
            f.loadChunks(); target.setNoGravity(true); target.setNoAi(true); villager.setNoGravity(true); villager.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(target, 5) && HighPowerCombat.enroll(villager, 5), "target ledgers enrolled");
            for (int x = 2; x <= 7; x++) for (int y = 3; y <= 5; y++) h.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
            BigDecimal before = total(target), friendlyBefore = new BigDecimal(HighPowerCombat.healthText(villager)).add(HighPowerCombat.shield(villager));
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "dark_construct"), "construct started");
            CanonSkillsCombatA.tickPlayer(new PlayerTickEvent.Post(f.player));
            h.assertTrue(total(target).compareTo(before) == 0, "solid wall blocks shard attack");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(villager)).add(HighPowerCombat.shield(villager)).compareTo(friendlyBefore) == 0,
                    "nonhostile villager is not targeted");
            h.succeed();
        } finally { target.discard(); villager.discard(); f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void dark_construct_revokes_attack_after_reentrant_target_movement(GameTestHelper h) {
        constructReentry(h, 0);
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void dark_construct_revokes_attack_after_reentrant_preset_change(GameTestHelper h) {
        constructReentry(h, 1);
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void dark_construct_revokes_attack_after_reentrant_deactivation(GameTestHelper h) {
        constructReentry(h, 2);
    }

    private static void constructReentry(GameTestHelper h, int mode) {
        var f = new Fixture(h, "dark_matter", "dark_construct");
        Zombie target = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 5.5));
        var hook = new ConstructHook(f.player, target, mode);
        NeoForge.EVENT_BUS.register(hook);
        try {
            f.loadChunks(); target.setNoGravity(true); target.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(target, 5), "target ledger enrolled");
            if (mode == 1) h.assertTrue(AbilityStorage.get(f.player).presets.edit(1, 0, "dark_construct", AbilityStorage.get(f.player)::learned),
                    "same skill on alternate preset");
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "dark_construct"), "construct started");
            BigDecimal before = total(target);
            CanonSkillsCombatA.tickPlayer(new PlayerTickEvent.Post(f.player));
            h.assertTrue(hook.calls == 1, "actual attack callback reached and attempted same-tick reentry");
            h.assertTrue(total(target).compareTo(before) == 0, "callback invalidates exact settlement");
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(hook); target.discard(); f.close(); }
    }

    public static final class ConstructHook {
        final ServerPlayer owner; final Zombie target; final int mode; int calls;
        ConstructHook(ServerPlayer owner, Zombie target, int mode) { this.owner = owner; this.target = target; this.mode = mode; }
        @SubscribeEvent public void attack(SkillAttackEvent e) {
            if (e.player != owner || e.target != target || !e.skill.equals("dark_matter.dark_construct")) return;
            calls++;
            if (calls != 1) return;
            if (mode == 0) target.setPos(owner.position().add(0, 0, 20));
            else if (mode == 1) AbilityStorage.get(owner).presets.switchTo(1);
            else AbilityStorage.get(owner).setActivateState(false);
            CanonSkillsCombatA.tickPlayer(new PlayerTickEvent.Post(owner));
        }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 230)
    public static void dark_construct_has_ten_spaced_attempts_with_bounded_exact_budget(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_construct");
        Zombie target = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 5.5));
        var hook = new ConstructBudgetHook(f.player, target);
        NeoForge.EVENT_BUS.register(hook);
        try {
            f.loadChunks(); target.setNoGravity(true); target.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(target, 5), "target ledger enrolled");
            BigDecimal budget = HighPowerGrowth.values(f.player).damage();
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "dark_construct"), "one activation pays for lifetime");
            CanonSkillsCombatA.tickPlayer(new PlayerTickEvent.Post(f.player));
            for (int tick = 20; tick <= 180; tick += 20)
                h.runAfterDelay(tick, () -> CanonSkillsCombatA.tickPlayer(new PlayerTickEvent.Post(f.player)));
            h.runAfterDelay(199, () -> {
                try {
                    h.assertTrue(hook.ticks.size() == 10, "exactly ten attempts across construct lifetime");
                    for (int i = 1; i < hook.ticks.size(); i++)
                        h.assertTrue(hook.ticks.get(i) - hook.ticks.get(i - 1) >= 20, "attempt interval at least 20 ticks");
                    BigDecimal tolerance = BigDecimal.valueOf(Math.ulp(budget.doubleValue())).multiply(BigDecimal.valueOf(20));
                    h.assertTrue(hook.sum.compareTo(budget.add(tolerance)) <= 0,
                            "all event amounts fit the frozen budget with double conversion tolerance");
                    h.succeed();
                } finally { NeoForge.EVENT_BUS.unregister(hook); target.discard(); f.close(); }
            });
        } catch (RuntimeException | Error failure) {
            NeoForge.EVENT_BUS.unregister(hook); target.discard(); f.close(); throw failure;
        }
    }

    public static final class ConstructBudgetHook {
        final ServerPlayer owner; final Zombie target; final List<Long> ticks = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        ConstructBudgetHook(ServerPlayer owner, Zombie target) { this.owner = owner; this.target = target; }
        @SubscribeEvent public void attack(SkillAttackEvent e) {
            if (e.player == owner && e.target == target && e.skill.equals("dark_matter.dark_construct")) {
                ticks.add(owner.serverLevel().getGameTime()); sum = sum.add(BigDecimal.valueOf(e.amount)); e.amount = 0;
            }
        }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void dark_construct_stops_on_deactivation_before_interception(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_construct");
        Arrow arrow = new Arrow(EntityType.ARROW, f.player.serverLevel());
        try {
            f.loadChunks(); h.assertTrue(CanonSkillsCombatA.cast(f.player, "dark_construct"), "construct started");
            Vec3 shard = CanonSkillsCombatA.constructPosition(f.player, 0);
            arrow.setPos(shard.add(-.8, 0, 0)); arrow.setDeltaMovement(1, 0, 0);
            f.player.serverLevel().addFreshEntity(arrow);
            BigDecimal before = HighPowerCombat.shield(f.player);
            AbilityStorage.get(f.player).setActivateState(false);
            CanonSkillsCombatA.tickPlayer(new PlayerTickEvent.Post(f.player));
            h.assertTrue(CanonSkillsCombatA.constructPosition(f.player, 0) == null, "deactivation removes construct lease");
            h.assertTrue(arrow.getDeltaMovement().x == 1 && HighPowerCombat.shield(f.player).compareTo(before) == 0,
                    "invalid construct cannot alter an arrow or consume shield");
            h.succeed();
        } finally { arrow.discard(); f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void nitrogen_punch_requires_shell_then_deals_exact_damage(GameTestHelper h) {
        var f = new Fixture(h, "nitrogen_armor", "nitrogen_armor");
        Zombie target = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 5.5));
        try {
            f.loadChunks(); target.setNoGravity(true); target.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(target, 5), "exact target ledger enrolled");
            f.select("nitrogen_punch"); double before = HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatA.cast(f.player, "nitrogen_punch"), "no punch without shell");
            h.assertTrue(HighPowerResources.cp(f.player) == before, "rejected punch is free");
            f.select("nitrogen_armor"); h.assertTrue(CanonSkillsCombatA.cast(f.player, "nitrogen_armor"), "shell started");
            f.select("nitrogen_punch"); BigDecimal targetBefore = total(target);
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "nitrogen_punch"), "armored punch accepted");
            h.assertTrue(total(target).compareTo(targetBefore) < 0, "exact damage committed");
            h.succeed();
        } finally { target.discard(); f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void nitrogen_dome_protects_actual_teammate_using_owners_shield(GameTestHelper h) {
        var owner = new Fixture(h, "nitrogen_armor", "nitrogen_armor");
        var ally = new Fixture(h, "aero_hand", "aero_jet");
        Arrow arrow = new Arrow(EntityType.ARROW, owner.player.serverLevel());
        var scoreboard = owner.player.server.getScoreboard();
        var team = scoreboard.addPlayerTeam("CA" + UUID.randomUUID().toString().substring(0, 8));
        try {
            owner.loadChunks(); ally.loadChunks();
            ally.player.moveTo(h.absoluteVec(new Vec3(6.5, 3, 2.5)), 0, 0);
            h.assertTrue(CanonSkillsCombatA.cast(owner.player, "nitrogen_armor"), "shell started");
            owner.select("nitrogen_dome"); h.assertTrue(CanonSkillsCombatA.cast(owner.player, "nitrogen_dome"), "team dome started");
            Vec3 hit = ally.player.position().add(0, 1, -.3);
            arrow.setPos(hit.add(0, 0, -.5)); arrow.setDeltaMovement(0, 0, 1); owner.player.serverLevel().addFreshEntity(arrow);
            BigDecimal before = HighPowerCombat.shield(owner.player);
            var outsiderImpact = new ProjectileImpactEvent(arrow, new EntityHitResult(ally.player, hit));
            NeoForge.EVENT_BUS.post(outsiderImpact);
            h.assertFalse(outsiderImpact.isCanceled(), "no interception for a player outside the team");
            h.assertTrue(HighPowerCombat.shield(owner.player).compareTo(before) == 0, "outsider costs no shield");
            scoreboard.addPlayerToTeam(owner.player.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(ally.player.getScoreboardName(), team);
            var teammateImpact = new ProjectileImpactEvent(arrow, new EntityHitResult(ally.player, hit));
            NeoForge.EVENT_BUS.post(teammateImpact);
            h.assertTrue(teammateImpact.isCanceled(), "visible teammate was protected");
            h.assertTrue(HighPowerCombat.shield(owner.player).compareTo(before) < 0, "owner pays shield");
            h.succeed();
        } finally { arrow.discard(); scoreboard.removePlayerTeam(team); ally.close(); owner.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void air_barrage_uses_one_exact_cast_for_visible_hostile(GameTestHelper h) {
        var f = new Fixture(h, "aero_hand", "aero_barrage");
        Zombie target = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 7.5));
        try {
            f.loadChunks(); target.setNoGravity(true); target.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(target, 5), "exact target ledger enrolled");
            f.player.serverLevel().setBlock(f.player.blockPosition().below(), Blocks.STONE.defaultBlockState(), 3);
            f.player.setOnGround(true); BigDecimal before = total(target);
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "aero_barrage"), "supported ground barrage accepted");
            h.assertTrue(total(target).compareTo(before) < 0, "real exact damage was settled");
            h.succeed();
        } finally { target.discard(); f.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void barrage_rechecks_range_after_real_attack_callback(GameTestHelper h) {
        barrageReentry(h, false);
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void barrage_rechecks_cone_after_real_attack_callback(GameTestHelper h) {
        barrageReentry(h, true);
    }

    private static void barrageReentry(GameTestHelper h, boolean cone) {
        var f = new Fixture(h, "aero_hand", "aero_barrage");
        Zombie first = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 7.5));
        Zombie second = h.spawn(EntityType.ZOMBIE, new Vec3(5.5, 3, 8));
        var hook = new MoveSecond(f.player, first, second, cone);
        NeoForge.EVENT_BUS.register(hook);
        try {
            f.loadChunks(); first.setNoGravity(true); first.setNoAi(true); second.setNoGravity(true); second.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(first, 5) && HighPowerCombat.enroll(second, 5), "target ledgers enrolled");
            f.player.serverLevel().setBlock(f.player.blockPosition().below(), Blocks.STONE.defaultBlockState(), 3);
            f.player.setOnGround(true); BigDecimal before = total(second);
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "aero_barrage"), "barrage paid");
            h.assertTrue(hook.moved && hook.secondCalls == 0, "first actual attack hook moved next target outside skill geometry");
            h.assertTrue(total(second).compareTo(before) == 0, "moved target receives no exact damage");
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(hook); first.discard(); second.discard(); f.close(); }
    }

    public static final class MoveSecond {
        final ServerPlayer owner; final Zombie first, second; final boolean cone;
        boolean moved; int secondCalls;
        MoveSecond(ServerPlayer owner, Zombie first, Zombie second, boolean cone) {
            this.owner = owner; this.first = first; this.second = second; this.cone = cone;
        }
        @SubscribeEvent public void attack(SkillAttackEvent e) {
            if (e.player != owner || !e.skill.equals("aero_hand.aero_barrage")) return;
            if (e.target == second) secondCalls++;
            if (e.target == first && !moved) {
                moved = true;
                double beyondReach = SkillTuning.number("aero_barrage", "reach") + 2;
                second.setPos(owner.position().add(cone ? 8 : 0, 0, cone ? 1 : beyondReach));
            }
        }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void blade_cancels_settlement_if_target_moves_during_own_attack_event(GameTestHelper h) {
        currentTargetReentry(h, "dark_matter", "dark_blade", false);
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void punch_cancels_settlement_if_target_moves_during_own_attack_event(GameTestHelper h) {
        currentTargetReentry(h, "nitrogen_armor", "nitrogen_punch", false);
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void barrage_cancels_settlement_if_target_leaves_range_during_own_attack_event(GameTestHelper h) {
        currentTargetReentry(h, "aero_hand", "aero_barrage", false);
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void barrage_cancels_settlement_if_target_leaves_cone_during_own_attack_event(GameTestHelper h) {
        currentTargetReentry(h, "aero_hand", "aero_barrage", true);
    }

    private static void currentTargetReentry(GameTestHelper h, String category, String skill, boolean cone) {
        var f = new Fixture(h, category, category.equals("nitrogen_armor") ? "nitrogen_armor" : skill);
        double z = category.equals("nitrogen_armor") ? 5.5 : category.equals("dark_matter") ? 6.5 : 7.5;
        Zombie target = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, z));
        var hook = new MoveCurrent(f.player, target, category + "." + skill, cone);
        NeoForge.EVENT_BUS.register(hook);
        try {
            f.loadChunks(); target.setNoGravity(true); target.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(target, 5), "exact target enrolled");
            if (category.equals("nitrogen_armor")) {
                h.assertTrue(CanonSkillsCombatA.cast(f.player, "nitrogen_armor"), "shell activated");
                f.select(skill);
            }
            if (category.equals("aero_hand")) {
                f.player.serverLevel().setBlock(f.player.blockPosition().below(), Blocks.STONE.defaultBlockState(), 3);
                f.player.setOnGround(true);
            }
            BigDecimal before = total(target); double cp = HighPowerResources.cp(f.player);
            h.assertTrue(CanonSkillsCombatA.cast(f.player, skill), "cast paid before target movement");
            h.assertTrue(hook.moved, "actual current target attack callback moved the entity");
            h.assertTrue(total(target).compareTo(before) == 0, "moved target receives no exact damage");
            h.assertTrue(HighPowerResources.cp(f.player) < cp, "already paid cost is retained");
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(hook); target.discard(); f.close(); }
    }

    public static final class MoveCurrent {
        final ServerPlayer owner; final Zombie target; final String skill; final boolean cone;
        boolean moved;
        MoveCurrent(ServerPlayer owner, Zombie target, String skill, boolean cone) {
            this.owner = owner; this.target = target; this.skill = skill; this.cone = cone;
        }
        @SubscribeEvent public void attack(SkillAttackEvent event) {
            if (!moved && event.player == owner && event.target == target && event.skill.equals(skill)) {
                moved = true;
                double beyondReach = SkillTuning.number(skill.substring(skill.indexOf('.')+1), "reach") + 2;
                target.setPos(owner.position().add(cone ? 8 : 0, 0, cone ? 1 : beyondReach));
            }
        }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void native_bracer_lease_blocks_shell_and_construct_controllers(GameTestHelper h) {
        var bracer = new Fixture(h, "electromaster", "magnetic_bracer");
        var shell = new Fixture(h, "nitrogen_armor", "nitrogen_armor");
        var construct = new Fixture(h, "dark_matter", "dark_construct");
        Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
        try {
            bracer.loadChunks(); shell.loadChunks(); construct.loadChunks();
            shell.player.moveTo(h.absoluteVec(new Vec3(8.5, 3, 4.5)), 0, 0);
            construct.player.moveTo(h.absoluteVec(new Vec3(8.5, 3, 8.5)), 0, 0);
            h.assertTrue(CanonSkillsCombatA.cast(shell.player, "nitrogen_armor"), "shell started");
            h.assertTrue(CanonSkillsCombatA.cast(construct.player, "dark_construct"), "construct started");
            bracer.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ElectromasterPrototype.CONTROL.get()));
            bracer.player.setShiftKeyDown(true); bracer.player.startUsingItem(InteractionHand.MAIN_HAND);
            arrow.setPos(bracer.player.getEyePosition().add(0, 0, 2)); arrow.setDeltaMovement(0, 0, -1);
            h.getLevel().addFreshEntity(arrow);
            bracer.player.getPersistentData().getCompound("academy:electromaster_prototype").putLong("last_tick", Long.MIN_VALUE);
            ElectromasterPrototype.tick(new PlayerTickEvent.Post(bracer.player));
            h.assertTrue(ElectromasterPrototype.leased(arrow) && arrow.getDeltaMovement().z > 0,
                    "real bracer controller redirected and leased the arrow");
            Vec3 hit = shell.player.position().add(0, 1, -.3);
            arrow.setPos(hit.add(0, 0, -.5)); arrow.setDeltaMovement(0, 0, 1);
            BigDecimal shellBefore = HighPowerCombat.shield(shell.player);
            var impact = new ProjectileImpactEvent(arrow, new EntityHitResult(shell.player, hit));
            NeoForge.EVENT_BUS.post(impact);
            h.assertFalse(impact.isCanceled(), "shell does not steal another controller's leased arrow");
            h.assertTrue(arrow.getDeltaMovement().z == 1 && HighPowerCombat.shield(shell.player).compareTo(shellBefore) == 0,
                    "shell leaves arrow and shield untouched");
            Vec3 shard = CanonSkillsCombatA.constructPosition(construct.player, 0);
            arrow.setPos(shard.add(-.8, 0, 0)); arrow.setDeltaMovement(1, 0, 0);
            BigDecimal constructBefore = HighPowerCombat.shield(construct.player);
            CanonSkillsCombatA.tickPlayer(new PlayerTickEvent.Post(construct.player));
            h.assertTrue(arrow.getDeltaMovement().x == 1 && HighPowerCombat.shield(construct.player).compareTo(constructBefore) == 0,
                    "construct leaves the same leased arrow and shield untouched");
            h.succeed();
        } finally { arrow.discard(); construct.close(); shell.close(); bracer.close(); }
    }

    @GameTest(template = "empty", batch = "canon_a", timeoutTicks = 100)
    public static void dark_blade_can_hit_exact_urban_boss_type(GameTestHelper h) {
        var f = new Fixture(h, "dark_matter", "dark_blade");
        var boss = UrbanCalibrator.BOSS.get().create(h.getLevel());
        try {
            f.loadChunks(); h.assertTrue(boss != null, "registered boss created");
            boss.moveTo(h.absoluteVec(new Vec3(4.5, 3, 6.5)), 0, 0); boss.setNoGravity(true); boss.setNoAi(true);
            h.getLevel().addFreshEntity(boss);
            h.assertTrue(HighPowerCombat.enroll(boss, 5), "boss exact ledger enrolled");
            BigDecimal before = new BigDecimal(HighPowerCombat.healthText(boss)).add(HighPowerCombat.shield(boss));
            h.assertTrue(CanonSkillsCombatA.cast(f.player, "dark_blade"), "blade accepts registered hostile boss");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(boss)).add(HighPowerCombat.shield(boss)).compareTo(before) < 0,
                    "boss receives exact damage");
            h.succeed();
        } finally { if (boss != null) boss.discard(); f.close(); }
    }

    private static BigDecimal total(Zombie target) {
        return new BigDecimal(HighPowerCombat.healthText(target)).add(HighPowerCombat.shield(target));
    }

    private static final class Fixture {
        final GameTestHelper helper; final ServerPlayer player; final EmbeddedChannel channel;
        Fixture(GameTestHelper h, String category, String skill) {
            helper = h; var level = h.getLevel(); UUID id = UUID.randomUUID();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "CA" + id.toString().substring(0, 8)), false);
            player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.getAbilities().instabuild = false;
            player.moveTo(h.absoluteVec(new Vec3(4.5, 3, 2.5)), 0, 0); player.setYHeadRot(0);
            var state = AbilityStorage.get(player); state.changeCategoryClassic(category); state.setLevel(5);
            String[] skills = switch (category) {
                case "mental_out" -> new String[]{"mental_probe", "mental_disrupt", "mental_out_crowd"};
                case "nitrogen_armor" -> new String[]{"nitrogen_armor", "nitrogen_punch", "nitrogen_dome"};
                case "aero_hand" -> new String[]{"aero_jet", "aero_launch", "aero_barrage"};
                case "dark_matter" -> new String[]{"dark_blade", "dark_wings", "dark_construct"};
                default -> new String[]{skill};
            };
            for (String idToLearn : skills) state.learn(idToLearn);
            state.setActivateState(true); select(skill);
            h.assertTrue(HighPowerCombat.enroll(player, 5), "actual exact-combat fixture enrollment");
            HighPowerResources.initializeBudget(player);
        }
        void loadChunks() {
            BlockPos p = player.blockPosition();
            for (int x = (p.getX() - 32) >> 4; x <= (p.getX() + 32) >> 4; x++)
                for (int z = (p.getZ() - 32) >> 4; z <= (p.getZ() + 32) >> 4; z++) player.serverLevel().getChunk(x, z);
        }
        void select(String id) { AbilityStorage.get(player).presets.edit(0, 0, id, AbilityStorage.get(player)::learned); }
        void close() {
            try { player.server.getPlayerList().remove(player); } finally { channel.finishAndReleaseAll(); }
        }
    }
}
