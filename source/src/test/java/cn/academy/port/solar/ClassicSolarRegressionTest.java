package cn.academy.port.solar;

import java.util.Random;
import net.minecraft.nbt.CompoundTag;

/** Server arithmetic, exact source boundaries, finite charging and short-wire synchronization. */
public final class ClassicSolarRegressionTest {
    private static int assertions;
    private static void check(boolean condition, String label) { assertions++; if (!condition) throw new AssertionError(label); }
    private static void equal(double expected, double actual, String label) { check(Double.isFinite(actual) && Math.abs(expected-actual)<1e-7,label+": "+actual+" != "+expected); }
    public static void main(String[] args) {
        for (long dayTime : new long[]{0,1,12500,24000,36500,Long.MAX_VALUE-24000-Long.MAX_VALUE%24000+12500}) {
            check(ClassicSolarRules.status(dayTime,true,false)==ClassicSolarRules.Status.STRONG,"inclusive source day "+dayTime);
            check(ClassicSolarRules.status(dayTime,true,true)==ClassicSolarRules.Status.WEAK,"source rain "+dayTime);
            check(ClassicSolarRules.status(dayTime,false,false)==ClassicSolarRules.Status.STOPPED,"source sky test "+dayTime);
        }
        for (long dayTime : new long[]{-1,-24001,12501,23999,36501,47999}) check(ClassicSolarRules.status(dayTime,true,false)==ClassicSolarRules.Status.STOPPED,"source night/negative remainder "+dayTime);
        equal(3,ClassicSolarRules.generation(10,ClassicSolarRules.Status.STRONG),"clear source3IF");
        equal(.6,ClassicSolarRules.generation(10,ClassicSolarRules.Status.WEAK),"source rain20percent");
        equal(.25,ClassicSolarRules.generation(.25,ClassicSolarRules.Status.STRONG),"remaining buffer headroom");
        equal(0,ClassicSolarRules.generation(-1,ClassicSolarRules.Status.STRONG),"negative required hardening");
        var buffer=new ClassicSolarBuffer();
        for(int tick=0;tick<334;tick++)buffer.tick(ClassicSolarRules.Status.STRONG,null);
        equal(1000,buffer.energy(),"finite buffer stops at1000");
        buffer.tick(ClassicSolarRules.Status.STOPPED,request->{equal(100,request,"generator source100IF recharge cap");return request-20;});
        equal(980,buffer.energy(),"unit source20IF limit leaves80");
        buffer.tick(ClassicSolarRules.Status.STOPPED,request->request);equal(980,buffer.energy(),"full supported item no drain");
        buffer.tick(ClassicSolarRules.Status.STOPPED,null);equal(980,buffer.energy(),"unsupported or absent item no drain");
        buffer.tick(ClassicSolarRules.Status.STOPPED,request->Double.NaN);equal(980,buffer.energy(),"corrupt callback no invented debit");
        equal(950,buffer.getProvidedEnergy(950),"source actual provided IF");equal(30,buffer.energy(),"provided debits");
        equal(30,buffer.getProvidedEnergy(100),"underpowered source partial drain");equal(0,buffer.energy(),"provided emptied");
        equal(900,buffer.addEnergy(1900,false),"source addition returns not accepted");equal(1000,buffer.energy(),"source addition capacity");
        equal(1,buffer.addEnergy(1,true),"source simulate saturated");equal(1000,buffer.energy(),"source simulation no mutation");
        for(double corrupt:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1}){buffer.load(corrupt);equal(0,buffer.energy(),"corrupt storage finite");equal(0,buffer.getProvidedEnergy(corrupt),"corrupt extraction no creation");}
        buffer.load(1001);equal(1000,buffer.energy(),"oversized load finite");
        var tag=new CompoundTag();tag.putDouble("energy",123.625);buffer.load(tag.getDouble("energy"));equal(123.625,buffer.energy(),"fractional NBT seam");
        var random=new Random(107);
        for(int iteration=0;iteration<20000;iteration++) {
            double value=random.nextDouble()*1000;
            // Simulate unsigned words transported in signed native ContainerData shorts.
            int[] words=new int[4];for(int index=0;index<4;index++)words[index]=(short)ClassicSolarRules.word(value,index);
            check(Double.doubleToRawLongBits(value)==Double.doubleToRawLongBits(ClassicSolarRules.fromWords(words[0],words[1],words[2],words[3])),"bit-exact finite menu data");
            buffer.load(value); double battery=random.nextDouble()*10000, batteryBefore=battery;
            final double[] charged={battery};double bandwidth=iteration%2==0?20:50;
            var status=ClassicSolarRules.Status.values()[iteration%3];double expectedGenerated=Math.min(1000-value,ClassicSolarRules.rate(status));
            buffer.tick(status,request->{double moved=Math.min(request,Math.min(10000-charged[0],bandwidth));charged[0]+=moved;return request-moved;});
            equal(value+batteryBefore+expectedGenerated,buffer.energy()+charged[0],"generation/slot total conservation");
            check(charged[0]>=batteryBefore&&charged[0]<=10000,"finite native battery");check(buffer.energy()>=0&&buffer.energy()<=1000,"finite generator buffer");
            check(charged[0]-batteryBefore<=bandwidth+1e-7,"item bandwidth respected");
        }
        buffer.load(0);final double[] portable={0};int ticks=0;
        while(portable[0]<10000) {buffer.tick(ClassicSolarRules.Status.STRONG,request->{double moved=Math.min(request,Math.min(10000-portable[0],50));portable[0]+=moved;return request-moved;});ticks++;}
        check(ticks==3334,"clear-day genuine fill duration");equal(10000,portable[0],"full finite portable");equal(2,buffer.energy(),"excess clear-day2IF retained");
        System.out.println("ClassicSolarRegressionTest: "+assertions+" assertions passed; source time/rain/capacity, conservation and exact menu doubles");
    }
}
