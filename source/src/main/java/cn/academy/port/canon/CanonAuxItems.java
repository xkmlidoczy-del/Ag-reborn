package cn.academy.port.canon;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Auxiliary items for the canon ability systems, in the role the coin and steel needle play for the original four:
 * physical things an esper carries. Liquid Shadow already has its Dense Shadow Medium and Observer Module.
 * Only origami paper is wired into a skill so far (paper skills accept it as a sheet); the rest are craftable materials
 * with no effect on cost or damage, so the audited damage budget is untouched.
 */
public final class CanonAuxItems {
    public static final int FLASK_CHARGES=4;
    private static final String MARKER_KEY="academy:float_marker";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("academy");
    public static final DeferredItem<Item> MENTAL_REMOTE = item("mental_remote",1);
    public static final DeferredItem<Item> DARK_MATTER_CRYSTAL = item("dark_matter_crystal",16);
    public static final DeferredItem<Item> NITROGEN_CANISTER = item("nitrogen_canister",16);
    public static final DeferredItem<Item> AERO_PRESSURE_CHIP = item("aero_pressure_chip",16);
    public static final DeferredItem<Item> WATER_FLASK = ITEMS.register("water_flask",()->new WaterFlask(new Item.Properties().durability(FLASK_CHARGES).component(DataComponents.DAMAGE,FLASK_CHARGES)));
    public static final DeferredItem<Item> BUOYANCY_FLOAT = ITEMS.register("buoyancy_float",()->new FloatMarker(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ORIGAMI_PAPER = item("origami_paper",64);
    private CanonAuxItems() {}

    static class AuxItem extends Item {
        private final String key;
        AuxItem(String key,Properties properties){super(properties);this.key=key;}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("item.academy."+key+".desc").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
    private static DeferredItem<Item> item(String name,int stack){
        return ITEMS.register(name,()->new AuxItem(name,new Item.Properties().stacksTo(stack)));
    }
    public static void register(IEventBus bus){ITEMS.register(bus);bus.addListener(CanonAuxItems::creative);}
    private static void creative(BuildCreativeModeTabContentsEvent event){
        if(event.getTabKey()==CreativeModeTabs.INGREDIENTS){
            event.accept(MENTAL_REMOTE);event.accept(DARK_MATTER_CRYSTAL);event.accept(NITROGEN_CANISTER);event.accept(AERO_PRESSURE_CHIP);
            event.accept(WATER_FLASK);event.accept(BUOYANCY_FLOAT);event.accept(ORIGAMI_PAPER);
        }
    }

    // ------------------------------------------------------------------ helpers shared by the skill hooks
    /** True when the stack is in either hand. Skills never read or write other inventory slots except through {@link #consume}. */
    public static boolean inHands(ServerPlayer p,DeferredItem<Item> item){return p.getMainHandItem().is(item.get())||p.getOffhandItem().is(item.get());}
    public static boolean inInventory(ServerPlayer p,DeferredItem<Item> item){
        var inv=p.getInventory();for(int i=0;i<inv.getContainerSize();i++)if(inv.getItem(i).is(item.get()))return true;return false;
    }
    /** Takes one item from the hands, then the rest of the inventory. Creative players keep it. Returns whether one was available. */
    public static boolean consume(ServerPlayer p,DeferredItem<Item> item){
        if(p==null||item==null)return false;
        if(p.getAbilities().instabuild)return inInventory(p,item)||inHands(p,item);
        for(ItemStack hand:new ItemStack[]{p.getMainHandItem(),p.getOffhandItem()})if(hand.is(item.get())){hand.shrink(1);return true;}
        var inv=p.getInventory();
        for(int i=0;i<inv.getContainerSize();i++){var stack=inv.getItem(i);if(stack.is(item.get())){stack.shrink(1);return true;}}
        return false;
    }

    // ------------------------------------------------------------------ water flask: four charges of real placed water
    /** A bucket with four small charges. Placing makes a real water source block, so Hydro Hand needs no special case. */
    static final class WaterFlask extends AuxItem {
        WaterFlask(Properties properties){super("water_flask",properties);}
        static int charges(ItemStack s){return FLASK_CHARGES-s.getDamageValue();}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            ItemStack stack=player.getItemInHand(hand);
            BlockHitResult hit=getPlayerPOVHitResult(level,player,ClipContext.Fluid.SOURCE_ONLY);
            if(hit.getType()!=HitResult.Type.BLOCK||stack.getDamageValue()==0)return InteractionResultHolder.pass(stack);
            BlockPos pos=hit.getBlockPos();var state=level.getBlockState(pos);
            if(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,hit.getDirection(),stack)
                    ||!level.getFluidState(pos).is(FluidTags.WATER)||!level.getFluidState(pos).isSource()
                    ||!(state.getBlock() instanceof BucketPickup pickup))return InteractionResultHolder.pass(stack);
            if(level.isClientSide)return InteractionResultHolder.sidedSuccess(stack,true);
            ItemStack picked=pickup.pickupBlock(player,level,pos,state);
            if(!picked.is(Items.WATER_BUCKET))return InteractionResultHolder.pass(stack);
            stack.setDamageValue(stack.getDamageValue()-1);
            level.playSound(null,pos,net.minecraft.sounds.SoundEvents.BUCKET_FILL,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1.3f);
            return InteractionResultHolder.sidedSuccess(stack,false);
        }
        @Override public InteractionResult useOn(UseOnContext context){
            Level level=context.getLevel();Player player=context.getPlayer();ItemStack stack=context.getItemInHand();
            if(player==null||charges(stack)<=0)return InteractionResult.PASS;
            BlockPos target=context.getClickedPos();var clicked=level.getBlockState(target);
            BlockPos at=clicked.canBeReplaced()?target:target.relative(context.getClickedFace());
            var existing=level.getBlockState(at);
            if(!existing.canBeReplaced()||level.getFluidState(at).is(FluidTags.WATER)&&level.getFluidState(at).isSource())return InteractionResult.PASS;
            if(!level.mayInteract(player,at)||!player.mayUseItemAt(at,context.getClickedFace(),stack))return InteractionResult.PASS;
            if(level.dimensionType().ultraWarm()){
                level.playSound(player,at,net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.BLOCKS,.5f,2.6f);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if(level.isClientSide)return InteractionResult.SUCCESS;
            level.setBlock(at,Blocks.WATER.defaultBlockState(),11);
            level.gameEvent(player,net.minecraft.world.level.gameevent.GameEvent.FLUID_PLACE,at);
            level.playSound(null,at,net.minecraft.sounds.SoundEvents.BUCKET_EMPTY,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1.3f);
            if(!player.getAbilities().instabuild)stack.setDamageValue(stack.getDamageValue()+1);
            return InteractionResult.CONSUME;
        }
    }

    // ------------------------------------------------------------------ buoyancy float: one marker per player
    /** Right-click a block to set the marker; use it in the air while sneaking to clear. Stored with the player, so it survives restarts. */
    static final class FloatMarker extends AuxItem {
        FloatMarker(Properties properties){super("buoyancy_float",properties);}
        @Override public InteractionResult useOn(UseOnContext context){
            if(!(context.getPlayer() instanceof ServerPlayer p))return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
            BlockPos at=context.getClickedPos().relative(context.getClickedFace());
            if(!p.serverLevel().mayInteract(p,at)||p.position().distanceToSqr(Vec3.atCenterOf(at))>MARKER_PLACE_RANGE*MARKER_PLACE_RANGE)return InteractionResult.FAIL;
            CompoundTag tag=new CompoundTag();tag.putString("dim",p.serverLevel().dimension().location().toString());
            tag.putInt("x",at.getX());tag.putInt("y",at.getY());tag.putInt("z",at.getZ());
            p.getPersistentData().put(MARKER_KEY,tag);
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("ac.canon.float_marker.set",at.getX(),at.getY(),at.getZ()),true);
            return InteractionResult.CONSUME;
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            ItemStack stack=player.getItemInHand(hand);
            if(!player.isShiftKeyDown()||!(player instanceof ServerPlayer p)||!p.getPersistentData().contains(MARKER_KEY))return InteractionResultHolder.pass(stack);
            p.getPersistentData().remove(MARKER_KEY);
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("ac.canon.float_marker.cleared"),true);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static final double MARKER_PLACE_RANGE=6, MARKER_REACH=16;
    /** The player's marker centre when it is in this dimension and within reach, else null. Field skills fall back to the caster. */
    public static Vec3 floatMarker(ServerPlayer p){
        if(p==null||!p.getPersistentData().contains(MARKER_KEY)||!inInventory(p,BUOYANCY_FLOAT))return null;
        CompoundTag tag=p.getPersistentData().getCompound(MARKER_KEY);
        if(!tag.getString("dim").equals(p.serverLevel().dimension().location().toString()))return null;
        Vec3 at=new Vec3(tag.getInt("x")+.5,tag.getInt("y")+.5,tag.getInt("z")+.5);
        return at.distanceToSqr(p.position())<=MARKER_REACH*MARKER_REACH?at:null;
    }
    /** A paper skill's material sheet: vanilla paper or origami paper, one sheet each. */
    public static boolean isSheet(ItemStack stack){return stack.is(Items.PAPER)||stack.is(ORIGAMI_PAPER.get());}
}
