package cn.academy.port.city;

import cn.academy.port.machine.MachineDeveloperBlock;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.wind.ClassicWindBlock;
import cn.academy.port.wind.ClassicWindGenerators;
import cn.academy.port.wind.ClassicWindMainBlockEntity;
import cn.academy.port.wireless.ClassicWirelessDevices;
import cn.academy.port.wireless.ClassicWirelessNodeBlockEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** D7 second-school public laboratory: real devices, generated once in new chunks. */
final class CityLabPlan {
    static final String NODE_NAME = "D7 Campus Laboratory";
    static final BlockPos NODE = new BlockPos(-225, 8, -105);
    static final List<BlockPos> MACHINES = List.of(new BlockPos(-238, 0, -109),
            new BlockPos(-233, 0, -109), new BlockPos(-217, 0, -109), new BlockPos(-212, 0, -109));
    static final List<BlockPos> WIND_BASES = List.of(new BlockPos(-236, 21, -110),
            new BlockPos(-214, 21, -110), new BlockPos(-236, 21, -99), new BlockPos(-214, 21, -99));

    private CityLabPlan() {}

    private static final class Layout {
        static final Map<BlockPos, BlockState> BLOCKS = create();
        private static Map<BlockPos, BlockState> create() {
            Map<BlockPos, BlockState> blocks = new HashMap<>();
            var machine = MachineDevelopers.ADVANCED.get().defaultBlockState()
                    .setValue(MachineDeveloperBlock.FACING, Direction.NORTH);
            for (var origin : MACHINES) for (int part = 0; part < 8; part++) {
                blocks.put(origin.offset(MachineDeveloperBlock.offset(part, Direction.NORTH)),
                        machine.setValue(MachineDeveloperBlock.PART, part));
            }
            blocks.put(NODE, ClassicWirelessDevices.ADVANCED.get().defaultBlockState());
            var base = ClassicWindGenerators.BASE.get().defaultBlockState();
            var pillar = ClassicWindGenerators.PILLAR.get().defaultBlockState();
            var main = ClassicWindGenerators.MAIN.get().defaultBlockState();
            for (var origin : WIND_BASES) {
                for (int part = 0; part < 2; part++) blocks.put(origin.above(part), base.setValue(ClassicWindBlock.PART, part));
                for (int y = 2; y <= 9; y++) blocks.put(origin.above(y), pillar);
                for (int part = 0; part < 3; part++) {
                    var root = origin.above(10);
                    blocks.put(root.offset(ClassicWindBlock.offset(main, part)), main.setValue(ClassicWindBlock.PART, part));
                }
            }
            return Map.copyOf(blocks);
        }
    }

    static BlockState blockAt(int x, int y, int z) { return Layout.BLOCKS.get(new BlockPos(x, y, z)); }

    /** Populate only newly generated block entities; existing player machines are never recharged this way. */
    static void initialize(BlockPos pos, BlockEntity entity) {
        if (MACHINES.contains(pos) && entity instanceof MachineDeveloperBlockEntity machine)
            machine.battery().energy(100_000);
        if (NODE.equals(pos) && entity instanceof ClassicWirelessNodeBlockEntity node) node.setNodeName(NODE_NAME);
        if (entity instanceof ClassicWindMainBlockEntity main
                && WIND_BASES.stream().anyMatch(base -> base.above(10).equals(pos)))
            main.setItem(0, new ItemStack(ClassicWindGenerators.FAN.get()));
    }
}
