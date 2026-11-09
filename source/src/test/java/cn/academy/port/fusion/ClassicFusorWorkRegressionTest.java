package cn.academy.port.fusion;
/** Literal source tick edge cases, finite energy and inventory/fluid conservation. */
public final class ClassicFusorWorkRegressionTest {
    private static int checks;
    private static void yes(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void eq(double expected,double actual,String message){yes(Double.doubleToLongBits(expected)==Double.doubleToLongBits(actual),message+" expected="+expected+" actual="+actual);}
    private static final class Fixture implements ClassicFusorWork.Access {
        int input=0,inputCount=1,output=-1,outputCount=0,liquid=3000,finished;double energy=2000;boolean componentMatch=true;
        public int recipeForInput(){return inputCount>0?input:-1;}public boolean inputMatches(int recipe){return inputCount>0&&input==recipe;}public boolean enoughInput(int recipe){return inputCount>=1;}
        public boolean outputTypeMatches(int recipe){return outputCount==0||output==recipe+1;}public boolean outputAvailable(int recipe){return outputCount==0||output==recipe+1&&outputCount<64&&componentMatch;}
        public int liquid(){return liquid;}public double pullEnergy(double request){double taken=Math.min(request,energy);energy-=taken;return taken;}
        public void complete(int recipe){inputCount--;output=recipe+1;outputCount++;liquid-=ClassicFusorWork.liquidRequired(recipe);finished++;}
    }
    public static void main(String[] args){
        yes(ClassicFusorWork.CAPACITY==2000&&ClassicFusorWork.BANDWIDTH==50&&ClassicFusorWork.TANK_SIZE==8000,"source receiver and tank");
        for(int recipe=0;recipe<2;recipe++){
            var work=new ClassicFusorWork();var fixture=new Fixture();fixture.input=recipe;fixture.liquid=ClassicFusorWork.liquidRequired(recipe);
            for(int tick=0;tick<9;tick++)work.tick(fixture);eq(2000,fixture.energy,"nine source check cooldown ticks consume nothing");yes(!work.working(),"not yet working");
            work.tick(fixture);yes(work.working(),"starts on tenth tick");eq(1988,fixture.energy,"same start tick consumes12");
            for(int tick=1;tick<120;tick++)work.tick(fixture);yes(fixture.finished==0,"source double120increments remains below1");yes(work.progress()<1,"source floating-point edge retained");
            work.tick(fixture);yes(fixture.finished==1&&fixture.inputCount==0&&fixture.outputCount==1&&fixture.output==recipe+1,"one source input to one upgraded output");eq(548,fixture.energy,"121 source work ticks consume1452IF");yes(fixture.liquid==0,"exact3000or8000mB consumed once");yes(!work.working()&&work.progress()==0,"completion clears progress");
        }
        var fixture=new Fixture();var work=new ClassicFusorWork();for(int i=0;i<10;i++)work.tick(fixture);fixture.inputCount=0;double before=fixture.energy;work.tick(fixture);eq(before,fixture.energy,"removed input short-circuits power pull");yes(!work.working(),"missing input aborts");
        fixture=new Fixture();work=new ClassicFusorWork();fixture.liquid=2999;for(int i=0;i<10;i++)work.tick(fixture);eq(1988,fixture.energy,"insufficient liquid still pulls12 before abort");yes(!work.working()&&fixture.liquid==2999,"low liquid abort never drains liquid");
        fixture=new Fixture();work=new ClassicFusorWork();fixture.output=2;fixture.outputCount=1;for(int i=0;i<10;i++)work.tick(fixture);eq(1988,fixture.energy,"different output item pulls12 before abort");yes(!work.working(),"different output type abort");
        for(boolean componentMatch:new boolean[]{true,false}){
            fixture=new Fixture();work=new ClassicFusorWork();fixture.output=1;fixture.outputCount=componentMatch?64:1;fixture.componentMatch=componentMatch;for(int i=0;i<10;i++)work.tick(fixture);yes(work.working()&&work.actionBlocked(fixture),"same-type full/component output stays active but blocked");eq(0,work.progress(),"blocked progress stayszero");eq(1988,fixture.energy,"blocked work still consumes12");fixture.outputCount=0;fixture.componentMatch=true;work.tick(fixture);eq(ClassicFusorWork.WORK_SPEED,work.progress(),"unblocking resumes without recipe rematch");
        }
        fixture=new Fixture();work=new ClassicFusorWork();fixture.energy=11.5;for(int i=0;i<10;i++)work.tick(fixture);eq(0,fixture.energy,"short energy debit drains actual remainder");yes(!work.working()&&fixture.finished==0&&fixture.liquid==3000,"underpowered attempt conserves liquid/item");
        fixture=new Fixture();work=new ClassicFusorWork();fixture.inputCount=2;fixture.liquid=6000;for(int i=0;i<130;i++)work.tick(fixture);yes(fixture.finished==1,"first exact completion");before=fixture.energy;work.tick(fixture);yes(work.working(),"source completion sets cooldownzero for next tick");eq(before-12,fixture.energy,"next item begins immediately");
        work.reset();yes(!work.working()&&work.progress()==0,"source NBT omits recipe/progress");before=fixture.energy;for(int i=0;i<9;i++)work.tick(fixture);eq(before,fixture.energy,"reload restores ten-tick initial check");
        System.out.println("ClassicFusorWorkRegressionTest: "+checks+" assertions passed");
    }
}
