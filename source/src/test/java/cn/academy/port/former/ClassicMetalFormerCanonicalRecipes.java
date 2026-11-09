package cn.academy.port.former;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Independent source parser: expected transformations come from the immutable old declarations. */
final class ClassicMetalFormerCanonicalRecipes {
    private static final Map<String,String> ITEMS=Map.ofEntries(
        Map.entry("ingotImagSil","academy:imag_silicon_ingot"),Map.entry("wafer","academy:wafer"),
        Map.entry("silPiece","academy:imag_silicon_piece"),Map.entry("dataChip","academy:data_chip"),
        Map.entry("calcChip","academy:calc_chip"),Map.entry("rfIronPlate","academy:reinforced_iron_plate"),
        Map.entry("ingotConst","academy:constraint_ingot"),Map.entry("constPlate","academy:constraint_plate"),
        Map.entry("oreImagSil","academy:imag_silicon_ore"),Map.entry("oreConstraintMetal","academy:constraint_metal_ore"),
        Map.entry("oreResoCrystal","academy:reso_crystal_ore"),Map.entry("resoCrystal","academy:reso_crystal"),
        Map.entry("oreImagCrystal","academy:crystal_ore"),Map.entry("crystalLow","academy:crystal_low"),
        Map.entry("needle","academy:needle"),Map.entry("coin","academy:coin"),Map.entry("silbarn","academy:silbarn"));
    private record Stack(String id,int count){}
    private static List<String> args(String text){
        var result=new ArrayList<String>();int depth=0,start=0;boolean quoted=false;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='"')quoted=!quoted;if(quoted)continue;
            if(c=='(')depth++;else if(c==')')depth--;else if(c==','&&depth==0){result.add(text.substring(start,i).trim());start=i+1;}}
        result.add(text.substring(start).trim());return result;
    }
    private static String body(String text,int open){int depth=1,end=open+1;boolean quoted=false;
        while(depth!=0){char c=text.charAt(end++);if(c=='"')quoted=!quoted;if(quoted)continue;if(c=='(')depth++;else if(c==')')depth--;}
        return text.substring(open+1,end-1);
    }
    private static Stack stack(String expression){
        if(!expression.startsWith("new ItemStack("))throw new AssertionError("Unexpected immutable stack declaration "+expression);
        var a=args(body(expression,expression.indexOf('(')));String old=a.getFirst(),id;
        if(old.equals("Block.getBlockFromName(\"rail\")"))id="minecraft:rail";
        else if(old.equals("Items.dye")){if(a.size()!=3||!a.get(2).equals("4"))throw new AssertionError("Original lapis metadata");id="minecraft:lapis_lazuli";}
        else if(old.startsWith("Items.")||old.startsWith("Blocks."))id="minecraft:"+old.substring(old.indexOf('.')+1);
        else {old=old.replace("ModuleCrafting.","");id=ITEMS.get(old);if(id==null)throw new AssertionError("Unmapped canonical identity "+old);}
        return new Stack(id,a.size()>1?Integer.parseInt(a.get(1)):1);
    }
    private static void direct(String source,List<ClassicMetalFormerRules.Rule> rules){
        var matcher=Pattern.compile("(?:mfr|MetalFormerRecipes\\.INSTANCE)\\.add\\s*\\(").matcher(source);
        while(matcher.find()){var a=args(body(source,matcher.end()-1));if(a.size()!=3)throw new AssertionError("Original add signature");
            if(!a.getFirst().startsWith("new ItemStack("))continue; // Conditional dictionary loop is checked separately.
            Stack in=stack(a.get(0)),out=stack(a.get(1));String mode=a.get(2).replace("Mode.","");
            rules.add(new ClassicMetalFormerRules.Rule(ClassicMetalFormerWork.Mode.valueOf(mode),in.id,in.count,out.id,out.count));}
    }
    static List<ClassicMetalFormerRules.Rule> read(Path fixture)throws Exception{
        var rules=new ArrayList<ClassicMetalFormerRules.Rule>();String crafting=Files.readString(fixture.resolve("java/cn/academy/crafting/ModuleCrafting.java"));
        direct(crafting,rules);
        var matcher=Pattern.compile("addOreDictRefineRecipe\\(\"ore([^\"]+)\",\\s*(new ItemStack\\([^;]+)\\);" ).matcher(crafting);
        while(matcher.find()){String name=matcher.group(1).toLowerCase(Locale.ROOT);Stack out=stack(matcher.group(2));
            String input=name.equals("quartz")?"minecraft:nether_quartz_ore":"minecraft:"+name+"_ore";
            rules.add(new ClassicMetalFormerRules.Rule(ClassicMetalFormerWork.Mode.REFINE,input,1,out.id,out.count));}
        direct(Files.readString(fixture.resolve("java/cn/academy/vanilla/ModuleVanilla.java")),rules);
        if(rules.size()!=21)throw new AssertionError("Canonical source transformed "+rules.size()+" rules, expected 21");
        return List.copyOf(rules);
    }
    static List<String> conditionalMetals(Path fixture)throws Exception{
        String source=Files.readString(fixture.resolve("java/cn/academy/crafting/ModuleCrafting.java"));var result=new ArrayList<String>();
        var m=Pattern.compile("addDefaultOreDictRefineRecipe\\(\"([^\"]+)\"\\)").matcher(source);while(m.find())result.add(m.group(1).toLowerCase(Locale.ROOT));return List.copyOf(result);
    }
}
