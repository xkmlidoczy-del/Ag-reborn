package cn.academy.port.former;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
/** Differential oracle executes unchanged pinned classic methods under an independent minimal old API. */
public final class ClassicMetalFormerSourceOracleTest {
 static int checks;
 static void yes(boolean test,String label){checks++;if(!test)throw new AssertionError(label);}
 static void eq(double expected,double actual,String label){yes(Double.doubleToLongBits(expected)==Double.doubleToLongBits(actual),label+" expected="+expected+" actual="+actual);}
 static final Map<String,String> HASHES=Map.ofEntries(
Map.entry("java/cn/academy/crafting/block/TileMetalFormer.java", "32e41e5a1ada6c2069225bc4bd3b09258695003484e84cc789d53f2d1e42dc13"),
Map.entry("java/cn/academy/crafting/block/BlockMetalFormer.java", "44d1f26d2eaacdd6677109e086230d90d466557c5cb3c91f936b63f2a010bd1d"),
Map.entry("java/cn/academy/crafting/block/ContainerMetalFormer.java", "71e3b9a392ce946daa27399fa84fbb03ba1d0836bf6f9ec9b75100f226860e8d"),
Map.entry("java/cn/academy/crafting/block/SlotMFItem.java", "8860ee238edc32c6ce4e840602b11dd00699d06a0697c8bf63c167503b918649"),
Map.entry("java/cn/academy/crafting/api/MetalFormerRecipes.java", "79385669e070ff7440fb6bc2731e6f9c3cfe92fc47ee187a1ba057f0373b08fe"),
Map.entry("java/cn/academy/crafting/ModuleCrafting.java", "f0ea3f22cc77815ecd0f4f6374d68ac0ca6334e720ee159d97aae0e3256a6b46"),
Map.entry("java/cn/academy/vanilla/ModuleVanilla.java", "5028171ed587fc4e54994133ae02e78b885c4d53e73e03218aea86ffb354960a"),
Map.entry("java/cn/academy/core/block/TileReceiverBase.java", "3579e9af020d787fe885e761bc990f12f0a729122fc45349db16958ac033f35d"),
Map.entry("java/cn/academy/core/tile/TileInventory.java", "748835f0402b366cc000bc64193245eeeb50b7047dea65d7430401c9e4df245e"),
Map.entry("resources/assets/academy/recipes/default.recipe", "a9dc4aa3477ad246f7cb9bb6bea3c33e033aaa8431f3c8a03acaf0845374da1f"));
 static String method(String source,String signature){int start=source.indexOf(signature);yes(start>=0,"pinned source signature "+signature);int open=source.indexOf('{',start),depth=1,end=open+1;while(depth>0){char c=source.charAt(end++);if(c=='{')depth++;else if(c=='}')depth--;}return source.substring(start,end);}
 static final class Fixture implements ClassicMetalFormerWork.Access {
  UpstreamMetalFormer.ItemStack input,output,battery;double energy=3000;List<UpstreamMetalFormer.RecipeObject> recipes;
  public int recipeForInput(ClassicMetalFormerWork.Mode mode){for(int i=0;i<recipes.size();i++)if(recipes.get(i).accepts(input,UpstreamMetalFormer.Mode.values()[mode.ordinal()]))return i;return -1;}
  public boolean accepts(int index,ClassicMetalFormerWork.Mode mode){return recipes.get(index).accepts(input,UpstreamMetalFormer.Mode.values()[mode.ordinal()]);}
  public boolean outputAvailable(int index){var result=recipes.get(index).output;return output==null||output.item==result.item&&output.damage==result.damage&&output.stackSize+result.stackSize<=output.max;}
  public double pullEnergy(double request){double taken=Math.min(request,energy);energy-=taken;return taken;}
  public void complete(int index){var recipe=recipes.get(index);input.stackSize-=recipe.input.stackSize;if(input.stackSize==0)input=null;if(output==null)output=recipe.output.copy();else output.stackSize+=recipe.output.stackSize;}
  void recharge(){if(UpstreamMetalFormer.EnergyItemHelper.isSupported(battery)){double gain=UpstreamMetalFormer.EnergyItemHelper.pull(battery,Math.min(3000-energy,50),false);energy+=Math.min(gain,3000-energy);}}
 }
 static UpstreamMetalFormer.ItemStack clone(UpstreamMetalFormer.ItemStack stack){return stack==null?null:stack.copy();}
 static void stack(UpstreamMetalFormer.ItemStack want,UpstreamMetalFormer.ItemStack got,String label){yes((want==null)==(got==null),label+" emptiness");if(want!=null)yes(want.item==got.item&&want.damage==got.damage&&want.stackSize==got.stackSize,label+" identity/count/meta");}
 public static void main(String[] args)throws Exception {
  Path stage=Path.of(System.getProperty("academy.former.stage",Files.exists(Path.of("src/main/java/cn/academy/port/former/ClassicMetalFormer.java"))?".":".staging/metal-former"));Path fixture=stage.resolve("src/test/resources/classic-metal-former-source");
  for(var hash:HASHES.entrySet()){byte[] bytes=Files.readAllBytes(fixture.resolve(hash.getKey()));String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));yes(actual.equals(hash.getValue()),"immutable upstream "+hash.getKey());}
  String canonical=Files.readString(fixture.resolve("java/cn/academy/crafting/block/TileMetalFormer.java"));String oracle=Files.readString(stage.resolve("src/test/java/cn/academy/port/former/UpstreamMetalFormer.java"));
  for(String signature:List.of("public void updateEntity()","public void cycleMode(int delta)","private boolean isActionBlocked()","public boolean isWorkInProgress()","public double getWorkProgress()"))yes(method(canonical,signature).equals(method(oracle,signature)),"unchanged executable source body "+signature);
  String recipeSource=Files.readString(fixture.resolve("java/cn/academy/crafting/api/MetalFormerRecipes.java"));for(String signature:List.of("public boolean accepts(ItemStack stack, Mode mode2)","public RecipeObject getRecipe(ItemStack input, Mode mode)"))yes(method(recipeSource,signature).equals(method(oracle,signature)),"unchanged independent recipe body "+signature);
  var recipes=UpstreamMetalFormer.MetalFormerRecipes.INSTANCE.objects;for(var rule:ClassicMetalFormerCanonicalRecipes.read(fixture))recipes.add(new UpstreamMetalFormer.RecipeObject(new UpstreamMetalFormer.ItemStack(rule.input().intern(),rule.inputCount(),0,64),new UpstreamMetalFormer.ItemStack(rule.output().intern(),rule.outputCount(),0,64),UpstreamMetalFormer.Mode.values()[rule.mode().ordinal()]));
  for(int recipe=0;recipe<21;recipe++){
   var old=new UpstreamMetalFormer();var modern=new ClassicMetalFormerWork();var f=new Fixture();f.recipes=recipes;f.input=recipes.get(recipe).input.copy();f.input.stackSize*=3;old.inventory[0]=clone(f.input);old.energy=f.energy;old.mode=recipes.get(recipe).mode;modern.loadMode(old.mode.ordinal());
   for(int tick=0;tick<195;tick++){old.updateEntity();modern.tick(f);f.recharge();eq(old.energy,f.energy,"source energy rule"+recipe+" tick"+tick);yes(old.workCounter==modern.counter()&&old.isWorkInProgress()==modern.working(),"source state rule"+recipe+" tick"+tick);stack(old.inventory[0],f.input,"source input");stack(old.inventory[1],f.output,"source output");}
  }
  for(int seed=0;seed<150;seed++){
   var random=new Random(seed);var old=new UpstreamMetalFormer();var modern=new ClassicMetalFormerWork();var f=new Fixture();f.recipes=recipes;old.energy=f.energy=3000;
   for(int tick=0;tick<2000;tick++){
    int change=random.nextInt(35);
    if(change==0){int delta=random.nextBoolean()?1:-1;old.cycleMode(delta);modern.cycleMode(delta);}
    if(change==1){var rule=recipes.get(random.nextInt(recipes.size()));f.input=new UpstreamMetalFormer.ItemStack(rule.input.item,random.nextInt(65),random.nextInt(8)==0?1:0,64);if(f.input.stackSize==0)f.input=null;old.inventory[0]=clone(f.input);}
    if(change==2){var rule=recipes.get(random.nextInt(recipes.size()));f.output=new UpstreamMetalFormer.ItemStack(rule.output.item,random.nextInt(65),random.nextInt(8)==0?1:0,64);if(f.output.stackSize==0)f.output=null;old.inventory[1]=clone(f.output);}
    if(change==3){double amount=switch(random.nextInt(5)){case 0->0;case 1->13.299999999999999;case 2->13.3;case 3->.25;default->random.nextDouble()*3000;};old.energy=f.energy=amount;}
    if(change==4){f.battery=new UpstreamMetalFormer.ItemStack("battery",1,0,1);f.battery.battery=random.nextDouble()*10000;old.inventory[2]=clone(f.battery);}
    if(change==5){f.battery=null;old.inventory[2]=null;}
    if(change==6){int ordinal=old.mode.ordinal();old.current=null;old.workCounter=0;modern.loadMode(ordinal);}
    old.updateEntity();modern.tick(f);f.recharge();eq(old.energy,f.energy,"random energy seed"+seed+" tick"+tick);yes(old.mode.ordinal()==modern.mode().ordinal()&&old.workCounter==modern.counter()&&old.isWorkInProgress()==modern.working(),"random source state");eq(old.getWorkProgress(),modern.progress(),"random source progress");stack(old.inventory[0],f.input,"random input");stack(old.inventory[1],f.output,"random output");if(f.battery!=null)eq(old.inventory[2].battery,f.battery.battery,"random finite battery");
   }
  }
  System.out.println("ClassicMetalFormerSourceOracleTest: "+checks+" pinned-source/differential checks passed");
 }
}
