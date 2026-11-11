package cn.academy.port.macro;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.SkillTuning;
import java.util.*;

/** Behavioral queue, catalog and geometry contracts; not a duplicate of renderer implementation. */
public final class MacroRulesRegressionTest {
    public static void main(String[] args){
        var active=SkillCatalog.REGISTERED.stream().filter(SkillCatalog.Skill::controllable).toList();
        require(active.size()==306,"active baseline inventory");
        require(active.size()==MacroSkillRules.profiles().size(),"every active skill has an authored profile");
        for(var skill:active){var p=MacroSkillRules.find(skill.id());require(p!=null&&p.level()==skill.level()&&p.category().equals(skill.category()),"correct profile "+skill.id());}
        require(active.stream().filter(s->s.level()==5).count()==56,"all 56 intrinsic LV5 skills");
        require(!MacroSkillRules.ultimate("meltdowner"),"LV3 Meltdowner stays LV3");
        for(String name:new String[]{"streak","glow","macro/plasma_body","macro/thunder_main","macro/thunder_arc","macro/thunder_ripple","macro/md_blend_in","macro/md_tile","macro/md_blend_out","macro/beam_tile"})
            require(MacroRulesRegressionTest.class.getResource("/assets/academy/textures/canon_fx/"+name+".png")!=null,"packaged material exists: "+name);
        for(var p:MacroSkillRules.profiles())require(MacroRulesRegressionTest.class.getResource("/assets/academy/textures/canon_fx/macro/"+p.category()+".png")!=null,"category texture exists "+p.category());
        require(MacroSkillRules.area("float_field",0,48)==32&&MacroSkillRules.area("float_field",1,48)==48,"ordinary field growth");
        require(MacroSkillRules.area("float_field",.5,48)==40,"continuous mastery range");
        require(MacroSkillRules.area("float_field",0,80)==80,"larger existing areas never shrink");
        require(SkillTuning.integer("mental_disrupt","duration_ticks")==800,"mental duration x10 preserved");
        var q=new FairWorkQueue();var got=new ArrayList<Integer>();var order=new ArrayList<Integer>();
        for(int k=0;k<3;k++){var jobs=new ArrayList<Runnable>();final int group=k;
            for(int i=0;i<100;i++){final int target=k*100+i;jobs.add(()->{got.add(target);order.add(group);});}q.add(jobs,()->true,()->{});}
        q.drain(10,64);require(got.size()==64,"global 64 target limit");q.drain(10,64);require(got.size()==64,"same tick cannot reset budget");
        require(new HashSet<>(order.subList(0,6)).size()==3,"round-robin fairness across casts");
        for(int tick=11;tick<20;tick++)q.drain(tick,64);
        require(got.size()==300&&new HashSet<>(got).size()==300&&q.pending()==0,"no target tail is truncated or duplicated");
        q.add(List.of(()->{throw new AssertionError("canceled cast executed");}),()->false,()->got.add(-1));q.drain(20,64);require(got.getLast()==-1,"cancellation releases completion fences");
        for(int i=0;i<64;i++)require(q.reserve(21,64),"native permit reservation");require(!q.reserve(21,64),"native and queued work share budget");
        System.out.println("PASS macro catalog / mastery / mental time / 300 exact targets / cancellation / shared native budget");
    }
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
