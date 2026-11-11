package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.environment.SkillEnvironment;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Actual saved block entities are read after a separate client/server process reopens the owned test world. */
final class MacroEnvironmentReloadQa {
    private static final List<BlockPos> POSITIONS=List.of(new BlockPos(4,-61,4),new BlockPos(4,-61,8),new BlockPos(8,-61,4),new BlockPos(8,-61,8));
    private static final List<net.minecraft.world.level.block.Block> ORIGINALS=List.of(Blocks.STONE,Blocks.STONE,Blocks.DIRT,Blocks.GRASS_BLOCK);
    private MacroEnvironmentReloadQa(){}
    static void step(Minecraft mc,String op){
        if(!Boolean.getBoolean("academy.qa.confirmOwnWorld"))throw new IllegalStateException("owned QA world required");
        mc.getSingleplayerServer().submit(()->{
            var world=mc.getSingleplayerServer().overworld();var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if(op.equals("place")){
                if(SkillEnvironment.mode(world)!=SkillEnvironment.Mode.RESTORE)throw new IllegalStateException("actual restore configuration required");
                var state=AbilityStorage.get(p);state.changeCategoryClassic("vecmanip");state.setLevel(5);state.learn("plasma_cannon");state.activated=true;
                if(!HighPowerCombat.enroll(p,5))throw new IllegalStateException("finite owner fixture required");
                for(int i=0;i<POSITIONS.size();i++)world.setBlockAndUpdate(POSITIONS.get(i),ORIGINALS.get(i).defaultBlockState());
                SkillEnvironment.begin(p,"vecmanip.plasma_cannon",5).enqueue(POSITIONS,true);
            }else if(op.equals("markers")){
                for(var pos:POSITIONS)if(!SkillEnvironment.isVacancy(world.getBlockState(pos))||world.getBlockEntity(pos)==null)throw new IllegalStateException("serialized original/marker missing at "+pos);
            }else if(op.equals("restored")){
                for(int i=0;i<POSITIONS.size();i++){var pos=POSITIONS.get(i);if(!world.getBlockState(pos).is(ORIGINALS.get(i))||world.getBlockEntity(pos)!=null)throw new IllegalStateException("original block did not restore after process/world reload at "+pos);}
            }else throw new IllegalArgumentException(op);
            LogUtils.getLogger().info("QA_MACRO_ENV_RELOAD op={} tick={} actualCells={} noSyntheticRestoration=true",op,world.getGameTime(),POSITIONS);
            return null;
        }).join();
    }
}
