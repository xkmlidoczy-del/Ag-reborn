/* Differential against independent classic float transcription and immutable source bytes. */
package cn.academy.port.core;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import com.google.gson.*;
public final class SharedSkillConsumptionSourceOracleTest {
    private static long checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static void floatNear(double actual,float expected,int ulps,String reason){check(Double.doubleToRawLongBits(actual)==Double.doubleToRawLongBits((double)expected),reason+" actual="+actual+" expected="+expected);}
    private static byte[] resource(String path)throws Exception{try(var stream=SharedSkillConsumptionSourceOracleTest.class.getResourceAsStream("/"+path)){if(stream==null)throw new IllegalArgumentException("Missing canonical resource "+path);return stream.readAllBytes();}}
    public static void main(String[] args)throws Exception{
        String prefix="classic-oracles/shared-skill-consumption/";
        var hashes=JsonParser.parseString(new String(resource(prefix+"source-manifest.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        check(hashes.get("academyCommit").getAsString().equals("00d19ec0cf538f61c1095c9292f5ee6863db4521"),"pinned canonical commit contract");
        for(var element:hashes.getAsJsonArray("files")){var file=element.getAsJsonObject();byte[] bytes=resource(file.get("resource").getAsString());String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));check(hash.equals(file.get("sha256").getAsString()),"immutable reference hash "+file.get("fixture"));}
        String cp=new String(resource(prefix+"academycraft/src/main/java/cn/academy/ability/api/data/CPData.java.txt"),java.nio.charset.StandardCharsets.UTF_8);
        check(cp.contains("MinecraftForge.EVENT_BUS.post(evt)")&&cp.contains("addMaxCP(cp)")&&cp.contains("addMaxOverload(overload)"),"oracle grounded in actual source event/training ingress");
        String skill=new String(resource(prefix+"academycraft/src/main/java/cn/academy/ability/api/Skill.java.txt"),java.nio.charset.StandardCharsets.UTF_8);
        check(skill.contains("cfg.hasPath(path) ? (float) cfg.getDouble(\"damage_scale\") : fallback"),"oracle asserts exact optional getter bug");
        var random=new Random(0x00d19ec0L);boolean foundRoundingDifference=false;
        for(int i=0;i<50000;i++){
            var oracle=new ClassicFloatConsumptionOracle();var s=new AbilityProgress();s.selectCategory("teleporter");s.setLevel(5);s.learn("mark_teleport");s.activated=true;
            s.cp=oracle.cp=random.nextFloat()*12000;s.overload=oracle.overload=random.nextFloat()*700;s.extraCp=oracle.extraCp=random.nextFloat()*6000;s.extraOverload=oracle.extraOverload=random.nextFloat()*100;
            float requestedCp=random.nextFloat()*1000,requestedOverload=random.nextFloat()*500,mutatedCp=random.nextFloat()*1000,mutatedOverload=random.nextFloat()*500;
            boolean force=random.nextBoolean(),creative=random.nextInt(8)==0,mutate=random.nextBoolean();var modernOverload=new int[1];var classicOverload=new int[1];
            s.bindConsumption(()->SkillConsumption.Config.DEFAULT,e->{if(mutate){e.cp=mutatedCp;e.overload=mutatedOverload;}},()->modernOverload[0]++,()->true);
            boolean expected=oracle.perform(requestedCp,requestedOverload,force,creative,e->{if(mutate){e.cp=mutatedCp;e.overload=mutatedOverload;}},()->classicOverload[0]++);
            boolean actual=force?s.consumeWithForceSkill("mark_teleport",requestedCp,requestedOverload,creative):s.consumeSkill("mark_teleport",requestedCp,requestedOverload,creative);
            check(actual==expected,"classic success branch");floatNear(s.cp,oracle.cp,1,"classic CP rounded resource");floatNear(s.overload,oracle.overload,1,"classic overload rounded cap");floatNear(s.extraCp,oracle.extraCp,3,"classic CP capacity float bound");floatNear(s.extraOverload,oracle.extraOverload,3,"classic O capacity float bound");check(s.cpDelay==oracle.cpDelay&&s.overloadDelay==oracle.overloadDelay&&s.overloadFine==oracle.fine,"classic delays/lock");check(modernOverload[0]==classicOverload[0],"classic overload callbacks");
            foundRoundingDifference|=s.cp!=(double)oracle.cp||s.extraCp!=(double)oracle.extraCp;
        }
        check(!foundRoundingDifference,"classic consumption has no double-ledger/legacy-float rounding divergence");
        // Unsafe arithmetic exists in the original, and is deliberately bounded by the modern ingress.
        var classic=new ClassicFloatConsumptionOracle();classic.cp=10;check(classic.perform(-5,-2,false,false,e->{},()->{})&&classic.cp==15&&classic.extraCp<0&&classic.overload<0,"source negative refund/training quirk documented");
        var modern=new AbilityProgress();modern.cp=10;check(!modern.consume(-5,-2,false)&&modern.cp==10,"modern existing finite nonnegative guard retained");
        classic=new ClassicFloatConsumptionOracle();check(classic.perform(Float.NaN,0,false,false,e->{},()->{})&&Float.isNaN(classic.cp),"source NaN poisoning quirk documented");check(!modern.consume(Double.NaN,0,false)&&modern.cp==10,"modern poisoning protection retained");
        System.out.println("PASS "+checks+" pinned source/hash and independent classic float differential assertions");
    }
}
