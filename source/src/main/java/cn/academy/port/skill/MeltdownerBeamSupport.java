/* AcademyCraft1.0.7 world/transport adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.AcademyConfig;
import cn.academy.port.api.SkillBlockDestroyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
/** No client dependencies. Modern protection/loot adapters are documented separately from source math. */
final class MeltdownerBeamSupport {
    private MeltdownerBeamSupport(){}
    static Vec3 direction(ServerPlayer p){var v=ClassicBeamRay.direction(p.getYHeadRot(),p.getXRot());return new Vec3(v.x(),v.y(),v.z());}
    static boolean loaded(ServerPlayer p,BlockPos pos){return !p.serverLevel().isOutsideBuildHeight(pos)&&p.serverLevel().hasChunkAt(pos);}
    // Source MineRaysBase posts BlockDestroyEvent directly; it consults the global/world pipeline, not the skill-local flag.
    static boolean sourceDenied(ServerPlayer p,String id,BlockPos pos){return !loaded(p,pos)||!AcademyConfig.canDestroyBlocks(p.serverLevel())||NeoForge.EVENT_BUS.post(new SkillBlockDestroyEvent(p,"meltdowner."+id,pos)).isCanceled()||nativeDenied(p,pos);}
    static boolean nativeDenied(ServerPlayer p,BlockPos pos){if(!loaded(p,pos)||!AcademyConfig.canDestroyBlocks(p.serverLevel())||!p.serverLevel().mayInteract(p,pos))return true;var state=p.serverLevel().getBlockState(pos);return !state.isAir()&&NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(p.serverLevel(),pos,state,p)).isCanceled();}
    static int harvestLevel(ServerPlayer p,BlockPos pos){var state=p.serverLevel().getBlockState(pos);if(state.is(BlockTags.NEEDS_DIAMOND_TOOL)||state.is(BlockTags.INCORRECT_FOR_IRON_TOOL))return 3;if(state.is(BlockTags.NEEDS_IRON_TOOL))return 2;if(state.is(BlockTags.NEEDS_STONE_TOOL))return 1;return state.requiresCorrectToolForDrops()?0:-1;}
    static void drop(ServerPlayer p,BlockPos pos,float chance,boolean fullTier){var world=p.serverLevel();var state=world.getBlockState(pos);var tool=new ItemStack(fullTier?Items.NETHERITE_PICKAXE:Items.IRON_PICKAXE);for(var stack:Block.getDrops(state,world,pos,world.getBlockEntity(pos),p,tool))if(world.random.nextFloat()<=chance)Block.popResource(world,pos,stack);}
    static void mineBreak(ServerPlayer p,BlockPos pos){if(nativeDenied(p,pos))return;var world=p.serverLevel();var state=world.getBlockState(pos);var sound=state.getSoundType(world,pos,p);world.playSound(null,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,sound.getBreakSound(),SoundSource.BLOCKS,.5F,1F);drop(p,pos,1,false);world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
    /** Modern full-tier Fortune tool is a loot-table adapter for classic direct fortune parameter.
     * No inventory item or persistent credential/state is created; block metadata has no modern analogue. */
    static net.minecraft.world.item.ItemStack advancedMineTool(ServerPlayer p,int fortune) {
        var tool=new ItemStack(Items.NETHERITE_PICKAXE);
        if(fortune>0)tool.enchant(p.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE),fortune);
        return tool;
    }
    static void advancedMineBreak(ServerPlayer p,BlockPos pos,int fortune) {
        if(nativeDenied(p,pos))return;var world=p.serverLevel();var state=world.getBlockState(pos);
        var sound=state.getSoundType(world,pos,p);world.playSound(null,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,sound.getBreakSound(),SoundSource.BLOCKS,.5F,1F);
        var tool=advancedMineTool(p,fortune);
        for(var stack:Block.getDrops(state,world,pos,world.getBlockEntity(pos),p,tool)) {
            // Original dropBlockAsItemWithChance always draws one nextFloat per emitted stack at chance1.
            if(world.random.nextFloat()<=1F)Block.popResource(world,pos,stack);
        }
        world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
    }
    static CompoundTag packet(ServerPlayer p,String kind,long token,long input){var tag=MeltdownerStarterSupport.packet(p,kind,token,input);tag.putUUID("entity_uuid",p.getUUID());return tag;}
    static void ray(CompoundTag tag,Vec3 from,Vec3 direction,double length){MeltdownerStarterSupport.position(tag,from);tag.putDouble("dx",direction.x);tag.putDouble("dy",direction.y);tag.putDouble("dz",direction.z);tag.putDouble("length",length);}
}
