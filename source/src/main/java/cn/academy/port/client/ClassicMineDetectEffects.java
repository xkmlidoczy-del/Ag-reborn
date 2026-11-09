/* AcademyCraft MineDetect local HandlerEntity/HandlerRender adapter. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.skill.ClassicMineOreAdapter;
import cn.academy.port.skill.ClassicMineScan;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.util.RandomSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;

/** Original textured six-face boxes through walls. Source-faithful input parity; pixels unverified. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ClassicMineDetectEffects {
    private static final SoundEvent SOUND=SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy",ClassicMineVisual.SOUND));
    private static final List<ClassicMineScan.Handler> HANDLERS=new ArrayList<>();
    private static final int MAX_HANDLERS=4, BOXES_PER_BATCH=1000;
    // SoundEngine does not mark naturally completed tickables stopped. Retain at most four
    // handles for explicit session cleanup; no assumption that isStopped means playback ended.
    private static final List<FollowingSound> SOUNDS=new ArrayList<>();
    private static ClientLevel activeLevel;
    private static Entity activePlayer;
    private static Object activeConnection;
    private static MultiBufferSource.BufferSource buffers;
    private ClassicMineDetectEffects() {}
    public static void receive(CompoundTag data) {
        if(data==null||!"mine_detect".equals(data.getString("kind"))
                ||!data.contains("entity",Tag.TAG_INT)||!data.contains("range",Tag.TAG_ANY_NUMERIC)
                ||!data.contains("advanced",Tag.TAG_BYTE))return;
        var tag=data.copy();var mc=Minecraft.getInstance();var level=mc.level;
        var player=mc.player;var connection=mc.getConnection();
        mc.execute(()->{
            if(level==null||player==null||mc.level!=level||mc.player!=player||mc.getConnection()!=connection
                    ||tag.getInt("entity")!=player.getId())return;
            synchronizeSession(mc);
            float range=tag.getFloat("range");
            if(!Float.isFinite(range)||range<15F||range>30F)return;
            if(HANDLERS.size()>=MAX_HANDLERS)HANDLERS.removeFirst();
            HANDLERS.add(new ClassicMineScan.Handler(player.getX(),player.getY(),player.getZ(),range,tag.getBoolean("advanced")));
            if(SOUNDS.size()>=MAX_HANDLERS)SOUNDS.removeFirst().terminate();
            var sound=new FollowingSound(player,connection);SOUNDS.add(sound);mc.getSoundManager().play(sound);
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();synchronizeSession(mc);
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        var access=ClassicMineOreAdapter.access(mc.level);var p=mc.player;
        HANDLERS.removeIf(handler->!handler.tick(p.getX(),p.getY(),p.getZ(),access));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();synchronizeSession(mc);
        if(mc.level==null||HANDLERS.isEmpty())return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var type=ClassicMineRenderType.TYPE;var poses=event.getPoseStack();var camera=event.getCamera().getPosition();
        // AFTER_LEVEL restores the renderer's model-view stack before this event. Set the
        // event's camera matrix explicitly; its PoseStack is a new identity stack in 1.21.1.
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        float[] color=RenderSystem.getShaderColor().clone();
        try {
        modelView.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderColor(1,1,1,1);
        VertexConsumer out=buffers.getBuffer(type);int batch=0;
        for(var handler:HANDLERS)for(var ore:handler.aliveSims()) {
            int alpha=ClassicMineVisual.alphaByte(ClassicMineVisual.alpha(handler.x()-ore.x(),handler.y()-ore.y(),handler.z()-ore.z(),handler.range));
            // Source still submits zero/negative-alpha boxes; preserve insertion/duplicate ordering.
            draw(poses,out,camera,ore,alpha);
            if(++batch==BOXES_PER_BATCH){buffers.endBatch(type);out=buffers.getBuffer(type);batch=0;}
        }
        buffers.endBatch(type);
        } finally {
            type.clearRenderState();
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            modelView.popMatrix();RenderSystem.applyModelViewMatrix();
        }
    }
    private static void draw(PoseStack poses,VertexConsumer out,Vec3 camera,ClassicMineScan.Element ore,int alpha) {
        poses.pushPose();
        try {
            // Translate in Double before Float vertex conversion, avoiding far-world precision loss.
            poses.translate(ore.x()+ClassicMineVisual.INSET-camera.x,ore.y()+ClassicMineVisual.INSET-camera.y,
                    ore.z()+ClassicMineVisual.INSET-camera.z);
            var matrix=poses.last().pose();
            int r=ClassicMineVisual.color(ore.level(),0),g=ClassicMineVisual.color(ore.level(),1),b=ClassicMineVisual.color(ore.level(),2);
            for(int face=0;face<6;face++)for(int corner=0;corner<4;corner++) {
                var v=ClassicMineVisual.vertex(face,corner);
                out.addVertex(matrix,(float)v.x(),(float)v.y(),(float)v.z()).setUv(v.u(),v.v()).setColor(r,g,b,alpha);
            }
        } finally {poses.popPose();}
    }
    public static void clear(){HANDLERS.clear();for(var sound:SOUNDS)sound.terminate();SOUNDS.clear();}
    private static void synchronizeSession(Minecraft mc) {
        if(activeLevel!=mc.level||activePlayer!=mc.player||activeConnection!=mc.getConnection()) {
            clear();activeLevel=mc.level;activePlayer=mc.player;activeConnection=mc.getConnection();
        }
    }
    private static final class FollowingSound extends AbstractTickableSoundInstance {
        private final Entity caster;private final Object connection;
        FollowingSound(Entity caster,Object connection) {
            super(SOUND,SoundSource.MASTER,RandomSource.create());this.caster=caster;this.connection=connection;
            volume=ClassicMineVisual.SOUND_VOLUME;pitch=1;looping=false;tick();
        }
        @Override public void tick() {
            var mc=Minecraft.getInstance();
            if(caster.isRemoved()||caster.level()!=mc.level||caster!=mc.player||connection!=mc.getConnection()){stop();return;}
            x=caster.getX();y=caster.getY();z=caster.getZ();
        }
        void terminate(){stop();}
    }
}
