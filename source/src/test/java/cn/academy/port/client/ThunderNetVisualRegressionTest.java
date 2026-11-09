package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Engine-free execution of NEW141's real input, transport, lifetime and64m geometry helpers. */
public final class ThunderNetVisualRegressionTest {
    private static int checks;
    private static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    private static UUID id(int n){return new UUID(0,n);}
    private static ThunderNetGeometry.Point p(double x,double y,double z){return new ThunderNetGeometry.Point(x,y,z);}
    private static ThunderNetState.Node node(int n,int status){return new ThunderNetState.Node(10+n,id(10+n),p(n*.4,2,50+n*.2),status);}
    private static ThunderNetState.Frame frame(int owner,long epoch,long input,long token,long seq,int phase,int age,List<ThunderNetState.Node> nodes,int links,int reason){
        return new ThunderNetState.Frame(new ThunderNetState.Identity(id(owner),epoch,input,token),seq,phase,age,p(0,0,0),p(0,2,50),nodes,links,reason);
    }
    private static ThunderNetState.Frame f(long seq,int phase,int age,List<ThunderNetState.Node> nodes){return frame(1,2,3,4,seq,phase,age,nodes,ThunderNetGeometry.linkMask(nodes.size()),0);}
    public static void main(String[] args)throws Exception{
        var input=new ThunderNetInputState();var first=new HighPowerInput.Input(0,3,8,2);var second=new HighPowerInput.Input(1,4,8,2);
        input.begin(first,-100);check(input.current().identity().equals(first)&&input.current().physicalKey()==-100,"capture immutable slot, actor, epoch and actual physical key");
        check(input.release(second)==null&&input.holding(),"wrong input cannot release held context");
        for(int snapshot=0;snapshot<100;snapshot++)check(input.releaseWhenUp(true)==null&&input.holding(),"ordinary snapshot cannot authorize release or cancel");
        check(input.releaseWhenUp(false).equals(first),"physical release survives silent classic delegate rebuild");
        check(input.releaseWhenUp(false)==null&&input.release(first)==null,"poll and normal callback release exactly once");
        check(input.cancelWhenBlocked(false)==null&&input.current()!=null,"release preserves identity for pending receipt");
        check(input.cancelWhenBlocked(true).equals(first)&&input.current()==null,"genuine UI or focus cancellation closes released identity");
        input.begin(second,19);check(input.cancel(first)==null&&input.holding(),"old abort cannot end replacement input");
        check(input.cancel(second).equals(second)&&input.release(second)==null,"matching abort prevents future release");
        input.begin(first,19);input.reset();check(input.current()==null&&!input.holding(),"session replacement clears input");
        boolean invalid=false;try{input.begin(new HighPowerInput.Input(4,3,8,2),19);}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"fifth configured slot refused");

