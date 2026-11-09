package cn.academy.port.gametest;

import cn.academy.port.highpower.core.BeamEtchingRules;
import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Authenticated native owner, frozen target queue, real client coordinator and saved cadence. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerBeamEtchingRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_beam_etching";
    private HighPowerBeamEtchingRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void beam_etching_hold_is_harmless_release_pays_only_native_removal(GameTestHelper h) {
        Fixture f=new Fixture(h); BlockPos dirt=f.block(0,1,6,Blocks.DIRT.defaultBlockState()); f.face(dirt);
        double cp=f.cp(), xp=f.xp(); h.assertTrue(f.start(1),"authenticated hold starts");
        h.assertTrue(BeamEtching.marks(f.p).size()==1&&h.getLevel().getBlockState(dirt).is(Blocks.DIRT),"holding only records one visible surface");
        near(h,f.cp(),cp,"harmless preview has no CP"); near(h,f.xp(),xp,"harmless preview has no mastery");
        h.assertTrue(f.release(1),"matching physical release commits frozen queue");
        h.assertTrue(h.getLevel().isEmptyBlock(dirt),"native block actually removed"); near(h,f.cp(),cp-1,"one exact LV1 CP debit");
        near(h,f.xp(),xp+.001,"one productive mastery award");
        h.assertFalse(BeamEtching.active(f.p),"completed queue has no residual controller");
        h.assertFalse(f.release(1)||f.start(1),"replayed release and press cannot repeat harvest");
        h.assertTrue(f.p.getPersistentData().getCompound(BeamEtching.TIMING_KEY).getLong("stopUntil")-f.p.getPersistentData().getCompound(BeamEtching.TIMING_KEY).getLong("observed")==10,"listed finish cooldown starts after success");
        h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void beam_etching_real_input_hold_release_and_paid_snapshot_keep_execution(GameTestHelper h) {
        Fixture f=new Fixture(h); List<BlockPos> targets=List.of(f.block(-3,1,7,Blocks.DIRT.defaultBlockState()),f.block(-1,1,7,Blocks.DIRT.defaultBlockState()),f.block(1,1,7,Blocks.DIRT.defaultBlockState()),f.block(3,1,7,Blocks.DIRT.defaultBlockState()));
        EtchingClientBinding client=new EtchingClientBinding(f); f.face(targets.getFirst()); client.tick(true);
        h.assertTrue(client.accepted&&BeamEtching.holding(f.p),"real default group enters authenticated holding state");
        for(int i=1;i<4;i++){int index=i;h.runAfterDelay(i,()->{f.face(targets.get(index));BeamEtching.tick(f.p);client.snapshot();client.tick(true);});}
        h.runAfterDelay(4,()->{
            h.assertTrue(client.aborts==0&&BeamEtching.marks(f.p).size()==4,"ordinary client polls keep all held marks");
            double before=f.cp(); client.tick(false);
            h.assertTrue(client.releases==1&&client.aborts==0&&!BeamEtching.holding(f.p)&&BeamEtching.active(f.p),"captured physical release freezes and starts execution");
            h.assertTrue(client.state.cooldowns.getOrDefault(BeamEtching.ID,0)==1,"working snapshot is one-tick payment guard");
            h.assertTrue(empty(h,targets)==2,"first actual server tick attempts only two selected blocks"); near(h,f.cp(),before-2,"two native successes charge exactly two CP");
            BeamEtching.tick(f.p);BeamEtching.tick(f.p);client.snapshot();client.tick(false);
            h.assertTrue(empty(h,targets)==2&&client.aborts==0,"duplicate server/client callbacks cannot accelerate or self-abort");
            h.runAfterDelay(1,()->{BeamEtching.tick(f.p);client.snapshot();client.tick(false);h.assertTrue(empty(h,targets)==4&&!BeamEtching.active(f.p),"following real tick finishes unchanged frozen targets");h.assertTrue(client.aborts==0,"completion cooldown cannot abort released input");near(h,f.xp(),.001,"four successes inside twenty ticks award once");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void beam_etching_empty_ore_liquid_and_out_of_range_are_free(GameTestHelper h) {
        for(int mode=0;mode<4;mode++) {
            Fixture f=new Fixture(h);
            BlockPos target=f.block(0,1,mode==3?(int)Math.ceil(BeamEtchingRules.MAX_RANGE)+2:6,Blocks.DIRT.defaultBlockState());
            if(mode==0)h.getLevel().setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());
            if(mode==1)f.block(0,1,3,Blocks.IRON_ORE.defaultBlockState());
            if(mode==2)f.block(0,1,3,Blocks.WATER.defaultBlockState());
            f.face(target);double cp=f.cp();h.assertTrue(f.start(1),"bounded empty preparation is accepted "+mode);
            h.assertTrue(BeamEtching.marks(f.p).isEmpty(),"no unsupported/hidden/out-of-range preview "+mode);
            h.assertTrue(f.release(1)&&!BeamEtching.active(f.p),"empty release cleans context "+mode);near(h,f.cp(),cp,"empty release free "+mode);near(h,f.xp(),0,"empty release grants no XP "+mode);f.close();
        }
        h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void beam_etching_changed_or_newly_blocked_target_stops_without_reacquisition(GameTestHelper h) {
        for(int mode=0;mode<3;mode++) {
            Fixture f=new Fixture(h);BlockPos target=f.block(0,1,6,Blocks.DIRT.defaultBlockState());BlockPos farther=f.block(0,1,8,Blocks.DIRT.defaultBlockState());f.face(target);
            h.assertTrue(f.start(1)&&BeamEtching.marks(f.p).size()==1,"admit visible dirt before mutation "+mode);double cp=f.cp();
            if(mode==0)h.getLevel().setBlockAndUpdate(target,Blocks.STONE.defaultBlockState());
            if(mode==1)f.block(0,1,3,Blocks.STONE.defaultBlockState());
            if(mode==2){f.p.setPos(f.p.position().add(0,0,-8));f.p.connection.resetPosition();}
            h.assertTrue(f.release(1),"release is consumed even when frozen target is now refused");
            h.assertFalse(BeamEtching.active(f.p),"changed geometry retires queue "+mode);
            h.assertTrue(!h.getLevel().isEmptyBlock(target)&&h.getLevel().getBlockState(farther).is(Blocks.DIRT),"neither changed slot nor hidden replacement is mined "+mode);
            near(h,f.cp(),cp,"changed target costs no CP "+mode);near(h,f.xp(),0,"changed target grants no XP "+mode);f.close();
        }
        h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void beam_etching_lifecycle_and_inventory_changes_cancel_harmless_hold(GameTestHelper h) {
        for(int mode=0;mode<7;mode++) {
            Fixture f=new Fixture(h);BlockPos target=f.block(0,1,6,Blocks.DIRT.defaultBlockState());f.face(target);h.assertTrue(f.start(1),"hold before lifecycle mutation "+mode);double cp=f.cp();
            switch(mode) {
                case 0->AbilityStorage.get(f.p).presets.edit(0,0,"",id->true);
                case 1->AbilityStorage.get(f.p).selectCategory("electromaster");
                case 2->AbilityStorage.get(f.p).interfering=true;
                case 3->f.p.containerMenu=ChestMenu.threeRows(17,f.p.getInventory());
                case 4->f.p.getInventory().setItem(8,new ItemStack(Items.DIRT));
                case 5->f.p.getInventory().selected=1;
                default->BeamEtching.logout(new PlayerEvent.PlayerLoggedOutEvent(f.p));
            }
            BeamEtching.tick(f.p);h.assertFalse(BeamEtching.active(f.p),"exact context retired "+mode);h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.DIRT),"lifecycle never mines "+mode);
            if(mode!=1)near(h,f.cp(),cp,"lifecycle preparation remains free "+mode);f.close();
        }
        h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void beam_etching_callback_cancel_reentry_and_attempt_fence_survive_abort(GameTestHelper h) {
        Fixture f=new Fixture(h);BlockPos target=f.block(0,1,6,Blocks.DIRT.defaultBlockState());f.face(target);boolean[] called={false},nested={true};
        f.hook(new Object(){@SubscribeEvent public void source(cn.academy.port.api.SkillBlockDestroyEvent e){if(e.player==f.p&&e.skill.equals("meltdowner.beam_etching")){called[0]=true;nested[0]=f.start(100);e.setCanceled(true);}}});
        h.assertTrue(f.start(1),"hold before native source veto");double cp=f.cp();h.assertTrue(f.release(1),"release reaches source callback");
        h.assertTrue(called[0]&&!nested[0],"native callback cannot replace active processing owner");
        h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.DIRT)&&!BeamEtching.active(f.p),"veto ends entire queue without block write");
        near(h,f.cp(),cp,"canceled attempt is never charged");near(h,f.xp(),0,"canceled attempt grants no mastery");
        var before=f.p.getPersistentData().getCompound(BeamEtching.TIMING_KEY).copy();BeamEtching.remove(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();
        h.assertFalse(f.start(2),"new owner epoch cannot remove free preview cadence");
        h.assertTrue(before.equals(f.p.getPersistentData().getCompound(BeamEtching.TIMING_KEY)),"abort and incarnation change preserve spent attempt slot");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void beam_etching_stale_release_cannot_cancel_new_hold(GameTestHelper h) {
        Fixture f=new Fixture(h);BlockPos target=f.block(0,1,6,Blocks.DIRT.defaultBlockState());f.face(target);h.assertTrue(f.start(1),"first hold");String old=f.wire(1);h.assertTrue(BeamEtching.abort(f.p,old),"matching abort");
        h.runAfterDelay(4,()->{h.assertTrue(f.start(2),"new hold after persisted preview cadence");h.assertFalse(BeamEtching.release(f.p,old)||BeamEtching.abort(f.p,old),"old exact input cannot affect new hold");h.assertTrue(BeamEtching.holding(f.p),"new context remains captured");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void beam_etching_unloaded_geometry_does_not_request_chunks(GameTestHelper h) {
        Fixture f=new Fixture(h);BlockPos absent=new BlockPos(12000007,100,12000007);
        h.assertFalse(h.getLevel().hasChunkAt(absent),"remote test cell begins unloaded");
        int loaded=h.getLevel().getChunkSource().getLoadedChunksCount();double cp=f.cp();
        // Moving a real player to the remote cell itself requests vanilla tracking chunks.
        // Exercise the actual bounded ray seam with a remote origin while its owner stays loaded.
        try {
            var trace=BeamEtching.class.getDeclaredMethod("firstVisible",ServerPlayer.class,Vec3.class,Vec3.class);trace.setAccessible(true);
            h.assertTrue(trace.invoke(null,f.p,Vec3.atCenterOf(absent).add(0,0,-6),Vec3.atCenterOf(absent))==null,"unloaded path refuses before native clip reads chunks");
        } catch(ReflectiveOperationException e){throw new AssertionError(e);}
        var target=new BeamEtchingMining.Target(absent,Blocks.DIRT.defaultBlockState(),Vec3.atCenterOf(absent),net.minecraft.core.Direction.NORTH);
        h.assertFalse(BeamEtching.visible(f.p,target),"unloaded remote target cannot be admitted from live owner");
        h.assertTrue(!h.getLevel().hasChunkAt(absent)&&loaded==h.getLevel().getChunkSource().getLoadedChunksCount(),"actual selection geometry never force-loads remote chunks; before="+loaded+" after="+h.getLevel().getChunkSource().getLoadedChunksCount()+" targetLoaded="+h.getLevel().hasChunkAt(absent));
        near(h,f.cp(),cp,"unloaded target has no charge");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_dimension",timeoutTicks=20)
    public static void beam_etching_real_world_transition_and_dead_owner_revoke_marks(GameTestHelper h) {
        Fixture f=new Fixture(h);BlockPos target=f.block(0,1,6,Blocks.DIRT.defaultBlockState());f.face(target);h.assertTrue(f.start(1),"world-owned hold");String old=f.wire(1);
        var other=f.p.server.getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(other!=null,"native destination exists");other.getChunk(f.p.blockPosition());
        f.p.teleportTo(other,f.p.getX(),f.p.getY(),f.p.getZ(),Set.of(),0,0);BeamEtching.tick(f.p);
        h.assertTrue(f.p.serverLevel()==other&&!BeamEtching.active(f.p),"real dimension move clears original context");
        h.assertFalse(BeamEtching.release(f.p,old),"old-world release cannot commit terrain");h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.DIRT),"old-world dirt intact");f.close();
        Fixture dead=new Fixture(h);BlockPos untouched=dead.block(0,1,6,Blocks.DIRT.defaultBlockState());dead.face(untouched);h.assertTrue(dead.start(1),"live hold before death");dead.p.setHealth(0);BeamEtching.tick(dead.p);
        h.assertFalse(BeamEtching.active(dead.p),"dead owner retires hold");h.assertTrue(h.getLevel().getBlockState(untouched).is(Blocks.DIRT),"death never acts as release");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void beam_etching_hold_deadline_ends_without_autofiring(GameTestHelper h) {
        Fixture f=new Fixture(h);BlockPos target=f.block(0,1,6,Blocks.DIRT.defaultBlockState());f.face(target);h.assertTrue(f.start(1),"hold at deadline origin");f.watch();
        h.runAfterDelay(99,()->h.assertTrue(BeamEtching.holding(f.p),"ninety-nine ticks remain harmless; "+f.debug()+" trace="+f.trace));
        h.runAfterDelay(100,()->{BeamEtching.tick(f.p);h.assertFalse(BeamEtching.active(f.p),"hundredth real tick retires hold");h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.DIRT),"timeout never acts as release");near(h,f.xp(),0,"idle timeout no XP");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void beam_etching_ordered_marks_cap_at_sixteen_without_interpolation(GameTestHelper h) {
        Fixture f=new Fixture(h);List<BlockPos> targets=new ArrayList<>();
        for(int row=0;row<4;row++)for(int column=0;column<4;column++)targets.add(f.block(-3+column*2,-2+row*2,7,Blocks.DIRT.defaultBlockState()));
        BlockPos extra=f.block(5,1,7,Blocks.DIRT.defaultBlockState());f.face(targets.getFirst());h.assertTrue(f.start(1),"first selected grid face");h.assertTrue(BeamEtching.marks(f.p).size()==1,"initial grid face admitted; "+f.debug());f.watch();
        for(int i=1;i<16;i++){int index=i;h.runAfterDelay(i,()->{f.face(targets.get(index));BeamEtching.tick(f.p);h.assertTrue(BeamEtching.marks(f.p).size()==index+1,"one new selected face on distinct real tick index="+index+" actual="+BeamEtching.marks(f.p).size()+" target="+targets.get(index)+" "+f.debug()+" trace="+f.trace);});}
        h.runAfterDelay(16,()->{f.face(extra);BeamEtching.tick(f.p);var marks=BeamEtching.marks(f.p);h.assertTrue(marks.size()==16,"seventeenth distinct target refused");for(int i=0;i<16;i++)h.assertTrue(marks.get(i).pos().equals(targets.get(i)),"ordered explicit surface, no interpolated block "+i);h.assertTrue(empty(h,targets)==0,"long mark sweep remains harmless");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void beam_etching_productive_xp_fence_survives_real_save_reload(GameTestHelper h) {
        Fixture f=new Fixture(h);BlockPos first=f.block(-2,1,7,Blocks.DIRT.defaultBlockState()),second=f.block(2,1,7,Blocks.DIRT.defaultBlockState());f.face(first);h.assertTrue(f.start(1)&&f.release(1),"first native productive cast");near(h,f.xp(),.001,"first award");
        AbilityStorage.save(f.p);var saved=new net.minecraft.nbt.CompoundTag();f.p.saveWithoutId(saved);BeamEtching.remove(f.p);AbilityStorage.remove(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);f.p.load(saved);f.place(0);f.epoch();
        h.assertFalse(f.start(1),"real reload retains success cooldown");
        h.runAfterDelay(12,()->{f.face(second);h.assertTrue(f.start(2),"fresh authenticated cast after cooldown");h.assertTrue(BeamEtching.marks(f.p).size()==1,"reload cast acquires its actual target; "+f.debug());h.assertTrue(f.release(2),"fresh authenticated release after cooldown");h.assertTrue(h.getLevel().isEmptyBlock(second),"second native removal succeeds; "+f.debug());near(h,f.xp(),.001,"saved productive twenty-tick fence denies second early award");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void beam_etching_callback_movement_cannot_rewrite_native_emission_origin(GameTestHelper h) {
        for(int mode=0;mode<2;mode++) {
            Fixture f=new Fixture(h);BlockPos target=f.block(0,1,6,Blocks.DIRT.defaultBlockState());f.face(target);Vec3 origin=f.p.getEyePosition();boolean[] moved={false};int selected=mode;
            f.hook(new Object(){
                @SubscribeEvent public void before(cn.academy.port.api.SkillBlockDestroyEvent e){if(selected==0&&e.player==f.p&&e.skill.equals("meltdowner.beam_etching")){moved[0]=true;f.p.setPos(f.p.position().add(.2,0,0));}}
                @SubscribeEvent public void after(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){if(selected==1&&!moved[0]&&e.getEntity() instanceof ItemEntity item&&item.position().distanceToSqr(Vec3.atCenterOf(target))<4){moved[0]=true;f.p.setPos(f.p.position().add(.2,0,0));}}
            });
            h.assertTrue(f.start(1),"mark before native pose callback "+mode);f.visuals();double cp=f.cp();h.assertTrue(f.release(1),"release reaches native callback "+mode);
            h.assertTrue(moved[0],"selected real native callback ran "+mode);var confirmed=f.visuals().stream().filter(v->v.phase()==BeamEtching.CONFIRMED).toList();
            if(mode==0){h.assertTrue(!h.getLevel().isEmptyBlock(target)&&confirmed.isEmpty(),"pre-removal movement revokes exact emission authority");near(h,f.cp(),cp,"refused moved emission remains free");}
            else{h.assertTrue(h.getLevel().isEmptyBlock(target)&&confirmed.size()==1,"post-removal movement retains one actual receipt");h.assertTrue(confirmed.getFirst().origin().equals(origin)&&!origin.equals(f.p.getEyePosition()),"confirmed packet preserves the real pre-removal eye origin");near(h,f.cp(),cp-1,"observed native removal is charged once");}
            h.assertFalse(BeamEtching.active(f.p),"callback movement ends remaining queue "+mode);f.close();
        }
        h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void beam_etching_malformed_saved_mastery_cannot_refill_award_allowance(GameTestHelper h) {
        for(int mode=0;mode<4;mode++) {
            Fixture f=new Fixture(h);BlockPos target=f.block(0,1,6,Blocks.DIRT.defaultBlockState());f.face(target);
            switch(mode){case 0->f.p.getPersistentData().putLong(BeamEtching.MASTERY_KEY,-1);case 1->f.p.getPersistentData().putLong(BeamEtching.MASTERY_KEY,Long.MAX_VALUE);case 2->f.p.getPersistentData().putDouble(BeamEtching.MASTERY_KEY,Double.NaN);default->f.p.getPersistentData().putString(BeamEtching.MASTERY_KEY,"unset");}
            var original=f.p.getPersistentData().get(BeamEtching.MASTERY_KEY).copy();h.assertTrue(f.start(1)&&f.release(1),"productive native cast with malformed saved award fence "+mode);
            h.assertTrue(h.getLevel().isEmptyBlock(target),"malformed mastery alone does not fabricate a mining failure "+mode);near(h,f.xp(),0,"invalid existing award stamp fails closed "+mode);
            h.assertTrue(original.equals(f.p.getPersistentData().get(BeamEtching.MASTERY_KEY)),"invalid saved stamp is not silently erased "+mode);f.close();
        }
        h.succeed();
    }

    private static int empty(GameTestHelper h,List<BlockPos> targets){int count=0;for(BlockPos pos:targets)if(h.getLevel().isEmptyBlock(pos))count++;return count;}
    private static void near(GameTestHelper h,double a,double b,String why){h.assertTrue(Math.abs(a-b)<1e-7,why+": "+a+" / "+b);}
    private static final class EtchingClientBinding implements cn.academy.port.client.ClassicClientInputBinding.Host {
        final Fixture f;final cn.academy.port.client.ClassicClientInputBinding binding;final cn.academy.port.client.BeamEtchingInputState inputs=new cn.academy.port.client.BeamEtchingInputState();
        cn.academy.port.core.AbilityProgress state;boolean held,accepted;long nextInput;int aborts,releases;
        EtchingClientBinding(Fixture f){this.f=f;snapshot();binding=new cn.academy.port.client.ClassicClientInputBinding(this);binding.runtime().updateDefaultGroup();}
        void snapshot(){state=AbilityStorage.decode(AbilityStorage.encode(AbilityStorage.get(f.p)));}
        void tick(boolean down){held=down;state.tickCooldowns();binding.tickStart();}
        public boolean available(){return true;}public boolean inGame(){return f.p.isAlive()&&!f.p.isRemoved()&&!f.p.isSpectator();}
        public boolean categoryActivated(){return state.isActivated();}public boolean canUseAbility(){return state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress();}
        public boolean terminalOpen(){return false;}public int actorId(){return f.p.getId();}public long ownerEpoch(){return f.epoch();}public boolean physicalDown(int key){return held;}
        public boolean registeredMapping(String skill){return cn.academy.port.preset.PresetSkills.registeredMapping(state,skill);}
        public boolean eligible(cn.academy.port.client.ClassicClientInputBinding.Node node){return inGame()&&state.canUse(node.skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,node.skill)&&cn.academy.port.SkillCatalog.enabled(state.category,node.skill);}
        public String mappedSkill(int slot){return state.presets.currentSkill(slot);}public int mappedKey(int slot){return 33;}public int slotCount(){return 1;}
        public void rawActivation(boolean desired){throw new AssertionError("No activation edge expected");}public void replaceOverrides(int[] keys){}
        public long down(cn.academy.port.client.ClassicClientInputBinding.Node node){var input=new HighPowerInput.Input(node.slot,++nextInput,node.actorId(),node.ownerEpoch());inputs.begin(input,node.key);accepted=cn.academy.port.AcademyGameplay.requestFromClient(f.p,new cn.academy.port.AcademyNetwork.Request("hp_beam_etching_start",input.wire()));snapshot();return input.input();}
        public void up(cn.academy.port.client.ClassicClientInputBinding.Node node,long nonce){releases++;var input=inputs.release(new HighPowerInput.Input(node.slot,nonce,node.actorId(),node.ownerEpoch()));if(input!=null)cn.academy.port.AcademyGameplay.requestFromClient(f.p,new cn.academy.port.AcademyNetwork.Request("hp_beam_etching_release",input.wire()));snapshot();}
        public void abort(cn.academy.port.client.ClassicClientInputBinding.Node node,long nonce){aborts++;var input=inputs.cancel(new HighPowerInput.Input(node.slot,nonce,node.actorId(),node.ownerEpoch()));if(input!=null)cn.academy.port.AcademyGameplay.requestFromClient(f.p,new cn.academy.port.AcademyNetwork.Request("hp_beam_etching_abort",input.wire()));snapshot();}
        public boolean cancelPending(cn.academy.port.client.ClassicClientInputBinding.Node node,long input){return false;}
        public void direction(cn.academy.port.client.ClassicClientInputBinding.ContextIdentity context,int direction,cn.academy.port.client.ClassicClientInputBinding.Edge edge,boolean silent){}
        public void localContextEnd(cn.academy.port.client.ClassicClientInputBinding.ContextIdentity context){}public void contextAbort(cn.academy.port.core.TargetedContextTermination.Request request){}
        public void modernCleanup(Collection<cn.academy.port.client.ClassicClientInputBinding.Node> defaults){}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final List<BlockPos> blocks=new ArrayList<>();final List<Object> hooks=new ArrayList<>();boolean closed;String trace="";
        Fixture(GameTestHelper h){
            this.h=h;var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"BE"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            // These tests require a stable prepared inventory. The independent tutorial tests
            // cover the ten-tick first-login gift, which would correctly cancel this held cast.
            var tutorial=cn.academy.port.tutorial.TutorialStorage.get(p);
            tutorial.restore(tutorial.conditionBits(),tutorial.activatedIds(),true,tutorial.firstOpened());
            cn.academy.port.tutorial.TutorialStorage.save(p);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;place(0);h.assertTrue(HighPowerCombat.enroll(p,1),"reviewed connected caster");
            var state=AbilityStorage.get(p);state.selectCategory("meltdowner");state.setLevel(1);state.learn(DualPoint.ID);state.learn(BeamEtching.ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,BeamEtching.ID,id->true),"bind learned NEW213");HighPowerResources.initializeBudget(p);epoch();
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
            h.onEachTick(()->{if(!closed)p.doTick();});
        }
        void place(int offset){Vec3 at=h.absoluteVec(new Vec3(12.5+offset,18,5.5));p.moveTo(at.x,at.y,at.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();}
        BlockPos block(int x,int y,int z,BlockState state){BlockPos pos=p.blockPosition().offset(x,y,z);h.getLevel().setBlockAndUpdate(pos,state);blocks.add(pos);return pos;}
        void face(BlockPos target){Vec3 delta=Vec3.atCenterOf(target).subtract(p.getEyePosition());p.setYRot((float)-Math.toDegrees(Math.atan2(delta.x,delta.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        boolean start(long input){return BeamEtching.start(p,wire(input));}boolean release(long input){return BeamEtching.release(p,wire(input));}double cp(){return HighPowerResources.cp(p);}double xp(){return AbilityStorage.get(p).exp(BeamEtching.ID);}
        List<BeamEtching.Visual> visuals(){List<BeamEtching.Visual> result=new ArrayList<>();channel.runPendingTasks();channel.flushOutbound();channel.runPendingTasks();Object packet;while((packet=channel.readOutbound())!=null){if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof BeamEtching.Visual visual)result.add(visual);io.netty.util.ReferenceCountUtil.release(packet);}return result;}
        String debug(){var s=AbilityStorage.get(p);return "serverTick="+p.server.getTickCount()+" active="+BeamEtching.active(p)+" eye="+p.getEyePosition()+" look="+p.getLookAngle()+" pose="+p.getPose()+" learned="+s.learned(BeamEtching.ID)+" activated="+s.activated+" fine="+s.overloadFine+" interfering="+s.interfering+" inventory="+p.getInventory().items+" timing="+p.getPersistentData().getCompound(BeamEtching.TIMING_KEY);}
        void watch(){trace=debug();h.onEachTick(()->{if(!closed&&BeamEtching.active(p))trace=debug();});}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);BeamEtching.remove(p);for(BlockPos pos:blocks){h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).forEach(ItemEntity::discard);}p.containerMenu=p.inventoryMenu;AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);channel.finishAndReleaseAll();}
    }
}
