package cn.academy.port.skill;
import static cn.academy.port.core.ClassicFloatLedgerExpectations.*;

import cn.academy.port.core.AbilityProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Independent stock-source differential oracle; no game or client bootstrap. */
public final class MineDetectRegressionTest {
    private static int assertions;
    private static void check(boolean yes,String label){assertions++;if(!yes)throw new AssertionError(label);}
    private static void same(double actual,double expected,String label){check(Double.doubleToLongBits(actual)==Double.doubleToLongBits(expected),label+": "+actual+" != "+expected);}
    private static AbilityProgress ready(double e,int level){var s=new AbilityProgress();s.selectCategory("electromaster");s.setLevel(level);s.learn("mine_detect");s.experience.put("mine_detect",e);s.activated=true;return s;}
    public static void main(String[] args) {
        var random=new Random(170710211252L);
        for(int i=0;i<20000;i++) {
            float old=i<5?new float[]{0,.5F,Math.nextUp(.5F),.999F,1}[i]:random.nextFloat();
            var c=MineDetectRules.cost(old);float cp=1500F+old*(1000F-1500F),o=200F+old*(180F-200F),range=(float)cn.academy.port.core.SkillTuning.number("mine_detect", "reach_novice")+old*((float)cn.academy.port.core.SkillTuning.number("mine_detect", "reach_mastered")-(float)cn.academy.port.core.SkillTuning.number("mine_detect", "reach_novice"));
            float awarded=Math.min(1F-old,.008F);float after=old+awarded;
            same(c.cp(),(double)cp,"float CP differential");same(c.overload(),(double)o,"float overload differential");
            same(MineDetectRules.range(old),(double)range,"captured float range differential");
            var s=ready(old,4);double beforeCp=s.cp;var plan=MineDetectRules.prepare(s,false);
            check(plan!=null,"stock cast accepted");same(s.cp,paidCpAfter(beforeCp,cp,1),"one exact float consumption");
            check(plan.advanced()==(old>.5F),"strict captured advanced classification");
            MineDetectRules.complete(s,plan);same(s.exp("mine_detect"),(double)after,"source Float cap/add differential");
            same(s.levelExperience,(double).008F,"source float amount widens for shared level EXP storage");
            check(s.cooldowns.get("mine_detect")== (int)(900F+after*(400F-900F)),"post-award truncating cooldown differential");
            check(MineDetectRules.prepare(s,false)==null,"cooldown replay rejected");
        }
        var novice=ready(0,3);var plan=MineDetectRules.prepare(novice,false);MineDetectRules.complete(novice,plan);
        check(novice.cooldowns.get("mine_detect")==896,"novice cooldown uses +.008 not original900");
        var threshold=ready(.5,4);var thresholdPlan=MineDetectRules.prepare(threshold,false);MineDetectRules.complete(threshold,thresholdPlan);
        check(!thresholdPlan.advanced()&&threshold.exp("mine_detect")>.5,"same cast does not upgrade effect after award crosses threshold");
        check(!MineDetectRules.advanced(Math.nextUp(.5F),3),"level3 blocks advanced mode despite mastery");
        check(!MineDetectRules.advanced(Math.nextUp(.5),4),"double above boundary that rounds to float.5 is not advanced");
        check(MineDetectRules.advanced(Math.nextUp(.5F),4),"next representable float is advanced");
        for(int mode=0;mode<7;mode++) {
            var s=ready(0,3);if(mode==0)s.cp=1499;if(mode==1)s.activated=false;if(mode==2)s.interfering=true;
            if(mode==3)s.overloadFine=false;if(mode==4)s.category="meltdowner";if(mode==5)s.level=2;if(mode==6)s.experience.clear();
            double cp=s.cp,o=s.overload;check(MineDetectRules.prepare(s,false)==null,"failed gate "+mode);same(s.cp,cp,"failed CP unchanged");same(s.overload,o,"failed overload unchanged");check(s.cooldowns.isEmpty(),"failed no cooldown");
        }
        var creative=ready(1,4);creative.cp=0;check(MineDetectRules.prepare(creative,true)!=null,"creative can cast at CP0");same(creative.cp,0,"creative consumes no CP");same(creative.overload,0,"creative consumes no overload");
        for(String tag:new String[]{"ores","ores/iron","ores_in_ground/stone","coal_ores","deepslate_ores"})check(MineOreTagRules.semanticOrePath(tag),"semantic ore-family tag bridge");
        for(String tag:new String[]{"stone_ore_replaceables","deepslate_ore_replaceables","more_blocks","mineable/pickaxe","storage_blocks/iron"})check(!MineOreTagRules.semanticOrePath(tag),"worldgen/non-ore tags never imply OreDictionary ore identity");
        same(ClassicMineScan.range(100),cn.academy.port.core.SkillTuning.number("mine_detect", "reach_mastered"),"preview clamps to the configured mastered reach");
        for(int h=-1;h<=5;h++){check(ClassicMineScan.colorLevel(true,h)==Math.min(3,h+1),"harvest+1 cap3");check(ClassicMineScan.colorLevel(false,h)==0,"novice default color");}
        var probe=new Probe();var small=ClassicMineScan.scan(.25,.5,-.25,1,true,probe);
        var oracle=oracleScan(.25,.5,-.25,1,true,1000);check(small.equals(oracle),"fractional floor/ceil inclusive scan/sphere/order differential");
        check(probe.visited.getFirst().equals("-1,-1,-2")&&probe.visited.getLast().equals("2,2,1"),"full inclusive candidate cube and X/Y/Z order");
        check(probe.visited.size()==64,"selector queried outside sphere before distance filtering");
        var noviceAccess=new ClassicMineScan.OreAccess(){public boolean accepts(int x,int y,int z){return true;}public int harvestLevel(int x,int y,int z){throw new AssertionError("novice must not query harvest");}};
        check(ClassicMineScan.scan(0,0,0,1,false,noviceAccess).stream().allMatch(e->e.level()==0),"novice skips harvest entirely");
        final int[] phase={0};var phases=new ClassicMineScan.OreAccess(){public boolean accepts(int x,int y,int z){check(phase[0]==0,"all ore candidate queries precede harvest pass");return true;}public int harvestLevel(int x,int y,int z){phase[0]=1;return 0;}};
        ClassicMineScan.scan(0,0,0,1,true,phases);check(phase[0]==1,"advanced harvest follows complete scan");
        var dense=ClassicMineScan.scan(0,0,0,15,true,new Probe());var expected=oracleScan(0,0,0,15,true,1000);
        check(dense.size()==1000&&dense.equals(expected),"first1000 source traversal matches independent dense oracle");
        var edge=new ClassicMineScan.OreAccess(){public boolean accepts(int x,int y,int z){return y==0&&z==0&&(x==15||x==16);}public int harvestLevel(int x,int y,int z){return 2;}};
        var edges=ClassicMineScan.scan(0,0,0,15,true,edge);check(edges.size()==1&&edges.getFirst().x()==15,"integer block-origin boundary included;ceil cube point outside omitted");
        var constant=new ClassicMineScan.OreAccess(){public boolean accepts(int x,int y,int z){return x==0&&y==0&&z==0;}public int harvestLevel(int x,int y,int z){return 1;}};
        var handler=new ClassicMineScan.Handler(0,0,0,15,true);
        check(handler.aliveSims().isEmpty(),"source constructor does not scan");handler.tick(0,0,0,constant);
        check(handler.refreshes()==1&&handler.aliveSims().size()==1,"source first update scans once");
        handler.tick(3,0,0,constant);check(handler.refreshes()==1,"exact movement threshold does not refresh");
        handler.tick(Math.nextUp(3.0),0,0,constant);check(handler.refreshes()==2&&handler.aliveSims().size()==2,"strict beyond threshold appends duplicate");
        handler.tick(0,0,0,constant);check(handler.aliveSims().size()==3,"source return movement accumulates old records");
        while(handler.age()<MineDetectRules.TIME)check(handler.tick(0,0,0,constant),"alive through configured final tick");
        check(!handler.tick(10,0,0,constant)&&handler.age()==MineDetectRules.TIME + 1,"strict expiry after final refresh");check(handler.refreshes()==4,"expiry refresh precedes death");
        var firstMoved=new ClassicMineScan.Handler(0,0,0,15,false);firstMoved.tick(10,0,0,constant);
        check(firstMoved.refreshes()==2&&firstMoved.aliveSims().size()==2,"first update scans spawn then sufficiently moved target");
        boolean readonly=false;try{firstMoved.aliveSims().clear();}catch(UnsupportedOperationException e){readonly=true;}check(readonly,"accumulation cannot be mutated externally");
        System.out.println("PASS "+assertions+" MineDetect stock-source numeric/scan/state assertions (no game startup)");
    }
    private static List<ClassicMineScan.Element> oracleScan(double a,double b,double c,double r,boolean advanced,int cap) {
        var out=new ArrayList<ClassicMineScan.Element>();
        for(int x=(int)Math.floor(a-r);x<=(int)Math.ceil(a+r);x++)for(int y=(int)Math.floor(b-r);y<=(int)Math.ceil(b+r);y++)for(int z=(int)Math.floor(c-r);z<=(int)Math.ceil(c+r);z++){
            double dx=x-a,dy=y-b,dz=z-c;if(dx*dx+dy*dy+dz*dz<=r*r){out.add(new ClassicMineScan.Element(x,y,z,advanced?Math.min(3,Math.floorMod(x+y+z,5)):0));if(out.size()==cap)return out;}
        }return out;
    }
    private static final class Probe implements ClassicMineScan.OreAccess {
        final List<String> visited=new ArrayList<>();public boolean accepts(int x,int y,int z){visited.add(x+","+y+","+z);return true;}
        public int harvestLevel(int x,int y,int z){return Math.floorMod(x+y+z,5)-1;}
    }
}