        var state=new ThunderNetState();var locks=List.of(node(0,0),node(1,0),node(2,0));
        check(state.accept(f(1,0,0,locks),true),"server-confirmed unpaid locks accepted");
        check(!state.accept(f(1,0,0,locks),true),"duplicate packet cannot reset lease");
        check(!state.accept(f(2,0,1,List.of(node(0,1))),true),"pending state cannot contain premature hit");
        check(!state.accept(f(2,0,8,locks),true),"client does not invent pending phase beyond server threshold");
        check(!state.accept(f(2,1,7,locks),true),"holding cannot precede server8tick threshold");
        check(state.accept(f(2,1,8,List.of(node(0,1),node(1,0),node(2,2))),true),"server hit, lock and refusal remain distinct");
        check(!state.accept(f(3,0,7,locks),true),"late unpaid preview cannot replace active hold");
        check(!state.accept(f(3,1,9,List.of(node(3,0))),true),"target absent from initialset cannot appear later");
        check(state.accept(f(3,1,10,List.of(node(0,0),node(2,0))),true),"locks may permanently leave set");
        check(!state.accept(f(4,1,11,locks),true),"removed target cannot reacquire");
        check(!state.accept(frame(1,3,3,4,4,1,11,List.of(node(0,0)),0,0),true),"same server token cannot change owner epoch");
        check(!state.accept(frame(1,2,4,4,4,1,11,List.of(node(0,0)),0,0),true),"same server token cannot change input");
        var movedAnchor=new ThunderNetState.Frame(f(4,1,11,List.of(node(0,0))).id(),4,1,11,p(0,0,0),p(1,2,50),List.of(node(0,0)),0,0);
        check(!state.accept(movedAnchor,true),"server anchor cannot move within cast");
        check(state.accept(f(4,3,12,List.of()),true)&&state.size()==0,"server end immediately retires controller");
        check(!state.accept(f(999,1,13,List.of(node(0,1))),true),"ended token cannot resurrect into hit");
        check(!state.accept(f(999,3,13,List.of(node(0,1))),true),"repeated terminal cannot restart impact fade");
        state.cancel(2,9);check(!state.accept(frame(1,2,9,5,1,0,0,locks,7,0),true),"abort before server lock fences late begin");
        for(int i=10;i<500;i++)state.cancel(2,i);
        check(!state.accept(frame(1,2,9,6,1,0,0,locks,7,0),true),"old local cancellations do not expire by cache eviction");
        check(state.accept(frame(1,2,500,7,1,0,0,locks,7,0),true),"new physical input accepted");
        state.clear();check(!state.accept(frame(1,2,500,7,2,1,8,locks,7,0),true),"explicit same-session clear retains replay fences");
        state.resetSession();check(state.accept(f(1,0,0,locks),true),"new session resets all transport ownership");
        check(state.accept(f(2,3,2,List.of(node(0,1),node(1,2))),true),"early tap terminal preserves confirmed hit and refusal distinctly");
        check(!ThunderNetState.valid(frame(1,2,3,4,1,3,8,List.of(node(0,1)),0,1)),"refusal cannot contain a hit");
        check(!ThunderNetState.valid(frame(1,2,3,4,1,3,68,List.of(node(0,0)),0,2)),"expired terminal cannot keep speculative lock");
        check(!ThunderNetState.valid(frame(1,2,3,4,1,0,0,locks,8,0)),"out-of-bounds LOS mask rejected");
        check(!ThunderNetState.valid(f(1,0,0,List.of(node(0,0),node(0,0)))),"duplicate target identifiers rejected");
        check(!ThunderNetState.valid(f(1,0,0,List.of(new ThunderNetState.Node(8,id(1),p(0,2,50),0)))),"caster cannot enter target web");
        check(!ThunderNetState.valid(f(1,0,0,List.of(new ThunderNetState.Node(10,id(10),p(Double.NaN,2,50),0)))),"nonfinite target rejected");
        check(!ThunderNetState.valid(f(1,0,0,List.of(new ThunderNetState.Node(10,id(10),p(0,2,ThunderNetGeometry.MAX_RANGE + .1),0)))),"overrange endpoint rejected");
        check(!ThunderNetState.valid(f(1,0,0,List.of(new ThunderNetState.Node(10,id(10),p(0,2,37.9),0)))),"target beyond immutable12m anchor rejected");
        check(!ThunderNetState.valid(f(1,0,0,List.of(node(0,0),node(1,0),node(2,0),node(3,0),node(4,0),node(5,0),node(6,0)))),"seventh node rejected");
        state.resetSession();for(int i=1;i<=64;i++)check(state.accept(frame(i+100,2,3,4,1,0,0,locks,7,0),false),"bounded observer controller accepted");
        check(!state.accept(frame(165,2,3,4,1,0,0,locks,7,0),false)&&state.size()==64,"64controller ceiling enforced");
        state.resetSession();for(int i=1;i<=1024;i++)check(state.accept(frame(i+100,2,3,4,1,3,0,List.of(),0,0),false),"bounded watermark accepted");
        check(state.watermarkCount()==1024&&!state.accept(frame(1125,2,3,4,1,0,0,locks,7,0),false),"unknown owners failclosed at watermark capacity");
        check(!state.accept(frame(101,2,3,4,99,0,0,locks,7,0),false),"capacity never evicts replay fence");
        check(state.accept(frame(101,2,4,5,1,0,0,locks,7,0),false),"known owner can progress at watermark capacity");

        var lease=new ThunderNetState.Lease(8,false);
        for(int i=0;i<300;i++)check(lease.tick(true,true)&&lease.age()==8&&lease.quiet()==0,"pause freezes clock and network lease");
        check(!lease.tick(true,false),"real invalidation clears during pause");
        lease=new ThunderNetState.Lease(8,false);for(int i=1;i<8;i++)check(lease.tick(false,true),"quiet grace bounded");
        check(!lease.tick(false,true),"missing keepalive retires at8ticks");
        lease=new ThunderNetState.Lease(67,false);check(lease.tick(false,true)&&!lease.tick(false,true),"absolute68tick lifetime cannot renew forever");
        lease=new ThunderNetState.Lease(7,true);for(int i=1;i<4;i++)check(lease.tick(false,true),"terminal hit fade survives exactly bounded display period");check(!lease.tick(false,true),"terminal hit expires after4ticks");
        lease=new ThunderNetState.Lease(12,false);lease.received(8,false);check(lease.age()==12&&lease.quiet()==0,"late keepalive cannot rewind elapsed time");

