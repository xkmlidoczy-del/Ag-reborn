package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.core.DissipationCurtainGeometry;
import cn.academy.port.highpower.core.DissipationCurtainGeometry.Pose;
import cn.academy.port.highpower.core.DissipationCurtainGeometry.Vector;
import cn.academy.port.client.DissipationCurtainState.Frame;
import cn.academy.port.client.DissipationCurtainState.Identity;
import static cn.academy.port.client.DissipationCurtainState.*;
import static cn.academy.port.client.FoldedMirrorMesh.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Engine-free NEW242 actual facet gaps, current-pose receipts, deferred held edges and finite render lifecycle. */
public final class DissipationCurtainVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER=new UUID(242,1);
    private static final Vector EYE=v(0,64,0),LOOK=v(0,0,1);
    private static final Identity ID=new Identity(8,OWNER,2,3,10);
    private static Vector v(double x,double y,double z) { return new Vector(x,y,z); }
    private static void check(boolean condition,String message) { checks++; if(!condition)throw new AssertionError(message); }
    private static Frame frame(long sequence,int phase) { return frame(ID,sequence,phase,EYE,LOOK,-1,EYE); }
    private static Frame frame(Identity id,long sequence,int phase,Vector center,Vector axis,int facet,Vector point) {
        return new Frame(id,sequence,phase,center,axis,phase==ACTIVE||phase==HIT?7:0,facet,point);
    }
    private static Frame hit(long sequence,int facet,Vector point) { return frame(ID,sequence,HIT,EYE,LOOK,facet,point); }
    private static Pose pose() { return DissipationCurtainGeometry.pose(EYE,LOOK); }
    public static void main(String[] args) throws Exception {
        receipts(); limits(); leases(); meshes(); contacts(); input(); nearClipAndBudget(); integration();
        System.out.println("PASS DissipationCurtainVisualRegressionTest: "+checks+" checks");
    }
    private static void receipts() {
        var state=new DissipationCurtainState(); var start=frame(1,STARTUP);
        check(valid(start) && state.accept(start,true),"six-tick warning is a receipt, not a live face");
        check(!state.accept(start,true),"duplicate startup sequence is fenced");
        check(state.accept(frame(2,ACTIVE),true),"native active receipt alone enables live presentation");
        check(!state.accept(frame(3,STARTUP),true),"one activation cannot regress to warning");
        Vector point=DissipationCurtainGeometry.facetCenter(pose(),1);
        check(state.accept(hit(4,1,point),true),"genuine front-plane absorption is accepted");
        check(!valid(hit(5,1,point.add(v(0,0,-.02)))),"rear/interior point cannot flash");
        check(!valid(hit(5,1,point.add(v(2,0,0)))),"a point beyond the rectangular facet cannot flash");
        check(!valid(hit(5,0,point)),"contact on a different facet is rejected");
        check(!valid(new Frame(ID,5,STARTUP,EYE,LOOK,7,-1,EYE)),"warning cannot claim live mask");
        check(!valid(new Frame(ID,5,ACTIVE,EYE,LOOK,7,1,point)),"active keepalive cannot invent a hit");
        check(state.accept(frame(6,ENDED),true) && state.size()==0,"terminal closes protection before cosmetic fade");
        check(!state.accept(frame(7,ACTIVE),true),"terminal receipt tombstone cannot be reopened");
        var newer=new Identity(8,OWNER,2,4,11);
        check(state.accept(frame(newer,1,STARTUP,EYE,LOOK,-1,EYE),true),"fresh authenticated held edge opens a new context");
        state.clear();check(!state.accept(frame(newer,2,ACTIVE,EYE,LOOK,-1,EYE),true),"same connection clear retains the replay fence");
        state.resetSession();check(state.accept(start,true),"new connection resets provenance");
        state.resetSession();state.cancel(2,3);check(!state.accept(start,true),"explicit local cancel rejects delayed start");
        check(!eligible(3,true,true) && eligible(4,true,true) && eligible(5,true,true) && !eligible(4,false,true),"level and parent skill gates are explicit");
    }
    private static void limits() {
        var state=new DissipationCurtainState();
        for(int i=1;i<=MAX_CONTEXTS;i++)check(state.accept(frame(new Identity(i,new UUID(242,i),2,3,10),1,ACTIVE,EYE,LOOK,-1,EYE),false),"bounded owners accepted");
        check(!state.accept(frame(new Identity(65,new UUID(242,65),2,3,10),1,ACTIVE,EYE,LOOK,-1,EYE),false),"65th context is refused");
        state.resetSession();
        for(int i=1;i<=MAX_WATERMARKS;i++) {var f=frame(new Identity(i,new UUID(242,i),2,3,10),1,ACTIVE,EYE,LOOK,-1,EYE);check(state.accept(f,false),"bounded fence accepted");state.retire(f.id());}
        check(state.watermarkCount()==MAX_WATERMARKS && !state.accept(frame(new Identity(2048,new UUID(242,2048),2,3,10),1,ACTIVE,EYE,LOOK,-1,EYE),false),"full fence map refuses owners rather than forgetting retired identities");
    }
    private static void leases() {
        var lease=new Lease();lease.received(frame(1,ACTIVE));
        for(int i=0;i<1200;i++) {if(i%4==0)lease.received(frame(i+2,ACTIVE));check(lease.tick(false,true),"held renewable heartbeat lease has no invented absolute timeout");}
        check(lease.live(),"renewed held mode remains active");
        lease.end();check(!lease.live() && lease.ending() && lease.fadeAge()==0,"cosmetic fade is not active");
        for(int i=0;i<40;i++)check(lease.tick(true,true) && lease.fadeAge()==0,"pause freezes only the cosmetic clock");
        for(int i=1;i<FADE_TICKS;i++)check(lease.tick(false,true),"short fade remains bounded");
        check(!lease.tick(false,true),"fade ends after four ticks");
        lease=new Lease();lease.received(frame(1,STARTUP));
        for(int i=0;i<QUIET_TICKS;i++)lease.tick(false,true);
        check(lease.ending() && !lease.live(),"lost heartbeat ends warning without predicting live protection");
        check(!lease.tick(true,false),"invalid world/owner is removed even while paused");
    }
    private static void meshes() {
        for(Vector direction:List.of(LOOK,v(0,1,0),v(0,-1,0),v(.6,.8,0),v(-.36,.48,.8))) {
            Pose pose=DissipationCurtainGeometry.pose(EYE,direction);
            for(int facet=0;facet<3;facet++) {
                var active=DissipationCurtainMesh.facet(pose,facet,false,-1,7);
                check(active.stream().filter(f->f.kind()==FILL).count()==1,"one flat original-artwork face per real facet");
                check(active.stream().filter(f->f.kind()==FILL).allMatch(f->f.texture()==SHIELD_TEXTURE && f.alpha()<255),"transparent original mdshield material remains scoped");
                for(var f:active)for(var p:f.vertices()) {
                    Vector point=v(p.x(),p.y(),p.z());
                    check(DissipationCurtainGeometry.contains(pose,facet,point),"body, inward thickness and rims stay inside exact server facet at full pitch");
                    Vector relative=point.subtract(EYE);
                    check(Math.hypot(relative.dot(pose.axis()),relative.dot(pose.right()))<=6.0000001,"no horizontal radial boundary protrudes beyond six metres");
                }
                var warning=DissipationCurtainMesh.facet(pose,facet,true,-1,0);
                var fade=DissipationCurtainMesh.facet(pose,facet,false,0,0);
                check(warning.size()==16 && warning.stream().allMatch(f->f.kind()==GHOST),"startup is four dashed real-plane outlines, with no filled blocker");
                check(fade.size()==16 && fade.stream().allMatch(f->f.kind()==GHOST && f.alpha()<warning.getFirst().alpha()),"ending differs from live protection and from brighter startup");
                check(DissipationCurtainMesh.facet(pose,facet,false,FADE_TICKS,0).isEmpty(),"expired cosmetic facet disappears");
            }
        }
        // Each chord occupies precisely 36 degrees; centers separated by 40 leave four real degrees open.
        for(int facet=0;facet<3;facet++) {
            var vertices=DissipationCurtainGeometry.vertices(pose(),facet);
            double a=Math.toDegrees(Math.atan2(vertices.get(0).x(),vertices.get(0).z()));
            double b=Math.toDegrees(Math.atan2(vertices.get(1).x(),vertices.get(1).z()));
            check(Math.abs(b-a-36)<1e-8,"each rectangle spans36degrees at the eye");
            if(facet<2) {var next=DissipationCurtainGeometry.vertices(pose(),facet+1).getFirst();double nextAngle=Math.toDegrees(Math.atan2(next.x(),next.z()));check(Math.abs(nextAngle-b-4)<1e-8,"adjacent body geometry preserves the four-degree gap");}
        }
        check(DissipationCurtainMesh.SPAWNED_PARTICLES==0,"hard zero particle entity budget");
    }
    private static void contacts() {
        var contacts=new Contacts();Vector point=DissipationCurtainGeometry.facetCenter(pose(),1).add(v(.2,.3,0));var hit=hit(3,1,point);
        contacts.received(frame(1,STARTUP));contacts.received(frame(2,ACTIVE));check(contacts.frame(1)==null,"startup/miss/keepalive cannot seed a ripple");
        contacts.received(hit);contacts.tick(false);contacts.received(hit);check(contacts.age(1)==1,"replayed contact cannot renew optical age");
        contacts.received(frame(4,ACTIVE));check(contacts.age(1)==1 && contacts.frame(1).point().equals(point),"keepalive neither moves nor restarts contact");
        var current=frame(4,ACTIVE);
        double firstRadius=0,lastRadius=0;
        for(int age=0;age<HIT_TICKS;age++) {
            var faces=DissipationCurtainMesh.ripple(current,hit,age,age);
            for(var f:faces)for(var p:f.vertices()) {
                Vector vertex=v(p.x(),p.y(),p.z());check(DissipationCurtainGeometry.contains(pose(),1,vertex),"expanding ripple clips to actual facet and never bridges a gap");
                if(age==0)firstRadius=Math.max(firstRadius,vertex.subtract(point).length());
                if(age==5)lastRadius=Math.max(lastRadius,vertex.subtract(point).length());
            }
        }
        check(firstRadius<.06 && lastRadius>1,"localized contact propagates outward toward edges");
        var moved=frame(ID,5,ACTIVE,EYE.add(v(0,0,2)),LOOK,-1,EYE.add(v(0,0,2)));
        check(DissipationCurtainMesh.ripple(moved,hit,1,1).isEmpty(),"owner moving away cannot leave a historical ghost plane or remote ripple");
        var rotated=frame(ID,6,ACTIVE,EYE,v(.6,0,.8),-1,EYE);
        check(DissipationCurtainMesh.ripple(rotated,hit,1,1).isEmpty(),"turning the held curtain cannot relocate an old contact");
        var sideStep=frame(ID,7,ACTIVE,EYE.add(v(.2,0,0)),LOOK,-1,EYE.add(v(.2,0,0)));
        for(var f:DissipationCurtainMesh.ripple(sideStep,hit,4,4))for(var p:f.vertices())
            check(DissipationCurtainGeometry.contains(DissipationCurtainGeometry.pose(sideStep.center(),sideStep.axis()),1,v(p.x(),p.y(),p.z())),"same-plane contact is reclipped to present visible bounds");
        check(DissipationCurtainMesh.ripple(frame(8,STARTUP),hit,1,1).isEmpty(),"warning never displays an absorption highlight");
        check(DissipationCurtainMesh.ripple(current,hit,HIT_TICKS,1).isEmpty(),"ripple expiry is finite");
        for(int i=1;i<HIT_TICKS;i++)contacts.tick(false);check(contacts.frame(1)==null,"contact lease expires independent of renewable body lease");
    }
    private static void input() {
        Object world=new Object(),player=new Object(),connection=new Object();
        var context=new DissipationCurtainInputState.DispatchContext(world,player,connection,1,0,2,"242");
        var state=new DissipationCurtainInputState();var input=new HighPowerInput.Input(0,1,8,2);
        state.begin(input,71,context);check(state.holding() && !state.permits(1,2),"physical press queues start but does not invent server acceptance");
        check(state.release(input)==null,"same-tick release is deferred behind its unsent start");
        var dispatch=state.flushQueuedStart();check(dispatch.start().equals(input) && dispatch.release().equals(input),"Post preserves start-before-release ordering");
        check(state.deferRelease(dispatch.release(),context),"release waits for native current look packets");
        check(!state.deferRelease(dispatch.release(),context),"one release occupies one slot");
        check(state.flushDeferredRelease(context).equals(input) && state.flushDeferredRelease(context)==null,"release is consumed once after native Post");
        state.accepted(1,2);check(state.permits(1,2),"receipt matches dispatched input only");state.ended(1,2);check(!state.permits(1,2),"server terminal retires held permission");
        var second=new HighPowerInput.Input(0,2,8,2);state.begin(second,71,context);state.flushQueuedStart();state.release(second);state.deferRelease(second,context);
        var changed=new DissipationCurtainInputState.DispatchContext(new Object(),player,connection,1,0,2,"242");
        check(state.flushDeferredRelease(changed)==null && state.flushDeferredRelease(context)==null,"world replacement consumes and discards stale deferred release");
        var third=new HighPowerInput.Input(0,3,8,2);state.begin(third,71,context);
        check(!state.queuedFor(changed) && state.queuedFor(context),"queued start retains exact dispatch provenance");
        check(state.cancelWhenBlocked(true).equals(third) && state.flushQueuedStart()==null && !state.holding(),"focus/preset/menu cancellation clears all edges");
    }
    private static void nearClipAndBudget() {
        var faces=new ArrayList<Face>();
        for(int i=0;i<12;i++)for(int facet=0;facet<3;facet++)faces.addAll(DissipationCurtainMesh.facet(pose(),facet,false,-1,0));
        var eye=DissipationCurtainMesh.materialPoint(EYE);var look=DissipationCurtainMesh.materialPoint(LOOK);
        var budget=new DissipationCurtainMesh.Budget();var result=DissipationCurtainMesh.ordered(faces,eye,look,budget);
        check(result.size()==DissipationCurtainMesh.MAX_MESH_QUADS && budget.used()==result.size(),"shared global post-clip budget caps every owner and material");
        check(DissipationCurtainMesh.MAX_VISIBLE_CONTEXTS==4,"at most four owners reach mesh generation");
        int previous=-1;for(var f:result) {check(layer(f)>=previous,"depth-tested layers preserve transparent fill before contact accents");previous=layer(f);}
        var closeEye=DissipationCurtainMesh.materialPoint(DissipationCurtainGeometry.facetCenter(pose(),1).add(v(0,0,-.01)));
        var sideLook=DissipationCurtainMesh.materialPoint(v(.6,0,.8));int clippedParts=0;
        for(var f:DissipationCurtainMesh.facet(pose(),1,false,-1,0))for(var clipped:FoldedMirrorMesh.nearClip(f,closeEye,sideLook)) {
            clippedParts++;
            for(var vertex:clipped.vertices()) {
                check(vertex.subtract(closeEye).dot(sideLook)>=NEAR_DEPTH-1e-8,"near camera cannot expand a plane or draw a camera wedge");
                check(DissipationCurtainGeometry.contains(pose(),1,v(vertex.x(),vertex.y(),vertex.z())),"near clipping preserves the real world slab");
            }
        }
        check(clippedParts>0,"grazing camera exercises actual near-plane polygon clipping");
    }
    private static void integration() throws Exception {
        String effect=Files.readString(Path.of("src/main/java/cn/academy/port/client/DissipationCurtainEffects.java"));
        String render=Files.readString(Path.of("src/main/java/cn/academy/port/client/FoldedMirrorRenderTypes.java"));
        check(effect.contains("FoldedMirrorMaterial.set(face)") && effect.contains("texture(\"effects/mdshield\")"),"only scoped original shield/material reuse is wired");
        check(render.contains("LEQUAL_DEPTH_TEST") && render.contains("COLOR_WRITE"),"reuse retains world depth occlusion without opaque depth writes");
        check(effect.contains("marker.frame,contact,marker.contacts.age") && effect.contains("next.phase() != DissipationCurtainState.HIT"),"historical receipts do not move the current body and are checked against present facets");
        check(effect.contains("capturedGeneration") && effect.contains("sameInputSessionEpoch") && effect.contains("hasChunkAt"),"queued packets and meshes remain bound to actual connection/world and loaded chunks");
        check(!effect.contains("addParticle(") && !effect.contains("setBlock(") && !effect.contains("RenderGuiEvent"),"no particle platform, terrain mutation or competing HUD panel");
    }
}
