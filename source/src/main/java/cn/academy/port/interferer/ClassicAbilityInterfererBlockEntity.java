/* AcademyCraft1.0.7 TileAbilityInterferer/AbilityInterf. GPLv3; see NOTICE. */
package cn.academy.port.interferer;
import cn.academy.port.AbilityStorage;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
/** Whitelist is deliberately stored/UI-visible only: the original effect never reads it. */
public final class ClassicAbilityInterfererBlockEntity extends BlockEntity {
    private boolean enabled;
    private double range=ClassicInterfererRules.MIN_RANGE;
    private String placer; // Original neither persists nor broadcasts the placer.
    private final SortedSet<String> whitelist=new TreeSet<>();
    private final ClassicInterfererRules.Clock clock=new ClassicInterfererRules.Clock();
    public ClassicAbilityInterfererBlockEntity(BlockPos pos,BlockState state){super(ClassicAbilityInterferers.TILE.get(),pos,state);}
    public boolean enabled(){return enabled;}public double range(){return range;}public String placer(){return placer;}
    public SortedSet<String> whitelist(){return Collections.unmodifiableSortedSet(new TreeSet<>(whitelist));}
    public boolean available(){return level!=null&&!isRemoved()&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this;}
    public void setPlacer(Player player){if(placer==null){placer=player.getGameProfile().getName();whitelist.add(placer);setChanged();}}
    public boolean mayConfigure(Player player){return player.getAbilities().instabuild||Objects.equals(player.getGameProfile().getName(),placer);}
    public String sourceName(){return "interferer@"+level.dimension().location()+"("+worldPosition.getX()+","+worldPosition.getY()+","+worldPosition.getZ()+")";}
    public ClassicInterfererRules.Bounds bounds(){return ClassicInterfererRules.bounds(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),range);}
    public void setRange(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("finite range required");range=ClassicInterfererRules.clampRange(value);setChanged();}
    public void setEnabled(boolean value){enabled=value;setChanged();}
    public void setWhitelist(Collection<String> names){if(names==null||names.size()>ClassicInterfererRules.MAX_NAMES||names.stream().anyMatch(name->!ClassicInterfererRules.validName(name)))throw new IllegalArgumentException("bounded plain whitelist labels required");whitelist.clear();whitelist.addAll(names);setChanged();}
    public int scanRemaining(){return clock.scanRemaining();}public int syncRemaining(){return clock.syncRemaining();}
    public static void serverTick(Level world,BlockPos pos,BlockState state,ClassicAbilityInterfererBlockEntity tile){
        if(!(world instanceof ServerLevel level)||!tile.available())return;
        var due=tile.clock.tick(tile.enabled);
        if(due.scan()){
            var box=tile.bounds();var aabb=new AABB(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ());
            for(var player:level.getEntitiesOfClass(ServerPlayer.class,aabb,p->!p.getAbilities().instabuild)){
                var progress=AbilityStorage.get(player);String source=tile.sourceName();
                progress.addInterference(source,()->box.inside(player.getX(),player.getY(),player.getZ())&&tile.available()&&player.serverLevel()==level&&!player.isRemoved()&&!player.getAbilities().instabuild&&tile.enabled);
            }
        }
        if(due.sync()){
            if(tile.getBlockState().getValue(ClassicAbilityInterfererBlock.ENABLED)!=tile.enabled)level.setBlock(pos,tile.getBlockState().setValue(ClassicAbilityInterfererBlock.ENABLED,tile.enabled),Block.UPDATE_KNOWN_SHAPE);
            ClassicInterfererNetwork.periodic(level,tile);
        }
    }
    private CompoundTag snapshot(){var tag=new CompoundTag();tag.putBoolean("enabled_",enabled);tag.putFloat("range_",(float)range);var names=new CompoundTag();names.putInt("size",whitelist.size());int index=0;for(String name:whitelist)names.putString(Integer.toString(index++),name);tag.put("whitelist_",names);return tag;}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider access){super.saveAdditional(tag,access);tag.merge(snapshot());}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider access){super.loadAdditional(tag,access);enabled=tag.getBoolean("enabled_");range=ClassicInterfererRules.loadRange(tag.getFloat("range_"));whitelist.clear();var names=tag.getCompound("whitelist_");for(int i=0;i<Math.max(0,Math.min(names.getInt("size"),ClassicInterfererRules.MAX_NAMES));i++){String name=names.getString(Integer.toString(i));if(ClassicInterfererRules.validName(name))whitelist.add(name);}placer=null;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider access){var tag=snapshot();tag.putDouble("range_",range);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider access){loadAdditional(tag,access);range=ClassicInterfererRules.loadRange(tag.getDouble("range_"));}
    public void acceptPresentation(double value,boolean active,List<String> names){if(level==null||!level.isClientSide||!Double.isFinite(value))return;range=ClassicInterfererRules.clampRange(value);enabled=active;whitelist.clear();for(String name:names)if(ClassicInterfererRules.validName(name))whitelist.add(name);}
}
