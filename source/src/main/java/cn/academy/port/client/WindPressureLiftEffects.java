package cn.academy.port.client;

import cn.academy.port.highpower.WindPressureLift;
import cn.academy.port.highpower.core.WindPressureLiftRules.Mode;
import cn.academy.port.client.WindPressureLiftMesh.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW443 server-owned permission flow at the real fixed anchor. No particles, platforms, or hit cues. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class WindPressureLiftEffects {
    private static final ResourceLocation GLOW = texture("effects/glow_line"), WHITE = texture("port/white");
    private static final WindPressureLiftState STATE = new WindPressureLiftState();
    private record Binding(Entity owner, WindPressureLiftState.Identity id) {}
    private static final Map<UUID,Binding> OWNERS = new HashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static Boolean enhancedMaterial;
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private WindPressureLiftEffects() {}
    private static ResourceLocation texture(String p) { return ResourceLocation.fromNamespaceAndPath("academy","textures/"+p+".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            WindPressureLift.clientReceiver = WindPressureLiftEffects::receive;
            WindPressureLift.clientRejectionReceiver = WindPressureLiftEffects::reject;
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> Minecraft.getInstance().execute(() -> {
                AcademyClient.cancelWindPressureLiftForReload(); WindPressureLiftClientMotion.clear(); clear();
                if (buffers!=null) buffers.endBatch(); if(storage!=null) storage.close();
                buffers=null; storage=null; enhancedMaterial=null; Material.TYPES.clear();
            }));
        }
    }
    private static WindPressureLiftState.Frame frame(WindPressureLift.Visual p) {
        return new WindPressureLiftState.Frame(new WindPressureLiftState.Identity(p.entity(),p.owner(),p.input(),p.token(),p.epoch()),
                p.sequence(),point(p.anchor()),p.mode(),point(p.direction()),p.issuedAt(),p.expiresAt(),p.terminal());
    }
    public static void receive(WindPressureLift.Visual p) {
        if (p==null || p.dimension()==null) return;
        var f=frame(p); if(!WindPressureLiftState.valid(f)) return;
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld=mc.level; Entity capturedPlayer=mc.player; Object capturedConnection=mc.getConnection();
        long capturedSession=AcademyClient.inputSessionEpoch(), capturedGeneration=generation;
        mc.execute(() -> {
            if(capturedWorld==null || capturedWorld!=mc.level || capturedPlayer!=mc.player || capturedConnection!=mc.getConnection()
                    || capturedGeneration!=generation || !AcademyClient.sameInputSessionEpoch(capturedSession)
                    || !p.dimension().equals(capturedWorld.dimension().location())) return;
            Entity owner=capturedWorld.getEntity(p.entity());
            if(!(owner instanceof Player) || !owner.getUUID().equals(p.owner())) return;
            Binding old=OWNERS.get(p.owner());
            if(old!=null && old.id.equals(f.id()) && old.owner!=owner) return;
            boolean local=owner==mc.player;
            if(local && (!AcademyClient.singleOwnerEpochMatches(p.epoch())
                    || !AcademyClient.windPressureLiftInputMatches(p.input(),p.epoch()))) return;
            if(local && p.terminal()) {
                // Cancellation suppresses late active flow, but must not suppress the matching off acknowledgement.
                STATE.retire(f.id());
                Binding binding=OWNERS.get(p.owner());if(binding!=null && binding.id.equals(f.id())) OWNERS.remove(p.owner());
                WindPressureLiftClientMotion.abortLocal(p.input(),p.epoch());
                AcademyClient.windPressureLiftEndedInput(p.input(),p.epoch()); return;
            }
            if(!p.terminal() && (!owner.isAlive() || owner.isRemoved() || owner.isSpectator()
                    || owner.position().distanceToSqr(p.anchor())>49 || local && (!AcademyClient.windPressureLiftControlValid()
                    || mc.screen!=null || !mc.isWindowActive() || mc.isPaused()))) return;
            if(!STATE.accept(f,local)) return;
            if(p.terminal()) {
                OWNERS.remove(p.owner());
                if(local) { WindPressureLiftClientMotion.abortLocal(p.input(),p.epoch()); AcademyClient.windPressureLiftEndedInput(p.input(),p.epoch()); }
            } else {
                OWNERS.put(p.owner(),new Binding(owner,f.id()));
                if(local) AcademyClient.windPressureLiftAcceptedInput(p.input(),p.epoch());
            }
            OWNERS.entrySet().removeIf(e -> STATE.current(e.getKey())==null);
        });
    }
    private static void reject(WindPressureLift.Rejection p) {
        if(p==null || p.owner()==null || p.entity()<0 || p.input()<=0 || p.epoch()<=0) return;
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld=mc.level; Entity capturedPlayer=mc.player; Object capturedConnection=mc.getConnection();
        long capturedGeneration=generation, capturedSession=AcademyClient.inputSessionEpoch();
        mc.execute(() -> {
            if(capturedWorld==null || capturedPlayer==null || capturedWorld!=mc.level || capturedPlayer!=mc.player
                    || capturedConnection!=mc.getConnection() || capturedGeneration!=generation
                    || !AcademyClient.sameInputSessionEpoch(capturedSession) || p.entity()!=capturedPlayer.getId()
                    || !p.owner().equals(capturedPlayer.getUUID()) || !AcademyClient.singleOwnerEpochMatches(p.epoch())) return;
            WindPressureLiftClientMotion.abortLocal(p.input(),p.epoch()); cancelLocal(p.input(),p.epoch());
            AcademyClient.windPressureLiftRejectedInput(p.input(),p.epoch());
        });
    }
    public static void cancelLocal(long input,long epoch) {
        synchronize(Minecraft.getInstance()); STATE.cancel(input,epoch); OWNERS.entrySet().removeIf(e -> STATE.current(e.getKey())==null);
    }
    public static void abortLocal(long input,long epoch) { cancelLocal(input,epoch); }
    public static void clear() { generation++;STATE.clear();OWNERS.clear(); }
    private static void synchronize(Minecraft mc) {
        if(world==mc.level && localPlayer==mc.player && connection==mc.getConnection()) return;
        clear(); if(connection!=mc.getConnection()) STATE.resetSession();
        world=mc.level;localPlayer=mc.player;connection=mc.getConnection();
    }
    private static boolean validOwner(WindPressureLiftState.Controller c,Minecraft mc) {
        if(c==null || world==null) return false;
        Binding b=OWNERS.get(c.id().owner()); if(b==null || !b.id.equals(c.id())) return false;
        Entity owner=b.owner;
        return owner instanceof Player && world.getEntity(c.id().entity())==owner && owner.getUUID().equals(c.id().owner())
                && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator()
                && owner.position().distanceToSqr(vec(c.frame().anchor()))<=49
                && (!c.local() || owner==mc.player && mc.screen==null && mc.isWindowActive() && !mc.isPaused()
                && AcademyClient.singleOwnerEpochMatches(c.id().epoch())
                && AcademyClient.windPressureLiftInputMatches(c.id().input(),c.id().epoch()) && AcademyClient.windPressureLiftControlValid());
    }
    public static boolean activeFor(UUID owner) {
        Minecraft mc=Minecraft.getInstance();synchronize(mc); var c=owner==null?null:STATE.current(owner);
        return c!=null && c.quiet()<WindPressureLiftState.QUIET_TICKS && validOwner(c,mc);
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc=Minecraft.getInstance();synchronize(mc);
        return mc.player!=null && activeFor(mc.player.getUUID())?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
    }
    public static boolean activeLocal() { return localVisual()==ClassicAbilityHud.DelegateVisual.ACTIVE; }
    public static String localHint() {
        Minecraft mc=Minecraft.getInstance();synchronize(mc);var c=mc.player==null?null:STATE.current(mc.player.getUUID());
        if(c==null || !activeLocal()) return "";
        String mode=c.frame().mode()==Mode.UP?"up":"side";
        String label=Component.translatableWithFallback("hud.academy.highpower.wind_pressure_lift."+mode,mode.equals("up")?"UP":"SIDE").getString();
        return Component.translatableWithFallback("hud.academy.highpower.wind_pressure_lift",
                "Wind %s | self only; assistance conditional; cancel may cause a fall",label).getString();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();synchronize(mc);
        List<WindPressureLiftState.Identity> local=STATE.controllers().stream().filter(WindPressureLiftState.Controller::local).map(WindPressureLiftState.Controller::id).toList();
        STATE.tick(mc.isPaused(),c -> validOwner(c,mc));
        OWNERS.entrySet().removeIf(e -> STATE.current(e.getKey())==null);
        for(var id:local) if(STATE.current(id.owner())==null) {
            WindPressureLiftClientMotion.abortLocal(id.input(),id.epoch());
            AcademyClient.windPressureLiftLocalVisualLost(id.input(),id.epoch());
        }
    }
    private static boolean visible(WindPressureLiftState.Controller c,RenderLevelStageEvent event,Vec3 camera) {
        Point a=c.frame().anchor();if(camera.distanceToSqr(vec(a))>WindPressureLiftMesh.CULL_DISTANCE*WindPressureLiftMesh.CULL_DISTANCE) return false;
        AABB bounds=new AABB(a.x()-3,a.y(),a.z()-3,a.x()+3,a.y()+6,a.z()+3);
        for(int x=(int)Math.floor(bounds.minX)>>4;x<=((int)Math.floor(bounds.maxX)>>4);x++)
            for(int z=(int)Math.floor(bounds.minZ)>>4;z<=((int)Math.floor(bounds.maxZ)>>4);z++)
                if(!world.hasChunkAt(new BlockPos(x*16,(int)Math.floor(a.y()),z*16))) return false;
        return event.getFrustum().isVisible(bounds);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc=Minecraft.getInstance();synchronize(mc);if(world==null || STATE.size()==0) return;
        Vec3 camera=event.getCamera().getPosition();Point eye=point(camera);
        var visible=STATE.controllers().stream().filter(c -> c.quiet()<WindPressureLiftState.QUIET_TICKS && validOwner(c,mc) && visible(c,event,camera))
                .sorted(Comparator.<WindPressureLiftState.Controller>comparingInt(c -> c.local()?0:1)
                        .thenComparingDouble(c -> camera.distanceToSqr(vec(c.frame().anchor()))).thenComparingInt(c -> c.id().entity()))
                .limit(WindPressureLiftMesh.MAX_CONTEXTS).toList();
        float partial=mc.isPaused()?0:event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (WindPressureLiftFlowRenderer.render(visible, event, partial)) return;
        var budget=new WindPressureLiftMesh.Budget();
        var shapes=visible.stream().map(c -> WindPressureLiftMesh.shape(c,partial)).filter(s -> budget.take(s.vertexCount())).toList();
        if(shapes.isEmpty()) return;
        if(enhancedMaterial==null) enhancedMaterial=mc.getResourceManager().getResource(GLOW).isPresent() && mc.getResourceManager().getResource(WHITE).isPresent();
        if(buffers==null) {storage=new ByteBufferBuilder(65536);buffers=MultiBufferSource.immediate(storage);}
        var look=event.getCamera().getLookVector();Point forward=new Point(look.x(),look.y(),look.z());
        var left=event.getCamera().getLeftVector();Vec3 fallback=new Vec3(left.x(),left.y(),left.z());
        var poses=event.getPoseStack();var modelView=RenderSystem.getModelViewStack();float[] color=RenderSystem.getShaderColor().clone();
        poses.pushPose();modelView.pushMatrix();
        try {
            RenderSystem.setShaderColor(1,1,1,1);modelView.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
            Matrix4f matrix=poses.last().pose();
            // Missing material switches the entire accepted group to the same sparse color-only strokes.
            for(Pass pass:enhancedMaterial?new Pass[]{Pass.SOFT,Pass.CORE}:new Pass[]{Pass.SIMPLE}) {
                VertexConsumer out=buffers.getBuffer(Material.quads(pass));
                for(var shape:shapes) for(var stroke:shape.strokes()) stroke(out,matrix,stroke,eye,forward,fallback,pass);
                buffers.endBatch(Material.quads(pass));
            }
        } finally {
            try {buffers.endBatch();} finally {poses.popPose();modelView.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);}
        }
    }
    private static void stroke(VertexConsumer out,Matrix4f matrix,WindPressureLiftMesh.Stroke stroke,Point camera,Point forward,Vec3 fallback,Pass pass) {
        double width=stroke.width()*(pass==Pass.SOFT?2.4:1);int gray=pass==Pass.SOFT?162:231;
        double alpha=stroke.alpha()*(pass==Pass.SOFT?.22:1);
        var clipped=WindPressureLiftMesh.clipNear(new WindPressureLiftMesh.Stroke(WindPressureLiftMesh.relative(stroke.from(),camera),
                WindPressureLiftMesh.relative(stroke.to(),camera),width,(int)alpha),forward,width/2);
        if(clipped==null) return;
        Vec3 a=vec(clipped.from()),b=vec(clipped.to());Vec3 side=b.subtract(a).cross(a.add(b).scale(-.5)).normalize();
        if(side.lengthSqr()<1e-10) side=fallback;side=side.scale(width/2);
        int aa=(int)Math.round(alpha*WindPressureLiftMesh.nearFade(clipped.from())),ab=(int)Math.round(alpha*WindPressureLiftMesh.nearFade(clipped.to()));
        vertex(out,matrix,a.subtract(side),0,1,gray,aa,pass==Pass.SIMPLE);vertex(out,matrix,a.add(side),0,0,gray,aa,pass==Pass.SIMPLE);
        vertex(out,matrix,b.add(side),1,0,gray,ab,pass==Pass.SIMPLE);vertex(out,matrix,b.subtract(side),1,1,gray,ab,pass==Pass.SIMPLE);
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 p,float u,float v,int gray,int alpha,boolean simple) {
        var vertex=out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z);if(!simple) vertex.setUv(u,v);vertex.setColor(gray,gray,gray,alpha);
    }
    private static Point point(Vec3 p) {return p==null?null:new Point(p.x,p.y,p.z);}
    private static Vec3 vec(Point p) {return new Vec3(p.x(),p.y(),p.z());}
    private enum Pass {SOFT,CORE,SIMPLE}
    private static final class Material extends RenderType {
        private static final Map<Pass,RenderType> TYPES=new HashMap<>();
        private Material(String n,VertexFormat f,VertexFormat.Mode m,int s,boolean c,boolean sorted,Runnable set,Runnable reset) {super(n,f,m,s,c,sorted,set,reset);}
        static RenderType quads(Pass pass) {
            return TYPES.computeIfAbsent(pass,p -> {
                var state=CompositeState.builder().setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY).setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET);
                if(p==Pass.SIMPLE) state.setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader));
                else state.setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader)).setTextureState(new TextureStateShard(p==Pass.SOFT?GLOW:WHITE,false,false));
                return create("academy_wind_pressure_lift_"+p,p==Pass.SIMPLE?DefaultVertexFormat.POSITION_COLOR:DefaultVertexFormat.POSITION_TEX_COLOR,
                        VertexFormat.Mode.QUADS,8192,false,true,state.createCompositeState(false));
            });
        }
    }
}
