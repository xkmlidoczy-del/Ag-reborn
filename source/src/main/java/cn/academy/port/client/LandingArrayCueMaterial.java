package cn.academy.port.client;

import cn.academy.port.HighPowerVisualConfig;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** NEW351-only original coordinate atlas and soft shoulder, with a complete classic fallback. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class LandingArrayCueMaterial {
    private static final String[] PATHS={"port/white","effects/glow_line","effects/tp_mark/0","effects/tp_mark/1","effects/tp_mark/2","effects/tp_mark/3","effects/tp_mark/4","effects/tp_mark/5","effects/tp_mark/6"};
    private static final String[] HASHES={"7ac2abfce2be8b46dd9826d597d94edeefc1570b4a992622721b90a1850e3ee9","0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361","952cca68c146cdde4f69b86be92ef0c0b0d79857f93aedb1a2225dba52a4b3ff","edf556933b5a773699938386caa07c3cdb23cd9c7ca1ee7db4cdeb5aaa361b60","72c6836532204fe659b9e3951c2efa6b60c4584f6b875f7b78e89e455d24c68d","1e99c3a45293bf26b293fa9b465e770871cbcebf24b600e55f7695cbc999bd9b","9e29f5c1002e46c930fe69ba4455d7343f90a184f595917863fd9ea2225a0bde","72f1adc9482023aacbf7e522d56d28358332b28474082a9510f2084ca1bf9f75","561d2d25207ef04d3b4d802f29064067733ba4eda0d3c0ac0c28f520b43baaf4"};
    private static volatile boolean ready;
    private static volatile int generation;
    private LandingArrayCueMaterial(){}
    static ResourceLocation texture(int index){if(index<0||index>=PATHS.length)throw new IllegalArgumentException("material index");return ResourceLocation.fromNamespaceAndPath("academy","textures/"+PATHS[index]+".png");}
    static int generation(){return generation+RailThunderArraySoftMaterial.generation();}
    static boolean enabled(){try{return !Boolean.getBoolean("academy.landingArrayCues.forceUnavailable")&&ready&&RailThunderArraySoftMaterial.available()&&HighPowerVisualConfig.SPEC.isLoaded()&&HighPowerVisualConfig.LANDING_ARRAY_CUES.get();}catch(RuntimeException unavailable){return false;}}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)resources->{
            ready=false;generation++;
            try{
                for(int i=0;i<PATHS.length;i++)try(var in=resources.getResourceOrThrow(texture(i)).open()){
                    byte[] data=in.readNBytes(65537);
                    if(data.length>65536||!HASHES[i].equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data))))return;
                }
                ready=true;
            }catch(Exception unavailable){ready=false;}
        });}
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event){event.getDispatcher().register(Commands.literal("aca_vfx2").then(Commands.literal("landing_array")
        .then(Commands.literal("on").executes(c->set(true))).then(Commands.literal("off").executes(c->set(false))).then(Commands.literal("status").executes(c->status()))));}
    private static int set(boolean on){if(!HighPowerVisualConfig.SPEC.isLoaded())return 0;HighPowerVisualConfig.LANDING_ARRAY_CUES.set(on);HighPowerVisualConfig.SPEC.save();return status();}
    private static int status(){var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(Component.translatable("academy.vfx2.landing_array.status",enabled(),ready,generation),false);return 1;}
}
