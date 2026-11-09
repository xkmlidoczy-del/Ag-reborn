package cn.academy.port.highpower;

import cn.academy.port.highpower.core.IronSandLanceRules;
import cn.academy.port.highpower.core.IronSandLanceRules.Segment;
import cn.academy.port.highpower.core.IronSandLanceRules.Vector;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** Pure finite bounds and source contracts. Native GameTests separately exercise inventory/combat/trajectories. */
public final class IronSandLanceRegressionTest {
    private static int checks;
    private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-9,why+" "+a+" != "+b);}
    private static int nativeDamage(Vector v,double damage){return (int)Math.ceil(Math.clamp((double)(float)Math.sqrt(v.lengthSquared())*damage,0,2_147_483_647d));}
    public static void main(String[] args)throws Exception{
        check(IronSandLanceRules.CHARGE_TICKS==12&&IronSandLanceRules.LANCES==3&&IronSandLanceRules.ORBIT_TICKS==cn.academy.port.core.SkillTuning.number("iron_sand_lance", "duration_ticks")&&IronSandLanceRules.SCAN_LIMIT==64,"approved finite budgets");
        near(IronSandLanceRules.TURN,Math.PI/4,"paired native directhit evidence requires bounded45degree deflection");near(IronSandLanceRules.RANGE,cn.academy.port.core.SkillTuning.number("iron_sand_lance", "reach"),"table-backed lance path");near(IronSandLanceRules.SPEED,2,"2m per native tick");
        check(!IronSandLanceRules.charged(100,111)&&IronSandLanceRules.charged(100,112)&&!IronSandLanceRules.charged(100,300),"server charge age exact");
        check(IronSandLanceRules.chargeExpired(100,300)&&IronSandLanceRules.chargeExpired(100,99)&&!IronSandLanceRules.chargeExpired(100,299),"bounded free hold");
        check(IronSandLanceRules.orbitExpired(100,100+IronSandLanceRules.ORBIT_TICKS)&&!IronSandLanceRules.orbitExpired(100,99+IronSandLanceRules.ORBIT_TICKS)&&IronSandLanceRules.orbitExpired(100,99),"exact configured orbit deadline");
        check(!IronSandLanceRules.clock(Long.MAX_VALUE)&&!IronSandLanceRules.clock(-1),"overflow/future invalid");
        for(int level=3;level<=5;level++){near(IronSandLanceRules.cpCost(BigDecimal.valueOf(level)),new double[]{12,4,1}[level-3],"CP anchors");check(IronSandLanceRules.cooldownTicks(BigDecimal.valueOf(level))==new int[]{60,15,2}[level-3],"cooldown anchors");}
        double before=12;for(int i=0;i<=2000;i++){BigDecimal q=BigDecimal.valueOf(3+i/1000d);double cost=IronSandLanceRules.cpCost(q);check(cost<=before+1e-10&&cost>=1,"continuous cost monotonic");before=cost;int cooldown=IronSandLanceRules.cooldownTicks(q);check(cooldown>=2&&cooldown<=60&&BigDecimal.valueOf(cooldown).compareTo(IronSandLanceRules.theoreticalCooldown(q))>=0,"ceil current curve bounded");}
        check(IronSandLanceRules.damage(new BigDecimal("1e20")).compareTo(new BigDecimal("6e19"))==0,"exact .6D no float ceiling bypass");
        check(IronSandLanceRules.damage(new BigDecimal("100000000000000000001"))==null&&IronSandLanceRules.damage(BigDecimal.ZERO)==null,"refuse invaliddamage");
        Vector zero=new Vector(0,0,0),look=new Vector(0,0,1);
        for(int i=0;i<3;i++){Vector direction=IronSandLanceRules.direction(look,i);near(direction.lengthSquared(),1,"unit launch direction");near(Math.atan2(-direction.x(),direction.z()),Math.toRadians((i-1)*4),"fixed ±4degree yaw");}
        check(IronSandLanceRules.direction(new Vector(0,0,2),0)==null&&IronSandLanceRules.direction(look,3)==null,"reject bad axis/index");
        for(int age=0;age<IronSandLanceRules.ORBIT_TICKS;age++)for(int i=0;i<3;i++){Segment lance=IronSandLanceRules.orbit(zero,i,age);near(Math.sqrt(lance.head().subtract(lance.tail()).lengthSquared()),1.2,"finitephysical length");Vector middle=lance.head().add(lance.tail()).scale(.5);near(Math.sqrt(middle.lengthSquared()),1.6,"ring radius");}
        for(double coordinate:new double[]{0,12_114_609,-11_583_366,29_999_900,-29_999_900})for(int index=0;index<3;index++){
            Vector start=new Vector(coordinate,-42,coordinate),at=start,direction=IronSandLanceRules.direction(look,index);double traveled=0;int ticks=0;
            while(IronSandLanceRules.RANGE-traveled>IronSandLanceRules.RANGE_EPSILON){
                Vector next=IronSandLanceRules.step(at,direction,IronSandLanceRules.RANGE-traveled);check(next!=null,"finite largecoordinate step");double distance=Math.sqrt(next.subtract(at).lengthSquared());
                check(distance<=IronSandLanceRules.SPEED&&distance<=IronSandLanceRules.RANGE-traveled,"actualstep neverrounds above speed/rangebudget");traveled+=distance;at=next;ticks++;check(ticks<=Math.ceil(IronSandLanceRules.RANGE/IronSandLanceRules.SPEED),"worldcoordinate ULP residual neveradds trajectoryticks");
            }
            check(traveled<=IronSandLanceRules.RANGE&&IronSandLanceRules.RANGE-traveled<=IronSandLanceRules.RANGE_EPSILON,"configured path bound respects actual representable path");
            check(IronSandLanceRules.step(at,direction,IronSandLanceRules.RANGE-traveled)==null,"submicron residual retires instead ofzero-motion loop");
        }
        check(IronSandLanceRules.orbit(zero,0,IronSandLanceRules.ORBIT_TICKS)==null,"expired orbit no pose");
        Segment lance=new Segment(new Vector(-.6,0,0),new Vector(.6,0,0));
        check(Double.isFinite(IronSandLanceRules.contact(new Vector(0,0,1),new Vector(0,0,-1),lance,lance)),"real crossing between endpoints contacts");
        check(Double.isNaN(IronSandLanceRules.contact(new Vector(0,.251,1),new Vector(0,.251,-1),lance,lance)),"outsidecapsule does notcontact");
        near(IronSandLanceRules.diagnosticMinimumDistance(new Vector(0,0,1),new Vector(0,0,-1),lance,lance),0,"read-only diagnostic witnesses actual crossing minimum");
        near(IronSandLanceRules.diagnosticMinimumDistance(new Vector(0,.251,1),new Vector(0,.251,-1),lance,lance),.251,"read-only diagnostic distinguishes vertical near-miss");

        check(Double.isNaN(IronSandLanceRules.contact(new Vector(1,0,1),new Vector(1,0,-1),lance,lance)),"cannot contact infinite extension");
        Segment displaced=new Segment(new Vector(-.6,0,2),new Vector(.6,0,2));
        check(Double.isNaN(IronSandLanceRules.contact(new Vector(0,0,-1),new Vector(0,0,1),lance,displaced)),"paths crossing spatially at differenttimes do not contact");
        check(Double.isFinite(IronSandLanceRules.contact(new Vector(0,0,1),new Vector(0,0,1),lance,displaced)),"actual movingcapsule sweeps stationaryarrow");
        Random random=new Random(131);
        for(int i=0;i<20000;i++){
            Vector velocity=new Vector((random.nextDouble()-.5)*.02,(random.nextDouble()-.5)*.4,-.1-random.nextDouble()*2);
            Vector at=new Vector((random.nextDouble()-.5),0,1.6),shooter=new Vector(0,0,12);
            Vector next=IronSandLanceRules.deflect(velocity,at,zero,shooter,new UUID(0,i));if(next==null)continue;
            check(Double.doubleToRawLongBits(next.y())==Double.doubleToRawLongBits(velocity.y()),"exact Y identity");
            check(next.lengthSquared()<=velocity.lengthSquared(),"nonincreasing native speed");
            check(next.x()*next.x()+next.z()*next.z()<=velocity.x()*velocity.x()+velocity.z()*velocity.z(),"nonincreasing horizontalenergy");
            check(next.dot(velocity)>0&&next.dot(shooter.subtract(at))<=1e-10,"no reflection or shooter aim");
            double cosine=(next.x()*velocity.x()+next.z()*velocity.z())/(Math.hypot(next.x(),next.z())*Math.hypot(velocity.x(),velocity.z()));
            check(Math.acos(Math.clamp(cosine,-1,1))<=Math.PI/4+1e-12,"singlecontact turns atmost45degrees");
            for(double damage:new double[]{2,3.5,10,1000,2_147_483_647d})check(nativeDamage(next,damage)<=nativeDamage(velocity,damage),"nativeceil damage cannotincrease");
        }
        check(IronSandLanceRules.deflect(new Vector(0,0,.5),new Vector(0,0,1.6),zero,new Vector(0,0,12),new UUID(0,1))==null,"outgoing arrow untouched");
        check(IronSandLanceRules.deflect(new Vector(0,0,-3.01),new Vector(0,0,1.6),zero,new Vector(0,0,12),new UUID(0,1))==null,"overspeed arrow untouched");
        String backend=Files.readString(Path.of("src/main/java/cn/academy/port/highpower/IronSandLance.java"));
        for(String forbidden:new String[]{".hurt(","setOwner(","setBaseDamage(","setPos(","teleportTo(","setNoGravity(","setNoPhysics(",".discard(","addFreshEntity(","getChunk(","setBlock(","setBlockAndUpdate(","invulnerableTime ="})check(!backend.contains(forbidden),"no native bypass: "+forbidden);
        for(String required:new String[]{"IronSandMedium.matches(p,c.medium)","IronSandMedium.commit(p,c.medium","c.input.equals(input)","c.identity.owns(p)","c.preset==","c.revision==","HighPowerCombat.attackIronSandLance(","c.damage","IronSandLanceRules.contact(","arrow.getClass()!=Arrow.class","PotionContents.EMPTY","ItemEnchantments.EMPTY","p.canHarmPlayer(other)","other.canHarmPlayer(p)","p.isAlliedTo(target)||target.isAlliedTo(p)","MagneticArrowSeparation.leased(arrow)","MagneticGroundStep.leased(arrow)","ElectromasterChargeDecoy.leased(arrow)","ElectromasterPrototype.leased(arrow)","world.hasChunk(x,z)","world.mayInteract(p,pos)","getBlockCollisions(p,swept)"})check(backend.contains(required),"required actualserverguard: "+required);
        System.out.println("PASS "+checks+" NEW131 finite geometry, exact curve/damage, contact, conservation and source checks");
    }
}
