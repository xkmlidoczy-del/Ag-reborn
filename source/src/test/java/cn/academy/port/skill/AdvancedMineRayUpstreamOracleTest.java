/* Executable unchanged-original AcademyCraft1.0.7 ray oracle. See fixture NOTICE.txt.
 * No game/Gradle/GL execution. This verifies constructor/update/renderer override source bodies
 * and their contract-stub boundary behavior, not actual NeoForge rendering or pixel parity. */
package cn.academy.port.skill;

import cn.academy.port.skill.oracle.*;
import cn.academy.port.skill.oracle.AdvancedMineOracleSupport.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

public final class AdvancedMineRayUpstreamOracleTest {
    private static final String ROOT="/classic-oracles/advanced-mining/";
    private static int checks;
    private static void check(boolean condition,String reason) { checks++;if(!condition)throw new AssertionError(reason); }
    private static void same(double actual,double expected,String reason) { check(Double.doubleToLongBits(actual)==Double.doubleToLongBits(expected),reason+": "+actual+" != "+expected); }
    private static void close(double actual,double expected,String reason) { check(Math.abs(actual-expected)<1E-12,reason+": "+actual+" != "+expected); }
    private static byte[] resource(String name)throws Exception {
        try(var in=AdvancedMineRayUpstreamOracleTest.class.getResourceAsStream(ROOT+name)) {
            if(in==null)throw new AssertionError("Missing mandatory original-source oracle resource "+name);
            return in.readAllBytes();
        }
    }
    private static String hash(byte[] bytes)throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static final String[][] SOURCES={
        {"MineRayExpert.scala", "62b7b89ebedb25a6a56af23a17fa4c310cef56c78a9a73b492d8b370a4273e5d", "", ""},
        {"MineRayLuck.scala", "ddd2773143e2a52f19407fddc023075631b37262b211d2ffe7736f47a297a8eb", "", ""},
        {"MineRaysBase.scala", "39d791417af63ffeaaec1f57e4fbb79cc613474ba0353ffa277bb703a70922eb", "", ""},
        {"MineRayBasic.scala", "6c45d0b528ce98447840c66a33ee6c44f7b9b82175802b8a770be3182e82b130", "", ""},
        {"CatMeltdowner.java", "fd9ccdbc902682b65f41f11fa583fed526ee1ed80a69efc72f6b6ab670be28be", "", ""},
        {"Skill.java", "afc9c70de37bfb393f62ee3c9fe1d4e02cedf1c73c75f935bf9d8d5e638f698c", "", ""},
        {"AbilityContext.java", "9830d2efcfec0e86539a8cf49f5984e1a5cf1e48e5d9b50ad6c167fd454db00a", "", ""},
        {"DeveloperType.java", "767d3d6fc66b5bb67c657dca44ffc4cb25adb36c530a50f504c63ec7d364e85a", "", ""},
        {"DevelopData.java", "c8dfbc8fbec9d3905d4362f75507c8b67a6afe354b6295773e04559aca307d68", "", ""},
        {"LearningHelper.java", "015173ee7abc49ad12e2f2ad42c1229d38b2baafd2e33f99421492ebacae87df", "", ""},
        {"DevelopActionSkill.java", "01ec5df090826815e719d7a979c1680d6f2ef85b3ee87b4c2aad940492dadfe9", "", ""},
        {"DevConditionDep.java", "0dace28d10b1d397137aa554f33af9b662c84459306aefcc91ba6f60fab7046f", "", ""},
        {"DevConditionDeveloperType.java", "195dfc7fceb3927a86714a6451d07293afac36f4808f19d93e38c94f757296d8", "", ""},
        {"DevConditionLevel.java", "670eec9c2007f63a0718ec9964f27a56bf6655161998951c74e2b3f4189a9ea4", "", ""},
        {"EntityMineRayExpert.java", "adc47e64f4c5dfceac0441cf8e53ef0665dda9c9a9da5bf603e762606f2e8ad2", "d9198d248e16bd6abfbc90eaef1ee2db20dd4837790114eb1b85c4d5db12a636", "f50bd94ac5ae5fdc1aad71a70777708301d768e7fec1cb5505d90a5936af9fe8"},
        {"EntityMineRayLuck.java", "0f96e68d2e054d4c8d55c3e778b42c8148b61e7cead1ab1e06fff9301f0961ac", "d16d43cf9c16197bcf956199049046637db65f0d2f6368d4fdfbd2c4195a0390", "9523960ad83d8d2a0df3f2d7a9df934ddb0eafe23590fb701775dfc0e097166b"},
        {"EntityRayBase.java", "c92031238e72442a0a484907336867eef1197013c5013588ede6a614173cdbd8", "165a1be88920917d0e394124728e7b7abeb31919a6068583bfb3be5fa7132a38", "426b5d053694335ae3288daf0bd466a8c8a62c9c8f23912618b00776da91ef5d"},
        {"IRay.java", "8c8b4e20ec02c2c7cc12cd203c45a226d36b98938b33e3a33e023a505d61cdaf", "f98444a3ed7b2621c9b2b0061307616f934eafb2683798988c3829762214e6d8", "4461cb3b8f2a54bbb502dc76f18055efda96a4efca868d9eac33c030f755eebd"},
        {"RendererRayComposite.java", "08a2eb2caef7f1bb5f3287f83393e3bfd692b465ac8aafc79fb1999c483076c4", "2711ccf1dd45ef603d9653e747f9b455e802015ca1d4a8b03abf91380de29242", "6dee19727eaf2855db0f0aa7ba5dadf1e820a156423d15e1af4dc964a1ae0d92"},
        {"RendererRayCylinder.java", "fe3fd76d90e5835c7311c60db58f82d63236850d2965e0399e4714fff191ca7d", "", ""},
        {"RendererRayBaseSimple.java", "7e9165978ea2608f166b2726ffe03c0eea95a162b3ec68bc99d41a872331a32f", "", ""},
        {"RendererRayBaseGlow.java", "f1cc35cb06dba90a5a67d2c7d3464cbc4283742b4ffc8dc5ad22dfbf2572cb1a", "", ""},
        {"RendererRayGlow.java", "d4168311b956a4258f0eae6ff71c9dd67ce9f1f98abd4eeb2dd0f4f6f2621cd7", "", ""},
        {"RendererList.java", "98477c6d274aa7e281d6063c5f90b86d3cecc228ae14f4b152015012765da93e", "eef3456945f5b0e8d0e1109c4dd960b07c455e3ce1526e57a39749fd0e102023", "f0c8a9d67c827ed7f918d8de27f2657dd8a5385a4058a2c302525a1032f01051"},
        {"MdParticleFactory.java", "9e0f1e2c80235184e886d76e51d375e8ec312505d39b331da52f66d5af551cdd", "6edf60d083e75afad437d1e563f0ef6dae6706dc157ea2483aac739b54150b2f", "b62691d9dcea47813e716188f3b47dcc07aba20a7e4f0d30135008c6ffbf2e62"},
        {"Motion3D.java", "85d514c11b5130b994c2ceb7efa3bd93a7c0a832e65a9abb0f196cc6d2897a56", "b8aa00a9cac04a08abc0ecef9bda41852a342a3b36de1acd735444a87d323f1c", "7a539f9671d6823b6cdc0944561abf4ef301550c9c5bebc144e538366c5adb6b"},
        {"RandUtils.java", "e06e74399c3fbb5de54672c1198135da76d16427e2ab75e943e847e1f05623eb", "3454ceab770637bd24720d170aaffe366e65e4962ae78eb24e206221af1717ed", "ca1982e23fc107d246ac2bdb5206a8ce83ab95e3be7a6c8026e1e4b5878f0a4b"},
        {"ViewOptimize.java", "4fbd82ec5ea86dde328d8c089280ab3a3a0ec4c12912edc14faa2fc472803364", "", ""},
        {"ParticleFactory.java", "b6e897ccb656b1bb579b6bc08a002575760e79f0d0953f9af9476f6e42c78a6f", "", ""},
        {"MathUtils.java", "e80c87ca15cbae89584bd9e1111e22d2f55866bf91b434c8813c20bb98176075", "", ""},
        {"Raytrace.java", "c893f0a9434bf6e33564aa4aebe8eaecda3d502a79f7b9862402bff852e9ac0b", "", ""}
    };
    public static void verifyCanonicalSources()throws Exception {
        resource("NOTICE.txt");resource("ACADEMYCRAFT-README.md.txt");resource("GPLv3-LICENSE.txt");resource("LAMBDALIB-MIT-LICENSE.txt");resource("upstream-source-sha256.json");
        for(String[] row:SOURCES) {
            byte[] original=resource(row[0]+".txt");check(hash(original).equals(row[1]),"pinned complete upstream SHA256 "+row[0]);
            if(row[2].isEmpty())continue;
            byte[] relocated=resource(row[0]+".relocated.txt");check(hash(relocated).equals(row[2]),"pinned relocated source SHA256 "+row[0]);
            String originalText=new String(original,StandardCharsets.UTF_8),relocatedText=new String(relocated,StandardCharsets.UTF_8);
            String token=row[0].equals("IRay.java")?"public interface ":"public class ";
            String body=originalText.substring(originalText.indexOf(token));
            check(body.equals(relocatedText.substring(relocatedText.indexOf(token))),"entire unchanged executable class/interface body "+row[0]);
            check(hash(body.getBytes(StandardCharsets.UTF_8)).equals(row[3]),"pinned upstream complete class-body SHA256 "+row[0]);
            // When supplied by the cached verification script, tie actual compiled Java inputs to pinned resources too.
            String sourceRoot=System.getProperty("academy.advancedMining.oracleSourceRoot");
            if(sourceRoot!=null) {
                Path source=Path.of(sourceRoot,"cn/academy/port/skill/oracle",row[0]);
                check(Files.exists(source),"missing executable oracle Java source "+source);
                check(hash(Files.readAllBytes(source)).equals(row[2]),"actual compilation input SHA256 "+row[0]);
            }
        }
    }
    public record ParticleOutput(double x,double y,double z,double vx,double vy,double vz,String texture,int fadeStart,int fadeDuration,double alpha,float size) {}
    public record RayOutput(double x,double y,double z,double length,float yaw,float pitch,int scheduledLife,List<ParticleOutput> particles,long nextRandomBits) {}
    /** Public result hook for a differential against the modern cosmetic timeline, with no production dependency. */
    public static RayOutput sample(boolean luck,boolean local,float yaw,float pitch,long seed) {
        GameTimer.time=1000;
        World world=new World();EntityPlayer player=new EntityPlayer(world,-.2,2.3,-.1);
        player.rotationYaw=-73;player.rotationYawHead=yaw;player.rotationPitch=pitch;
        Minecraft.getMinecraft().thePlayer=local?player:null;
        RandUtils.RNG.setSeed(seed);
        EntityRayBase ray=luck?new EntityMineRayLuck(player):new EntityMineRayExpert(player);
        ray.onUpdate();
        List<ParticleOutput> particles=new ArrayList<>();
        for(Entity entity:world.spawned) {
            Particle particle=(Particle)entity;
            particles.add(new ParticleOutput(particle.posX,particle.posY,particle.posZ,particle.motionX,particle.motionY,particle.motionZ,particle.texture.toString(),particle.fadeStart,particle.fadeDuration,particle.color.a,particle.size));
        }
        return new RayOutput(ray.posX,ray.posY,ray.posZ,ray.length,ray.rotationYaw,ray.rotationPitch,ray.scheduledLife,List.copyOf(particles),RandUtils.RNG.nextLong());
    }
    private static void constructorsAndRendererOverrides() {
        GameTimer.time=1000;World world=new World();EntityPlayer player=new EntityPlayer(world,1,2,3);
        for(boolean luck:new boolean[]{false,true}) {
            EntityRayBase ray=luck?new EntityMineRayLuck(player):new EntityMineRayExpert(player);
            check(ray.blendInTime==200&&ray.blendOutTime==400&&ray.widthShrinkTime==300&&ray.life==233333,"original inherited ray timing");
            same(ray.length,15,"constructor visual length");check(ray.ignoreFrustumCheck&&ray.needsViewOptimize(),"source ray frustum/view flags");
            var renderer=luck?new EntityMineRayLuck.LuckRayRender():new EntityMineRayExpert.ExpertRayRenderer();
            same(renderer.cylinderIn.width,luck?.04:.045,"original inner radius");same(renderer.cylinderOut.width,luck?.05:.056,"original outer radius");same(renderer.glow.width,luck?.45:.5,"original billboard full width");
            same(renderer.cylinderIn.color.a,230/255D,"source constructor inner alpha");same(renderer.glow.color.a,luck?.6:.7,"source constructor glow alpha");
            check(renderer.glow.textureName.equals(luck?"mdray_luck":"mdray_expert"),"texture family is distinct");same(renderer.cylinderIn.headFix,.98,"source inner headFix");same(renderer.cylinderOut.headFix,1,"source outer headFix");
            renderer.doRender(ray,0,0,0,0,0);
            same(renderer.cylinderIn.color.a,(luck?230:180)/255D,"actual unchanged Expert doRender reset / Luck absence of override");same(renderer.glow.color.a,luck?.6:.5,"actual unchanged renderer glow-alpha override");
            same(renderer.cylinderIn.color.r,(luck?241:216)/255D,"source inner red");same(renderer.cylinderIn.color.g,(luck?229:248)/255D,"source inner green");same(renderer.cylinderIn.color.b,(luck?247:216)/255D,"source inner blue");
            same(renderer.cylinderOut.color.r,(luck?205:106)/255D,"source outer red");same(renderer.cylinderOut.color.g,(luck?166:242)/255D,"source outer green");same(renderer.cylinderOut.color.b,(luck?232:106)/255D,"source outer blue");same(renderer.cylinderOut.color.a,50/255D,"source outer alpha");
            check(world.renderCalls.size()==3,"original composite invokes all three sinks");check(world.renderCalls.get(0).startsWith("glow:"),"source composite glow first");check(world.renderCalls.get(1).equals("cylinder:"+(luck?.04:.045)+":0.98"),"source composite inner second");check(world.renderCalls.get(2).equals("cylinder:"+(luck?.05:.056)+":1.0"),"source composite outer third");world.renderCalls.clear();
            // Repeated doRender must reapply Expert settings even if another render changed them.
            if(!luck) { renderer.cylinderIn.color.a=.123;renderer.glow.color.a=.234;renderer.doRender(ray,0,0,0,0,0);same(renderer.cylinderIn.color.a,180/255D,"Expert reapplies alpha every draw");same(renderer.glow.color.a,.5,"Expert reapplies glow every draw");world.renderCalls.clear(); }
            GameTimer.time=1100;same(ray.getLength(),7.5,"source 200ms blend-in length");GameTimer.time=1200;same(ray.getLength(),15,"source blend-in exact boundary");
            long life=ray.getLifeMS();GameTimer.time=1000+life-200;same(ray.getAlpha(),.5,"source alpha fade");same(ray.getWidth(),1D-100D/300,"source independent width shrink");same(ray.getGlowAlpha(),.9*.5,"source glow wiggle-alpha uses getAlpha");
            GameTimer.time=1000;
        }
    }
    private static double[] direction(float yaw,float pitch) {
        float y=yaw/180F*(float)Math.PI,p=pitch/180F*(float)Math.PI;
        double x=-MathHelper.sin(y)*MathHelper.cos(p),dy=-MathHelper.sin(p),z=MathHelper.cos(y)*MathHelper.cos(p);
        double n=Math.sqrt(x*x+dy*dy+z*z);return new double[]{x/n,dy/n,z/n};
    }
    private static void updatesAndParticleRandomOrder() {
        int spawns=0,misses=0;
        for(boolean luck:new boolean[]{false,true})for(boolean local:new boolean[]{false,true})for(float yaw:new float[]{0,37,90,-157})for(float pitch:new float[]{0,-40,75})for(long seed=0;seed<64;seed++) {
            long actualSeed=seed*0x9E3779B97F4A7C15L;
            RayOutput actual=sample(luck,local,yaw,pitch,actualSeed);double[] direction=direction(yaw,pitch);
            same(actual.x(),-.2,"source from x");same(actual.y(),2.3+(local?0:1.6),"source from height differs local/remote");same(actual.z(),-.1,"source from z");check(actual.scheduledLife()==233333,"unchanged first-update callback life");
            // Motion3D's nonlocal-client X offset is a classic bug, not a 1.6-unit Y correction.
            double actualDx=(-.2+(local?0:1.6)+direction[0]*15)-actual.x();
            double actualDy=(2.3+(double)1.62F+direction[1]*15)-actual.y();
            double actualDz=(-.1+direction[2]*15)-actual.z();
            same(actual.length(),Math.sqrt(actualDx*actualDx+actualDz*actualDz+actualDy*actualDy),"source endpoint recomputes geometric length, not always 15");
            check(Float.floatToIntBits(actual.yaw())==Float.floatToIntBits((float)(-Math.atan2(actualDx,actualDz)*180/Math.PI)),"unchanged setFromTo float yaw");
            check(Float.floatToIntBits(actual.pitch())==Float.floatToIntBits((float)(-Math.atan2(actualDy,Math.sqrt(actualDx*actualDx+actualDz*actualDz))*180/Math.PI)),"unchanged setFromTo float pitch");
            Random expected=new Random(actualSeed);boolean spawn=expected.nextDouble()<.6;
            if(spawn)spawns++;else misses++;
            check(actual.particles().size()==(spawn?1:0),"strict .6 Bernoulli one-flight-particle update");
            if(spawn) {
                double distance=expected.nextDouble()*10;
                double vx=-.03+expected.nextDouble()*.06,vy=-.03+expected.nextDouble()*.06,vz=-.03+expected.nextDouble()*.06;
                int fadeStart=25+expected.nextInt(30);double alpha=.3+expected.nextDouble()*(.6-.3);float size=.05F+expected.nextFloat()*(.07F-.05F);
                ParticleOutput particle=actual.particles().get(0);double[] rayDirection=direction(actual.yaw(),actual.pitch());
                same(particle.x(),actual.x()+rayDirection[0]*distance,"particle along ray x with original float/LUT reprojection");same(particle.y(),actual.y()+(double)(1.8F*.85F)+rayDirection[1]*distance,"particle starts at original ray entity eye height");same(particle.z(),actual.z()+rayDirection[2]*distance,"particle along ray z");
                same(particle.vx(),vx,"particle velocity x RNG order");same(particle.vy(),vy,"particle velocity y RNG order");same(particle.vz(),vz,"particle velocity z RNG order");
                check(particle.fadeStart()==fadeStart&&particle.fadeDuration()==20,"original MdParticleFactory fade");same(particle.alpha(),alpha,"original factory alpha RNG order");check(Float.floatToIntBits(particle.size())==Float.floatToIntBits(size),"original factory float size RNG order");
                check(particle.texture().equals("academy:textures/effects/md_particle"+(luck?"_luck":"")+".png"),"Expert default / Luck flight-only particle texture");
            }
            check(actual.nextRandomBits()==expected.nextLong(),"whole unchanged update preserves public RandUtils RNG call order");
        }
        check(spawns>0&&misses>0,"seed coverage executes both spawn and nonspawn updates");
    }
    public static void main(String[] args)throws Exception {
        verifyCanonicalSources();constructorsAndRendererOverrides();updatesAndParticleRandomOrder();
        System.out.println("PASS "+checks+" pinned unchanged upstream Expert/Luck ray constructor/update/renderer-override oracle checks (no game/GL/pixel execution)");
    }
}
