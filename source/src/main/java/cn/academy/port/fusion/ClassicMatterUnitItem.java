/* AcademyCraft1.0.7 ItemMatterUnit none/phase_liquid variants. GPLv3. See NOTICE. */
package cn.academy.port.fusion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.HitResult;

/** Modern registry variants preserve the source material distinction and stack limit16. */
public final class ClassicMatterUnitItem extends Item {
    private final boolean phase;
    public ClassicMatterUnitItem(boolean phase){super(new Properties().stacksTo(16));this.phase=phase;}
    public boolean phase(){return phase;}
    public static boolean empty(ItemStack stack){return stack.is(ClassicFusion.MATTER_UNIT.get());}
    public static boolean filled(ItemStack stack){return stack.is(ClassicFusion.PHASE_MATTER_UNIT.get());}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);var hit=getPlayerPOVHitResult(level,player,ClipContext.Fluid.SOURCE_ONLY);
        if(hit.getType()!=HitResult.Type.BLOCK)return InteractionResultHolder.pass(stack);
        BlockPos target=hit.getBlockPos();if(!level.mayInteract(player,target)||!player.mayUseItemAt(target,hit.getDirection(),stack))return InteractionResultHolder.fail(stack);
        if(!phase){
            // Original Forge fluid ray collision only includes source metadata0.
            if(!level.getBlockState(target).is(ClassicFusion.PHASE_BLOCK.get())||!level.getFluidState(target).isSource())return InteractionResultHolder.pass(stack);
            if(!level.isClientSide){level.setBlock(target,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);stack=ItemUtils.createFilledResult(stack,player,new ItemStack(ClassicFusion.PHASE_MATTER_UNIT.get()));net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new MatterUnitHarvestEvent(player,"phase_liquid"));}
        }else{
            BlockPos destination=level.getBlockState(target).canBeReplaced()?target:target.relative(hit.getDirection());
            if(!level.mayInteract(player,destination)||!player.mayUseItemAt(destination,hit.getDirection(),stack)||!level.getBlockState(destination).canBeReplaced())return InteractionResultHolder.fail(stack);
            if(!level.isClientSide){level.setBlock(destination,ClassicFusion.PHASE_BLOCK.get().defaultBlockState(),Block.UPDATE_ALL);stack=ItemUtils.createFilledResult(stack,player,new ItemStack(ClassicFusion.MATTER_UNIT.get()));net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new MatterUnitHarvestEvent(player,"none"));}
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
