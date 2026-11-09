/* AcademyCraft 1.0.7 CatElectromaster metal lists. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.MagneticRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

/** Original configurable defaults; registry IDs and #block tags replace 1.7 ore-dictionary names. */
public final class ClassicMetalTargets {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NORMAL, WEAK, ENTITIES;
    static {
        var b=new ModConfigSpec.Builder();b.push("electromaster");
        NORMAL=b.comment("AcademyCraft1.0.7 normalMetalBlocks; use registry IDs or #block tags")
                .defineListAllowEmpty("normalMetalBlocks",List.of("minecraft:rail","minecraft:iron_bars","minecraft:iron_block","minecraft:iron_door","minecraft:activator_rail","minecraft:detector_rail","minecraft:powered_rail","minecraft:sticky_piston","minecraft:piston"),()->"minecraft:iron_block",ClassicMetalTargets::validName);
        WEAK=b.comment("AcademyCraft1.0.7 weakMetalBlocks; source movement threshold is intentionally redundant")
                .defineListAllowEmpty("weakMetalBlocks",List.of("minecraft:dispenser","minecraft:hopper","minecraft:iron_ore"),()->"minecraft:iron_ore",ClassicMetalTargets::validName);
        ENTITIES=b.comment("Classic metalEntities, with modern minecart/iron-golem IDs and the real magnetic hook")
                .defineListAllowEmpty("metalEntities",List.of("minecraft:minecart","minecraft:chest_minecart","minecraft:furnace_minecart","minecraft:tnt_minecart","minecraft:hopper_minecart","minecraft:spawner_minecart","minecraft:command_block_minecart","academy:maghook","minecraft:iron_golem"),()->"minecraft:iron_golem",ClassicMetalTargets::validName);
        b.pop();SPEC=b.build();
    }
    private ClassicMetalTargets() {}
    private static boolean validName(Object o) { return o instanceof String s&&ResourceLocation.tryParse(s.startsWith("#")?s.substring(1):s)!=null; }
    private static boolean matches(BlockState state,List<? extends String> names) {
        if(state==null)return false;
        for(String name:names) {
            ResourceLocation id=ResourceLocation.tryParse(name.startsWith("#")?name.substring(1):name);
            if(id!=null&&(name.startsWith("#")?state.is(TagKey.create(net.minecraft.core.registries.Registries.BLOCK,id)):BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id)))return true;
        }
        return false;
    }
    public static boolean normal(BlockState state) { return matches(state,NORMAL.get()); }
    public static boolean weak(BlockState state) { return matches(state,WEAK.get()); }
    public static boolean metal(BlockState state) { return normal(state)||weak(state); }
    public static boolean movement(BlockState state,double exp) { return MagneticRules.acceptsMovementBlock(exp,normal(state),weak(state)); }
    public static boolean entity(Entity entity) {
        if(entity==null)return false;String name=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();return ENTITIES.get().contains(name);
    }
    public static boolean manipulation(BlockState state) {
        // Source excludes LambdaLib BlockMulti and BlockDoor. Current port machine towers
        // are the corresponding multiblock class; single-block container data is preserved.
        return state!=null&&!(state.getBlock() instanceof DoorBlock)
                &&!(state.getBlock() instanceof cn.academy.port.machine.MachineDeveloperBlock)
                &&state.getBlock().asItem()!=net.minecraft.world.item.Items.AIR&&metal(state);
    }
}
