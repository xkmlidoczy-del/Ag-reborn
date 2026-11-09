package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.HighPowerSkills;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** NEW133's transparent non-pausing held-slot selector. Opening requests only a no-payment server identity. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class MagneticItemPullSelection {
    private static final TagKey<Item> MAGNETIC = TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("academy","magnetic_items"));
    private static final TagKey<Item> IMMOVABLE = TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("academy","magnetic_item_pull_immovable"));
    private static final boolean DIAGNOSTICS=Boolean.getBoolean("academy.highpower.itemPullSelectionDiagnostics");
    private static final org.slf4j.Logger DIAGNOSTIC_LOG=org.slf4j.LoggerFactory.getLogger("academy.item_pull_selection_diagnostics");
    private static SelectionScreen active;
    private MagneticItemPullSelection() {}
    public static boolean open(HighPowerInput.Input input) { return open(input,false); }
    public static boolean openArrows(HighPowerInput.Input input) { return open(input,true); }
    private static boolean open(HighPowerInput.Input input,boolean arrows) {
        Minecraft mc=Minecraft.getInstance();
        if(input==null||active!=null||mc.screen!=null||mc.player==null||mc.level==null||!mc.isWindowActive())return false;
        active=new SelectionScreen(input,mc,arrows);mc.setScreen(active);return true;
    }
    public static boolean arrows(HighPowerInput.Input input) { return active!=null&&active.arrows&&active.input.equals(input); }
    public static boolean previewingArrows(long input,long epoch) { return active!=null&&active.arrows&&active.input.input()==input&&active.input.epoch()==epoch&&active.valid(); }
    public static boolean releaseArrows(HighPowerInput.Input input,boolean abort) {
        SelectionScreen screen=active;
        if(screen==null||!screen.arrows||!screen.input.equals(input))return false;
        if(abort||!screen.valid()){cancel();return false;}
        // A release before the authoritative corridor has actually been shown cannot arm invisibly.
        if(!screen.arrowPreviewShown||!MagneticArrowSeparationEffects.previewReady(input)){
            cancel();Minecraft mc=Minecraft.getInstance();
            if(mc.player!=null)mc.player.displayClientMessage(Component.translatable("academy.highpower.item_pull.arrow_preview_wait"),false);
            return false;
        }
        close();return true;
    }
    public static boolean ownScreen() { return active!=null&&Minecraft.getInstance().screen==active; }
    public static boolean selecting() { return active!=null; }
    public static boolean permits(String skill,int slot) { return active==null||HighPowerSkills.MAGNETIC_ITEM_PULL.equals(skill)&&slot==active.input.slot(); }
    public static void tick() {
        if(active==null)return;
        if(!active.valid()){cancel();return;}
        if(++active.age>=cn.academy.port.highpower.MagneticItemPull.SELECTION_TICKS){
            Minecraft mc=Minecraft.getInstance();cancel();
            if(mc.player!=null)mc.player.displayClientMessage(Component.translatable("academy.highpower.item_pull.selection_expired"),false);
        }
    }
    public static void cancel() { boolean hadSelection=active!=null;close();if(hadSelection)AcademyClient.cancelItemPullInput(); }
    /** Session teardown drops the old identity without sending a request into the new world. */
    public static void clear() { close(); }
    /** Returns null on cancellation/stale identity, and an empty list for a genuine empty rectangle. */
    public static List<UUID> release(HighPowerInput.Input input,boolean abort) {
        SelectionScreen screen=active;
        if(screen==null||screen.arrows||!screen.input.equals(input))return null;
        if(abort||!screen.valid()){cancel();return null;}
        List<UUID> selected=screen.candidates();screen.diagnosticRelease(selected.size());close();return selected;
    }
    private static void close() {
        SelectionScreen old=active;active=null;MagneticArrowSeparationEffects.closePreview();
        Minecraft mc=Minecraft.getInstance();
        if(old!=null&&mc.screen==old)mc.setScreen(null);
        if(old!=null){
            for(var key:List.of(mc.options.keyAttack,mc.options.keyUse,mc.options.keyPickItem)){
                key.setDown(false);while(key.consumeClick()){}
            }
        }
    }
    @SubscribeEvent public static void project(RenderLevelStageEvent event) {
        SelectionScreen screen=active;
        if(screen==null||screen.arrows||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||!screen.valid())return;
        Minecraft mc=Minecraft.getInstance();
        Matrix4f transform=MagneticItemPullSelectionGeometry.viewProjection(event.getProjectionMatrix(),event.getModelViewMatrix(),event.getPoseStack().last().pose());
        Vec3 camera=event.getCamera().getPosition();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var points=new ArrayList<MagneticItemPullSelectionGeometry.Point>();
        int examined=0,itemCount=0,tagged=0,inRange=0,visible=0;
        var samples=DIAGNOSTICS?new ArrayList<String>():null;
        for(Entity entity:mc.level.entitiesForRendering()){
            if(++examined>2048)break;
            if(!(entity instanceof ItemEntity item))continue;
            if(++itemCount>256)break;
            if(!candidateItem(item))continue;tagged++;
            Vec3 position=item.getPosition(partial).add(0,item.getBbHeight()*.5,0);
            double distanceSquared=mc.player.getEyePosition().distanceToSqr(position);
            if(distanceSquared>(cn.academy.port.highpower.core.MagneticItemPullRules.RANGE * cn.academy.port.highpower.core.MagneticItemPullRules.RANGE))continue;inRange++;
            var hit=mc.level.clip(new ClipContext(mc.player.getEyePosition(),position,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()!=HitResult.Type.MISS)continue;visible++;
            Vec3 relative=position.subtract(camera);
            Vector4f clip=transform.transform(new Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1));
            var point=MagneticItemPullSelectionGeometry.project(item.getUUID(),clip.x,clip.y,clip.z,clip.w,distanceSquared,screen.width,screen.height);
            if(point!=null)points.add(point);
            if(DIAGNOSTICS&&samples.size()<4)samples.add("entity="+item.getId()+" clip="+clip+" gui="+(point==null?"rejected":point.x()+","+point.y()));
        }
        screen.points=List.copyOf(points);screen.projectedAt=net.minecraft.Util.getMillis();
        if(DIAGNOSTICS&&screen.diagnosticFrames<8&&(screen.diagnosticFrames==0||screen.projectedAt-screen.lastDiagnostic>=500)){
            screen.diagnosticFrames++;screen.lastDiagnostic=screen.projectedAt;
            DIAGNOSTIC_LOG.info("NEW133_SELECT frame input={} age={} viewport={}x{} cursor={},{} rect={} examined={} items={} tagged={} inRange={} visible={} projected={} selected={} samples={}",
                    screen.input.input(),screen.age,screen.width,screen.height,screen.mouseX,screen.mouseY,screen.rectangle(),examined,itemCount,tagged,inRange,visible,points.size(),screen.candidates().size(),samples);
        }
    }
    private static boolean candidateItem(ItemEntity item) {
        return item.isAlive()&&!item.isRemoved()&&!item.getItem().isEmpty()&&item.getItem().is(MAGNETIC)&&!item.getItem().is(IMMOVABLE);
    }
    private static final class SelectionScreen extends Screen {
        final HighPowerInput.Input input;
        final boolean arrows;
        boolean arrowPreviewShown;
        final ClientLevel level;
        final Entity player;
        final Object connection;
        final int preset;
        final long revision,session;
        double mouseX,mouseY;
        long projectedAt;
        int age,diagnosticFrames;
        long lastDiagnostic;
        List<MagneticItemPullSelectionGeometry.Point> points=List.of();
        SelectionScreen(HighPowerInput.Input input,Minecraft mc,boolean arrows) {
            super(Component.translatable(arrows?"academy.highpower.item_pull.arrow_mode":"academy.highpower.item_pull.select"));this.input=input;this.arrows=arrows;
            level=mc.level;player=mc.player;connection=mc.getConnection();session=AcademyClient.inputSessionEpoch();
            preset=AcademyClient.state.presets.current();revision=AcademyClient.state.presets.revision();
        }
        @Override public void resize(Minecraft mc,int width,int height) { cancel(); }
        @Override protected void init() { mouseX=width/2.0;mouseY=height/2.0;points=List.of();projectedAt=0; }
        boolean valid() {
            Minecraft mc=Minecraft.getInstance();var state=AcademyClient.state;
            return active==this&&mc.screen==this&&mc.level==level&&mc.player==player&&mc.getConnection()==connection
                    &&mc.isWindowActive()&&player.isAlive()&&!player.isRemoved()&&!mc.player.isSpectator()
                    &&AcademyClient.sameInputSessionEpoch(session)&&AcademyClient.singleOwnerEpochMatches(input.epoch())
                    &&player.getId()==input.actor()&&state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress()
                    &&state.presets.current()==preset&&state.presets.revision()==revision
                    &&state.presets.currentSkill(input.slot()).equals(HighPowerSkills.MAGNETIC_ITEM_PULL)
                    &&HighPowerSkills.eligible(state,HighPowerSkills.MAGNETIC_ITEM_PULL);
        }
        MagneticItemPullSelectionGeometry.Rect rectangle() { return MagneticItemPullSelectionGeometry.rectangle(width,height,mouseX,mouseY); }
        List<UUID> candidates() {
            if(!MagneticItemPullSelectionGeometry.fresh(net.minecraft.Util.getMillis(),projectedAt))return List.of();
            return MagneticItemPullSelectionGeometry.candidates(rectangle(),points);
        }
        void diagnosticRelease(int selected) {
            if(DIAGNOSTICS)DIAGNOSTIC_LOG.info("NEW133_SELECT release input={} age={} viewport={}x{} cursor={},{} rect={} projectionAgeMs={} projected={} selected={}",
                    input.input(),age,width,height,mouseX,mouseY,rectangle(),projectedAt==0?-1:net.minecraft.Util.getMillis()-projectedAt,points.size(),selected);
        }
        @Override public void render(GuiGraphics graphics,int x,int y,float partial) {
            if(arrows){
                boolean ready=MagneticArrowSeparationEffects.previewReady(input);
                arrowPreviewShown|=ready;
                graphics.drawCenteredString(font,Component.translatable("academy.highpower.item_pull.arrow_mode"),width/2,12,0xffc5e8ff);
                graphics.drawCenteredString(font,Component.translatable("academy.highpower.item_pull.arrow_bounds",MagneticArrowSeparationEffects.previewRange()),width/2,24,0xffb6c9d3);
                graphics.drawCenteredString(font,Component.translatable(ready?"academy.highpower.item_pull.arrow_release":"academy.highpower.item_pull.arrow_waiting"),width/2,height-36,0xffc5e8ff);
                graphics.drawCenteredString(font,Component.translatable("academy.highpower.item_pull.arrow_payment"),width/2,height-24,0xffb6c9d3);
                return;
            }
            mouseX=x;mouseY=y;var rect=rectangle();var selected=new HashSet<>(candidates());
            int left=(int)rect.left(),top=(int)rect.top(),right=(int)rect.right(),bottom=(int)rect.bottom();
            graphics.fill(left,top,right,bottom,0x1679bde5);outline(graphics,left,top,right,bottom,0x998cceff);
            for(var point:points){int px=(int)Math.round(point.x()),py=(int)Math.round(point.y());
                outline(graphics,px-3,py-3,px+4,py+4,selected.contains(point.id())?0xffb0dfff:0x557b9faf);
            }
            graphics.drawCenteredString(font,Component.translatable("academy.highpower.item_pull.select"),width/2,12,0xffc5e8ff);
            graphics.drawCenteredString(font,Component.translatable("academy.highpower.item_pull.candidates",selected.size()),width/2,24,0xffb6c9d3);
            graphics.drawCenteredString(font,Component.translatable("academy.highpower.item_pull.release"),width/2,height-24,0xffc5e8ff);
            graphics.drawCenteredString(font,Component.translatable("academy.highpower.item_pull.mode_hint",Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage()),width/2,height-12,0xffb6c9d3);
        }
        private static void outline(GuiGraphics g,int x0,int y0,int x1,int y1,int color) {
            g.fill(x0,y0,x1,y0+1,color);g.fill(x0,y1-1,x1,y1,color);g.fill(x0,y0,x0+1,y1,color);g.fill(x1-1,y0,x1,y1,color);
        }
        @Override public boolean isPauseScreen() { return false; }
        @Override public boolean keyPressed(int key,int scan,int modifiers) {
            if(key==GLFW.GLFW_KEY_ESCAPE||AcademyClient.TOGGLE.matches(key,scan)||AcademyClient.SWITCH_PRESET.matches(key,scan)||AcademyClient.EDIT_PRESET.matches(key,scan))cancel();
            return true;
        }
        @Override public boolean keyReleased(int key,int scan,int modifiers) { return true; }
        @Override public boolean mouseClicked(double x,double y,int button) { mouseX=x;mouseY=y;return true; }
        @Override public boolean mouseReleased(double x,double y,int button) { mouseX=x;mouseY=y;return true; }
        @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) { mouseX=x;mouseY=y;return true; }
        @Override public void onClose() { cancel(); }
        @Override public void removed() { if(active==this){active=null;AcademyClient.cancelItemPullInput();} }
    }
}
