package cn.academy.port.machine;
import net.minecraft.core.BlockPos;public class MachineDeveloperBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {public MachineDeveloperBlockEntity root;public MachineDeveloperBlockEntity(BlockPos p){super(p);root=this;}public MachineDeveloperBlockEntity origin(){return root;}}
