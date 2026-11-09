package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.integer;
import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerCombat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Real collision blocks with bounded server leases; stale saved blocks remove themselves on load. */
@EventBusSubscriber(modid="academy")
public final class CanonPaperContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("academy");
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"academy");
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,"academy");
    public static final DeferredHolder<EntityType<?>,EntityType<PaperPlane>> PLANE=ENTITIES.register("paper_plane",
            ()->EntityType.Builder.<PaperPlane>of(PaperPlane::new,MobCategory.MISC).sized(.25f,.25f).noSave().clientTrackingRange(8).updateInterval(1).build("academy:paper_plane"));
    public static final DeferredBlock<PaperBlock> PAPER=BLOCKS.register("paper_construct",()->new PaperBlock(BlockBehaviour.Properties.of().strength(.4f).noLootTable()));
    public static final DeferredBlock<PaperPlate> PLATE=BLOCKS.register("paper_plate",()->new PaperPlate(BlockBehaviour.Properties.of().strength(.4f).noLootTable().noCollission()));
    public static final DeferredBlock<PaperButton> BUTTON=BLOCKS.register("paper_button",()->new PaperButton(BlockBehaviour.Properties.of().strength(.4f).noLootTable().noCollission()));
    public static final DeferredBlock<PaperLadder> LADDER=BLOCKS.register("paper_ladder",()->new PaperLadder(BlockBehaviour.Properties.of().strength(.4f).noLootTable().noCollission()));
    public static final DeferredBlock<PaperGate> GATE=BLOCKS.register("paper_gate",()->new PaperGate(BlockBehaviour.Properties.of().strength(.4f).noLootTable().noOcclusion()));
    private static final DeferredHolder<BlockEntityType<?>,BlockEntityType<PaperEntity>> TILE=TILES.register("paper_construct",
            ()->BlockEntityType.Builder.of(PaperEntity::new,PAPER.get(),PLATE.get(),BUTTON.get(),LADDER.get(),GATE.get()).build(null));
    private static final int LIMIT=128;
    private static final double PLANE_REACH=number("paper_plane", "reach");
    private static final int PLANE_TICKS=integer("paper_plane", "duration_ticks");
    private static final Map<UUID,Lease> LEASES=new HashMap<>();
    private record Lease(UUID token,UUID owner,ServerLevel world,BlockPos pos,long expires) {}
    private CanonPaperContent() {}
    public static void register(IEventBus bus){BLOCKS.register(bus);TILES.register(bus);ENTITIES.register(bus);}

    /** Strong evidence for a puzzle: same live block, owner, dimension, expiry and in-memory lease. */
    public static boolean owned(ServerPlayer player,BlockPos pos) {
        if(player==null||pos==null||!player.server.isSameThread()||player.connection==null||player.hasDisconnected()
                ||player.server.getPlayerList().getPlayer(player.getUUID())!=player||!player.isAlive()||player.isRemoved()
                ||player.isSpectator())return false;
        ServerLevel level=player.serverLevel();if(!level.hasChunkAt(pos)||!isPaper(level.getBlockState(pos)))return false;
        if(!(level.getBlockEntity(pos) instanceof PaperEntity tile)||tile.token==null)return false;
        Lease lease=LEASES.get(tile.token);
        return valid(lease,tile) && player.getUUID().equals(lease.owner);
    }
    /** Validate the entire batch and its protection hooks before revoking any lease.
     * All ownership tokens are revoked together, then their matching blocks are removed.
     * A temporarily unloaded or externally replaced block is never overwritten; any stale
     * paper entity self-cleans on its next loaded server tick. No paper is refunded. */
    public static boolean consumeOwned(ServerPlayer player,List<BlockPos> positions) {
        if(player==null||positions==null||positions.isEmpty()||positions.size()>16||!player.server.isSameThread())return false;
        var unique=new HashSet<BlockPos>();var level=player.serverLevel();var chosen=new ArrayList<Lease>();
        for(BlockPos input:positions) {
            if(input==null||!unique.add(input.immutable())||!owned(player,input)||!level.mayInteract(player,input))return false;
        }
        for(BlockPos pos:unique) {
            if(NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos,level.getBlockState(pos),player)).isCanceled())return false;
            // Any protection callback may re-enter and change any earlier member.
            for(BlockPos check:unique)if(!owned(player,check)||!level.mayInteract(player,check))return false;
        }
        for(BlockPos pos:unique) {
            if(!(level.getBlockEntity(pos) instanceof PaperEntity tile))return false;
            Lease lease=LEASES.get(tile.token);if(!valid(lease,tile))return false;chosen.add(lease);
        }
        // Server-thread map operations have no callback or yield: no partial owned state is visible.
        for(Lease lease:chosen)LEASES.remove(lease.token,lease);
        for(Lease lease:chosen) {
            if(level.hasChunkAt(lease.pos)&&level.getBlockEntity(lease.pos) instanceof PaperEntity tile
                    &&lease.token.equals(tile.token)&&isPaper(level.getBlockState(lease.pos)))removePhysical(level,lease.pos);
        }
        return true;
    }

    private static boolean valid(Lease lease,PaperEntity tile) {
        if(lease==null||tile==null||tile.isRemoved()||tile.getLevel()!=lease.world||!lease.pos.equals(tile.getBlockPos())
                ||!lease.token.equals(tile.token)||!lease.owner.equals(tile.owner)||lease.expires!=tile.expires
                ||lease.world.getGameTime()>=lease.expires||!lease.world.hasChunkAt(lease.pos)
                ||!isPaper(lease.world.getBlockState(lease.pos))||lease.world.getBlockEntity(lease.pos)!=tile)return false;
        ServerPlayer p=lease.world.getServer().getPlayerList().getPlayer(lease.owner);
        if(p==null||!p.isAlive()||p.isRemoved()||p.hasDisconnected()||p.isSpectator()||p.serverLevel()!=lease.world
                ||HighPowerCombat.unsupportedReason(p).isPresent())return false;
        var state=AbilityStorage.get(p);
        return "paper_psychokinesis".equals(state.category)&&state.activated&&state.overloadFine&&!state.interfering
                &&state.learned("paper_patch")&&SkillCatalog.enabled(state.category,"paper_patch");
    }
    private static boolean isPaper(BlockState state){return state.is(PAPER.get())||state.is(PLATE.get())||state.is(BUTTON.get())||state.is(LADDER.get())||state.is(GATE.get());}
    /** Only the paper controller may call after paying CP; does not consume or refund materials. */
    static UUID place(ServerPlayer player,BlockPos pos,Direction face,String skill) {return place(player,pos,face,0,skill);}
    static UUID placePlate(ServerPlayer player,BlockPos pos,Direction face,String skill) {return place(player,pos,face,1,skill);}
    static UUID placeButton(ServerPlayer player,BlockPos pos,Direction face,String skill) {return place(player,pos,face,2,skill);}
    static UUID placeLadder(ServerPlayer player,BlockPos pos,Direction face,String skill) {return place(player,pos,face,3,skill);}
    static UUID placeGate(ServerPlayer player,BlockPos pos,Direction face,String skill) {return place(player,pos,face,4,skill);}
    private static UUID place(ServerPlayer player,BlockPos pos,Direction face,int kind,String skill) {
        double reach=number(skill, "reach");
        int lifetime=integer(skill, "duration_ticks");
        if(player==null||pos==null||face==null||!player.server.isSameThread()||!player.isAlive()||player.isRemoved()
                ||player.connection==null||player.hasDisconnected()||player.server.getPlayerList().getPlayer(player.getUUID())!=player
                ||LEASES.size()>=LIMIT)return null;
        ServerLevel level=player.serverLevel();long now=level.getGameTime();
        if(now<0||now>Long.MAX_VALUE-lifetime||!level.hasChunkAt(pos)||!level.getBlockState(pos).isAir()
                ||level.getBlockEntity(pos)!=null||!level.mayInteract(player,pos)||player.distanceToSqr(pos.getCenter())>reach*reach
                ||!level.noCollision(null,new AABB(pos)))return null;
        BlockState next=switch(kind){case 1->PLATE.get().defaultBlockState();case 2->BUTTON.get().defaultBlockState();
            case 3->LADDER.get().defaultBlockState();case 4->GATE.get().defaultBlockState();default->PAPER.get().defaultBlockState();};
        if(kind==3){if(!face.getAxis().isHorizontal())return null;next=next.setValue(LadderBlock.FACING,face);}
        if(kind==4){Direction facing=face.getAxis().isHorizontal()?face:player.getDirection();next=next.setValue(TrapDoorBlock.FACING,facing);}
        if(kind==2){
            AttachFace attachment=face==Direction.UP?AttachFace.FLOOR:face==Direction.DOWN?AttachFace.CEILING:AttachFace.WALL;
            Direction facing=face.getAxis().isHorizontal()?face:player.getDirection();
            next=next.setValue(ButtonBlock.FACE,attachment).setValue(ButtonBlock.FACING,facing);
        }
        if(kind!=0&&!next.canSurvive(level,pos))return null;
        BlockSnapshot previous=BlockSnapshot.create(level.dimension(),level,pos);
        UUID token=UUID.randomUUID();Lease lease=new Lease(token,player.getUUID(),level,pos.immutable(),now+lifetime);
        if(!level.setBlock(pos,next,3))return null;
        if(!(level.getBlockEntity(pos) instanceof PaperEntity tile)||!isPaper(level.getBlockState(pos))) {
            if(isPaper(level.getBlockState(pos)) && level.getBlockEntity(pos)==null)level.removeBlock(pos,false);
            return null;
        }
        tile.owner=lease.owner;tile.token=token;tile.expires=lease.expires;tile.setChanged();LEASES.put(token,lease);
        try {
            boolean denied=EventHooks.onBlockPlace(player,previous,face);
            if(denied||!valid(lease,tile)||!level.mayInteract(player,pos)) {discard(lease);return null;}
            return token;
        } catch(RuntimeException | Error failure) {discard(lease);throw failure;}
    }
    static void discard(UUID token) {Lease lease=LEASES.get(token);if(lease!=null)discard(lease);}
    private static void discard(Lease lease) {
        if(!LEASES.remove(lease.token,lease))return;
        var world=lease.world;if(!world.hasChunkAt(lease.pos))return;
        if(world.getBlockEntity(lease.pos) instanceof PaperEntity tile && lease.token.equals(tile.token)
                && isPaper(world.getBlockState(lease.pos)))removePhysical(world,lease.pos);
    }
    private static void removePhysical(ServerLevel world,BlockPos pos) {
        var state=world.getBlockState(pos);
        if(state.hasProperty(BlockStateProperties.WATERLOGGED)&&state.getValue(BlockStateProperties.WATERLOGGED))
            world.setBlock(pos,net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(),3);
        else world.removeBlock(pos,false);
    }
    private static void removePlayer(ServerPlayer p){for(Lease lease:List.copyOf(LEASES.values()))if(lease.owner.equals(p.getUUID()))discard(lease);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)removePlayer(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)removePlayer(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p)removePlayer(p);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){LEASES.values().removeIf(lease->lease.world.getServer()==e.getServer());}

    public static final class PaperBlock extends Block implements EntityBlock {
        PaperBlock(BlockBehaviour.Properties properties){super(properties);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PaperEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
            if(level.isClientSide||type!=TILE.get())return null;
            return (world,pos,block,tile)->{if(tile instanceof PaperEntity paper)paper.tick();};
        }
    }
    /** A real redstone pressure plate built from consumed paper, never a decorative particle. */
    public static final class PaperPlate extends PressurePlateBlock implements EntityBlock {
        PaperPlate(BlockBehaviour.Properties properties){super(BlockSetType.STONE,properties);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PaperEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
            if(level.isClientSide||type!=TILE.get())return null;
            return (world,pos,block,tile)->{if(tile instanceof PaperEntity paper)paper.tick();};
        }
    }
    /** A real player-operated redstone button supported by a leased paper construct. */
    public static final class PaperButton extends ButtonBlock implements EntityBlock {
        PaperButton(BlockBehaviour.Properties properties){super(BlockSetType.STONE,20,properties);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PaperEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
            if(level.isClientSide||type!=TILE.get())return null;
            return (world,pos,block,tile)->{if(tile instanceof PaperEntity paper)paper.tick();};
        }
    }
    public static final class PaperLadder extends LadderBlock implements EntityBlock {
        PaperLadder(BlockBehaviour.Properties properties){super(properties);}
        @Override public boolean canPlaceLiquid(Player p,BlockGetter w,BlockPos pos,BlockState state,Fluid fluid){return false;}
        @Override public boolean placeLiquid(LevelAccessor w,BlockPos pos,BlockState state,FluidState fluid){return false;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PaperEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
            if(level.isClientSide||type!=TILE.get())return null;return (world,pos,block,tile)->{if(tile instanceof PaperEntity paper)paper.tick();};
        }
    }
    public static final class PaperGate extends TrapDoorBlock implements EntityBlock {
        PaperGate(BlockBehaviour.Properties properties){super(BlockSetType.OAK,properties);}
        @Override public boolean canPlaceLiquid(Player p,BlockGetter w,BlockPos pos,BlockState state,Fluid fluid){return false;}
        @Override public boolean placeLiquid(LevelAccessor w,BlockPos pos,BlockState state,FluidState fluid){return false;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PaperEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
            if(level.isClientSide||type!=TILE.get())return null;return (world,pos,block,tile)->{if(tile instanceof PaperEntity paper)paper.tick();};
        }
    }
    /** A finite, non-damaging physical paper projectile; no saved entity or item drops. */
    public static final class PaperPlane extends ThrowableItemProjectile {
        public PaperPlane(EntityType<? extends ThrowableItemProjectile> type,Level level){super(type,level);}
        @Override protected Item getDefaultItem(){return Items.PAPER;}
        @Override public boolean shouldBeSaved(){return false;}
        @Override public void tick(){
            if(level() instanceof ServerLevel world){
                if(!(getOwner() instanceof ServerPlayer p)||p.serverLevel()!=world||p.isRemoved()||!p.isAlive()
                        ||p.hasDisconnected()||p.server.getPlayerList().getPlayer(p.getUUID())!=p||tickCount>=PLANE_TICKS
                        ||!world.hasChunkAt(blockPosition())||!world.mayInteract(p,blockPosition())
                        ||distanceToSqr(p)>PLANE_REACH*PLANE_REACH){discard();return;}
                var ability=AbilityStorage.get(p);
                if(!"paper_psychokinesis".equals(ability.category)||!ability.activated||!ability.overloadFine
                        ||ability.interfering||!ability.learned("paper_plane")||!SkillCatalog.enabled(ability.category,"paper_plane")){
                    discard();return;
                }
                var next=position().add(getDeltaMovement());
                if(!Double.isFinite(next.x)||!Double.isFinite(next.y)||!Double.isFinite(next.z)
                        ||!world.hasChunkAt(BlockPos.containing(next))||!world.mayInteract(p,BlockPos.containing(next))){discard();return;}
            }
            super.tick();
        }
        @Override protected void onHit(HitResult hit){if(!level().isClientSide)discard();}
    }
    public static final class PaperEntity extends BlockEntity {
        private UUID owner,token;private long expires;
        public PaperEntity(BlockPos pos,BlockState state){super(TILE.get(),pos,state);}
        @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
            super.saveAdditional(tag,registries);if(owner!=null)tag.putUUID("owner",owner);if(token!=null)tag.putUUID("token",token);tag.putLong("expires",expires);
        }
        @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
            super.loadAdditional(tag,registries);owner=tag.hasUUID("owner")?tag.getUUID("owner"):null;
            token=tag.hasUUID("token")?tag.getUUID("token"):null;expires=tag.getLong("expires");
        }
        @Override public void setRemoved() {
            super.setRemoved();
            if(token!=null){Lease lease=LEASES.get(token);if(lease!=null&&lease.world==level&&lease.pos.equals(worldPosition))LEASES.remove(token,lease);}
        }
        private void tick() {
            if(!(level instanceof ServerLevel world))return;Lease lease=token==null?null:LEASES.get(token);
            if(!valid(lease,this)){
                if(lease!=null)LEASES.remove(token,lease);
                if(world.getBlockEntity(worldPosition)==this && isPaper(world.getBlockState(worldPosition)))
                    removePhysical(world,worldPosition);
            }
        }
    }
}
