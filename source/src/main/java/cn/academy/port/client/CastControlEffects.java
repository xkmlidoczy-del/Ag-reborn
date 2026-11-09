package cn.academy.port.client;

import cn.academy.port.VisualTierConfig;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Hand interfaces follow the same sampled motion and fade as the character. No extra network or gameplay state. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CastControlEffects {
    private CastControlEffects() {}
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc=Minecraft.getInstance(); var tier=VisualTierConfig.tier();
        if(mc.level==null || mc.player==null || tier.layers<3) return;
        double time=mc.level.getGameTime()+e.getPartialTick().getGameTimeDeltaPartialTick(false);
        var out=new FxLib.Builder(); int count=0;
        for(var p:mc.level.players().stream().sorted(java.util.Comparator.comparingDouble(p->p.distanceToSqr(mc.player))).toList()) {
            if(p.distanceToSqr(mc.player)>32*32) continue;
            var profile=CastPoseClient.profile(p); var motion=CastPoseClient.motion(p,time);
            if(profile==null || motion.strength()<=0) continue;
            if(++count>8) break;
            var look=p.getLookAngle(); var f=new Point(look.x,look.y,look.z);
            var wrist=CastPoseClient.handAnchor(p,time,e.getPartialTick().getGameTimeDeltaPartialTick(false));
            var hand=new Point(wrist.x,wrist.y,wrist.z);
            out.add(CastControlDecor.fx(profile,hand,f,time,motion.strength(),tier));
        }
        VisualKit.render(e,out.build(),tier,2048);
    }
}
