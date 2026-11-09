package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillBlockDestroyEvent;
import cn.academy.port.highpower.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Connected native players, actual destroyBlock and ordinary loot/durability under NEW213. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerBeamEtchingMiningRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_beam_etching_mining";
    private static void eq(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-8,why+": "+actual+" / "+expected);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_exact_seven_native_drops_one_durability_and_one_cp_each(GameTestHelper h){
        Fixture f=new Fixture(h);f.p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SHOVEL));
        var cap=BeamEtchingMining.newCapacity();Hook hook=f.hook(new Hook(f.p));double cp=f.cp();int i=0;
        for(Block block:List.of(Blocks.DIRT,Blocks.COARSE_DIRT,Blocks.ROOTED_DIRT,Blocks.MUD,Blocks.GRASS_BLOCK,Blocks.PODZOL,Blocks.MYCELIUM)){
            var target=f.target(block,i++);var inspect=BeamEtchingMining.inspect(f.p,target,cap);
            h.assertTrue(inspect.eligible(),"canonical exact7 eligibility: "+block+" "+inspect.reason());
            h.assertValueEqual(cap.reservations(),i-1,"inspect is read-only");
            var result=BeamEtchingMining.attempt(f.p,target,1,()->true,cap);
            h.assertTrue(result.removed(),"actual native removal "+block+": "+result.reason());
            h.assertTrue(h.getLevel().getBlockState(target.pos()).isAir(),"native block removal");
            h.assertValueEqual(f.drops(target.pos(),inspect.drop()),1,"one ordinary native loot item");
            h.assertValueEqual(f.p.getMainHandItem().getDamageValue(),i,"native durability exactly once");
            eq(h,f.cp(),cp-i,"only actual removals charged");
        }
        h.assertValueEqual(hook.sources,7,"one source event per attempt");h.assertValueEqual(hook.breaks,7,"one native BreakEvent per attempt");
        h.assertValueEqual(cap.reservations(),7,"same-cast prior drops reserve space conservatively");
        h.assertFalse(HighPowerResources.hasReservation(f.p),"synchronous reservations released");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_empty_hand_and_paired_native_control_keep_normal_harvest(GameTestHelper h){
        Fixture f=new Fixture(h);var target=f.target(Blocks.DIRT,0);double cp=f.cp();Hook hook=f.hook(new Hook(f.p));
        h.assertTrue(BeamEtchingMining.attempt(f.p,target,1,()->true,BeamEtchingMining.newCapacity()).removed(),"empty-hand native harvest");
        h.assertTrue(f.p.getMainHandItem().isEmpty(),"empty hand remains empty");eq(h,f.cp(),cp-1,"single CP payment");
        var control=f.target(Blocks.DIRT,1);h.assertTrue(f.p.gameMode.destroyBlock(control.pos()),"ordinary control native break");
        h.assertValueEqual(f.drops(control.pos(),new ItemStack(Items.DIRT)),1,"ordinary control one drop");
        eq(h,f.cp(),cp-1,"ordinary native break spends no ability CP");h.assertValueEqual(hook.sources,1,"ordinary break has no skill event");
        h.assertValueEqual(hook.breaks,2,"each normal native route posts exactly once");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_source_and_native_cancel_are_free_without_tool_wear(GameTestHelper h){
        for(boolean source:List.of(true,false)){
            Fixture f=new Fixture(h);f.p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SHOVEL));var target=f.target(Blocks.DIRT,source?0:1);
            double cp=f.cp();Hook hook=f.hook(new Hook(f.p));if(source)hook.source=e->e.setCanceled(true);else hook.nativeBreak=e->e.setCanceled(true);
            var cap=BeamEtchingMining.newCapacity();h.assertFalse(BeamEtchingMining.attempt(f.p,target,1,()->true,cap).removed(),"permission cancellation refuses");
            h.assertTrue(h.getLevel().getBlockState(target.pos())==target.state(),"canceled target intact");
            eq(h,f.cp(),cp,"canceled CP unchanged");h.assertValueEqual(f.p.getMainHandItem().getDamageValue(),0,"canceled native tool unchanged");
            h.assertValueEqual(f.drops(target.pos(),new ItemStack(Items.DIRT)),0,"cancel creates no synthetic loot");
            h.assertValueEqual(hook.sources,1,"one skill callback");h.assertValueEqual(hook.breaks,source?0:1,"native callback not duplicated");
            h.assertValueEqual(cap.reservations(),0,"cancel releases capacity");h.assertFalse(HighPowerResources.hasReservation(f.p),"cancel releases CP claim");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_reentrant_callback_state_tool_inventory_cp_and_owner_changes_refuse(GameTestHelper h){
        for(int scenario=0;scenario<8;scenario++){
            Fixture f=new Fixture(h);f.p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SHOVEL));var target=f.target(Blocks.DIRT,scenario);
            double cp=f.cp();AtomicBoolean owner=new AtomicBoolean(true);Hook hook=f.hook(new Hook(f.p));int which=scenario;
            hook.nativeBreak=e->{switch(which){
                case 0->h.getLevel().setBlockAndUpdate(target.pos(),Blocks.STONE.defaultBlockState());
                case 1->f.p.setItemInHand(InteractionHand.MAIN_HAND,f.p.getMainHandItem().copy());
                case 2->f.p.getInventory().setItem(8,new ItemStack(Items.APPLE));
                case 3->f.p.getPersistentData().put(HighPowerResources.DATA_KEY,f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy());
                case 4->owner.set(false);
                case 5->f.p.setGameMode(GameType.ADVENTURE);
                case 6->f.p.setHealth(0);
                case 7->{throw new IllegalStateException("deliberate permission callback failure");}
                default->throw new AssertionError();
            }};
            var cap=BeamEtchingMining.newCapacity();h.assertFalse(BeamEtchingMining.attempt(f.p,target,1,owner::get,cap).removed(),"callback invalidation scenario "+scenario);
            eq(h,f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp"),cp,"callback refusal never restores ledger or debits");
            h.assertValueEqual(f.p.getMainHandItem().getDamageValue(),0,"prewear callback rejection preserves tool");
            h.assertValueEqual(f.drops(target.pos(),new ItemStack(Items.DIRT)),0,"refusal no loot");h.assertValueEqual(cap.reservations(),0,"refusal no capacity consumption");
            h.assertFalse(HighPowerResources.hasReservation(f.p),"death/exception/refusal cannot strand CP claim");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_nested_destroy_and_attempt_cannot_duplicate_events_or_loot(GameTestHelper h){
        Fixture f=new Fixture(h);var target=f.target(Blocks.DIRT,0);var cap=BeamEtchingMining.newCapacity();double cp=f.cp();Hook hook=f.hook(new Hook(f.p));
        hook.nativeBreak=e->{
            h.assertFalse(f.p.gameMode.destroyBlock(target.pos()),"reentrant native break refused before native event");
            h.assertFalse(BeamEtchingMining.attempt(f.p,target,1,()->true,cap).removed(),"reentrant ability attempt refused");
        };
        h.assertTrue(BeamEtchingMining.attempt(f.p,target,1,()->true,cap).removed(),"outer authorized attempt survives rejected reentry");
        h.assertValueEqual(hook.sources,1,"one source event");h.assertValueEqual(hook.breaks,1,"one original native fireBlockBreak call");
        h.assertValueEqual(f.drops(target.pos(),new ItemStack(Items.DIRT)),1,"one normal loot item");eq(h,f.cp(),cp-1,"single reservation settlement");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_capacity_is_main36_component_aware_and_persists_virtual_drops(GameTestHelper h){
        Fixture f=new Fixture(h);for(int i=0;i<36;i++)f.p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        f.p.getInventory().selected=0;f.p.getInventory().setItem(0,new ItemStack(Items.IRON_SHOVEL));f.p.getInventory().setItem(1,new ItemStack(Items.DIRT,63));
        var cap=BeamEtchingMining.newCapacity();var first=f.target(Blocks.DIRT,0);var second=f.target(Blocks.DIRT,1);f.face(first.pos());
        h.assertTrue(BeamEtchingMining.inspect(f.p,first,cap).eligible(),"one compatible main-slot space admits exactly one drop");
        h.assertTrue(BeamEtchingMining.attempt(f.p,first,1,()->true,cap).removed(),"first native drop");f.face(second.pos());
        h.assertFalse(BeamEtchingMining.inspect(f.p,second,cap).eligible(),"floor drop virtual reservation remains even before pickup");
        f.p.getInventory().setItem(1,new ItemStack(Items.DIRT,62));h.assertTrue(BeamEtchingMining.inspect(f.p,second,cap).eligible(),"actual available space is resimulated conservatively");
        ItemStack named=new ItemStack(Items.DIRT,62);named.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("distinct"));f.p.getInventory().setItem(1,named);
        h.assertFalse(BeamEtchingMining.inspect(f.p,second,cap).eligible(),"different components never merge");
        h.assertValueEqual(cap.reservations(),1,"read-only previews never add/clear reservations");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_reservation_shared_spend_preserves_claim_and_no_whole_ledger_rollback(GameTestHelper h){
        Fixture f=new Fixture(h);f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp",10);
        var target=f.target(Blocks.DIRT,0);Hook hook=f.hook(new Hook(f.p));hook.nativeBreak=e->{
            h.assertTrue(HighPowerResources.hasReservation(f.p),"native callback sees held claim");eq(h,HighPowerResources.availableCp(f.p),9,"shared ledger subtracts claim from spendable CP");
            h.assertFalse(HighPowerResources.trySpend(f.p,HighPowerSkills.DUAL_POINT,10,1),"other skill cannot eat reserved CP");
            h.assertTrue(HighPowerResources.trySpend(f.p,HighPowerSkills.DUAL_POINT,3,1),"legitimate shared spend uses unreserved CP");
            f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putString("unrelated","kept");
        };
        h.assertTrue(BeamEtchingMining.attempt(f.p,target,1,()->true,BeamEtchingMining.newCapacity()).removed(),"valid current reservation survives legitimate ledger spend");
        eq(h,f.cp(),6,"costs add atomically without overwriting concurrent spend");
        h.assertTrue(f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getString("unrelated").equals("kept"),"no whole-ledger rollback");
        h.assertFalse(HighPowerResources.hasReservation(f.p),"settled claim released");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_refused_blocks_tools_modes_and_data_policy_are_inspectable(GameTestHelper h){
        Fixture f=new Fixture(h);var cap=BeamEtchingMining.newCapacity();int index=0;
        for(Block block:List.of(Blocks.STONE,Blocks.SAND,Blocks.GRAVEL,Blocks.CHEST,Blocks.WATER,Blocks.BEDROCK)){
            var target=f.target(block,index++);h.assertFalse(BeamEtchingMining.inspect(f.p,target,cap).eligible(),"unreviewed block refused "+block);
        }
        var dirt=f.target(Blocks.DIRT,index);for(ItemStack stack:List.of(new ItemStack(Items.STICK),new ItemStack(Items.DIAMOND_SWORD))){f.p.setItemInHand(InteractionHand.MAIN_HAND,stack);h.assertFalse(BeamEtchingMining.inspect(f.p,dirt,cap).eligible(),"unreviewed tool refused");}
        ItemStack enchanted=new ItemStack(Items.IRON_SHOVEL);enchanted.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);f.p.setItemInHand(InteractionHand.MAIN_HAND,enchanted);
        h.assertFalse(BeamEtchingMining.inspect(f.p,dirt,cap).eligible(),"enchantment cannot change reviewed loot");
        f.p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        boolean capture=h.getLevel().captureBlockSnapshots,restoring=h.getLevel().restoringBlockSnapshots;
        try{h.getLevel().captureBlockSnapshots=true;h.assertFalse(BeamEtchingMining.inspect(f.p,dirt,cap).eligible(),"deferred snapshot scope refused");h.getLevel().captureBlockSnapshots=false;h.getLevel().restoringBlockSnapshots=true;h.assertFalse(BeamEtchingMining.inspect(f.p,dirt,cap).eligible(),"restoring snapshot scope refused");}
        finally{h.getLevel().captureBlockSnapshots=capture;h.getLevel().restoringBlockSnapshots=restoring;}
        AbilityStorage.get(f.p).setCooldown(HighPowerSkills.BEAM_ETCHING,1);
        h.assertTrue(HighPowerResources.reserve(f.p,HighPowerSkills.BEAM_ETCHING,1)==null,"one-tick guard cannot be borrowed without an authenticated executing queue");
        AbilityStorage.get(f.p).cooldowns.remove(HighPowerSkills.BEAM_ETCHING);
        for(GameType mode:List.of(GameType.CREATIVE,GameType.ADVENTURE,GameType.SPECTATOR)){f.p.setGameMode(mode);h.assertFalse(BeamEtchingMining.inspect(f.p,dirt,cap).eligible(),"non-survival refused");}
        h.assertTrue(VanillaMiningContract.runtimeReason(Map.of("minecraft","1.21.1","neoforge","21.1.252","academy","0.1.0-m41-highpower.0.37-prototype"),Set.of("vanilla","file/custom.zip")).isPresent(),"custom data packs never guessed");
        h.assertTrue(VanillaMiningContract.runtimeReason(Map.of("minecraft","1.21.1","neoforge","21.1.251","academy","0.1.0-m41-highpower.0.37-prototype"),Set.of("vanilla")).isPresent(),"other native build refused");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_native_true_is_not_proof_of_removal_and_breaking_tool_is_native(GameTestHelper h){
        Fixture f=new Fixture(h);var target=f.target(Blocks.DIRT,0);ItemStack tool=new ItemStack(Items.WOODEN_SHOVEL);tool.setDamageValue(tool.getMaxDamage()-1);f.p.setItemInHand(InteractionHand.MAIN_HAND,tool);
        double cp=f.cp();h.assertTrue(BeamEtchingMining.attempt(f.p,target,1,()->true,BeamEtchingMining.newCapacity()).removed(),"native final durability use accepted");
        h.assertTrue(f.p.getMainHandItem().isEmpty(),"native tool destruction preserved without recreation");eq(h,f.cp(),cp-1,"observed removal settled once");
        h.assertTrue(f.p.gameMode.destroyBlock(target.pos()),"native destroyBlock can return true for already-air/no-removal");
        var air=new BeamEtchingMining.Target(target.pos(),h.getLevel().getBlockState(target.pos()),target.surface(),target.face());
        h.assertFalse(BeamEtchingMining.attempt(f.p,air,1,()->true,BeamEtchingMining.newCapacity()).removed(),"no CP debit from native true alone");
        eq(h,f.cp(),cp-1,"no second removal charge");h.assertValueEqual(f.drops(target.pos(),new ItemStack(Items.DIRT)),1,"no duplicate loot after native true/no-removal");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_canonical_resource_bytes_reject_datapack_copy_and_same_name_override(GameTestHelper h) throws java.io.IOException {
        Fixture f=new Fixture(h);
        for(Block block:List.of(Blocks.DIRT,Blocks.COARSE_DIRT,Blocks.ROOTED_DIRT,Blocks.MUD,Blocks.GRASS_BLOCK,Blocks.PODZOL,Blocks.MYCELIUM)){
            var state=block.defaultBlockState();String path=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
            var resource=f.p.server.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.withDefaultNamespace("loot_table/blocks/"+path+".json")).orElseThrow();
            byte[] bytes;try(var input=resource.open()){bytes=input.readAllBytes();}
            h.assertTrue(VanillaMiningContract.canonicalLootEvidence(state,resource.sourcePackId(),resource.knownPackInfo(),bytes),"actual official resource admitted "+path);
            h.assertFalse(VanillaMiningContract.canonicalLootEvidence(state,"file/copied-vanilla.zip",resource.knownPackInfo(),bytes),"identical copied bytes are not official pack provenance");
            h.assertFalse(VanillaMiningContract.canonicalLootEvidence(state,"vanilla",Optional.empty(),bytes),"claimed vanilla id alone is insufficient");
            byte[] tampered=bytes.clone();tampered[tampered.length/2]^=1;
            h.assertFalse(VanillaMiningContract.canonicalLootEvidence(state,"vanilla",resource.knownPackInfo(),tampered),"modified same-name resource is refused "+path);
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_category_switch_legacy_spend_and_logout_release_only_the_claim(GameTestHelper h){
        for(int scenario=0;scenario<3;scenario++){
            Fixture f=new Fixture(h);var target=f.target(Blocks.DIRT,scenario);double before=f.cp();Hook hook=f.hook(new Hook(f.p));int which=scenario;
            hook.nativeBreak=e->{
                if(which==0){var ability=AbilityStorage.get(f.p);ability.changeCategoryClassic("electromaster");ability.setLevel(1);ability.learn(HighPowerSkills.FORKED_ARC);ability.activated=true;ability.overloadFine=true;
                    h.assertFalse(ElectromasterPrototype.cast(f.p),"legacy direct writer cannot consume a live mining claim after category switch");}
                else if(which==1)f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp",0);
                else f.p.server.getPlayerList().remove(f.p);
            };
            h.assertFalse(BeamEtchingMining.attempt(f.p,target,1,()->true,BeamEtchingMining.newCapacity()).removed(),"changed owner/ledger lifecycle refused");
            eq(h,f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp"),scenario==1?0:before,"only claim released, never restore/mint direct writer or logout ledger");
            h.assertFalse(HighPowerResources.hasReservation(f.p),"category/CP/logout cannot strand claim");h.assertTrue(h.getLevel().getBlockState(target.pos())==target.state(),"refused target remains native intact");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_neighbor_exception_after_committed_removal_settles_without_synthetic_loot(GameTestHelper h){
        Fixture f=new Fixture(h);var target=f.target(Blocks.DIRT,0);f.p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SHOVEL));
        double before=f.cp();var cap=BeamEtchingMining.newCapacity();var hook=f.hook(new NeighborFailure(f.p,target.pos()));
        var result=BeamEtchingMining.attempt(f.p,target,1,()->true,cap);
        h.assertTrue(hook.thrown,"actual native post-removal neighbor event was reached");
        h.assertTrue(result.removed()&&!result.reason().isEmpty(),"truthful partial-completion result stops caller queue");
        h.assertTrue(h.getLevel().getBlockState(target.pos()).isAir(),"committed native removal is never world-rolled-back");
        eq(h,f.cp(),before-1,"committed removal charged once despite missing native boolean return");
        h.assertValueEqual(f.p.getMainHandItem().getDamageValue(),1,"already-applied vanilla tool wear retained");
        h.assertValueEqual(cap.reservations(),1,"capacity conservatively holds committed cell");
        h.assertValueEqual(f.drops(target.pos(),new ItemStack(Items.DIRT)),0,"interrupted native loot is never fabricated");
        h.assertFalse(HighPowerResources.hasReservation(f.p),"exception cannot strand CP claim");h.succeed();
    }
    public static final class NeighborFailure {
        final ServerPlayer owner;final BlockPos pos;boolean thrown;
        NeighborFailure(ServerPlayer owner,BlockPos pos){this.owner=owner;this.pos=pos;}
        @SubscribeEvent public void neighbor(BlockEvent.NeighborNotifyEvent event){
            if(!thrown&&event.getLevel()==owner.level()&&event.getPos().equals(pos)&&event.getState().isAir()&&HighPowerResources.hasReservation(owner)){
                thrown=true;throw new IllegalStateException("deliberate native neighbor notification failure after cell removal");
            }
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void mining_settled_claim_releases_before_native_loot_callback_shared_spend(GameTestHelper h){
        Fixture f=new Fixture(h);var target=f.target(Blocks.DIRT,0);f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp",10);
        var cap=BeamEtchingMining.newCapacity();var hook=f.hook(new PostLootSpend(h,f.p,target,cap));
        var result=BeamEtchingMining.attempt(f.p,target,1,()->true,cap);
        h.assertTrue(result.removed()&&result.reason().isEmpty(),"ordinary native harvest completes with callback spend");
        h.assertTrue(hook.called,"actual native item-join callback reached");eq(h,f.cp(),6,"one native debit plus legitimate post-settlement shared spend");
        h.assertValueEqual(f.drops(target.pos(),new ItemStack(Items.DIRT)),1,"ordinary drop is still produced once");
        h.assertValueEqual(cap.reservations(),1,"one capacity commit");h.assertFalse(HighPowerResources.hasReservation(f.p),"settled reservation cannot linger");h.succeed();
    }
    public static final class PostLootSpend {
        final GameTestHelper h;final ServerPlayer owner;final BeamEtchingMining.Target target;final BeamEtchingMining.Capacity capacity;boolean called;
        PostLootSpend(GameTestHelper h,ServerPlayer owner,BeamEtchingMining.Target target,BeamEtchingMining.Capacity capacity){this.h=h;this.owner=owner;this.target=target;this.capacity=capacity;}
        @SubscribeEvent public void join(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){
            if(!called&&event.getLevel()==owner.level()&&event.getEntity() instanceof ItemEntity item&&item.getItem().is(Items.DIRT)
                    &&item.getBoundingBox().intersects(new AABB(target.pos()).inflate(1))){
                called=true;h.assertFalse(HighPowerResources.hasReservation(owner),"settled claim is already removed inside native loot callback");
                eq(h,HighPowerResources.availableCp(owner),9,"spendable balance is not double-reserved after debit");
                h.assertTrue(HighPowerResources.trySpend(owner,HighPowerSkills.DUAL_POINT,3,1),"post-removal legitimate shared spend uses remaining balance");
                h.assertFalse(BeamEtchingMining.attempt(owner,target,1,()->true,capacity).removed(),"private mining permit still prevents reentry despite released CP claim");
            }
        }
    }

    public static final class Hook {
        final ServerPlayer owner;int sources,breaks;Consumer<SkillBlockDestroyEvent> source=e->{};Consumer<BlockEvent.BreakEvent> nativeBreak=e->{};
        Hook(ServerPlayer owner){this.owner=owner;}
        @SubscribeEvent public void source(SkillBlockDestroyEvent event){if(event.player==owner&&event.skill.equals("meltdowner.beam_etching")){sources++;source.accept(event);}}
        @SubscribeEvent public void nativeBreak(BlockEvent.BreakEvent event){if(event.getPlayer()==owner){breaks++;nativeBreak.accept(event);}}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final List<Object> hooks=new ArrayList<>();final List<BlockPos> positions=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){
            this.h=h;var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"Etch"+UUID.randomUUID().toString().substring(0,9)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var network=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(network);NetworkRegistry.configureMockConnection(network);server.getPlayerList().placeNewPlayer(network,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;
            h.assertTrue(HighPowerCombat.enroll(p,1),"native player enrollment");var ability=AbilityStorage.get(p);ability.selectCategory("meltdowner");ability.setLevel(1);ability.learn(HighPowerSkills.DUAL_POINT);ability.learn(HighPowerSkills.BEAM_ETCHING);ability.activated=true;ability.overloadFine=true;ability.presets.edit(0,0,HighPowerSkills.BEAM_ETCHING,id->true);HighPowerResources.initializeBudget(p);
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        BeamEtchingMining.Target target(Block block,int index){BlockPos pos=h.absolutePos(new BlockPos(3+index*3,5,8));positions.add(pos);h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());face(pos);return new BeamEtchingMining.Target(pos,h.getLevel().getBlockState(pos),new Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ()),Direction.NORTH);}
        void face(BlockPos pos){p.setPos(pos.getX()+.5,pos.getY()+.5-p.getEyeHeight(),pos.getZ()-3);p.setYRot(0);p.setXRot(0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();}
        double cp(){return HighPowerResources.cp(p);}
        int drops(BlockPos pos,ItemStack item){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(.8)).stream().filter(e->ItemStack.isSameItemSameComponents(e.getItem(),item)).mapToInt(e->e.getItem().getCount()).sum();}
        <T>T hook(T hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);return hook;}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);for(BlockPos pos:positions){h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1)).forEach(ItemEntity::discard);h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());}BeamEtching.remove(p);AbilityStorage.remove(p);p.server.getPlayerList().remove(p);channel.finishAndReleaseAll();}
    }
}
