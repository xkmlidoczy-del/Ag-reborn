package cn.academy.port.skill;
import static cn.academy.port.core.ClassicFloatLedgerExpectations.*;
import cn.academy.port.core.AbilityProgress;
public final class VectorStarterRegressionTest {
    private static int checks;
    private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1E-6,why+" actual="+a+" expected="+b);}
    private static AbilityProgress ready(String id,double e){var s=new AbilityProgress();s.selectCategory("vecmanip");s.setLevel(2);s.learn(id);s.experience.put(id,e);s.activated=true;return s;}
    public static void main(String[] args){
        for(double e:new double[]{0,.25,.5,Math.nextUp(.5F),1}){
            var s=ready(VecAccelSession.ID,e);double before=s.cp;var a=VecAccelSession.begin(s);check(a!=null,"eligible charge");
            check(a.release(false),"immediate tick0 airborne release uses initiallytrue");near(s.cp,paidCpAfter(before,VecAccelSession.cp(e),1),"capturedCP");near(s.overload,VecAccelSession.overload(e),"dynamic strain");check(s.cooldowns.get(a.ID)==VecAccelSession.cooldown(e),"source integer cooldown");near(s.exp(a.ID),masteryAfter(e,.002F,1),"source one award");check(!a.release(false),"duplicate release denied");
            s=ready(VecAccelSession.ID,e);a=VecAccelSession.begin(s);a.tick(false);check(a.canPerform()==((float)e>.5F),"strictcapturedhalf groundgate");check(a.release(false)==((float)e>.5F),"ground gate release");
        }
        var s=ready("vec_accel",0);var a=VecAccelSession.begin(s);s.experience.put("vec_accel",1D);a.tick(true);double cp=s.cp;check(a.release(false),"capturedcontext release");near(s.cp,cp-120,"CPcaptured despite gainedEXP");near(s.overload,15,"overload dynamic");check(s.cooldowns.get("vec_accel")==50,"cooldowndynamic");
        s=ready("vec_accel",0);a=VecAccelSession.begin(s);for(int i=0;i<1000;i++)a.tick(true);check(a.ticks()==1000&&a.release(false),"noinventedmaximumhold");near(VecAccelSession.speed(20),2.5*Math.sin(1),"sinradians speed");near(VecAccelSession.speed(1000),VecAccelSession.speed(20),"charge clamped20");
        s=ready("vec_accel",0);s.cp=119;a=VecAccelSession.begin(s);check(!a.release(false)&&s.cooldowns.isEmpty()&&s.exp("vec_accel")==0,"faileddebit no launch rewardcooldown");
        s=ready("vec_accel",0);a=VecAccelSession.begin(s);a.discard();check(!a.release(false)&&s.cooldowns.isEmpty(),"abortfree");
        s=ready("vec_deviation",0);var d=VecDeviationSession.begin(s,false);near(s.overload,80,"initial strain80");double before=s.cp;check(d.beginTick(false),"tickbody");d.affect(1,false);d.affect(.1F,false);d.affect(1.4F,false);d.endTick(false);
        float expectedExp=masteryAfter(masteryAfter(masteryAfter(0,.001f,1),.001f*.1f,1),.001f*1.4f,1);near(s.cp,paidCpSequence(before,13f,15f,15f,15f,5f+expectedExp*(2.5f-5f)),"two source float drains and fixed three15CP entitycosts");near(s.exp(d.ID),expectedExp,"difficulty EXP only");check(s.cooldowns.isEmpty(),"noinventedcooldown");
        s=ready("vec_deviation",0);d=VecDeviationSession.begin(s,false);s.cp=0;double extra=s.extraCp;check(d.beginTick(false)&&d.ending(),"failed13CP requestsdeferredend");d.affect(1,false);d.affect(1,false);d.endTick(false);near(s.cp,0,"forcedcost clamps0");near(s.extraCp,cpTrainingAfter(extra,15f,2,1000f),"force trainsfull requested debit");near(s.exp(d.ID),2*.001F,"failedtick stillprocessesentirequery");
        s=ready("vec_deviation",.25);d=VecDeviationSession.begin(s,false);s.cp=0;float out=d.reduceDamage(10,false);near(s.cp,0,"zeroCP reduction stillworks");near(s.exp(d.ID),masteryAfter(.25,10*.0006F,1),"incomingEXP");near(out,10*(1-VecDeviationSession.reduction(s.exp(d.ID))),"postaward EXP used toreduce");
        s=ready("vec_deviation",0);d=VecDeviationSession.begin(s,false);s.cp=3;d.reduceDamage(0,false);near(s.cp,0,"damagezero stillconsumesmincurrentCP");check(s.cpDelay==15&&s.overloadDelay==32,"zero strain holdsrecoverydelay");
        s=ready("vec_deviation",0);d=VecDeviationSession.begin(s,false);s.overload=0;d.beginTick(false);check(s.overload>=d.overloadFloor(),"floorrestored");d.endTick(false);check(s.overload>=80,"floorpersists");d.discard();check(!d.beginTick(false),"discard noextra processing");
        for(String id:new String[]{"vec_accel","vec_deviation"}){s=ready(id,0);s.category="meltdowner";check(id.equals("vec_accel")?!VecAccelSession.mayStart(s):!VecDeviationSession.mayStart(s),"wrongcategory");s=ready(id,0);s.cp=Double.NaN;check(id.equals("vec_accel")?!VecAccelSession.mayStart(s):!VecDeviationSession.mayStart(s),"NaN denied");}
        System.out.println("PASS "+checks+" VecAccel/VecDeviation state, cost, float, force, lifecycle checks");
    }
}
