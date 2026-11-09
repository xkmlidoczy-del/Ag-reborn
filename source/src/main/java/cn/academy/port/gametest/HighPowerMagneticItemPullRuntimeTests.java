package cn.academy.port.gametest;

import cn.academy.port.highpower.core.MagneticItemPullRules;
import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real Q/control-Q events, injected native merge semantics, physics and one-use server selection. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerMagneticItemPullRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_magnetic_item_pull";
    private HighPowerMagneticItemPullRuntimeTests() {}
    private static void near(GameTestHelper h, double a, double b, String why) { h.assertTrue(Math.abs(a-b)<1e-7, why + ": " + a + " != " + b); }
    private static CompoundTag nativeTag(ItemEntity item) { return item.saveWithoutId(new CompoundTag()); }
    private static void merge(ItemEntity survivor, ItemEntity donor) {
        try {
            Method method = ItemEntity.class.getDeclaredMethod("merge", ItemEntity.class, ItemStack.class, ItemEntity.class, ItemStack.class);
            method.setAccessible(true); method.invoke(null, survivor, survivor.getItem(), donor, donor.getItem());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void tryMerge(ItemEntity receiver, ItemEntity other) {
        try { Method method=ItemEntity.class.getDeclaredMethod("tryToMerge",ItemEntity.class);method.setAccessible(true);method.invoke(receiver,other); }
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void item_pull_real_q_and_control_q_provenance_null_target_and_persistence(GameTestHelper h) {
        Fixture f = new Fixture(h); var inv = f.p.getInventory(); inv.setItem(inv.selected, new ItemStack(Items.IRON_INGOT, 9));
        h.assertTrue(f.p.drop(false), "genuine native Q enters Forge toss event");
        ItemEntity q = f.findDrop(1); h.assertTrue(q.getTarget() == null, "ordinary Q has no pickup owner target");
        h.assertTrue(MagneticItemProvenance.ownedBy(q,f.p.getUUID()), "server Q proof records owner and exact entity UUID");
        near(h, inv.getSelected().getCount(), 8, "Q removes exactly one native stack item");
        h.assertTrue(f.p.drop(true), "genuine native control-Q enters same event");
        ItemEntity whole = f.findDrop(8); h.assertTrue(MagneticItemProvenance.ownedBy(whole,f.p.getUUID()), "control-Q stack certified");
        h.assertTrue(inv.getSelected().isEmpty(), "control-Q leaves native selected slot empty");
        ItemEntity loaded = new ItemEntity(EntityType.ITEM,h.getLevel()); loaded.load(nativeTag(q));
        h.assertTrue(MagneticItemProvenance.ownedBy(loaded,f.p.getUUID()), "proof persists through full native entity NBT");
        loaded.setUUID(UUID.randomUUID()); h.assertFalse(MagneticItemProvenance.ownedBy(loaded,f.p.getUUID()), "copied NBT with new identity is not transferable");
        ItemEntity unproven = f.raw(2); unproven.setThrower(f.p); unproven.setTarget(f.p.getUUID());
        h.assertFalse(MagneticItemProvenance.ownedBy(unproven,f.p.getUUID()), "thrower and target cannot backfill unknown provenance");
        q.setPos(f.p.position().add(0,1,4)); q.setDeltaMovement(Vec3.ZERO);
        h.assertTrue(f.tap(1,q), "own Q with null pickup target is eligible"); h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void item_pull_merge_both_survivors_same_owner_native_delay_age_and_count(GameTestHelper h) {
        Fixture f = new Fixture(h);
        for (boolean reverse : new boolean[]{false,true}) {
            ItemEntity a=f.toss(reverse?3:7),b=f.toss(reverse?7:3); a.setPickUpDelay(11);b.setPickUpDelay(31);
            CompoundTag saved=nativeTag(a);saved.putShort("Age",(short)40);a.load(saved);saved=nativeTag(b);saved.putShort("Age",(short)12);b.load(saved);
            ItemEntity survivor=reverse?b:a,donor=reverse?a:b; UUID id=survivor.getUUID();
            tryMerge(a,b);
            h.assertTrue(!survivor.isRemoved()&&donor.isRemoved(),"native explicitly selected survivor retained, donor consumed");
            h.assertTrue(survivor.getUUID().equals(id)&&MagneticItemProvenance.ownedBy(survivor,f.p.getUUID()),"both survivor directions preserve exact original proof");
            near(h,survivor.getItem().getCount(),10,"native stack conservation");near(h,nativeTag(survivor).getShort("PickupDelay"),31,"native maximum pickup delay");near(h,survivor.getAge(),12,"native minimum age");
        }
        ItemEntity a=f.toss(60),b=f.toss(10);UUID donor=b.getUUID();merge(a,b);
        near(h,a.getItem().getCount(),64,"native partial destination cap");near(h,b.getItem().getCount(),6,"native untransferred leftover");
        h.assertTrue(!b.isRemoved()&&b.getUUID().equals(donor)&&MagneticItemProvenance.ownedBy(a,f.p.getUUID())&&MagneticItemProvenance.ownedBy(b,f.p.getUUID()),"same-owner partial leftover keeps its own entity proof");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void item_pull_mixed_unknown_and_malformed_merge_clear_both_without_changing_vanilla(GameTestHelper h) {
        Fixture f=new Fixture(h);
        for(int kind=0;kind<5;kind++)for(boolean reverse:new boolean[]{false,true}) {
            ItemEntity a=f.toss(60),b=f.toss(10);
            if(kind==0)MagneticItemProvenance.invalidate(b);
            if(kind==1)b.getPersistentData().getCompound(MagneticItemProvenance.KEY).putUUID("owner",UUID.randomUUID());
            if(kind==2)b.getPersistentData().getCompound(MagneticItemProvenance.KEY).putString("version","1");
            if(kind==3)b.getPersistentData().getCompound(MagneticItemProvenance.KEY).putUUID("entity",UUID.randomUUID());
            if(kind==4)b.getPersistentData().putString(MagneticItemProvenance.KEY,"malformed");
            ItemEntity survivor=reverse?b:a,donor=reverse?a:b; merge(survivor,donor);
            h.assertTrue(MagneticItemProvenance.owner(a)==null&&MagneticItemProvenance.owner(b)==null,"unknown/mixed/malformed clears both regardless of survivor");
            near(h,survivor.getItem().getCount(),64,"native cap unaffected by proof invalidation");near(h,donor.getItem().getCount(),6,"native partial count unaffected");
            ItemEntity reload=new ItemEntity(EntityType.ITEM,h.getLevel());reload.load(nativeTag(donor));h.assertTrue(MagneticItemProvenance.owner(reload)==null,"invalidated leftover stays unproven after reload");
        }h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void item_pull_authenticated_selection_replay_cancel_subset_and_one_cost(GameTestHelper h) {
        Fixture f=new Fixture(h);ItemEntity own=f.toss(2),unknown=f.raw(3);double cp=f.cp();
        h.assertFalse(MagneticItemPull.tap(f.p,f.wire(1),List.of(own.getUUID())),"candidate payload without server begin rejected");
        h.assertTrue(MagneticItemPull.begin(f.p,f.wire(1)),"bounded server selection begins");
        h.assertTrue(MagneticItemPull.abort(f.p,f.wire(1)),"cancel retires pending selection before candidate payload");
        h.assertFalse(MagneticItemPull.tap(f.p,f.wire(1),List.of(own.getUUID())),"delayed canceled selection cannot start");
        h.assertFalse(MagneticItemPull.begin(f.p,f.wire(1)),"replayed begin cannot resurrect selection");
        h.assertTrue(MagneticItemPull.begin(f.p,f.wire(2)),"fresh selection");
        h.assertTrue(MagneticItemPull.tap(f.p,f.wire(2),List.of(unknown.getUUID(),own.getUUID(),own.getUUID())),"only independent owned subset accepted, duplicate UUID deduped");
        near(h,MagneticItemPull.targetCount(f.p),1,"one native target despite repeated UUID");near(h,f.cp(),cp-8,"LV3 exact one activation CP debit");
        h.assertFalse(MagneticItemPull.tap(f.p,f.wire(2),List.of(own.getUUID())),"candidate replay cannot debit again");
        near(h,f.cp(),cp-8,"no replay cost");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void item_pull_refuses_foreign_target_container_unproven_and_far_without_payment(GameTestHelper h) {
        Fixture f=new Fixture(h);ItemEntity own=f.toss(2);double cp=f.cp();
        own.setTarget(UUID.randomUUID());h.assertFalse(f.tap(1,own),"a different native pickup target is refused");own.setTarget(null);
        own.getItem().set(DataComponents.CONTAINER,ItemContainerContents.EMPTY);h.assertFalse(f.tap(2,own),"container component on tagged native stack is refused");own.getItem().remove(DataComponents.CONTAINER);
        own.getPersistentData().putBoolean("academy:protected",true);h.assertFalse(f.tap(3,own),"protected item refused");own.getPersistentData().remove("academy:protected");
        own.getPersistentData().putBoolean("academy:immovable",true);h.assertFalse(f.tap(4,own),"immovable item refused");own.getPersistentData().remove("academy:immovable");
        own.setPos(f.p.position().add(0,1,MagneticItemPullRules.RANGE+.5));h.assertFalse(f.tap(5,own),"candidate beyond configured reach refused");own.setPos(f.p.position().add(0,1,4));
        MagneticItemProvenance.invalidate(own);own.setThrower(f.p);h.assertFalse(f.tap(6,own),"matching vanilla thrower does not repair proof");near(h,f.cp(),cp,"refusals spend no CP");
        h.assertTrue(MagneticItemPull.begin(f.p,f.wire(7)),"oversized payload fixture selection");h.assertFalse(MagneticItemPull.tap(f.p,f.wire(7),Collections.nCopies(17,own.getUUID())),"raw17candidate list rejected even if duplicates");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void item_pull_velocity_only_preserves_delay_inventory_stack_gravity_and_no_mastery(GameTestHelper h) {
        Fixture f=new Fixture(h);ItemEntity own=f.toss(12);own.setNoGravity(false);own.setPickUpDelay(80);own.fallDistance=6;int count=own.getItem().getCount();
        for(int i=0;i<f.p.getInventory().items.size();i++)f.p.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));
        double exp=AbilityStorage.get(f.p).exp(MagneticItemPull.ID);h.assertTrue(f.tap(1,own),"full inventory does not fake pickup or block physical pull");
        h.runAfterDelay(1,()->{
            Vec3 position=own.position(),old=own.getDeltaMovement();int delay=nativeTag(own).getShort("PickupDelay");float fall=own.fallDistance;boolean noPhysics=own.noPhysics;
            MagneticItemPull.tick(f.p);Vec3 next=own.getDeltaMovement();
            h.assertTrue(own.position().equals(position),"server controller never directly moves entity");
            h.assertTrue(next.subtract(old).length()<=.120000001&&next.length()<=.350000001,"only bounded additive impulse");
            MagneticItemPull.tick(f.p);h.assertTrue(own.getDeltaMovement().equals(next),"duplicate same-tick callback cannot add a second impulse");
            near(h,nativeTag(own).getShort("PickupDelay"),delay,"controller preserves exact native delay");near(h,own.getItem().getCount(),count,"controller preserves stack count");
            near(h,own.fallDistance,fall,"controller preserves fall state");h.assertTrue(own.noPhysics==noPhysics&&!own.isNoGravity(),"controller preserves gravity and collision flags");
            own.playerTouch(f.p);h.assertTrue(!own.isRemoved()&&own.getItem().getCount()==count,"real native touch cannot bypass delay/full inventory");
            near(h,AbilityStorage.get(f.p).exp(MagneticItemPull.ID),exp,"repeated self toss grants no mastery");MagneticItemPull.abort(f.p);h.assertTrue(own.getDeltaMovement().equals(next),"abort never brakes or removes item");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void item_pull_provenance_loss_and_merged_away_identity_retire_before_impulse(GameTestHelper h) {
        Fixture f=new Fixture(h);ItemEntity selected=f.toss(2),survivor=f.toss(4);survivor.setPos(f.p.position().add(1,1,5));
        h.assertTrue(f.tap(1,selected),"selected original identity");merge(survivor,selected);Vec3 inertia=survivor.getDeltaMovement();
        h.runAfterDelay(1,()->{
            survivor.setDeltaMovement(inertia);MagneticItemPull.tick(f.p);h.assertFalse(MagneticItemPull.active(f.p),"removed selected donor retires controller");h.assertTrue(survivor.getDeltaMovement().equals(inertia),"controller never retargets unselected surviving stack");
            f.resetCooldown();ItemEntity next=f.toss(2),unknown=f.raw(3);h.assertTrue(f.tap(2,next),"new selected stack");merge(next,unknown);
            h.runAfterDelay(1,()->{Vec3 before=next.getDeltaMovement();MagneticItemPull.tick(f.p);h.assertFalse(MagneticItemPull.active(f.p),"mixed merge invalidation retires before next impulse");h.assertTrue(next.getDeltaMovement().equals(before),"no impulse after proof invalidation");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void item_pull_wall_swept_geometry_and_unloaded_guards_never_load(GameTestHelper h) {
        Fixture f=new Fixture(h);ItemEntity own=f.toss(2);double cp=f.cp();h.setBlock(new BlockPos(5,3,6),Blocks.STONE);h.setBlock(new BlockPos(5,2,6),Blocks.STONE);
        h.assertFalse(f.tap(1,own),"full wall blocks eye-to-item and hand visibility");near(h,f.cp(),cp,"blocked launch is free");h.setBlock(new BlockPos(5,3,6),Blocks.AIR);h.setBlock(new BlockPos(5,2,6),Blocks.AIR);
        h.assertTrue(f.tap(2,own),"clear initial line accepted");h.setBlock(new BlockPos(5,3,6),Blocks.STONE);h.setBlock(new BlockPos(5,2,6),Blocks.STONE);
        h.runAfterDelay(1,()->{Vec3 before=own.getDeltaMovement();MagneticItemPull.tick(f.p);h.assertFalse(MagneticItemPull.active(f.p),"new obstruction retires per-target immediately");h.assertTrue(own.getDeltaMovement().equals(before),"wall loss never writes velocity");
            int far=1_600_000;h.assertFalse(h.getLevel().hasChunk(far>>4,far>>4),"native far chunk not loaded");
            try{Method guard=MagneticItemPull.class.getDeclaredMethod("loadedArea",net.minecraft.server.level.ServerLevel.class,AABB.class);guard.setAccessible(true);h.assertFalse((Boolean)guard.invoke(null,h.getLevel(),new AABB(far,f.start.y,far,far+1,f.start.y+1,far+1)),"production guard rejects unloaded geometry");}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
            h.assertFalse(h.getLevel().hasChunk(far>>4,far>>4),"refusal leaves far chunk unloaded");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void item_pull_native_candidate_codec_caps_before_uuid_allocation(GameTestHelper h) {
        Fixture f=new Fixture(h);UUID a=UUID.randomUUID();var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try { var packet=new MagneticItemPull.CandidateRequest(f.wire(1),List.of(a,a));MagneticItemPull.CandidateRequest.CODEC.encode(buffer,packet);var decoded=MagneticItemPull.CandidateRequest.CODEC.decode(buffer);h.assertTrue(decoded.candidates().equals(List.of(a))&&decoded.wire().equals(f.wire(1))&&buffer.readableBytes()==0,"native codec roundtrip keeps identity and deduped bound"); }
        finally {buffer.release();}
        for(int count:new int[]{-1,17,Integer.MAX_VALUE}) {var bad=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{bad.writeUtf(f.wire(2),64);bad.writeVarInt(count);boolean rejected=false;try{MagneticItemPull.CandidateRequest.CODEC.decode(bad);}catch(io.netty.handler.codec.DecoderException expected){rejected=true;}h.assertTrue(rejected,"invalid count rejected before reading or allocating UUID array");}finally{bad.release();}}
        h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=215)
    public static void item_pull_server_selection_expiry_and_preset_lifecycle(GameTestHelper h) {
        Fixture f=new Fixture(h);ItemEntity own=f.toss(2);h.assertTrue(MagneticItemPull.begin(f.p,f.wire(1)),"pending selection started");
        h.runAfterDelay(MagneticItemPull.SELECTION_TICKS+1,()->{h.assertFalse(MagneticItemPull.tap(f.p,f.wire(1),List.of(own.getUUID())),"stale candidate request expires by server clock");
            own.setPos(f.p.position().add(0,1,4));own.setDeltaMovement(Vec3.ZERO);h.assertTrue(MagneticItemPull.begin(f.p,f.wire(2)),"fresh pending input");AbilityStorage.get(f.p).presets.switchTo(1);
            h.assertFalse(MagneticItemPull.tap(f.p,f.wire(2),List.of(own.getUUID())),"preset identity change invalidates in-flight selection");AbilityStorage.get(f.p).presets.switchTo(0);
            h.assertTrue(f.tap(3,own),"fresh context after reselect");MagneticItemPull.logout(new PlayerEvent.PlayerLoggedOutEvent(f.p));h.assertFalse(MagneticItemPull.active(f.p),"logout cleanup owns context");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void item_pull_midcast_interference_category_and_epoch_loss_retire(GameTestHelper h) {
        Fixture f=new Fixture(h);ItemEntity item=f.toss(2);h.assertTrue(f.tap(1,item),"initial controller");Vec3 inertia=item.getDeltaMovement();
        AbilityStorage.get(f.p).interfering=true;MagneticItemPull.tick(f.p);h.assertFalse(MagneticItemPull.active(f.p),"new interference ends even same tick before next impulse");h.assertTrue(item.getDeltaMovement().equals(inertia),"interference preserves motion");
        AbilityStorage.get(f.p).interfering=false;f.resetCooldown();h.assertTrue(f.tap(2,item),"fresh controller after interference ends");
        LegacySingleKeyOwnerEpoch.forget(f.p);MagneticItemPull.tick(f.p);h.assertFalse(MagneticItemPull.active(f.p),"owner epoch loss retires original session");f.epoch();f.resetCooldown();
        h.assertTrue(f.tap(3,item),"new epoch controller");AbilityStorage.get(f.p).selectCategory("meltdowner");MagneticItemPull.tick(f.p);h.assertFalse(MagneticItemPull.active(f.p),"category change retires immediately");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void item_pull_two_players_genuine_toss_proofs_do_not_launder_through_merge(GameTestHelper h) {
        Fixture f=new Fixture(h),other=new Fixture(h);ItemEntity own=f.toss(3),foreign=other.toss(4);
        h.assertTrue(MagneticItemProvenance.ownedBy(foreign,other.p.getUUID()),"second real server player's native toss has its own evidence");
        h.assertFalse(f.tap(1,foreign),"foreign genuine toss is refused despite tag and visibility");
        merge(own,foreign);h.assertTrue(MagneticItemProvenance.owner(own)==null&&MagneticItemProvenance.owner(foreign)==null,"genuine different owners invalidate both proofs at native merge HEAD");
        near(h,own.getItem().getCount(),7,"mixed-owner native merge still transfers all seven items");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=155)
    public static void item_pull_finite_lifetime_full_inventory_and_actual_native_motion(GameTestHelper h) {
        Fixture f=new Fixture(h);ItemEntity own=f.toss(12);own.setPos(f.p.position().add(0,0,23.5));own.setNoGravity(false);Vec3 start=own.position();
        for(int i=0;i<f.p.getInventory().items.size();i++)f.p.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));
        ItemEntity near=f.p.drop(new ItemStack(Items.IRON_NUGGET,5),false);h.assertTrue(near!=null,"native near-ground toss");f.entities.add(near);near.setPos(f.p.position().add(0,0,4));near.setDeltaMovement(Vec3.ZERO);near.setNoGravity(false);near.setPickUpDelay(200);
        own.setPickUpDelay(200);h.assertTrue(f.tap(1,own,near),"finite near and far ground cast starts");
        h.runAfterDelay(MagneticItemPullRules.MAX_TICKS+1,()->{h.assertFalse(MagneticItemPull.active(f.p),"controller cannot outlive the configured duration");h.assertTrue(own.position().distanceTo(start)>22.5&&own.position().distanceTo(f.p.position())<1,"actual native ground drag and gravity return23.5m stack into real pickup area within the finite controller duration");h.assertTrue(!near.isRemoved()&&near.getItem().getCount()==5&&near.position().distanceTo(f.p.position())<1,"near ground stack also reaches pickup area with native pickup delay and count intact");System.out.println("NEW133_NATIVE_GROUND_TRAVEL="+own.position().distanceTo(start)+" remaining="+own.position().distanceTo(f.p.position())+" ticks="+(MagneticItemPullRules.MAX_TICKS+1));h.assertTrue(!own.isRemoved()&&own.getItem().getCount()==12,"full inventory finite cast conserves entire original stack");near(h,AbilityStorage.get(f.p).exp(MagneticItemPull.ID),0,"long pull never farms mastery");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=175)
    public static void item_pull_long_settled_native_gravity_floor_contact_still_returns(GameTestHelper h) {
        Fixture f=new Fixture(h);ItemEntity item=f.toss(5);item.setPos(f.p.position().add(0,0,8));item.setNoGravity(false);
        for(int i=0;i<f.p.getInventory().items.size();i++)f.p.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));
        class Settled implements Runnable {
            int samples;
            public void run() {
                h.assertTrue(item.onGround(),"native item has settled on its actual supporting floor after45ticks");
                Vec3 inherited=item.getDeltaMovement();
                if(inherited.y>-.075&&samples++<4){h.runAfterDelay(1,new Runnable(){public void run(){Settled.this.run();}});return;}
                h.assertTrue(inherited.y<=-.075&&inherited.y>=-.160001,"ordinary skipped movement ticks retain native downward gravity");
                Vec3 hand=f.p.getEyePosition().add(f.p.getLookAngle().scale(.25)).add(0,-.6,0);
                Vec3 toward=hand.subtract(item.getBoundingBox().getCenter());
                var proposed=cn.academy.port.highpower.core.MagneticItemPullRules.pull(
                        new cn.academy.port.highpower.core.MagneticItemPullRules.Vector(inherited.x,inherited.y,inherited.z),
                        new cn.academy.port.highpower.core.MagneticItemPullRules.Vector(toward.x,toward.y,toward.z));
                h.assertTrue(proposed!=null,"bounded additive controller accepts native settled inertia");
                Vec3 next=new Vec3(proposed.x(),proposed.y(),proposed.z()),start=item.position();
                h.assertFalse(h.getLevel().noCollision(item,item.getBoundingBox().expandTowards(next).deflate(1e-7)),"old raw sweep reproduces false floor obstruction");
                try {Method guard=MagneticItemPull.class.getDeclaredMethod("movementPermitted",ServerPlayer.class,ItemEntity.class,Vec3.class);guard.setAccessible(true);h.assertTrue((Boolean)guard.invoke(null,f.p,item,next),"native clipped-floor sweep admits safe settled assistance");}
                catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
                h.assertTrue(item.position().equals(start)&&item.getDeltaMovement().equals(inherited),"collision preflight is read-only and keeps original downward motion");
                h.assertTrue(f.tap(1,item),"naturally aged settled own stack starts");
                h.runAfterDelay(12,()->{h.assertTrue(item.position().distanceTo(start)>1,"settled stack actually travels with native physics after cast");h.assertTrue(!item.isNoGravity()&&!item.noPhysics&&item.getItem().getCount()==5,"gravity/collision/stack conservation remain native");});
                h.runAfterDelay(101,()->{h.assertFalse(MagneticItemPull.active(f.p),"settled pull remains finite");h.assertTrue(!item.isRemoved()&&item.getItem().getCount()==5&&item.position().distanceTo(f.p.position())<1,"settled stack returns to native pickup area despite full inventory");System.out.println("NEW133_SETTLED_GRAVITY="+inherited.y+" travel="+item.position().distanceTo(start)+" remaining="+item.position().distanceTo(f.p.position()));h.succeed();});
            }
        }
        h.runAfterDelay(45,new Settled());
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void item_pull_native_floor_clip_never_admits_wall_ceiling_or_embedded_box(GameTestHelper h) {
        Fixture f=new Fixture(h);ItemEntity item=f.toss(2);item.setPos(f.p.position().add(0,0,4));item.setNoGravity(false);
        try {Method guard=MagneticItemPull.class.getDeclaredMethod("movementPermitted",ServerPlayer.class,ItemEntity.class,Vec3.class);guard.setAccessible(true);
            Vec3 downward=new Vec3(0,-.12,-.12),inertia=new Vec3(.015,-.12,.025);item.setDeltaMovement(inertia);Vec3 position=item.position();
            h.assertTrue((Boolean)guard.invoke(null,f.p,item,downward),"ordinary floor clipping is permitted");
            h.setBlock(new BlockPos(5,2,7),Blocks.STONE);item.setPos(f.p.position().add(0,0,3.65));
            h.assertFalse((Boolean)guard.invoke(null,f.p,item,new Vec3(0,-.12,-.2)),"floor clipping does not bypass horizontal wall clipping");
            h.setBlock(new BlockPos(5,2,7),Blocks.AIR);item.setPos(f.p.position().add(0,.65,4));h.setBlock(new BlockPos(5,3,8),Blocks.STONE);
            h.assertFalse((Boolean)guard.invoke(null,f.p,item,new Vec3(0,.2,-.02)),"upward ceiling clipping remains refused");
            h.setBlock(new BlockPos(5,3,8),Blocks.AIR);item.setPos(position);h.setBlock(new BlockPos(5,2,8),Blocks.STONE);
            h.assertFalse((Boolean)guard.invoke(null,f.p,item,downward),"embedded native body remains refused");
            h.assertTrue(item.getDeltaMovement().equals(inertia),"all guard calls leave full velocity unchanged");h.succeed();
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final Vec3 start;final List<Entity> entities=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;ItemEntity seamProbe=new ItemEntity(EntityType.ITEM,h.getLevel());h.assertTrue(seamProbe instanceof NativeItemMergeAccess seam&&seam.academy$nativeItemMergeContractVersion()==1,"actual native transform exposes verified noncanceling merge contract");for(int x=3;x<=8;x++)for(int z=3;z<=30;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPItemProbe"),false);p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;
            start=h.absoluteVec(new Vec3(5.5,2,4.5));p.moveTo(start.x,start.y,start.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();
            h.assertTrue(HighPowerCombat.enroll(p,3),"real connected LV3 enrollment");var s=AbilityStorage.get(p);s.selectCategory("electromaster");s.setLevel(3);s.learn("forked_arc");s.learn("magnetic_bracer");s.learn(MagneticItemPull.ID);s.activated=true;s.overloadFine=true;
            h.assertTrue(s.presets.edit(0,0,MagneticItemPull.ID,id->id.equals(MagneticItemPull.ID)),"real learned preset");ElectromasterPrototype.initializeBudget(p);epoch();
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        boolean tap(long input,ItemEntity...items){return MagneticItemPull.begin(p,wire(input))&&MagneticItemPull.tap(p,wire(input),Arrays.stream(items).map(Entity::getUUID).toList());}
        ItemEntity toss(int count){ItemEntity item=p.drop(new ItemStack(Items.IRON_INGOT,count),false);h.assertTrue(item!=null,"native toss produced item");item.setPos(p.position().add(0,1,4));item.setDeltaMovement(Vec3.ZERO);item.setNoGravity(true);entities.add(item);return item;}
        ItemEntity raw(int count){ItemEntity item=new ItemEntity(h.getLevel(),start.x+1,start.y+1,start.z+4,new ItemStack(Items.IRON_INGOT,count),0,0,0);item.setNoGravity(true);h.getLevel().addFreshEntity(item);entities.add(item);return item;}
        ItemEntity findDrop(int count){var list=h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(2),item->item.getItem().getCount()==count&&MagneticItemProvenance.ownedBy(item,p.getUUID()));h.assertTrue(list.size()==1,"one exact genuine Q result");entities.add(list.getFirst());return list.getFirst();}
        double cp(){return p.getPersistentData().getCompound("academy:electromaster_prototype").getDouble("cp");}
        void resetCooldown(){var s=AbilityStorage.get(p);s.cooldowns.remove(MagneticItemPull.ID);s.cooldownMaxTicks.remove(MagneticItemPull.ID);p.getPersistentData().getCompound("academy:electromaster_prototype").remove("next_"+MagneticItemPull.ID);}
        void close(){if(closed)return;closed=true;MagneticItemPull.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}
