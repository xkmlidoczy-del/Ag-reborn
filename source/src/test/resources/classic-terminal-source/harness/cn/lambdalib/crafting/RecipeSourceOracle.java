package cn.lambdalib.crafting;
import java.nio.file.*;
public final class RecipeSourceOracle {
 public static void main(String[] args)throws Throwable {var p=new RecipeParser(Files.readString(Path.of(args[0]))); int index=0;while(p.parseNext()){index++;if(index==22||index==38||index==39||index==40){System.out.print(index+"|"+p.getType()+"|"+p.getOutput().name+"|"+p.getOutput().amount+"|"+p.getWidth()+"|"+p.getHeight()+"|");for(int i=0;i<p.getInput().length;i++){if(i>0)System.out.print(",");System.out.print(p.getInput()[i]==null?"nil":p.getInput()[i].name);}System.out.println();}}p.close();if(index!=49)throw new AssertionError("source block count "+index);}
}