        for(var axis:List.of(p(1,0,0),p(0,1,0),p(0,-1,0),p(0,0,1),p(1,2,3))){
            var unit=axis.scale(1/axis.length());var from=p(3,7,9);
            for(double length:List.of(.1,12.0,32.0,48.0,ThunderNetGeometry.MAX_RANGE))for(boolean fine:List.of(false,true)){
                var to=from.plus(unit.scale(length));var arc=ThunderNetGeometry.arc(from,to,43,17,fine);
                check(!arc.isEmpty()&&arc.size()<=64,"64m3D arc has finite segment budget");
                check(arc.getFirst().start().equals(from)&&arc.getLast().end().equals(to),"true accepted endpoints never replaced by hand or flattened surface");
                for(var segment:arc)for(var vertex:List.of(segment.start(),segment.end())){
                    var delta=vertex.minus(from);double along=delta.dot(unit),off=delta.minus(unit.scale(along)).length();
                    check(vertex.finite()&&along>=-1e-7&&along<=length+1e-7&&off<=(fine?.045:.129),"arc jitter bounded in both orthogonal world axes");
                }
            }
            var lock=ThunderNetGeometry.lock(from,axis);check(lock.size()==4,"lock is four sparse disconnected marks");
            for(int age=0;age<4;age++){
                var hit=ThunderNetGeometry.hit(from,axis,4,age);check(hit.size()==8,"eight finite server-hit grains");
                for(var grain:hit)check(grain.point().finite()&&grain.point().minus(from).length()<.141,"hit remains close to exact server target point");
            }
        }
        check(ThunderNetGeometry.arc(p(0,0,0),p(0,0,ThunderNetGeometry.MAX_RANGE + .01),1,0,false).isEmpty(),"NEW141 refuses geometry above the configured reach");
        check(ThunderNetGeometry.arc(p(0,0,0),p(0,0,0),1,0,false).isEmpty(),"degenerate arc empty");
        check(ThunderNetGeometry.arc(p(0,0,0),p(Double.NaN,0,1),1,0,false).isEmpty(),"invalid geometry empty");
        check(ThunderNetGeometry.hit(p(0,0,0),p(0,0,1),1,4).isEmpty(),"hit grains cannot linger beyond finite flash");
        check(ThunderNetGeometry.MAX_NODES*ThunderNetGeometry.GRAINS_PER_HIT==48&&48<=ThunderNetGeometry.MAX_DECORATIVE_GRAINS&&ThunderNetGeometry.MAX_DECORATIVE_GRAINS==96,"strict96particle ceiling, actual max48");
        int bits=0;for(int i=0;i<6;i++)for(int j=i+1;j<6;j++){int bit=ThunderNetGeometry.linkBit(6,i,j);check(bit>0&&(bit&(bit-1))==0&&(bits&bit)==0,"each decorative link has unique LOS bit");bits|=bit;}
        check(bits==32767&&bits==ThunderNetGeometry.linkMask(6),"exact15edge server mask");
        int visible=0;for(int age=0;age<60;age++)if(ThunderNetGeometry.restraintVisible(age))visible++;
        check(visible==10&&!ThunderNetGeometry.restraintVisible(2)&&!ThunderNetGeometry.restraintVisible(11),"lowfrequency restraint has real10tick visual gaps");
        var forward=p(0,0,1);var up=p(0,1,0);
        var far=ThunderNetGeometry.nearClip(p(3,2,48),p(4,4,64),forward,.013);check(far!=null,"64mnonplanar line survives independent clipper");
        check(ThunderNetGeometry.nearClip(p(1,0,-1),p(1,0,-3),forward,.013)==null,"behindcamera line culled");
        var near=ThunderNetGeometry.nearClip(p(1,0,-1),p(1,1,1),forward,.013);
        check(near!=null&&near.start().z()>=.213-1e-8,"wholewidth near clipping");
        check(ThunderNetGeometry.clearCenter(new ThunderNetGeometry.Segment(p(0,0,1),p(0,0,64)),forward,up).isEmpty(),"sightline center remains clear even along64mbeam");
        var split=ThunderNetGeometry.clearCenter(new ThunderNetGeometry.Segment(p(-1,0,10),p(1,0,10)),forward,up);
        check(split.size()==2&&split.get(0).end().x()<=-.2+1e-8&&split.get(1).start().x()>=.2-1e-8,"center cross clipped into two unchanged outside segments");
        check(ThunderNetGeometry.clearCenter(new ThunderNetGeometry.Segment(p(1,1,10),p(2,2,20)),forward,up).size()==1,"offcenter geometry retained");
        for(var segment:split){var ribbon=ThunderNetGeometry.ribbon(segment,forward,.0035);check(ribbon!=null&&ribbon.halfWidth()<=.0035&&ribbon.side().finite(),"thin camera facing finite ribbon");}

