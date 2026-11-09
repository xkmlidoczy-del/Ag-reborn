package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Executes NEW142's production toggle, receiver, quiet lease and geometry helpers without a renderer. */
public final class PotentialBufferVisualRegressionTest {
    private static int checks;
    private static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    private static UUID id(int n){return new UUID(0,n);}
    private static PotentialBufferGeometry.Point p(double x,double y,double z){return new PotentialBufferGeometry.Point(x,y,z);}
    private static PotentialBufferState.Frame frame(int entity,int owner,long epoch,long input,long token,long sequence,int phase){
        return new PotentialBufferState.Frame(new PotentialBufferState.Identity(entity,id(owner),epoch,input,token),sequence,phase,
                p(0,0,0),p(0,0,1),phase==PotentialBufferState.HIT?p(0,1,.75):p(0,0,0));
    }
    private static PotentialBufferState.Frame f(long sequence,int phase){return frame(1,1,2,3,4,sequence,phase);}
    public static void main(String[] args)throws Exception{
        input();transport();lease();geometry();integration();
        System.out.println("PASS "+checks+" NEW142 toggle-release, exact identity, replay, lease, frontal surface,6m pulse and bounded visual checks; rendered acceptance not inferred");
    }
    private static void input(){
        var inputs=new PotentialBufferInputState();
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
        var state=new PotentialBufferState();
        check(state.accept(f(1,0),true)&&state.size()==1,"only authoritative frame creates one controller");
        check(!state.accept(f(1,0),true)&&!state.accept(f(0,0),true),"duplicate and invalid sequence cannot reset lease");
        check(state.accept(f(2,1),true),"positive absorbed hit accepted independently of mode");
        check(state.accept(f(3,2),true),"server paid pulse accepted independently of hit");
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
        check(!PotentialBufferState.valid(frame(-1,1,2,3,4,1,0)),"invalid entity rejected");
        check(!PotentialBufferState.valid(frame(1,1,0,3,4,1,0)),"zero epoch rejected");
        check(!PotentialBufferState.valid(frame(1,1,2,0,4,1,0)),"zero input rejected");
        check(!PotentialBufferState.valid(frame(1,1,2,3,0,1,0)),"zero token rejected");
        check(!PotentialBufferState.valid(f(1,4)),"unknown phase rejected");
        for(var origin:List.of(p(Double.NaN,0,0),p(30_000_001,0,0),p(0,4097,0)))
            check(!PotentialBufferState.valid(new PotentialBufferState.Frame(f(1,0).id(),1,0,origin,p(0,0,1),p(0,0,0))),"invalidworld position rejected");
        for(var direction:List.of(p(0,0,0),p(0,0,2),p(0,Double.POSITIVE_INFINITY,1)))
            check(!PotentialBufferState.valid(new PotentialBufferState.Frame(f(1,0).id(),1,0,p(0,0,0),direction,p(0,0,0))),"nonfinite or nonunit server facing rejected");
        check(!PotentialBufferState.valid(new PotentialBufferState.Frame(f(1,1).id(),1,1,p(0,0,0),p(0,0,1),p(0,1,6))),"distant fabricated impact rejected");
        state.resetSession();for(int i=0;i<64;i++)check(state.accept(frame(i,i+100,2,3,4,1,0),false),"bounded remote controller accepted");
        check(!state.accept(frame(100,200,2,3,4,1,0),false)&&state.size()==64,"hard64controller capacity enforced");
        state.resetSession();for(int i=0;i<1024;i++)check(state.accept(frame(i,i+100,2,3,4,1,3),false),"bounded owner replay fence accepted");
        check(state.watermarkCount()==1024&&!state.accept(frame(1024,1124,2,3,4,1,0),false),"unknown owner failsclosed at1024fences");
        check(!state.accept(frame(0,100,2,3,4,9,1),false),"capacity never evicts an old terminal");
        check(state.accept(frame(0,100,2,4,5,1,0),false),"knownowner fresh context proceeds at fence capacity");
    }
    private static void lease(){
        var lease=new PotentialBufferState.Lease();lease.received(0);
        check(lease.hitAge()==4&&lease.pulseAge()==6,"ACTIVEnever fabricates hit or pulse");
        lease.received(1);check(lease.hitAge()==0&&lease.pulseAge()==6,"HITcreates only absorption branching");
        lease.tick(false,true);lease.received(0);check(lease.hitAge()==1,"keepalive cannot repeat or extend hit");
        lease.received(2);check(lease.hitAge()==1&&lease.pulseAge()==0,"PULSEdoes not reset absorption feedback");
        lease.tick(false,true);lease.received(0);check(lease.hitAge()==2&&lease.pulseAge()==1,"keepalive preserves independently aging events");
        for(int i=0;i<300;i++)check(lease.tick(true,true)&&lease.hitAge()==2&&lease.pulseAge()==1&&lease.quiet()==0,"pause freezes visuals and lease without forgetting server mode");
        check(!lease.tick(true,false),"real invalidation closes visuals during pause");
        lease=new PotentialBufferState.Lease();for(int i=1;i<12;i++)check(lease.tick(false,true),"bounded keepalive grace");
        check(!lease.tick(false,true),"12quietticks stop lost terminal or stopped server controller");
        lease=new PotentialBufferState.Lease();
        for(int tick=0;tick<100_000;tick++){
            if(tick%4==0)lease.received(0);
            check(lease.tick(false,true)&&lease.frame()<1200,"validtoggle may persist indefinitely with bounded counters");
        }
        check(lease.hitAge()==4&&lease.pulseAge()==6,"idle keepalives create no event graphics");
    }
    private static void geometry(){
        var origin=p(3,7,9);
        for(var facing:List.of(p(1,0,0),p(0,0,1),p(0,0,-1),p(.6,.8,0),p(-.6,.8,0))){
            var horizontal=p(facing.x(),0,facing.z());horizontal=horizontal.scale(1/horizontal.length());
            for(int frame=0;frame<30;frame++){
                var layer=PotentialBufferGeometry.layer(origin,facing,123,frame);
                check(layer.size()==24,"sparse24segment surface budget");
                for(var segment:layer)for(var point:List.of(segment.start(),segment.end())){
                    var delta=point.minus(origin);var xz=p(delta.x(),0,delta.z());
                    check(point.finite()&&Math.abs(xz.length()-.75)<1e-8,"surface remains thin at exact local radius");
                    check(xz.dot(horizontal)/xz.length()>=.5&&Math.abs(delta.y())<.46,"all surface rails remain inside120degree horizontal front sector");
                }
            }
            for(int age=0;age<4;age++){
                var hit=PotentialBufferGeometry.hit(origin,facing,123,age);
                check(hit.size()==18,"onehit yields18bounded irregular local branches");
                for(var segment:hit)for(var point:List.of(segment.start(),segment.end()))
                    check(point.finite()&&point.minus(origin).length()<.25,"branching remains local to true absorbed surface point");
                var grains=PotentialBufferGeometry.grains(origin,facing,123,age);
                check(grains.size()==12,"twelvefleckspercontroller well below96particle budget");
                for(var grain:grains)check(grain.point().minus(origin).length()<.11&&grain.size()<=.007,"finefiniteflecks never fill the view");
            }
            double previous=0;
            for(int age=0;age<6;age++){
                var pulse=PotentialBufferGeometry.pulse(origin,facing,age);double radius=PotentialBufferGeometry.pulseRadius(age);
                check(pulse.size()==24&&radius>previous&&radius<=PotentialBufferGeometry.PULSE_RANGE,"outward pulse progresses with 24 segments within the configured range");
                for(var segment:pulse)for(var point:List.of(segment.start(),segment.end()))
                    check(point.finite()&&Math.abs(point.minus(origin).length()-radius)<1e-8,"pulse centered on serverfeet origin with exact spherical radius");
                previous=radius;
            }
            check(previous==PotentialBufferGeometry.PULSE_RANGE,"last visible pulse reaches the server range");
        }
        check(PotentialBufferGeometry.layer(origin,p(0,1,0),1,0).isEmpty(),"straightverticalfacing has no fabricated horizontal front");
        check(PotentialBufferGeometry.layer(origin,p(1e-7,1,0),1,0).isEmpty(),"nearvertical yaw cutoff matches authoritative1e-6 refusal");
        check(PotentialBufferGeometry.hit(origin,p(0,0,1),1,4).isEmpty()&&PotentialBufferGeometry.grains(origin,p(0,0,1),1,4).isEmpty(),"hitbranches andflecks expire at4ticks");
        check(PotentialBufferGeometry.pulse(origin,p(0,0,1),6).isEmpty(),"pulse stops after6ticks");
        check(PotentialBufferGeometry.MAX_DECORATIVE_GRAINS==96&&PotentialBufferGeometry.GRAINS_PER_HIT==12,"decorativeparticles limit remains96 actual12");
        var look=p(0,0,1);var up=p(0,1,0);
        check(PotentialBufferGeometry.nearClip(p(1,0,-1),p(1,1,-2),look,.01)==null,"behindcamera fragments culled");
        var clip=PotentialBufferGeometry.nearClip(p(1,0,-1),p(1,1,1),look,.01);
        check(clip!=null&&clip.start().z()>=.21-1e-8,"entireribbon stays infrontof nearplane");
        check(PotentialBufferGeometry.clearCenter(new PotentialBufferGeometry.Segment(p(0,0,1),p(0,0,6)),look,up,.003).isEmpty(),"centralview never filled by axial geometry");
        var split=PotentialBufferGeometry.clearCenter(new PotentialBufferGeometry.Segment(p(-1,0,6),p(1,0,6)),look,up,.003);
        check(split.size()==2&&split.getFirst().end().x()<=-.153&&split.getLast().start().x()>=.153,"center crossing split without movingworldgeometry");
        for(var segment:split){
            var ribbon=PotentialBufferGeometry.ribbon(segment,look,.003);
            check(ribbon!=null&&ribbon.halfWidth()<=.003&&ribbon.side().finite(),"bounded thin ribbon");
            for(var endpoint:List.of(ribbon.start(),ribbon.end()))for(int sign:List.of(-1,1)){
                var edge=endpoint.plus(ribbon.side().scale(sign));
                check(Math.abs(edge.x())>=PotentialBufferGeometry.CENTER_CLEAR*edge.z(),"whole ribbon stays outside empty central aiming cone");
            }
        }
    }
    private static void integration()throws Exception{
        String client=Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String effects=Files.readString(Path.of("src/main/java/cn/academy/port/client/PotentialBufferEffects.java"));
        String geometry=Files.readString(Path.of("src/main/java/cn/academy/port/client/PotentialBufferGeometry.java"));
        check(client.contains("potentialBufferInputs.tap(identity)")&&client.contains("request(\"hp_potential_buffer_toggle\",identity.wire())"),"bound key sends only authenticated toggle identity");
        check(client.contains("potentialBufferInputs.release(identity)")&&!client.contains("hp_potential_buffer_release"),"release is intentionally inert with no serverreleasechannel");
        check(client.contains("potentialBufferInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null)")&&client.contains("potentialBufferInputs.reset();PotentialBufferEffects.clear()"),"focus,screen andsession cleanup wired");
        check(client.contains("PotentialBufferEffects.activeLocal()?ClassicAbilityHud.DelegateVisual.ACTIVE"),"existingkeyHUD reflects acceptedpersistentmode afterrelease");
        String cleanup=client.substring(client.indexOf("private static void cleanupModern("),client.indexOf("private static void modernSnapshotFallback("));
        check(!cleanup.contains("potentialBuffer"),"silentCPsnapshot cleanup cannot end persistenttoggle");
        check(effects.contains("mc.level!=captured")&&effects.contains("mc.player!=capturedPlayer")&&effects.contains("mc.getConnection()!=capturedConnection")&&effects.contains("clearGeneration!=generation"),"queued packets fenced to world,player,connection and explicitclear");
        check(effects.contains("sameInputSessionEpoch(capturedSession)")&&effects.contains("potentialBufferInputMatches(packet.input(),packet.epoch())"),"localpacket requires exact authenticatedinput and owner session");
        check(effects.contains("old.owner!=owner")&&effects.contains("world.getEntity(marker.owner.getId())==marker.owner"),"sameUUIDentityreplacement cannot inherit existing context");
        check(effects.contains("marker.lease.tick(paused,validOwner(marker,mc))"),"productiontick uses testedpause,invalidations andquietlease");
        check(effects.contains("hitPoint=packet.impact()")&&effects.contains("pulsePoint=packet.point()"),"hit andpulse anchors remain distinctauthoritativepositions");
        check(effects.contains("STATE.accept(frame,owner==mc.player)")&&effects.contains("STATE.retire(marker.identity())"),"productionreceiver andexpiry retain testedreplaygate");
        check(effects.contains("MAX_VISIBLE_CONTEXTS=8")&&effects.contains("MAX_CONTEXTS=64")&&!effects.contains("addParticle")&&!effects.contains("setDeltaMovement")&&!effects.contains("setPos(")&&!effects.contains("PacketDistributor"),"boundedcosmeticcontroller has no physics,spawnorserverauthority");
        check(effects.contains("ClassicRenderTypes.world(WHITE)")&&effects.contains("ClipContext.Block.COLLIDER")&&effects.contains("world.hasChunkAt")&&effects.contains("modelView.popMatrix()"),"depthtested loadedworldfragments restore renderstate");
        check(!geometry.contains("ThunderNetGeometry")&&!geometry.contains("PolarityTetherGeometry"),"NEW142does not relaxNEW141orNEW131geometrylimits");
        var icon=Files.readAllBytes(Path.of("src/main/resources/assets/academy/textures/abilities/electromaster/skills/potential_buffer.png"));
        check(Arrays.equals(icon,Files.readAllBytes(Path.of("src/main/resources/assets/academy/textures/abilities/electromaster/skills/body_intensify.png"))),"licensedclassicprotectiveicon reusedbyteforbyte");
        for(String locale:List.of("en_us","zh_cn","zh_tw","ja_jp")){
            String lang=Files.readString(Path.of("src/main/resources/assets/academy/lang/"+locale+".json"));
            cn.academy.port.highpower.SkillLocalizationContracts.verify(
                    com.google.gson.JsonParser.parseString(lang).getAsJsonObject(), "electromaster", "potential_buffer", locale);
            for(String key:List.of("ac.ability.electromaster.potential_buffer.name","ac.ability.electromaster.potential_buffer.desc","academy.highpower.potential_buffer.unsafe_mode","academy.highpower.potential_buffer.no_shield","academy.highpower.potential_buffer.cannot_pulse"))
                check(lang.contains("\""+key+"\""),"translatedskillbounds and refusal text in"+locale);
        }
    }
}
