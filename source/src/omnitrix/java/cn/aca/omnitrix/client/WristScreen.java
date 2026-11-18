package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Form;
import cn.aca.omnitrix.Kind;
import cn.aca.omnitrix.Omnitrix;
import cn.aca.omnitrix.Wrist.Stage;
import cn.aca.omnitrix.WristNetwork;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.lwjgl.glfw.GLFW;

final class WristScreen extends Screen {
    private final Kind kind;
    private final boolean equipment;
    private int selection;
    private float rotation, previous;
    private int age, previewAt;
    private boolean sent;
    private static int lastClassic;
    private static int lastCelestial = 10;
    private static final float TAU=(float)Math.PI*2;
    private static final float TOP=-TAU/4;
    private static final int BACK=0xdc192330;
    private static final int INK=0xffe8efe8;
    private static final int MUTED=0xffadc1c6;

    WristScreen(Kind kind,boolean equipment) {
        super(Component.translatable("item.aca_omnitrix."+kind.id));
        this.kind=kind;
        this.equipment=equipment;
        selection=kind==Kind.CLASSIC?lastClassic:kind==Kind.CELESTIAL?lastCelestial:0;
        rotation=selection;
        previous=selection;
    }

    private static void scan(GuiGraphics graphics, float age, int color) {
        if (ClientState.reduced()) return;
        int line = (int)(age % 22 - 11);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 68);
        graphics.fill(-15, line, 16, line + 1, DialDraw.alpha(color, .43f));
        graphics.fill(-12, line - 2, 13, line - 1, DialDraw.alpha(color, .13f));
        graphics.pose().popPose();
    }

    static boolean isOpen(){return Minecraft.getInstance().screen instanceof WristScreen;}
    static float lift(float partial){return Minecraft.getInstance().screen instanceof WristScreen s ? Motion.part(s.age+partial,0,6):0;}
    static float dialYaw(float partial) {
        if (!(Minecraft.getInstance().screen instanceof WristScreen s)) return 0f;
        float turn = ClientState.reduced() ? s.selection : Mth.lerp(partial, s.previous, s.rotation);
        int count = s.kind == Kind.CLASSIC ? 10 : 11;
        return turn * (360f / count);
    }
    static float switchImpulse(float partial) {
        if (!(Minecraft.getInstance().screen instanceof WristScreen s) || ClientState.reduced()) return 0f;
        float dt = (s.age + partial) - s.previewAt;
        return dt < 0 || dt > 5 ? 0f : (float) Math.sin(dt * Math.PI / 5f);
    }

    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){
        age++;
        previous=rotation;
        rotation+=.36f*(selection-rotation);
        if (Math.abs(selection - rotation) < .009f) rotation = selection;
        var view=ClientState.self();
        if(view==null||view.s().watch()!=kind||view.s().phase()==Stage.ARMING||view.s().phase()==Stage.LOCKING||view.s().morphed()) minecraft.setScreen(null);
    }

    private void choose(int delta){
        if(kind==Kind.CARNITRIX||equipment||sent)return;
        selection+=delta;
        int count = kind == Kind.CLASSIC ? 10 : 11;
        if(Math.abs(selection)>1000){selection=Math.floorMod(selection,count);rotation=selection;previous=selection;}
        if (kind == Kind.CLASSIC) lastClassic=Math.floorMod(selection,10);
        else lastCelestial=Math.floorMod(selection,11);
        previewAt=age;
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(Omnitrix.SELECT.get(),1, .42f));
    }

    private Form chosen(){
        if (kind == Kind.CLASSIC) return Form.CLASSIC.get(Math.floorMod(selection,10));
        if (kind == Kind.CELESTIAL) {
            int slot = Math.floorMod(selection, 11);
            return slot < 10 ? Form.CLASSIC.get(slot) : Form.ALIEN_X;
        }
        return Form.ALIEN_X;
    }

    private void confirm(){
        var view=ClientState.self();
        if(view==null||sent)return;
        if(equipment){
            sent=true;
            WristNetwork.ask(WristNetwork.UNEQUIP,0);
            minecraft.setScreen(null);
        }else if(kind!=Kind.CARNITRIX&&view.s().phase()==Stage.IDLE) {
            sent=true;
            WristNetwork.ask(WristNetwork.TRANSFORM,chosen().ordinal());
            minecraft.setScreen(null);
        }
    }

    @Override public void removed(){
        if(!sent&&minecraft.getConnection()!=null&&minecraft.player!=null)WristNetwork.ask(WristNetwork.CLOSE,0);
    }

    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(vertical!=0)choose(vertical>0?-1:1);
        return true;
    }

    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_UP){choose(-1);return true;}
        if(key==GLFW.GLFW_KEY_RIGHT||key==GLFW.GLFW_KEY_DOWN){choose(1);return true;}
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){confirm();return true;}
        return super.keyPressed(key,scan,modifiers);
    }

    @Override public boolean mouseClicked(double x,double y,int button){
        float scale=scale();
        float dx=(float)x-centerX(scale),dy=(float)y-centerY(scale);
        if(button==0&&Math.hypot(dx,dy)<31*scale){confirm();return true;}
        if(button==0&&kind!=Kind.CARNITRIX&&!equipment&&Math.hypot(dx,dy)<60*scale){
            int count = kind == Kind.CLASSIC ? 10 : 11;
            float relative=(float)Math.atan2(dy,dx)-TOP;
            int slot=Math.floorMod(Math.round(relative/TAU*count),count);
            if(slot>count/2)slot-=count;
            choose(slot);
            return true;
        }
        return true;
    }

    private float scale(){return Math.max(.61f,Math.min(1.05f,Math.min(width/400f,height/245f)));}
    private boolean left(){return minecraft.player==null||minecraft.player.getMainArm()==HumanoidArm.RIGHT;}
    private float centerX(float scale){return left()?width*.40f:width*.60f;}
    private float centerY(float scale){return height - Math.min(102*scale,height*.36f);}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        var view=ClientState.self();
        if(view==null)return;
        float scale=scale();
        float reveal=Motion.part(age+partial,0,6);
        float popRing=reveal < 1 ? (float)Math.sin(reveal*Math.PI) : 0;
        float switchWave=ClientState.reduced() ? 0 : Math.max(0, 1 - (age + partial - previewAt) / 7f);
        float cx=centerX(scale),cy=centerY(scale);
        var pose=g.pose();
        pose.pushPose();
        pose.translate(cx,cy+(1-reveal)*20,30);
        pose.scale(scale,scale,1);
        float time=age+partial;
        int color=DialDraw.glow(kind);
        if(minecraft.options.getCameraType().isFirstPerson()){
            float direction=left()?-1:1;
            DialDraw.edge(g,44*direction,27,64*direction,51,1.1f,DialDraw.alpha(color,.53f));
            DialDraw.arc(g,64*direction,51,1.2f,2.5f,0,TAU,color);
        }
        DialDraw.arc(g,0,0,0,53,0,TAU,BACK);
        DialDraw.arc(g,0,0,52.0f,53,0,TAU,DialDraw.alpha(color,.53f));
        DialDraw.arc(g,0,0,45,46,0,TAU,DialDraw.alpha(color,.23f));
        if (popRing > .02f) {
            float pr = 52 + (1 - reveal) * 12;
            DialDraw.arc(g, 0, 0, pr, pr + 2.5f, 0, TAU, DialDraw.alpha(color, popRing * .72f));
        }
        if (switchWave > .02f) {
            float sr = 18 + (1 - switchWave) * 32;
            DialDraw.arc(g, 0, 0, sr, sr + 1.8f, 0, TAU, DialDraw.alpha(color, switchWave * .75f));
        }
        // Holographic projection pedestal beneath the 3D alien preview
        if (!equipment && kind != Kind.CARNITRIX) {
            DialDraw.arc(g, 0, 22, 0, 16, 0, TAU, DialDraw.alpha(color, .13f));
            DialDraw.arc(g, 0, 22, 15.5f, 17.0f, 0, TAU, DialDraw.alpha(color, .44f));
            DialDraw.edge(g, -20, 44, -15, 22, 1.0f, DialDraw.alpha(color, .22f));
            DialDraw.edge(g, 20, 44, 15, 22, 1.0f, DialDraw.alpha(color, .22f));
        }
        for(int notch=0;notch<40;notch++){
            float angle=notch*TAU/40 + (ClientState.reduced() ? 0 : time * .005f);
            DialDraw.tick(g,0,0,notch%4==0?48:50,52,angle,DialDraw.alpha(color,notch%4==0?.78f:.32f));
        }
        if(equipment||kind==Kind.CARNITRIX){
            float end=kind==Kind.CARNITRIX?(ClientState.reduced()?1:time*.038f):TOP;
            DialDraw.arc(g,0,0,40,43,end,end+.77f,color);
            g.flush();
            drawModel(g,Rig.get(kind.id),Rig.STILL,0,-2,82,6,-(ClientState.reduced()?0:time*.45f),-35);
        }else if(kind==Kind.CLASSIC){
            float turn=ClientState.reduced()?selection:Mth.lerp(partial,previous,rotation);
            for(int i=0;i<10;i++){
                float angle=TOP+(i-turn)*TAU/10;
                boolean selected=i==Math.floorMod(selection,10);
                float x=Mth.cos(angle)*37.9f,y=Mth.sin(angle)*37.9f;
                if(selected)DialDraw.arc(g,0,0,42,45.5f,angle-.25f,angle+.25f,color);
                DialDraw.arc(g,x,y,0,selected?7.8f:6.8f,0,TAU,selected?0xff273933:0xff242d38);
                g.flush();
                pose.pushPose();
                pose.translate(0,0,6);
                g.blit(Omnitrix.id("textures/gui/icons.png"),Math.round(x)-5,Math.round(y)-5,10,10,(float)i*16,0f,16,16,176,16);
                pose.popPose();
            }
            Rig previewRig = Rig.get(chosen().id());
            Rig.Motion previewMotion = ClientState.reduced() ? Rig.STILL : previewRig.animate(Rig.STILL, "idle", time, 1f);
            float popScale = .68f + .36f * Motion.part(age+partial-previewAt,-1.5f,4.5f) - .04f * switchWave;
            float hover = ClientState.reduced() ? 0 : Mth.sin(time*.094f)*1.7f;
            float spinIn = ClientState.reduced() ? 0 : switchWave * 38f;
            drawModel(g,previewRig,previewMotion,0,4+hover,34*popScale,4,
                    (ClientState.reduced()?19:19+Mth.sin(time*.051f)*13) + spinIn, 0);
            scan(g,time,color);
        }else{
            float turn=ClientState.reduced()?selection:Mth.lerp(partial,previous,rotation);
            for(int i=0;i<3;i++){
                float turnRing=TOP+i*TAU/3f+(ClientState.reduced()?0:time*(.014f + i * .005f));
                DialDraw.arc(g,0,0,36+i*3,37+i*3,turnRing,turnRing+1.45f,DialDraw.alpha(color,.94f-.18f*i));
                float x=Mth.cos(turnRing+1.45f)*(37+i*3),y=Mth.sin(turnRing+1.45f)*(37+i*3);
                DialDraw.arc(g,x,y,0,2,0,TAU,color);
            }
            for(int i=0;i<11;i++){
                float angle=TOP+(i-turn)*TAU/11;
                boolean selected=i==Math.floorMod(selection,11);
                float x=Mth.cos(angle)*37.9f,y=Mth.sin(angle)*37.9f;
                if(selected)DialDraw.arc(g,0,0,42,45.5f,angle-.22f,angle+.22f,color);
                DialDraw.arc(g,x,y,0,selected?7.8f:6.8f,0,TAU,selected?0xff1f2a3a:0xff161e2b);
                g.flush();
                pose.pushPose();
                pose.translate(0,0,6);
                if (i < 10) {
                    g.blit(Omnitrix.id("textures/gui/icons.png"),Math.round(x)-5,Math.round(y)-5,10,10,(float)i*16,0f,16,16,176,16);
                } else {
                    g.blit(Omnitrix.id("textures/gui/icons.png"),Math.round(x)-5,Math.round(y)-5,10,10,160f,0f,16,16,176,16);
                }
                pose.popPose();
            }
            g.flush();
            StarfieldShader.clock(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            Rig previewRig = Rig.get(chosen().asset(kind));
            Rig.Motion previewMotion = ClientState.reduced() ? Rig.STILL : previewRig.animate(Rig.STILL, "idle", time, 1f);
            float popScale = .68f + .36f * Motion.part(age+partial-previewAt,-1.5f,4.5f) - .04f * switchWave;
            float hover = ClientState.reduced() ? 0 : Mth.sin(time*.09f)*1.6f;
            drawModel(g,previewRig,previewMotion,0,4+hover,35*popScale,5,
                    (ClientState.reduced()?8:time*1.65f) + (ClientState.reduced() ? 0 : switchWave * 32f),0);
            scan(g,time,color);
        }
        g.flush();
        int labelX=left()?-56:57;
        Component title=equipment?Component.translatable("watch.aca_omnitrix.slot")
                : kind==Kind.CARNITRIX?getTitle():Component.translatable(chosen().translationKey(kind));
        Component description=equipment?getTitle():kind==Kind.CARNITRIX?Component.translatable("watch.aca_omnitrix.waiting")
                : Component.translatable("ability.aca_omnitrix."+chosen().id());
        String hintKey=equipment?(view.s().bonded()?"quarantine":"remove_key")
                :kind==Kind.CARNITRIX?"monitor_hint":view.s().phase()==Stage.COOLING?"charging":"confirm";
        Component hint=Component.translatable("watch.aca_omnitrix."+hintKey);
        int length=Math.max(font.width(title),Math.max(font.width(description),font.width(hint)));
        int textX=left()?labelX-length:labelX;
        if(textX<-cx/scale+8||textX+length>(width-cx)/scale-8){textX=-length/2;labelX=0;}
        int top=labelX==0?58:-17;
        g.fill(textX-5,top-5,textX+length+5,top+31,0xd91d2530);
        g.fill(textX-5,top-5,textX+Math.min(23,length),top-4,color);
        g.drawString(font,title,textX,top,INK,false);
        g.drawString(font,description,textX,top+12,MUTED,false);
        g.drawString(font,hint,textX,top+23,color,false);
        pose.popPose();
    }

    private static void drawModel(GuiGraphics g,Rig rig,Rig.Motion motion,float x,float y,float size,float z,float yaw,float pitch){
        g.flush();
        var pose=g.pose();
        pose.pushPose();
        pose.translate(x,y+size*.50f,z+60);
        pose.scale(size,-size,size);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(180+yaw));
        rig.draw(pose,g.bufferSource(),motion,16/rig.height,-1,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
        g.flush();
        pose.popPose();
    }
}
