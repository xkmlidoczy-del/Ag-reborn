package cn.academy.port.skill;
import static cn.academy.port.core.ClassicFloatLedgerExpectations.*;
import cn.academy.port.core.AbilityProgress;
import java.util.*;
import static cn.academy.port.skill.AdvancedMineRaySession.*;

/** Deterministic source Float/order/lifecycle differentials. No world, resource grant, or game process. */
public final class AdvancedMineRayRegressionTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void bits(float a,float b,String why){check(Float.floatToIntBits(a)==Float.floatToIntBits(b),why);}
    static AbilityProgress ready(Tier tier,double mastery){var s=new AbilityProgress();s.selectCategory("meltdowner");s.setLevel(tier.level);s.learn(tier.id);s.experience.put(tier.id,mastery);s.activated=true;return s;}
    private static float original(float a,float b,double mastery){return a+(b-a)*(float)Math.max(0,Math.min(1,mastery));}
    public static void main(String[] args) {
        for(Tier tier:Tier.values()) {
            check(range(tier)==cn.academy.port.core.SkillTuning.number(tier.id, "reach")&&HARVEST_LEVEL==5&&EXPERIENCE==.0003F,"table-backed variant reach and source mining constants");
            check(tier.fortune==(tier==Tier.EXPERT?0:3),"distinct Fortune");
            for(int i=-10;i<=1010;i++) {
                double e=i/1000D;bits(speed(e),original(.5F,1,e),"Float speed");
                bits(tier.consumption(e),original(tier==Tier.EXPERT?25:50,tier==Tier.EXPERT?15:35,e),"Float CP");
                bits(tier.overload(e),original(tier==Tier.EXPERT?300:350,tier==Tier.EXPERT?200:300,e),"Float overload");
                check(cooldown(e)==(int)original(60,30,e),"Float truncate cooldown");
            }
            for(double exp:new double[]{0,.00001,.25,.5,.8,1})for(float hard:new float[]{-1,0,.5F,1.5F,50,Float.MAX_VALUE})for(int scenario=0;scenario<5;scenario++) {
                var s=ready(tier,exp);var context=begin(s,tier,false);var w=new Grid();w.hardness=hard;w.harvest=scenario==1?6:5;w.denied=scenario==2;
                float captured=original(.5F,1,exp),left=Float.MAX_VALUE;Cell at=Cell.NONE;float cp=(float)s.cp;double paid=0;int broken=0,particles=0;boolean end=false;
                for(int tick=1;tick<=30&&!end;tick++) {
                    if(scenario==3&&tick==3){s.cp=0;cp=0;}if(scenario==4&&tick==3)w.hit=null;
                    if(scenario==4&&tick==5)w.hit=new Cell(9,2,8);
                    float cost=tier.consumption(exp);if(cp<cost)end=true;else{cp-=cost;paid+=cost;}
                    Cell hit=w.hit;
                    if(hit==null)at=Cell.NONE;
                    else if(!hit.equals(at)){if(!w.denied&&w.harvest<=5){at=hit;left=hard<0?Float.MAX_VALUE:hard;}else at=Cell.NONE;}
                    else {left-=captured;if(left<=0){broken++;at=Cell.NONE;}particles++;}
                    check(context.tick(false,w)==end,"failed consume ends after source mining tick");
                    check(context.target().equals(at),"original target/order differential");bits(context.hardnessLeft(),left,"captured Float hardness differential");
                    check(w.breaks==broken&&w.particles.size()==particles,"break/particle ordering differential");
                    check(Double.doubleToRawLongBits(s.cp)==Double.doubleToRawLongBits((double)cp),"real finite CP debit");
                    if(w.breaks>0)check(w.lastFortune==tier.fortune,"actual variant fortune delivered");
                }
                check(Double.doubleToRawLongBits(s.levelExperience)==Double.doubleToRawLongBits((double)progressAfter(0,.0003F,broken)),"literal block XP including failedCP tick");
                check(context.finish()&&!context.finish(),"termination cooldown once");check(s.cooldowns.get(tier.id)==cooldown(exp),"captured source cooldown");
                int ticks=context.ticks();context.tick(false,w);check(context.ticks()==ticks,"retired context cannot mine");
            }
            var s=ready(tier,0);var c=begin(s,tier,false);var w=new Grid();w.hardness=.5F;
            c.tick(false,w);check(w.metadata.equals(Cell.NONE),"source old target metadata passed on acquisition");
            s.experience.put(tier.id,1D);c.tick(false,w);check(w.breaks==1&&w.particles.getLast().equals(Cell.NONE),"sentinel particles after zero hardness reset");
            bits(c.mastery(),0,"mastery captured rather than recaptured");c.finish();check(s.cooldowns.get(tier.id)==60,"captured novice cooldown after mastery changes");
            s=ready(tier,0);c=begin(s,tier,false);double floor=c.overloadFloor();s.overload=0;c.tick(false,new Grid());check(s.overload==floor,"source overload floor retained");
            c.discard();check(s.cooldowns.isEmpty()&&!c.active(),"lifecycle discard never creates cooldown");
            s=ready(tier,0);c=begin(s,tier,false);w=new Grid();w.hit=Cell.NONE;w.hardness=.5F;w.denied=true;c.tick(false,w);check(!c.hasTarget()&&w.breaks==0,"actual modern negative-height sentinel block receives protection");w.denied=false;c.tick(false,w);check(c.hasTarget()&&w.metadata.equals(Cell.NONE),"negative-height block captures rather than bypassing source acquisition");c.tick(false,w);check(w.breaks==1&&!c.hasTarget()&&w.particles.getLast().equals(Cell.NONE),"modern sentinel collision fixed without filtering original post-break particle sentinel");
            s=ready(tier,1);s.cp=0;c=begin(s,tier,false);check(c!=null,"source startup costs overload only, no invented CP startup gate");w=new Grid();check(c.tick(false,w)&&c.target().equals(w.hit),"failed acquisition CP tick still captures target");
            s=ready(tier,1);s.overload=s.maxOverload()-1;c=begin(s,tier,false);check(c.ending()&&!s.overloadFine,"startup overload ceiling ends hold");c.finish();check(s.cooldowns.get(tier.id)==30,"startup termination installs cooldown");
            for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1}) {
                s=ready(tier,0);s.cp=invalid;check(begin(s,tier,false)==null,"nonfinite/negative modern saved CP fails closed");
                s=ready(tier,0);s.overload=invalid;check(begin(s,tier,false)==null,"invalid modern overload fails closed");
            }
            s=ready(tier,0);c=begin(s,tier,false);s.cp=Double.NaN;w=new Grid();check(c.tick(false,w)&&c.ticks()==0&&w.breaks==0,"post-start malformed CP closes without world mutation");
            s=ready(tier,0);s.extraCp=Double.POSITIVE_INFINITY;check(begin(s,tier,false)==null,"nonfinite modern CP capacity fails closed");s=ready(tier,0);s.level=99;check(begin(s,tier,false)==null,"malformed modern level fails before array/resource mutation");
            s=ready(tier,0);double before=s.cp;c=begin(s,tier,true);for(int i=0;i<10;i++)c.tick(true,new Grid());check(s.cp==before&&s.overload==0,"creative respects native consume semantics");
            s=ready(tier,0);s.level=tier.level-1;check(!mayStart(s,tier),"exact level gate");s.level=tier.level;s.interfering=true;check(!mayStart(s,tier),"interference gate");s.interfering=false;s.setCooldown(tier.id,1);check(!mayStart(s,tier),"cooldown gate");s.cooldowns.clear();s.category="electromaster";check(!mayStart(s,tier),"category gate");
        }
        System.out.println("PASS "+checks+" advanced mining Float, original-order, finite-power and lifecycle checks");
    }
    private static final class Grid implements World {
        Cell hit=new Cell(2,4,6),metadata;float hardness=1.5F;int harvest=5,breaks,lastFortune=-1;boolean denied;
        final List<Cell> particles=new ArrayList<>();public Cell trace(){return hit;}public boolean denied(Cell c){return denied;}
        public int harvestLevel(Cell c,Cell previous){metadata=previous;return harvest;}public float hardness(Cell c){return hardness;}
        public void breakBlock(Cell c,int fortune){breaks++;lastFortune=fortune;}public void particles(Cell c){particles.add(c);}
    }
}
