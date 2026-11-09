package cn.academy.port.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Pure screen projection/budget checks plus explicit lifecycle wiring boundaries. */
public final class MagneticItemPullSelectionRegressionTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static UUID id(int value){return new UUID(0,value);}
    public static void main(String[] args)throws Exception{
        var a=MagneticItemPullSelectionGeometry.rectangle(1000,600,99999,99999);
        check(a.left()==500&&a.top()==300&&a.right()==756&&a.bottom()==480,"large-window drag bound is256x180");
        var b=MagneticItemPullSelectionGeometry.rectangle(200,100,-999,-999);
        check(b.left()==10&&b.top()==5&&b.right()==100&&b.bottom()==50,"small-window rectangle clamps inside five-percent margin");
        for(int width:List.of(200,427,640,960,1920))for(int height:List.of(100,240,360,540,1080))for(double x:List.of(-100.0,0.0,1e6))for(double y:List.of(-100.0,0.0,1e6)){
            var rect=MagneticItemPullSelectionGeometry.rectangle(width,height,x,y);
            check(rect.left()>=0&&rect.right()<=width&&rect.top()>=0&&rect.bottom()<=height,"all UI scales keep rectangle in viewport");
        }
        check(!MagneticItemPullSelectionGeometry.rectangle(640,360,320,180).usable(),"zero-area click never invents a selection");
        check(!MagneticItemPullSelectionGeometry.rectangle(640,360,Double.NaN,180).usable(),"invalid cursor rejected");
        check(!MagneticItemPullSelectionGeometry.rectangle(0,360,0,0).usable(),"invalid viewport rejected");
        var center=MagneticItemPullSelectionGeometry.project(id(1),0,0,0,1,4,640,360);
        check(center!=null&&center.x()==320&&center.y()==180,"camera clip origin projects to screen center");
        var topRight=MagneticItemPullSelectionGeometry.project(id(2),1,1,0,1,24*24,640,360);
        check(topRight!=null&&topRight.x()==640&&topRight.y()==0,"clip Y inverts for GUI and exact24m range accepted");
        for(double w:List.of(-1.0,0.0,Double.NaN))check(MagneticItemPullSelectionGeometry.project(id(1),0,0,0,w,4,640,360)==null,"behind-camera/invalid W rejected");
        check(MagneticItemPullSelectionGeometry.project(id(1),0,0,1.01,1,4,640,360)==null,"far-plane point rejected");
        check(MagneticItemPullSelectionGeometry.project(id(1),0,0,-1.01,1,4,640,360)==null,"near-plane point rejected");
        check(MagneticItemPullSelectionGeometry.project(id(1),1.01,0,0,1,4,640,360)==null,"offscreen point rejected");
        check(MagneticItemPullSelectionGeometry.project(id(1),0,0,0,1,Math.pow(MagneticItemPullSelectionGeometry.RANGE, 2)+.01,640,360)==null,"overrange candidate rejected");
        var perspective=new Matrix4f().perspective((float)Math.toRadians(70),640f/360f,.05f,100f);
        var sideView=new Matrix4f().rotationY((float)Math.PI/2);
        var correct=MagneticItemPullSelectionGeometry.viewProjection(perspective,sideView,new Matrix4f()).transform(new Vector4f(10,0,0,1));
        var rotatedCenter=MagneticItemPullSelectionGeometry.project(id(40),correct.x,correct.y,correct.z,correct.w,100,640,360);
        check(rotatedCenter!=null&&Math.abs(rotatedCenter.x()-320)<.001&&Math.abs(rotatedCenter.y()-180)<.001,"actual side-facing camera maps visible world+X item to crosshair");
        var omittedView=new Matrix4f(perspective).transform(new Vector4f(10,0,0,1));
        check(MagneticItemPullSelectionGeometry.project(id(40),omittedView.x,omittedView.y,omittedView.z,omittedView.w,100,640,360)==null,"regression reproduces missing-model-view empty rectangle bug");
        for(int yaw:List.of(0,45,90,135,180,225,270,315))for(int pitch:List.of(-60,-30,0,30,60)){
            var view=new Matrix4f().rotateX((float)Math.toRadians(pitch)).rotateY((float)Math.toRadians(yaw));
            var relative=new Matrix4f(view).invert().transform(new Vector4f(0,0,-10,1));
            var clip=MagneticItemPullSelectionGeometry.viewProjection(perspective,view,new Matrix4f()).transform(relative);
            var projected=MagneticItemPullSelectionGeometry.project(id(41),clip.x,clip.y,clip.z,clip.w,100,640,360);
            check(projected!=null&&Math.abs(projected.x()-320)<.001&&Math.abs(projected.y()-180)<.001,"yaw/pitch camera orientation is included "+yaw+"/"+pitch);
        }
        var originalProjection=new Matrix4f(perspective);var originalView=new Matrix4f(sideView);var localPose=new Matrix4f().translation(.1f,0,0);
        MagneticItemPullSelectionGeometry.viewProjection(perspective,sideView,localPose);
        check(perspective.equals(originalProjection)&&sideView.equals(originalView),"projection composition never mutates renderer matrices");
        var rect=MagneticItemPullSelectionGeometry.rectangle(640,360,500,300);
        var points=new ArrayList<MagneticItemPullSelectionGeometry.Point>();
        for(int i=25;i>=1;i--)points.add(new MagneticItemPullSelectionGeometry.Point(id(i),350,200,i));
        points.add(new MagneticItemPullSelectionGeometry.Point(id(1),350,200,1));
        points.add(new MagneticItemPullSelectionGeometry.Point(id(30),10,10,0));
        points.add(new MagneticItemPullSelectionGeometry.Point(id(31),350,200,Double.NaN));
        var selected=MagneticItemPullSelectionGeometry.candidates(rect,points);
        check(selected.size()==16&&new HashSet<>(selected).size()==16,"max16 deduplicated UUIDs despite dense piles");
        check(selected.getFirst().equals(id(1))&&selected.getLast().equals(id(16)),"nearest projected candidates prioritized deterministically");
        check(MagneticItemPullSelectionGeometry.candidates(new MagneticItemPullSelectionGeometry.Rect(0,0,0,0),points).isEmpty(),"click without drag selects nothing");
        check(MagneticItemPullSelectionGeometry.fresh(1250,1000)&&!MagneticItemPullSelectionGeometry.fresh(1251,1000),"stale render projections expire after250ms");
        check(!MagneticItemPullSelectionGeometry.fresh(999,1000)&&!MagneticItemPullSelectionGeometry.fresh(1000,0),"time reversal and never-rendered lists cannot commit");
        String selection=Files.readString(Path.of("src/main/java/cn/academy/port/client/MagneticItemPullSelection.java"));
        String client=Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String effects=Files.readString(Path.of("src/main/java/cn/academy/port/client/MagneticItemPullEffects.java"));
        check(selection.contains("resize(Minecraft mc,int width,int height) { cancel(); }")&&selection.contains("GLFW.GLFW_KEY_ESCAPE"),"resize/UI-scale change and Escape cancel rather than reanchor or commit");
        check(selection.contains("mc.isWindowActive()")&&selection.contains("mc.getConnection()==connection")&&selection.contains("sameInputSessionEpoch(session)")&&selection.contains("state.presets.revision()==revision"),"selector binds focus/world/connection/session/preset revision");
        check(selection.contains("event.getCamera().getPosition()")&&selection.contains("viewProjection(event.getProjectionMatrix(),event.getModelViewMatrix(),event.getPoseStack().last().pose())"),"projection uses actual camera for third-person as well as first-person");
        check(selection.contains("Boolean.getBoolean(\"academy.highpower.itemPullSelectionDiagnostics\")")&&selection.contains("screen.diagnosticFrames<8")&&selection.contains("samples.size()<4"),"diagnostics are opt-in and capped to eight frames/four samplepoints plus release");
        check(selection.contains("++examined>2048")&&selection.contains("++itemCount>256"),"preview entity traversal and raycasts have explicit per-frame budgets");
        check(selection.contains("mc.options.keyAttack,mc.options.keyUse,mc.options.keyPickItem")&&selection.contains("while(key.consumeClick()){}"),"cancel/close clears original vanilla interactions");
        check(client.contains("GLFW.glfwGetMouseButton")&&client.contains("InputConstants.isKeyDown")&&!client.contains("SLOTS[slot].isDown()"),"slot polling reads actual hardware, not setScreen's reset KeyMapping state");
        check(client.indexOf("request(\"hp_item_pull_begin\",identity.wire())")<client.indexOf("MagneticItemPullSelection.open(identity)"),"no-payment server begin precedes selector opening");
        check(client.contains("mc.screen!=null&&!MagneticItemPullSelection.ownScreen()"),"only this selector bypasses terminal-open abort");
        check(selection.contains("++active.age>=cn.academy.port.highpower.MagneticItemPull.SELECTION_TICKS")&&selection.contains("academy.highpower.item_pull.selection_expired"),"local ten-second lease expiry cancels once with honest no-payment feedback");
        check(selection.contains("isPauseScreen() { return false; }")&&!selection.contains("super.render("),"selector stays transparent and non-pausing");
        check(effects.contains("CANCELLED.contains(new Input(packet.epoch(),packet.input()))")&&effects.contains("world.getEntity(m.owner.getId())!=m.owner"),"visuals fence canceled input and replaced entity identity");
        check(effects.contains("MAX_CONTEXTS=64")&&effects.contains("++examined>2048||visibleItems.size()==wanted.size()")&&effects.contains("wanted.contains(item.getUUID())"),"line renderer scans at most2048 entities and retains only bounded server-approved UUIDs");
        check(effects.contains("item.getPosition(partial)")&&effects.contains("PolarityTetherGeometry.clip")&&!effects.contains("setDeltaMovement")&&!effects.contains("setPos("),"pale-blue lines follow actual items and never predict or teleport them");
        System.out.println("PASS "+checks+" NEW133 screen projection, selection budget and lifecycle checks");
    }
}
