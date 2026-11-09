package cn.academy.port.client;

import cn.academy.port.HighPowerVisualConfig;
import cn.academy.port.highpower.RailThunderArray;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import proof.Trace;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Successful optional renderer integration, separate from the immutable frozen CPU corpus. */
public final class RailThunderArrayEnabledHarness {
    private static final Minecraft MC=Minecraft.getInstance();
    private static final List<String> CHECKS=new ArrayList<>();
    private static BufferedWriter capture,ledger;
    private static int snapshots,optionalVertices,combinedVertices,requiredQuads;
    private record Snapshot(List<String> trace,List<String> submissions,List<String> vertices,String state) {}
    private static void check(boolean valid,String name){if(!valid)throw new AssertionError(name);CHECKS.add(name);}
    private static Object field(Object subject,String name)throws Exception{return RailThunderArrayFallbackHarness.field(subject,name);}
    private static Map<?,?> markers()throws Exception{return RailThunderArrayFallbackHarness.markers();}
    private static void reset(){
        MC.defer=false;MC.queued.clear();AcademyClient.session=1;
        RailThunderArrayFallbackHarness.reset(0,2);
        RailThunderArrayFallbackHarness.origin=new Vec3(2,1,4);
        RailThunderArrayFallbackHarness.to=new Vec3(6,1,20);
        HighPowerVisualConfig.RAIL_ARRAY_GRAND.set(true);
    }
    private static void receive(int phase,int status,int serverAge){RailThunderArrayEffects.receive(RailThunderArrayFallbackHarness.packet(phase,status,1,serverAge,0));}
    private static void tick(int amount){for(int i=0;i<amount;i++)RailThunderArrayFallbackHarness.tick();}
    private static Snapshot record(String name)throws Exception {
        List<String> rows=List.copyOf(Trace.rows);
        List<String> submitted=rows.stream().filter(s->s.startsWith("buffer:")||s.startsWith("v:")).toList();
        List<String> vertices=rows.stream().filter(s->s.startsWith("v:")).toList();
        String state=RailThunderArrayFallbackHarness.state();
        capture.write("CASE "+name+"\n"+String.join("\n",rows)+"\n"+state+"\nEND\n");
        ledger.write(name+"\t"+vertices.size()+"\t"+RailThunderArrayFallbackHarness.sha(String.join("\n",rows))+"\t"+RailThunderArrayFallbackHarness.sha(state)+"\n");
        snapshots++;return new Snapshot(rows,submitted,vertices,state);
    }
    private static Snapshot render(String name)throws Exception {
        Trace.reset();String matrix=RenderSystem.STACK.value.toString();int depth=RenderSystem.STACK.frames.size();
        RailThunderArrayEffects.render(RailThunderArrayFallbackHarness.event);
        check(matrix.equals(RenderSystem.STACK.value.toString())&&depth==RenderSystem.STACK.frames.size(),"matrix-restored/"+name);
        check(Trace.vertices%4==0,"complete-quads/"+name);
        return record(name);
    }
    private static Object onlyMarker()throws Exception{check(markers().size()==1,"one-marker");return markers().values().iterator().next();}
    private static List<?> flashes()throws Exception{return (List<?>)field(onlyMarker(),"flashes");}
    private static int flashAge()throws Exception{check(flashes().size()==1,"one-confirmed-flash");return (int)field(flashes().getFirst(),"age");}
    private static boolean soft(Snapshot s){return s.submissions.stream().anyMatch(x->x.contains("textures/effects/glow_line.png"));}

