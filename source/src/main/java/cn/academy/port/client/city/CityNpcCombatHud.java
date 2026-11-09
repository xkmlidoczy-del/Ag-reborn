package cn.academy.port.client.city;

import cn.academy.port.city.CityNpcEntity;
import cn.academy.port.client.ClassicTerminalDrawing;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Live server-synced resources remain visible while observing or sparring with a real NPC. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CityNpcCombatHud {
    private static final ClassicTerminalDrawing DRAW=new ClassicTerminalDrawing();
    private CityNpcCombatHud(){}
    @SubscribeEvent public static void draw(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(mc.screen!=null||mc.options.hideGui||!(mc.crosshairPickEntity instanceof CityNpcEntity npc)||npc.esperLevel()==0)return;
        var g=event.getGuiGraphics();g.pose().pushPose();g.pose().translate(9,mc.getWindow().getGuiScaledHeight()-84,0);g.pose().scale(.62f,.62f,1);
        DRAW.begin(g);
        try {
            DRAW.fill(0,0,315,115,0x0b141c,.9);DRAW.fill(0,0,3,115,0x9addea,.8);
            String name=npc.getCustomName()==null?npc.esper().label().getString():npc.getDisplayName().getString();
            DRAW.text(name+"  Lv"+npc.esperLevel(),12,6,19,0xe4edf0,1,0);
            meter("HP",npc.getHealth(),npc.getMaxHealth(),31,0xa5dbca);
            meter("CP",npc.abilityCp(),npc.abilityMaxCp(),55,0x9addea);
            meter("OL",npc.abilityOverload(),npc.abilityMaxOverload(),79,0xd8bd80);
            DRAW.text(Component.translatable("academy.city.combat.mode."+npc.combatMode().name().toLowerCase(java.util.Locale.ROOT)).getString(),303,100,14,0x8daab8,1,2);
        } finally {DRAW.end();g.pose().popPose();}
    }
    private static void meter(String label,float current,float max,int y,int color) {
        DRAW.text(label,12,y,15,color,1,0);DRAW.fill(40,y+4,166,7,color,.14);DRAW.fill(40,y+4,166*Math.max(0,Math.min(1,current/Math.max(1,max))),7,color,.75);
        DRAW.text(Math.round(current)+" / "+Math.round(max),303,y,15,color,1,2);
    }
}
