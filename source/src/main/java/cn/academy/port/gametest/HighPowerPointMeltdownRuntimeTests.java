package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillBlockDestroyEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.PointMeltdownRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Connected native players; focus advances through real server posts, never a synthetic tick loop. */
@GameTestHolder("academy_point_meltdown_runtime")
@EventBusSubscriber(modid="academy")
@PrefixGameTestTemplate(false)
public final class HighPowerPointMeltdownRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_point_meltdown";
    private static final Map<GameTestHelper,Steps> STEPS=new IdentityHashMap<>();
    private HighPowerPointMeltdownRuntimeTests() {}

    @SubscribeEvent(priority=EventPriority.LOWEST) public static void installFocusedTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var original=level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower",TEMPLATE));
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_point_meltdown_runtime",TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK),original.save(new CompoundTag()));
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void point_ten_real_ticks_remove_one_native_stone_without_replay_or_tunnel(GameTestHelper h) {
        Fixture f = new Fixture(h); f.target(Blocks.STONE,6); f.pickaxe();
        BlockPos behind = f.block(f.target.pos().south(2),Blocks.STONE);
        f.capture(); h.assertTrue(f.start(1),"authenticated physical press starts preview");
        h.assertTrue(PointMeltdown.holding(f.p) && PointMeltdown.marks(f.p).size()==1,"preview records one visible block; "+f.debug());
        near(h,f.rawCp(),f.before,"preview is free"); near(h,f.xp(),0,"preview has no mastery");
        f.releaseChecked(1);
        h.assertFalse(f.release(1) || f.start(2),"duplicate release or new press cannot replace a focusing owner");
        PointMeltdown.tick(f.p); PointMeltdown.tick(f.p); PointMeltdown.tick(f.p);
        h.assertTrue(h.getLevel().getBlockState(f.target.pos()).is(Blocks.STONE),"same-tick public callbacks cannot manufacture focus");
        after(h,9,()->{
            h.assertTrue(h.getLevel().getBlockState(f.target.pos()).is(Blocks.STONE),"nine elapsed server ticks cannot remove terrain");
            h.assertValueEqual(f.hook.sources,0,"no source event before the tenth focus tick");
            near(h,f.rawCp(),f.before,"focus remains free before native removal");
        });
        after(h,11,()->{
            f.assertSuccess();
            h.assertValueEqual(f.p.getMainHandItem().getDamageValue(),1,"one ordinary native pickaxe wear");
            h.assertValueEqual(f.drops(),1,"stone produces one ordinary cobblestone");
            h.assertTrue(h.getLevel().getBlockState(behind).is(Blocks.STONE),"newly exposed stone is never reacquired");
            h.assertFalse(f.release(1) || f.start(1),"finished physical edges cannot harvest again");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void point_cobblestone_preserves_native_drop_and_pickaxe_wear(GameTestHelper h) {
        successfulHarvest(h,Blocks.COBBLESTONE,false,false);
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void point_oak_planks_allow_ordinary_empty_hand_harvest(GameTestHelper h) {
        successfulHarvest(h,Blocks.OAK_PLANKS,true,false);
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void point_last_durability_pickaxe_breaks_natively_and_keeps_stone_drop(GameTestHelper h) {
        successfulHarvest(h,Blocks.STONE,false,true);
    }
    private static void successfulHarvest(GameTestHelper h,Block block,boolean emptyHand,boolean lastUse) {
        Fixture f = new Fixture(h); f.target(block,6);
        if (!emptyHand) {
            ItemStack tool = new ItemStack(lastUse ? Items.WOODEN_PICKAXE : Items.IRON_PICKAXE);
            if (lastUse) tool.setDamageValue(tool.getMaxDamage()-1);
            f.p.setItemInHand(InteractionHand.MAIN_HAND,tool);
        }
        f.begin(1);
        after(h,11,()->{
            f.assertSuccess(); h.assertValueEqual(f.drops(),1,"one ordinary canonical native drop");
            if (emptyHand || lastUse) h.assertTrue(f.p.getMainHandItem().isEmpty(),"empty hand or final-use native tool destruction is preserved");
            else h.assertValueEqual(f.p.getMainHandItem().getDamageValue(),1,"tool wears only once");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void point_walking_keeps_visible_frozen_face_without_a_stationary_leash(GameTestHelper h) {
        Fixture f = new Fixture(h); f.target(Blocks.STONE,6); f.pickaxe(); f.begin(1);
        Vec3 original = f.p.position();
        after(h,4,()->{f.walk(.75,0,.2);f.face(f.target.pos());});
        after(h,7,()->{
            f.walk(.35,0,0); f.face(f.target.pos());
            h.assertTrue(PointMeltdown.active(f.p),"ordinary walking retains the same visible north face");
            h.assertTrue(PointMeltdown.marks(f.p).getFirst().pos().equals(f.target.pos()),"walking never substitutes a new target");
        });
        after(h,11,()->{
            h.assertTrue(f.p.position().distanceToSqr(original)>1,"fixture actually walked beyond one metre");
            f.assertSuccess(); h.assertValueEqual(f.drops(),1,"moving focus still produces one native drop"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=155)
    public static void point_focus_state_face_obstruction_range_and_lifecycle_changes_stay_free(GameTestHelper h) {
        invalidatedFocus(h,0);
    }
    private static void invalidatedFocus(GameTestHelper h,int mode) {
        Fixture f = new Fixture(h); f.target(Blocks.STONE,6); f.pickaxe(); f.begin(1);
        after(h,4,()->{
            switch (mode) {
                case 0 -> h.getLevel().setBlockAndUpdate(f.target.pos(),Blocks.COBBLESTONE.defaultBlockState());
                case 1 -> {f.p.setPos(f.target.pos().getX()+4,f.p.getY(),f.target.pos().getZ()+.5);f.p.connection.resetPosition();f.face(f.target.pos());}
                case 2 -> f.block(f.target.pos().north(3),Blocks.DIRT);
                case 3 -> {f.walk(0,0,-31);f.face(f.target.pos());}
                case 4 -> h.assertTrue(f.abort(1),"matching physical abort is accepted");
                case 5 -> f.p.containerMenu=ChestMenu.threeRows(17,f.p.getInventory());
                case 6 -> AbilityStorage.get(f.p).selectCategory("electromaster");
                case 7 -> f.p.setHealth(0);
                case 8 -> f.p.getInventory().setItem(8,new ItemStack(Items.APPLE));
                case 9 -> AbilityStorage.get(f.p).presets.edit(0,0,"",id->true);
                case 10 -> AbilityStorage.get(f.p).interfering=true;
                default -> throw new AssertionError();
            }
        });
        after(h,11,()->{
            h.assertFalse(h.getLevel().isEmptyBlock(f.target.pos()),"invalidated focus never removes terrain, case "+mode);
            h.assertFalse(PointMeltdown.active(f.p),"invalidated focus is retired, case "+mode);
            f.assertFree("focus invalidation "+mode); h.assertValueEqual(f.hook.sources,0,"no native attempt after invalidation");
            f.close(); if (mode<10) invalidatedFocus(h,mode+1); else h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void point_source_and_native_permission_cancellation_have_no_cost_wear_or_drop(GameTestHelper h) {
        permissionCancellation(h,true);
    }
    private static void permissionCancellation(GameTestHelper h,boolean source) {
        Fixture f = new Fixture(h); f.target(Blocks.STONE,6); f.pickaxe();
        if (source) f.hook.source=e->e.setCanceled(true); else f.hook.nativeBreak=e->e.setCanceled(true);
        f.begin(1);
        after(h,11,()->{
            f.assertFree(source ? "source cancellation" : "native BreakEvent cancellation");
            h.assertTrue(h.getLevel().getBlockState(f.target.pos()).is(Blocks.STONE),"veto leaves native block intact");
            h.assertValueEqual(f.hook.sources,1,"one source callback per real attempt");
            h.assertValueEqual(f.hook.breaks,source ? 0 : 1,"native break event is neither skipped nor duplicated");
            h.assertFalse(PointMeltdown.active(f.p),"canceled native attempt is terminal");
            f.close(); if (source) permissionCancellation(h,false); else h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=145)
    public static void point_native_callback_mutations_cannot_commit_stale_owner_tool_or_target(GameTestHelper h) {
        callbackMutation(h,0);
    }
    private static void callbackMutation(GameTestHelper h,int mode) {
        Fixture f = new Fixture(h); f.target(Blocks.STONE,6); f.pickaxe();
        f.hook.nativeBreak=e->{
            switch (mode) {
                case 0 -> h.getLevel().setBlockAndUpdate(f.target.pos(),Blocks.COBBLESTONE.defaultBlockState());
                case 1 -> f.p.setItemInHand(InteractionHand.MAIN_HAND,f.p.getMainHandItem().copy());
                case 2 -> f.p.getInventory().setItem(8,new ItemStack(Items.APPLE));
                case 3 -> f.p.containerMenu=ChestMenu.threeRows(17,f.p.getInventory());
                case 4 -> AbilityStorage.get(f.p).selectCategory("electromaster");
                case 5 -> f.p.setHealth(0);
                case 6 -> f.walk(.2,0,0);
                case 7 -> {throw new IllegalStateException("deliberate point native permission failure");}
                case 8 -> f.abort(1);
                case 9 -> f.p.getPersistentData().put(HighPowerResources.DATA_KEY,f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy());
                default -> throw new AssertionError();
            }
        };
        f.begin(1);
        after(h,11,()->{
            h.assertValueEqual(f.hook.sources,1,"real source callback reached");h.assertValueEqual(f.hook.breaks,1,"real native permission callback reached");
            h.assertFalse(h.getLevel().isEmptyBlock(f.target.pos()),"callback mutation preserves unremoved cell, case "+mode);
            f.assertFree("native callback mutation "+mode);h.assertFalse(PointMeltdown.active(f.p),"stale callback owner retires");
            f.close();if(mode<9)callbackMutation(h,mode+1);else h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void point_nested_release_start_and_native_destroy_cannot_duplicate_harvest(GameTestHelper h) {
        Fixture f = new Fixture(h);f.target(Blocks.STONE,6);f.pickaxe();
        f.hook.source=e->{h.assertFalse(f.start(2),"source callback cannot replace resolving context");h.assertFalse(f.release(1),"source callback cannot release twice");};
        f.hook.nativeBreak=e->{
            h.assertFalse(f.p.gameMode.destroyBlock(f.target.pos()),"recursive native destroy is refused before another event");
            h.assertFalse(BeamEtchingMining.attempt(f.p,f.target,1,()->true,BeamEtchingMining.newCapacity()).removed(),"shared native permit excludes another skill's nested attempt");
            h.assertFalse(f.release(1) || f.start(3),"native callbacks cannot manufacture another point cast");
        };
        f.begin(1);after(h,11,()->{f.assertSuccess();h.assertValueEqual(f.drops(),1,"reentry cannot duplicate native loot");h.assertValueEqual(f.p.getMainHandItem().getDamageValue(),1,"reentry cannot duplicate tool wear");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void point_native_partial_failure_settles_removed_cell_without_fabricated_loot(GameTestHelper h) {
        Fixture f = new Fixture(h);f.target(Blocks.STONE,6);f.pickaxe();NeighborFailure failure=f.register(new NeighborFailure(f));f.begin(1);
        after(h,11,()->{
            h.assertTrue(failure.thrown,"real post-removal neighbor notification was reached");
            f.assertSuccess();h.assertValueEqual(f.p.getMainHandItem().getDamageValue(),1,"already-applied native wear is retained");
            h.assertValueEqual(f.drops(),0,"interrupted native loot is never synthesized");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void point_native_harvest_check_veto_retains_wear_without_removal_cost_or_loot(GameTestHelper h) {
        Fixture f=new Fixture(h);f.target(Blocks.STONE,6);f.pickaxe();boolean[] called={false};
        f.register(new Object(){@SubscribeEvent public void harvest(PlayerEvent.HarvestCheck event){
            if(event.getEntity()==f.p && event.getPos().equals(f.target.pos())){called[0]=true;event.setCanHarvest(false);}
        }});
        f.begin(1);after(h,11,()->{
            h.assertTrue(called[0],"actual native HarvestCheck event was reached");
            h.assertTrue(h.getLevel().getBlockState(f.target.pos()).is(Blocks.STONE),"native harvest veto preserves the block");
            h.assertValueEqual(f.p.getMainHandItem().getDamageValue(),1,"native wear before harvest refusal is retained");
            near(h,f.rawCp(),f.before,"no removal means no CP debit");h.assertValueEqual(f.drops(),0,"harvest veto creates no loot");
            h.assertValueEqual(f.hook.sources,1,"one source event");h.assertValueEqual(f.hook.breaks,1,"one native break event");
            h.assertFalse(HighPowerResources.hasReservation(f.p) || PointMeltdown.active(f.p),"veto releases claim and ends focus");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void point_full_inventory_wrong_tools_and_enchantments_never_attempt_native_work(GameTestHelper h) {
        refusedAdmission(h,0);
    }
    private static void refusedAdmission(GameTestHelper h,int mode) {
        Fixture f = new Fixture(h);f.target(Blocks.STONE,6);
        switch(mode){
            case 0 -> {for(int i=0;i<36;i++)f.p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));f.pickaxe();}
            case 1 -> f.p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            case 2 -> f.p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SHOVEL));
            case 3 -> {f.pickaxe();f.p.getMainHandItem().enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);}
            case 4 -> {f.pickaxe();h.getLevel().setBlockAndUpdate(f.target.pos(),Blocks.DIAMOND_ORE.defaultBlockState());}
            default -> throw new AssertionError();
        }
        f.capture();boolean started=f.start(1);
        if(mode==3){h.assertTrue(HighPowerCombat.unsupportedReason(f.p).isPresent(),"silk touch is outside the existing combat contract");h.assertFalse(started,"unsupported enchanted owner is refused before preview");}
        else h.assertTrue(started,"harmless refused-target preview starts, case "+mode+"; "+f.debug());
        f.release(1);
        after(h,11,()->{
            h.assertFalse(h.getLevel().isEmptyBlock(f.target.pos()),"unsupported admission leaves target intact, case "+mode);
            f.assertFree("admission refusal "+mode);h.assertValueEqual(f.hook.sources,0,"unsupported admission posts no skill terrain event");
            h.assertValueEqual(f.hook.breaks,0,"unsupported admission reaches no native destroy");
            f.close();if(mode<4)refusedAdmission(h,mode+1);else h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=50)
    public static void point_native_range_passes_thirty_two_and_exact_thirty_six_but_refuses_outside(GameTestHelper h) {
        range(h,0);
    }
    private static void range(GameTestHelper h,int mode) {
        Fixture f = new Fixture(h);double distance = mode==0 ? 32.001 : mode==1 ? 36 : 36.0001;
        f.verticalTarget(Blocks.STONE,distance);f.pickaxe();f.capture();
        h.assertTrue(f.start(1),"bounded range preview starts");
        if(mode<2){h.assertTrue(PointMeltdown.marks(f.p).size()==1,"actual long-range surface is admitted, distance="+distance+"; "+f.debug());f.releaseChecked(1);}
        else f.release(1);
        after(h,11,()->{
            if(mode<2){f.assertSuccess();h.assertValueEqual(f.drops(),1,"long-range native removal retains one ordinary drop");}
            else{f.assertFree("beyond thirty-six metres");h.assertTrue(h.getLevel().getBlockState(f.target.pos()).is(Blocks.STONE),"outside target remains intact");h.assertValueEqual(f.hook.sources,0,"outside surface reaches no native event");}
            f.close();if(mode<2)range(h,mode+1);else h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=60)
    public static void point_quarter_metre_trace_boundaries_preserve_original_outside_origin(GameTestHelper h) {
        sliceBoundary(h,0);
    }
    private static void sliceBoundary(GameTestHelper h,int mode) {
        Fixture f=new Fixture(h);double[] distances={5.9999,6,6.0001,6.25};
        f.target(Blocks.STONE,distances[mode]);f.pickaxe();f.begin(1);
        after(h,11,()->{f.assertSuccess();h.assertValueEqual(f.drops(),1,"slice boundaries keep one ordinary native drop");f.close();if(mode<3)sliceBoundary(h,mode+1);else h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void point_unloaded_trace_never_requests_remote_chunks(GameTestHelper h) {
        Fixture f = new Fixture(h);BlockPos absent = new BlockPos(12_000_007,100,12_000_007);
        h.assertFalse(h.getLevel().hasChunkAt(absent),"remote geometry begins unloaded");
        int loaded = h.getLevel().getChunkSource().getLoadedChunksCount();double before=f.rawCp();
        try {
            var trace=PointMeltdown.class.getDeclaredMethod("firstVisible",ServerPlayer.class,Vec3.class,Vec3.class);trace.setAccessible(true);
            h.assertTrue(trace.invoke(null,f.p,Vec3.atCenterOf(absent).add(0,0,-36),Vec3.atCenterOf(absent))==null,
                    "unloaded ray refuses before native clip can request chunks");
        } catch (ReflectiveOperationException failure) {throw new AssertionError(failure);}
        h.assertTrue(!h.getLevel().hasChunkAt(absent) && loaded==h.getLevel().getChunkSource().getLoadedChunksCount(),"bounded selection does not force-load remote chunks");
        near(h,f.rawCp(),before,"unloaded trace remains free");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void point_hundred_tick_preview_deadline_never_autofires(GameTestHelper h) {
        Fixture f = new Fixture(h);f.target(Blocks.STONE,6);f.pickaxe();f.capture();h.assertTrue(f.start(1),"preview begins");
        after(h,99,()->h.assertTrue(PointMeltdown.holding(f.p),"ninety-nine elapsed ticks retain harmless preview"));
        after(h,101,()->{
            h.assertFalse(PointMeltdown.active(f.p),"hundredth actual tick retires preview");h.assertFalse(f.release(1),"expired release cannot revive work");
            h.assertTrue(h.getLevel().getBlockState(f.target.pos()).is(Blocks.STONE),"expiry is never a release");f.assertFree("preview expiry");
            h.assertValueEqual(f.hook.sources,0,"expiry posts no native event");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void point_stale_release_and_abort_cannot_retire_a_new_physical_hold(GameTestHelper h) {
        Fixture f = new Fixture(h);f.target(Blocks.STONE,6);f.pickaxe();f.capture();h.assertTrue(f.start(1),"first physical preview");
        String old=f.wire(1);h.assertTrue(f.abort(1),"matching old abort");
        after(h,2,()->{
            h.assertTrue(f.start(2),"new physical input starts after preview cadence");
            h.assertFalse(PointMeltdown.release(f.p,old) || PointMeltdown.abort(f.p,old),"old exact input cannot affect current hold");
            h.assertTrue(PointMeltdown.holding(f.p),"new physical hold remains captured");f.releaseChecked(2);
        });
        after(h,13,()->{f.assertSuccess();h.assertValueEqual(f.drops(),1,"only the new physical release harvests");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void point_canonical_loot_rejects_custom_pack_copies_and_modified_official_resources(GameTestHelper h) throws java.io.IOException {
        Fixture f = new Fixture(h);
        for(Block block:List.of(Blocks.STONE,Blocks.COBBLESTONE,Blocks.OAK_PLANKS)){
            var state=block.defaultBlockState();String path=BuiltInRegistries.BLOCK.getKey(block).getPath();
            var resource=f.p.server.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("loot_table/blocks/"+path+".json")).orElseThrow();
            byte[] bytes;try(var input=resource.open()){bytes=input.readAllBytes();}
            h.assertTrue(VanillaMiningContract.canonicalPointLootEvidence(state,resource.sourcePackId(),resource.knownPackInfo(),bytes),"actual canonical point loot is admitted: "+path);
            h.assertFalse(VanillaMiningContract.canonicalPointLootEvidence(state,"file/custom.zip",resource.knownPackInfo(),bytes),"identical copied custom-pack bytes remain unsupported");
            h.assertFalse(VanillaMiningContract.canonicalPointLootEvidence(state,"vanilla",Optional.empty(),bytes),"claimed pack name without provenance is unsupported");
            byte[] changed=bytes.clone();changed[changed.length/2]^=1;
            h.assertFalse(VanillaMiningContract.canonicalPointLootEvidence(state,"vanilla",resource.knownPackInfo(),changed),"same-name modified loot bytes are refused");
            h.assertFalse(VanillaMiningContract.reviewedBlock(state),"the extra point blocks do not widen NEW213's reviewed set");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void point_malformed_saved_cooldown_never_refills_or_admits_a_cast(GameTestHelper h) {
        for(int mode=0;mode<5;mode++){
            Fixture f=new Fixture(h);f.target(Blocks.STONE,6);f.pickaxe();f.capture();
            var tag=new CompoundTag();tag.putInt("version",1);tag.putInt("remaining",0);
            switch(mode){
                case 0 -> f.p.getPersistentData().putString(PointMeltdown.COOLDOWN_KEY,"invalid");
                case 1 -> {tag.putInt("version",2);f.p.getPersistentData().put(PointMeltdown.COOLDOWN_KEY,tag);}
                case 2 -> {tag.putInt("remaining",-1);f.p.getPersistentData().put(PointMeltdown.COOLDOWN_KEY,tag);}
                case 3 -> {tag.putInt("remaining",11);f.p.getPersistentData().put(PointMeltdown.COOLDOWN_KEY,tag);}
                case 4 -> {tag.putString("extra","invalid");f.p.getPersistentData().put(PointMeltdown.COOLDOWN_KEY,tag);}
                default -> throw new AssertionError();
            }
            var saved=f.p.getPersistentData().get(PointMeltdown.COOLDOWN_KEY).copy();
            h.assertFalse(f.start(1),"malformed persisted cooldown refuses before preview, case "+mode);
            PointMeltdown.tick(f.p);
            h.assertTrue(saved.equals(f.p.getPersistentData().get(PointMeltdown.COOLDOWN_KEY)),"malformed cooldown is not silently erased");
            f.assertFree("corrupt cooldown "+mode);f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_cleanup",timeoutTicks=15)
    public static void point_live_clear_and_remove_cannot_replay_old_or_rejected_physical_inputs(GameTestHelper h) {
        // clear() is deliberately tested in its own batch, outside other live focus fixtures.
        for(boolean clear:List.of(true,false)){
            Fixture f=new Fixture(h);f.target(Blocks.STONE,6);f.pickaxe();f.capture();
            h.assertTrue(f.start(1),"first physical hold starts");h.assertFalse(f.start(2),"fresh press while held is rejected and retired");
            if(clear)PointMeltdown.clear();else PointMeltdown.remove(f.p);
            h.assertFalse(f.start(1) || f.start(2),"live cleanup preserves both old and rejected input watermarks");
            h.assertTrue(f.start(3),"genuinely newer physical input remains usable after cleanup");
            h.assertTrue(f.abort(3),"newly accepted physical hold still has its own cancellation authority");
            h.assertTrue(h.getLevel().getBlockState(f.target.pos()).is(Blocks.STONE),"cleanup and replay never remove terrain");f.assertFree("live cleanup replay");f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void point_native_clone_carries_cooldown_only_after_irreversible_removal(GameTestHelper h) {
        cloneDuringNativeCallback(h,false);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void point_completed_cooldown_survives_native_save_logout_reload_and_clone(GameTestHelper h) {
        Fixture f=new Fixture(h);f.target(Blocks.STONE,6);f.pickaxe();f.begin(1);
        after(h,11,()->{
            f.assertSuccess();int remaining=PointMeltdown.cooldownRemaining(f.p);double cp=f.rawCp();
            h.assertTrue(remaining>0,"ordinary completed cast still owns a remaining cooldown");
            AbilityStorage.save(f.p);CompoundTag saved=f.p.saveWithoutId(new CompoundTag());
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));
            f.p.load(saved);AbilityStorage.remove(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();
            h.assertValueEqual(PointMeltdown.cooldownRemaining(f.p),remaining,"native save/logout/reload preserves exact remaining cooldown");
            h.assertFalse(f.start(2),"new authenticated input cannot bypass reloaded cooldown");near(h,f.rawCp(),cp,"reload and refused retry neither refill nor debit CP");
            ServerPlayer replacement=cloneWitness(f);
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement,f.p,false));
            h.assertValueEqual(PointMeltdown.cooldownRemaining(replacement),remaining,"ordinary post-return clone preserves paid cooldown");
            h.assertFalse(PointMeltdown.active(f.p) || PointMeltdown.active(replacement),"persistence restores no target or focus context");
            h.assertValueEqual(f.hook.sources,1,"persistence creates no second native attempt");h.assertValueEqual(f.drops(),1,"persistence creates no duplicate loot");h.succeed();
        });
    }
    private static void cloneDuringNativeCallback(GameTestHelper h,boolean afterRemoval) {
        Fixture f=new Fixture(h);f.target(Blocks.STONE,6);f.pickaxe();
        ServerPlayer replacement=cloneWitness(f);
        boolean[] cloned={false};double[] replacementCp={Double.NaN};
        Runnable clone=()->{
            h.assertTrue(f.p.getUUID().equals(replacement.getUUID()) && f.p!=replacement,"native replacement has the same UUID and a distinct incarnation");
            cloned[0]=true;NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement,f.p,false));
            replacementCp[0]=replacement.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp");
        };
        if(afterRemoval)f.register(new Object(){@SubscribeEvent public void loot(EntityJoinLevelEvent event){
            if(!cloned[0] && event.getLevel()==f.p.level() && event.getEntity() instanceof ItemEntity item
                    && item.getItem().is(Items.COBBLESTONE) && item.getBoundingBox().intersects(new AABB(f.target.pos()).inflate(1))){
                h.assertTrue(h.getLevel().isEmptyBlock(f.target.pos()),"clone callback follows actual native removal");clone.run();
            }
        }});
        else f.hook.source=event->clone.run();
        f.begin(1);
        after(h,11,()->{
            h.assertTrue(cloned[0],"actual native callback dispatched the Clone event");
            h.assertTrue(f.hook.settlementObserved,"original ledger was observed in the native attempt post");
            near(h,f.hook.settledCp,f.before-(afterRemoval?f.cost:0),"clone preserves exactly the already-committed debit");
            h.assertValueEqual(f.hook.sources,1,"clone never produces an extra source event");
            h.assertValueEqual(f.hook.breaks,afterRemoval?1:0,"only post-removal clone reached native break");
            h.assertTrue(h.getLevel().isEmptyBlock(f.target.pos())==afterRemoval,"clone cannot fabricate or roll back removal");
            h.assertValueEqual(f.drops(),afterRemoval?1:0,"native clone neither fabricates nor duplicates drops");
            h.assertValueEqual(PointMeltdown.cooldownRemaining(replacement),afterRemoval?PointMeltdownRules.cooldown(java.math.BigDecimal.valueOf(3)):0,
                    "replacement inherits full cooldown only for an actual removal");
            near(h,replacement.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp"),replacementCp[0],"cooldown handoff adds no replacement CP debit");
            h.assertFalse(PointMeltdown.active(f.p) || PointMeltdown.active(replacement),"clone retains no focusing context on either incarnation");
            h.assertFalse(HighPowerResources.hasReservation(f.p) || HighPowerResources.hasReservation(replacement),"clone cannot strand either native claim");
            f.close();if(!afterRemoval)cloneDuringNativeCallback(h,true);else h.succeed();
        });
    }

    private static ServerPlayer cloneWitness(Fixture f) {
        var cookie=CommonListenerCookie.createInitial(f.p.getGameProfile(),false);
        ServerPlayer replacement=new ServerPlayer(f.p.server,f.h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        // This is an actual Clone-event data witness, not a published player-list replacement.
        // Its constructor rebinds the UUID-shared advancements; keep that separate native
        // service owned by the still-connected original while the witness remains unconnected.
        f.p.getAdvancements().setPlayer(f.p);
        return replacement;
    }

    public static final class Hook {
        final ServerPlayer owner;int sources,breaks;long sourceTick=-1;double sourceCp,settledCp;boolean settlementObserved;
        Consumer<SkillBlockDestroyEvent> source=event->{};Consumer<BlockEvent.BreakEvent> nativeBreak=event->{};
        Hook(ServerPlayer owner){this.owner=owner;}
        @SubscribeEvent public void source(SkillBlockDestroyEvent event){
            if(event.player==owner && event.skill.equals("meltdowner.point_meltdown")){sources++;sourceTick=owner.server.getTickCount();sourceCp=owner.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp");source.accept(event);}
        }
        @SubscribeEvent public void nativeBreak(BlockEvent.BreakEvent event){if(event.getPlayer()==owner){breaks++;nativeBreak.accept(event);}}
        /** Observe the settled ledger in the attempt's real post, before next PlayerTick recovery. */
        @SubscribeEvent(priority=EventPriority.LOWEST) public void settled(ServerTickEvent.Post event){
            if(event.getServer()==owner.server && sourceTick==event.getServer().getTickCount()){
                settledCp=owner.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp");settlementObserved=true;
            }
        }
    }
    public static final class NeighborFailure {
        final Fixture f;boolean thrown;
        NeighborFailure(Fixture f){this.f=f;}
        @SubscribeEvent public void neighbor(BlockEvent.NeighborNotifyEvent event){
            if(!thrown && event.getLevel()==f.p.level() && event.getPos().equals(f.target.pos()) && event.getState().isAir() && HighPowerResources.hasReservation(f.p)){
                thrown=true;throw new IllegalStateException("deliberate point neighbor notification failure after native removal");
            }
        }
    }
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-7,why+": "+actual+" / "+expected);}

    private static long now(GameTestHelper h){return Integer.toUnsignedLong(h.getLevel().getServer().getTickCount());}
    private static void after(GameTestHelper h,long delay,Runnable action){steps(h).at(now(h)+delay,action);}
    private static Steps steps(GameTestHelper h){return STEPS.computeIfAbsent(h,Steps::new);}
    /** One native GameTest callback is installed at startup. Later fixtures and steps only
     * mutate this private queue, never the vanilla scheduler map while it is being iterated. */
    private static final class Steps {
        final GameTestHelper h;final List<Fixture> fixtures=new ArrayList<>();
        final NavigableMap<Long,List<Runnable>> pending=new TreeMap<>();boolean stopped;
        Steps(GameTestHelper h){
            this.h=h;
            h.testInfo.addListener(new GameTestListener(){
                public void testStructureLoaded(GameTestInfo info){}
                public void testPassed(GameTestInfo info,GameTestRunner runner){stop();}
                public void testFailed(GameTestInfo info,GameTestRunner runner){stop();}
                public void testAddedForRerun(GameTestInfo info,GameTestInfo next,GameTestRunner runner){stop();}
            });
            h.onEachTick(this::tick);
        }
        void at(long tick,Runnable action){pending.computeIfAbsent(tick,ignored->new ArrayList<>()).add(action);}
        void tick(){
            if(stopped)return;
            // Assert only after this frame's native PlayerTick, so ordinary CP recovery
            // has a defined phase relative to the previous server-post debit snapshot.
            for(Fixture f:List.copyOf(fixtures))if(!f.closed)f.p.doTick();
            while(!stopped && !pending.isEmpty() && pending.firstKey()<=now(h)){
                for(Runnable action:pending.pollFirstEntry().getValue()){if(stopped)return;action.run();}
            }
        }
        void stop(){stopped=true;pending.clear();STEPS.remove(h);}
    }

    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final Hook hook;
        final List<Object> hooks=new ArrayList<>();final List<BlockPos> positions=new ArrayList<>();
        BeamEtchingMining.Target target;double before,cost;long released;boolean closed;
        Fixture(GameTestHelper h){
            this.h=h;var server=h.getLevel().getServer();
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"Point"+UUID.randomUUID().toString().substring(0,9)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);
            channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            // Tutorial delivery intentionally changes inventory at login; that separate feature
            // is pre-completed so these casts exercise only their declared inventory mutations.
            var tutorial=cn.academy.port.tutorial.TutorialStorage.get(p);
            tutorial.restore(tutorial.conditionBits(),tutorial.activatedIds(),true,tutorial.firstOpened());cn.academy.port.tutorial.TutorialStorage.save(p);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;
            Vec3 at=h.absoluteVec(new Vec3(12.5,38,6));p.moveTo(at.x,at.y,at.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();
            h.assertTrue(HighPowerCombat.enroll(p,3),"real connected LV3 native owner");var state=AbilityStorage.get(p);
            state.selectCategory("meltdowner");state.setLevel(3);for(String id:List.of(DualPoint.ID,BeamEtching.ID,PointMeltdown.ID))state.learn(id);
            state.activated=true;state.overloadFine=true;h.assertTrue(state.presets.edit(0,0,PointMeltdown.ID,id->true),"bind learned NEW233");
            HighPowerResources.initializeBudget(p);epoch();hook=register(new Hook(p));
            h.testInfo.addListener(new GameTestListener(){
                public void testStructureLoaded(GameTestInfo info){}
                public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
                public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
                public void testAddedForRerun(GameTestInfo info,GameTestInfo next,GameTestRunner runner){close();}
            });
            // Later cases attach to the existing scheduler without registering new native callbacks.
            steps(h).fixtures.add(this);
        }
        void target(Block block,double distance){
            BlockPos pos=h.absolutePos(new BlockPos(12,40,12));Vec3 surface=new Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ());
            Vec3 eye=surface.add(0,0,-distance);p.setPos(eye.x,eye.y-p.getEyeHeight(),eye.z);p.setYRot(0);p.setXRot(0);p.connection.resetPosition();
            // Setup explicitly loads the test corridor. Selection itself must never load it.
            for(int z=p.blockPosition().getZ()-1;z<=pos.getZ()+1;z++)h.getLevel().getChunk(new BlockPos(pos.getX(),pos.getY(),z));
            block(pos.below(),Blocks.STONE);block(pos,block);target=new BeamEtchingMining.Target(pos,block.defaultBlockState(),surface,Direction.NORTH);
        }
        void verticalTarget(Block block,double distance){
            BlockPos pos=h.absolutePos(new BlockPos(12,60,12));Vec3 surface=new Vec3(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            Vec3 eye=surface.add(0,-distance,0);p.setPos(eye.x,eye.y-p.getEyeHeight(),eye.z);p.setYRot(0);p.setXRot(-90);p.connection.resetPosition();
            h.getLevel().getChunk(pos);block(pos,block);target=new BeamEtchingMining.Target(pos,block.defaultBlockState(),surface,Direction.DOWN);
        }
        BlockPos block(BlockPos pos,Block block){positions.add(pos);h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());return pos;}
        void face(BlockPos pos){Vec3 delta=Vec3.atCenterOf(pos).subtract(p.getEyePosition());p.setYRot((float)-Math.toDegrees(Math.atan2(delta.x,delta.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));}
        void walk(double x,double y,double z){p.setPos(p.position().add(x,y,z));p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();}
        void pickaxe(){p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        boolean request(String action,long input){return cn.academy.port.AcademyGameplay.requestFromClient(p,new cn.academy.port.AcademyNetwork.Request(action,wire(input)));}
        boolean start(long input){return request("hp_point_meltdown_start",input);}
        boolean release(long input){return request("hp_point_meltdown_release",input);}
        boolean abort(long input){return request("hp_point_meltdown_abort",input);}
        double rawCp(){return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp");}
        double xp(){return AbilityStorage.get(p).exp(PointMeltdown.ID);}
        void capture(){before=rawCp();cost=PointMeltdownRules.cost(HighPowerGrowth.values(p).position());}
        void begin(long input){capture();h.assertTrue(start(input),"authenticated point preview starts");h.assertTrue(PointMeltdown.marks(p).size()==1,"one reviewed visible surface is frozen; "+debug());releaseChecked(input);}
        void releaseChecked(long input){released=p.server.getTickCount();h.assertTrue(release(input),"matching physical release starts focus");h.assertTrue(PointMeltdown.active(p) && !PointMeltdown.holding(p),"release retains an active focusing context");near(h,rawCp(),before,"release is free");}
        int drops(){ItemStack expected=VanillaMiningContract.expectedPointDrop(target.state());return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(target.pos()).inflate(.9)).stream().filter(item->ItemStack.isSameItemSameComponents(item.getItem(),expected)).mapToInt(item->item.getItem().getCount()).sum();}
        void assertSuccess(){
            h.assertTrue(h.getLevel().isEmptyBlock(target.pos()),"actual native removal occurred");
            h.assertTrue(hook.settlementObserved,"settlement was observed inside the actual native server post");
            near(h,hook.sourceCp,before,"focus stayed free up to native source event");near(h,hook.settledCp,hook.sourceCp-cost,"exactly one inherited continuous CP debit before recovery");
            h.assertTrue(HighPowerGrowth.values(p).cpRecovery()/20>=cost,"one ordinary recovery tick covers this fixture's small LV3 debit");
            near(h,rawCp(),before,"later native PlayerTick normally recovers the debit up to the CP cap");
            h.assertValueEqual(hook.sources,1,"one native skill source event");h.assertValueEqual(hook.breaks,1,"one native BreakEvent");
            h.assertTrue(hook.sourceTick-released==PointMeltdownRules.FOCUS_TICKS,"native attempt occurs at exactly the tenth real server tick: "+(hook.sourceTick-released));
            h.assertFalse(PointMeltdown.active(p),"single block completion retires controller");h.assertFalse(HighPowerResources.hasReservation(p),"native work releases its CP claim");near(h,xp(),0,"NEW233 does not add mastery");
        }
        void assertFree(String why){
            if(hook.sources>0){h.assertTrue(hook.settlementObserved,why+" has a same-post ledger observation");near(h,hook.settledCp,before,why+" has no debit hidden by later recovery");}
            near(h,rawCp(),before,why+" has no CP debit");h.assertValueEqual(drops(),0,why+" has no synthetic drops");h.assertValueEqual(p.getMainHandItem().getDamageValue(),0,why+" has no tool wear");h.assertFalse(HighPowerResources.hasReservation(p),why+" leaves no CP claim");near(h,xp(),0,why+" has no mastery");
        }
        String debug(){
            try{
                var aim=PointMeltdown.class.getDeclaredMethod("aim",ServerPlayer.class);aim.setAccessible(true);
                var trace=PointMeltdown.class.getDeclaredMethod("firstVisible",ServerPlayer.class,Vec3.class,Vec3.class);trace.setAccessible(true);
                Vec3 eye=p.getEyePosition();Object hit=trace.invoke(null,p,eye,eye.add(p.getLookAngle().normalize().scale(36)));
                return "eye="+eye+" look="+p.getLookAngle()+" target="+target+" aim="+aim.invoke(null,p)+" trace="+hit
                        +" combat="+HighPowerCombat.unsupportedReason(p)+" mining="+VanillaMiningContract.unsupportedReason(p);
            }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        }
        <T>T register(T listener){hooks.add(listener);NeoForge.EVENT_BUS.register(listener);return listener;}
        void close(){
            if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);PointMeltdown.remove(p);
            for(BlockPos pos:positions){h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).forEach(ItemEntity::discard);h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());}
            p.containerMenu=p.inventoryMenu;AbilityStorage.remove(p);p.server.getPlayerList().remove(p);channel.finishAndReleaseAll();
        }
    }
}
