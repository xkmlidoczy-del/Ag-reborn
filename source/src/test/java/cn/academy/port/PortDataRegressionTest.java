package cn.academy.port;
import cn.academy.port.core.*;
import net.minecraft.nbt.CompoundTag;
public final class PortDataRegressionTest {
    private static int assertions;
    private static void check(boolean value,String message) {assertions++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        check(SkillCatalog.ALL.size()==50,"50 classic entries");
        var active = SkillCatalog.REGISTERED.stream().filter(SkillCatalog.Skill::controllable)
                .map(SkillCatalog.Skill::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        check(SkillTuning.skills().equals(active), "packaged tuning rows match registered active skills");
        String[] categories={"electromaster","meltdowner","teleporter","vecmanip"};int[][] counts={{2,2,2,2,1},{1,2,2,3,2},{1,2,2,1,1},{2,2,2,2,1}};
        for(int c=0;c<4;c++)for(int level=1;level<=5;level++){var s=new AbilityProgress();s.selectCategory(categories[c]);s.setLevel(level);check(SkillCatalog.levelSkillCount(s)==counts[c][level-1],"count "+categories[c]+level);}
        var s=new AbilityProgress();s.selectCategory("electromaster");s.setLevel(4);var rail=SkillCatalog.find(s.category,"railgun").orElseThrow();check(!SkillCatalog.canLearn(s,rail),"missing dependencies blocked");
        s.experience.put("thunder_bolt",.3);s.experience.put("mag_manip",.999);check(!SkillCatalog.canLearn(s,rail),"mag_manip below exact threshold");s.experience.put("mag_manip",1.0);check(SkillCatalog.canLearn(s,rail),"exact dependencies accepted");s.setLevel(3);check(!SkillCatalog.canLearn(s,rail),"level below threshold blocked");
        s.setLevel(4);s.learn("railgun");check(!SkillCatalog.canLearn(s,rail),"already learned rejected");s.cp=5100;s.overload=51;s.extraCp=50;s.extraOverload=6;s.cpDelay=9;s.overloadDelay=17;s.cooldowns.put("railgun",240);s.activated=true;s.levelExperience=1.25;
        var restored=AbilityStorage.decode(AbilityStorage.encode(s));check(restored.category.equals(s.category),"category roundtrip");check(restored.level==4,"level roundtrip");check(restored.cp==5100&&restored.overload==51,"resources roundtrip");check(restored.extraCp==50&&restored.extraOverload==6,"training roundtrip");check(restored.cpDelay==9&&restored.overloadDelay==17,"recovery delays roundtrip");check(restored.cooldowns.get("railgun")==240,"cooldown roundtrip");check(restored.experience.equals(s.experience),"mastery roundtrip");check(restored.levelExperience==1.25&&restored.activated,"progress/activation roundtrip");
        var malformed=AbilityStorage.encode(s);malformed.putString("category","bad_category");malformed.putInt("level",999);malformed.putDouble("cp",Double.NaN);malformed.putDouble("overload",Double.POSITIVE_INFINITY);malformed.getCompound("skills").putDouble("unknown",1);var clean=AbilityStorage.decode(malformed);check(clean.category.isEmpty(),"unknown category rejected");check(clean.level==5&&clean.cp==0&&clean.overload==0,"corrupt numeric fields sanitized");check(clean.experience.isEmpty(),"unknown category removes skills");
        check(!AbilityStorage.decode(new CompoundTag()).hasCategory(),"fresh player empty");
        var brain=SkillCatalog.find("electromaster","brain_course").orElseThrow();var passiveState=new AbilityProgress();passiveState.selectCategory("electromaster");passiveState.setLevel(3);check(!SkillCatalog.canLearn(passiveState,brain),"brain course needs a learned L3 skill");passiveState.learn("body_intensify");check(SkillCatalog.canLearn(passiveState,brain),"learned L3 unlocks brain course");
        System.out.println("PASS "+assertions+" catalog/persistence regression assertions");
    }
}
