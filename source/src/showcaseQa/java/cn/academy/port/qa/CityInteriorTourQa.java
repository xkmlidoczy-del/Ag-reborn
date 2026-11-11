package cn.academy.port.qa;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Camera look only: no movement, scene reconstruction or extra decoration. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CityInteriorTourQa {
    private static float yaw,delta;private static int duration,at;
    public static void pan(Minecraft mc,String value){var a=value.split(",");yaw=mc.player.getYRot();delta=Float.parseFloat(a[0]);duration=Integer.parseInt(a[1]);at=0;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){if(duration<=0||at>=duration)return;var mc=Minecraft.getInstance();if(mc.player==null)return;float t=(float)++at/duration;mc.player.setYRot(yaw+delta*(float)(.5-.5*Math.cos(t*Math.PI)));}
}
