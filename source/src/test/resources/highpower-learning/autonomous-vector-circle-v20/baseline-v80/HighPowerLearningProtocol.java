package cn.academy.port.highpower;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Correlates an early developer rejection with the exact open high-power learning attempt. */
public final class HighPowerLearningProtocol {
    public static final String KIND="highpower_learning_result";
    public record Request(String skill,int sequence) {
        public String wire(){return wireSkill(skill)+":~"+Integer.toString(sequence,36);}
    }
    private HighPowerLearningProtocol(){}
    // NEW132's full20-character ID would make UUID + max sequence65 characters. Only its
    // wire name is compacted; registry, prerequisite, feedback and saved IDs remain unchanged.
    // NEW133 magnetic_item_pull already fits UUID + max sequence in63 characters.
    // NEW231 likewise uses ds only on the bounded machine wire; all saved IDs remain canonical.
    // NEW311 also needs a compact machine wire; saved identity remains displaced_projection.
    // NEW433 uses cl only on the machine wire; its canonical saved skill ID is unchanged.
    // NEW442 uses mar so UUID plus maximum sequence still fits the native64-character machine value.
    // NEW253 uses rsr only on the machine wire; saved identity remains remote_scatter_relay.
    private static String wireSkill(String skill){return HighPowerSkills.SCATTER_LOCATOR_PLATE.equals(skill)?"slp":HighPowerSkills.REMOTE_SCATTER_RELAY.equals(skill)?"rsr":HighPowerSkills.WIND_PRESSURE_LIFT.equals(skill)?"wpl":HighPowerSkills.MULTI_AXIS_REFLECTION.equals(skill)?"mar":HighPowerSkills.CIRCULATION_LISTENING.equals(skill)?"cl":HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY.equals(skill)?"mla":HighPowerSkills.SPATIAL_PINCER_CORRIDOR.equals(skill)?"spc":HighPowerSkills.TACTICAL_COORDINATE_FORMATION.equals(skill)?"tcf":HighPowerSkills.DISPLACED_PROJECTION.equals(skill)?"dp":HighPowerSkills.DISSIPATION_CURTAIN.equals(skill)?"dc":HighPowerSkills.MAGNETIC_GROUND_STEP.equals(skill)?"mgs":HighPowerSkills.DISINTEGRATION_SWEEP.equals(skill)?"ds":skill;}
    private static String canonicalSkill(String wire){return "slp".equals(wire)?HighPowerSkills.SCATTER_LOCATOR_PLATE:"rsr".equals(wire)?HighPowerSkills.REMOTE_SCATTER_RELAY:"wpl".equals(wire)?HighPowerSkills.WIND_PRESSURE_LIFT:"mar".equals(wire)?HighPowerSkills.MULTI_AXIS_REFLECTION:"cl".equals(wire)?HighPowerSkills.CIRCULATION_LISTENING:"mla".equals(wire)?HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY:"spc".equals(wire)?HighPowerSkills.SPATIAL_PINCER_CORRIDOR:"tcf".equals(wire)?HighPowerSkills.TACTICAL_COORDINATE_FORMATION:"dp".equals(wire)?HighPowerSkills.DISPLACED_PROJECTION:"dc".equals(wire)?HighPowerSkills.DISSIPATION_CURTAIN:"mgs".equals(wire)?HighPowerSkills.MAGNETIC_GROUND_STEP:"ds".equals(wire)?HighPowerSkills.DISINTEGRATION_SWEEP:wire;}
    public static Request parse(String wire){
        if(wire==null||wire.length()>64)return null;
        String[] parts=wire.split(":",-1);if(parts.length!=2)return null;
        String skill=canonicalSkill(parts[0]);if(!HighPowerSkills.contains(skill))return null;
        try{
            boolean compact=parts[1].startsWith("~");
            int sequence=Integer.parseInt(compact?parts[1].substring(1):parts[1],compact?36:10);
            var request=new Request(skill,sequence);
            String canonical=compact?request.wire():request.skill()+":"+Integer.toString(sequence);
            return sequence>0&&canonical.equals(wire)?request:null;
        }catch(NumberFormatException invalid){return null;}
    }
    public static CompoundTag result(Request request,boolean accepted,String reason){
        var tag=new CompoundTag();tag.putString("kind",KIND);tag.putString("skill",request.skill());
        tag.putInt("request",request.sequence());tag.putBoolean("accepted",accepted);
        tag.putString("reason",reason==null?"":reason.substring(0,Math.min(512,reason.length())));return tag;
    }
    /** One-shot feedback state. A response for a closed, retried or different skill cannot consume it. */
    public static final class Pending {
        private Request request;
        private boolean waiting,accepted;
        private String reason="";
        public void begin(Request request){if(request==null||parse(request.wire())==null)throw new IllegalArgumentException("Valid learning request required");this.request=request;waiting=true;accepted=false;reason="";}
        public boolean receive(CompoundTag result){
            if(!waiting||request==null||result==null||!result.getString("kind").equals(KIND)
                    ||!result.contains("request",Tag.TAG_INT)||result.getInt("request")!=request.sequence()
                    ||!result.getString("skill").equals(request.skill())||!result.contains("accepted",Tag.TAG_BYTE)
                    ||!result.contains("reason",Tag.TAG_STRING)||result.getString("reason").length()>512)return false;
            waiting=false;accepted=result.getBoolean("accepted");reason=result.getString("reason");return true;
        }
        public boolean waiting(){return waiting;}
        public boolean accepted(){return accepted;}
        public String reason(){return reason;}
        public void cancel(){waiting=false;}
    }
}
