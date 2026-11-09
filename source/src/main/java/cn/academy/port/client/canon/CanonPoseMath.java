package cn.academy.port.client.canon;

/** Cosmetic server-confirmed poses: no game state, input or damage authority. */
public final class CanonPoseMath {
    public record Pose(double rightX,double rightY,double rightZ,double leftX,double leftY,double leftZ,double lean,double handX,double handY,double handZ) {}
    private static final Pose NONE=new Pose(0,0,0,0,0,0,0,0,0,0);
    private CanonPoseMath() {}
    public static Pose sample(String category,String skill,double age,int duration,boolean rightHand){
        return sample(category,skill,age,duration,rightHand,false);
    }
    public static Pose sample(String category,String skill,double age,int duration,boolean rightHand,boolean impact){
        if(category==null||skill==null||duration<1||!Double.isFinite(age)||age<0||age>=duration)return NONE;
        double rise=Math.min(1,(age+1)/3),fall=Math.min(1,(duration-age)/4),strength=rise*fall;
        Pose target=cn.academy.port.canon.CanonOffenseSkills.find(skill)!=null?offense(category,skill,age/duration,impact):switch(category){
            case "mental_out" -> skill.contains("crowd")||skill.contains("conflict") ? p(-1.25,-.18,.25,-1.12,.22,-.22,.02,.10,.03,-.08)
                    : p(-1.42,-.22,.12,-.35,.12,-.16,.02,.08,.05,-.09);
            case "dark_matter" -> skill.contains("blade")||skill.contains("strike") ? p(-1.38,-.20,.20,-.48,.14,-.22,.16,.15,-.04,-.16)
                    : p(-.85,-.3,1.0,-.85,.3,-1.0,-.08,.13,.03,-.1);
            case "nitrogen_armor" -> skill.contains("punch")||skill.contains("shove") ? p(-1.50,-.12,.08,-.85,.15,-.30,.22,.16,-.04,-.2)
                    : p(-1.12,-.32,.36,-1.12,.32,-.36,.05,.07,.02,-.1);
            case "aero_hand" -> skill.contains("barrage")||skill.contains("push") ? p(-1.55,-.34,.20,-1.50,.34,-.20,.18,.16,.02,-.16)
                    : p(-.65,-.20,.62,-.65,.20,-.62,.23,.10,-.03,-.12);
            case "hydro_hand" -> skill.contains("whip")||skill.contains("transfer") ? p(-1.44,-.18,.18,-.80,.38,-.52,.10,.15,.03,-.14)
                    : p(-1.04,-.46,.35,-1.04,.46,-.35,.05,.08,.04,-.1);
            case "float_dial" -> p(-1.48,-.12,.30,-1.48,.12,-.30,-.06,.08,.10,-.1);
            case "liquid_shadow" -> p(-.84,-.50,.10,-.40,.10,-.32,.12,.09,-.08,-.08);
            case "paper_psychokinesis" -> skill.contains("blade")||skill.contains("array") ? p(-1.35,-.42,.12,-.78,.35,-.34,.10,.16,.04,-.14)
                    : p(-1.12,-.28,.44,-1.12,.28,-.44,.04,.08,.02,-.1);
            default -> NONE;
        };
        if(!rightHand)target=new Pose(target.leftX,-target.leftY,-target.leftZ,target.rightX,-target.rightY,-target.rightZ,
                target.lean,-target.handX,target.handY,target.handZ);
        return scale(target,strength);
    }
    private static Pose offense(String category,String skill,double time,boolean impact){
        double burst=impact?Math.pow(1-Math.min(1,time),.6):Math.sin(Math.min(1,time)*Math.PI*.5);
        var spec=cn.academy.port.canon.CanonOffenseSkills.find(skill);double tier=spec.level();
        return switch(category){
            case "mental_out"->tier>=4?p(-1.22-.22*burst,-.26,.25,-1.16-.22*burst,.26,-.25,-.03,.10,.08,-.12)
                    :p(impact?-1.48:-1.15,-.27,impact?.12:-.36,-.50,.16,-.17,.02,.13,.08,-.14);
            case "float_dial"->{boolean down=skill.contains("drop")||skill.contains("collapse");
                double angle=impact&&down?-.42-1.15*burst:-.90-.55*burst;
                yield p(angle,-.22,.42,angle,.22,-.42,down?.12:-.06,.07,down?-.08:.12,-.12);}
            case "paper_psychokinesis"->skill.contains("lance")?p(-1.20-.35*burst,-.05,.08,-.65,.28,-.45,.18,.19,-.01,-.22)
                    :skill.contains("wheel")?p(-1.05-.20*burst,-.6+burst*.5,.35,-.95,.55-burst*.5,-.35,.12,.15,.03,-.15)
                    :p(-1.20,-.48+burst*.25,.58,-.85,.45,-.45,.07,.14,.07,-.13);
            case "liquid_shadow"->p(-.62-.62*burst,-.45,.20,-.45-(tier>1?.4:0),.28,-.28,.13,.12,-.10,-.12);
            default->p(-.72-.82*burst,-.17,.13,-.65,.22,-.38,.17,.19,-.02,-.22);
        };
    }
    private static Pose p(double rx,double ry,double rz,double lx,double ly,double lz,double lean,double hx,double hy,double hz){return new Pose(rx,ry,rz,lx,ly,lz,lean,hx,hy,hz);}
    private static Pose scale(Pose p,double v){return new Pose(p.rightX*v,p.rightY*v,p.rightZ*v,p.leftX*v,p.leftY*v,p.leftZ*v,p.lean*v,p.handX*v,p.handY*v,p.handZ*v);}
    public static double envelope(double age,int duration){return !Double.isFinite(age)||age<0||duration<1||age>=duration?0:Math.min(1,(age+1)/3)*Math.min(1,(duration-age)/4);}
}
