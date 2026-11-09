/* AcademyCraft 1.0.7 CPBar, KeyHintUI and BackgroundMask adaptation. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.ClassicHudConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Actual source textures/layout/shader equations. Client-only; the caller supplies delegate/preset state. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ClassicAbilityHud {
    public enum DelegateVisual {IDLE,CHARGE,ACTIVE}
    /** mouseButton -1 = keyboard; 0/1 = source left/right sprites; larger values = generic mouse sprite. */
    public record KeyHint(String group,int order,String skill,Component keyLabel,int mouseButton,
                          DelegateVisual visual,int ticksLeft,int maxTicks) {
        public KeyHint {group=group==null?"def":group;visual=visual==null?DelegateVisual.IDLE:visual;ticksLeft=Math.max(0,ticksLeft);maxTicks=Math.max(1,maxTicks);}
    }
    /** toggleHeld starts on physical V key-down; the renderer supplies the original 200ms number delay. */
    public record Inputs(boolean toggleHeld,double consumptionHint,int presetId,String activateHint,List<KeyHint> keys) {
        public Inputs {presetId=Math.max(0,Math.min(3,presetId));keys=keys==null?List.of():List.copyOf(keys);activateHint=activateHint==null?"":activateHint;}
        public static Inputs empty(){return new Inputs(false,0,0,"",List.of());}
    }
    private static final ResourceLocation BACK=tex("guis/cpbar/back_normal"),BACK_OVERLOAD=tex("guis/cpbar/back_overload"),
            CP=tex("guis/cpbar/cp"),OVERLOAD=tex("guis/cpbar/front_overload"),HIGHLIGHT=tex("guis/cpbar/highlight_overload"),
            MASK=tex("guis/cpbar/mask"),SCREEN_MASK=tex("effects/screen_mask"),KEY_BACK=tex("guis/key_hint/back"),ICON_BACK=tex("guis/key_hint/icon_back");
    private static ClassicHudTimeline timeline=new ClassicHudTimeline();
    private static final ClassicHudFont font=new ClassicHudFont();
    private static Object level,player;
    private static long gameClock,lastWall=Util.getMillis();
    private ClassicAbilityHud(){}

    /** Invoke once from RenderGuiEvent.Post instead of the placeholder fill()/CP text HUD. */
    public static void render(GuiGraphics graphics,AbilityProgress state,Inputs input){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);
        if(mc.level==null||mc.player==null||mc.options.hideGui)return;
        long wall=advanceClock(mc);
        if(!state.hasCategory())return;
        boolean overloaded=ClassicHudTimeline.overloadWarning(state.overloadFine,state.overloadDelay);
        var frame=timeline.update(gameClock,wall,state.activated,overloaded,!state.overloadFine,state.interfering,
                state.cp/state.maxCp(),state.overload/state.maxOverload(),input.toggleHeld(),input.presetId(),state.category);
        font.use(ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.FONT.get():"Microsoft YaHei");
        ClassicHudCanvas canvas=new ClassicHudCanvas(graphics);canvas.begin();
        try{
            // BackgroundMask.draw disables GL_ALPHA_TEST so the vignette keeps every low-alpha texel.
            var mask=frame.screenMask();if(mask.a()>0)canvas.rect(SCREEN_MASK,0,0,graphics.guiWidth(),graphics.guiHeight(),0,0,1,1,mask,ClassicSkillAlphaShader.get(),null);
            if(frame.alpha()>0){
                double x=ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.CP_X.get():ClassicHudTimeline.CP_X;
                double y=ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.CP_Y.get():ClassicHudTimeline.CP_Y;
                var origin=ClassicHudTimeline.cpOrigin(graphics.guiWidth(),x,y);
                graphics.pose().pushPose();graphics.pose().translate(origin.x(),origin.y(),0);
                graphics.pose().scale((float)ClassicHudTimeline.CP_SCALE,(float)ClassicHudTimeline.CP_SCALE,1);
                graphics.pose().translate(frame.offsetX(),frame.offsetY(),0);
                drawCp(canvas,state,input,frame,overloaded);
                graphics.pose().popPose();
            }
            if(state.activated&&frame.keysAlpha()>0&&!input.keys().isEmpty())drawKeys(canvas,graphics,state,input.keys(),frame);
        }finally{canvas.end();}
    }
    /** Session transitions discard fade/buffer/preset history, never show the previous world's resources. */
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();synchronize(mc);advanceClock(mc);}
    private static long advanceClock(Minecraft mc){long wall=Util.getMillis();if(!mc.isPaused())gameClock+=Math.max(0,wall-lastWall);lastWall=wall;return wall;}
    public static void reset(){timeline=new ClassicHudTimeline();gameClock=0;lastWall=Util.getMillis();}
    private static void synchronize(Minecraft mc){if(level!=mc.level||player!=mc.player){level=mc.level;player=mc.player;reset();}}
    private static void drawCp(ClassicHudCanvas canvas,AbilityProgress state,Inputs input,ClassicHudTimeline.Frame f,boolean overloaded){
        double alpha=f.alpha();
        if(overloaded){
            canvas.rect(BACK_OVERLOAD,0,0,964,147,rgba(1,1,1,.8*alpha));
            if(ClassicHudShaders.overload!=null)ClassicHudShaders.overload.safeGetUniform("TexOffset").set((float)f.scroll());
            // HudUtils.rect(30,0,0,0,914,147,914,147) uses FRONT texture dimensions 974x147.
            canvas.rect(OVERLOAD,30,0,914,147,0,0,914/974.0,1,rgba(1,1,1,alpha),ClassicHudShaders.overload,MASK);
            canvas.rect(HIGHLIGHT,0,0,964,147,rgba(1,1,1,f.warningAlpha()*alpha));
        }else{
            canvas.rect(BACK,0,0,964,147,rgba(1,1,1,.8*alpha));
            double length=f.overload()*943,x=943-length;
            var color=ClassicHudTimeline.overloadColor(f.overload());
            // Literal CPBar autoLerp endpoint bypasses master alpha at exactly zero.
            canvas.rect(MASK,x,21,length,104,x/964,21/147.0,943/964.0,125/147.0,color.multiplyAlpha(f.overload()==0?1:alpha),null,null);
        }
        boolean unavailable=ClassicHudTimeline.unavailable(state.overloadFine,state.interfering);
        double current=ClassicHudTimeline.clamp(state.cp/state.maxCp());
        ResourceLocation icon=tex("abilities/"+state.category+"/icon_overlay");
        double consumption=Double.isFinite(input.consumptionHint())?Math.max(0,input.consumptionHint()):0;
        if(consumption>0){drawCpFill(canvas,icon,current,alpha*ClassicHudTimeline.consumptionPulse(gameClock),unavailable);
            drawCpFill(canvas,icon,Math.max(0,state.cp-consumption)/state.maxCp(),alpha,unavailable);
        }else drawCpFill(canvas,icon,f.cp(),alpha,unavailable);
        if(f.presetAlpha()>0)drawPresets(canvas,input.presetId(),f.presetAlpha());
        if(f.numbersAlpha()>0)drawNumbers(canvas,state,.6*alpha*f.numbersAlpha());
        if(!input.activateHint().isEmpty())drawActivateHint(canvas,input.activateHint());
    }
    private static void drawCpFill(ClassicHudCanvas canvas,ResourceLocation icon,double progress,double alpha,boolean unavailable){
        progress=ClassicHudTimeline.clamp(progress);var color=ClassicHudTimeline.cpColor(progress);
        // Preserve the classic endpoint exception: the first autoLerp entry binds its own alpha directly.
        canvas.cp(CP,icon,progress,color.multiplyAlpha(progress==0?1:alpha*(unavailable?.3:1)));
    }
    private static void drawNumbers(ClassicHudCanvas canvas,AbilityProgress state,double alpha){
        String cp=String.format(Locale.ROOT,"%.0f",state.cp),ol=String.format(Locale.ROOT,"%.0f",state.overload);
        double label=Math.max(font.width("CP ",40,false),font.width("OL ",40,false));
        double digits=Math.max(font.width(cp,40,false),font.width(ol,40,false)),end=110+label+digits;
        var color=rgba(1,1,1,alpha);
        font.draw(canvas,"CP ",110,55,40,color,false,false);font.draw(canvas,"OL ",110,85,40,color,false,false);
        font.draw(canvas,cp,end-font.width(cp,40,false),55,40,color,false,false);font.draw(canvas,ol,end-font.width(ol,40,false),85,40,color,false,false);
        font.draw(canvas,String.format(Locale.ROOT,"/%.0f",state.maxCp()),end,55,40,color,false,false);
        font.draw(canvas,String.format(Locale.ROOT,"/%.0f",state.maxOverload()),end,85,40,color,false,false);
    }
    private static void drawPresets(ClassicHudCanvas canvas,int current,double alpha){
        for(int i=0;i<4;i++){
            double x=580+i*62;canvas.fill(x,136,52,52,rgba(48/255.0,48/255.0,48/255.0,alpha),false);
            String text=Integer.toString(i+1);var textColor=rgba(1,1,1,Math.max(.05,alpha*.8));
            font.draw(canvas,text,x+26-font.width(text,46,true)*.5,141,46,textColor,true,false);
            if(i==current)canvas.glow(x,136,52,52,5,rgba(1,1,1,200/255.0),false);
        }
    }
    private static void drawActivateHint(ClassicHudCanvas canvas,String text){
        double width=font.width(text,44,false);canvas.fill(500-8-width,132,width+16,60,rgba(65/255.0,65/255.0,65/255.0,70/255.0),false);
        canvas.glow(500-8-width,132,width+16,60,5,rgba(1,1,1,40/255.0),false);
        font.draw(canvas,text,500-width,140,44,rgba(1,1,1,160/255.0),false,false);
    }
    private static void drawKeys(ClassicHudCanvas canvas,GuiGraphics graphics,AbilityProgress state,List<KeyHint> keys,ClassicHudTimeline.Frame f){
        double x=ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.KEY_X.get():ClassicHudTimeline.KEY_X;
        double y=ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.KEY_Y.get():ClassicHudTimeline.KEY_Y;
        var origin=ClassicHudTimeline.keyOrigin(graphics.guiWidth(),graphics.guiHeight(),x,y);
        graphics.pose().pushPose();graphics.pose().translate(origin.x(),origin.y(),0);graphics.pose().scale((float)ClassicHudTimeline.KEY_SCALE,(float)ClassicHudTimeline.KEY_SCALE,1);
        // Source default delegate group first; other nonempty groups are alphabetic, one 200px column each.
        var groups=keys.stream().map(KeyHint::group).distinct().sorted(Comparator.comparing((String group)->!ClassicHudTimeline.defaultGroup(group)).thenComparing(Comparator.naturalOrder())).toList();
        int column=0;for(String group:groups){int row=0;
            for(KeyHint key:keys.stream().filter(k->k.group().equals(group)).sorted(Comparator.comparingInt(KeyHint::order)).toList()){
                graphics.pose().pushPose();graphics.pose().translate(-200-column*200,row*92,0);drawKey(canvas,state,key,f);graphics.pose().popPose();row++;
            }column++;
        }graphics.pose().popPose();
    }
    private static void drawKey(ClassicHudCanvas canvas,AbilityProgress state,KeyHint key,ClassicHudTimeline.Frame f){
        double master=f.keysAlpha();canvas.rect(KEY_BACK,122,0,185,83,rgba(1,1,1,master));
        boolean mono=ClassicHudTimeline.unavailable(state.overloadFine,state.interfering)||key.ticksLeft()>0;double tint=mono?.7:1;
        String label=key.keyLabel()==null?"":key.keyLabel().getString();String sprite;
        if(key.mouseButton()<0){label=label.toUpperCase(Locale.ROOT);sprite=label.length()<=2?"key_short":"key_long";}
        else sprite=key.mouseButton()==0?"mouse_left":key.mouseButton()==1?"mouse_right":"mouse_generic";
        canvas.rect(tex("guis/key_hint/"+sprite),146,10,70,70,0,0,1,1,rgba(tint,tint,tint,master),mono?ClassicHudShaders.mono:null,null);
        if(key.mouseButton()<0||key.mouseButton()>1){if(key.mouseButton()>1)label=Integer.toString(key.mouseButton());
            font.draw(canvas,label,180-font.width(label,32,false)*.5,27,32,rgba(25/255.0,66/255.0,70/255.0,1),false,mono);
        }
        canvas.rect(ICON_BACK,216,5,72,72,0,0,1,1,rgba(1,1,1,master),mono?ClassicHudShaders.mono:null,null);
        boolean animated=key.visual()!=DelegateVisual.IDLE;double sin=animated?f.sinAlpha():1;
        double iconAlpha=key.ticksLeft()>0?.4:(key.visual()==DelegateVisual.IDLE?.7:1)*(.4+sin*.6);
        canvas.rect((key.group().equals("TP_Flashing")?tex("abilities/teleporter/flashing/"+key.skill()):SkillIconAtlas.texture(state.category,key.skill())),221,10,62,62,0,0,1,1,rgba(1,1,1,iconAlpha*master),mono?ClassicHudShaders.mono:null,null);
        if(animated){var glow=key.visual()==DelegateVisual.CHARGE?rgba(1,173/255.0,55/255.0,sin):rgba(70/255.0,179/255.0,1,sin);canvas.glow(221,10,62,62,5,glow,mono);}
        if(key.ticksLeft()>0){double progress=ClassicHudTimeline.clamp(key.ticksLeft()/(double)key.maxTicks());canvas.fill(221,10+62*(1-progress),62,62*progress,rgba(.6,.6,.6,.3*master),false);}
    }
    public static String resolvedFont(){return font.resolvedName();}
    private static ClassicHudTimeline.Rgba rgba(double r,double g,double b,double a){return new ClassicHudTimeline.Rgba(r,g,b,a);}
    private static ResourceLocation tex(String path){return ClassicHudCanvas.texture(path);}
}
