package cn.academy.port.wind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Atomic all-cell placement and identity-checked, once-only multiblock teardown. */
public final class ClassicWindStructure {
    private static final Map<Level,Set<BlockPos>> MUTATING=new WeakHashMap<>();
    private record Replaced(BlockPos pos,BlockState state,CompoundTag blockEntity) {}
    private ClassicWindStructure() {}
    public static boolean valid(BlockState state){return state.getBlock() instanceof ClassicWindBlock block&&state.getValue(ClassicWindBlock.PART)<ClassicWindRules.parts(block.kind);}
    public static ClassicWindInventory inventory(Level level,BlockPos pos,BlockState state){if(!valid(state))return null;var origin=ClassicWindBlock.origin(pos,state);if(!level.hasChunkAt(origin)||!complete(level,origin,state))return null;return level.getBlockEntity(origin) instanceof ClassicWindInventory tile&&tile.isOrigin()?tile:null;}
    private static Set<BlockPos> locks(Level level){return MUTATING.computeIfAbsent(level,ignored->new HashSet<>());}
    public static boolean allLoaded(Level level,BlockPos origin,BlockState state){for(int part=0;part<ClassicWindRules.parts(((ClassicWindBlock)state.getBlock()).kind);part++)if(!level.hasChunkAt(origin.offset(ClassicWindBlock.offset(state,part))))return false;return true;}
    public static boolean complete(Level level,BlockPos origin,BlockState state){
        if(!allLoaded(level,origin,state))return false;
        for(int part=0;part<ClassicWindRules.parts(((ClassicWindBlock)state.getBlock()).kind);part++){var current=level.getBlockState(origin.offset(ClassicWindBlock.offset(state,part)));if(current.getBlock()!=state.getBlock()||current.getValue(ClassicWindBlock.PART)!=part||current.getValue(ClassicWindBlock.FACING)!=state.getValue(ClassicWindBlock.FACING))return false;}
        return true;
    }
    /** Validation includes all occupied cells, world bounds, loaded chunks, edits and living/entity collision. */
    public static boolean canPlace(BlockPlaceContext context,BlockState state){
        var level=context.getLevel();var player=context.getPlayer();var origin=context.getClickedPos();
        for(int part=0;part<ClassicWindRules.parts(((ClassicWindBlock)state.getBlock()).kind);part++){
            var pos=origin.offset(ClassicWindBlock.offset(state,part));
            if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.hasChunkAt(pos)||!level.getBlockState(pos).canBeReplaced(context))return false;
            if(player!=null&&(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand())))return false;
            if(!level.isUnobstructed(state.setValue(ClassicWindBlock.PART,part),pos,player==null?CollisionContext.empty():CollisionContext.of(player)))return false;
        }
        return true;
    }
    /** False means no item is consumed by BlockItem.place; every replaced state/NBT is restored. */
    public static boolean place(BlockPlaceContext context,BlockState state){
        if(!canPlace(context,state))return false;
        var level=context.getLevel();var origin=context.getClickedPos();var snapshots=new ArrayList<Replaced>();
        var lock=locks(level);if(!lock.add(origin))return false;
        boolean placed=false;
        try {
            for(int part=0;part<ClassicWindRules.parts(((ClassicWindBlock)state.getBlock()).kind);part++){
                var pos=origin.offset(ClassicWindBlock.offset(state,part));var old=level.getBlockState(pos);var oldEntity=level.getBlockEntity(pos);
                snapshots.add(new Replaced(pos,old,oldEntity==null?null:oldEntity.saveWithFullMetadata(level.registryAccess())));
                if(!level.setBlock(pos,state.setValue(ClassicWindBlock.PART,part),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS))return false;
            }
            placed=complete(level,origin,state);return placed;
        } finally {
            if(!placed){for(int i=snapshots.size()-1;i>=0;i--){var replaced=snapshots.get(i);level.setBlock(replaced.pos(),replaced.state(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);if(replaced.blockEntity()!=null){var entity=BlockEntity.loadStatic(replaced.pos(),replaced.state(),replaced.blockEntity(),level.registryAccess());if(entity!=null)level.setBlockEntity(entity);}}}
            lock.remove(origin);if(lock.isEmpty())MUTATING.remove(level);
            for(var replaced:snapshots){level.invalidateCapabilities(replaced.pos());level.updateNeighborsAt(replaced.pos(),level.getBlockState(replaced.pos()).getBlock());}
        }
    }
    /** Vanilla's clicked-cell destruction/loot remains the sole machine-item drop. Other cells never drop. */
    public static void remove(Level level,BlockPos removed,BlockState state){
        if(!valid(state))return;
        var origin=ClassicWindBlock.origin(removed,state);var lock=locks(level);if(!lock.add(origin))return;
        try {
            var root=level.hasChunkAt(origin)?level.getBlockEntity(origin):null;
            if(root instanceof ClassicWindInventory developer&&developer.getBlockState().getBlock()==state.getBlock()&&developer.isOrigin()&&developer.getBlockState().getValue(ClassicWindBlock.FACING)==state.getValue(ClassicWindBlock.FACING)){Containers.dropContents(level,origin,developer);developer.clearContent();}
            for(int part=0;part<ClassicWindRules.parts(((ClassicWindBlock)state.getBlock()).kind);part++){
                var pos=origin.offset(ClassicWindBlock.offset(state,part));
                if(pos.equals(removed)||!level.hasChunkAt(pos))continue;
                var current=level.getBlockState(pos);
                if(current.getBlock()==state.getBlock()&&current.getValue(ClassicWindBlock.PART)==part&&current.getValue(ClassicWindBlock.FACING)==state.getValue(ClassicWindBlock.FACING))level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
            }
        } finally {lock.remove(origin);if(lock.isEmpty())MUTATING.remove(level);}
    }
}
