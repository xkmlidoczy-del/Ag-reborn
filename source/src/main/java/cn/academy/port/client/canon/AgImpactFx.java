package cn.academy.port.client.canon;

import cn.academy.port.VisualTier;
import cn.academy.port.VisualTierConfig;
import cn.academy.port.macro.BeamImpactVfx;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;

/** Material fragments and surface heat from server-confirmed removed cells, never invented craters. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
final class AgImpactFx {
    private record Fragment(BlockState state,Vec3 from,Vec3 velocity,double born,double size,double spin){}
    private record Mark(Vec3 centre,Vec3 normal,double radius,double born,int life,double seed,boolean heat){}
    private static final List<Fragment> FRAGMENTS=new ArrayList<>();
    private static final List<Mark> MARKS=new ArrayList<>();
    private static final Map<UUID,Set<BlockPos>> SEEN=new LinkedHashMap<>();
    private static ClientLevel world;private static MultiBufferSource.BufferSource buffers;
    private AgImpactFx(){}
    private static void sync(){var w=Minecraft.getInstance().level;if(w!=world){world=w;FRAGMENTS.clear();MARKS.clear();SEEN.clear();}}
    static void accept(BeamImpactVfx.Scene scene){
        sync();if(world==null||VisualTierConfig.tier()==VisualTier.OFF)return;
        if(SEEN.size()>64)SEEN.remove(SEEN.keySet().iterator().next());var seen=SEEN.computeIfAbsent(scene.cast(),k->new HashSet<>());
        int max=VisualTierConfig.tier()==VisualTier.CINEMATIC?72:VisualTierConfig.tier()==VisualTier.STANDARD?40:16;
        double now=world.getGameTime();Vec3 axis=scene.to().subtract(scene.from()).normalize();
        for(var cell:scene.cells()){
            var pos=cell.position();if(!seen.add(pos)||!world.hasChunkAt(pos)||pos.getCenter().distanceToSqr(scene.from())>192*192)continue;
            BlockState state=Block.stateById(cell.originalState());if(state.isAir()||state.hasBlockEntity())continue;
            var random=new Random(pos.asLong()^scene.cast().getLeastSignificantBits());
            if(state.getFluidState().isEmpty()&&state.canOcclude()){
                if(FRAGMENTS.size()>=max)FRAGMENTS.removeFirst();
                Vec3 away=pos.getCenter().subtract(scene.from()).subtract(axis.scale(pos.getCenter().subtract(scene.from()).dot(axis)));if(away.lengthSqr()<.01)away=new Vec3(random.nextDouble()-.5,0,random.nextDouble()-.5);away=away.normalize();
                FRAGMENTS.add(new Fragment(state,pos.getCenter(),away.scale(.07+random.nextDouble()*.09).add(axis.scale(.025)).add(0,.10+random.nextDouble()*.10,0),now,.12+random.nextDouble()*.18,random.nextDouble()*360));
            }
            for(int i=0;i<(max>40?3:1);i++)world.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,state),pos.getX()+random.nextDouble(),pos.getY()+random.nextDouble(),pos.getZ()+random.nextDouble(),(random.nextDouble()-.5)*.08,.05+random.nextDouble()*.07,(random.nextDouble()-.5)*.08);
            if(scene.style()==BeamImpactVfx.Style.BEAM)for(Direction side:Direction.values()){
                BlockPos neighbour=pos.relative(side);if(!world.hasChunkAt(neighbour))continue;var surface=world.getBlockState(neighbour);if(surface.isAir()||surface.hasBlockEntity()||!surface.canOcclude())continue;
                Vec3 n=new Vec3(-side.getStepX(),-side.getStepY(),-side.getStepZ());Vec3 at=pos.getCenter().add(side.getStepX()*.498,side.getStepY()*.498,side.getStepZ()*.498);
                addMark(new Mark(at,n,.64,now,90,random.nextDouble(),true));
            }
        }
        for(var point:scene.impacts())if(cn.academy.port.macro.MacroVfx.finite(point)&&point.distanceToSqr(scene.from())<192*192){
            addMark(new Mark(point,axis.scale(-1),Math.min(1.7,.35+scene.radius()*.25),now,18,new Random(scene.cast().getMostSignificantBits()).nextDouble(),false));
        }
    }
    private static void addMark(Mark mark){if(MARKS.size()>=192)MARKS.removeFirst();MARKS.add(mark);}
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||AgVolumeRenderer.shadow())return;sync();if(world==null||VisualTierConfig.tier()==VisualTier.OFF)return;
        double now=world.getGameTime()+e.getPartialTick().getGameTimeDeltaPartialTick(false);FRAGMENTS.removeIf(f->now-f.born>30);MARKS.removeIf(m->now-m.born>m.life);
        if(FRAGMENTS.isEmpty()&&MARKS.isEmpty())return;if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(1<<18));var mc=Minecraft.getInstance();var camera=e.getCamera().getPosition();var stack=RenderSystem.getModelViewStack();stack.pushMatrix();
        try{stack.set(e.getModelViewMatrix());RenderSystem.applyModelViewMatrix();var ps=e.getPoseStack();
            for(var f:List.copyOf(FRAGMENTS)){
                double age=now-f.born;Vec3 at=f.from.add(f.velocity.scale(age)).add(0,-.006*age*age,0);if(camera.distanceToSqr(at)>112*112||!world.hasChunkAt(BlockPos.containing(at)))continue;
                var shape=world.getBlockState(BlockPos.containing(at)).getCollisionShape(world,BlockPos.containing(at));if(!shape.isEmpty()&&age>3)continue;
                ps.pushPose();ps.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);ps.mulPose(Axis.YP.rotationDegrees((float)(f.spin+age*7)));ps.mulPose(Axis.XP.rotationDegrees((float)(age*11)));float size=(float)(f.size*Math.min(1,(30-age)/7));ps.scale(size,size,size);ps.translate(-.5,-.5,-.5);mc.getBlockRenderer().renderSingleBlock(f.state,ps,buffers,0xC000C0,OverlayTexture.NO_OVERLAY);ps.popPose();
            }
            buffers.endBatch();
            for(var mark:List.copyOf(MARKS))if(AgEffectMaterials.surfaces()&&camera.distanceToSqr(mark.centre)<128*128){
                Vec3 n=mark.normal,side=n.cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=new Vec3(1,0,0);else side=side.normalize();Vec3 up=side.cross(n).normalize();
                double age=Math.clamp((now-mark.born)/mark.life,0,1),grow=mark.heat?1:.5+age*1.3;side=side.scale(mark.radius*grow);up=up.scale(mark.radius*grow);
                var out=buffers.getBuffer(AgEffectMaterials.type(mark.heat?4:3));var centre=mark.centre.add(n.scale(.012));
                vertex(out,ps.last().pose(),camera,centre.subtract(side).subtract(up),0,0,age,mark.seed,1-age);vertex(out,ps.last().pose(),camera,centre.add(side).subtract(up),1,0,age,mark.seed,1-age);vertex(out,ps.last().pose(),camera,centre.add(side).add(up),1,1,age,mark.seed,1-age);vertex(out,ps.last().pose(),camera,centre.subtract(side).add(up),0,1,age,mark.seed,1-age);
            }buffers.endBatch();
        }finally{stack.popMatrix();RenderSystem.applyModelViewMatrix();}
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 cam,Vec3 p,float u,float v,double age,double seed,double fade){p=p.subtract(cam);out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor((int)(age*255),(int)(seed*255),255,(int)(fade*255));}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){FRAGMENTS.clear();MARKS.clear();SEEN.clear();world=null;}
}
