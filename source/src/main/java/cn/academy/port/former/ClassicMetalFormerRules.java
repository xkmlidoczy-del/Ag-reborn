/* AcademyCraft 1.0.7 ModuleCrafting / ModuleVanilla MetalFormer recipes, GPLv3. */
package cn.academy.port.former;
import java.util.List;
import static cn.academy.port.former.ClassicMetalFormerWork.Mode.*;

/** Immutable source descriptors; modern registry IDs do not imply legacy world conversion. */
public final class ClassicMetalFormerRules {
    public record Rule(ClassicMetalFormerWork.Mode mode,String input,int inputCount,String output,int outputCount) {}
    private ClassicMetalFormerRules(){}
    public static final List<Rule> BUILT_INS=List.of(
        new Rule(INCISE,"academy:imag_silicon_ingot",1,"academy:wafer",2),
        new Rule(INCISE,"academy:wafer",1,"academy:imag_silicon_piece",4),
        new Rule(ETCH,"academy:data_chip",1,"academy:calc_chip",1),
        new Rule(PLATE,"minecraft:iron_ingot",1,"academy:reinforced_iron_plate",1),
        new Rule(PLATE,"academy:constraint_ingot",1,"academy:constraint_plate",1),
        new Rule(REFINE,"academy:imag_silicon_ore",1,"academy:imag_silicon_ingot",4),
        new Rule(REFINE,"academy:constraint_metal_ore",1,"academy:constraint_ingot",2),
        new Rule(REFINE,"academy:reso_crystal_ore",1,"academy:reso_crystal",3),
        new Rule(REFINE,"academy:crystal_ore",1,"academy:crystal_low",4),
        new Rule(REFINE,"minecraft:gold_ore",1,"minecraft:gold_ingot",2),
        new Rule(REFINE,"minecraft:iron_ore",1,"minecraft:iron_ingot",2),
        new Rule(REFINE,"minecraft:emerald_ore",1,"minecraft:emerald",2),
        new Rule(REFINE,"minecraft:nether_quartz_ore",1,"minecraft:quartz",2),
        new Rule(REFINE,"minecraft:diamond_ore",1,"minecraft:diamond",2),
        new Rule(REFINE,"minecraft:redstone_ore",1,"minecraft:redstone_block",1),
        new Rule(REFINE,"minecraft:lapis_ore",1,"minecraft:lapis_lazuli",12),
        new Rule(REFINE,"minecraft:coal_ore",1,"minecraft:coal",2),
        new Rule(INCISE,"academy:reinforced_iron_plate",1,"academy:needle",6),
        new Rule(INCISE,"minecraft:rail",1,"academy:needle",2),
        new Rule(PLATE,"academy:reinforced_iron_plate",2,"academy:coin",3),
        new Rule(ETCH,"academy:wafer",1,"academy:silbarn",1)
    );
    public static final List<String> CONDITIONAL_METALS=List.of("copper","tin","lead","platinum","silver","nickel");
    public static int refinedCount(int furnaceYield){return furnaceYield<32?2*furnaceYield:64;}
    public static int rawRefinedCount(int furnaceYield){return Math.min(64,3*furnaceYield);}
    public static int rawBlockRefinedCount(int furnaceYield){return Math.min(64,27*furnaceYield);}
}
