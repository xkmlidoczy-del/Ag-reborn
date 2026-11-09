package cn.academy.port.passive;
import cn.academy.port.core.*;
import cn.academy.port.skill.*;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.*;
import javax.tools.ToolProvider;
public final class PassiveSkillsSourceOracleTest {
 static long checks;static void yes(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static byte[] bytes(String path)throws Exception{try(var stream=PassiveSkillsSourceOracleTest.class.getResourceAsStream("/classic-oracles/passive-skills/"+path)){if(stream==null)throw new IllegalArgumentException("Missing source fixture "+path);return stream.readAllBytes();}}
 static void bits(double modern,float original,String label){yes(Double.doubleToRawLongBits(modern)==Double.doubleToRawLongBits((double)original),label+" modern="+modern+" source="+original);}
 public static void main(String[] args)throws Exception {
  var manifest=JsonParser.parseString(new String(bytes("source-manifest.json"),StandardCharsets.UTF_8)).getAsJsonObject();yes(manifest.get("academyCommit").getAsString().equals("00d19ec0cf538f61c1095c9292f5ee6863db4521"),"pinned canonical source revision");
  for(var item:manifest.getAsJsonArray("files")){var f=item.getAsJsonObject();var resource=f.get("resource").getAsString().substring("classic-oracles/passive-skills/".length());yes(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(resource))).equals(f.get("sha256").getAsString()),"unchanged source bytes "+resource);}
  for(var item:JsonParser.parseString(new String(bytes("source-media-manifest.json"),StandardCharsets.UTF_8)).getAsJsonArray()){var f=item.getAsJsonObject();try(var input=PassiveSkillsSourceOracleTest.class.getResourceAsStream("/"+f.get("resource").getAsString())){yes(input!=null&&HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())).equals(f.get("sha256").getAsString()),"source-exact shipped passive icon/particle asset");}}
  String radiation=new String(bytes("witness/AcademyCraft-1.0.7/src/main/scala/cn/academy/vanilla/meltdowner/passiveskill/RadiationIntensify.scala.txt"),StandardCharsets.UTF_8);
  yes(radiation.contains("MathUtils.clampf(0, 1, cpData.getMaxCP / CPData.get(data.getEntity).getInitCP(5))")&&radiation.contains("MathUtils.lerpf(1.4f, 1.8f, data.getSkillExp(this))"),"radiation oracle retains exact unchanged Scala expressions");
  for(String path:List.of("src/main/java/cn/academy/vanilla/electromaster/CatElectromaster.java","src/main/java/cn/academy/vanilla/meltdowner/CatMeltdowner.java","src/main/java/cn/academy/vanilla/teleporter/CatTeleporter.java","src/main/scala/cn/academy/vanilla/vecmanip/CatVecManip.scala"))yes(new String(bytes("witness/AcademyCraft-1.0.7/"+path+".txt"),StandardCharsets.UTF_8).contains("ModuleVanilla.addGenericSkills(this)"),"actual source registers all3 generic instances in "+path);
  Path tmp=Files.createTempDirectory("academy-passive-source-oracle-");try {
   var files=JsonParser.parseString(new String(bytes("oracle-files.json"),StandardCharsets.UTF_8)).getAsJsonArray();var compilerArgs=new ArrayList<String>();compilerArgs.addAll(List.of("-proc:none","-d",tmp.resolve("classes").toString()));
   for(var entry:files){String path=entry.getAsString();Path file=tmp.resolve("src").resolve(path.substring(0,path.length()-4));Files.createDirectories(file.getParent());Files.write(file,bytes("oracle/"+path));compilerArgs.add(file.toString());}
   var compiler=ToolProvider.getSystemJavaCompiler();yes(compiler!=null&&compiler.run(null,System.out,System.err,compilerArgs.toArray(String[]::new))==0,"compile unchanged original generic Java listeners and extracted source methods");
   try(var loader=new URLClassLoader(new java.net.URL[]{tmp.resolve("classes").toUri().toURL()},ClassLoader.getPlatformClassLoader())){
    Class<?> oracle=loader.loadClass("cn.academy.passiveoracle.ClassicPassiveSourceOracle");var setup=oracle.getMethod("setup",int.class,boolean.class,float.class,boolean.class,float.class);var max=oracle.getMethod("maximumCP",float.class);var over=oracle.getMethod("maximumOverload",float.class);var recover=oracle.getMethod("recover",float.class,float.class,float.class);var probability=oracle.getMethod("probability",int.class);var rate=oracle.getMethod("radiation",float.class,float.class);var damage=oracle.getMethod("damage",float.class,int.class);var random=new Random(0x107504153L);
    for(int i=0;i<25000;i++){
     int courses=random.nextInt(8);boolean folding=random.nextBoolean(),fluct=random.nextBoolean();float fe=random.nextFloat(),se=random.nextFloat();setup.invoke(null,courses,folding,fe,fluct,se);
     var s=new AbilityProgress();s.selectCategory("teleporter");s.setLevel(5);if((courses&1)!=0)s.learn("brain_course");if((courses&2)!=0)s.learn("brain_course_advanced");if((courses&4)!=0)s.learn("mind_course");if(folding)s.experience.put("dim_folding_theorem",(double)fe);if(fluct)s.experience.put("space_fluct",(double)se);
     float base=random.nextFloat()*12000,overload=random.nextFloat()*600,cp=random.nextFloat()*base,speed=.2f+random.nextFloat()*3;
     bits(ClassicPassiveSkills.calculate(s,AbilityCalculation.Kind.MAX_CP,base),(float)max.invoke(null,base),"original BrainCourse listeners float maxCP");bits(ClassicPassiveSkills.calculate(s,AbilityCalculation.Kind.MAX_OVERLOAD,overload),(float)over.invoke(null,overload),"original AdvancedBrainCourse overload listener");
     float multiplier=ClassicPassiveSkills.calculate(s,AbilityCalculation.Kind.CP_RECOVERY,1);bits(ClassicPassiveSkills.cpRecovery(cp,base,speed,multiplier),(float)recover.invoke(null,cp,base,speed),"original MindCourse listener and CPData float recovery");
     float maximum=random.nextFloat()*24000,level5=8000+((courses&1)!=0?1000:0)+((courses&2)!=0?1500:0);
     bits(RadiationMarks.rate(RadiationMarks.displayedMastery(maximum,level5)),(float)rate.invoke(null,maximum,level5),"unchanged source RadiationIntensify and LambdaLib float expression");
     for(int tier=0;tier<3;tier++){bits(ThreateningTeleport.criticalProbability(s,tier),(float)probability.invoke(null,tier),"actual extracted TPSkillHelper sequential tier probability");float raw=random.nextFloat()*70;bits(ClassicPassiveSkills.criticalDamage(raw,tier),(float)damage.invoke(null,raw,tier),"actual source float critical multiplier");}
    }
    // Boundary source behavior must differ from a mathematically equivalent double implementation.
    setup.invoke(null,0,true,0f,false,0f);float p=(float)probability.invoke(null,0);yes((double)p>.1d,"source .1f rounds above decimal .1");
    var s=new AbilityProgress();s.selectCategory("teleporter");s.setLevel(1);s.learn("dim_folding_theorem");yes(ThreateningTeleport.criticalTier(s,()->.1d)==0,"source strict comparison succeeds at decimal .1 below .1f");
   }
  }finally{try(var paths=Files.walk(tmp)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  System.out.println("PASS "+checks+" immutable source/hash and bit-exact independent original Java/Scala float differential assertions");
 }
}
