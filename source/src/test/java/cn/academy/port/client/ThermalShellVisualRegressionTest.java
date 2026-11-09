package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Executes NEW222's production toggle, receiver, quiet lease and geometry helpers without a renderer. */
public final class ThermalShellVisualRegressionTest {
    private static int checks;
    private static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    private static UUID id(int n){return new UUID(0,n);}
    private static ThermalShellMesh.Point p(double x,double y,double z){return new ThermalShellMesh.Point(x,y,z);}
    private static ThermalShellState.Frame frame(int entity,int owner,long epoch,long input,long token,long sequence,int phase){
        return new ThermalShellState.Frame(new ThermalShellState.Identity(entity,id(owner),epoch,input,token),sequence,phase,
                p(0,0,0),p(0,0,1),phase==ThermalShellState.HIT?p(0,1,.68):p(0,1,0));
    }
    private static ThermalShellState.Frame f(long sequence,int phase){return frame(1,1,2,3,4,sequence,phase);}
    public static void main(String[] args)throws Exception{
        levels();input();transport();lease();snapshots();pulseDrift();geometry();camera();
        if(args.length==0||!args[0].equals("--core"))integration();
        System.out.println("PASS "+checks+" NEW222 toggle, receipt fence, lease, event snapshots, four broken rings, 3m pulse and bounded camera geometry checks; rendered acceptance not inferred");
    }
    private static void levels(){
        check(ThermalShellState.eligibleLevel(2),"NEW222 production visual eligibility admits LV2 learning tier");
        check(ThermalShellState.eligibleLevel(3),"NEW222 production visual eligibility admits LV3 instead of inherited LV4 gate");
        check(ThermalShellState.eligibleLevel(4)&&ThermalShellState.eligibleLevel(5),"later supported levels remain eligible");
        check(!ThermalShellState.eligibleLevel(-1)&&!ThermalShellState.eligibleLevel(0)&&!ThermalShellState.eligibleLevel(1)
                &&!ThermalShellState.eligibleLevel(6),"unlearnable and unsupported levels rejected");
    }
    private static void input(){
        var inputs=new ThermalShellInputState();
        var first=new HighPowerInput.Input(0,3,8,2);var second=new HighPowerInput.Input(1,5,8,2);
        inputs.tap(first);check(inputs.current().equals(first)&&inputs.permits(3,2),"tap captures immutable authenticated input");
        check(!inputs.permits(2,2)&&!inputs.permits(3,1)&&!inputs.permits(4,2),"unrelated, old epoch and unsent inputs refused");
        for(int i=0;i<100;i++)inputs.release(first);
        check(inputs.permits(3,2)&&inputs.current().equals(first),"ordinary release never cancels persistent mode or identity");
        inputs.accepted(3,2);inputs.tap(second);
        check(inputs.permits(3,2)&&inputs.permits(5,2)&&!inputs.permits(4,2),"new toggle awaits server without erasing accepted old context or admitting unsent input");
        inputs.release(second);check(inputs.permits(3,2),"release of toggleoff request still awaits server terminal");
        check(inputs.cancel(first)==null&&inputs.permits(5,2),"old callback cannot cancel newer tap");
        inputs.ended(3,2);check(!inputs.permits(3,2)&&inputs.permits(5,2),"old terminal retires only exact accepted input");
        inputs.accepted(5,2);inputs.ended(3,2);check(inputs.permits(5,2),"stale terminal cannot erase new accepted context");
        check(inputs.cancelWhenBlocked(false)==null&&inputs.current()!=null,"safe tick does not cancel");
        check(inputs.cancelWhenBlocked(true).equals(second)&&!inputs.permits(5,2),"focus and screen abort captures latest input and clears local ownership");
        inputs.tap(first);inputs.reset();check(inputs.current()==null&&!inputs.permits(3,2),"connection reset clears tap and accepted state");
        boolean invalid=false;try{inputs.tap(new HighPowerInput.Input(4,3,8,2));}catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"invalid fifthslot identity refused before request");
    }
    private static void transport(){
        var state=new ThermalShellState();
        check(state.accept(f(1,0),true)&&state.size()==1,"only authoritative frame creates one controller");
        check(!state.accept(f(1,0),true)&&!state.accept(f(0,0),true),"duplicate and invalid sequence cannot reset lease");
        check(state.accept(f(2,1),true),"committed absorption contact accepted independently of mode");
        check(state.accept(f(3,2),true),"server deferred pulse accepted independently of hit");
        check(state.accept(f(4,0),true),"ACTIVE refresh may follow either event without phase regression confusion");
        check(!state.accept(f(3,2),true),"old pulse cannot replay after refresh");
        check(!state.accept(frame(1,1,2,3,5,5,1),true),"same input cannot replace opaque server token");
        check(!state.accept(frame(2,1,2,3,4,5,1),true),"sameinput entity replacement refused");
        check(!state.accept(frame(1,1,2,2,5,5,1),true),"newer token never rehabilitates stale input");
        check(!state.accept(frame(1,1,2,4,4,5,1),true),"same token cannot migrate to newer input");
        check(!state.accept(frame(1,1,1,20,6,5,1),true),"old ownerepoch always refused");
        check(state.accept(f(5,3),true)&&state.size()==0,"server terminal immediately removes all active geometry");
        check(!state.accept(f(999,1),true)&&!state.accept(f(999,2),true)&&!state.accept(f(999,3),true),"ended token cannot restart hit, pulse or terminal fade");
        check(state.accept(frame(1,1,2,5,6,1,0),true),"fresh toggle input and token can restart");
        state.clear();check(!state.accept(frame(1,1,2,5,6,20,0),true),"sameconnection clear preserves retired replay fence");
        state.cancel(2,9);check(!state.accept(frame(1,1,2,9,7,1,0),true),"cancelbeforefirstframe fences delayed activation");
        for(int i=10;i<2000;i++)state.cancel(2,i);
        check(!state.accept(frame(1,1,2,9,100,1,0),true),"local cancellation does not expire under owner churn");
        state.cancel(1,99999);check(!state.accept(frame(1,1,2,1999,100,1,0),true),"staleepoch cancellation cannot erase newer local fence");
        check(state.accept(frame(1,1,2,2000,101,1,0),true),"new authorized tap passes cancelthrough boundary");
        state.resetSession();check(state.accept(f(1,0),true),"actual new session resets all server transport fences");
        check(!ThermalShellState.valid(frame(-1,1,2,3,4,1,0)),"invalid entity rejected");
        check(!ThermalShellState.valid(frame(1,1,0,3,4,1,0)),"zero epoch rejected");
        check(!ThermalShellState.valid(frame(1,1,2,0,4,1,0)),"zero input rejected");
        check(!ThermalShellState.valid(frame(1,1,2,3,0,1,0)),"zero token rejected");
        check(!ThermalShellState.valid(f(1,4)),"unknown phase rejected");
        for(var origin:List.of(p(Double.NaN,0,0),p(30_000_001,0,0),p(0,4097,0)))
            check(!ThermalShellState.valid(new ThermalShellState.Frame(f(1,0).id(),1,0,origin,p(0,0,1),p(0,0,0))),"invalidworld position rejected");
        for(var direction:List.of(p(0,0,0),p(0,0,2),p(0,Double.POSITIVE_INFINITY,1)))
            check(!ThermalShellState.valid(new ThermalShellState.Frame(f(1,0).id(),1,0,p(0,0,0),direction,p(0,0,0))),"nonfinite or nonunit server facing rejected");
        check(!ThermalShellState.valid(new ThermalShellState.Frame(f(1,1).id(),1,1,p(0,0,0),p(0,0,1),p(0,1,6))),"distant fabricated impact rejected");
        state.resetSession();for(int i=0;i<64;i++)check(state.accept(frame(i,i+100,2,3,4,1,0),false),"bounded remote controller accepted");
        check(!state.accept(frame(100,200,2,3,4,1,0),false)&&state.size()==64,"hard64controller capacity enforced");
        state.resetSession();for(int i=0;i<1024;i++)check(state.accept(frame(i,i+100,2,3,4,1,3),false),"bounded owner replay fence accepted");
        check(state.watermarkCount()==1024&&!state.accept(frame(1024,1124,2,3,4,1,0),false),"unknown owner failsclosed at1024fences");
        check(!state.accept(frame(0,100,2,3,4,9,1),false),"capacity never evicts an old terminal");
        check(state.accept(frame(0,100,2,4,5,1,0),false),"knownowner fresh context proceeds at fence capacity");
    }
    private static void lease(){
        var lease=new ThermalShellState.Lease();lease.received(0);
        check(lease.hitAge()==4&&lease.pulseAge()==6,"ACTIVEnever fabricates hit or pulse");
        lease.received(1);check(lease.hitAge()==0&&lease.pulseAge()==6,"HITcreates only absorption branching");
        lease.tick(false,true);lease.received(0);check(lease.hitAge()==1,"keepalive cannot repeat or extend hit");
        lease.received(2);check(lease.hitAge()==1&&lease.pulseAge()==0,"PULSEdoes not reset absorption feedback");
        lease.tick(false,true);lease.received(0);check(lease.hitAge()==2&&lease.pulseAge()==1,"keepalive preserves independently aging events");
        for(int i=0;i<300;i++)check(lease.tick(true,true)&&lease.hitAge()==2&&lease.pulseAge()==1&&lease.quiet()==0,"pause freezes visuals and lease without forgetting server mode");
        check(!lease.tick(true,false),"real invalidation closes visuals during pause");
        lease=new ThermalShellState.Lease();for(int i=1;i<12;i++)check(lease.tick(false,true),"bounded keepalive grace");
        check(!lease.tick(false,true),"12quietticks stop lost terminal or stopped server controller");
        lease=new ThermalShellState.Lease();
        for(int tick=0;tick<100_000;tick++){
            if(tick%4==0)lease.received(0);
            check(lease.tick(false,true)&&lease.frame()<1200,"validtoggle may persist indefinitely with bounded counters");
        }
        check(lease.hitAge()==4&&lease.pulseAge()==6,"idle keepalives create no event graphics");
    }
    private static void snapshots(){
        var events=new ThermalShellState.Events();events.received(f(1,0));
        check(events.hit()==null&&events.pulse()==null,"ACTIVE never invents contact or pulse");
        events.received(f(2,1));var contact=events.hit();
        check(contact.point().equals(p(0,1,.68))&&contact.outward().equals(p(0,0,1)),"HIT uses actual contact and snapshot outward direction");
        events.received(new ThermalShellState.Frame(f(1,0).id(),3,0,p(20,2,5),p(1,0,0),p(20,3,5)));
        check(events.hit().equals(contact)&&events.pulse()==null,"moving keepalive cannot move or repeat contact");
        events.received(f(4,2));var pulse=events.pulse();
        check(pulse.equals(p(0,1,0))&&events.hit().equals(contact),"deferred wave uses exact server center without fabricating a target impact");
        events.received(new ThermalShellState.Frame(f(1,0).id(),5,0,p(20,2,5),p(1,0,0),p(20,3,5)));
        check(events.pulse().equals(pulse)&&events.hit().equals(contact),"later moving ACTIVE leaves both historical event anchors fixed");
        var centered=new ThermalShellState.Frame(f(1,1).id(),6,1,p(0,0,0),p(0,0,1),p(0,1,0));
        check(ThermalShellState.valid(centered),"server's degenerate source-at-center contact is retained exactly");
        events.received(centered);
        check(events.hit().point().equals(p(0,1,0))&&events.hit().outward().equals(p(0,0,1)),"zero-distance contact keeps actual point with finite facing basis");
        check(!ThermalShellState.valid(new ThermalShellState.Frame(f(1,1).id(),1,1,p(0,0,0),p(0,0,1),p(0,1,1.4))),"inflated distant shell contact refused");
        check(!ThermalShellState.valid(new ThermalShellState.Frame(f(1,2).id(),1,2,p(0,0,0),p(0,0,1),p(0,1,2))),"PULSE snapshot farther than approved owner drift is refused");
    }
    private static void pulseDrift(){
        var frozenCenter=p(0,1,0);
        for(double drift:List.of(.25,1.0)){
            var state=new ThermalShellState();var events=new ThermalShellState.Events();
            var release=new ThermalShellState.Frame(f(1,2).id(),1,2,p(drift,0,0),p(0,0,1),frozenCenter);
            check(ThermalShellState.valid(release)&&state.accept(release,true),"deferred PULSE accepts approved "+drift+"m owner drift");
            events.received(release);
            check(events.pulse().equals(frozenCenter)&&events.hit()==null,"release keeps frozen absorption center and creates no target contact");
            var later=new ThermalShellState.Frame(release.id(),2,0,p(drift+2,0,0),p(1,0,0),p(drift+2,1,0));
            check(state.accept(later,true),"later moving ACTIVE updates current owner frame");events.received(later);
            check(state.current(id(1)).origin().equals(p(drift+2,0,0))&&events.pulse().equals(frozenCenter)&&events.hit()==null,
                    "current rings follow owner frame while historical pulse remains fixed without phantom impacts");
            for(var segment:ThermalShellMesh.pulse(events.pulse(),5))for(var vertex:List.of(segment.start(),segment.end()))
                check(Math.abs(vertex.minus(frozenCenter).length()-ThermalShellMesh.PULSE_RANGE)<1e-8,"moving owner cannot drag the outgoing wave");
        }
        for(double drift:List.of(1.000001,1.01,1.5))
            check(!ThermalShellState.valid(new ThermalShellState.Frame(f(1,2).id(),1,2,p(drift,0,0),p(0,0,1),frozenCenter)),
                    "PULSE rejects owner drift beyond strict 1m boundary");
        check(ThermalShellState.valid(new ThermalShellState.Frame(f(1,2).id(),1,2,p(.6,.8,0),p(0,0,1),frozenCenter)),
                "drift allowance uses 3D distance matching native owner movement");
    }
    private static void geometry(){
        var center=p(3,7,9);
        for(var facing:List.of(p(1,0,0),p(0,0,1),p(0,0,-1),p(.6,.8,0),p(-.6,.8,0),p(0,1,0))){
            for(int tick=0;tick<30;tick++){
                var rings=ThermalShellMesh.rings(center,facing,123,tick);
                check(rings.size()==96,"exactly four broken rings with 24 short segments each");
                int[] count=new int[4];
                for(var segment:rings){
                    check(segment.band()>=0&&segment.band()<4,"all shell segments belong to one of four rings");count[segment.band()]++;
                    for(var vertex:List.of(segment.start(),segment.end())){
                        var delta=vertex.minus(center);
                        check(vertex.finite()&&Math.abs(Math.hypot(delta.x(),delta.z())-.68)<1e-8,"ring stays at thin close-body radius");
                        check(Math.abs(delta.y()-(segment.band()-1.5)*.4)<1e-8&&delta.length()<.91,"exact four heights and bounded body envelope");
                    }
                    check(segment.start().minus(segment.end()).length()<.08,"short fragments cannot span or fill a view");
                }
                for(int ring=0;ring<4;ring++){
                    check(count[ring]==24,"each ring has eight three-segment arcs");
                    for(int dash=0;dash<8;dash++){
                        int first=ring*24+dash*3,next=ring*24+(dash+1)%8*3;
                        var end=rings.get(first+2).end();var start=rings.get(next).start();
                        check(end.minus(start).length()>.28,"visual gaps remain visibly open between arc endpoints");
                    }
                }
            }
            for(int age=0;age<4;age++){
                var hit=ThermalShellMesh.hit(center,facing,123,age);
                check(hit.size()==8,"one absorption receipt creates eight tiny contact spokes");
                for(var segment:hit)for(var vertex:List.of(segment.start(),segment.end()))
                    check(vertex.finite()&&vertex.minus(center).length()<.10,"contact remains local to real receipt point");
                var grains=ThermalShellMesh.grains(center,facing,123,age);
                check(grains.size()==8,"at most eight direct-rendered contact flecks");
                for(var grain:grains)check(grain.point().minus(center).length()<.09&&grain.size()==.004,"fine finite flecks cannot fill camera");
            }
        }
        double previous=0;
        for(int age=0;age<6;age++){
            var pulse=ThermalShellMesh.pulse(center,age);double radius=ThermalShellMesh.pulseRadius(age);
            check(pulse.size()==32&&radius>previous&&radius<=ThermalShellMesh.PULSE_RANGE+1e-9,"outward pulse has 32 fragments within the configured range");
            for(var segment:pulse)for(var vertex:List.of(segment.start(),segment.end()))
                check(vertex.finite()&&Math.abs(vertex.minus(center).length()-radius)<1e-8,"pulse anchored to historical server center with exact radius");
            previous=radius;
        }
        check(Math.abs(previous-ThermalShellMesh.PULSE_RANGE)<1e-9,"last visible wave reaches the server range");
        check(ThermalShellMesh.hit(center,p(0,0,1),1,4).isEmpty()&&ThermalShellMesh.grains(center,p(0,0,1),1,4).isEmpty(),"contact graphics expire after four ticks");
        check(ThermalShellMesh.pulse(center,6).isEmpty()&&ThermalShellMesh.pulse(center,-1).isEmpty(),"wave geometry limited to six ticks");
        check(ThermalShellMesh.rings(p(Double.NaN,0,0),p(0,0,1),1,0).isEmpty(),"malformed ring center fails closed");
        check(ThermalShellMesh.rings(center,p(0,0,0),1,0).isEmpty(),"malformed facing fails closed");
        var budget=new ThermalShellMesh.Budget();for(int i=0;i<256;i++)check(budget.take(),"bounded mesh budget accepts exactly 256 quads");
        for(int i=0;i<30;i++)check(!budget.take()&&budget.used()==256,"mesh budget never expands after cap");
        check(ThermalShellMesh.SPAWNED_PARTICLES==0&&96*2+8*2+8+32<=ThermalShellMesh.MAX_MESH_QUADS,"all simultaneous ring contact fleck and pulse geometry fits hard cap with zero particles");
        for(int color:List.of(ThermalShellMesh.CORE,ThermalShellMesh.GREEN))
            check((color>>8&255)>=(color&255)&&(color>>8&255)>=(color>>16&255),"palette stays green-white without blue electromagnetic tones");
    }
    private static void camera(){
        var forward=p(0,0,1);var origin=p(0,0,0);
        check(ThermalShellMesh.nearClip(p(.01,0,-.2),p(.01,0,-.1),forward,.005)==null,"geometry behind camera culled");
        check(ThermalShellMesh.nearClip(p(.02,0,-.1),p(.02,0,.20),forward,.005).start().z()>=.125-1e-8,"whole ribbon safely clipped in front of near plane");
        check(ThermalShellMesh.nearClip(p(0,0,.2),p(0,0,.3),forward,.19)==null,"historical broad close-camera width rejected");
        check(ThermalShellMesh.nearClip(p(0,0,.2),p(0,0,3),forward,.005)==null,"long wedge-like segment refused");
        check(ThermalShellMesh.ribbon(new ThermalShellMesh.Segment(p(0,0,.2),p(0,0,.3),0),forward,.19)==null,"ribbon also rejects width bypass");
        int drawn=0;
        for(var camera:List.of(p(0,.62,0),p(0,.60,.679),p(.679,.60,0),p(.68,0,.68),p(0,0,2))){
            for(var look:List.of(p(0,0,1),p(0,0,-1),p(1,0,0),p(-1,0,0),p(0,-1,0),p(.6,.8,0))){
                var basis=ThermalShellMesh.basis(look);
                for(var segment:ThermalShellMesh.rings(origin,forward,1,0)){
                    var clipped=ThermalShellMesh.nearClip(segment.start().minus(camera),segment.end().minus(camera),look,.005);
                    var ribbon=ThermalShellMesh.ribbon(clipped,look,.005);if(ribbon==null)continue;drawn++;
                    check(ribbon.halfWidth()<=.005&&ribbon.side().finite(),"first-person and edge-camera width remains bounded");
                    for(var endpoint:List.of(ribbon.start(),ribbon.end())){
                        var a=endpoint.plus(ribbon.side());var b=endpoint.minus(ribbon.side());
                        double za=a.dot(basis.forward()),zb=b.dot(basis.forward());
                        check(za>=ThermalShellMesh.NEAR_DEPTH-1e-8&&zb>=ThermalShellMesh.NEAR_DEPTH-1e-8,"entire quad remains past near plane for camera inside or on ring");
                        // Compare normalized screen coordinates across the true quad width.
                        double dx=a.dot(basis.side())/za-b.dot(basis.side())/zb;
                        double dy=a.dot(basis.up())/za-b.dot(basis.up())/zb;
                        check(Math.hypot(dx,dy)<=.017,"projected ribbon width stays below one degree even at ring edge");
                    }
                }
            }
        }
        check(drawn>100,"camera assertions cover surviving first-person and close ring-edge geometry");
    }
    private static void integration()throws Exception{
        String client=Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String effects=Files.readString(Path.of("src/main/java/cn/academy/port/client/ThermalShellEffects.java"));
        String mesh=Files.readString(Path.of("src/main/java/cn/academy/port/client/ThermalShellMesh.java"));
        String render=Files.readString(Path.of("src/main/java/cn/academy/port/client/ClassicRenderTypes.java"));
        String server=Files.readString(Path.of("src/main/java/cn/academy/port/highpower/ThermalShell.java"));
        String rules=Files.readString(Path.of("src/main/java/cn/academy/port/highpower/core/ThermalShellRules.java"));
        String skills=Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerSkills.java"));
        String eligible=effects.substring(effects.indexOf("private static boolean localEligible()"),effects.indexOf("private static boolean validOwner("));
        check(eligible.contains("ThermalShellState.eligibleLevel(state.level)")&&!eligible.contains("state.level>="),"real local eligibility calls exercised LV2 and LV3 gate with no hidden copied LV4 requirement");
        check(eligible.contains("state.learned(ThermalShell.ID)&&state.learned(ScatterPlates.ID)")
                &&server.contains("s.learned(ID) && s.learned(ScatterPlates.ID) && s.level >= 2 && s.level <= 5")
                &&skills.contains("HighPowerCategories.MELTDOWNER, THERMAL_SHELL, 2, true"),"client level and prerequisite gates agree with actual server and LV2 catalog");
        check(ThermalShellMesh.PULSE_RANGE==cn.academy.port.highpower.core.ThermalShellRules.RANGE&&ThermalShellMesh.SHELL_RADIUS==cn.academy.port.highpower.core.ThermalShellRules.SHELL_RADIUS,"visual radius and pulse reach match NEW222 rules rather than NEW142 values");
        check(server.contains("% 4 == 0) send(c, ACTIVE")&&ThermalShellState.QUIET_TICKS==12
                &&rules.contains("PULSE_INTERVAL = 15, PAYMENT_PERIOD = 20")&&ThermalShellState.PULSE_TICKS==6,
                "12-tick lease accepts four-tick keepalive; six-tick visual wave does not replace server pulse or payment cadence");
        check(client.contains("thermalShellInputs.tap(identity)")&&client.contains("hp_thermal_shell_toggle"),"bound classic key sends authenticated toggle");
        check(client.contains("thermalShellInputs.release(identity)")&&!client.contains("hp_thermal_shell_release"),"ordinary release is inert and sends no toggle-off");
        check(client.contains("thermalShellInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null")&&client.contains("thermalShellInputs.reset();ThermalShellEffects.clear()"),"focus menu and session abort wired");
        check(client.contains("ThermalShellEffects.activeLocal()?ClassicAbilityHud.DelegateVisual.ACTIVE"),"existing key HUD shows server-owned active mode after key release");
        check(effects.contains("mc.level!=captured")&&effects.contains("mc.player!=capturedPlayer")&&effects.contains("mc.getConnection()!=capturedConnection")&&effects.contains("clearGeneration!=generation"),"queued receipts fenced to world player connection and generation");
        check(effects.contains("sameInputSessionEpoch(capturedSession)")&&effects.contains("thermalShellInputMatches(packet.input(),packet.epoch())"),"local receipt requires exact issued input and session");
        check(effects.contains("old.owner!=owner")&&effects.contains("world.getEntity(marker.owner.getId())==marker.owner"),"entity replacement cannot inherit context");
        check(effects.contains("state.category.equals(\"meltdowner\")")&&effects.contains("state.presets.currentContains(ThermalShell.ID)")&&effects.contains("mc.screen==null&&mc.isWindowActive()"),"category preset menu and focus invalidation immediate");
        check(effects.contains("marker.lease.tick(paused,validOwner(marker,mc))")&&effects.contains("events.received(frame(packet))"),"production controller uses tested lease and immutable event anchors");
        check(effects.contains("Vec3 center=marker.state.point().add(0,1,0)")&&effects.contains("var pulse=marker.events.pulse()")
                &&effects.contains("ThermalShellMesh.pulse(pulse,marker.lease.pulseAge())")
                &&server.contains("p.position().distanceToSqr(pending.ownerPosition) <= 1"),"current ring anchor and frozen wave anchor respect actual server one-metre drift contract");
        check(effects.contains("STATE.accept(frame,owner==mc.player)")&&effects.contains("STATE.retire(marker.identity())"),"production receiver expiry and abort preserve replay fences");
        check(effects.contains("MAX_VISIBLE_CONTEXTS=8")&&effects.contains("MAX_CONTEXTS=64")&&effects.contains("ThermalShellMesh.Budget")&&!effects.contains("addParticle")&&!effects.contains("setDeltaMovement")&&!effects.contains("setPos(")&&!effects.contains("PacketDistributor")&&!effects.contains("hurt("),"bounded renderer has no damage physics particle allocation or networking");
        check(effects.contains("ClassicRenderTypes.world(SMALL_RAY)")&&effects.contains("ClassicRenderTypes.world(WHITE)")&&effects.contains("textures/effects/mdray_small/tile.png"),"original MD green ray texture and thin white core reused");
        check(effects.contains("greenBuffers.getBuffer(greenType),core=buffers.getBuffer(coreType)"),"independent immediate buffers prevent mixed-texture consumer invalidation");
        check(effects.contains("ClipContext.Block.COLLIDER")&&effects.contains("world.hasChunkAt")&&effects.contains("modelView.popMatrix()")&&render.contains("setWriteMaskState(COLOR_WRITE)")&&!render.contains("NO_DEPTH_TEST"),"loaded-world clipping native depth test and restored render state");
        check(!mesh.contains("clearCenter")&&!mesh.contains("ThunderNet")&&!effects.contains("ClassicMeltdownerBeamEffects"),"four real ring arcs need no camera wedge mask or terrain/beam backend");
    }
}
