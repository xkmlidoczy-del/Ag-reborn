package cn.academy.port.highpower;

import cn.academy.port.highpower.core.MagneticItemPullRules;
import cn.academy.port.highpower.core.MagneticItemPullRules.Vector;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** NEW133 pure bounded policies plus narrow source invariants; native fixtures test actual engine behavior. */
public final class MagneticItemPullRegressionTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        check(MagneticItemPullRules.MAX_TARGETS==16&&MagneticItemPullRules.RANGE==cn.academy.port.core.SkillTuning.number("magnetic_item_pull", "reach")&&MagneticItemPullRules.MAX_TICKS==cn.academy.port.core.SkillTuning.number("magnetic_item_pull", "duration_ticks"),"approved bounded first-mode parameters");
        double last=8;
        for(int i=1;i<=200;i++) {var q=new BigDecimal("3").add(BigDecimal.valueOf(i).divide(new BigDecimal("100")));double cost=MagneticItemPullRules.cpCost(q);check(cost<last&&cost>=1,"continuous monotone CP curve");last=cost;check(BigDecimal.valueOf(MagneticItemPullRules.cooldownTicks(q)).compareTo(MagneticItemPullRules.theoreticalCooldown(q))>=0,"native cooldown rounds upward once");}
        for(int level=3;level<=5;level++){int i=level-3;check(MagneticItemPullRules.cpCost(BigDecimal.valueOf(level))==new double[]{8,4,1}[i],"LV3/4/5 CP anchors");check(MagneticItemPullRules.cooldownTicks(BigDecimal.valueOf(level))==new int[]{40,10,1}[i],"LV3/4/5 cooldown anchors");}
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();check(MagneticItemPullRules.candidates(List.of(a,b,a)).equals(List.of(a,b)),"selection-order dedup");check(MagneticItemPullRules.candidates(Collections.nCopies(17,a))==null,"count rejected before dedup");check(MagneticItemPullRules.candidates(Arrays.asList(a,null))==null,"null invalid");
        check(MagneticItemPullRules.expired(1,1+MagneticItemPullRules.MAX_TICKS)&&!MagneticItemPullRules.expired(1,MagneticItemPullRules.MAX_TICKS)&&MagneticItemPullRules.expired(1,0),"strict configured lifetime and backwards-clock fence");
        var zero=new Vector(0,0,0);check(MagneticItemPullRules.pull(new Vector(.36,0,0),new Vector(10,0,0))==null,"external overspeed cannot be rewritten");check(MagneticItemPullRules.pull(zero,new Vector(Math.nextUp(MagneticItemPullRules.RANGE),0,0))==null,"range bound");check(MagneticItemPullRules.pull(zero,new Vector(.6,0,0))==null,"arrival retires without braking");
        Random r=new Random(133);for(int i=0;i<20000;i++){
            Vector v=new Vector((r.nextDouble()-.5)*.3,(r.nextDouble()-.5)*.3,(r.nextDouble()-.5)*.3);Vector d=new Vector(r.nextDouble()*12+1,r.nextDouble()*6-3,r.nextDouble()*12+1);Vector next=MagneticItemPullRules.pull(v,d);
            check(next!=null&&next.finite()&&next.length()<=.3500000001,"finite slow speed ceiling");Vector add=new Vector(next.x()-v.x(),next.y()-v.y(),next.z()-v.z());check(add.length()<=.1200000001,"bounded additive impulse");check(add.x()*d.x()+add.y()*d.y()+add.z()*d.z()>=-1e-12,"added velocity points toward hand");
            check(Math.abs(add.x()*d.y()-add.y()*d.x())<1e-12&&Math.abs(add.x()*d.z()-add.z()*d.x())<1e-12,"pre-existing perpendicular inertia is preserved");
        }
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){check(MagneticItemPullRules.pull(new Vector(invalid,0,0),new Vector(1,0,0))==null,"invalid velocity refused");check(MagneticItemPullRules.pull(zero,new Vector(invalid,0,0))==null,"invalid endpoint refused");}
        String backend=Files.readString(Path.of("src/main/java/cn/academy/port/highpower/MagneticItemPull.java"));
        for(String forbidden:new String[]{"teleportTo(","setPos(","moveTo(",".move(","setItem(","setTarget(","setPickUpDelay(","setNoPickUpDelay(","playerTouch(",".discard(","setNoGravity(","noPhysics =","fallDistance =","getChunk(",".award(","awardExperience("})check(!backend.contains(forbidden),"no physical/pickup/mastery shortcut: "+forbidden);
        check(backend.contains("SELECTION_TICKS = 200")&&backend.contains("selectionValid")&&backend.contains("SELECTIONS.remove(p.getUUID(), selection)"),"one-use bounded server-aged selection");
        check(backend.contains("!state.interfering")&&backend.contains("world.getEntity(target.uuid) != item")&&backend.contains("world.noCollision(item, swept)"),"native identity, interference and swept collision fences");
        check(backend.contains("Entity.collideBoundingBox")&&backend.contains("requested.y < 0 && clipped.y >= requested.y && clipped.y <= 0")&&backend.contains("clipped.x != requested.x || clipped.z != requested.z"),"native downward floor clipping only, walls and ceilings still refused");
        String provenance=Files.readString(Path.of("src/main/java/cn/academy/port/highpower/MagneticItemProvenance.java"));check(provenance.contains("ItemTossEvent")&&!provenance.contains("getOwner()")&&!provenance.contains("getThrower("),"server event is sole provenance source");check(provenance.contains("HighPowerCombat.unsupportedReason(owner)")&&provenance.contains("getAllKeys().size() != 3"),"narrow reviewed runtime and strict versioned schema");
        System.out.println("PASS "+checks+" NEW133 bounded motion, server-selection, continuous-resource and safety checks");
    }
}
