package cn.academy.port.client;

import cn.academy.port.HighPowerVisualConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import proof.Trace;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import static cn.academy.port.client.LandingArrayFallbackHarness.*;

/** Explicit CPU host proof: shared real submission, isolation and opt-in gates; no native pixels or combat claim. */
public final class LandingArrayPreviewHarness {
    static class Gui extends GuiGraphics {
        final List<String> labels=new ArrayList<>();
        final com.mojang.blaze3d.vertex.PoseStack poses=new com.mojang.blaze3d.vertex.PoseStack();
        @Override public com.mojang.blaze3d.vertex.PoseStack pose(){return poses;}
        @Override public int guiWidth(){return 392;}
        @Override public void fill(int x,int y,int z,int a,int b){require(x>=0&&z<=guiWidth(),"FIXTURE_HUD_OUTSIDE_VIEWPORT");labels.add("background");}
        @Override public void drawString(Font f,Component s,int x,int y,int c,boolean shadow){labels.add(s.toString());}
    }
    static void renderUnchanged()throws Exception {
        String before=stateDump(),matrix=RenderSystem.STACK.value.toString(),pose=event.getPoseStack().matrix.toString();
        float[] color=RenderSystem.color.clone();int depth=RenderSystem.STACK.frames.size(),poseDepth=event.getPoseStack().frames.size();
        LandingArrayMaterialPreview.render(event);
        require(before.equals(stateDump()),"PREVIEW_CHANGED_PRODUCTION_STATE");
        require(matrix.equals(RenderSystem.STACK.value.toString())&&depth==RenderSystem.STACK.frames.size(),"PREVIEW_MODEL_VIEW_LEAK");
        require(pose.equals(event.getPoseStack().matrix.toString())&&poseDepth==event.getPoseStack().frames.size(),"PREVIEW_POSE_LEAK");
        require(Arrays.equals(color,RenderSystem.color),"PREVIEW_COLOR_LEAK");
    }
    @SuppressWarnings("unchecked") static List<LandingArrayMaterialPreview.Sample> samples()throws Exception {
        return (List<LandingArrayMaterialPreview.Sample>)field(LandingArrayMaterialPreview.class,"samples");
    }
    public static void main(String[] args)throws Exception {
        lane="ready-enabled";prepareMaterial(lane,Path.of(args[1]));reset(0,false);Trace.reset();
        boolean enabled=args[0].equals("enabled");require(LandingArrayMaterialPreview.allowed()==enabled,"PREVIEW_DOUBLE_GATE:"+args[0]);
        var gui=new Gui();renderUnchanged();List<String> first=List.copyOf(Trace.rows);
        LandingArrayMaterialPreview.hud(new RenderGuiEvent.Post(gui));
        require(gui.poses.frames.isEmpty()&&gui.poses.matrix.toString().equals(new Matrix4f().toString()),"PREVIEW_HUD_POSE_LEAK");
        if(!enabled){
            LandingArrayMaterialPreview.key(new InputEvent.Key(GLFW.GLFW_KEY_F8,GLFW.GLFW_PRESS));
            require(Trace.rows.isEmpty()&&gui.labels.isEmpty()&&samples().isEmpty(),"DEFAULT_PREVIEW_SIDE_EFFECT");
            System.out.println("{\"result\":\"PASS_DISABLED_NO_STATE_OR_RENDER\",\"lane\":\""+args[0]+"\"}");return;
        }
        require(Trace.vertices==288&&samples().size()==2&&cues().isEmpty()&&state().active()==null,"STAGED_PAIR_NOT_ISOLATED");
        require(gui.labels.stream().anyMatch(s->s.contains("STAGED FIXTURE"))
                &&gui.labels.stream().anyMatch(s->s.contains("非实战")&&s.contains("NO CAST OR DAMAGE")),"STAGED_LABEL_MISSING");
        List<LandingArrayMaterialPreview.Sample> frozen=samples();
        Trace.reset();renderUnchanged();require(first.equals(Trace.rows)&&frozen.equals(samples()),"PREVIEW_PHASE_OR_POINTS_MOVED");
        var enhanced=MultidimensionalLandingArrayEffects.class.getDeclaredMethod("enhancedCue",Matrix4f.class,
                cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point.class,boolean.class,int.class,Vec3.class,Vec3.class,float.class,int.class);
        enhanced.setAccessible(true);
        var classic=MultidimensionalLandingArrayEffects.class.getDeclaredMethod("classicCue",Matrix4f.class,
                cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point.class,int.class,Vec3.class,Vec3.class,int.class);
        classic.setAccessible(true);
        for(boolean optional:List.of(true,false)){
            HighPowerVisualConfig.LANDING_ARRAY_CUES.set(optional);Trace.reset();renderUnchanged();
            List<String> actual=Trace.rows.stream().filter(r->r.startsWith("buffer:")||r.startsWith("v:")||r.startsWith("normal:")).toList();
            int actualVertices=Trace.vertices;Trace.reset();
            for(var s:frozen)if(optional)enhanced.invoke(null,event.getPoseStack().last().pose(),s.at(),s.contact(),s.age(),camera,forward,s.partial(),2048);
                else classic.invoke(null,event.getPoseStack().last().pose(),s.at(),s.age(),camera,forward,MultidimensionalLandingArrayMesh.alpha(s.age()));
            require(Trace.vertices==actualVertices&&actual.equals(Trace.rows.stream().filter(r->r.startsWith("buffer:")||r.startsWith("v:")||r.startsWith("normal:")).toList()),"PREVIEW_DIFFERS_FROM_SHARED_SUBMISSION:"+optional);
            require(actualVertices==(optional?288:80),"PREVIEW_VERTEX_BOUND:"+actualVertices);
        }
        HighPowerVisualConfig.LANDING_ARRAY_CUES.set(true);
        MC.options.hideGui=true;Trace.reset();renderUnchanged();require(Trace.rows.isEmpty(),"UNLABELLED_HIDDEN_GUI_FIXTURE");MC.options.hideGui=false;
        MC.screen=new Object();Trace.reset();renderUnchanged();require(Trace.rows.isEmpty(),"MENU_FIXTURE");MC.screen=null;
        MC.player.alive=false;Trace.reset();renderUnchanged();require(Trace.rows.isEmpty(),"DEAD_PLAYER_FIXTURE");MC.player.alive=true;
        LandingArrayMaterialPreview.key(new InputEvent.Key(GLFW.GLFW_KEY_F9,GLFW.GLFW_PRESS));Trace.reset();renderUnchanged();require(Trace.rows.isEmpty()&&samples().isEmpty(),"HIDDEN_FIXTURE_RESIDUE");
        LandingArrayMaterialPreview.key(new InputEvent.Key(GLFW.GLFW_KEY_F9,GLFW.GLFW_PRESS));Trace.reset();renderUnchanged();require(Trace.vertices==288,"PREVIEW_SHOW_FAILED");
        LandingArrayMaterialPreview.key(new InputEvent.Key(GLFW.GLFW_KEY_F8,GLFW.GLFW_PRESS));require(samples().isEmpty(),"REFRAME_DID_NOT_CLEAR");
        prepare(3);Trace.reset();renderUnchanged();var liveGui=new Gui();LandingArrayMaterialPreview.hud(new RenderGuiEvent.Post(liveGui));
        require(Trace.rows.isEmpty()&&cues().size()==1&&liveGui.labels.isEmpty(),"FIXTURE_OR_STAGED_HUD_MIXED_WITH_AUTHENTICATED_CUE");
        System.out.println("{\"result\":\"PASS_SHARED_SUBMISSION_AND_ISOLATION\",\"optional_pair_vertices\":288,\"classic_pair_vertices\":80,\"fixed_phase\":3.5,\"real_cues_created\":0,\"limits\":\"CPU hosts only; no native GPU pixels or gameplay acceptance\"}");
    }
}
