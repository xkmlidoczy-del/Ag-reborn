package cn.academy.port.client.vfx2;

import cn.academy.port.ClassicHudConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.joml.Matrix4f;
import java.util.Set;

/** No packet listener, independent effects queue, particles, sound or gameplay callbacks. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class RailgunVfx2 {
    /** Original inner cylinder: twelve sides, one body plus eight cap quads, four vertices. */
    public static final int INNER_CORE_VERTICES = 12 * 9 * 4;
    private static int drawn, coreDrawn, vertices, peakVertices;
    private static boolean frameGrand;
    private static boolean frameNearEye;
    private static long frameNanos, samples, totalNanos, peakNanos;
    private static long reportedCoreFailure = Long.MIN_VALUE;
    private static ClassicHudConfig.Vfx2Quality frameQuality = ClassicHudConfig.Vfx2Quality.OFF;
    private RailgunVfx2() {}
    public static void beginFrame() {
        drawn=coreDrawn=vertices=0; frameNanos=0;
        frameQuality=ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.VFX2_QUALITY.get():ClassicHudConfig.Vfx2Quality.OFF;
        frameGrand=ClassicHudConfig.SPEC.isLoaded()&&ClassicHudConfig.GRAND_RAILGUN.get()&&frameQuality!=ClassicHudConfig.Vfx2Quality.OFF;
        frameNearEye=ClassicHudConfig.SPEC.isLoaded()&&ClassicHudConfig.RAILGUN_NEAR_EYE_VISIBILITY.get()&&frameQuality!=ClassicHudConfig.Vfx2Quality.OFF;
        RailgunGrandPass.beginFrame(frameGrand);
    }
    public static int maxInstances() {
        if(frameGrand)return RailgunGrandPass.available()?RailgunGrandPass.MAX_BEAMS:0;
        if (!RailgunVfx2Material.available()) return 0;
        return switch(frameQuality) { case OFF -> 0; case LOW -> 8; case MEDIUM -> 16; };
    }
    public static double maxDistance() { return !frameGrand&&frameQuality==ClassicHudConfig.Vfx2Quality.LOW?32:64; }
    public static int maxCoreInstances() {
        return !frameGrand && frameQuality==ClassicHudConfig.Vfx2Quality.MEDIUM && RailgunVfx2Material.coreAvailable()?4:0;
    }
    /** Acquire and revalidate before the caller omits even one classic inner-core vertex. */
    public static VertexConsumer prepareInnerCore(long age,MultiBufferSource buffers,Set<RenderType> used) {
        if(age<0||age>=2500||coreDrawn>=maxCoreInstances()||vertices+INNER_CORE_VERTICES>4096)return null;
        long generation=RailgunVfx2Material.coreGeneration(), started=System.nanoTime();
        try {
            VertexConsumer target=buffers.getBuffer(RailgunVfx2RenderTypes.INNER_CORE);
            if(!RailgunVfx2Material.coreAvailable()||generation!=RailgunVfx2Material.coreGeneration())return null;
            used.add(RailgunVfx2RenderTypes.INNER_CORE);
            coreDrawn++;vertices+=INNER_CORE_VERTICES;
            return new CorePhaseConsumer(target,age/1800f,started);
        } catch(RuntimeException failure) {
            if(reportedCoreFailure!=generation) {
                reportedCoreFailure=generation;
                com.mojang.logging.LogUtils.getLogger().warn("Railgun VFX2 inner material unavailable; original core retained",failure);
            }
            return null;
        }
    }
    /** Only the UV phase changes. Every position, color, alpha and other attribute is forwarded. */
    private static final class CorePhaseConsumer implements VertexConsumer {
        private final VertexConsumer target;
        private final float phase;
        private final long started;
        private int colors;
        private CorePhaseConsumer(VertexConsumer target,float phase,long started){this.target=target;this.phase=phase;this.started=started;}
        @Override public VertexConsumer addVertex(float x,float y,float z){target.addVertex(x,y,z);return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){target.setColor(r,g,b,a);if(++colors==INNER_CORE_VERTICES)frameNanos+=System.nanoTime()-started;return this;}
        @Override public VertexConsumer setUv(float u,float v){target.setUv(u+phase,v);return this;}
        @Override public VertexConsumer setUv1(int u,int v){target.setUv1(u,v);return this;}
        @Override public VertexConsumer setUv2(int u,int v){target.setUv2(u,v);return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){target.setNormal(x,y,z);return this;}
    }
    public static double distanceSquared(Vec3 origin,Vec3 direction,double length,Vec3 camera) {
        if(origin==null||direction==null||camera==null||!Double.isFinite(length)||length<=0)return Double.POSITIVE_INFINITY;
        double t=Math.max(0,Math.min(length,camera.subtract(origin).dot(direction)));
        return camera.distanceToSqr(origin.add(direction.scale(t)));
    }
    public static void render(Vec3 origin,Vec3 direction,double length,Vec3 camera,long age,double widthScale,
                              long seed,Matrix4f matrix,MultiBufferSource buffers,Set<RenderType> used) {
        if(drawn>=maxInstances()||length<=0||!Double.isFinite(length))return;
        long started=System.nanoTime();
        if(frameGrand){int count=RailgunGrandPass.submit(origin,direction,length,camera,age,widthScale);if(count>0){drawn++;vertices+=count;}frameNanos+=System.nanoTime()-started;return;}
        var quality=RailgunOverlayGeometry.Quality.valueOf(frameQuality.name());
        var quads=RailgunOverlayGeometry.buildAlong(vec(origin),vec(direction),length,vec(camera),age,widthScale,seed,quality);
        int count=quads.size()*4, limit=frameQuality==ClassicHudConfig.Vfx2Quality.LOW?512:4096;
        if(count==0||vertices+count>limit)return;
        for(var quad:quads) {
            boolean halo=quad.layer()==RailgunOverlayGeometry.Layer.HALO;
            RenderType type=halo?RailgunVfx2RenderTypes.HALO:RailgunVfx2RenderTypes.CORE;
            used.add(type);VertexConsumer out=buffers.getBuffer(type);
            for(var vertex:java.util.List.of(quad.a(),quad.b(),quad.c(),quad.d())) {
                var p=vertex.position();
                out.addVertex(matrix,(float)p.x(),(float)p.y(),(float)p.z()).setUv((float)vertex.u(),(float)vertex.v())
                        .setColor(halo?236:255,halo?170:240,halo?93:214,(int)Math.round(vertex.alpha()*255));
            }
        }
        drawn++;vertices+=count;frameNanos+=System.nanoTime()-started;
    }
    private static RailgunOverlayGeometry.Vec vec(Vec3 p){return new RailgunOverlayGeometry.Vec(p.x,p.y,p.z);}
    public static boolean grandSelected(){return frameGrand;}
    public static boolean nearEyeSelected(){return frameNearEye;}
    public static void cameraForward(Vec3 value){RailgunGrandPass.cameraForward(value);}
    public static double priority(Object key,double distanceSquared){return frameGrand?RailgunGrandPass.priority(key,distanceSquared):distanceSquared;}
    public static void selected(Object key){if(frameGrand)RailgunGrandPass.selected(key);}
    public static void clearGrand(){RailgunGrandPass.clear();}
    public static void flushGrand(Matrix4f matrix,MultiBufferSource.BufferSource buffers){if(frameGrand){long started=System.nanoTime();RailgunGrandPass.flush(matrix,buffers);frameNanos+=System.nanoTime()-started;}}
    public static void finishFrame(){RailgunGrandPass.finishFrame();if(drawn>0||coreDrawn>0){samples++;totalNanos+=frameNanos;peakNanos=Math.max(peakNanos,frameNanos);peakVertices=Math.max(peakVertices,vertices);}}
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        var root=Commands.literal("aca_vfx2").executes(c->status());
        for(var quality:ClassicHudConfig.Vfx2Quality.values())root.then(Commands.literal(quality.name().toLowerCase(java.util.Locale.ROOT)).executes(c->set(quality)));
        root.then(Commands.literal("status").executes(c->status()));
        root.then(Commands.literal("grand").executes(c->grandStatus())
                .then(Commands.literal("on").executes(c->setGrand(true)))
                .then(Commands.literal("off").executes(c->setGrand(false)))
                .then(Commands.literal("status").executes(c->grandStatus())));
        root.then(Commands.literal("near_eye").executes(c->nearEyeStatus())
                .then(Commands.literal("on").executes(c->setNearEye(true)))
                .then(Commands.literal("off").executes(c->setNearEye(false)))
                .then(Commands.literal("status").executes(c->nearEyeStatus())));
        event.getDispatcher().register(root);
    }
    private static int setNearEye(boolean enabled){
        if(!ClassicHudConfig.SPEC.isLoaded())return 0;
        ClassicHudConfig.RAILGUN_NEAR_EYE_VISIBILITY.set(enabled);ClassicHudConfig.SPEC.save();return nearEyeStatus();
    }
    private static int nearEyeStatus(){
        var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.translatable("academy.vfx2.near_eye",
                ClassicHudConfig.SPEC.isLoaded()&&ClassicHudConfig.RAILGUN_NEAR_EYE_VISIBILITY.get(),
                cn.academy.port.client.RailgunNearEyeMaterial.available()),false);
        return 1;
    }
    private static int setGrand(boolean enabled){
        if(!ClassicHudConfig.SPEC.isLoaded())return 0;
        ClassicHudConfig.GRAND_RAILGUN.set(enabled);ClassicHudConfig.SPEC.save();
        samples=totalNanos=peakNanos=0;peakVertices=0;RailgunGrandPass.clear();return grandStatus();
    }
    private static int grandStatus(){
        var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.translatable("academy.vfx2.grand",
                ClassicHudConfig.SPEC.isLoaded()&&ClassicHudConfig.GRAND_RAILGUN.get(),RailgunGrandPass.available(),RailgunGrandPass.beams(),RailgunGrandPass.vertices(),RailgunGrandPass.batches()),false);
        return 1;
    }
    private static int set(ClassicHudConfig.Vfx2Quality quality) {
        if(!ClassicHudConfig.SPEC.isLoaded())return 0;
        ClassicHudConfig.VFX2_QUALITY.set(quality);ClassicHudConfig.SPEC.save();
        var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.translatable("academy.vfx2.quality",quality.name()),false);
        return 1;
    }
    private static int status() {
        var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.translatable("academy.vfx2.status",
                ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.VFX2_QUALITY.get().name():"OFF",RailgunVfx2Material.available(),RailgunVfx2Material.coreAvailable(),drawn,coreDrawn,vertices,peakVertices,
                samples==0?0:(totalNanos/samples)/1000,peakNanos/1000,samples),false);
        return 1;
    }
}
