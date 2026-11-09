package cn.academy.port.client;

import cn.academy.port.VisualTierConfig;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;

/** Only the successful server relocation emits this bounded arrival cue, including cross-dimension arrivals. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ConfirmedTeleportArrivalEffects {
    private record Arrival(Vec3 at,double began) {}
    private static final ArrayList<Arrival> ARRIVALS=new ArrayList<>();
    private static Object world;
    private ConfirmedTeleportArrivalEffects() {}
    public static void receive(CompoundTag t) {
        var mc=Minecraft.getInstance();
        if(t==null || !"location_teleport_arrival".equals(t.getString("kind")) || !t.getBoolean("success") || mc.level==null || mc.player==null
                || !mc.level.dimension().location().toString().equals(t.getString("dimension")) || !t.hasUUID("entity_uuid")
                || !t.contains("entity",Tag.TAG_INT) || !t.contains("token",Tag.TAG_LONG) || !t.contains("input",Tag.TAG_LONG)
                || !(mc.level.getEntity(t.getInt("entity")) instanceof Player p) || !p.getUUID().equals(t.getUUID("entity_uuid"))) return;
        for(String k:new String[]{"x","y","z"})if(!t.contains(k,Tag.TAG_DOUBLE)||!Double.isFinite(t.getDouble(k)))return;
        if(world!=mc.level){ARRIVALS.clear();world=mc.level;}
        if(!CastPoseClient.acceptedRelease("location_teleport",p.getId(),p.getUUID(),t.getLong("input"),0,t.getLong("token"),false))return;
        if(ARRIVALS.size()>=8)ARRIVALS.removeFirst();
        ARRIVALS.add(new Arrival(new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z")),mc.level.getGameTime()));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();
        if(world!=mc.level || mc.level==null){ARRIVALS.clear();world=mc.level;return;}
        double time=mc.level.getGameTime()+(mc.isPaused()?0:e.getPartialTick().getGameTimeDeltaPartialTick(false));
        ARRIVALS.removeIf(a->time-a.began()>24);
        var tier=VisualTierConfig.tier();if(!tier.enhanced())return;
        var parts=new FxLib.Builder();
        for(var a:ARRIVALS){
            if(a.at().distanceToSqr(e.getCamera().getPosition())>64*64)continue;
            double age=Math.max(0,time-a.began()),fade=Math.max(0,1-age/24);Point feet=new Point(a.at().x,a.at().y+.04,a.at().z);
            parts.add(FxLib.groundGlow(feet,.1,CastSignatureDecor.TP,1,.4+age*.12,180*fade,tier));
            parts.add(FxMotifs.reticle(feet,new Point(1,0,0),new Point(0,0,1),.8+age*.025,age*.04,0xE6FBFF,210*fade,tier));
            parts.add(FxLib.lensStreak(feet.plus(new Point(0,1,0)),1.2,CastSignatureDecor.TP,140,fade,tier));
        }
        VisualKit.render(e,parts.build(),tier,700);
    }
}
