package cn.academy.port.gametest;

import static cn.academy.port.gametest.CanonSkillAuditRuntimeTests.*;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerCombat;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Player-path effect audit of every controllable skill of the original four systems, set up exactly as a tester with commands
 * would (/aim cat, learn_all, learn_hp, level 5, fullcp) and cast through the real key protocol: a tap, a 1.2 s hold and a
 * 2.5 s hold aimed at the ground. The shared effect probe records what changed in the world; skills whose effect needs a
 * multi-step setup (anchors, marks, coordinates) are listed and covered by their own runtime tests.
 */
@GameTestHolder("academy_classic_audit")
@PrefixGameTestTemplate(false)
public final class ClassicSkillAuditRuntimeTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Set<String> CATEGORIES = Set.of("electromaster", "meltdowner", "teleporter", "vecmanip");
    private static final Vec3 START = new Vec3(8.5, 2, 3.5);
    private ClassicSkillAuditRuntimeTests() {}

    @GameTestGenerator public static Collection<TestFunction> audit() {
        var only = Set.of(System.getProperty("academy.audit.only", "").split(","));
        var tests = new ArrayList<TestFunction>();
        for (var skill : SkillCatalog.REGISTERED) {
            if (!CATEGORIES.contains(skill.category()) || !only.contains("") && !only.contains(skill.id())) continue;
            String id = skill.id(), category = skill.category();
            tests.add(new TestFunction("classic_" + id, "academy_classic_audit.classic_" + id, "academy_classic_audit:arena", Rotation.NONE, 220, 0, true, false, 1, 1, false,
                    h -> run(h, category, id)));
        }
        LOG.info("CLASSIC_AUDIT_GENERATED {} only={}", tests.size(), only);
        return tests;
    }

    private static void command(ServerPlayer p, String line) {
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), line);
    }

    private static void run(GameTestHelper h, String category, String id) {
        var level = h.getLevel(); level.setDayTime(18000);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 1, z), Blocks.DIRT); h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        var player = join(h, "CL");
        player.moveTo(h.absoluteVec(START), 0, 6); player.setYHeadRot(0); player.setOnGround(true);
        command(player, "aim cat " + category); command(player, "aim learn_all"); command(player, "aim learn_hp"); command(player, "aim level 5"); command(player, "aim fullcp");
        var state = AbilityStorage.get(player); state.setActivateState(true);
        boolean learned = state.learned(id); state.presets.edit(0, 0, id, state::learned);
        var mobs = new ArrayList<LivingEntity>();
        for (double d : new double[]{1.8, 3, 5, 8, 12}) mobs.add(h.spawn(EntityType.ZOMBIE, START.add(0, 0, d)));
        mobs.add(h.spawn(EntityType.ZOMBIE, START.add(4, 0, 3)));
        for (var m : mobs) { ((Zombie) m).setPersistenceRequired(); m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 20, false, false)); }
        boolean[] accepted = new boolean[3]; Probe[] snap = new Probe[1]; Early[] pre = new Early[1]; String[] quick = {""}; double[] peak = {0}; int[] inv = {0};
        Runnable aimMob = () -> mobs.stream().filter(LivingEntity::isAlive).min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(player)))
                .ifPresent(m -> aimAt(player, m.getBoundingBox().getCenter()));
        Runnable tick = () -> { if (!player.isRemoved()) { player.setOnGround(true); NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player)); NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player)); } };
        for (int k = 4; k < 205; k++) h.runAfterDelay(k, tick);
        h.runAfterDelay(4, () -> { for (var m : mobs) if (m instanceof Zombie z) z.setTarget(player); aimMob.run();
            snap[0] = probe(h, player, null, mobs); pre[0] = early(h, player, mobs); inv[0] = invHash(player); });
        // 1: tap
        h.runAfterDelay(5, () -> accepted[0] = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0")));
        h.runAfterDelay(7, () -> AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_release", "0")));
        h.runAfterDelay(8, () -> { if (pre[0] != null) quick[0] = earlyDiff(pre[0], early(h, player, mobs)); });
        // 2: 1.2 s hold on the nearest enemy
        h.runAfterDelay(40, () -> { command(player, "aim cd_clear"); command(player, "aim fullcp"); aimMob.run();
            accepted[1] = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0")); });
        for (int k = 41; k < 64; k++) h.runAfterDelay(k, aimMob);
        h.runAfterDelay(64, () -> AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_release", "0")));
        // 3: 2.5 s hold aimed at the ground three blocks ahead (beams that dig, ground strikes, markers)
        h.runAfterDelay(100, () -> { command(player, "aim cd_clear"); command(player, "aim fullcp"); aimAt(player, player.position().add(.6, 0, 3));
            accepted[2] = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0")); });
        h.runAfterDelay(150, () -> AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_release", "0")));
        for (int k = 6; k < 200; k += 3) h.runAfterDelay(k, () -> { if (snap[0] != null) for (var m : mobs) if (m.isAlive()) peak[0] = Math.max(peak[0], m.position().distanceTo(snap[0].mobPos().get(m))); });
        h.runAfterDelay(200, () -> {
            try {
                String effect = snap[0] == null ? "" : (diff(h, snap[0], player, null, mobs) + " " + quick[0] + (peak[0] > 1.2 ? String.format(Locale.ROOT, " mob_peak=%.1f", peak[0]) : "")
                        + (invHash(player) != inv[0] ? " inventory" : "")).replace("NONE", "").replaceAll("\\bspawned=1\\b", "").trim();
                LOG.info("CLASSIC_EFFECT {} {} learned={} enrolled={} accepted={}/{}/{} effect=[{}]", category, id, learned, HighPowerCombat.enabled(player),
                        accepted[0], accepted[1], accepted[2], effect);
            } finally {
                for (var m : mobs) m.discard();
                player.server.getPlayerList().remove(player);
            }
            h.succeed();
        });
    }
}