    private static void statusAuthority()throws Exception {
        for(int phase:List.of(RailThunderArrayState.TAP,RailThunderArrayState.HOLD)){
            List<Snapshot> values=new ArrayList<>();
            for(int status:List.of(RailThunderArrayState.MISS,RailThunderArrayState.BLOCKED,RailThunderArrayState.HIT)){
                reset();receive(phase,status,0);tick(2);Snapshot s=render("enabled/status/p"+phase+"/s"+status);
                check(soft(s)&&!s.vertices.isEmpty(),"successful-optional/p"+phase+"/s"+status);
                check(s.submissions.stream().filter(x->x.startsWith("buffer:")&&x.contains("academy_rail_array_soft")).allMatch(x->x.contains("textures/effects/glow_line.png")&&x.contains("shader=academy:rail_array_soft")),"soft-material-limited-to-glow/p"+phase+"/s"+status);values.add(s);
            }
            Snapshot miss=values.get(0),blocked=values.get(1),hit=values.get(2);
            check(miss.submissions.equals(blocked.submissions),"blocked-does-not-manufacture-hit/p"+phase);
            check(hit.vertices.size()==miss.vertices.size()+8,"hit-only-two-contact-quads/p"+phase);
            check(hit.vertices.subList(0,miss.vertices.size()).equals(miss.vertices),"hit-retains-complete-base-path/p"+phase);
            MC.level.entities.get(12).alive=false;Snapshot removed=render("enabled/hit-target-gone/p"+phase);
            check(hit.submissions.equals(removed.submissions),"confirmed-hit-survives-target-disappearance/p"+phase);
        }
    }
    private static void clocksAndHeartbeat()throws Exception {
        for(int phase:List.of(RailThunderArrayState.TAP,RailThunderArrayState.HOLD)){
            reset();receive(phase,2,0);Snapshot fresh=render("enabled/shot-age-zero/p"+phase);
            reset();receive(phase,2,200);check(flashAge()==0,"cast-age-200-flash-age-zero/p"+phase);
            Snapshot late=render("enabled/shot-cast-age-200/p"+phase);
            check(fresh.submissions.equals(late.submissions),"shot-envelope-independent-of-cast-age/p"+phase);
            tick(3);Snapshot before=render("enabled/before-empty-heartbeat/p"+phase);int age=flashAge();
            var p=RailThunderArrayFallbackHarness.packet(phase,2,2,204,0);
            RailThunderArrayEffects.receive(new RailThunderArray.Visual(p.entity(),p.owner(),p.input(),p.token(),p.epoch(),p.sequence(),p.phase(),p.age(),p.origin(),p.axes(),List.of(),p.reason()));
            Snapshot after=render("enabled/after-empty-heartbeat/p"+phase);
            check(flashAge()==age&&before.submissions.equals(after.submissions),"empty-heartbeat-preserves-envelope/p"+phase);
            tick(2);check(render("enabled/heartbeat-expiry/p"+phase).vertices.isEmpty()&&flashes().isEmpty(),"empty-heartbeat-does-not-refresh/p"+phase);
            reset();receive(phase,2,200);MC.paused=true;Snapshot paused=render("enabled/paused-start/p"+phase);String state=paused.state;
            tick(40);Snapshot still=render("enabled/paused-forty-ticks/p"+phase);
            check(state.equals(still.state)&&paused.submissions.equals(still.submissions)&&flashAge()==0,"pause-freezes-lease-and-flash/p"+phase);
            MC.paused=false;tick(1);check(flashAge()==1,"resume-advances-flash-once/p"+phase);
        }
        reset();receive(0,0,20);Snapshot first=render("enabled/preparation-clock-initial");tick(3);Snapshot elapsed=render("enabled/preparation-clock-elapsed");
        check(!first.submissions.equals(elapsed.submissions),"preparation-advances-between-packets");
        RailThunderArrayEffects.receive(RailThunderArrayFallbackHarness.packet(0,0,2,23,0));Snapshot heartbeat=render("enabled/preparation-clock-heartbeat");
        check(elapsed.submissions.equals(heartbeat.submissions),"preparation-heartbeat-does-not-jump-clock");
    }
    private static void queuedCallbacks()throws Exception {
        for(String boundary:List.of("clear","session","end")){
            reset();MC.defer=true;receive(3,2,0);check(MC.queued.size()==1&&markers().isEmpty(),"callback-is-actually-queued/"+boundary);
            if(boundary.equals("clear"))RailThunderArrayEffects.clear();
            else if(boundary.equals("session"))AcademyClient.session++;
            else {MC.defer=false;RailThunderArrayEffects.receive(RailThunderArrayFallbackHarness.packet(4,0,2,1,0));}
            MC.drain();MC.defer=false;
            check(markers().isEmpty()&&render("enabled/queued-after-"+boundary).vertices.isEmpty(),"stale-queued-callback-dropped/"+boundary);
        }
        reset();receive(2,2,0);MC.defer=true;RailThunderArrayEffects.receive(RailThunderArrayFallbackHarness.packet(2,2,2,1,0));MC.defer=false;
        RailThunderArrayEffects.receive(RailThunderArrayFallbackHarness.packet(4,0,3,2,0));Snapshot ended=render("enabled/ended-before-drain");MC.drain();Snapshot drained=render("enabled/ended-after-drain");
        check(ended.state.equals(drained.state)&&ended.submissions.equals(drained.submissions)&&flashes().size()==1,"queued-shot-cannot-revive-ended-cast");
        tick(5);check(render("enabled/ended-expired").vertices.isEmpty(),"ended-confirmed-flash-still-expires");
    }
    private static void heavyScene(){RailThunderArrayFallbackHarness.origin=new Vec3(-4,0,12);RailThunderArrayFallbackHarness.to=new Vec3(4,0,12);}
    private static void remote(int index,int phase,int segments)throws Exception {
        Entity remote=new Entity(100+index,new UUID(17,index+1));MC.level.entities.put(remote.id,remote);
        var packet=new ArrayList<RailThunderArray.Segment>();
        for(int i=0;i<segments;i++)packet.add(new RailThunderArray.Segment(0,12,RailThunderArrayFallbackHarness.TARGET,RailThunderArrayFallbackHarness.origin,RailThunderArrayFallbackHarness.to,RailThunderArrayState.HIT));
        RailThunderArrayEffects.receive(new RailThunderArray.Visual(remote.id,remote.uuid,1,200+index,1,1,phase,0,RailThunderArrayFallbackHarness.origin,RailThunderArrayFallbackHarness.AXES,packet,0));
    }
    private static void controllerBudget()throws Exception {
        List<String> expected=new ArrayList<>();optionalVertices=0;
        for(int index=0;index<5;index++){
            reset();heavyScene();remote(index,3,16);tick(2);HighPowerVisualConfig.RAIL_ARRAY_GRAND.set(index<4);
            Snapshot single=render("enabled/controller-budget/single-"+index);
            if(index<4){check(soft(single),"heavy-controller-uses-optional/"+index);check(single.vertices.size()<=1024,"per-controller-1024-vertices/"+index);optionalVertices+=single.vertices.size();}
            else check(!soft(single),"fifth-controller-classic-reference");
            expected.addAll(single.submissions);
        }
        reset();heavyScene();for(int i=0;i<5;i++)remote(i,3,16);tick(2);Snapshot combined=render("enabled/controller-budget/five-together");combinedVertices=combined.vertices.size();
        check(combined.submissions.equals(expected),"five-controllers-equal-four-optional-plus-one-complete-classic");
        check(optionalVertices>2000&&optionalVertices<=4096,"combined-optional-global-4096-vertex-budget");
        reset();heavyScene();remote(0,2,16);tick(2);Snapshot overflow=render("enabled/oversize-controller-on");HighPowerVisualConfig.RAIL_ARRAY_GRAND.set(false);Snapshot classic=render("enabled/oversize-controller-off");
        check(overflow.submissions.equals(classic.submissions)&&!soft(overflow),"oversize-controller-complete-classic-fallback");
    }
    private static Object invokeMarker(String name,Object marker,int[] budget)throws Exception {
        Method method=Arrays.stream(RailThunderArrayEffects.class.getDeclaredMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow();method.setAccessible(true);
        var h=RailThunderArrayFallbackHarness.event;var l=h.getCamera().getLookVector();var u=h.getCamera().getUpVector();
        Object[] common={marker,h.getPoseStack().last().pose(),MC,h.getCamera().getPosition(),new Vec3(l.x(),l.y(),l.z()),new Vec3(u.x(),u.y(),u.z())};
        if(name.equals("originalMarker"))return method.invoke(null,common);
        Object[] expanded=Arrays.copyOf(common,8);expanded[6]=.375;expanded[7]=budget;return method.invoke(null,expanded);
    }
    private static void flush()throws Exception{((net.minecraft.client.renderer.MultiBufferSource.BufferSource)field(RailThunderArrayEffects.class,"buffers")).endBatch();}
    private static void insufficientBudgetMethod()throws Exception {
        reset();heavyScene();remote(0,3,16);tick(2);Snapshot event=render("enabled/budget-method/event-reference");Object marker=onlyMarker();
        Trace.reset();int[] ample={1024};boolean admitted=(boolean)invokeMarker("grandMarker",marker,ample);Snapshot planned=record("enabled/budget-method/ample");flush();requiredQuads=planned.vertices.size()/4;
        check(admitted&&requiredQuads>0&&ample[0]==1024-requiredQuads&&planned.submissions.equals(event.submissions),"real-planning-method-matches-event-submission");
        Trace.reset();int[] exact={requiredQuads};check((boolean)invokeMarker("grandMarker",marker,exact)&&exact[0]==0,"exact-budget-admits-whole-controller");Snapshot exactDraw=record("enabled/budget-method/exact");flush();check(exactDraw.submissions.equals(planned.submissions),"exact-budget-does-not-truncate");
        Trace.reset();int[] shortBudget={requiredQuads-1};check(!(boolean)invokeMarker("grandMarker",marker,shortBudget),"insufficient-budget-rejects-controller");
        check(Trace.rows.isEmpty()&&shortBudget[0]==requiredQuads-1,"insufficient-budget-emits-nothing-and-keeps-budget");record("enabled/budget-method/rejected-empty");
        invokeMarker("originalMarker",marker,null);Snapshot fallback=record("enabled/budget-method/complete-fallback");flush();
        HighPowerVisualConfig.RAIL_ARRAY_GRAND.set(false);Snapshot original=render("enabled/budget-method/classic-reference");
        check(fallback.submissions.equals(original.submissions)&&!soft(fallback),"insufficient-budget-fallback-is-complete-original-submission");
    }
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[0]);Files.createDirectories(output);
        // Readiness is established only through the actual material registration paths.
        RailThunderArrayFallbackHarness.prepareMaterial("enabled",Path.of(args[1]));
        check(RailThunderArrayGrandMaterial.enabled(),"actual-material-enabled-after-explicit-registration");
        try(var c=new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(output.resolve("capture.txt.gz"))),StandardCharsets.UTF_8));var l=Files.newBufferedWriter(output.resolve("cases.tsv"))){capture=c;ledger=l;statusAuthority();clocksAndHeartbeat();queuedCallbacks();controllerBudget();insufficientBudgetMethod();}
        Files.writeString(output.resolve("checks.txt"),String.join("\n",CHECKS)+"\n");
        System.out.println("{\"result\":\"PASS_ENABLED_INTEGRATION\",\"checks\":"+CHECKS.size()+",\"snapshots\":"+snapshots+",\"optional_vertices_four_controllers\":"+optionalVertices+",\"combined_vertices_including_classic\":"+combinedVertices+",\"required_quads_budget_method\":"+requiredQuads+"}");
    }
}
