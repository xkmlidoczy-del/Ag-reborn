package proof.charging;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.*;
import java.util.*;

/** One identical driver is compiled against the unmodified M41 API and loaded on both sides. */
public final class ChargingCoreTrace {
    static final StringBuilder OUT = new StringBuilder();
    static int cases;
    static String bits(double value) {
        if (!Double.isFinite(value)) throw new AssertionError("NONFINITE_TRACE");
        return Long.toHexString(Double.doubleToRawLongBits(value));
    }
    static String map(Map<String,Double> values) {
        var result = new TreeMap<String,String>();
        values.forEach((key,value)->result.put(key,bits(value))); return result.toString();
    }
    static String ledger(AbilityProgress s) {
        return "category="+s.category+",level="+s.level+",cp="+bits(s.cp)+",overload="+bits(s.overload)
            +",growthCp="+bits(s.extraCp)+",growthOverload="+bits(s.extraOverload)
            +",baseCp="+bits(s.baseCp())+",baseOverload="+bits(s.baseOverload())
            +",maxCp="+bits(s.maxCp())+",maxOverload="+bits(s.maxOverload())
            +",levelXp="+bits(s.levelExperience)+",skillXp="+map(s.experience)
            +",unlearnedXp="+map(s.unlearnedExperience)+",delays="+s.cpDelay+":"+s.overloadDelay
            +",active="+s.activated+",fine="+s.overloadFine+",interfering="+s.interfering
            +",busy="+s.consumptionInProgress()+",cooldowns="+new TreeMap<>(s.cooldowns)
            +",cooldownMax="+new TreeMap<>(s.cooldownMaxTicks)+",classicCount="+SkillCatalog.levelSkillCount(s);
    }
    static final class Run {
        final String name; final AbilityProgress s=new AbilityProgress();
        CurrentChargingSession session; int seq; boolean nested;
        String callback="none"; Runnable consumeExtra=()->{};
        SkillConsumption.Config config=SkillConsumption.Config.DEFAULT;
        Run(String name,double mastery,int level) {
            this.name=name; cases++;
            s.selectCategory("electromaster");s.setLevel(level);s.learn("charging");
            s.experience.put("charging",mastery);s.activated=true;
            s.bindProgressEvents((id,effects)->{event("learn-before:"+id);effects.run();event("learn-after:"+id);},
                (value,effects)->{event("level-before:"+value);effects.run();event("level-after:"+value);},
                award->{event("xp-changed:"+award.skill()+":"+bits(award.amount()));hook("changed");},
                award->{event("xp-added:"+award.skill()+":"+bits(award.amount()));hook("added");});
            s.bindConsumption(()->config,request->{
                event("perform:"+request.skill+":"+bits(request.cp)+":"+bits(request.overload)
                    +":creative="+request.creative+":force="+request.force);
                hook("perform");consumeExtra.run();
            },()->event("overloaded"),()->true);
        }
        void event(String what) {OUT.append(name).append('\t').append(seq++).append('\t').append(what).append('\t').append(ledger(s)).append('\n');}
        void hook(String where) {
            if(session==null||nested)return;
            if(callback.equals(where+"-end")){session.end();event("callback-end:"+where);}
            if(callback.equals(where+"-reenter")){
                nested=true;
                try {event("callback-tick:"+where+":"+session.tick(new Target(this,"nested",true,true,false),false));}
                finally{nested=false;}
            }
            if(callback.equals(where+"-consume")){
                nested=true;
                try {event("callback-consume:"+s.consumeSkill("charging",.75,.25,false));}
                finally{nested=false;}
            }
        }
        void begin(boolean item,boolean creative){
            event("before-begin:item="+item+":creative="+creative);
            session=CurrentChargingSession.begin(s,item,creative);
            event(session==null?"begin:null":"begin:active="+session.active()+":item="+session.itemMode()
                +":mastery="+bits(session.mastery())+":floor="+bits(session.overloadFloor()));
        }
        void tick(Target target,boolean creative){
            event("before-tick:"+target.name+":creative="+creative);
            var result=session.tick(target,creative);
            event("after-tick:"+result+":sessionActive="+session.active()+":received="+bits(target.received));
        }
    }
    static final class Target implements CurrentChargingSession.Target {
        final Run r; final String name; boolean present,supported,full; double received;
        Target(Run r,String name,boolean present,boolean supported,boolean full){this.r=r;this.name=name;this.present=present;this.supported=supported;this.full=full;}
        public boolean present(){r.event("target-present:"+name+":"+present);r.hook("present");return present;}
        public boolean supported(){r.event("target-supported:"+name+":"+supported);r.hook("supported");return supported;}
        public double charge(double amount,boolean bypass){r.event("target-charge:"+name+":"+bits(amount)+":bypass="+bypass);r.hook("charge");if(!full)received+=amount;return full?amount:0;}
    }
    static void matrix() {
        for(boolean item:new boolean[]{true,false}) for(boolean creative:new boolean[]{false,true})
        for(double mastery:new double[]{0,.1,1.0/3,.5,.999999,1}) for(int support=0;support<4;support++)
        for(int balance=0;balance<4;balance++) {
            String name="matrix/"+item+"/"+creative+"/"+bits(mastery)+"/"+support+"/"+balance;
            var r=new Run(name,mastery,1);
            float cost=CurrentChargingSession.consumption(mastery);
            r.s.cp=switch(balance){case 0->0;case 1->Math.nextDown(cost);case 2->cost;default->1800.125;};
            r.begin(item,creative);
            var t=new Target(r,"hand-A",support!=3,support<2,support==1);
            r.tick(t,creative);r.tick(t,creative);
        }
    }
    static void captureAndRecovery() {
        for(boolean item:new boolean[]{true,false}) {
            var r=new Run("capture-live-target/"+item,.25,1);r.begin(item,false);
            r.tick(new Target(r,"hand-A",true,true,false),false);
            r.s.experience.put("charging",1D);r.s.overload=0;r.event("live-mastery-1-floor-0");
            r.tick(new Target(r,"hand-B",true,true,false),false);
            r.s.overload=90;r.event("external-overload-90");
            r.tick(new Target(r,"unsupported-C",true,false,false),false);
            r.tick(new Target(r,"empty-D",false,false,false),false);
            r.tick(new Target(r,"returned-E",true,true,false),false);
            r.session.end();r.event("explicit-release");r.tick(new Target(r,"released-F",true,true,false),false);
            for(int i=0;i<36;i++){r.s.tick();r.event("recovery:"+i);}
        }
        for(double overload:new double[]{34.9,35,40,99.9}) {
            var r=new Run("start-overload/"+bits(overload),0,1);r.s.overload=overload;r.begin(true,false);
            r.tick(new Target(r,"target",true,true,false),false);
        }
        for(int invalid=0;invalid<7;invalid++) {
            var r=new Run("invalid-after-start/"+invalid,0,1);r.begin(true,false);
            switch(invalid){case 0->r.s.category="meltdowner";case 1->r.s.level=0;case 2->r.s.activated=false;
                case 3->r.s.interfering=true;case 4->r.s.overloadFine=false;case 5->r.s.experience.clear();
                case 6->r.s.cooldowns.put("charging",1);}
            r.tick(new Target(r,"target",true,true,false),false);
        }
    }
    static void callbacksAndConfiguration() {
        for(boolean item:new boolean[]{true,false}) for(String callback:List.of("perform-end","perform-reenter",
                "perform-consume","present-end","supported-end","charge-end","charge-reenter","changed-end",
                "changed-reenter","added-end")) {
            var r=new Run("callback/"+item+"/"+callback,.5,1);r.begin(item,false);r.callback=callback;
            r.tick(new Target(r,"target",true,true,false),false);r.tick(new Target(r,"later",true,true,false),false);
        }
        for(boolean item:new boolean[]{true,false}) for(int mode=0;mode<4;mode++) {
            var r=new Run("live-creative/"+item+"/"+mode,.5,1);r.begin(item,false);r.s.cp=0;
            final boolean live=(mode&1)!=0;r.s.bindCreativeMode(requested->live);
            r.tick(new Target(r,"target",true,true,false),(mode&2)!=0);
        }
        for(boolean item:new boolean[]{true,false}) {
            var r=new Run("live-config/"+item,.5,1);r.begin(item,false);
            r.config=SkillConsumption.Config.DEFAULT.withOptions(Map.of("electromaster.charging.damage_scale",1.5D,
                "electromaster.charging.cp_consume_speed",7D,"electromaster.charging.overload_incr_speed",9D));
            r.tick(new Target(r,"target",true,true,false),false);
        }
        for(boolean item:new boolean[]{true,false}) {
            var r=new Run("event-mutation/"+item,.5,1);r.begin(item,false);
            r.s.bindConsumption(()->r.config,request->{r.event("perform-before-mutation:"+bits(request.cp));request.cp=.125f;request.overload=.75f;r.event("perform-after-mutation:"+bits(request.cp));},()->r.event("overloaded"),()->true);
            r.tick(new Target(r,"target",true,true,false),false);
        }
    }
    static void passiveAndProgress() {
        for(String passive:List.of("brain_course","brain_course_advanced","mind_course")) {
            var r=new Run("passive/"+passive,.99999,5);r.s.learn(passive);r.s.addPassiveExperienceRaw(passive,.125f);
            r.s.addPassiveExperienceRaw(passive,.9375f);r.begin(false,false);
            r.tick(new Target(r,"full",true,true,true),false);r.tick(new Target(r,"unsupported",true,false,false),false);
            r.session.end();for(int i=0;i<36;i++){r.s.tick();r.event("passive-recovery:"+i);}
        }
        var r=new Run("fractional-capped-growth",.99999,1);r.s.extraCp=899.999;r.s.extraOverload=39.999;
        r.s.cp=10.00001;r.s.levelExperience=.999999;r.begin(false,false);
        for(int i=0;i<3;i++)r.tick(new Target(r,"target-"+i,true,true,false),false);
        for(String category:List.of("electromaster","meltdowner","teleporter","vecmanip")) {
            var p=new Run("classic-catalog/"+category,0,1);p.s.selectCategory(category);p.s.setLevel(1);
            for(int level=1;level<=5;level++){p.s.setLevel(level);p.event("classic-level-count:"+level);}
        }
    }
    public static String run() {
        OUT.setLength(0);cases=0;SkillCatalog.resetConfiguration();
        OUT.append("catalog-classic-size\t").append(SkillCatalog.ALL.size()).append('\n');
        matrix();captureAndRecovery();callbacksAndConfiguration();passiveAndProgress();
        OUT.append("cases\t").append(cases).append('\n');return OUT.toString();
    }
}
