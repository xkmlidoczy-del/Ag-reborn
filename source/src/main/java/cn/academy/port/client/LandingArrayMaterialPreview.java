package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

/** Launch-only, singleplayer material fixture. No receipt, cast, target, damage, packet or gameplay clock. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class LandingArrayMaterialPreview {
    private static final String PREFIX="academy.landingArrayCues.preview";
    private static final boolean ENABLED=Boolean.getBoolean("academy.visual.qa") && Boolean.getBoolean(PREFIX);
    private static final Settings SETTINGS=settings();
    private static Object world,player,connection;
    private static long session;
    private static List<Sample> samples=List.of();
    private static boolean shown=true;
    private static int optionalRendered,classicRendered;
    record Sample(Point at,boolean contact,int age,float partial) {}
    private record Settings(double phase,String kind,double distance,double lateral,double vertical,double separation) {}
    private LandingArrayMaterialPreview() {}

    private static double number(String key,double fallback) {
        return Double.parseDouble(System.getProperty(PREFIX+"."+key,Double.toString(fallback)));
    }
    private static Settings settings() {
        if(!ENABLED)return null;
        try {
            var s=new Settings(number("phase",3.5),System.getProperty(PREFIX+".kind","pair"),number("distance",8),
                    number("lateral",0),number("vertical",-.65),number("separation",3));
            if(!Double.isFinite(s.phase)||s.phase<0||s.phase>=7||!List.of("source","contact","pair").contains(s.kind)
                    ||!Double.isFinite(s.distance)||s.distance<.15||s.distance>48
                    ||!Double.isFinite(s.lateral)||Math.abs(s.lateral)>32
                    ||!Double.isFinite(s.vertical)||Math.abs(s.vertical)>32
                    ||!Double.isFinite(s.separation)||s.separation<.5||s.separation>12)return null;
            return s;
        } catch(RuntimeException invalid) {return null;}
    }
    static boolean allowed() {
        if(!ENABLED||SETTINGS==null||!shown)return false;
        var mc=Minecraft.getInstance();
        return mc.level!=null&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isRemoved()
                &&mc.hasSingleplayerServer()&&mc.isWindowActive()&&!mc.isPaused()&&mc.screen==null&&!mc.options.hideGui
                &&MultidimensionalLandingArrayEffects.materialFixtureIdle();
    }
    private static void clear() {samples=List.of();world=null;player=null;connection=null;optionalRendered=classicRendered=0;}
    private static List<Sample> samples(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();
        if(world!=mc.level||player!=mc.player||connection!=mc.getConnection()||!AcademyClient.sameInputSessionEpoch(session))clear();
        if(!samples.isEmpty())return samples;
        var look=event.getCamera().getLookVector();var cameraUp=event.getCamera().getUpVector();
        Vec3 forward=new Vec3(look.x(),look.y(),look.z()).normalize();
        Vec3 up=new Vec3(cameraUp.x(),cameraUp.y(),cameraUp.z()).normalize();
        Vec3 side=forward.cross(up).normalize();
        if(forward.lengthSqr()<.5||side.lengthSqr()<.5||up.lengthSqr()<.5)return List.of();
        Vec3 center=event.getCamera().getPosition().add(forward.scale(SETTINGS.distance))
                .add(side.scale(SETTINGS.lateral)).add(up.scale(SETTINGS.vertical));
        int age=(int)Math.floor(SETTINGS.phase);float partial=(float)(SETTINGS.phase-age);
        if(SETTINGS.kind.equals("pair"))samples=List.of(sample(center.add(side.scale(-SETTINGS.separation/2)),false,age,partial),
                sample(center.add(side.scale(SETTINGS.separation/2)),true,age,partial));
        else samples=List.of(sample(center,SETTINGS.kind.equals("contact"),age,partial));
        world=mc.level;player=mc.player;connection=mc.getConnection();session=AcademyClient.inputSessionEpoch();
        return samples;
    }
    private static Sample sample(Vec3 at,boolean contact,int age,float partial) {
        return new Sample(new Point(at.x,at.y,at.z),contact,age,partial);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(!ENABLED||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        optionalRendered=classicRendered=0;
        if(!allowed()){if(Minecraft.getInstance().level==null||Minecraft.getInstance().player==null)clear();return;}
        int[] counts=MultidimensionalLandingArrayEffects.renderMaterialFixture(event,samples(event));
        optionalRendered=counts[0];classicRendered=counts[1];
    }
    @SubscribeEvent public static void key(InputEvent.Key event) {
        if(!ENABLED||SETTINGS==null||event.getAction()!=GLFW.GLFW_PRESS)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!mc.hasSingleplayerServer()||!mc.isWindowActive()||mc.screen!=null)return;
        if(event.getKey()==GLFW.GLFW_KEY_F8)clear();
        else if(event.getKey()==GLFW.GLFW_KEY_F9){shown=!shown;clear();}
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        if(!allowed())return;
        var mc=Minecraft.getInstance();var graphics=event.getGuiGraphics();
        String[] lines={"NEW351 材质样例 / STAGED FIXTURE", "非实战 / NO CAST OR DAMAGE",
                String.format(Locale.ROOT,"%s | phase %.2f | .57 material %d / .56 classic %d",SETTINGS.kind,SETTINGS.phase,optionalRendered,classicRendered),
                "F8 重定位 / reframe | F9 隐藏 / hide", "/aca_vfx2 landing_array on/off"};
        int width=0;for(String line:lines)width=Math.max(width,mc.font.width(Component.literal(line)));
        float scale=Math.min(1F,Math.max(1,graphics.guiWidth()-14)/(float)Math.max(1,width));
        graphics.fill(3,3,(int)Math.ceil(width*scale)+11,(int)Math.ceil(lines.length*11*scale)+11,0xE010151C);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(7,7,0);graphics.pose().scale(scale,scale,1);
            for(int i=0;i<lines.length;i++)graphics.drawString(mc.font,Component.literal(lines[i]),0,i*11,i<2?0xFFFFC765:0xFFE3ECF2,true);
        } finally {graphics.pose().popPose();}
    }
}
