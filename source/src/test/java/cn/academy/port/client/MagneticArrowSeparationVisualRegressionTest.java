package cn.academy.port.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Executes bounded geometry and the exact receiver gate, plus client controls/authority boundaries. */
public final class MagneticArrowSeparationVisualRegressionTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static UUID id(int value){return new UUID(0,value);}
    private static MagneticArrowSeparationState.Frame frame(int owner,long epoch,long input,long token,long sequence,int phase,List<UUID> targets){return new MagneticArrowSeparationState.Frame(new MagneticArrowSeparationState.Identity(id(owner),epoch,input,token),sequence,phase,targets);}
    public static void main(String[] args)throws Exception{
        var state=new MagneticArrowSeparationState();var armed=frame(1,4,20,5,1,0,List.of());
        check(state.accept(armed,true),"empty unpaid field is a valid visual without fake targets");
        check(!state.accept(armed,true),"duplicate armed packet ignored");
        check(!state.accept(frame(1,4,20,5,0,0,List.of()),true),"nonpositive sequence invalid");
        check(state.accept(frame(1,4,20,5,2,1,List.of(id(10))),true),"real target advances one owned field");
        check(!state.accept(frame(1,4,20,5,3,0,List.of()),true),"late armed phase cannot regress steering");
        check(!state.accept(frame(1,4,21,5,3,1,List.of(id(10))),true),"same token cannot acquire another input");
        check(!state.accept(frame(1,3,20,5,3,1,List.of(id(10))),true),"same token cannot acquire another epoch");
        check(!state.accept(frame(1,4,20,5,3,1,List.of(id(10),id(10))),true),"duplicate arrow UUIDs rejected");
        check(!state.accept(frame(1,4,20,5,3,1,List.of(id(10),id(11),id(12),id(13),id(14))),true),"fifth arrow never enters renderer");
        check(!state.accept(frame(1,4,20,5,3,0,List.of(id(10))),true),"unpaid armed state cannot imply a hit");
        check(state.accept(frame(1,4,20,5,4,2,List.of()),true)&&state.size()==0,"end removes field immediately");
        check(!state.accept(frame(1,4,20,5,100,1,List.of(id(10))),true),"late update after end cannot resurrect field");
        state.cancel(4,21);
        check(!state.accept(frame(1,4,21,6,1,0,List.of()),true),"abort before first packet is fenced");
        check(state.accept(frame(1,4,22,7,1,0,List.of()),true),"new input after cancel is independent");
        state.retire(state.current(id(1)).id());
        check(!state.accept(frame(1,4,22,7,50,1,List.of(id(10))),true),"quiet/expiry retirement rejects replay");
        state.clear();check(state.size()==0&&state.accept(armed,true),"world reset owns a fresh cache");
        for(int i=2;i<=64;i++)check(state.accept(frame(i,1,i,10+i,1,0,List.of()),false),"bounded owner admitted");
        check(state.size()==64&&!state.accept(frame(65,1,65,99,1,0,List.of()),false),"owner cap is64");
        var origin=new MagneticArrowSeparationGeometry.Point(5,7,9);
        for(var axis:List.of(new MagneticArrowSeparationGeometry.Point(1,0,0),new MagneticArrowSeparationGeometry.Point(0,1,0),new MagneticArrowSeparationGeometry.Point(0,-1,0),new MagneticArrowSeparationGeometry.Point(0,0,-1),new MagneticArrowSeparationGeometry.Point(1,2,3))){
            var unit=axis.scale(1/axis.length());
            for(double range:List.of(.5,3.0,12.0,24.0)){
                var lines=MagneticArrowSeparationGeometry.corridor(origin,axis,range);
                check(lines.size()==54,"3 rings,4 rails and far-cap cross have fixed54-segment budget");
                for(var line:lines)for(var p:List.of(line.from(),line.to())){
                    var r=p.minus(origin);double along=r.dot(unit);double radial=r.minus(unit.scale(along)).length();
                    check(p.finite()&&along>=-1e-8&&along<=range+1e-8&&radial<=2+1e-8,"full3D server-shortened corridor stays finite and radius2");
                }
            }
        }
        check(MagneticArrowSeparationGeometry.corridor(origin,new MagneticArrowSeparationGeometry.Point(0,0,0)).isEmpty(),"zero axis rejected");
        check(MagneticArrowSeparationGeometry.corridor(origin,new MagneticArrowSeparationGeometry.Point(0,0,1),24.1).isEmpty(),"excess range rejected");
        check(MagneticArrowSeparationGeometry.corridor(origin,new MagneticArrowSeparationGeometry.Point(0,0,1),Double.NaN).isEmpty(),"nonfinite range rejected");
        var arrow=origin.plus(new MagneticArrowSeparationGeometry.Point(1,0,8));
        var curve=MagneticArrowSeparationGeometry.outward(arrow,origin,new MagneticArrowSeparationGeometry.Point(0,0,1));
        check(curve.size()==6&&curve.getFirst().from().equals(arrow),"outward guide begins at actual arrow position with6 segments");
        for(var segment:curve)check(segment.to().minus(arrow).length()<=1.5&&segment.to().x()>segment.from().x(),"guide curves outward without long predictive trail");
        check(MagneticArrowSeparationGeometry.outward(origin,origin,new MagneticArrowSeparationGeometry.Point(0,0,1)).isEmpty(),"undefined radial direction invents no curve");
        var perspective=new Matrix4f().perspective((float)Math.toRadians(70),640f/360f,.05f,100f);
        for(int yaw:List.of(0,45,90,135,180,225,270,315))for(int pitch:List.of(-85,-60,0,60,85)){
            var view=new Matrix4f().rotateX((float)Math.toRadians(pitch)).rotateY((float)Math.toRadians(yaw));
            var relative=new Matrix4f(view).invert().transform(new Vector4f(0,0,-12,1));
            var clip=MagneticItemPullSelectionGeometry.viewProjection(perspective,view,new Matrix4f()).transform(relative);
            check(Math.abs(clip.x)<.0001&&Math.abs(clip.y)<.0001&&clip.w>0,"model-view keeps corridor centered across yaw/pitch");
        }
        String client=Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String selection=Files.readString(Path.of("src/main/java/cn/academy/port/client/MagneticItemPullSelection.java"));
        String effects=Files.readString(Path.of("src/main/java/cn/academy/port/client/MagneticArrowSeparationEffects.java"));
        check(client.indexOf("boolean arrows=down(Minecraft.getInstance().options.keyShift)")<client.indexOf("MagneticItemPullSelection.openArrows(identity)"),"configured hardware crouch captured before logical setScreen reset");
        check(client.contains("request(\"hp_item_separate_begin\",identity.wire())")&&client.contains("request(\"hp_item_separate_release\",identity.wire())"),"second mode uses the same authenticated slot input");
        check(selection.contains("final boolean arrows;")&&selection.contains("if(screen==null||screen.arrows||!screen.input.equals(input))return null;"),"mode immutable and arrow release cannot submit item UUID proposals");
        check(client.contains("new cn.academy.port.highpower.MagneticItemPull.CandidateRequest(identity.wire(),candidates)")&&selection.contains("screen.candidates()"),"first-mode item rectangle and candidate request preserved");
        check(selection.contains("!screen.arrowPreviewShown||!MagneticArrowSeparationEffects.previewReady(input)")&&selection.contains("arrow_waiting")&&selection.contains("arrow_mode"),"mode shown immediately and release requires a fresh actually rendered server preview");
        check(effects.contains("packet.expiresAt()<=captured.getGameTime()")&&effects.contains("MagneticItemPullSelection.previewingArrows(packet.input(),packet.epoch())")&&effects.contains("if(preview!=null)return;"),"late/expired preview ignored and repeated packet cannot reanchor");
        check(effects.contains("mc.level!=captured")&&effects.contains("mc.getConnection()!=capturedConnection")&&effects.contains("sameInputSessionEpoch(capturedSession)")&&effects.contains("world.getEntity(m.owner.getId())!=m.owner"),"queued packets and render ownership fence world connection session and entity replacement");
        check(effects.contains("m.quiet>=QUIET_TICKS")&&effects.contains("m.pulseAge>=PULSE_TICKS+QUIET_TICKS")&&effects.contains("STATE.retire(m.identity())"),"quiet and finite total lifetime retire instead of allowing late restart");
        check(effects.contains("MAX_VISIBLE_CONTEXTS=8")&&effects.contains("++examined>2048")&&effects.contains("wanted.contains(arrow.getUUID())"),"renderer caps contexts and entity traversal");
        check(effects.contains("arrow.getPosition(partial)")&&!effects.contains("addParticle")&&!effects.contains("setDeltaMovement")&&!effects.contains("setPos("),"outward guides follow real arrows only and never imply invented hits or move anything");
        check(effects.contains("viewProjection(event.getProjectionMatrix(),event.getModelViewMatrix(),matrix)")&&effects.contains("PolarityTetherGeometry.clip"),"actual camera model-view and near-plane clip retained");
        check(selection.contains("mc.options.keyAttack,mc.options.keyUse,mc.options.keyPickItem")&&selection.contains("while(key.consumeClick()){}"),"both modes clear vanilla attack use and pick on close");
        check(client.contains("MagneticArrowSeparationEffects.abortLocal(input.input(),input.epoch())")&&client.contains("MagneticArrowSeparationEffects.clear()"),"shared abort and session teardown clear second-mode state");
        System.out.println("PASS "+checks+" NEW133 arrow visual lifecycle, geometry, preview and controls checks");
    }
}
