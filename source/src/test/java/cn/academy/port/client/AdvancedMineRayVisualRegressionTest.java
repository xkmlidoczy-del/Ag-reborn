package cn.academy.port.client;

import cn.academy.port.skill.AdvancedMineRayUpstreamOracleTest;
import java.util.*;
import static cn.academy.port.client.ClassicMeltdownerBeamTimeline.*;
import static cn.academy.port.client.ClassicAdvancedMineRayTimeline.*;

/** Cosmetic differential against executable complete original entities and factory bodies.
 * Covers math/variant wiring; actual GL/audio/socket delivery remains the main lane's work. */
public final class AdvancedMineRayVisualRegressionTest {
    private static int checks;
    private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1E-11,why+": "+a+" != "+b);}
    public static void main(String[] args)throws Exception {
        AdvancedMineRayUpstreamOracleTest.verifyCanonicalSources();
        for(boolean luck:new boolean[]{false,true}) {
            String id=luck?LUCK_ID:EXPERT_ID;var spec=spec(id);
            check(spec.lifeTicks()==233333&&spec.blendIn()==200&&spec.blendOut()==400&&spec.shrink()==300,"source variant ray timeline");
            near(spec.inner(),luck?.04:.045,"actual distinct inner width");near(spec.outer(),luck?.05:.056,"actual distinct outer width");near(spec.glowWidth(),luck?.45:.5,"actual glow width");near(spec.glowAlpha(),luck?.6:.5,"Expert render override rather than constructor alpha");near(spec.particleChance(),.6,"source strict flight particle chance");
            var inner=luck?LUCK_INNER:EXPERT_INNER;var outer=luck?LUCK_OUTER:EXPERT_OUTER;
            check(inner.equals(luck?new Color(241,229,247,230):new Color(216,248,216,180))&&outer.equals(luck?new Color(205,166,232,50):new Color(106,242,106,50)),"original variant colors, actual Expert alpha180");
            check(startup(id).equals(luck?"md.mine_luck_startup":"md.mine_expert_startup"),"source postfix startup media");
            for(String suffix:List.of("_start","_end","_particles"))check(skill(id+suffix).equals(id),"all exact acknowledged packet kinds route to own skill");
            for(long age=0;age<=spec.lifeMillis();age+=997) {
                near(lengthScale(spec,age),age<200?age/200D:1,"source blend ramp");
                double alpha=age>spec.lifeMillis()-400?1-(age+400-spec.lifeMillis())/400D:1;
                near(ClassicMeltdownerBeamTimeline.alpha(spec,age),alpha,"source400ms fade");near(glowAlpha(spec,age,.03),spec.glowAlpha()*alpha*.93*alpha,"original double alpha multiplication");
            }
            for(boolean local:new boolean[]{false,true})for(float yaw:new float[]{-180,-157,-35,0,37,90,179})for(float pitch:new float[]{-89,-40,0,33,75,89})for(int i=0;i<16;i++) {
                long seed=i*0x9e3779b97f4a7c15L;var original=AdvancedMineRayUpstreamOracleTest.sample(luck,local,yaw,pitch,seed);
                var endpoints=mineSourceEndpoints(new Point(-.2,2.3,-.1),(double)1.62F,yaw,pitch,local);
                near(endpoints.from().x(),original.x(),"actual source origin x");near(endpoints.from().y(),original.y(),"actual legacy viewpoint source origin y");near(endpoints.from().z(),original.z(),"actual source origin z");
                Point delta=endpoints.to().subtract(endpoints.from());near(delta.length(),original.length(),"geometry recomputes length despite15 nominal");
                var random=new Random(seed);boolean emits=random.nextDouble()<.6;check(original.particles().size()==(emits?1:0),"same original particle branch");
                if(emits) {
                    double travel=random.nextDouble()*10;Point velocity=new Point(-.03+random.nextDouble()*.06,-.03+random.nextDouble()*.06,-.03+random.nextDouble()*.06);
                    int life=25+random.nextInt(30);double alpha=.3+random.nextDouble()*(.6-.3);float size=.05F+random.nextFloat()*(.07F-.05F);
                    Point at=endpoints.from().add(new Point(0,RAY_EYE_HEIGHT,0)).add(entityDirection(delta.normalize()).scale(travel));var particle=original.particles().getFirst();
                    near(at.x(),particle.x(),"reused render float/LUT direction produces original flight x");near(at.y(),particle.y(),"source nonplayer eye offset produces original flight y");near(at.z(),particle.z(),"source flight z");
                    near(velocity.x(),particle.vx(),"source RNG velocity x");near(velocity.y(),particle.vy(),"source RNG velocity y");near(velocity.z(),particle.vz(),"source RNG velocity z");
                    check(particle.fadeStart()==life&&particle.fadeDuration()==20,"reused original factory fade");near(alpha,particle.alpha(),"factory RNG alpha");check(Float.floatToIntBits(size)==Float.floatToIntBits(particle.size()),"factory Float size");
                    check(particle.texture().equals("academy:textures/effects/md_particle"+(luck?"_luck":"")+".png"),"Luck changes flight particles only");
                }
                check(random.nextLong()==original.nextRandomBits(),"full original random draw order");
            }
        }
        check(skill("mine_ray_luck_bad").isEmpty()&&skill(null).isEmpty(),"unknown kind failclosed");
        // Exercise real adapter's selection methods; no Minecraft object or game loop is started.
        var adapter=ClassicMeltdownerBeamEffects.class;
        for(String methodName:List.of("contexts","tokens","input")) {
            var method=adapter.getDeclaredMethod(methodName,String.class);method.setAccessible(true);
            Set<Object> objects=Collections.newSetFromMap(new IdentityHashMap<>());for(String id:List.of(MELTDOWNER,MINE,EXPERT_ID,LUCK_ID))check(objects.add(method.invoke(null,id)),"four independent actual client "+methodName+" states");
        }
        for(String id:List.of(MELTDOWNER,MINE,EXPERT_ID,LUCK_ID))check(ClassicMeltdownerBeamEffects.owns(id),"real adapter owns all four acknowledged abilities");check(!ClassicMeltdownerBeamEffects.owns("ray_barrage"),"unrelated combat adapter unaffected");
        var a=new Tokens();var caster=new Caster(5,UUID.randomUUID());check(a.end(caster,2)&&!a.start(caster,2),"late start cannot resurrect ended variant");
        var b=new Tokens();check(b.start(caster,2)&&b.active(caster,2),"separate variant token history does not collide");
        Point sentinel=mineParticlePosition(-1,-1,-1,.1,.2,.3);near(sentinel.x(),-.9,"literal postbreak sentinel retained");near(particleStep(sentinel,new Point(.01,.02,.03),true).y(),-.79,"ordinary hit particles retain original gravity order and green texture");
        System.out.println("PASS "+checks+" advanced mining source-oracle cosmetic differentials and independent client variant ownership checks");
    }
}
