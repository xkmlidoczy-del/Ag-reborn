package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.List;
import java.util.UUID;

/** Receipt provenance, post-hurt queue honesty, exact budget display and projected label bounds. */
public final class EmergencyReturnVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER=new UUID(342,1),OTHER=new UUID(342,2);
    private static final Point SOURCE=new Point(0,64,0),DEST=new Point(39,64,0);
    private static final Box BOX=new Box(38.7,64,-.3,39.3,65.8,.3);
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static EmergencyReturnState.Frame frame(UUID owner,long input,long token,long seq,int phase,int remaining) {
        return new EmergencyReturnState.Frame(new EmergencyReturnState.Identity(19,owner,71,input,token),seq,phase,
                phase==0?DEST:SOURCE,DEST,BOX,phase==2,"",remaining,"100","100",phase==2?1200:0);
    }
    private static EmergencyReturnState.Frame geometry(EmergencyReturnState.Frame f,Point source,Point destination,Box bounds,boolean moved) {
        return new EmergencyReturnState.Frame(f.id(),f.sequence(),f.phase(),source,destination,bounds,moved,f.reason(),f.remainingTicks(),f.currentBudget(),f.maxBudget(),f.automaticCooldownTicks());
    }
    private static EmergencyReturnState.Frame budget(EmergencyReturnState.Frame f,String current,String maximum,int cooldown) {
        return new EmergencyReturnState.Frame(f.id(),f.sequence(),f.phase(),f.source(),f.destination(),f.bounds(),f.moved(),f.reason(),f.remainingTicks(),current,maximum,cooldown);
    }
    public static void main(String[] args) {
        var state=new EmergencyReturnState();var mark=frame(OWNER,1,1,1,0,1200);
        check(state.accept(mark,true)&&state.markerVisible()&&state.statusFresh(),"only authenticated mark exposes destination and budget");
        check(!state.accept(mark,true),"duplicate receipt rejected");
        check(!state.accept(frame(OWNER,1,2,2,0,1200),true),"same physical nonce cannot create another controller");
        check(!state.accept(frame(OWNER,1,1,2,0,1201),true),"mark lease bounded at1200 server ticks");
        for(int i=0;i<1300;i++)check(state.tick(false,true)==null,"client time cannot destructively retire a server mark");
        check(state.active()!=null&&!state.markerVisible()&&!state.statusFresh(),"stale geometry, budget and exhausted estimate hide");
        check(state.accept(frame(OWNER,1,1,2,0,1180),true)&&state.markerVisible()&&state.remainingTicks()==1180,
                "new server heartbeat restores authoritative remaining ticks after client/server rate divergence");
        check(!state.accept(frame(OWNER,1,1,3,0,1190),true),"server sequence cannot extend original lease");
        var altered=geometry(frame(OWNER,1,1,3,0,1170),DEST,new Point(38,64,0),new Box(37.7,64,-.3,38.3,65.8,.3),false);
        check(!state.accept(altered,true),"heartbeat cannot drag frozen mark");
        var retained=geometry(frame(OWNER,2,1,3,4,1170),SOURCE,null,null,false);
        check(state.accept(retained,true)&&state.active().destination().equals(DEST)&&state.markerVisible(),"preflight refusal retains authenticated mark even without repeated geometry");
        check(!EmergencyReturnState.terminal(retained)&&state.accept(frame(OWNER,2,1,4,0,1160),true),"matching heartbeat resumes retained mark");
        var warning=frame(OWNER,2,1,5,1,1);
        check(state.accept(warning,true)&&state.queuedVisible()&&state.markerVisible(),"WARNING means actually queued automatic return");
        check(state.tick(false,true)==null&&!state.markerVisible()&&state.queuedVisible()&&state.active()!=null,
                "one-tick pulse ends without inventing evade, arrival or removal");
        check(!state.accept(frame(OWNER,2,1,6,0,1150),true),"heartbeat cannot rewind actual queued transaction");
        var success=frame(OWNER,2,1,6,2,0);
        check(!state.accept(geometry(success,SOURCE,DEST,BOX,false),true),"unconfirmed move cannot animate arrival");
        check(state.accept(success,true)&&state.active()==null&&state.automaticRemainingTicks()==1200,"actual success consumes mark and carries independent auto cooldown");
        check(!state.accept(success,true)&&!state.accept(frame(OWNER,2,1,7,1,1),true),"consumed controller never replays");
        state.tick(true,true);check(state.automaticRemainingTicks()==1200,"pause freezes cosmetic cooldown");
        state.tick(false,true);check(state.automaticRemainingTicks()==1199,"cooldown display advances one client tick");
        check(state.accept(frame(OWNER,3,2,1,0,1200),true),"new gesture/token can establish next own mark");
        check(state.accept(frame(OWNER,4,2,2,4,0),true)&&state.active()==null,"admitted failed attempt consumes mark without success");
        check(!state.accept(frame(OWNER,4,2,3,2,0),true),"late success after failure cannot reanimate");
        state.cancel(71,5);check(!state.accept(frame(OWNER,5,3,1,0,1200),true),"cancel fence blocks first late acknowledgement");
        check(state.accept(frame(OWNER,6,4,1,0,1200),true),"fresh uncancelled input proceeds");
        check(state.tick(true,false)!=null&&state.active()==null,"context invalidity still retires immediately while paused");
        check(!state.accept(frame(OWNER,6,4,2,0,1190),true),"retired world/control receipt stays fenced");
        state.resetSession();check(state.accept(mark,true),"only verified new connection resets replay table");
        var observer=new EmergencyReturnState();observer.accept(mark,true);
        check(!observer.accept(frame(OTHER,1,1,1,0,1200),false),"remote persistent mark remains private");
        check(observer.accept(frame(OTHER,1,1,2,1,1),false)&&observer.active().equals(mark),"remote queued warning does not overwrite own mark");
        check(observer.accept(frame(OTHER,1,1,3,2,0),false)&&observer.active().equals(mark),"remote confirmed success is admitted independently");
        check(!observer.accept(frame(new UUID(342,3),1,1,1,3,0),false),"unseen observer terminal cannot allocate state");
        double outside = cn.academy.port.highpower.core.EmergencyReturnRules.RANGE + 1;
        check(!EmergencyReturnState.valid(geometry(success,SOURCE,new Point(outside,64,0),new Box(outside-.3,64,-.3,outside+.3,65.8,.3),true)),"overrange arrival rejected");
        check(!EmergencyReturnState.valid(geometry(warning,new Point(Double.NaN,64,0),DEST,BOX,false)),"NaN source rejected");
        check(!EmergencyReturnState.valid(geometry(warning,SOURCE,DEST,BOX,true)),"queued warning cannot claim already moved");
        for(var bad:List.of("NaN","-1","1e2","100.0","100000000000000000001"))check(!EmergencyReturnState.valid(budget(mark,bad,"100",0)),"noncanonical or out-of-bound budget rejected");
        check(!EmergencyReturnState.valid(budget(mark,null,"100",0))&&!EmergencyReturnState.valid(budget(mark,"101","100",0)),"missing or overspent budget rejected");
        check(!EmergencyReturnState.valid(budget(mark,"100","100",1201)),"automatic cooldown bounded");
        var full=new EmergencyReturnState();
        for(int i=1;i<=EmergencyReturnState.MAX_WATERMARKS;i++)check(full.accept(frame(new UUID(342,i),1,1,1,2,0),false),"bounded owner watermark admitted");
        check(!full.accept(frame(new UUID(342,2000),1,1,1,2,0),false)&&full.watermarkCount()==1024,"full watermark table never evicts replay fences");
        for(double world:new double[]{0,29_999_000,-29_999_000}) {
            var camera=new Point(world+.0625,64,world+.125);var endpoint=new Point(world+.2425,65.8,world+.305);
            var relative=EmergencyReturnState.relative(endpoint,camera);
            check(Math.abs((float)relative.x()-.18)<1e-6&&Math.abs((float)relative.z()-.18)<1e-6,"camera-relative double subtraction preserves border corners");
        }
        check(EmergencyReturnState.labelScale(.4,1,120)==0&&EmergencyReturnState.labelScale(1,.4,120)==0,"near-camera labels suppressed");
        for(double distance:new double[]{1,2,10,40,64})for(int pixels:new int[]{20,120,400}) {
            float scale=EmergencyReturnState.labelScale(distance,distance,pixels);
            check(scale>0&&scale<=.02401&&scale*pixels/distance<=.16001,"projected label width stays compact at every distance");
        }
        for(int age=0;age<7;age++)check(PhaseSidestepMesh.frame(age)==age&&PhaseSidestepMesh.particles(SOURCE,DEST,age).size()<=32,"seven original atlas frames with bounded particles");
        check(PhaseSidestepMesh.alpha(7)==0&&PhaseSidestepMesh.particles(SOURCE,DEST,7).isEmpty(),"no lingering arrival after seven frames");
        hudLayouts();
        System.out.println("EmergencyReturnVisualRegressionTest: "+checks+" checks passed");
    }
    private static void hudLayouts() {
        for(int width:new int[]{320,480,640,960})for(int height:new int[]{180,270,360,540}) {
            var cp=new HighPowerRecoveryHudModel.Box(width-206,12,194,40);
            var keys=new HighPowerRecoveryHudModel.Box(width-185,height/2,185,88);
            var notification=new HighPowerRecoveryHudModel.Box(0,15,130,43);
            var chat=new HighPowerRecoveryHudModel.Box(0,height-130,180,100);
            var recovery=HighPowerRecoveryHudModel.layout(width,height,cp,keys,notification,chat);
            var panel=EmergencyReturnHudLayout.fit(width,height,cp,keys,notification,chat,recovery);
            check(panel==null||panel.within(width,height)&&!panel.intersects(cp)&&!panel.intersects(keys)
                    &&!panel.intersects(notification)&&!panel.intersects(chat)&&(recovery==null||!panel.intersects(recovery)),
                    "compact budget fits without touching CP, keys, recovery, notifications or chat");
            if(width>=480)check(panel!=null,"ordinary viewport has compact budget placement");
        }
        check(EmergencyReturnHudLayout.fit(100,100,null,null,null,null,null)==null,"unsupported tiny viewport hides cleanly");
        for(String lang:List.of("en_us","zh_cn","zh_tw","ja_jp")) {
            var pool=HighPowerRecoveryHudModel.pool("99999999999999999999.9","100000000000000000000",HighPowerRecoveryHudModel.hudNumberLocale(lang));
            check(!pool.full()&&!pool.current().equals(pool.maximum()),"evacuation budget formatting never invents a full exact pool");
        }
    }
}
