package cn.academy.port.qa;

import cn.academy.port.train.TrainManager;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.Items;

/** R5-compatible bootstrap for an explicitly owned disposable world; no new production class is required. */
public final class CityInteriorSaveQa {
    public static boolean step(Minecraft mc,String stage){
        if(mc.getSingleplayerServer()==null||!Boolean.getBoolean("academy.qa.confirmOwnWorld")||!System.getProperty("academy.qa.world","").startsWith("AcademyShowcase-"))throw new IllegalStateException("Owned integrated test world required");
        var report=mc.getSingleplayerServer().submit(()->{
            var w=mc.getSingleplayerServer().getLevel(TrainManager.CITY);var gold=new BlockPos(-162,0,-80);var chest=new BlockPos(-160,0,-80);
            if(w==null||w.getChunkSource().getChunkNow(gold.getX()>>4,gold.getZ()>>4)==null)throw new IllegalStateException("Existing fixture chunk must already be loaded");
            boolean playerBlock=w.getBlockState(gold).is(Blocks.GOLD_BLOCK),storage=w.getBlockEntity(chest)instanceof ChestBlockEntity box&&box.getItem(0).is(Items.IRON_INGOT)&&box.getItem(0).getCount()==3;
            if(!playerBlock||!storage)throw new IllegalStateException("Player-built fixture or its stored contents changed");
            var data=new LinkedHashMap<String,Object>();data.put("stage",stage);data.put("goldRetained",playerBlock);data.put("chestRetained",storage);data.put("health",mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).getHealth());
            if(stage.equals("upgraded"))try{Object group=w.getChunkSource().getGenerator().getClass().getMethod("interiorAt",int.class,int.class).invoke(w.getChunkSource().getGenerator(),-160,-105);if(group!=null)throw new IllegalStateException("Existing school must remain entirely R5");data.put("wholeOldSchoolUsesR5",true);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            data.put("version",net.neoforged.fml.ModList.get().getModContainerById("academy").orElseThrow().getModInfo().getVersion().toString());return data;
        }).join();
        try{Files.writeString(Path.of(System.getProperty("academy.qa.macro.root")).resolve("city-save-"+stage+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}catch(Exception e){throw new IllegalStateException(e);}return true;
    }
}
