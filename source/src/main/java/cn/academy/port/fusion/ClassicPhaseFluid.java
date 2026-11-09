/* AcademyCraft1.0.7 BlockImagPhase 3-quanta fluid adaptation. GPLv3. See NOTICE. */
package cn.academy.port.fusion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/** Genuine source/flowing states, finite 3-quanta lateral reach, no source regeneration or bucket. */
public abstract class ClassicPhaseFluid extends BaseFlowingFluid {
    protected ClassicPhaseFluid(){super(new Properties(ClassicFusion.PHASE_TYPE,ClassicFusion.PHASE_SOURCE,ClassicFusion.PHASE_FLOWING).block(ClassicFusion.PHASE_BLOCK).slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(30));}
    @Override public void tick(net.minecraft.world.level.Level level,BlockPos pos,FluidState state){cn.academy.port.fusion.flow.ClassicPhaseFlow.tick(level,pos);}
    @Override public FluidState getFlowing(int amount,boolean falling){return super.getFlowing(Math.min(2,Math.max(1,amount)),falling);}
    @Override public float getOwnHeight(FluidState state){return state.getAmount()/3f*.875f;}
    @Override protected BlockState createLegacyBlock(FluidState state){return ClassicFusion.PHASE_BLOCK.get().defaultBlockState().setValue(LiquidBlock.LEVEL,state.isSource()?0:3-state.getAmount());}
    private static int quanta(net.minecraft.world.level.BlockGetter level,BlockPos pos){var state=level.getBlockState(pos);return state.isAir()?0:state.is(ClassicFusion.PHASE_BLOCK.get())?3-Math.min(2,state.getValue(LiquidBlock.LEVEL)):-1;}
    @Override public net.minecraft.world.phys.Vec3 getFlow(net.minecraft.world.level.BlockGetter level,BlockPos pos,FluidState state){
        var vector=net.minecraft.world.phys.Vec3.ZERO;int decay=3-quanta(level,pos);
        for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){var neighbor=pos.relative(direction);int otherDecay=3-quanta(level,neighbor);if(otherDecay>=3){if(!level.getBlockState(neighbor).blocksMotion()){otherDecay=3-quanta(level,neighbor.below());if(otherDecay>=0){int power=otherDecay-(decay-3);vector=vector.add(direction.getStepX()*power,0,direction.getStepZ()*power);}}}else if(otherDecay>=0){int power=otherDecay-decay;vector=vector.add(direction.getStepX()*power,0,direction.getStepZ()*power);}}
        if(level.getBlockState(pos.above()).is(ClassicFusion.PHASE_BLOCK.get())){for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){var neighbor=pos.relative(direction);if(level.getBlockState(neighbor).isFaceSturdy(level,neighbor,direction.getOpposite())||level.getBlockState(neighbor.above()).isFaceSturdy(level,neighbor.above(),direction.getOpposite())){vector=vector.normalize().add(0,-6,0);break;}}}
        return vector.normalize();
    }
    public static final class Source extends ClassicPhaseFluid {
        @Override public int getAmount(FluidState state){return 3;}
        @Override public boolean isSource(FluidState state){return true;}
    }
    public static final class Flowing extends ClassicPhaseFluid {
        public Flowing(){registerDefaultState(getStateDefinition().any().setValue(LEVEL,2));}
        @Override protected void createFluidStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.material.Fluid,FluidState> builder){super.createFluidStateDefinition(builder);builder.add(LEVEL);}
        @Override public int getAmount(FluidState state){return Math.min(2,state.getValue(LEVEL));}
        @Override public boolean isSource(FluidState state){return false;}
    }
}
