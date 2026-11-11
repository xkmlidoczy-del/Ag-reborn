package cn.academy.port.client.canon;

import cn.academy.port.canon.CanonVfxDesign;
import cn.academy.port.macro.MacroSkillRules;

/** Presentation forms are selected from each skill's existing functional motif, never its damage. */
final class AgEffectDesign {
    static final int FIELD=0,SHELL=1,WINGS=2,LIFT=3,FOCUS=4,SCAN=5;
    private AgEffectDesign(){}
    static int form(MacroSkillRules.Profile p){
        var design=CanonVfxDesign.find(p.skill());
        if(design!=null)return switch(design.motif()){
            case DARK_WINGS,PAPER_WING -> WINGS;
            case NITROGEN_SHELL,NITROGEN_DOME,PAPER_ARMOR,WATER_SHIELD -> SHELL;
            case FLOAT_LIFT,FLOAT_RESCUE,AERO_JET,AERO_LAUNCH -> LIFT;
            case REMOTE,LIQUID_SCOUT -> SCAN;
            case DARK_BLADE,PAPER_BLADE,NITROGEN_PUNCH,LIQUID_STRIKE,WATER_WHIP,WATER_TRANSFER -> FOCUS;
            default -> FIELD;
        };
        return switch(p.skill()){
            case "zero_gap_armor","light_shield","vec_deviation","vec_reflection","particle_laminate","directional_dissipation_curtain","vector_construct" -> SHELL;
            case "storm_wing" -> WINGS;
            case "mag_movement","magnetic_ground_step","wind_pressure_lift","safe_landing_probe" -> LIFT;
            default -> p.shape()==MacroSkillRules.Shape.POINT?FOCUS:p.shape()==MacroSkillRules.Shape.SELF?SHELL:FIELD;
        };
    }
    static float height(MacroSkillRules.Profile p,float radius){
        if(p.skill().equals("plasma_cannon"))return radius*1.65f;
        if(p.skill().equals("thunder_clap"))return Math.max(48,radius*1.5f);
        return switch(form(p)){
            case SHELL -> Math.min(28,radius*.8f);
            case WINGS -> Math.min(24,radius*.6f);
            case LIFT -> Math.min(36,radius*1.2f);
            case FOCUS,SCAN -> Math.min(14,radius*.6f);
            default -> Math.min(40,radius*.85f);
        };
    }
}
