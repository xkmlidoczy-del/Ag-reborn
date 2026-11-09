package cn.academy.port.former;
import static cn.academy.port.former.ClassicMetalFormerWork.*;
public final class ClassicMetalFormerRegressionTest {
 static int checks;
 static void yes(boolean test,String label){checks++;if(!test)throw new AssertionError(label);}
 static void eq(double want,double got,String label){yes(Double.doubleToLongBits(want)==Double.doubleToLongBits(got),label+" expected="+want+" actual="+got);}
 static final class Fixture implements Access {
  int input=0,inCount=1,damage,out=-1,outCount,outDamage,outMax=64,complete;
  double energy=3000;
  Mode recipeMode=Mode.PLATE;
  int required=1,yield=1;
  public int recipeForInput(Mode mode){return input==0&&inCount>=required&&damage==0&&mode==recipeMode?0:-1;}
  public boolean accepts(int recipe,Mode mode){return recipeForInput(mode)==recipe;}
  public boolean outputAvailable(int recipe){return outCount==0||out==1&&outDamage==0&&outCount+yield<=outMax;}
  public double pullEnergy(double amount){double taken=Math.min(amount,energy);energy-=taken;return taken;}
  public void complete(int recipe){inCount-=required;out=1;outCount+=yield;complete++;}
 }
 public static void main(String[] args){
  yes(CAPACITY==3000&&BANDWIDTH==50&&CONSUME_PER_TICK==13.3&&WORK_TICKS==60&&SCAN_TICKS==5,"exact source constants");
  for(var mode:Mode.values()){
   var f=new Fixture();f.recipeMode=mode;var w=new ClassicMetalFormerWork();w.loadMode(mode.ordinal());
   for(int i=0;i<4;i++){w.tick(f);yes(!w.working(),"first four idle ticks");eq(3000,f.energy,"scan consumes no IF");}
   w.tick(f);yes(w.working()&&w.counter()==0,"fifth tick selects without debit");eq(3000,f.energy,"select consumes no IF");
   double expected=3000;for(int tick=1;tick<=60;tick++){w.tick(f);expected-=13.3;eq(expected,f.energy,"literal iterative source debit");yes(f.complete==(tick==60?1:0),"complete exact60");}
   yes(!w.working()&&w.counter()==0&&f.inCount==0&&f.outCount==1,"single atomic inventory completion");
  }
  for(int change=0;change<7;change++){
   var f=new Fixture();var w=new ClassicMetalFormerWork();for(int i=0;i<5;i++)w.tick(f);
   switch(change){case 0->f.inCount=0;case 1->f.input=9;case 2->f.damage=1;case 3->f.outCount=64;case 4->{f.outCount=1;f.out=9;}case 5->{f.outCount=1;f.out=1;f.outDamage=1;}case 6->w.cycleMode(1);}
   double before=f.energy;w.tick(f);eq(before-13.3,f.energy,"blocked work debits before check "+change);yes(!w.working()&&w.counter()==0&&f.complete==0,"blocked resets exact state");
  }
  for(double amount:new double[]{0,.125,13.299999999999999,13.3}){var f=new Fixture();f.energy=amount;var w=new ClassicMetalFormerWork();for(int i=0;i<5;i++)w.tick(f);w.tick(f);eq(0,f.energy,"actual underpowered remainder drained");yes(w.working()==(amount==13.3),"exact equality successfulpull");}
  var f=new Fixture();f.inCount=4;f.required=2;f.yield=3;var w=new ClassicMetalFormerWork();for(int i=0;i<65;i++)w.tick(f);yes(f.inCount==2&&f.outCount==3&&f.complete==1,"coin exact2plates3coins");for(int i=0;i<65;i++)w.tick(f);yes(f.inCount==0&&f.outCount==6&&f.complete==2,"fresh scan between jobs");
  w.loadMode(Mode.REFINE.ordinal());yes(!w.working()&&w.counter()==0&&w.mode()==Mode.REFINE,"save reload drops transientwork");w.cycleMode(1);yes(w.mode()==Mode.PLATE,"right wraps");w.cycleMode(-1);yes(w.mode()==Mode.REFINE,"left wraps");w.loadMode(-1);yes(w.mode()==Mode.PLATE,"malformed ordinal safe adaptation");w.loadMode(9);yes(w.mode()==Mode.PLATE,"large ordinal safe adaptation");
  yes(ClassicMetalFormerRules.BUILT_INS.size()==21,"all21source transformations");for(int count=1;count<=64;count++)yes(ClassicMetalFormerRules.refinedCount(count)==(count<32?2*count:64),"conditional furnace yield rule");
  System.out.println("ClassicMetalFormerRegressionTest: "+checks+" checks passed");
 }
}
