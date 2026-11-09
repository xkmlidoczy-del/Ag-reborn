package cn.academy.port.canon;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.SkillTuning;
import java.util.List;

/** Concrete offensive branches; existing growth, damage factors and resource curves are unchanged. */
public final class CanonOffenseSkills {
    public enum Shape { POINT, CONE, LINE, AREA, AIR_POINT, BODY_POINT, BODY_SWEEP }
    public record Spec(String category,String id,int level,String parent,Shape shape,double cp,int cooldown) {
        public double reach(){return SkillTuning.number(id,"reach");}
        public double radius(){return SkillTuning.number(id,"radius");}
        public int targets(){return SkillTuning.integer(id,"target_cap");}
        public int warmup(){return SkillTuning.integer(id,"warmup_ticks");}
        public boolean body(){return shape==Shape.BODY_POINT||shape==Shape.BODY_SWEEP;}
    }
    public static final List<Spec> ALL=List.of(
        s("mental_out","mental_neural_sting",1,"mental_probe",Shape.POINT,1,30),
        s("mental_out","mental_synaptic_burst",2,"mental_neural_sting",Shape.CONE,2,50),
        s("mental_out","mental_feedback_spike",3,"mental_synaptic_burst",Shape.POINT,2,60),
        s("mental_out","mental_cortical_wave",4,"mental_feedback_spike",Shape.AREA,3,90),
        s("mental_out","mental_resonance_cascade",5,"mental_cortical_wave",Shape.AREA,5,140),
        s("float_dial","float_buoyant_strike",1,"float_lift",Shape.POINT,1,30),
        s("float_dial","float_reversal_drop",2,"float_buoyant_strike",Shape.AIR_POINT,1.5,45),
        s("float_dial","float_counterpulse",3,"float_reversal_drop",Shape.CONE,2,60),
        s("float_dial","float_pressure_drop",4,"float_counterpulse",Shape.AREA,3,90),
        s("float_dial","float_collapse_field",5,"float_pressure_drop",Shape.AREA,5,140),
        s("paper_psychokinesis","paper_fold_fan",2,"paper_blade",Shape.CONE,2,45),
        s("paper_psychokinesis","paper_piercing_lance",3,"paper_fold_fan",Shape.LINE,2,60),
        s("paper_psychokinesis","paper_returning_wheel",4,"paper_piercing_lance",Shape.AREA,3,90),
        s("liquid_shadow","liquid_touch_spike",1,"liquid_scout",Shape.BODY_POINT,1,30),
        s("liquid_shadow","liquid_crosscut",2,"liquid_touch_spike",Shape.BODY_SWEEP,2,50),
        s("aero_hand","aero_pressure_bullet",1,"aero_jet",Shape.POINT,1,40));
    private static Spec s(String c,String id,int level,String parent,Shape shape,double cp,int cooldown){return new Spec(c,id,level,parent,shape,cp,cooldown);}
    private CanonOffenseSkills() {}
    public static Spec find(String id){return ALL.stream().filter(s->s.id.equals(id)).findFirst().orElse(null);}
    public static List<SkillCatalog.Skill> skills(){return ALL.stream().map(s->new SkillCatalog.Skill(s.category,s.id,s.level,true,List.of(new SkillCatalog.Requirement(s.parent,0)),0)).toList();}
}
