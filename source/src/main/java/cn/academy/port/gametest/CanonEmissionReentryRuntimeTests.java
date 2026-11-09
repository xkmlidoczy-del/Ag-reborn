package cn.academy.port.gametest;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.canon.CanonSkillsCombatA;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.CanonEmission;
import cn.academy.port.highpower.HighPowerResources;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real synchronous attack-event reentry against a paid multi-target cast. */
@GameTestHolder("academy_canon_a")
@PrefixGameTestTemplate(false)
public final class CanonEmissionReentryRuntimeTests {
    private CanonEmissionReentryRuntimeTests() {}

    @GameTest(template = "empty", batch = "canon_emission_reentry", timeoutTicks = 100)
    public static void construct_permit_rejects_untrusted_caller_before_payment(GameTestHelper h) {
        var level = h.getLevel(); UUID id = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "CU" + id.toString().substring(0, 8)), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection); level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        try {
            player.setGameMode(GameType.SURVIVAL); player.getAbilities().instabuild = false;
            var state = AbilityStorage.get(player); state.changeCategoryClassic("dark_matter"); state.setLevel(5);
            for (String skill : new String[]{"dark_blade", "dark_wings", "dark_construct"}) state.learn(skill);
            state.setActivateState(true);
            h.assertTrue(state.presets.edit(0, 0, "dark_construct", state::learned), "construct slot mapped");
            h.assertTrue(HighPowerCombat.enroll(player, 5), "player exact ledger enrolled");
            HighPowerResources.initializeBudget(player);
            double before = HighPowerResources.cp(player);
            h.assertTrue(CanonEmission.beginConstruct(player, () -> true, target -> true) == null,
                    "an unrelated server caller cannot acquire a construct capability");
            h.assertTrue(HighPowerResources.cp(player) == before, "unauthorized call does not charge CP");
            h.succeed();
        } finally {
            try { player.server.getPlayerList().remove(player); } finally { channel.finishAndReleaseAll(); }
        }
    }

    @GameTest(template = "empty", batch = "canon_emission_reentry", timeoutTicks = 100)
    public static void preset_switch_to_same_skill_revokes_remaining_emission(GameTestHelper h) {
        var level = h.getLevel(); UUID id = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "CE" + id.toString().substring(0, 8)), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection); level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        Zombie first = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 7.5));
        Zombie second = h.spawn(EntityType.ZOMBIE, new Vec3(5.5, 3, 8.0));
        var hook = new SwitchPreset(player);
        NeoForge.EVENT_BUS.register(hook);
        try {
            player.setGameMode(GameType.SURVIVAL); player.getAbilities().instabuild = false;
            player.moveTo(h.absoluteVec(new Vec3(4.5, 3, 2.5)), 0, 0); player.setYHeadRot(0);
            BlockPos origin = player.blockPosition();
            for (int x = (origin.getX() - 32) >> 4; x <= (origin.getX() + 32) >> 4; x++)
                for (int z = (origin.getZ() - 32) >> 4; z <= (origin.getZ() + 32) >> 4; z++) level.getChunk(x, z);
            level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 3); player.setOnGround(true);
            var state = AbilityStorage.get(player); state.changeCategoryClassic("aero_hand"); state.setLevel(5);
            for (String skill : new String[]{"aero_jet", "aero_launch", "aero_barrage"}) state.learn(skill);
            state.setActivateState(true);
            h.assertTrue(state.presets.edit(0, 0, "aero_barrage", state::learned), "original slot mapped");
            h.assertTrue(state.presets.edit(1, 0, "aero_barrage", state::learned), "other preset maps the same skill");
            h.assertTrue(HighPowerCombat.enroll(player, 5), "player exact ledger enrolled");
            HighPowerResources.initializeBudget(player);
            first.setNoGravity(true); first.setNoAi(true); second.setNoGravity(true); second.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(first, 5) && HighPowerCombat.enroll(second, 5), "target ledgers enrolled");
            BigDecimal beforeFirst = total(first), beforeSecond = total(second); double cp = HighPowerResources.cp(player);
            h.assertTrue(CanonSkillsCombatA.cast(player, "aero_barrage"), "paid attack started");
            h.assertTrue(hook.calls == 1 && state.presets.current() == 1, "first attack hook switched to same-skill preset");
            h.assertTrue(total(first).compareTo(beforeFirst) == 0 && total(second).compareTo(beforeSecond) == 0,
                    "reentrant preset change prevents both pending exact settlements");
            h.assertTrue(HighPowerResources.cp(player) < cp, "already paid cost is not refunded");
            h.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(hook); first.discard(); second.discard();
            try { player.server.getPlayerList().remove(player); } finally { channel.finishAndReleaseAll(); }
        }
    }

    @GameTest(template = "empty", batch = "canon_emission_reentry", timeoutTicks = 100)
    public static void attack_callback_moving_target_outside_actual_skill_range_revokes_hit(GameTestHelper h) {
        double reach = SkillTuning.number("aero_barrage", "reach");
        int fixtureSpan = (int) Math.ceil(reach) + 4;
        var level = h.getLevel(); UUID id = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "CG" + id.toString().substring(0, 8)), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection); level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        Zombie target = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 3, 7.5));
        var hook = new MoveTarget(player, target);
        Map<BlockPos, BlockState> cleared = new LinkedHashMap<>();
        NeoForge.EVENT_BUS.register(hook);
        try {
            player.setGameMode(GameType.SURVIVAL); player.getAbilities().instabuild = false;
            player.moveTo(h.absoluteVec(new Vec3(4.5, 3, 2.5)), 0, 0); player.setYHeadRot(0);
            BlockPos origin = player.blockPosition();
            for (int x = (origin.getX() - fixtureSpan) >> 4; x <= (origin.getX() + fixtureSpan) >> 4; x++)
                for (int z = (origin.getZ() - fixtureSpan) >> 4; z <= (origin.getZ() + fixtureSpan) >> 4; z++) level.getChunk(x, z);
            // The empty template ends before the moved target; generated terrain outside it can block LOS.
            // Clear only the ray corridor and restore it synchronously after the assertion.
            for (int z = origin.getZ() + 4; z <= origin.getZ() + fixtureSpan; z++) {
                for (int y = origin.getY(); y <= origin.getY() + 3; y++) {
                    BlockPos pos = new BlockPos(origin.getX(), y, z);
                    BlockState old = level.getBlockState(pos);
                    if (!old.isAir()) { cleared.put(pos, old); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); }
                }
            }
            level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 3); player.setOnGround(true);
            var state = AbilityStorage.get(player); state.changeCategoryClassic("aero_hand"); state.setLevel(5);
            for (String skill : new String[]{"aero_jet", "aero_launch", "aero_barrage"}) state.learn(skill);
            state.setActivateState(true);
            h.assertTrue(state.presets.edit(0, 0, "aero_barrage", state::learned), "barrage slot mapped");
            h.assertTrue(HighPowerCombat.enroll(player, 5), "player exact ledger enrolled");
            HighPowerResources.initializeBudget(player);
            target.setNoGravity(true); target.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(target, 5), "target ledger enrolled");
            BigDecimal before = total(target); double cp = HighPowerResources.cp(player);
            h.assertTrue(CanonSkillsCombatA.cast(player, "aero_barrage"), "paid attack started");
            h.assertTrue(hook.calls == 1, "real SkillAttackEvent moved the selected target");
            double distance = player.distanceToSqr(target);
            h.assertTrue(distance > reach * reach && distance < 96 * 96 && player.hasLineOfSight(target),
                    "target remains visible and within the shared 96-block limit but exceeds the skill range");
            Vec3 delta = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
            h.assertTrue(delta.normalize().dot(player.getLookAngle().normalize()) >= .7,
                    "target stays in the skill cone, isolating its range guard");
            h.assertTrue(total(target).compareTo(before) == 0, "skill-specific guard rejects the moved target after callback");
            h.assertTrue(HighPowerResources.cp(player) < cp, "already paid cost is not refunded");
            h.succeed();
        } finally {
            cleared.forEach((pos, old) -> level.setBlock(pos, old, 2));
            NeoForge.EVENT_BUS.unregister(hook); target.discard();
            try { player.server.getPlayerList().remove(player); } finally { channel.finishAndReleaseAll(); }
        }
    }

    private static BigDecimal total(Zombie target) {
        return new BigDecimal(HighPowerCombat.healthText(target)).add(HighPowerCombat.shield(target));
    }
    public static final class SwitchPreset {
        final ServerPlayer owner; int calls;
        SwitchPreset(ServerPlayer owner) { this.owner = owner; }
        @SubscribeEvent public void attack(SkillAttackEvent event) {
            if (event.player != owner || !event.skill.equals("aero_hand.aero_barrage")) return;
            calls++;
            if (calls == 1) AbilityStorage.get(owner).presets.switchTo(1);
        }
    }
    public static final class MoveTarget {
        final ServerPlayer owner; final Zombie target; int calls;
        MoveTarget(ServerPlayer owner, Zombie target) { this.owner = owner; this.target = target; }
        @SubscribeEvent public void attack(SkillAttackEvent event) {
            if (event.player != owner || event.target != target || !event.skill.equals("aero_hand.aero_barrage")) return;
            calls++; double beyondReach = SkillTuning.number("aero_barrage", "reach") + 2;
            target.setPos(target.getX(), target.getY(), owner.getZ() + beyondReach);
        }
    }
}
