package cn.academy.port.city;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Station-owned gate block; ordinary player-placed iron doors are never controlled by the timetable. */
public final class CityPlatformGate {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("academy");
    public static final DeferredBlock<DoorBlock> GATE = BLOCKS.register("platform_gate", () ->
            new DoorBlock(BlockSetType.IRON, BlockBehaviour.Properties.of().strength(5.0f, 6.0f)
                    .noOcclusion().sound(SoundType.METAL)));

    private CityPlatformGate() {}
    static void register(IEventBus bus) { BLOCKS.register(bus); }
}
