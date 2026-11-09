package cn.academy.port.bridge;

import com.google.gson.JsonParser;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.tools.ToolProvider;

/** Compiles and executes unchanged1.0.7 tiles/managers/RFSupport in an independent minimal old API. */
public final class ClassicEnergyBridgeSourceOracleTest {
    private static final String MANIFEST_SHA="610298c2f0b22a1513915f08a88607e66041da09a0990e79326c7796e370bb1b";
    private static int checks;
    private static Class<?> original;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void equal(double expected,double actual,String why){check(Double.isFinite(actual)&&Math.abs(expected-actual)<1e-8,why+": expected="+expected+", actual="+actual);}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static Object old(double input,double output)throws Exception{return original.getConstructor(double.class,double.class).newInstance(input,output);}
    private static Object call(Object instance,String name,Object... args)throws Exception{Class<?>[] types=new Class<?>[args.length];for(int i=0;i<types.length;i++)types[i]=args[i] instanceof Integer?int.class:args[i] instanceof Double?double.class:args[i] instanceof Boolean?boolean.class:args[i].getClass();return original.getMethod(name,types).invoke(instance,args);}
    private static double number(Object instance,String name,Object... args)throws Exception{return ((Number)call(instance,name,args)).doubleValue();}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(System.getProperty("academy.energybridges.stage",".")),fixture=root.resolve("src/test/resources/classic-energy-bridges-source");byte[] manifest=Files.readAllBytes(fixture.resolve("witness-manifest.json"));check(sha(manifest).equals(MANIFEST_SHA),"independently pinned primary witness manifest");for(var entry:JsonParser.parseString(new String(manifest,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("files")){var e=entry.getAsJsonObject();check(sha(Files.readAllBytes(fixture.resolve(e.get("path").getAsString()))).equals(e.get("sha256").getAsString()),"unchanged packaged primary "+e.get("path"));}
        Path classes=Files.createTempDirectory("academy-rf-original-");try{
            var sources=new ArrayList<>(List.of("-proc:none","-encoding","UTF-8","-d",classes.toString()));for(var directory:List.of("java","stubs"))try(var paths=Files.walk(fixture.resolve(directory))){paths.filter(p->p.toString().endsWith(".java")).sorted().forEach(p->sources.add(p.toString()));}
            var compiler=ToolProvider.getSystemJavaCompiler();check(compiler!=null&&compiler.run(null,null,null,sources.toArray(String[]::new))==0,"unchanged RFSupport, tiles, base classes and both generic managers compile independently");
            try(var loader=new URLClassLoader(new URL[]{classes.toUri().toURL()},ClassLoader.getPlatformClassLoader())){original=loader.loadClass("oracle.OriginalRFHarness");constantsAndDefects();validDifferentials();}
        }finally{try(var paths=Files.walk(classes)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        String rf=Files.readString(fixture.resolve("java/cn/academy/support/rf/RFSupport.java")),ic2=Files.readString(fixture.resolve("witness/src/main/java/cn/academy/support/ic2/IC2Support.java"));check(!rf.contains("Optional")&&rf.contains("@RegWithName(\"rf_input\")")&&rf.contains("@RegWithName(\"rf_output\")"),"RF pair unconditional bundled legacy API registration");check(ic2.contains("@Optional.Method(modid=MODID)")&&ic2.contains("insulatedCopperCableItem")&&ic2.contains("batBox")&&ic2.contains("EnergyItemHelper.register(new IC2EnergyItemManager())"),"IC2 conditional genuine cable/BatBox/energy-item-manager boundary");
        String converter=Files.readString(fixture.resolve("witness/src/main/java/cn/academy/support/BlockConverterBase.java"));check(converter.contains("setHarvestLevel(\"pickaxe\", 0)")&&converter.contains("setHardness(2.5f)")&&converter.contains("WirelessPage.userPage((TileEntity) te).window()")&&converter.contains("!player.isSneaking()"),"source wood-pickaxe full cube and standalone wireless-only activation");check(!converter.contains("registerItemRenderer")&&!converter.contains("getIcon"),"ordinary source ItemBlock has no custom flat-item renderer");
        var boundary=JsonParser.parseString(Files.readString(fixture.resolve("witness/legacy-registration-boundary.json"))).getAsJsonObject();check(boundary.get("directItemIngredientMetadata").getAsInt()==0&&boundary.get("currentNativeEmptyUnitDefaultDamage").getAsInt()==13&&boundary.get("wildcardMetadata").getAsInt()==32767,"pinned official old metadata0 boundary and established modern genuine empty state");check(!ClassicBridgeEnergyUnitIngredient.acceptsEnergy(5000)&&ClassicBridgeEnergyUnitIngredient.acceptsEnergy(0)&&ClassicBridgeEnergyUnitIngredient.acceptsEnergy(9800),"narrow empty adaptation and source-equivalent full-gauge0 state, no intermediate-charge broadening");
        System.out.println("ClassicEnergyBridgeSourceOracleTest: "+checks+" unchanged-original valid-case differentials, proven-defect and conditional/source-visual witnesses passed");
    }
    private static void constantsAndDefects()throws Exception{
        Object tile=old(0,0);for(String method:List.of("inputCapacity","outputCapacity"))equal(2000,number(tile,method),"unchanged source "+method);for(String method:List.of("inputBandwidth","outputBandwidth"))equal(100,number(tile,method),"unchanged source "+method);equal(0,number(tile,"inputInventory"),"source input inventory absent");equal(0,number(tile,"outputInventory"),"source output inventory absent");check((Boolean)call(tile,"allFaces"),"all source RF faces connect");
        equal(4,number(tile,"recipeCount"),"unchanged Java registration executes all4 recipes");check(call(tile,"recipe",0).equals("rf_input|abc| d |a|energy_unit|b|machine_frame|c|constraint_plate|d|energy_convert_component"),"exact source primary RF input grid");check(call(tile,"recipe",1).equals("rf_output|abc| d |a|energy_unit|b|machine_frame|c|reso_crystal|d|energy_convert_component"),"exact source primary RF output grid");check(call(tile,"recipe",2).equals("rf_input|X|X|rf_output")&&call(tile,"recipe",3).equals("rf_output|X|X|rf_input"),"both one-item source conversion grids");
        tile=old(0,0);equal(3,number(tile,"receive",3,false),"source reports3 RF accepted for a sub-IF request");equal(0,number(tile,"inputEnergy"),"source actually credits0 IF, proven loss defect");var modern=new ClassicEnergyBridgeBuffer();equal(0,modern.receive(3,false),"modern returns exactly zero actual acceptance");
        tile=old(0,.75);equal(0,number(tile,"extract",3,false),"source truncates fractional IF before return");equal(0,number(tile,"outputEnergy"),"source debits.75 IF while returning0 RF");modern.load(.75);equal(3,modern.extract(3,false),"modern quarter-IF extraction conservation correction");equal(0,modern.energy(),"exact modern actual extraction debit");
        tile=old(123.625,123.625);equal(0,number(tile,"inputReload"),"original generator NBT omits energy");equal(123.625,number(tile,"outputReload"),"original receiver energy persists");equal(160,number(tile,"managerStored"),"source generic provider multiplies40RF by4 instead of divides");equal(16,number(tile,"managerPull",16.0),"source generic provider returns16IF after taking4RF for16IF request");equal(0,number(tile,"managerCharge",10.0),"source generic receiver reports10IF accepted after taking only10RF");equal(10,number(tile,"helperCharge",10.0),"source aggregate helper returns full unaccepted IF after real charge");
        tile=old(0,100);check(call(tile,"order").equals("DOWN:UP|UP:DOWN|NORTH:SOUTH|SOUTH:NORTH|WEST:EAST|EAST:WEST|"),"literal source neighbor iteration and opposite ingress faces");tile=old(0,0);equal(81,number(tile,"tickPublication"),"20th receiver publication precedes that tick's output push");
    }
    private static void validDifferentials()throws Exception{
        Random random=new Random(107);for(int run=0;run<1000;run++){
            double energy=random.nextInt(8001)/4d;Object tile=old(energy,Math.floor(energy));var input=new ClassicEnergyBridgeBuffer();input.load(energy);var output=new ClassicEnergyBridgeBuffer();output.load(Math.floor(energy));
            for(int step=0;step<20;step++){
                int amount=random.nextInt(2001)*4;boolean simulation=random.nextBoolean();equal(number(tile,"receive",amount,simulation),input.receive(amount,simulation),"valid divisible source receive/simulation");equal(number(tile,"inputEnergy"),input.energy(),"valid input source IF state");
                // Integer original output energy and whole-IF requests exclude its independently proven fractional/overdraw defects.
                int take=random.nextInt((int)output.energy()+1)*4;equal(number(tile,"extract",take,simulation),output.extract(take,simulation),"valid source extract/simulation");equal(number(tile,"outputEnergy"),output.energy(),"valid output source IF state");
            }
        }
    }
}
