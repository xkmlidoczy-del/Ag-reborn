package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.ConstructRuntime;
import cn.academy.port.highpower.HighPowerCategories;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.core.NewSkillBudget;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

/**
 * QA-only driver for V-ADV-01 on the integrated server: {@code vc:setup} enrolls the player at LV5 with the skill learned and
 * builds a small plank-and-glass hut eight blocks ahead; {@code vc:press} / {@code vc:sneak} call the real server press
 * (Sable assembly, control, landing and throw all run for real; only the key-binding authentication is skipped).
 */
final class ConstructQaFixture {
    private static final Logger LOG = LogUtils.getLogger();
    private ConstructQaFixture() {}

    static void run(Minecraft mc, String arg) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) { LOG.warn("QA_VC_NO_SERVER"); return; }
        var id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) return;
            String key = arg.contains(":") ? arg.substring(0, arg.indexOf(':')) : arg;
            switch (key) {
                case "setup" -> setup(p);
                case "structure" -> hut(p);
                case "tower" -> { enroll(p); tower(p); }
                case "probe" -> { var link = cn.academy.port.highpower.ConstructRuntime.heldBody(p); LOG.info("QA_VC_PROBE player={} yaw={} {} ", p.position(), p.getYRot(), cn.academy.port.highpower.ConstructRuntime.probe(p)); }
                case "dummy" -> {
                    var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
                    z.setPos(p.getX(), p.getY(), p.getZ() + (arg.contains(":") ? Integer.parseInt(arg.substring(arg.indexOf(':') + 1)) : 55)); z.setNoAi(true); z.setPersistenceRequired(); z.setSilent(true);
                    z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
                    p.serverLevel().addFreshEntity(z);
                    LOG.info("QA_VC_DUMMY enrolled={} at={}", cn.academy.port.highpower.HighPowerCombat.enroll(z, 5), z.position());
                }
                case "cage" -> {
                    var z = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, p.getBoundingBox().inflate(120)).stream().findFirst().orElse(null);
                    if (z == null) { LOG.info("QA_VC_CAGE no zombie"); break; }
                    var box = z.getBoundingBox(); int solid = 0, total = 0, air = 0;
                    for (int[] c : cn.academy.port.highpower.core.ConstructRules.cage(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                        total++; var st = p.serverLevel().getBlockState(new BlockPos(c[0], c[1], c[2]));
                        if (!st.isAir()) solid++; else air++;
                    }
                    LOG.info("QA_VC_CAGE zombie={} alive={} health={} shellSolid={} shellAir={} shell={}", z.position(), z.isAlive(), z.getHealth(), solid, air, total);
                }
                case "press" -> press(p, false);
                case "sneak" -> press(p, true);
                default -> LOG.warn("QA_VC_UNKNOWN {}", arg);
            }
        });
    }

    private static void setup(ServerPlayer p) {
        enroll(p);
        hut(p);
    }
    /** Geometry only: preserves the separately prepared real learned preset and earned level. */
    private static void hut(ServerPlayer p) {
        BlockPos base = p.blockPosition().offset(-2, 0, 6);
        for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) {
            boolean shell = x == 0 || x == 4 || z == 0 || z == 4 || y == 3;
            if (!shell) continue;
            boolean window = y == 1 && (x == 2 || z == 2) && y != 3;
            p.serverLevel().setBlockAndUpdate(base.offset(x, y, z), (window ? Blocks.GLASS : y == 3 ? Blocks.SPRUCE_PLANKS : Blocks.OAK_PLANKS).defaultBlockState());
        }
    }

    private static void enroll(ServerPlayer p) {
        if (!HighPowerCombat.enabled(p)) LOG.info("QA_VC_ENROLL {}", HighPowerCombat.enroll(p, 5));
        var s = AbilityStorage.get(p);
        s.selectCategory(HighPowerCategories.VECMANIP); s.setLevel(5);
        s.learn(HighPowerCategories.recoveryRoot(HighPowerCategories.VECMANIP)); s.learn(HighPowerSkills.VECTOR_CAGE); s.learn(HighPowerSkills.VECTOR_CONSTRUCT);
        s.activated = true; s.overloadFine = true;
        HighPowerResources.initializeBudget(p);
        LOG.info("QA_VC_SETUP cp={} level={}", HighPowerResources.cp(p), HighPowerGrowth.values(p).position());
    }

    /**
     * A 9x9, 24-storey-block office tower 22 blocks ahead (inside the 24-block grab range): concrete shell, glass window
     * bands, a slab every fourth level and a central core. It stays under the LV5 limits (4096 blocks, 32-block span).
     */
    private static void tower(ServerPlayer p) {
        BlockPos base = p.blockPosition().offset(-4, 0, 22);
        var level = p.serverLevel(); int count = 0;
        for (int y = 0; y < 24; y++) for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) {
            boolean edge = x == 0 || x == 8 || z == 0 || z == 8, corner = (x == 0 || x == 8) && (z == 0 || z == 8);
            boolean slab = y % 4 == 0 || y == 23, core = x == 4 && z == 4 && y > 0 && y < 23;
            if (!edge && !slab && !core) continue;
            boolean glass = edge && !corner && !slab && y % 4 != 3 && (x % 2 == 0 && z % 2 == 0 || (x == 0 || x == 8) && z % 2 == 0 || (z == 0 || z == 8) && x % 2 == 0);
            var block = glass ? Blocks.GLASS : slab ? Blocks.SMOOTH_STONE : corner ? Blocks.QUARTZ_PILLAR : core ? Blocks.IRON_BLOCK : Blocks.WHITE_CONCRETE;
            level.setBlockAndUpdate(base.offset(x, y, z), block.defaultBlockState()); count++;
        }
        LOG.info("QA_VC_TOWER blocks={} span=24 base={}", count, base);
    }

    private static void press(ServerPlayer p, boolean sneak) {
        var q = HighPowerGrowth.values(p).position();
        p.setShiftKeyDown(sneak);
        boolean ok = ConstructRuntime.press(p, true, true, NewSkillBudget.cp(NewSkillBudget.Skill.CONSTRUCT, q), NewSkillBudget.cooldownTicks(NewSkillBudget.Skill.CONSTRUCT, q));
        p.setShiftKeyDown(false);
        LOG.info("QA_VC_PRESS sneak={} ok={} held={} preview={}", sneak, ok, ConstructRuntime.heldBody(p), ConstructRuntime.previewing(p));
    }
}
