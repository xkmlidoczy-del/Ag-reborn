package cn.aca.omnitrix.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DispenserBlock.class)
public interface DispenserAccess {
    @Invoker("dispenseFrom")
    void omnitrix$dispense(ServerLevel level, BlockState state, BlockPos pos);
}
