package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.skill.LightShield;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/** QA-only: drives classic skills through their real server entry points on the integrated server ({@code ls:start|release}). */
final class ClassicQaFixture {
    private static final Logger LOG = LogUtils.getLogger();
    private ClassicQaFixture() {}

    static void lightShield(Minecraft mc, String arg) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        var id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) return;
            if (arg.equals("start")) {
                var s = AbilityStorage.get(p);
                s.selectCategory("meltdowner"); s.setLevel(5); s.learn(LightShield.ID); s.activated = true; s.overloadFine = true;
                s.cp = s.maxCp(); AbilityStorage.save(p);
                LOG.info("QA_LS_START {}", LightShield.start(p));
            } else LOG.info("QA_LS_RELEASE {}", LightShield.release(p));
        });
    }

    /**
     * {@code wind:build} builds a complete wind tower the way the native fixtures do (every part with its real PART/FACING, 8 pillars, a fan in the
     * main slot) 12 blocks east of the player. {@code wind:blocked} then puts one stone block in the fan's clearance plane.
     */
    static void wind(Minecraft mc, String arg) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        var id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) return;
            var level = p.serverLevel();
            var origin = net.minecraft.core.BlockPos.containing(p.getX() + 12, p.getY() - 1, p.getZ());
            var facing = net.minecraft.core.Direction.WEST;
            if (arg.equals("blocked")) {
                var main = origin.above(10);
                level.setBlock(main.offset(-1, 3, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                LOG.info("QA_WIND_BLOCKED at {}", main.offset(-1, 3, 0));
                return;
            }
            for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) for (int dy = 0; dy <= 14; dy++) level.setBlock(origin.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            place(level, origin, cn.academy.port.wind.ClassicWindGenerators.BASE.get(), facing);
            for (int i = 0; i < 8; i++) place(level, origin.above(i + 2), cn.academy.port.wind.ClassicWindGenerators.PILLAR.get(), net.minecraft.core.Direction.NORTH);
            place(level, origin.above(10), cn.academy.port.wind.ClassicWindGenerators.MAIN.get(), facing);
            if (level.getBlockEntity(origin.above(10)) instanceof cn.academy.port.wind.ClassicWindMainBlockEntity main) main.setItem(0, new net.minecraft.world.item.ItemStack(cn.academy.port.wind.ClassicWindGenerators.FAN.get()));
            LOG.info("QA_WIND_BUILT origin={} baseBE={} mainBE={}", origin, level.getBlockEntity(origin), level.getBlockEntity(origin.above(10)));
        });
    }

    private static void place(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos origin, cn.academy.port.wind.ClassicWindBlock block, net.minecraft.core.Direction facing) {
        var state = block.defaultBlockState().setValue(cn.academy.port.wind.ClassicWindBlock.FACING, facing);
        for (int part = 0; part < cn.academy.port.wind.ClassicWindRules.parts(block.kind); part++)
            level.setBlock(origin.offset(cn.academy.port.wind.ClassicWindBlock.offset(state, part)), state.setValue(cn.academy.port.wind.ClassicWindBlock.PART, part),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
    }
}
