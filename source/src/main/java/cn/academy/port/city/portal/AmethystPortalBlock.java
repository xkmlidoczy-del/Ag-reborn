package cn.academy.port.city.portal;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.shapes.*;

/** Water remains water; the purple surface uses native portal tracking and native dimension-change hooks. */
public final class AmethystPortalBlock extends Block implements Portal {
    public static final IntegerProperty QUADRANT=IntegerProperty.create("quadrant",0,3);
    private static final MapCodec<AmethystPortalBlock> CODEC=simpleCodec(AmethystPortalBlock::new);
    public AmethystPortalBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(QUADRANT,0));}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(QUADRANT);}
    public static BlockPos anchor(BlockState state,BlockPos at){int q=state.getValue(QUADRANT);return at.offset(-(q%2),0,-(q/2));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return box(0,0,0,16,14.4,16);}
    @Override protected FluidState getFluidState(BlockState state){return Fluids.WATER.getSource(false);}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){if(!level.isClientSide)level.scheduleTick(pos,this,20);}
    @Override protected BlockState updateShape(BlockState s,Direction direction,BlockState other,LevelAccessor level,BlockPos p,BlockPos q){level.scheduleTick(p,this,2);return s;}
    @Override protected void tick(BlockState s,ServerLevel world,BlockPos p,RandomSource random){
        if(!AmethystPortalRitual.loaded(world,anchor(s,p))){world.scheduleTick(p,this,20);return;}
        if(!AmethystPortalRitual.active(world,anchor(s,p)))AmethystPortalRitual.close(world,anchor(s,p));else world.scheduleTick(p,this,20);
    }
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){
        if(entity instanceof ServerPlayer player&&player.isAlive()&&!player.isSpectator()&&player.canUsePortal(false))player.setAsInsidePortal(this,pos);
    }
    @Override public int getPortalTransitionTime(ServerLevel world,Entity entity){return 20;}
    @Override public Transition getLocalTransition(){return Transition.NONE;}
    @Override public DimensionTransition getPortalDestination(ServerLevel world,Entity entity,BlockPos pos){
        var s=world.getBlockState(pos);if(!(entity instanceof ServerPlayer player)||!s.is(this)||!AmethystPortalRitual.active(world,anchor(s,pos)))return null;
        return CityPortalTravel.destination(world,player,anchor(s,pos));
    }
    @Override public void animateTick(BlockState s,Level world,BlockPos p,RandomSource random){
        for(int i=0;i<2;i++)world.addParticle(AmethystPortalRitual.SPARK,p.getX()+random.nextDouble(),p.getY()+.9+random.nextDouble()*.15,p.getZ()+random.nextDouble(),0,.018,0);
        if(random.nextInt(100)==0)world.playLocalSound(p.getX()+.5,p.getY()+.8,p.getZ()+.5,net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,net.minecraft.sounds.SoundSource.BLOCKS,.25f,1.15f,false);
    }
    @Override protected boolean canBeReplaced(BlockState state,Fluid fluid){return false;}
}
