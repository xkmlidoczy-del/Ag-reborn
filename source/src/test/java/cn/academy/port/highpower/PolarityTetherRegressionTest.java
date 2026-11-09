package cn.academy.port.highpower;

import cn.academy.port.highpower.core.PolarityTetherRules;
import cn.academy.port.highpower.core.PolarityTetherRules.Phase;
import cn.academy.port.highpower.core.PolarityTetherRules.Vector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Independent NEW-113 finite/phase/native-gravity witnesses; never weakens canonical movement tests. */
public final class PolarityTetherRegressionTest {
    private static int checks;
    private static void check(boolean value,String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static double distance(Vector a,Vector b) { return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2)); }
    public static void main(String[] args) throws Exception {
        check(PolarityTetherRules.RANGE == cn.academy.port.core.SkillTuning.number("polarity_tether", "reach"),"table-backed range");
        check(PolarityTetherRules.MAX_CONTROLLERS == 1 && PolarityTetherRules.MAX_PARTICLES == 64,"bounded workbook object budget");
        check(!PolarityTetherRules.canCommit(20,20) && PolarityTetherRules.canCommit(20,21),"minimum one native tick before paid commit");
        check(!PolarityTetherRules.canCommit(20,19) && PolarityTetherRules.expired(20,19),"clock regression abort");
        check(PolarityTetherRules.expired(20,20+PolarityTetherRules.MAX_TICKS) && !PolarityTetherRules.expired(20,19+PolarityTetherRules.MAX_TICKS),"finite exact expiry boundary");
        check(PolarityTetherRules.tap(Phase.PENDING,false)==Phase.PENDING,"same-tick second press cannot start motion");
        check(PolarityTetherRules.tap(Phase.ANCHORED,false)==Phase.PULLING,"second fresh tap pulls");
        check(PolarityTetherRules.tap(Phase.PULLING,false)==Phase.BRAKING,"third fresh tap brakes");
        check(PolarityTetherRules.tap(Phase.ANCHORED,true)==Phase.ENDED,"crouched anchored cancel");
        check(PolarityTetherRules.tap(Phase.PULLING,true)==Phase.BRAKING,"crouched moving cancel brakes");
        check(PolarityTetherRules.tap(Phase.ENDED,false)==Phase.ENDED,"ended cannot restart same controller");
        check(Double.isNaN(PolarityTetherRules.cpCost(0)) && Double.isNaN(PolarityTetherRules.cpCost(6)),"invalid levels have no affordable cost");
        for (int level=1;level<=5;level++) check(PolarityTetherRules.cpCost(level)>0,"explicit finite provisional CP");
        var zero=new Vector(0,0,0); var toward=new Vector(0,0,10);
        check(PolarityTetherRules.pull(new Vector(1,0,0),toward)==null,"external fast inertia aborts, never clamps old knockback");
        check(PolarityTetherRules.brake(new Vector(1,0,0))==null,"brake does not erase external fast knockback");
        check(PolarityTetherRules.pull(zero,new Vector(0,0,.8))==null,"arrival retires safely");
        check(PolarityTetherRules.pull(zero,new Vector(0,0,Math.nextUp(PolarityTetherRules.RANGE)))==null,"range strict");
        check(PolarityTetherRules.pull(zero,new Vector(0,0,PolarityTetherRules.RANGE))!=null,"inclusive range");
        check(PolarityTetherRules.pull(zero,toward,.080001)==null,"unreviewed extra gravity refused");
        for (double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            check(PolarityTetherRules.pull(new Vector(bad,0,0),toward)==null,"invalid velocity finite fence");
            check(PolarityTetherRules.pull(zero,new Vector(0,bad,0))==null,"invalid anchor finite fence");
            check(PolarityTetherRules.pull(zero,toward,bad)==null,"invalid gravity finite fence");
        }
        check(PolarityTetherRules.inwardDotProgress(1,2)<PolarityTetherRules.inwardDotProgress(0,2),"bright dots flow from anchor back to player");
        for(int tick=0;tick<1000;tick++) for(int i=0;i<4;i++) {
            double progress=PolarityTetherRules.inwardDotProgress(tick,i);
            check(progress>=0&&progress<1,"bounded inward visual progress");
        }
        Random random=new Random(113);
        for(int i=0;i<12000;i++) {
            Vector v=new Vector(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5);
            v=v.scale(random.nextDouble()*.6/Math.max(v.length(),1e-9));
            Vector a=new Vector(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5);
            a=a.scale(8/Math.max(a.length(),1e-9));
            Vector next=PolarityTetherRules.pull(v,a,.08);
            check(next!=null&&next.finite()&&next.length()<=.6000000001,"bounded finite controller speed");
            check(distance(next,v)<=PolarityTetherRules.MAX_STEP_CHANGE+1e-10,"compensated controller acceleration ceiling");
            Vector brake=PolarityTetherRules.brake(v);
            check(brake!=null&&brake.length()<=v.length()+1e-10&&distance(brake,v)<=.1800000001,"bounded monotonic brake");
        }
        // Same dry-land gravity and vertical drag as reviewed Minecraft 1.21.1 LivingEntity.travel.
        for(Vector anchor:new Vector[]{new Vector(0,10,0),new Vector(8,8,0),new Vector(10,2,0)}) {
            Vector velocity=zero; double height=0;
            for(int tick=0;tick<20;tick++) {
                Vector command=PolarityTetherRules.pull(velocity,anchor,.08);
                check(command!=null,"upward/diagonal controller remains admitted under native gravity");
                height+=command.y(); velocity=new Vector(command.x()*.91,(command.y()-.08)*.98,command.z()*.91);
            }
            check(height>1,"native gravity compensated: vertical/diagonal/shallow upward pulls ascend");
        }
        Vector brake=new Vector(.6,0,0); for(int i=0;i<4;i++)brake=PolarityTetherRules.brake(brake);
        check(brake.length()==0,"emergency brake terminates in four zero-gravity steps at ceiling");
        String backend=Files.readString(Path.of("src/main/java/cn/academy/port/highpower/PolarityTether.java"));
        for(String forbidden:new String[]{"teleportTo(","setPos(","moveTo(","fallDistance =","setBlock(","getChunk(",".hurt("})check(!backend.contains(forbidden),"no forbidden shortcut "+forbidden);
        String visual=Files.readString(Path.of("src/main/java/cn/academy/port/client/PolarityTetherEffects.java"));
        check(!visual.contains("addParticle(")&&!visual.contains("addFreshEntity("),"zero spawned particles/entities per cast");
        check(visual.contains("if (mc.isPaused()) return;"),"paused integrated game does not age or expire visual token");
        System.out.println("PASS "+checks+" NEW-113 pure phase, bounded physics, native-gravity and safety checks");
    }
}
