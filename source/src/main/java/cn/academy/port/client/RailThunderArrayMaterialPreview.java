package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;
import cn.academy.port.highpower.RailThunderArray;

/** Opt-in synthetic material fixture. Never inserts a production receipt, casts, hurts, or sends a packet. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class RailThunderArrayMaterialPreview {
    private static final boolean ENABLED=Boolean.getBoolean("academy.railArrayMaterialPreview");
    /** Staged contact fixture (QA only): -Dacademy.railArrayMaterialPreview.hit=true marks the staged segments as confirmed hits. */
    private static final boolean HIT_FIXTURE=Boolean.getBoolean("academy.railArrayMaterialPreview.hit");
    private static ClientLevel world;
    private static Entity player;
    private static Object connection;
    private static long session;
    private static int materialGeneration;
    private static Vec3 origin,forward,side,up;
    private static boolean active,baseline,hold;
    private static int phase=3,controllers=1,optionalRendered,classicRendered;
    static void beginRender(){optionalRendered=0;classicRendered=0;}
    static void rendered(boolean optional){if(optional)optionalRendered++;else classicRendered++;}
    private RailThunderArrayMaterialPreview(){}
    static void clear(){active=false;world=null;player=null;connection=null;origin=null;}
    static boolean active(){
        if(!ENABLED||!active)return false;
        Minecraft mc=Minecraft.getInstance();
        if(materialGeneration!=RailThunderArrayGrandMaterial.generation()||mc.level!=world||mc.player!=player||mc.getConnection()!=connection||!AcademyClient.sameInputSessionEpoch(session)||mc.player==null||!mc.player.isAlive()){clear();return false;}
        return mc.screen==null&&mc.isWindowActive();
    }
    static boolean baseline(){return baseline;}
    static double age(){return phase+.5;}
    static List<RailThunderArray.Visual> frames(){
        if(!active())return List.of();
        List<RailThunderArray.Visual> result=new ArrayList<>();
        double[] lateral={8,20,12},vertical={3,-1,8};
        for(int c=0;c<controllers;c++){
            Vec3 start=origin.add(side.scale((c%2)*2.2)).add(up.scale((c/2)*2.2));
            List<Vec3> axes=new ArrayList<>();List<RailThunderArray.Segment> segments=new ArrayList<>();
            for(int a=0;a<3;a++){
                Vec3 delta=forward.scale(20).add(side.scale(lateral[a])).add(up.scale(vertical[a]));
                axes.add(delta.normalize());
                double relative=hold?age():age()-a;
                if(relative>=0&&relative<5)segments.add(new RailThunderArray.Segment(a,-1,new UUID(0,0),start,start.add(delta),HIT_FIXTURE?RailThunderArrayState.HIT:RailThunderArrayState.MISS));
            }
            result.add(new RailThunderArray.Visual(player.getId(),player.getUUID(),1,1+c,1,1,hold?RailThunderArrayState.HOLD:RailThunderArrayState.TAP,0,start,axes,segments,0));
        }
        return List.copyOf(result);
    }
    static double segmentAge(int axis){return hold?age():age()-axis;}
    @SubscribeEvent public static void key(InputEvent.Key event){
        if(!ENABLED||event.getAction()!=GLFW.GLFW_PRESS)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.screen!=null||!mc.isWindowActive()||!mc.hasSingleplayerServer())return;
        if(event.getKey()==GLFW.GLFW_KEY_F8){
            if(active){clear();return;}
            var camera=mc.gameRenderer.getMainCamera();var l=camera.getLookVector();var u=camera.getUpVector();
            forward=new Vec3(l.x(),l.y(),l.z()).normalize();up=new Vec3(u.x(),u.y(),u.z()).normalize();side=forward.cross(up).normalize();
            if(side.lengthSqr()<.5)return;
            origin=camera.getPosition().add(forward.scale(12)).add(side.scale(-5)).add(up.scale(-.5));
            world=mc.level;player=mc.player;connection=mc.getConnection();session=AcademyClient.inputSessionEpoch();materialGeneration=RailThunderArrayGrandMaterial.generation();active=true;phase=3;controllers=1;baseline=false;hold=false;
        }else if(active()&&event.getKey()==GLFW.GLFW_KEY_F9){if((event.getModifiers()&GLFW.GLFW_MOD_SHIFT)!=0)hold=!hold;else phase=(phase+1)%5;}
        else if(active()&&event.getKey()==GLFW.GLFW_KEY_F10){if((event.getModifiers()&GLFW.GLFW_MOD_SHIFT)!=0)controllers=controllers==1?4:1;else baseline=!baseline;}
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        if(!active())return;var mc=Minecraft.getInstance();var g=event.getGuiGraphics();
        String[] lines={"STAGED GEOMETRY FIXTURE - NO CAST / NO DAMAGE",(baseline?".55 ORIGINAL":optionalRendered==controllers?".56 OPTIONAL":optionalRendered==0?".55 FALLBACK":"MIXED FALLBACK")+" | "+(hold?"NET":"RAILS")+" | phase "+age()+" | "+controllers+" controllers","F8 exit | F9 phase / Shift mode | F10 original / Shift count"};
        int w=0;for(String line:lines)w=Math.max(w,mc.font.width(line));g.fill(3,3,w+13,38,0xD010151C);
        for(int i=0;i<lines.length;i++)g.drawString(mc.font,lines[i],7,7+i*10,i==0?0xFFFFC765:0xFFE3ECF2,true);
    }
}
