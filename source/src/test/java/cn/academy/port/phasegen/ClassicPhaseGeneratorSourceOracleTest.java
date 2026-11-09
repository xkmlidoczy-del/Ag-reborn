package cn.academy.port.phasegen;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Runs the unchanged 1.0.7 generator classes in an isolated, minimal old API. */
public final class ClassicPhaseGeneratorSourceOracleTest {
    private static int checks;
    private static Class<?> oracleType;
    // Immutable upstream fixture hashes, frozen independently of the port.
    private static final Map<String,String> HASHES = Map.ofEntries(
        Map.entry("java/cn/academy/energy/block/TilePhaseGen.java", "6311b2d4241da74750f088fc01472c5d1dcb02f74b17e8c160b428cf9f5a4c2c"),
        Map.entry("java/cn/academy/core/block/TileGeneratorBase.java", "21669f5fdb7684452fec12cb8e0384731b543ff8bda27c6d22fbacc122c6207a"),
        Map.entry("java/cn/academy/core/tile/TileInventory.java", "748835f0402b366cc000bc64193245eeeb50b7047dea65d7430401c9e4df245e"),
        Map.entry("java/cn/academy/energy/api/IFItemManager.java", "3d5ebf467392ba53c8741c03a44c7f18feebb679e0011866a4768d84171dfed7"),
        Map.entry("java/cn/academy/energy/IFConstants.java", "55079b4e5dbefab7de2ac9c62386642211d5a281bc0cf450bd6fb90cbe860e24"),
        Map.entry("witness/ContainerPhaseGen.java", "0185244b06409618bd34587e4dff8cd68d7b4f816e842c0c25e32f0fa64ff63b"),
        Map.entry("witness/BlockPhaseGen.java", "a1b4d28ba6bf62dc500cd2acb084dd0041e44d7151d0c0cf43df778a97747a8c"),
        Map.entry("witness/RenderPhaseGen.java", "d43d3d6cc463238888315307b15721933b5429cf5020ee04a02f1a0a41a27ab1"),
        Map.entry("witness/GuiPhaseGen.scala", "ee69fe61243f372d0c88000d23b9f8da08f4893a629290b59c8c8c2ca898a94f"),
        Map.entry("witness/TechUIContainer.java", "03a0b43816ff2667583c6dca3f629c026ff100e37ab88c9b6df41827fef8289f"),
        Map.entry("witness/SlotMatterUnit.java", "2468d7f53fe70fd32348bf24bb28a6e11b27fdd2f502c51ab2caa7bcba1894fe"),
        Map.entry("witness/ItemMatterUnit.java", "4c4294def0cec1465bc627cbf240da14dba5f7c4936a21a35344381cc032060b"),
        Map.entry("witness/NodeConn.java", "3f1b7f21cc91c14f9525650237ae7f3613f6d827e6fdd60ce5493b262e0bef3b"),
        Map.entry("witness/default.recipe", "a9dc4aa3477ad246f7cb9bb6bea3c33e033aaa8431f3c8a03acaf0845374da1f"),
        Map.entry("witness/CleanContainer.java", "8a82cbc21a9fcb12a9f339fbd44322290d37cdf750f7d00f6d7311e2e56dd722")
    );

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static void equal(double expected, double actual, String label) {
        check(Double.isFinite(actual) && Math.abs(expected-actual) < 1e-9,
            label + ": expected=" + expected + ", actual=" + actual);
    }
    private static final class Original {
        final Object delegate;
        Original(double energy, int liquid) throws Exception {
            delegate = oracleType.getConstructor(double.class, int.class).newInstance(energy, liquid);
        }
        Object call(String name, Object... arguments) throws Exception {
            Class<?>[] types = new Class<?>[arguments.length];
            for (int i=0; i<types.length; i++) {
                Object value=arguments[i];
                types[i] = value instanceof Integer ? int.class : value instanceof Double ? double.class
                    : value instanceof Boolean ? boolean.class : value.getClass();
            }
            try { return oracleType.getMethod(name, types).invoke(delegate, arguments); }
            catch (InvocationTargetException exception) {
                if (exception.getCause() instanceof Exception cause) throw cause;
                if (exception.getCause() instanceof Error cause) throw cause;
                throw exception;
            }
        }
        double number(String name, Object... arguments) throws Exception { return ((Number)call(name,arguments)).doubleValue(); }
        int integer(String name, Object... arguments) throws Exception { return ((Number)call(name,arguments)).intValue(); }
        boolean flag(String name, Object... arguments) throws Exception { return (Boolean)call(name,arguments); }
        double energy() throws Exception { return number("energy"); }
        int liquid() throws Exception { return integer("liquid"); }
    }
    private static ClassicPhaseGeneratorBuffer modern(double energy,int liquid) {
        var buffer=new ClassicPhaseGeneratorBuffer(); buffer.load(energy,liquid); return buffer;
    }
    private static void state(Original old,ClassicPhaseGeneratorBuffer modern,String label) throws Exception {
        equal(old.energy(),modern.energy(),label+" energy");
        check(old.liquid()==modern.liquid(),label+" liquid");
    }
    private static Path fixture(Path stage) { return stage.resolve("src/test/resources/classic-phase-generator-source"); }
    private static URLClassLoader compileOriginal(Path source) throws Exception {
        for (var hash:HASHES.entrySet()) {
            byte[] bytes=Files.readAllBytes(source.resolve(hash.getKey()));
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            check(hash.getValue().equals(actual),"immutable original fixture "+hash.getKey());
        }
        Path output=Files.createTempDirectory("academy-phase-generator-original-");
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        check(compiler!=null,"portable source oracle requires a JDK with javac");
        List<String> arguments=new ArrayList<>(List.of("-proc:none","-encoding","UTF-8","-d",output.toString()));
        for (String directory:List.of("java","stubs")) {
            try (var files=Files.walk(source.resolve(directory))) {
                files.filter(path->path.toString().endsWith(".java")).sorted().map(Path::toString).forEach(arguments::add);
            }
        }
        int exit=compiler.run(null,null,null,arguments.toArray(String[]::new));
        check(exit==0,"unchanged original generator classes compile under minimal old API");
        var loader=new URLClassLoader(new java.net.URL[]{output.toUri().toURL()},ClassLoader.getPlatformClassLoader());
        oracleType=Class.forName("oracle.OriginalPhaseHarness",true,loader);
        return loader;
    }
    private static void constantsAndSourceWitnesses(Path source) throws Exception {
        var old=new Original(0,0);
        equal(6000,old.number("capacity"),"source buffer capacity");
        equal(50,old.number("bandwidth"),"source IFConstants.LATENCY_MK1 is50");
        equal(old.number("capacity"),ClassicPhaseGeneratorRules.CAPACITY,"modern buffer source capacity");
        equal(old.number("bandwidth"),ClassicPhaseGeneratorRules.BANDWIDTH,"modern actual classic bandwidth");
        check(ClassicPhaseGeneratorRules.TANK_SIZE==8000,"source8000mB tank");
        check(ClassicPhaseGeneratorRules.PER_UNIT==1000,"source1000mB matter unit");
        check(ClassicPhaseGeneratorRules.CONSUME_PER_TICK==100,"source100mB drain/tick");
        equal(.5,ClassicPhaseGeneratorRules.GEN_PER_MB,"source0.5IF/mB");
        check(old.flag("canFillAllDirections")&&old.flag("canDrainAllDirections"),"source liquid interfaces accept every direction");
        check(old.flag("inventoryAcceptsAllSlots"),"source unsided inventory has no insertion restrictions");
        old=new Original(0,1000);
        check(old.integer("wrongFluidFill",100)==0,"source fill rejects different fluid identity");
        check(old.integer("wrongFluidDrain",100)==100&&old.liquid()==900,"source resource-drain ignores fluid identity bug witness");
        old=new Original(10,1000);
        equal(-5,old.number("getProvidedEnergy",-5.0),"source negative wireless request bug returnsnegative");
        equal(15,old.energy(),"source negative request creates energy bug witness");
        var safe=modern(10,1000);
        equal(0,safe.getProvidedEnergy(-5),"finite modern adapter rejects negative wireless requests");
        equal(10,safe.energy(),"negative request cannot create modern energy");
        old=new Original(123.625,1000);old.call("matter",2,3,false);old.call("reload");
        equal(0,old.energy(),"original generator energy is absent from inherited NBT bug witness");
        check(old.liquid()==1000&&old.integer("input")==2&&old.integer("output")==3,"original tank and inventory persist");
        safe=modern(123.625,1000);var restored=modern(safe.energy(),safe.liquid());
        equal(123.625,restored.energy(),"modern explicit load preserves finite fractional energy");
        check(restored.liquid()==1000,"modern explicit load preserves liquid");
        old=new Original(0,1000);old.call("remote",true);old.call("generate");
        equal(0,old.energy(),"original client cannot generate");check(old.liquid()==1000,"original client does not consume liquid");
        String tile=Files.readString(source.resolve("java/cn/academy/energy/block/TilePhaseGen.java"));
        check(tile.indexOf("super.updateEntity();")<tile.indexOf("// Sink in liquid")&&tile.indexOf("// Sink in liquid")<tile.indexOf("// Output energy"),"unchanged original generation/acquisition/charging order");
        String container=Files.readString(source.resolve("witness/ContainerPhaseGen.java"));
        check(container.contains("ModuleCrafting.imagPhase.mat, SLOT_LIQUID_OUT, 112, 51"),"source output menu filter unexpectedly accepts phasefilled units");
        check(container.contains("gRange(4, 4+36)"),"source player transfer range has off-by-one bug witness");
        String matter=Files.readString(source.resolve("witness/ItemMatterUnit.java"));
        check(matter.contains("setMaxStackSize(16)"),"old API fixture uses actual source16 matter stack limit");
        String clean=Files.readString(source.resolve("witness/CleanContainer.java"));
        check(clean.contains("gRange(int from, int toExclusive)"),"LambdaLib slot group range is exclusive");
        String recipe=Files.readString(source.resolve("witness/default.recipe"));
        check(recipe.contains("shaped(phase_gen) {\n    [crystal0,    frame, crystal0]\n    [matter_unit, nil,   matter_unit]\n}"),"source phase generator recipe is two rows");
        String wireless=Files.readString(source.resolve("witness/NodeConn.java"));
        check(wireless.contains("Math.min(igen.getBandwidth(), iNode.getMaxEnergy() - cur)")&&wireless.contains("igen.getProvidedEnergy(required)"),"wireless caller enforces generator bandwidth before debiting");
        String render=Files.readString(source.resolve("witness/RenderPhaseGen.java"));
        check(render.contains("Math.round(4.0 * gen.getLiquidAmount() / gen.getTankSize())"),"source five fill texture selection rounds nearest");
        String gui=Files.readString(source.resolve("witness/GuiPhaseGen.scala"));
        check(gui.contains("WirelessPage.userPage(tile)")&&gui.contains("TechUI.histEnergy")&&gui.contains("%d mB"),"source GUI includes wireless, IF buffer and mB tank");
    }
    private static void generationBoundaries() throws Exception {
        double[] energy={0,.1,1,1000,5949.9,5950,5998.9,5999,5999.1,5999.5,5999.500001,5999.75,5999.9,6000};
        int[] liquid={0,1,2,99,100,101,999,1000,6999,7000,7999,8000};
        for(double stored:energy)for(int amount:liquid) {
            var old=new Original(stored,amount);var buffer=modern(stored,amount);
            equal(old.number("generate"),buffer.generate(),"original integer-drain generation");
            state(old,buffer,"generation energy="+stored+" liquid="+amount);
        }
        for(double stored:energy)for(int amount:liquid)for(double batteryBandwidth:new double[]{0,.1,20,50,500}) {
            var old=new Original(stored,amount);var buffer=modern(stored,amount);double before=buffer.energy();
            double charged=old.number("tick",9999.0,batteryBandwidth);final double[] modernCharge={0};
            double delta=buffer.tick(request->{modernCharge[0]=Math.min(request,batteryBandwidth);return modernCharge[0];});
            equal(charged,modernCharge[0],"source IF item manager bandwidth");
            equal(buffer.energy()-before,delta,"tick result is actual energy delta");
            state(old,buffer,"generation-before-native-item-charge");
        }
    }
    private static void matterAndOrder() throws Exception {
        for(double stored:new double[]{0,5999.75,6000})for(int amount:new int[]{0,6999,7000,7001,7900,8000})
            for(int input:new int[]{1,2,16})for(int output:new int[]{-1,0,14,15,16})for(boolean outputPhase:new boolean[]{false,true}) {
                var old=new Original(stored,amount);old.call("matter",input,output,outputPhase);
                var buffer=modern(stored,amount);final int[] modernInput={input},modernOutput={Math.max(0,output)};final double[] modernCharge={0};
                double oldCharge=old.number("tick",9999.0,500.0);
                buffer.tick(()->{
                    boolean available=output<0||!outputPhase&&modernOutput[0]<16;
                    if(modernInput[0]>0&&available&&ClassicPhaseGeneratorRules.canAcquireUnit(buffer.liquid())) {
                        buffer.fill(1000,false);modernInput[0]--;modernOutput[0]++;
                    }
                },request->{modernCharge[0]=request;return request;});
                state(old,buffer,"source matter acquisition strict tank boundary and charge order");
                check(old.integer("input")==modernInput[0]&&old.integer("output")==modernOutput[0],"source one-unit acquisition and empty return count");
                equal(oldCharge,modernCharge[0],"source inserted matter cannot generate until nexttick");
            }
        var old=new Original(6000,7000);old.call("matter",1,-1,false);old.call("generate");
        check(old.integer("input")==1&&old.liquid()==7000,"strict free=1000 blocks source matter unit");
        old=new Original(6000,6999);old.call("matter",1,-1,false);old.call("generate");
        check(old.integer("input")==0&&old.liquid()==7999&&old.flag("outputEmptyMaterial"),"free=1001 allows source1000mB acquisition");
        old=new Original(0,0);old.call("matter",1,-1,false);equal(0,old.number("tick",9999.0,500.0),"firsttick unit has no generatedIF");
        equal(50,old.number("tick",9999.0,500.0),"secondtick generates and charges50IF");
        old=new Original(6000,0);old.call("matter",0,-1,false);old.call("generate");
        check(old.liquid()==0&&old.integer("output")==1,"source zero-count stack creates empty matter return bug witness");
        old=new Original(1000,0);old.call("clearNetwork");for(int tick=0;tick<20;tick++)old.call("tick",9999.0,50.0);
        equal(50,old.number("lastSync","sync_energy"),"source energy sync happens before charging on20thtick");
        equal(0,old.energy(),"source twentieth itemcharge follows50IF snapshot");
        old=new Original(6000,0);old.call("clearNetwork");for(int tick=0;tick<9;tick++)old.call("generate");old.call("matter",1,-1,false);old.call("generate");
        equal(0,old.number("lastSync","sync"),"source tank10thtick sync precedes acquisition");check(old.liquid()==1000,"source acquired liquid follows snapshot");
    }
    private static void randomizedDifferential() throws Exception {
        for(int seed=0;seed<80;seed++) {
            var random=new Random(107L+seed);var old=new Original(0,0);var buffer=modern(0,0);
            for(int operation=0;operation<1000;operation++) {
                String label="random seed="+seed+" operation="+operation;
                switch(random.nextInt(8)) {
                    case 0 -> {int amount=random.nextInt(10001);boolean simulate=random.nextBoolean();equal(old.number("fill",amount,!simulate),buffer.fill(amount,simulate),label+" fill/simulate");}
                    case 1 -> {int amount=random.nextInt(10001);boolean simulate=random.nextBoolean();equal(old.number("drain",amount,!simulate),buffer.drain(amount,simulate),label+" drain/simulate");}
                    case 2 -> equal(old.number("generate"),buffer.generate(),label+" generation");
                    case 3 -> {double amount=random.nextDouble()*10000;boolean simulate=random.nextBoolean();equal(old.number("addEnergy",amount,simulate),buffer.addEnergy(amount,simulate),label+" add remainder/simulate");}
                    case 4 -> {double amount=random.nextDouble()*10000;equal(old.number("getProvidedEnergy",amount),buffer.getProvidedEnergy(amount),label+" actual wireless IF");}
                    case 5 -> {double stored=random.nextDouble()*6000;int liquid=random.nextInt(8001);old=new Original(stored,liquid);buffer.load(stored,liquid);}
                    case 6,7 -> {
                        double headroom=random.nextDouble()*100,batteryBandwidth=random.nextDouble()*700;
                        double actual=old.number("tick",headroom,batteryBandwidth);final double[] accepted={0};
                        buffer.tick(request->{accepted[0]=Math.min(request,Math.min(headroom,batteryBandwidth));return accepted[0];});
                        equal(actual,accepted[0],label+" finite old battery acceptance");
                    }
                    default -> throw new AssertionError();
                }
                state(old,buffer,label);
            }
        }
    }
    private static void finiteAdaptation() {
        for(double corrupt:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1}) {
            var buffer=modern(corrupt,-1);equal(0,buffer.energy(),"modern rejects corrupt saved energy");check(buffer.liquid()==0,"modern rejects negative saved liquid");
            buffer.load(10,1000);equal(0,buffer.getProvidedEnergy(corrupt),"modern corrupt wireless request");equal(10,buffer.energy(),"corrupt wireless request leaves energy");
            buffer.tick(request->corrupt);equal(60,buffer.energy(),"modern corrupt battery acceptance retains generated energy");check(buffer.liquid()==900,"modern invalid receiver cannot duplicate fuel");
        }
        var buffer=modern(9999,9999);equal(6000,buffer.energy(),"modern oversized saved energy clamps");check(buffer.liquid()==8000,"modern oversized saved liquid clamps");
        equal(0,buffer.fill(-1,false),"negative fill rejected");equal(0,buffer.drain(-1,false),"negative drain rejected");
        buffer.load(10,1000);buffer.tick(request->request+1);equal(10,buffer.energy(),"modern receiver overstatement bounded to offer");
        buffer.load(123.625,1000);
        int a=(short)ClassicPhaseGeneratorRules.word(buffer.energy(),0),b=(short)ClassicPhaseGeneratorRules.word(buffer.energy(),1),c=(short)ClassicPhaseGeneratorRules.word(buffer.energy(),2),d=(short)ClassicPhaseGeneratorRules.word(buffer.energy(),3);
        equal(123.625,ClassicPhaseGeneratorRules.fromWords(a,b,c,d),"signed short native menu transport retains fractional IF");
        for(int liquid=0;liquid<=8000;liquid++) {
            int expected=Math.max(0,Math.min(4,(int)Math.round(4.0*liquid/8000)));
            check(expected==ClassicPhaseGeneratorRules.textureIndex(liquid),"all classic fill texture thresholds "+liquid);
        }
    }
    public static void main(String[] args) throws Exception {
        Path stage=Path.of(System.getProperty("academy.phasegen.stage","."));
        try(var originalLoader=compileOriginal(fixture(stage))) {
            constantsAndSourceWitnesses(fixture(stage));generationBoundaries();matterAndOrder();randomizedDifferential();finiteAdaptation();
        }
        System.out.println("ClassicPhaseGeneratorSourceOracleTest: "+checks+" unchanged-source/differential checks passed; classic bandwidth50, integer fuel boundaries, strict acquisition, item/wireless conservation and finite load");
    }
}
