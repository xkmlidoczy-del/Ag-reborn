package cn.academy.port.client.canon;

import java.util.HashSet;
import java.util.List;

/** Deterministic pose envelope and independent eight-category posture checks. */
public final class CanonPoseMathTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var categories=List.of("mental_out","dark_matter","nitrogen_armor","aero_hand","hydro_hand","float_dial","liquid_shadow","paper_psychokinesis");
        var poses=new HashSet<CanonPoseMath.Pose>();
        for(String category:categories){
            var pose=CanonPoseMath.sample(category,"fixture",5,12,true);poses.add(pose);
            check(CanonPoseMath.envelope(5,12)==1,"full strength "+category);
            check(CanonPoseMath.envelope(0,12)<1&&CanonPoseMath.envelope(11,12)<1,"bounded fade "+category);
            check(CanonPoseMath.envelope(12,12)==0,"expiry "+category);
            check(CanonPoseMath.sample(category,"fixture",12,12,true).equals(CanonPoseMath.sample("unknown","fixture",5,12,true)),"zero after expiry "+category);
            var left=CanonPoseMath.sample(category,"fixture",5,12,false);
            check(left.rightX()==pose.leftX()&&left.leftX()==pose.rightX(),"handedness swaps arms "+category);
        }
        check(poses.size()==8,"eight categories have distinct native poses");
        check(CanonPoseMath.envelope(Double.NaN,10)==0,"non-finite age refused");
        check(CanonPoseMath.envelope(0,0)==0,"zero duration refused");
        check(!CanonPoseMath.sample("hydro_hand","hydro_whip",5,12,true).equals(CanonPoseMath.sample("hydro_hand","hydro_shield",5,12,true)),"whip and shield differ");
        check(!CanonPoseMath.sample("dark_matter","dark_blade",5,12,true).equals(CanonPoseMath.sample("dark_matter","dark_wings",5,12,true)),"blade and wings differ");
        for(var skill:cn.academy.port.canon.CanonOffenseSkills.ALL){
            var prep=CanonPoseMath.sample(skill.category(),skill.id(),5,18,true,false);
            var release=CanonPoseMath.sample(skill.category(),skill.id(),5,18,true,true);
            check(!prep.equals(release),"accepted wind-up and confirmed release differ "+skill.id());
            for(boolean impact:new boolean[]{false,true})for(int tick=0;tick<=18;tick++){
                var p=CanonPoseMath.sample(skill.category(),skill.id(),tick,18,true,impact);
                var l=CanonPoseMath.sample(skill.category(),skill.id(),tick,18,false,impact);
                check(l.rightX()==p.leftX()&&l.leftX()==p.rightX()&&l.handX()==-p.handX(),"dominant-hand mirror "+skill.id());
                for(double v:new double[]{p.rightX(),p.rightY(),p.rightZ(),p.leftX(),p.leftY(),p.leftZ(),p.handX(),p.handY(),p.handZ()})
                    check(Double.isFinite(v)&&Math.abs(v)<2,"finite bounded action "+skill.id());
            }
            check(CanonPoseMath.sample(skill.category(),skill.id(),18,18,true,true).equals(CanonPoseMath.sample("unknown","fixture",5,18,true)),"new action restores at lease end "+skill.id());
        }
        System.out.println("PASS canon native pose math: "+checks+" checks, 8 distinct category poses");
    }
}
