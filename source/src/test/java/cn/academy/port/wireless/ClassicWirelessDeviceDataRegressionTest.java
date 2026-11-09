package cn.academy.port.wireless;

import cn.academy.port.machine.MachineDeveloperRules;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

/** Exact new recipe shapes/output identities, source limits and whole cube cardinal cells. */
public final class ClassicWirelessDeviceDataRegressionTest {
    private static int assertions;
    private static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static void equal(double a,double b){check(Math.abs(a-b)<1e-7,"numeric source difference "+a+" != "+b);}
    private static String resource(String path)throws Exception{try(var input=ClassicWirelessDeviceDataRegressionTest.class.getResourceAsStream('/'+path)){if(input==null)throw new AssertionError("Missing "+path);return new String(input.readAllBytes(),StandardCharsets.UTF_8);}}
    private static void recipe(String name,String output,String[] rows,String[][] keys)throws Exception{
        var json=JsonParser.parseString(resource("data/academy/recipe/classic/"+name+".json")).getAsJsonObject();check(json.get("type").getAsString().equals("minecraft:crafting_shaped"),"actual vanilla shape "+name);check(json.getAsJsonObject("result").get("id").getAsString().equals("academy:"+output),"actual itemidentity "+name);check(json.getAsJsonObject("result").get("count").getAsInt()==1,"source yieldone "+name);var pattern=json.getAsJsonArray("pattern");check(pattern.size()==rows.length,"source gridheight "+name);for(int row=0;row<rows.length;row++)check(pattern.get(row).getAsString().equals(rows[row]),"exact source row "+name+" "+row);var ingredients=json.getAsJsonObject("key");check(ingredients.size()==keys.length,"no invented ingredients "+name);for(var key:keys)check(ingredients.getAsJsonObject(key[0]).get(key[1]).getAsString().equals(key[2]),"exact source ingredient "+name+" "+key[0]);
    }
    public static void main(String[] args)throws Exception{
        int[][] source={{15000,150,9,5},{50000,300,12,10},{200000,900,19,20}};int index=0;
        for(var type:ClassicWirelessRules.NodeType.values()){equal(type.energy,source[index][0]);equal(type.bandwidth,source[index][1]);equal(type.range,source[index][2]);equal(type.capacity,source[index++][3]);for(int level=0;level<=4;level++)check(ClassicWirelessRules.level(type.energy*level/4d,type.energy)==level,"source rounded dynamic node icon");}
        for(int core=0;core<=3;core++)for(int plates=0;plates<=3;plates++){boolean working=core>0&&plates==3;equal(ClassicWirelessRules.matrixCapacity(core,plates),working?8*core:0);equal(ClassicWirelessRules.matrixBandwidth(core,plates),working?60*core*core:0);equal(ClassicWirelessRules.matrixRange(core,plates),working?24*Math.sqrt(core):0);}
        for(var facing:MachineDeveloperRules.Facing.values()){var cells=new HashSet<ClassicWirelessRules.Cell>();for(int part=0;part<8;part++){var offset=ClassicWirelessRules.offset(part,facing);check(cells.add(offset),"all sourceparts unique "+facing);check(offset.y()==0||offset.y()==1,"only source two layers");check(Math.abs(offset.x())<=1&&Math.abs(offset.z())<=1,"source 2×2 footprint");}check(cells.size()==8,"source full 2×2×2 cube");}
        recipe("wireless_node_basic_14","wireless_node_basic",new String[]{" C ","IFI","LRL"},new String[][]{{"C","item","academy:calc_chip"},{"I","tag","c:ingots/iron"},{"F","item","academy:machine_frame"},{"L","item","academy:crystal_low"},{"R","item","academy:reso_crystal"}});
        recipe("wireless_node_standard_15","wireless_node_standard",new String[]{" L ","CEC"," N "},new String[][]{{"L","item","academy:crystal_normal"},{"C","item","academy:calc_chip"},{"E","item","academy:energy_convert_component"},{"N","item","academy:wireless_node_basic"}});
        recipe("wireless_node_advanced_16","wireless_node_advanced",new String[]{"P","R","N"},new String[][]{{"P","item","academy:crystal_pure"},{"R","item","academy:resonance_component"},{"N","item","academy:wireless_node_standard"}});
        recipe("wireless_matrix_27","wireless_matrix",new String[]{" R ","rFr","DRD"},new String[][]{{"R","item","academy:reso_crystal"},{"r","tag","c:dusts/redstone"},{"F","item","academy:machine_frame"},{"D","item","academy:data_chip"}});
        recipe("developer_advanced_46","developer_advanced",new String[]{"PPP","GNG","SCR"},new String[][]{{"P","item","academy:constraint_plate"},{"G","item","minecraft:glowstone"},{"N","item","academy:developer_normal"},{"S","item","academy:wireless_node_standard"},{"C","item","academy:crystal_pure"},{"R","item","academy:reso_crystal"}});
        String original=resource("cn/academy/port/wireless/source-recipes.recipe");for(String mapping:new String[]{"shaped(node0)","shaped(node1)","shaped(node2)","shaped(mat)","shaped(dev_advanced)"})check(original.contains(mapping),"retained unchanged canonical recipe excerpt "+mapping);
        for(String block:new String[]{"wireless_node_basic","wireless_node_standard","wireless_node_advanced","wireless_matrix"}){var loot=JsonParser.parseString(resource("data/academy/loot_table/blocks/"+block+".json")).getAsJsonObject();check(loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString().equals("academy:"+block),"exact single source block drop "+block);}
        System.out.println("ClassicWirelessDeviceDataRegressionTest: "+assertions+" assertions passed;5exactsource recipes, limits and8cube cells");
    }
}
