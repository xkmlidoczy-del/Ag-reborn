/* AcademyCraft CatElectromaster.isOreBlock modern compatibility adapter. See NOTICE. */
package cn.academy.port.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SculkBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

/** No registry-ID substring guesses: native classes, original vanilla counterparts and ore tags. */
public final class ClassicMineOreAdapter {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADDITIONAL_ORES, HARVEST_LEVELS;
    static {
        var b=new ModConfigSpec.Builder();b.push("mineDetection");
        ADDITIONAL_ORES=b.comment("Extra modern counterparts of legacy BlockOre/OreDictionary ores. Registry IDs or #block tags; defaults already use native ore classes and semantic ore-family block/item tags.")
                .defineListAllowEmpty("additionalOreBlocks",List.<String>of(),()->"#c:ores",ClassicMineOreAdapter::validTarget);
        HARVEST_LEVELS=b.comment("Modded legacy metadata-0 harvest compatibility: registry ID or #block tag followed by =level (-1..3). Modern vanilla needs_stone/iron/diamond tags otherwise map to 1/2/3, tool-required to0, hand-harvest to-1. First match wins.")
                .defineListAllowEmpty("harvestLevelOverrides",List.<String>of(),()->"minecraft:iron_ore=1",ClassicMineOreAdapter::validHarvest);
        b.pop();SPEC=b.build();
    }
    private ClassicMineOreAdapter() {}
    private static boolean validTarget(Object value) {
        return value instanceof String s&&ResourceLocation.tryParse(s.startsWith("#")?s.substring(1):s)!=null;
    }
    private static boolean validHarvest(Object value) {
        if(!(value instanceof String s))return false;int p=s.lastIndexOf('=');
        if(p<1||!validTarget(s.substring(0,p)))return false;
        try{int n=Integer.parseInt(s.substring(p+1));return n>=-1&&n<=3;}catch(NumberFormatException ignored){return false;}
    }
    public static boolean oreTagPath(String path){return MineOreTagRules.semanticOrePath(path);}
    private static boolean matches(BlockState state,String target) {
        boolean tag=target.startsWith("#");var id=ResourceLocation.tryParse(tag?target.substring(1):target);
        return id!=null&&(tag?state.is(TagKey.create(Registries.BLOCK,id))
                :BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id));
    }
    public static boolean isOre(BlockState state) {
        if(state==null)return false;
        Block block=state.getBlock();
        // DropExperienceBlock replaces vanilla BlockOre in the cached1.21.1 Blocks source.
        // SculkBlock also derives from it but is a new non-ore family, so exclude that
        // known superclass broadening (while explicit semantic tags/config may opt it in).
        if((block instanceof DropExperienceBlock&&!(block instanceof SculkBlock))
                ||block instanceof RedStoneOreBlock)return true;
        // Classic returns false for Item.getItemFromBlock(null); classes bypass that guard.
        // Modern worldgen tags such as stone_ore_replaceables are not OreDictionary ore
        // entries. Only semantic ore-family tags map automatically; custom legacy names
        // use the explicit additionalOreBlocks registry/#tag bridge.
        if(block.asItem()==Items.AIR)return false;
        if(state.getTags().anyMatch(t->oreTagPath(t.location().getPath()))
                ||block.asItem().getDefaultInstance().getTags().anyMatch(t->oreTagPath(t.location().getPath())))return true;
        return ADDITIONAL_ORES.get().stream().anyMatch(target->matches(state,target));
    }
    public static int harvestLevel(BlockState state) {
        for(String override:HARVEST_LEVELS.get()) {
            int p=override.lastIndexOf('=');
            if(p>0&&matches(state,override.substring(0,p)))return Integer.parseInt(override.substring(p+1));
        }
        if(state.is(BlockTags.NEEDS_DIAMOND_TOOL))return 3;
        if(state.is(BlockTags.NEEDS_IRON_TOOL))return 2;
        if(state.is(BlockTags.NEEDS_STONE_TOOL))return 1;
        return state.requiresCorrectToolForDrops()?0:-1;
    }
    /** Client discovery never creates/loads chunks or probes beyond the modern build height. */
    public static ClassicMineScan.OreAccess access(Level level) {
        if(level==null)throw new IllegalArgumentException("Missing mine level");
        return new ClassicMineScan.OreAccess() {
            private final BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
            // A single synchronous client tick cannot reload tags/config. Source classification
            // depends on block identity/metadata0, so per-access memoization changes no scan
            // selection and avoids hundreds of thousands of repeated tag-stream allocations.
            private final Map<Block,Boolean> ores=new IdentityHashMap<>();
            private final Map<Block,Integer> harvest=new IdentityHashMap<>();
            public boolean accepts(int x,int y,int z) {
                pos.set(x,y,z);
                if(level.isOutsideBuildHeight(pos)||!level.getChunkSource().hasChunk(x>>4,z>>4))return false;
                var state=level.getBlockState(pos);return ores.computeIfAbsent(state.getBlock(),b->isOre(state));
            }
            public int harvestLevel(int x,int y,int z) {
                var state=level.getBlockState(pos.set(x,y,z));
                return harvest.computeIfAbsent(state.getBlock(),b->ClassicMineOreAdapter.harvestLevel(state));
            }
        };
    }
}
