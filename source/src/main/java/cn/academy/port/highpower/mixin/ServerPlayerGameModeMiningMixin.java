package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.BeamEtchingMining;
import cn.academy.port.highpower.NativeMiningAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMiningMixin implements NativeMiningAccess {
    @Override public int academy$nativeMiningContractVersion() { return 0; }
    @Redirect(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/CommonHooks;fireBlockBreak(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/GameType;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/neoforged/neoforge/event/level/BlockEvent$BreakEvent;"), require = 1, expect = 1, allow = 1)
    private static BlockEvent.BreakEvent academy$miningBreak(Level level, GameType type, ServerPlayer player, BlockPos pos, BlockState state) {
        return BeamEtchingMining.nativeBreak(level, type, player, pos, state);
    }
    @Redirect(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;mineBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"), require = 1, expect = 1, allow = 1)
    private static void academy$miningTool(ItemStack stack, Level level, BlockState state, BlockPos pos, Player player) {
        BeamEtchingMining.nativeMine(stack, level, state, pos, player);
    }
    @Redirect(method = "removeBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;onDestroyedByPlayer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/level/material/FluidState;)Z"), require = 1, expect = 1, allow = 1)
    private static boolean academy$miningRemove(BlockState state, Level level, BlockPos pos, Player player, boolean harvest, FluidState fluid) {
        return BeamEtchingMining.nativeRemove(state, level, pos, player, harvest, fluid);
    }
}
