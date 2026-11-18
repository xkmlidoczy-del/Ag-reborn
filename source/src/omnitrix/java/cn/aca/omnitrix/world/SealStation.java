package cn.aca.omnitrix.world;

import cn.aca.omnitrix.Kind;
import cn.aca.omnitrix.Omnitrix;
import cn.aca.omnitrix.WristService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SealStation extends Block {
    private final Kind kind;

    public SealStation(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(kind == Kind.CELESTIAL ? Omnitrix.STAR_CORE.get() : Omnitrix.CARNA_CORE.get())) {
            if (player instanceof ServerPlayer server) {
                if (!WristService.eligible(server) || !server.mayInteract(level, pos)) return ItemInteractionResult.FAIL;
                var watch = Omnitrix.watch(kind).getDefaultInstance();
                if (!player.getAbilities().instabuild && stack.getCount() == 1) {
                    player.setItemInHand(hand, watch);
                } else {
                    stack.consume(1, player);
                    if (!player.getInventory().add(watch)) player.drop(watch, false);
                }
                level.playSound(null, pos, Omnitrix.LATCH.get(), SoundSource.BLOCKS, .85f, .9f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && server.mayInteract(level, pos)) {
            if (kind == Kind.CARNITRIX && Omnitrix.wrist(server).kind() == Kind.CARNITRIX) WristService.unequip(server, true);
            else WristService.tell(server, kind == Kind.CARNITRIX ? "carni_station_hint" : "star_station_hint");
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
