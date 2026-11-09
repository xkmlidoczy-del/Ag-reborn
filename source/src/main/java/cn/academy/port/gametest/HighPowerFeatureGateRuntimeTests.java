package cn.academy.port.gametest;

import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.highpower.HighPowerSkills;
import java.util.HashMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Graduated default admission and explicit operator-disable behavior preserve existing earned state. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerFeatureGateRuntimeTests {
    private static final java.util.Map<String,net.minecraft.nbt.CompoundTag> OPT_INS=new HashMap<>();
    private static final java.util.Map<String,Boolean> PINNED=new HashMap<>();
    private static final ClassicSkillConfiguration.Key CANDIDATE=ClassicSkillConfiguration.key("electromaster",HighPowerSkills.MAGNETIC_GROUND_STEP);
    /**
     * Historical QA-only opt-in retained for replaying old evidence, never used by current native batch admission.
     * Choosing the live flag alone was not enough: a server-config (re)load re-applies the
     * in-memory values from {@code AcademyConfig.SKILL_FLAGS} and used to flip the candidate off under a running controller,
     * which failed the progression fixtures intermittently. Pinning the in-memory value for the batch makes the opt-in
     * survive such a reload; the live snapshot and the pinned value are both restored in {@link #restore}.
     */
    private static void optIn(String batch){
        OPT_INS.put(batch,SkillCatalog.configurationSnapshot());
        var value=AcademyConfig.SKILL_FLAGS.get(CANDIDATE).enabled();
        PINNED.put(batch,value.get());
        value.set(true);
        var flags=new HashMap<>(SkillCatalog.configuration().liveSnapshot());
        flags.put(CANDIDATE,new ClassicSkillConfiguration.Flags(true,true));
        SkillCatalog.refreshConfiguration(flags);
    }
    private static void restore(String batch){
        var saved=OPT_INS.remove(batch);if(saved!=null)SkillCatalog.applyConfigurationSnapshot(saved);
        var prior=PINNED.remove(batch);
        if(prior!=null)AcademyConfig.SKILL_FLAGS.get(CANDIDATE).enabled().set(prior);
    }
    public static void candidateMotionOptIn(net.minecraft.server.level.ServerLevel level){optIn("motion");}
    public static void candidateMotionRestore(net.minecraft.server.level.ServerLevel level){restore("motion");}
    public static void candidateProgressionOptIn(net.minecraft.server.level.ServerLevel level){optIn("progression");}
    public static void candidateProgressionRestore(net.minecraft.server.level.ServerLevel level){restore("progression");}
    @GameTest(template="highpower_empty",batch="highpower_feature_gate",timeoutTicks=10)
    public static void graduated_ground_step_defaults_on_and_respects_operator_disable_without_erasing_skills(GameTestHelper h) {
        var key=ClassicSkillConfiguration.key("electromaster",HighPowerSkills.MAGNETIC_GROUND_STEP);
        h.assertTrue(AcademyConfig.defaultSkillEnabled(key),"NEW132 is now a normal default-on skill");
        h.assertTrue(AcademyConfig.SKILL_FLAGS.get(key).enabled().getDefault(),"actual generated server config defaults on");
        for(var classic:SkillCatalog.ALL)h.assertTrue(AcademyConfig.defaultSkillEnabled(ClassicSkillConfiguration.key(classic.category(),classic.id())),"classic default unchanged");
        var snapshot=SkillCatalog.configurationSnapshot();
        try {
            var state=new AbilityProgress();state.changeCategoryClassic("electromaster");state.setLevel(3);state.setActivateState(true);
            state.learn(HighPowerSkills.FORKED_ARC);state.learn(HighPowerSkills.POLARITY_TETHER);
            var node=SkillCatalog.find("electromaster",HighPowerSkills.MAGNETIC_GROUND_STEP).orElseThrow();
            h.assertTrue(SkillCatalog.enabled("electromaster",node.id())&&SkillCatalog.canLearn(state,node),"normal world config admits the actual LV3 learned-parent skill with no QA opt-in");
            var flags=new HashMap<>(SkillCatalog.configuration().liveSnapshot());
            flags.put(key,new ClassicSkillConfiguration.Flags(false,true));SkillCatalog.refreshConfiguration(flags);
            h.assertFalse(SkillCatalog.canLearn(state,node),"explicit operator disable still blocks new learning");
            state.learn(HighPowerSkills.MAGNETIC_GROUND_STEP);
            h.assertFalse(HighPowerSkills.eligible(state,HighPowerSkills.MAGNETIC_GROUND_STEP),"saved candidate cannot activate while disabled");
            h.assertTrue(state.learned(HighPowerSkills.MAGNETIC_GROUND_STEP),"disabling never erases earned save data");
            h.assertTrue(HighPowerSkills.eligible(state,HighPowerSkills.FORKED_ARC),"accepted neighboring skill unaffected");
            flags.put(key,new ClassicSkillConfiguration.Flags(true,true));SkillCatalog.refreshConfiguration(flags);
            h.assertTrue(HighPowerSkills.eligible(state,HighPowerSkills.MAGNETIC_GROUND_STEP),"normal operator re-enable restores retained skill");
            h.succeed();
        } finally { SkillCatalog.applyConfigurationSnapshot(snapshot); }
    }
}
