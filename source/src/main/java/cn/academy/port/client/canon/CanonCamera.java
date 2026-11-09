package cn.academy.port.client.canon;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Caster-only camera and screen presentation (plan codes CM1-CM3, CM5, CM6, LT3). Callers must only trigger these for the local
 * player's own casts; nothing here changes game time, input or what other players see. All of it can be turned off in
 * academy-canon-fx-client.toml.
 *
 * <p>Impact freeze (CM1) pauses the effect clock, not the world: every canon effect and particle holds still for a few frames
 * while the game keeps running.</p>
 */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonCamera {
    private static double shakeAmp,shakeTicks,shakeLeft;
    private static double fovAmount,fovTicks,fovLeft;
    private static double freezeLeftMs,pausedTicks,frozenAt=Double.NaN;
    private static long lastFrame;
    private static float[] tint={1,1,1};private static double tintStrength,tintTicks,tintLeft;
    private static double darkStrength,darkTicks,darkLeft;
    private static String edge;
    @SuppressWarnings("unused") private static final int OVERLAY_VERSION=2;private static double edgeStrength,edgeTicks,edgeLeft;
    private static ResourceLocation vignette;
    private CanonCamera() {}

    /** Drop presentation time and overlays when the client world changes. */
    static void clear(){
        shakeAmp=shakeTicks=shakeLeft=fovAmount=fovTicks=fovLeft=0;
        freezeLeftMs=pausedTicks=0;frozenAt=Double.NaN;lastFrame=0;
        tintStrength=tintTicks=tintLeft=darkStrength=darkTicks=darkLeft=edgeStrength=edgeTicks=edgeLeft=0;edge=null;
    }

    // ------------------------------------------------------------------ triggers (local caster only)
    static void shake(double amplitude,int ticks){
        double s=amplitude*CanonFxConfig.shake();if(s<=0)return;
        if(s>=shakeAmp*(shakeLeft/Math.max(1,shakeTicks))){shakeAmp=s;shakeTicks=ticks;shakeLeft=ticks;}
    }
    static void fovKick(double amount,int ticks){if(CanonFxConfig.fovKick()){fovAmount=amount;fovTicks=ticks;fovLeft=ticks;}}
    static void freeze(double millis){if(CanonFxConfig.hitFreeze())freezeLeftMs=Math.max(freezeLeftMs,millis);}
    static void tint(float[] rgb,double strength,int ticks){if(CanonFxConfig.screen()){tint=rgb;tintStrength=strength;tintTicks=ticks;tintLeft=ticks;}}
    static void darken(double strength,int ticks){if(CanonFxConfig.screen()){darkStrength=strength;darkTicks=ticks;darkLeft=ticks;}}
    static void edge(String kind,double strength,int ticks){if(CanonFxConfig.screen()){edge=kind;edgeStrength=strength;edgeTicks=ticks;edgeLeft=ticks;}}

    /** Effect clock in ticks: game time minus every impact freeze so far; constant while a freeze is running. */
    static double fxNow(double gameTicks){
        if(!Double.isNaN(frozenAt))return frozenAt;
        return gameTicks-pausedTicks;
    }
    private static void frame(double gameTicks){
        long now=System.nanoTime();double dt=lastFrame==0?0:Math.min(100,(now-lastFrame)/1e6);lastFrame=now;
        if(freezeLeftMs>0){
            if(Double.isNaN(frozenAt))frozenAt=gameTicks-pausedTicks;
            freezeLeftMs-=dt;pausedTicks+=dt/50.0;
            if(freezeLeftMs<=0){freezeLeftMs=0;frozenAt=Double.NaN;}
        }
    }

    // ------------------------------------------------------------------ camera
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        double partial=event.getPartialTick();frame(mc.level.getGameTime()+partial);
        if(shakeLeft<=0||mc.isPaused())return;
        double k=shakeLeft/Math.max(1,shakeTicks),a=shakeAmp*k*k,t=(mc.level.getGameTime()+partial)*1.7;
        event.setYaw((float)(event.getYaw()+a*(Math.sin(t*3.1)+.5*Math.sin(t*7.3))));
        event.setPitch((float)(event.getPitch()+a*(Math.cos(t*2.7)+.5*Math.sin(t*6.1))));
        event.setRoll((float)(event.getRoll()+a*.35*Math.sin(t*4.3)));
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event){
        if(fovLeft<=0)return;double p=1-fovLeft/Math.max(1,fovTicks);
        double env=p<.2?p/.2:1-(p-.2)/.8;env=Math.max(0,env*env*(3-2*env));
        event.setFOV(event.getFOV()*(1+fovAmount*env));
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.isPaused())return;
        shakeLeft=Math.max(0,shakeLeft-1);fovLeft=Math.max(0,fovLeft-1);tintLeft=Math.max(0,tintLeft-1);darkLeft=Math.max(0,darkLeft-1);edgeLeft=Math.max(0,edgeLeft-1);
        if(mc.level==null){shakeLeft=fovLeft=tintLeft=darkLeft=edgeLeft=0;}
    }

    // ------------------------------------------------------------------ screen overlays (under the HUD)
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Layers {
        @SubscribeEvent public static void register(RegisterGuiLayersEvent event){
            event.registerBelowAll(ResourceLocation.fromNamespaceAndPath("academy","canon_fx_screen"),(g,delta)->draw(g,delta.getGameTimeDeltaPartialTick(false)));
        }
    }
    private static double fade(double left,double total){
        if(left<=0)return 0;double p=1-left/Math.max(1,total);
        return p<.15?p/.15:1-Math.pow((p-.15)/.85,2);
    }
    private static void draw(net.minecraft.client.gui.GuiGraphics g,float partial){
        double ti=tintStrength*fade(tintLeft,tintTicks),dk=darkStrength*fade(darkLeft,darkTicks),ed=edgeStrength*fade(edgeLeft,edgeTicks);
        if(ti<=0&&dk<=0&&ed<=0)return;
        int w=g.guiWidth(),h=g.guiHeight();g.flush();
        RenderSystem.disableDepthTest();
        try{
            if(dk>0)quad(g,vignette(),0,0,w,h,0,0,1,1,0,0,0,(float)Math.min(.85,dk));
            if(ti>0)quad(g,vignette(),0,0,w,h,0,0,1,1,tint[0],tint[1],tint[2],(float)Math.min(.55,ti));
            if(ed>0&&"water".equals(edge))water(g,w,h,ed);
            if(ed>0&&"ink".equals(edge))ink(g,w,h,ed);
            if(ed>0&&"frost".equals(edge))frost(g,w,h,ed);
            if(ed>0&&"speed".equals(edge))speed(g,w,h,ed);
        }finally{RenderSystem.setShaderColor(1,1,1,1);RenderSystem.enableDepthTest();RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();}
    }
    /** One immediately drawn, alpha-blended screen quad (GUI coordinates); colour and alpha come from the vertex colour. */
    private static void quad(net.minecraft.client.gui.GuiGraphics g,ResourceLocation tex,float x0,float y0,float x1,float y1,float u0,float v0,float u1,float v1,
                             float r,float gr,float b,float a){
        if(a<=.004f)return;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.setShaderTexture(0,tex);RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        var m=g.pose().last().pose();
        var buf=com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.addVertex(m,x0,y1,0).setUv(u0,v1).setColor(r,gr,b,a);buf.addVertex(m,x1,y1,0).setUv(u1,v1).setColor(r,gr,b,a);
        buf.addVertex(m,x1,y0,0).setUv(u1,v0).setColor(r,gr,b,a);buf.addVertex(m,x0,y0,0).setUv(u0,v0).setColor(r,gr,b,a);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buf.buildOrThrow());
    }
    /** CM6 water: a light blue wet vignette and droplets running down near both screen edges. */
    private static void water(net.minecraft.client.gui.GuiGraphics g,int w,int h,double strength){
        var mc=Minecraft.getInstance();double t=mc.level==null?0:mc.level.getGameTime()*.05;
        quad(g,vignette(),0,0,w,h,0,0,1,1,.45f,.7f,1f,(float)(.3*strength));
        var drops=CanonFx4.tex("hh_drop_atlas");var grid=Fx4Atlas.get("hh_drop_atlas");
        for(int k=0;k<16;k++){
            double r=CanonFxScripts.rnd(k*7+3);boolean leftSide=k%2==0;
            float x=(float)(leftSide?r*w*.16:w-r*w*.16-24),y=(float)(((CanonFxScripts.rnd(k)+t*(.15+.25*r))%1.2-.1)*h);
            float size=(float)(8+16*CanonFxScripts.rnd(k+11));float[] uv=grid.cell(k%grid.cells());
            quad(g,drops,x,y,x+size,y+size,uv[0],uv[1],uv[2],uv[3],.85f,.95f,1f,(float)(.7*strength));
        }
    }
    /** CM6 ink: a dark violet vignette and ink blots creeping in from the corners. */
    private static void ink(net.minecraft.client.gui.GuiGraphics g,int w,int h,double strength){
        quad(g,vignette(),0,0,w,h,0,0,1,1,.08f,.05f,.14f,(float)(.55*strength));
        var tex=CanonFx4.tex("ls_ink_sheet");var grid=Fx4Atlas.get("ls_ink_sheet");float[] uv=grid.cell(grid.frame(.45));
        float[][] spots={{-.05f,-.05f},{.85f,-.08f},{-.08f,.8f},{.88f,.82f},{.4f,-.12f},{.45f,.9f}};
        for(int k=0;k<spots.length;k++){float s=(float)(h*(.22+.1*CanonFxScripts.rnd(k)));float x=spots[k][0]*w,y=spots[k][1]*h;
            quad(g,tex,x,y,x+s,y+s,uv[0],uv[1],uv[2],uv[3],.1f,.07f,.16f,(float)(.85*strength));}
    }
    /** CM6 frost: a pale icy vignette with frost crystals growing in from the corners. */
    private static void frost(net.minecraft.client.gui.GuiGraphics g,int w,int h,double strength){
        quad(g,vignette(),0,0,w,h,0,0,1,1,.8f,.93f,1f,(float)(.45*strength));
        var tex=CanonFx4.tex("na_frost_decal");float[][] spots={{-.12f,-.15f},{.82f,-.15f},{-.12f,.72f},{.82f,.72f}};
        for(int k=0;k<spots.length;k++){float s=h*.45f;float x=spots[k][0]*w,y=spots[k][1]*h;
            quad(g,tex,x,y,x+s,y+s,0,0,1,1,.9f,.97f,1f,(float)(.8*strength));}
    }
    /** CM6 speed: thin streaks rushing in from the screen edges toward the centre. */
    private static void speed(net.minecraft.client.gui.GuiGraphics g,int w,int h,double strength){
        var mc=Minecraft.getInstance();double t=mc.level==null?0:mc.level.getGameTime();var tex=CanonFx4.tex("common_speedline");
        for(int k=0;k<18;k++){double ang=k*.349+CanonFxScripts.rnd(k)*.2,ph=CanonFxScripts.frac(t*.15+CanonFxScripts.rnd(k+5));
            double cx=w*.5,cy=h*.5,r0=Math.hypot(cx,cy)*(1.05-.35*ph),len=h*(.12+.1*CanonFxScripts.rnd(k+9));
            float x0=(float)(cx+Math.cos(ang)*r0),y0=(float)(cy+Math.sin(ang)*r0),x1=(float)(cx+Math.cos(ang)*(r0-len)),y1=(float)(cy+Math.sin(ang)*(r0-len));
            float nx=(float)(-Math.sin(ang)*1.5),ny=(float)(Math.cos(ang)*1.5);
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderTexture(0,tex);RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
            var m=g.pose().last().pose();float a=(float)(.6*strength*(1-ph));
            var buf=com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
            buf.addVertex(m,x0-nx,y0-ny,0).setUv(1,0).setColor(1f,1f,1f,a);buf.addVertex(m,x0+nx,y0+ny,0).setUv(1,1).setColor(1f,1f,1f,a);
            buf.addVertex(m,x1+nx,y1+ny,0).setUv(0,1).setColor(1f,1f,1f,0f);buf.addVertex(m,x1-nx,y1-ny,0).setUv(0,0).setColor(1f,1f,1f,0f);
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buf.buildOrThrow());}
    }
    /** Soft radial vignette built once in memory (alpha rises toward the corners). */
    private static ResourceLocation vignette(){
        if(vignette!=null)return vignette;
        var img=new NativeImage(256,256,true);
        for(int y=0;y<256;y++)for(int x=0;x<256;x++){
            double dx=(x-127.5)/127.5,dy=(y-127.5)/127.5,r=Math.sqrt(dx*dx*.9+dy*dy*1.1);
            int a=(int)Math.round(255*Math.max(0,Math.min(1,(r-.55)/.75)));a=a*a/255;
            img.setPixelRGBA(x,y,(a<<24)|0xFFFFFF);
        }
        vignette=Minecraft.getInstance().getTextureManager().register("academy_canon_vignette",new DynamicTexture(img));
        return vignette;
    }
}
