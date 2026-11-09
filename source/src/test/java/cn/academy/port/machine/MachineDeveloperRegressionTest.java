package cn.academy.port.machine;

import cn.academy.port.develop.DeveloperEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.DevelopmentProcess;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class MachineDeveloperRegressionTest {
    private static int assertions;
    private static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static void equal(double a,double b,String label){check(Math.abs(a-b)<1e-8,label+": "+a+" != "+b);}
    public static void main(String[] args){
        geometry();battery();stimulation();tiers();System.out.println("Machine developer regression: "+assertions+" assertions passed");
    }
    private static void geometry(){
        check(MachineDeveloperRules.CELLS.size()==8,"source 8 occupied cells");
        for(var facing:MachineDeveloperRules.Facing.values()){
            var unique=new HashSet<MachineDeveloperRules.Cell>();
            for(int part=0;part<8;part++){
                var off=MachineDeveloperRules.offset(part,facing);check(unique.add(off),"unique source part");
                check(off.y()>=0&&off.y()<=2,"three source layers");
                check(facing==MachineDeveloperRules.Facing.NORTH||facing==MachineDeveloperRules.Facing.SOUTH?off.x()==0:off.z()==0,"one source cell wide");
            }
            check(unique.contains(new MachineDeveloperRules.Cell(0,0,0)),"root same pivot");
            check(!unique.contains(new MachineDeveloperRules.Cell(0,2,0)),"missing top foot source cell stays empty");
        }
        for(int turns=-8;turns<=8;turns++)for(int quadrant=0;quadrant<4;quadrant++)check(MachineDeveloperRules.fromYaw(turns*360+quadrant*90)==MachineDeveloperRules.Facing.values()[quadrant],"source yaw periodicity");
        check(MachineDeveloperRules.fromYaw(44.99f)==MachineDeveloperRules.Facing.NORTH,"source yaw rounding below45");
        check(MachineDeveloperRules.fromYaw(45f)==MachineDeveloperRules.Facing.EAST,"source yaw rounding45");
        check(MachineDeveloperRules.fromYaw(-45f)==MachineDeveloperRules.Facing.NORTH,"source negative45 boundary");
        check(MachineDeveloperRules.fromYaw(-45.01f)==MachineDeveloperRules.Facing.WEST,"source negativebelow45 boundary");
        try{MachineDeveloperRules.offset(8,MachineDeveloperRules.Facing.NORTH);throw new AssertionError("invalid part accepted");}catch(IllegalArgumentException expected){check(true,"invalid part rejected");}
    }
    private static void battery(){
        for(var type:new DeveloperType[]{DeveloperType.NORMAL,DeveloperType.ADVANCED}){
            var live=new AtomicBoolean(true);var changed=new AtomicInteger();var battery=new MachineDeveloperEnergy(type,changed::incrementAndGet,live::get);
            equal(battery.energy(),0,"no implicit fill");equal(battery.getMaxEnergy(),type.energy,"exact source capacity");equal(battery.getBandwidth(),type.bandwidth,"exact source receiver bandwidth");
            equal(battery.injectEnergy(type.energy+25),25,"source inject returns excess");equal(battery.energy(),type.energy,"capacity clamped");
            equal(battery.pullEnergy(type.energy-10),type.energy-10,"source public pull actual");
            check(!battery.tryPull(11),"source insufficient atomic pull fails");equal(battery.energy(),10,"source failed pull does not drain remainder");
            check(battery.tryPull(10),"source exact pull succeeds");equal(battery.energy(),0,"source exact pull empties");
            for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1}){check(!battery.tryPull(bad),"reject invalid pull");battery.load(bad);equal(battery.energy(),0,"NBT invalid energy sanitized");}
            battery.load(type.energy+1);equal(battery.energy(),type.energy,"NBT overcapacity sanitized");battery.load(0);
            int maximum=(int)(type.bandwidth*4);
            check(battery.receiveFe(Integer.MAX_VALUE,true,100)==maximum,"FE simulate respects bandwidth");equal(battery.energy(),0,"FE simulate immutable");
            check(battery.receiveFe(1,false,100)==1,"FE fractional IF accepted");equal(battery.energy(),.25,"1FE=quarter IF");
            check(battery.receiveFe(Integer.MAX_VALUE,false,100)==maximum-1,"shared tick bandwidth remainder");check(battery.receiveFe(1,false,100)==0,"six face repeated receive cannot bypass bandwidth");
            equal(battery.energy(),type.bandwidth,"native IF energy accumulated");check(battery.receiveFe(maximum,false,101)==maximum,"next tick budget reset");
            battery.load(type.energy-.2);check(battery.receiveFe(100,true,102)==0,"no invented fractional FE under1 acceptance");equal(battery.injectEnergy(.2),0,"native IF can exactly fill fractional capacity");
            battery.load(123);live.set(false);equal(battery.energy(),0,"removed/unloaded unavailable");check(battery.receiveFe(400,false,103)==0,"retained capability cannot charge dead storage");check(!battery.tryPull(1),"dead receiver refuses process drain");equal(battery.persistedEnergy(),123,"unavailability does not erase saved battery");live.set(true);equal(battery.energy(),123,"reactivation restores finite battery");
            check(changed.get()>0,"writes dirty persistent machine");
        }
        var portable=new DeveloperEnergy.Access(){double energy=10;public double energy(){return energy;}public void energy(double value){energy=value;}public DeveloperType type(){return DeveloperType.PORTABLE;}};
        check(!DeveloperEnergy.tryPull(portable,11),"portable failure remains distinct");equal(portable.energy(),0,"portable source remainder drain retained");
    }
    private static void stimulation(){
        for(var type:new DeveloperType[]{DeveloperType.NORMAL,DeveloperType.ADVANCED}){
            var battery=new MachineDeveloperEnergy(type,()->{},()->true);battery.load(type.energy);var completed=new AtomicInteger();
            var developer=new DevelopmentProcess.Developer(){public DeveloperType type(){return type;}public boolean tryPullEnergy(double amount){return battery.tryPull(amount);}public double energy(){return battery.energy();}public double maxEnergy(){return battery.getMaxEnergy();}};
            var action=new DevelopmentProcess.Action(){public String id(){return "test";}public int stimulations(){return 2;}public boolean validate(DevelopmentProcess.Developer d){return true;}public void complete(){completed.incrementAndGet();}};
            var process=new DevelopmentProcess();process.start(developer,action);
            for(int tick=1;tick<2*(type.tps+1);tick++){process.tick();check(process.isDeveloping(),"source TPS+1 holds before final tick");}
            process.tick();check(process.state()==DevelopmentProcess.State.DONE,"source exact stimulation done");check(completed.get()==1,"completion exactly once");equal(type.energy-battery.energy(),type.actualConsumption(2),"real source machine IF debit");
            battery.load(type.energyPerTick()-.01);process.start(developer,action);process.tick();check(process.state()==DevelopmentProcess.State.FAILED,"underpowered machine stops development");equal(battery.energy(),type.energyPerTick()-.01,"atomic machine remainder retained on failure");
        }
    }
    private static void tiers(){
        for(int level=1;level<=5;level++)for(var type:DeveloperType.values())check(type.supportsSkill(level)==(level<=2||level==3&&type!=DeveloperType.PORTABLE||level>=4&&type==DeveloperType.ADVANCED),"source tier skill gate");
        equal(DeveloperType.NORMAL.energyPerTick(),35,"normal700/20IF per tick");equal(DeveloperType.ADVANCED.energyPerTick(),40,"advanced600/15IF per tick");
        check(DeveloperType.NORMAL.ticksPerStimulation()==21,"normal21actual ticks");check(DeveloperType.ADVANCED.ticksPerStimulation()==16,"advanced16actual ticks");
        equal(DeveloperType.NORMAL.syncRate,.7,"normal source sync GUI rate");equal(DeveloperType.ADVANCED.syncRate,1,"advanced source sync GUI rate");
    }
}
