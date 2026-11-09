package cn.academy.port.skill;
import cn.academy.port.skill.vectororacle.MathUtils;
import cn.academy.port.skill.vectororacle.CubicCurve;
import cn.academy.port.skill.vectororacle.ParabolaOracle;
import cn.academy.port.client.ClassicVectorStarterTimeline;
import java.util.regex.*;
/** Source-derived numeric oracle plus unchanged executable LambdaLib arithmetic/curve implementations.
 * Original Scala context is inspected and source-gated; it is NOT claimed to run on1.7 Forge here. */
public final class VectorStarterSourceOracleTest {
    private static int checks;
    private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1E-7,why+" actual="+a+" expected="+b);}
    private static float[] pair(String text,String pattern){var m=Pattern.compile(pattern).matcher(text);check(m.find(),"canonical expression "+pattern);return new float[]{Float.parseFloat(m.group(1)),Float.parseFloat(m.group(2))};}
    private static CubicCurve curve(String text,String name){var curve=new CubicCurve();var matcher=Pattern.compile(name+".addPoint\\(([^,]+), ([^)]+)\\)").matcher(text);int n=0;while(matcher.find()){curve.addPoint(Double.parseDouble(matcher.group(1)),Double.parseDouble(matcher.group(2)));n++;}check(n>=3,"source curve knots "+name);return curve;}
    public static void main(String[] args)throws Exception{
        check(VectorSourceFixtures.verifyAll()==22,"allpinned noticed source files");
        String accel=VectorSourceFixtures.source("academy/VecAccel.scala"),dev=VectorSourceFixtures.source("academy/VecDeviation.scala");
        float[] acp=pair(accel,"consumption = lerpf\\((\\d+), (\\d+),"),ao=pair(accel,"overload = lerpf\\((\\d+), (\\d+),"),acd=pair(accel,"setCooldown\\(lerpf\\((\\d+), (\\d+),");
        float[] dcp=pair(dev,"tickConsumption = lerpf\\((\\d+), (\\d+),"),de=pair(dev,"comsumption = lerpf\\((\\d+), (\\d+),"),dinitial=pair(dev,"overloadToKeep = lerpf\\((\\d+), (\\d+),"),dnorm=pair(dev,"normConsume = lerpf\\(([^,]+), ([^f,]+)f,"),dstrain=pair(dev,"normOverload = lerpf\\(([^f,]+)f, ([^f,]+)f,"),dr=pair(dev,"\\((0\\.4)f, (0\\.9)f,");
        for(int i=0;i<=1000;i++){float e=i/1000F;near(VecAccelSession.cp(e),MathUtils.lerpf(acp[0],acp[1],e),"unchangedFloatCP");near(VecAccelSession.overload(e),MathUtils.lerpf(ao[0],ao[1],e),"unchangedFloatstrain");check(VecAccelSession.cooldown(e)==(int)MathUtils.lerpf(acd[0],acd[1],e),"integer cooldown");near(VecDeviationSession.tickCp(e),MathUtils.lerpf(dcp[0],dcp[1],e),"sourcecapturedtick");near(VecDeviationSession.entityCp(e),MathUtils.lerpf(de[0],de[1],e),"sourcefixedentity");near(VecDeviationSession.initialOverload(e),MathUtils.lerpf(dinitial[0],dinitial[1],e),"sourceinitial");near(VecDeviationSession.normalCp(e),MathUtils.lerpf(dnorm[0],dnorm[1],e),"sourceg_tickcp");near(VecDeviationSession.normalOverload(e),MathUtils.lerpf(dstrain[0],dstrain[1],e),"sourceg_tickstrain");near(VecDeviationSession.reduction(e),MathUtils.lerpf(dr[0],dr[1],e),"sourcefraction");}
        var progress=pair(accel,"prog = lerp\\(([^,]+), ([^,]+),");var max=Pattern.compile("MAX_VELOCITY = ([0-9.]+)").matcher(accel);check(max.find(),"sourcevelocityconstant");double velocity=Double.parseDouble(max.group(1));
        for(int ticks=0;ticks<500;ticks++)near(VecAccelSession.speed(ticks),Math.sin(MathUtils.lerp(progress[0],progress[1],MathUtils.clampd(0,1,ticks/20.0)))*velocity,"source sinecharge");
        String wave=VectorSourceFixtures.source("academy/WaveEffect.scala");var alpha=curve(wave,"alphaCurve");var size=curve(wave,"sizeCurve");
        for(int tick=0;tick<15;tick++)for(int life=8;life<12;life++)for(int offset=-1;offset<1;offset++){
            near(ClassicVectorStarterTimeline.waveAlpha(tick,life,offset),Math.min(MathUtils.clampd(0,1,alpha.valueAt(tick/15.0)),MathUtils.clampd(0,1,alpha.valueAt((tick-offset)/(double)life)))*.7,"unchanged originalCubicCurve alpha");
            near(ClassicVectorStarterTimeline.waveSize(tick,.6),.6*size.valueAt(MathUtils.clampd(0,1.62,tick/20.0)),"unchanged originalCubicCurve scale");}
        for(int yaw=-180;yaw<=180;yaw+=30)for(int pitch=-90;pitch<=90;pitch+=30)for(int charge:new int[]{0,1,10,20,50}){
            var actual=ClassicVectorStarterTimeline.parabola(yaw,pitch,charge);var original=ParabolaOracle.trajectory(yaw,pitch,charge);
            check(actual.size()==original.size(),"Original hundred-vertex geometry");for(int i=0;i<actual.size();i++){near(actual.get(i).x(),original.get(i).xCoord,"Original mutable trajectory X");near(actual.get(i).y(),original.get(i).yCoord,"Original mutable trajectory Y");near(actual.get(i).z(),original.get(i).zCoord,"Original mutable trajectory Z");}}
        check(dev.indexOf("ctx.addSkillExp(dmg * 0.0006f)")<dev.indexOf("dmg * (1 - lerpf"),"postaward reduction order");
        check(dev.contains("entities.removeAll(visited)")&&dev.contains("visited ++= entities")&&dev.contains("EntityAffection.mark(entity)"),"sourceidentityvisited persistentmark");
        check(dev.contains("ctx.consumeWithForce(0, comsumption)")&&!dev.contains("comsumption * difficulty"),"difficultyEXP no scaleddebit");
        check(VectorSourceFixtures.source("lambdalib/WorldUtils.java").contains("getEntitiesWithinAABBExcludingEntity(null"),"queryincludescaster");
        String network=VectorSourceFixtures.source("lambdalib/NetworkS11n.java");String entity=network.substring(network.indexOf("addDirect(Entity.class"),network.indexOf("addDirect(World.class"));check(entity.contains("getEntityByID")&&!entity.contains("getEntityData"),"nolegacycustomNBTtransmission");
        check(dev.contains("if (EntityAffection.isMarked(ent))"),"clientwave markconditional retained");
        System.out.println("PASS "+checks+" pinned-source scalar and executable unchanged LambdaLib differential checks");
    }
}
