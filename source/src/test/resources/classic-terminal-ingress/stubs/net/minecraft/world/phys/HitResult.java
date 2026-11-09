package net.minecraft.world.phys;
import net.minecraft.core.BlockPos;public class HitResult {public enum Type{MISS,BLOCK,ENTITY}private final Type type;private final BlockPos pos;public HitResult(Type t,BlockPos p){type=t;pos=p;}public Type getType(){return type;}public BlockPos getBlockPos(){return pos;}}
