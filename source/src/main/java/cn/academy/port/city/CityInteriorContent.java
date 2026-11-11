package cn.academy.port.city;

import com.mojang.serialization.MapCodec;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;

/** Static baked furniture, non-ticking storage, occupied-only seats and a small active elevator controller. */
public final class CityInteriorContent {
    public static final BooleanProperty LIT=BlockStateProperties.LIT,OPEN=BlockStateProperties.OPEN,RIGHT=BooleanProperty.create("right");
    public static final IntegerProperty SLIDE=IntegerProperty.create("slide",0,12);
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("academy");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"academy");
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,"academy");
    public static final Map<String,DeferredBlock<? extends Block>> FURNITURE=new LinkedHashMap<>();
    public static final List<String> IDS=List.of("study_desk","office_desk","chair","bench","sofa","counter","lab_bench","equipment_rack","wall_monitor","notice_board","sink","toilet","cabinet","locker","bedside","interior_lamp","wall_light","elevator_shell","elevator_panel","elevator_gate","elevator_controller");
    static{
        for(String id:IDS){
            DeferredBlock<? extends Block> block;
            if(Set.of("cabinet","locker","bedside").contains(id))block=BLOCKS.register("city_"+id,()->new Storage(id,BlockBehaviour.Properties.of().strength(2).sound(SoundType.WOOD).noOcclusion()));
            else if(id.equals("elevator_controller"))block=BLOCKS.register("city_"+id,()->new Controller(BlockBehaviour.Properties.of().strength(5).sound(SoundType.METAL)));
            else if(id.equals("elevator_gate"))block=BLOCKS.register("city_"+id,()->new Gate(BlockBehaviour.Properties.of().strength(4).sound(SoundType.METAL).noOcclusion()));
            else block=BLOCKS.register("city_"+id,()->new Furnishing(id,BlockBehaviour.Properties.of().strength(2).sound(SoundType.METAL).noOcclusion().lightLevel(s->(id.equals("interior_lamp")||id.equals("wall_light"))&&s.getValue(LIT)?15:0)));
            FURNITURE.put(id,block);ITEMS.registerSimpleBlockItem("city_"+id,block);
        }
    }
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<StorageEntity>> STORAGE=TILES.register("city_furniture_storage",()->BlockEntityType.Builder.of(StorageEntity::new,block("cabinet"),block("locker"),block("bedside")).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CityElevatorController>> ELEVATOR=TILES.register("city_elevator_controller",()->BlockEntityType.Builder.of(CityElevatorController::new,block("elevator_controller")).build(null));
    public static final DeferredHolder<EntityType<?>,EntityType<CitySeatEntity>> SEAT=ENTITIES.register("city_interior_seat",()->EntityType.Builder.<CitySeatEntity>of(CitySeatEntity::new,MobCategory.MISC).sized(.2F,.2F).clientTrackingRange(6).build("academy:city_interior_seat"));
    public static final DeferredHolder<EntityType<?>,EntityType<CityElevatorEntity>> CABIN=ENTITIES.register("city_elevator",()->EntityType.Builder.<CityElevatorEntity>of(CityElevatorEntity::new,MobCategory.MISC).sized(1.9F,2.8F).clientTrackingRange(12).updateInterval(1).build("academy:city_elevator"));
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TILES.register(bus);ENTITIES.register(bus);bus.addListener(CityInteriorContent::creative);bus.addListener(CityElevatorNetwork::register);}
    private static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS)FURNITURE.values().forEach(b->e.accept(b.get()));}
    public static Block block(String id){return FURNITURE.get(id).get();}
    public static BlockState state(String id){return block(id).defaultBlockState();}
    public static VoxelShape shape(String id){return switch(id){
        case "chair" -> Shapes.or(Block.box(3,0,3,13,9,13),Block.box(3,9,11,13,20,14));
        case "bench","sofa" -> Shapes.or(Block.box(0,0,2,16,9,14),Block.box(0,9,12,16,18,16));
        case "office_desk" -> Shapes.or(Block.box(0,12,0,16,15,16),Block.box(1,0,1,3,12,15),Block.box(13,0,1,15,12,15),Block.box(3,18,10.5,13,24,12));
        case "study_desk","counter","lab_bench" -> Shapes.or(Block.box(0,12,0,16,15,16),Block.box(1,0,1,3,12,15),Block.box(13,0,1,15,12,15));
        case "toilet" -> Shapes.or(Block.box(4,0,5,12,4,13),Block.box(1,3,3,15,8,14),Block.box(2,8,12,14,18,16));
        case "wall_monitor","notice_board","elevator_panel" -> Block.box(1,2,13,15,15,16);
        case "interior_lamp","wall_light","elevator_shell" -> Shapes.block();
        case "elevator_gate" -> Block.box(0,0,0,16,16,2);
        default -> Block.box(0,0,0,16,id.equals("bedside")?12:16,16);
    };}
    private static VoxelShape rotate(VoxelShape shape,Direction d){
        VoxelShape[] out={Shapes.empty()};int turns=d==Direction.NORTH?0:d==Direction.EAST?1:d==Direction.SOUTH?2:3;
        shape.forAllBoxes((a,b,c,x,y,z)->{double l=a,n=c,r=x,s=z;for(int i=0;i<turns;i++){double v=l;l=1-s;s=r;r=1-n;n=v;}out[0]=Shapes.or(out[0],Shapes.box(l,b,n,r,y,s));});return out[0];
    }
    public static class Furnishing extends HorizontalDirectionalBlock {
        final String id;
        Furnishing(String id,Properties p){super(p);this.id=id;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(LIT,true).setValue(OPEN,false));}
        @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return simpleCodec(p->new Furnishing(id,p));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,LIT,OPEN);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
        @Override protected VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return CityInteriorContent.rotate(shape(id),s.getValue(FACING));}
        @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return id.equals("elevator_gate")&&s.getValue(OPEN)?Shapes.empty():getShape(s,w,p,c);}
        @Override protected InteractionResult useWithoutItem(BlockState s,Level w,BlockPos pos,Player p,BlockHitResult hit){
            if(w.isClientSide)return InteractionResult.SUCCESS;
            if(id.equals("interior_lamp")||id.equals("wall_light")){boolean lit=!s.getValue(LIT);w.setBlock(pos,s.setValue(LIT,lit),3);if(id.equals("wall_light")&&w instanceof net.minecraft.server.level.ServerLevel server)CityInteriors.roomLighting(server,pos,lit);return InteractionResult.CONSUME;}
            if(id.equals("chair")||id.equals("bench")||id.equals("sofa")){CitySeatEntity.sit(p,pos);return InteractionResult.CONSUME;}
            if(id.equals("elevator_panel")||id.equals("elevator_gate")){CityElevators.open(p,pos);return InteractionResult.CONSUME;}
            return InteractionResult.PASS;
        }
    }
    public static final class Gate extends HorizontalDirectionalBlock {
        public Gate(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(SLIDE,0).setValue(RIGHT,false));}
        @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return simpleCodec(Gate::new);}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,SLIDE,RIGHT);}
        @Override protected VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){double dx=s.getValue(SLIDE)/12.0*(s.getValue(RIGHT)?1:-1);return CityInteriorContent.rotate(Shapes.box(dx,0,.90625,1+dx,1,1),s.getValue(FACING));}
        @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return s.getValue(SLIDE)==12?Shapes.empty():CityInteriorContent.rotate(Block.box(0,0,14.5,16,16,16),s.getValue(FACING));}
        @Override protected InteractionResult useWithoutItem(BlockState s,Level w,BlockPos p,Player player,BlockHitResult hit){if(!w.isClientSide)CityElevators.open(player,p);return InteractionResult.sidedSuccess(w.isClientSide);}
    }
    public static final class Storage extends BaseEntityBlock {
        final String id;
        public Storage(String id,Properties p){super(p);this.id=id;registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING,Direction.NORTH));}
        @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new Storage(id,p));}
        @Override protected VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return CityInteriorContent.rotate(shape(id),s.getValue(HorizontalDirectionalBlock.FACING));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(HorizontalDirectionalBlock.FACING);}
        @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new StorageEntity(p,s);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,c.getHorizontalDirection().getOpposite());}
        @Override protected InteractionResult useWithoutItem(BlockState s,Level w,BlockPos pos,Player p,BlockHitResult hit){if(!w.isClientSide&&w.getBlockEntity(pos)instanceof StorageEntity box)p.openMenu(box);return InteractionResult.sidedSuccess(w.isClientSide);}
        @Override protected void onRemove(BlockState s,Level w,BlockPos p,BlockState next,boolean moving){if(!s.is(next.getBlock())&&w.getBlockEntity(p)instanceof StorageEntity box)Containers.dropContents(w,p,box);super.onRemove(s,w,p,next,moving);}
    }
    public static final class StorageEntity extends RandomizableContainerBlockEntity {
        private NonNullList<ItemStack> items=NonNullList.withSize(27,ItemStack.EMPTY);
        public StorageEntity(BlockPos p,BlockState s){super(STORAGE.get(),p,s);}
        @Override protected Component getDefaultName(){return Component.translatable("container.academy.city_storage");}
        @Override public int getContainerSize(){return 27;}
        @Override protected NonNullList<ItemStack> getItems(){return items;}
        @Override protected void setItems(NonNullList<ItemStack> values){items=values;}
        @Override protected AbstractContainerMenu createMenu(int id,net.minecraft.world.entity.player.Inventory inv){return ChestMenu.threeRows(id,inv,this);}
        @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider lookup){super.loadAdditional(t,lookup);items=NonNullList.withSize(27,ItemStack.EMPTY);if(!tryLoadLootTable(t))ContainerHelper.loadAllItems(t,items,lookup);}
        @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider lookup){super.saveAdditional(t,lookup);if(!trySaveLootTable(t))ContainerHelper.saveAllItems(t,items,lookup);}
    }
    public static final class Controller extends BaseEntityBlock {
        public Controller(Properties p){super(p);}
        @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(Controller::new);}
        @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new CityElevatorController(p,s);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level w,BlockState s,BlockEntityType<T> type){return w.isClientSide?null:createTickerHelper(type,ELEVATOR.get(),CityElevatorController::tick);}
        @Override protected InteractionResult useWithoutItem(BlockState s,Level w,BlockPos pos,Player p,BlockHitResult hit){if(!w.isClientSide)CityElevators.open(p,pos);return InteractionResult.sidedSuccess(w.isClientSide);}
    }
}