        String client=Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String effects=Files.readString(Path.of("src/main/java/cn/academy/port/client/ThunderNetEffects.java"));
        String geometry=Files.readString(Path.of("src/main/java/cn/academy/port/client/ThunderNetGeometry.java"));
        String binding=Files.readString(Path.of("src/main/java/cn/academy/port/client/ClassicClientInputBinding.java"));
        check(client.contains("thunderNetInputs.begin(identity,node.key)")&&client.contains("request(\"hp_thunder_net_begin\",identity.wire())"),"begin sends only authenticated identity");
        check(client.contains("thunderNetInputs.release(identity)")&&client.contains("thunderNetInputs.releaseWhenUp(physicalDown(netHeld.physicalKey()))"),"captured callback and snapshot-safe actual physical release wired");
        check(client.contains("thunderNetInputs.cancelWhenBlocked(inputBlocked)")&&client.contains("thunderNetInputs.reset();ThunderNetEffects.clear()"),"focus, UI, session fences integrated");
        String cleanup=client.substring(client.indexOf("private static void cleanupModern("),client.indexOf("private static void modernSnapshotFallback("));
        check(!cleanup.contains("thunderNet"),"silent CP cleanup cannot cancelNEW141");
        String instant=binding.substring(binding.indexOf("Set<String> INSTANT="),binding.indexOf("Set<String> CONTEXTS="));check(!instant.contains("thunder_net"),"NEW141 stays held single input, never instant mapping");
        check(effects.contains("mc.level!=captured")&&effects.contains("mc.player!=capturedPlayer")&&effects.contains("mc.getConnection()!=capturedConnection")&&effects.contains("clearGeneration!=generation"),"queued packets fenced to level, player, connection, explicit clear");
        check(effects.contains("sameInputSessionEpoch(capturedSession)")&&effects.contains("thunderNetInputMatches(packet.input(),packet.epoch())"),"local packet must match exact authenticated press");
        check(effects.contains("m.lease.tick(paused,validOwner(m,mc))")&&effects.contains("world.getEntity(m.owner.getId())==m.owner"),"actual renderer uses tested pause and identity lifecycle");
        check(effects.contains("node.status()==ThunderNetState.HIT")&&effects.contains("new Impact(packet.origin(),node.point(),packet.age())"),"hit paths use serverconfirmed casterorigin and targetpoint only");
        check(effects.contains("m.state.links()&ThunderNetGeometry.linkBit")&&effects.contains("if(!visible(m.owner,a,b))continue"),"decorative edges require authoritative and presentclient LOS");
        check(effects.contains("ClipContext.Block.COLLIDER")&&effects.contains("world.hasChunkAt")&&effects.contains("visible(owner,a,b)"),"jagged segments never intentionally show through loadedworld blockers");
        check(effects.contains("MAX_VISIBLE_CONTEXTS=8")&&effects.contains("MAX_CONTEXTS=64")&&!effects.contains("addParticle")&&!effects.contains("setDeltaMovement")&&!effects.contains("setPos(")&&!effects.contains("PacketDistributor"),"bounded cosmetic renderer has no gameplay or target authority");
        check(effects.contains("ClassicRenderTypes.world(WHITE)")&&!effects.contains("ClassicRenderTypes.hand")&&effects.contains("modelView.popMatrix()"),"depth-tested worldgeometry and renderstate restoration");
        check(!geometry.contains("PolarityTetherGeometry")&&!geometry.contains("ConductiveCircuitGeometry"),"dedicated64mnonplanar geometry does not modify legacy32mcontract");
        byte[] original=Files.readAllBytes(Path.of("src/main/resources/assets/academy/textures/abilities/electromaster/skills/thunder_bolt.png"));
        check(Arrays.equals(original,Files.readAllBytes(Path.of("src/main/resources/assets/academy/textures/abilities/electromaster/skills/thunder_net.png"))),"licensed classic icon preserved byte-for-byte");
        for(String locale:List.of("en_us","zh_cn","zh_tw","ja_jp")){
            String lang=Files.readString(Path.of("src/main/resources/assets/academy/lang/"+locale+".json"));
            String description=lang.lines().filter(line->line.trim().startsWith("\"ac.ability.electromaster.thunder_net.desc\":"))
                    .findFirst().orElseThrow();
            cn.academy.port.highpower.SkillLocalizationContracts.verify(
                    com.google.gson.JsonParser.parseString(lang).getAsJsonObject(), "electromaster", "thunder_net", locale);
            for(String key:List.of("ac.ability.electromaster.thunder_net.name","ac.ability.electromaster.thunder_net.desc","academy.highpower.thunder_net.no_targets","academy.highpower.thunder_net.players_tap_only","academy.highpower.thunder_net.cannot_pay"))check(lang.contains("\""+key+"\""),"translatedNEW141 skill and refusal text in"+locale);
        }
        System.out.println("PASS "+checks+" NEW141 input, serverphase, replay, pause, initialset,64m3Dgeometry, visibleline and boundedparticle checks");
    }
}
