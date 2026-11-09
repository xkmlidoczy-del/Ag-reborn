package cn.academy.port.highpower;

import cn.academy.port.highpower.core.MagneticArrowSeparationRules;
import cn.academy.port.highpower.core.MagneticArrowSeparationRules.*;
import cn.academy.port.highpower.core.MagneticArrowSeparationRules.Vector;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** Pure quantitative policy tests; native fixtures independently exercise actual Arrow physics. */
public final class MagneticArrowSeparationRegressionTest {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void near(double a, double b, String why) { check(Math.abs(a - b) <= 1e-10, why); }
    private static int nativeDamage(Vector velocity,double baseDamage) {
        // Pinned AbstractArrow.onHitEntity: float length, double multiply/clamp, integer ceil.
        double amount=Math.max(0,Math.min(2_147_483_647d,(double)(float)Math.sqrt(velocity.lengthSquared())*baseDamage));
        return (int)Math.ceil(amount);
    }
    public static void main(String[] args) throws Exception {
        check(MagneticArrowSeparationRules.PULSE_TICKS == 20 && MagneticArrowSeparationRules.QUOTA_TICKS == 20
                && MagneticArrowSeparationRules.MAX_ARROWS == 4 && MagneticArrowSeparationRules.SCAN_LIMIT == 64, "approved pulse, persistent quota and scan limits");
        Vector origin = new Vector(0, 0, 0), axis = new Vector(0, 0, 1), shooter = new Vector(0, 0, 26);
        check(MagneticArrowSeparationRules.inCorridor(new Vector(0, 0, 24), origin, axis), "24m boundary included");
        check(!MagneticArrowSeparationRules.inCorridor(new Vector(0, 0, 24.000001), origin, axis), "outside24m refused");
        check(MagneticArrowSeparationRules.inCorridor(new Vector(2, 0, 12), origin, axis), "radius2 boundary included");
        check(!MagneticArrowSeparationRules.inCorridor(new Vector(2.000001, 0, 12), origin, axis), "outside radius2 refused");
        check(!MagneticArrowSeparationRules.inCorridor(new Vector(0, 0, -.001), origin, axis), "no rear field");
        check(!MagneticArrowSeparationRules.inCorridor(new Vector(0, 2.00001, 12), origin, axis), "3D corridor has bounded vertical radius");
        check(!MagneticArrowSeparationRules.inCorridor(new Vector(0, 0, 12), origin, new Vector(0, 0, 2)), "unnormalized axis rejected");
        for (int level = 3; level <= 5; level++) { int index = level - 3; near(MagneticArrowSeparationRules.cpCost(BigDecimal.valueOf(level)), new double[]{8, 4, 1}[index], "unchanged CP anchors"); check(MagneticArrowSeparationRules.cooldownTicks(BigDecimal.valueOf(level)) == new int[]{40, 10, 1}[index], "unchanged cooldown anchors"); }
        UUID even = new UUID(0, 2), odd = new UUID(0, 3);
        Turn l = MagneticArrowSeparationRules.turn(new Vector(0, .14, -.5), new Vector(0, 0, 12), origin, axis, shooter, 0, 0, even);
        Turn r = MagneticArrowSeparationRules.turn(new Vector(0, .14, -.5), new Vector(0, 0, 12), origin, axis, shooter, 0, 0, odd);
        check(l != null && r != null && l.side() == 1 && r.side() == -1, "deterministic UUID tie sides");
        near(l.velocity().x(), -r.velocity().x(), "symmetric outward guide");
        near(l.velocity().y(), .14, "actual Y is untouched");
        near(Math.hypot(l.velocity().x(), l.velocity().z()), .5, "actual horizontal speed untouched");
        Vector v = new Vector(0, .1, -.5); double accumulated = 0; int side = 0, writes = 0;
        for (int i = 0; i < 20; i++) {
            Turn turn = MagneticArrowSeparationRules.turn(v, new Vector(0, 0, 12), origin, axis, shooter, side, accumulated, even);
            if (turn == null) break;
            check(turn.angle() <= .10000000001, "at most .10rad per native tick");
            near(turn.velocity().y(), v.y(), "every write preserves current Y");
            near(Math.hypot(turn.velocity().x(), turn.velocity().z()), Math.hypot(v.x(), v.z()), "every write preserves current XZ speed");
            v = turn.velocity(); side = turn.side(); accumulated += turn.angle(); writes++;
        }
        near(accumulated, Math.PI / 4, "45degree cumulative bound"); check(writes == 8, "last partial turn ends exact45degrees");
        check(MagneticArrowSeparationRules.turn(v, new Vector(0,0,12), origin, axis, shooter, side, accumulated, even) == null, "cannot accumulate more on ninth tick");
        check(MagneticArrowSeparationRules.turn(new Vector(0,0,.5), new Vector(0,0,12), origin, axis, shooter, 0, 0, even) == null, "receding arrow refused");
        check(MagneticArrowSeparationRules.turn(new Vector(0,0,-.5), new Vector(0,0,12), origin, axis, new Vector(0,0,0), 0, 0, even) == null, "arrow already aimed at shooter cannot be redirected");
        check(MagneticArrowSeparationRules.turn(new Vector(0,0,-3.01), new Vector(0,0,12), origin, axis, shooter,0,0,even)==null,"unsupported overspeed refused");
        check(MagneticArrowSeparationRules.turn(new Vector(0,0,-.02), new Vector(0,0,12), origin, axis, shooter,0,0,even)==null,"negligible horizontal speed refused");
        Random random = new Random(1332);
        for (int i=0;i<20000;i++) {
            Vector velocity = new Vector((random.nextDouble()-.5)*.02,(random.nextDouble()-.5)*.4,-.1-random.nextDouble()*2);
            Vector at = new Vector((random.nextDouble()-.5)*2,0,4+random.nextDouble()*18);
            Turn turn = MagneticArrowSeparationRules.turn(velocity,at,origin,axis,shooter,0,0,new UUID(0,i));
            if (turn==null) continue;
            near(turn.velocity().y(),velocity.y(),"random actual Y identity");
            check(Double.doubleToRawLongBits(turn.velocity().y()) == Double.doubleToRawLongBits(velocity.y()), "Y bit identity");
            check(turn.velocity().lengthSquared() <= velocity.lengthSquared(), "native total squared speed never rounds upward");
            check(Math.abs(Math.hypot(turn.velocity().x(),turn.velocity().z())-Math.hypot(velocity.x(),velocity.z())) <= 16*Math.ulp(Math.hypot(velocity.x(),velocity.z())), "horizontal concession is at most sixteen ULPs");
            check(turn.velocity().x()*turn.velocity().x()+turn.velocity().z()*turn.velocity().z() <= velocity.x()*velocity.x()+velocity.z()*velocity.z(), "horizontal squared speed never rounds upward");
            for(double damage:new double[]{.5,1,2,3.5,10,1000,2_147_483_647d})
                check(nativeDamage(turn.velocity(),damage)<=nativeDamage(velocity,damage), "exact native float-speed ceil damage never increases");
            near(Math.hypot(turn.velocity().x(),turn.velocity().z()),Math.hypot(velocity.x(),velocity.z()),"random horizontal energy preservation");
            check(turn.velocity().dot(shooter.subtract(at))<=1e-10,"never aims back toward shooter");
            check(Math.abs(turn.angle())<=.1,"random angular limit");
        }
        for(double speed:new double[]{Math.nextDown(1.5),1.5,Math.nextUp(1.5),(double)Math.nextDown(1.5f),(double)Math.nextUp(1.5f),Math.nextDown(3.0)}) {
            for(double y:new double[]{0,.1,-.2}) {
                Vector incoming=new Vector(0,y,-Math.sqrt(speed*speed-y*y));
                Turn turned=MagneticArrowSeparationRules.turn(incoming,new Vector(0,0,12),origin,axis,shooter,0,0,even);
                if(turned!=null) for(double base:new double[]{2,3,10,2_147_483_647d})
                    check(nativeDamage(turned.velocity(),base)<=nativeDamage(incoming,base), "exact native damage threshold boundary");
            }
        }
        List<Admission> four = List.of(new Admission(new UUID(0,1),100),new Admission(new UUID(0,2),101),new Admission(new UUID(0,3),102),new Admission(new UUID(0,4),103));
        check(MagneticArrowSeparationRules.window(four,103,119).size()==4,"four distinct arrows remain inside rolling20");
        check(MagneticArrowSeparationRules.window(four,103,120).size()==3,"exact rolling expiry admits one capacity only");
        check(MagneticArrowSeparationRules.window(four,103,123).isEmpty(),"all four expire independently");
        check(MagneticArrowSeparationRules.window(four,103,102)==null,"backwards clock fails closed");
        check(MagneticArrowSeparationRules.window(List.of(new Admission(even,101)),100,110)==null,"future admission fails closed");
        check(MagneticArrowSeparationRules.window(List.of(new Admission(even,100),new Admission(even,101)),101,110)==null,"duplicate persistent UUID fails closed");
        check(MagneticArrowSeparationRules.window(List.of(new Admission(even,-1)),100,110)==null,"negative admission fails closed");
        check(MagneticArrowSeparationRules.window(four,103,Long.MAX_VALUE)==null,"clock overflow fails closed");
        check(MagneticArrowSeparationRules.expired(100,120)&&!MagneticArrowSeparationRules.expired(100,119)&&MagneticArrowSeparationRules.expired(100,99),"original20tick lifetime");
        for(double bad:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY})check(MagneticArrowSeparationRules.turn(new Vector(bad,0,-.5),new Vector(0,0,12),origin,axis,shooter,0,0,even)==null,"nonfinite refused");
        String backend=Files.readString(Path.of("src/main/java/cn/academy/port/highpower/MagneticArrowSeparation.java"));
        for(String forbidden:new String[]{".hurt(","setOwner(","setBaseDamage(","setPickupItemStack(","setPos(","teleportTo(","setNoGravity(","setNoPhysics(",".discard(","addFreshEntity(","getChunk(","addExperience("})check(!backend.contains(forbidden),"no damage/ownership/physics/entity/mastery shortcut: "+forbidden);
        for(String required:new String[]{"arrow.getClass() != Arrow.class","PotionContents.EMPTY","getWeaponItem()","ItemEnchantments.EMPTY","owner.getClass() == ServerPlayer.class","p.server.isPvpAllowed()","other.canHarmPlayer(p)","p.isAlliedTo(owner) || owner.isAlliedTo(p)","BlockGetter.traverseBlocks","MagneticGroundStep.leased(arrow)","ElectromasterChargeDecoy.leased(arrow)","ElectromasterPrototype.leased(arrow)","c.paid","QUOTA_KEY","LEASE_KEY","ownsLease(c, target, tick)"})check(backend.contains(required),"mandatory native guard: "+required);
        System.out.println("PASS "+checks+" NEW133 arrow corridor, horizontal conservation, turn, quota and source checks");
    }
}
