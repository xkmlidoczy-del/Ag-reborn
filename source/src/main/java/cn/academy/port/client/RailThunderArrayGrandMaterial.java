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

/** Original ACA textures with a separately scoped optional soft shader; no external copied asset. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class RailThunderArrayGrandMaterial {
    private static final String[] PATHS={"port/white","effects/glow_line","effects/railgun/blend_in","effects/railgun/tile","effects/railgun/blend_out","vfx2/radial_glow","vfx2/star_flare","vfx2/shock_ring","vfx2/beam_taper","vfx2/arc_glow","vfx2/ray_spoke"};
    private static final String[] HASHES={"", "0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361", "51a30948bbea0a4f0dac7c332ab2cd14a65624aba8ea21c77bdc71e40ad080af", "3fbc725dc998318d271a8bfa3c188aca38209314703d4bf5bd1e4aa5a69cd5a7", "aae45dd462353dbefbb9f4ee4c3e5ea9a1c2ebceff3004847e2c2fb78eb8325b", "ecde1f7b7d22e5486bb63c9ffdf3cdbd3c084e46d0438ea72f529260b90f3671", "d1430dc2bac76eb5267e0ae7c2f18577435e8a0c541f5a0b0e85b569935eb9e8", "83e27cd664137797e2448bff970b43325b45a6cdcee26e61243e6c2f962e3903", "d4ad7283af8038cd071da9f382a5608310f4d0ee7d4b8df22ec39df97017d206", "ed1c9756def37ca175d957d957e86176558a9b814a1d3b51f65cac8abaeafe23", "e3d5a802c4e80c9b58fbe39f963467e9ce70e4c11a5a9a244b78c8ad71a3ae60"};
    private static volatile boolean ready;
    private static volatile int generation;
    private RailThunderArrayGrandMaterial(){}
    static ResourceLocation texture(int index){if(index<0||index>=PATHS.length)throw new IllegalArgumentException("material index");return ResourceLocation.fromNamespaceAndPath("academy","textures/"+PATHS[index]+".png");}
    /** Shared decoration kit readiness: textures verified by hash and the soft shader linked (independent of the NEW151 switch). */
    static boolean kitReady(){return !Boolean.getBoolean("academy.railArrayGrand.forceUnavailable")&&ready&&RailThunderArraySoftMaterial.available();}
    static int generation(){return generation+RailThunderArraySoftMaterial.generation();}
    static boolean enabled(){try{return !Boolean.getBoolean("academy.railArrayGrand.forceUnavailable")&&ready&&RailThunderArraySoftMaterial.available()&&HighPowerVisualConfig.SPEC.isLoaded()&&HighPowerVisualConfig.RAIL_ARRAY_GRAND.get();}catch(RuntimeException unavailable){return false;}}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)resources->{
            ready=false;generation++;
            try{
                for(int i=1;i<PATHS.length;i++)try(var in=resources.getResourceOrThrow(texture(i)).open()){
                    byte[] data=in.readNBytes(65537);
                    if(data.length>65536||!HASHES[i].equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data))))return;
                }
                ready=true;
            }catch(Exception unavailable){ready=false;}
        });}
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event){event.getDispatcher().register(Commands.literal("aca_vfx2").then(Commands.literal("rail_array")
        .then(Commands.literal("on").executes(c->set(true))).then(Commands.literal("off").executes(c->set(false))).then(Commands.literal("status").executes(c->status()))));}
    private static int set(boolean on){if(!HighPowerVisualConfig.SPEC.isLoaded())return 0;HighPowerVisualConfig.RAIL_ARRAY_GRAND.set(on);HighPowerVisualConfig.SPEC.save();return status();}
    private static int status(){var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(Component.translatable("academy.vfx2.rail_array.status",enabled(),ready,generation),false);return 1;}
}
