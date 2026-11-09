package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.SkillTuning;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.canon.CanonTargetPolicy;
import cn.academy.port.story.UrbanCalibrator;
import cn.academy.port.canon.CanonSkillsCombatB;
import cn.academy.port.canon.CanonLiquidEntities;
import cn.academy.port.canon.CanonPaperContent;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Connected-player server tests; neither a live client nor a real player is simulated. */
@GameTestHolder("academy_canon_media")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class CanonMediumRuntimeTests {
    private CanonMediumRuntimeTests() {}
    @SubscribeEvent public static void template(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag=new CompoundTag(); var size=new ListTag(); var position=new ListTag();
        for (int value:new int[]{12,12,12}) size.add(IntTag.valueOf(value));
        for (int i=0;i<3;i++) position.add(IntTag.valueOf(0));
        var air=new CompoundTag(); air.putString("Name","minecraft:air");
        var cell=new CompoundTag(); cell.put("pos",position); cell.putInt("state",0);
        var blocks=new ListTag(); blocks.add(cell); var palette=new ListTag(); palette.add(air);
        tag.put("size",size); tag.put("blocks",blocks); tag.put("palette",palette); tag.put("entities",new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_canon_media","empty"))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void water_push_requires_real_source_and_leaves_it_intact(GameTestHelper h) {
        var f=new Fixture(h,"hydro_hand","hydro_whip");
        var target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,7.5));
        try {
            h.assertTrue(HighPowerCombat.enroll(target,5),"target has actual exact health ledger");
            BigDecimal health=new BigDecimal(HighPowerCombat.healthText(target));
            double before=HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"hydro_whip"),"no water refuses cast");
            h.assertTrue(before==HighPowerResources.cp(f.player),"failure leaves CP untouched");
            h.setBlock(new BlockPos(3,3,2),Blocks.WATER);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"hydro_whip"),"actual water source admits jet");
            h.assertTrue(target.getDeltaMovement().z>.5,"server-side physical push applied");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).compareTo(health)<0,"exact damage applied to enrolled target");
            h.assertBlockPresent(Blocks.WATER,new BlockPos(3,3,2));
            h.assertTrue(HighPowerResources.cp(f.player)<before,"accepted jet pays actual CP");
            target.discard(); f.close(); h.succeed();
        } catch(Throwable failure) { target.discard(); f.close(); throw failure; }
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void hydro_current_requires_water_and_applies_real_motion(GameTestHelper h) {
        var f=new Fixture(h,"hydro_hand","hydro_current");
        try {
            h.assertTrue(f.acknowledgeTeleports()>0,"client acknowledges login teleport");
            Vec3 at=h.absoluteVec(new Vec3(4.5,3,2.5));f.player.moveTo(at.x,at.y,at.z,0,0);f.player.setYHeadRot(0);
            double cp=HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"hydro_current"),"dry player cannot generate water movement");
            h.assertTrue(HighPowerResources.cp(f.player)==cp,"dry refusal costs nothing");
            h.setBlock(new BlockPos(4,3,2),Blocks.WATER);h.setBlock(new BlockPos(4,4,2),Blocks.WATER);
            f.player.connection.tick();f.player.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(f.player.isInWaterOrBubble(),"native fluid state marks player immersed");
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"hydro_current"),"immersed player directs water current");
            h.assertTrue(f.player.getDeltaMovement().z>.4,"server applies actual forward velocity");
            h.assertTrue(HighPowerResources.cp(f.player)<cp,"accepted motion consumes CP");
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void hydro_rescue_requires_underwater_player_sneak_consent(GameTestHelper h) {
        var f=new Fixture(h,"hydro_hand","hydro_rescue");var other=new Fixture(h,"hydro_hand","hydro_rescue");
        try {
            h.assertTrue(f.acknowledgeTeleports()>0&&other.acknowledgeTeleports()>0,"real login teleports acknowledged");
            Vec3 a=h.absoluteVec(new Vec3(4.5,3,2.5)),b=h.absoluteVec(new Vec3(4.5,3,5.5));
            f.player.moveTo(a.x,a.y,a.z,0,6);f.player.setYHeadRot(0);other.player.moveTo(b.x,b.y,b.z,0,0);
            h.setBlock(new BlockPos(4,3,5),Blocks.WATER);h.setBlock(new BlockPos(4,4,5),Blocks.WATER);
            other.player.connection.tick();other.player.setDeltaMovement(0,-.2,0);
            h.assertTrue(other.player.isInWaterOrBubble(),"recipient actually immersed");double cp=HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"hydro_rescue"),"player without sneak consent is not moved");
            h.assertTrue(HighPowerResources.cp(f.player)==cp,"no-consent refusal costs nothing");
            other.player.setShiftKeyDown(true);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"hydro_rescue"),"consenting immersed recipient rescued");
            h.assertTrue(other.player.getDeltaMovement().y>=.22,"recipient receives real upward movement");
            f.close();other.close();h.succeed();
        } catch(Throwable failure) {f.close();other.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void hydro_irrigate_moistens_real_farmland_with_existing_water(GameTestHelper h) {
        var f=new Fixture(h,"hydro_hand","hydro_irrigate");
        try {
            h.getLevel().setBlock(h.absolutePos(new BlockPos(4,4,6)),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,0),3);
            double cp=HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"hydro_irrigate"),"no water source refuses hydration");
            h.assertTrue(HighPowerResources.cp(f.player)==cp,"missing source has no resource cost");
            h.setBlock(new BlockPos(3,3,2),Blocks.WATER);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"hydro_irrigate"),"real water moistens real farmland");
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(4,4,6))).getValue(FarmBlock.MOISTURE)==7,"farmland becomes wet");
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(3,3,2))).isSource(),"existing source remains intact");
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void hydro_extinguish_removes_real_fire_without_creating_water(GameTestHelper h) {
        var f=new Fixture(h,"hydro_hand","hydro_extinguish");
        try {
            h.setBlock(new BlockPos(3,3,2),Blocks.WATER);h.setBlock(new BlockPos(4,3,6),Blocks.STONE);
            h.setBlock(new BlockPos(4,4,7),Blocks.STONE);h.setBlock(new BlockPos(4,4,6),Blocks.FIRE);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"hydro_extinguish"),"water extinguishes actual fire");
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(4,4,6))).isAir(),"fire was removed");
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(3,3,2))).isSource(),"original water source retained");
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void hydro_transfer_moves_one_real_water_source(GameTestHelper h) {
        var f=new Fixture(h,"hydro_hand","hydro_transfer");
        try {
            h.setBlock(new BlockPos(4,4,5),Blocks.WATER);h.setBlock(new BlockPos(1,4,2),Blocks.STONE);
            double cp=HighPowerResources.cp(f.player);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"hydro_transfer"),"source selected by actual water ray");
            h.assertTrue(HighPowerResources.cp(f.player)==cp,"selection alone spends nothing");
            f.player.setYHeadRot(90);f.player.setYRot(90);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"hydro_transfer"),"actual source transferred to selected adjacent air");
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(4,4,5))).isAir(),"old source removed");
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(2,4,2))).isSource(),"exactly one source at destination");
            h.assertTrue(HighPowerResources.cp(f.player)<cp,"successful movement pays CP once");
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_hydro_boundary",timeoutTicks=100)
    public static void hydro_transfer_moves_existing_source_across_loaded_chunk_boundary(GameTestHelper h) {
        var f=new Fixture(h,"hydro_hand","hydro_transfer");
        var snapshots=new java.util.ArrayList<net.neoforged.neoforge.common.util.BlockSnapshot>();
        try {
            int base=Math.floorDiv(f.player.blockPosition().getX(),16)*16;
            double z=f.player.getZ();double y=f.player.getY();f.player.moveTo(base+15.5,y,z,0,0);f.player.setYHeadRot(0);
            int eyeY=net.minecraft.util.Mth.floor(f.player.getEyeY());int blockZ=net.minecraft.util.Mth.floor(z);
            var source=new BlockPos(base+15,eyeY,blockZ+3);var support=new BlockPos(base+18,eyeY,blockZ);
            var destination=new BlockPos(base+17,eyeY,blockZ);
            h.assertTrue((source.getX()>>4)!=(destination.getX()>>4),"positions really span two chunks");
            var level=h.getLevel();h.assertTrue(level.hasChunkAt(source)&&level.hasChunkAt(destination),"both chunks already loaded");
            // This deliberate cross-chunk setup extends outside the 12x12 template. Isolate the
            // batch and preserve every touched block so prior tests cannot intercept either ray.
            var corridor=java.util.List.of(f.player.blockPosition(),new BlockPos(base+15,eyeY,blockZ),new BlockPos(base+15,eyeY,blockZ+1),new BlockPos(base+15,eyeY,blockZ+2),source,
                    new BlockPos(base+16,eyeY,blockZ),destination,support);
            for(var pos:corridor)snapshots.add(net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(),level,pos));
            for(var pos:corridor)level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(source,Blocks.WATER.defaultBlockState(),3);level.setBlock(support,Blocks.STONE.defaultBlockState(),3);
            Vec3 eye=f.player.getEyePosition();var direction=cn.academy.port.skill.ClassicRaytrace.direction(f.player);
            var first=level.clip(new net.minecraft.world.level.ClipContext(eye,eye.add(direction.scale(6)),
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY,f.player));
            h.assertTrue(first.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&first.getBlockPos().equals(source),
                    "selection ray hits the intended real source; eye="+eye+", direction="+direction+", actual="+first.getType()+" "+first.getBlockPos()
                    +", actualState="+level.getBlockState(first.getBlockPos())+", originState="+level.getBlockState(BlockPos.containing(eye))
                    +", source="+source+", sourceFluid="+level.getFluidState(source));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"hydro_transfer"),"select real source");
            f.player.setYHeadRot(-90);f.player.setYRot(-90);
            direction=cn.academy.port.skill.ClassicRaytrace.direction(f.player);
            var second=level.clip(new net.minecraft.world.level.ClipContext(eye,eye.add(direction.scale(6)),
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,f.player));
            h.assertTrue(second.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&second.getBlockPos().equals(support)
                    &&second.getBlockPos().relative(second.getDirection()).equals(destination),"destination ray hits intended support face");
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"hydro_transfer"),"move existing source across boundary");
            h.assertTrue(level.getBlockState(source).isAir()&&level.getFluidState(destination).isSource(),
                    "one source moved, none copied; source="+level.getBlockState(source)+", destination="+level.getBlockState(destination));
            f.close();h.succeed();
        } finally {for(int i=snapshots.size()-1;i>=0;i--)snapshots.get(i).restore();f.close();}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void hydro_transfer_restores_source_when_place_event_denies(GameTestHelper h) {
        var f=new Fixture(h,"hydro_hand","hydro_transfer");var guard=new PaperPlacementGuard(f.player);
        try {
            h.setBlock(new BlockPos(4,4,5),Blocks.WATER);h.setBlock(new BlockPos(1,4,2),Blocks.STONE);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"hydro_transfer"),"real source selected");
            f.player.setYHeadRot(90);f.player.setYRot(90);NeoForge.EVENT_BUS.register(guard);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"hydro_transfer"),"native target protection denies transfer");
            h.assertTrue(guard.seen==1,"target protection event invoked");
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(4,4,5))).isSource(),"denied move restores source");
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(2,4,2))).isAir(),"denied move leaves no target water");
            h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(guard);f.close();}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_blade_needs_and_consumes_paper_and_applies_exact_damage(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_blade");
        var target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,7.5));
        try {
            h.assertTrue(HighPowerCombat.enroll(target,5),"target has actual exact health ledger");
            BigDecimal health=new BigDecimal(HighPowerCombat.healthText(target)); double cp=HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"paper_blade"),"no paper refuses cast");
            h.assertTrue(HighPowerResources.cp(f.player)==cp,"missing paper cannot consume CP");
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_blade"),"paper blade accepted");
            h.assertTrue(f.player.getMainHandItem().getCount()==1,"exactly one actual paper consumed");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).compareTo(health)<0,"exact damage applied to enrolled target");
            h.assertTrue(HighPowerResources.cp(f.player)<cp,"accepted blade spends CP");
            target.discard(); f.close(); h.succeed();
        } catch(Throwable failure) { target.discard(); f.close(); throw failure; }
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_array_spends_six_sheets_once_and_hits_multiple_targets(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_array");
        var a=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(3,3,7.5));
        var b=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(6,3,7.5));
        try {
            h.assertTrue(HighPowerCombat.enroll(a,5)&&HighPowerCombat.enroll(b,5),"exact target ledgers");
            BigDecimal hpA=new BigDecimal(HighPowerCombat.healthText(a)),hpB=new BigDecimal(HighPowerCombat.healthText(b));
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,6));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_array"),"bounded paper array fires");
            h.assertTrue(f.player.getMainHandItem().isEmpty(),"one six-sheet debit");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(a)).compareTo(hpA)<0
                    &&new BigDecimal(HighPowerCombat.healthText(b)).compareTo(hpB)<0,"both targets receive exact damage");
            a.discard();b.discard();f.close();h.succeed();
        } catch(Throwable failure) { a.discard();b.discard();f.close();throw failure; }
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_patch_is_a_solid_owned_block_and_supports_a_real_switch(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_patch");
        var anchor=new BlockPos(4,4,6);var relative=new BlockPos(4,4,5);var absolute=h.absolutePos(relative);
        try {
            h.setBlock(anchor,Blocks.STONE);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,1));
            double cp=HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"paper_patch"),"one sheet cannot create construction");
            h.assertTrue(cp==HighPowerResources.cp(f.player),"missing material costs nothing");
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_patch"),"two sheets create paper construction");
            h.assertTrue(f.player.getMainHandItem().isEmpty()&&HighPowerResources.cp(f.player)<cp,"actual paper and CP debited");
            h.assertTrue(CanonPaperContent.owned(f.player,absolute),"lease reports real owned block");
            h.assertFalse(h.getLevel().getBlockState(absolute).getCollisionShape(h.getLevel(),absolute).isEmpty(),"paper construction has real collision");
            h.getLevel().setBlock(absolute.above(),Blocks.STONE_PRESSURE_PLATE.defaultBlockState(),3);
            var zombie=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,5,5.5));
            h.runAtTickTime(8,()->{
                var plate=h.getLevel().getBlockState(absolute.above());
                h.assertTrue(plate.is(Blocks.STONE_PRESSURE_PLATE)&&plate.getValue(BlockStateProperties.POWERED),"real mob presses supported pressure plate");
                AbilityStorage.get(f.player).setActivateState(false);
                h.runAtTickTime(12,()->{
                    h.assertFalse(CanonPaperContent.owned(f.player,absolute),"deactivation revokes owned evidence");
                    h.assertTrue(h.getLevel().getBlockState(absolute).isAir(),"invalidated construction is removed");
                    zombie.discard();f.close();h.succeed();
                });
            });
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=120)
    public static void paper_construct_batch_is_all_or_none_and_honors_break_veto(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_patch");
        try {
            h.assertTrue(f.acknowledgeTeleports()>0,"client acknowledges login teleport");
            Vec3 origin=h.absoluteVec(new Vec3(4.5,3,2.5));f.player.moveTo(origin.x,origin.y,origin.z,0,0);f.player.setYHeadRot(0);
            for(int tick=1;tick<=35;tick++)h.runAtTickTime(tick,()->f.player.connection.tick());
            h.setBlock(new BlockPos(4,4,6),Blocks.STONE);h.setBlock(new BlockPos(5,4,6),Blocks.STONE);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_patch"),"first real construction");
            var first=h.absolutePos(new BlockPos(4,4,5));var second=h.absolutePos(new BlockPos(5,4,5));
            h.runAtTickTime(35,()->{
                Vec3 secondOrigin=h.absoluteVec(new Vec3(5.5,3,2.5));f.player.moveTo(secondOrigin.x,secondOrigin.y,secondOrigin.z,0,0);f.player.setYHeadRot(0);
                f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
                h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_patch"),"second real construction after cooldown");
                h.assertFalse(CanonPaperContent.consumeOwned(f.player,java.util.List.of(first,first)),"duplicate batch refused");
                h.assertFalse(CanonPaperContent.consumeOwned(f.player,java.util.List.of(first,h.absolutePos(new BlockPos(7,4,5)))),"missing member refuses entire batch");
                h.assertTrue(CanonPaperContent.owned(f.player,first)&&CanonPaperContent.owned(f.player,second),"failed preflight retains both blocks");
                var guard=new PaperBreakGuard(f.player);NeoForge.EVENT_BUS.register(guard);
                try {h.assertFalse(CanonPaperContent.consumeOwned(f.player,java.util.List.of(first,second)),"native protection veto refuses batch");}
                finally {NeoForge.EVENT_BUS.unregister(guard);}
                h.assertTrue(guard.seen>0&&CanonPaperContent.owned(f.player,first)&&CanonPaperContent.owned(f.player,second),"veto consumes neither owned block");
                h.assertTrue(CanonPaperContent.consumeOwned(f.player,java.util.List.of(first,second)),"valid batch consumes together");
                h.assertTrue(h.getLevel().getBlockState(first).isAir()&&h.getLevel().getBlockState(second).isAir(),"both physical blocks removed");
                f.close();h.succeed();
            });
        } catch(Throwable failure) {f.close();throw failure;}
    }
    public static final class PaperBreakGuard {
        final ServerPlayer owner;int seen;PaperBreakGuard(ServerPlayer owner){this.owner=owner;}
        @SubscribeEvent public void block(BlockEvent.BreakEvent event){if(event.getPlayer()==owner){seen++;event.setCanceled(true);}}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_plane_is_a_real_temporary_non_damaging_projectile(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_plane");
        try {
            for(int x=3;x<=5;x++)for(int z=4;z<=6;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            // Husk has the native zombie hitbox and does not ignite in daylight.
            var target=h.spawnWithNoFreeWill(EntityType.HUSK,new Vec3(4.5,3,5.5));target.setBaby(false);
            h.assertTrue(HighPowerCombat.enroll(target,5),"target exact ledger");BigDecimal health=new BigDecimal(HighPowerCombat.healthText(target));
            float nativeHealth=target.getHealth();int fire=target.getRemainingFireTicks();Vec3 initial=target.position();
            h.assertFalse(target.isOnFire(),"target starts without environmental fire");
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_plane"),"real paper plane launched");
            var planes=h.getLevel().getEntitiesOfClass(CanonPaperContent.PaperPlane.class,f.player.getBoundingBox().inflate(8));
            h.assertTrue(planes.size()==1&&planes.getFirst().getOwner()==f.player&&!planes.getFirst().shouldBeSaved(),"temporary projectile has actual owner and cannot persist");
            h.assertTrue(f.player.getMainHandItem().getCount()==1,"one actual sheet consumed");var plane=planes.getFirst();
            boolean[] touched={false};
            var impactProbe=new Object(){@SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
                public void impact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event){
                    if(event.getProjectile()==plane&&!event.isCanceled()&&event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity()==target)touched[0]=true;
                }};
            NeoForge.EVENT_BUS.register(impactProbe);
            h.testInfo.addListener(new GameTestListener(){
                public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){NeoForge.EVENT_BUS.unregister(impactProbe);}
                public void testFailed(GameTestInfo i,GameTestRunner r){NeoForge.EVENT_BUS.unregister(impactProbe);}
                public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){NeoForge.EVENT_BUS.unregister(impactProbe);}
            });
            h.runAtTickTime(12,()->{
                BigDecimal after=new BigDecimal(HighPowerCombat.healthText(target));
                String diagnostic="; exact="+health+" -> "+after+", native="+nativeHealth+" -> "+target.getHealth()
                        +", fire ticks="+fire+" -> "+target.getRemainingFireTicks()+", position="+initial+" -> "+target.position();
                h.assertTrue(plane.isRemoved()&&touched[0],"physical plane stops on actual target entity contact"+diagnostic);
                h.assertFalse(target.isOnFire(),"target remains free of environmental fire"+diagnostic);
                h.assertTrue(after.compareTo(health)==0,"plane does not invent damage"+diagnostic);
                target.discard();f.close();h.succeed();
            });
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_ladder_is_a_real_climbable_block(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_patch");
        try {
            h.setBlock(new BlockPos(4,4,6),Blocks.STONE);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_patch"),"real paper support built");
            var state=AbilityStorage.get(f.player);state.learn("paper_ladder");state.presets.edit(0,0,"paper_ladder",state::learned);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_ladder"),"paper ladder attached to owned support");
            var pos=h.absolutePos(new BlockPos(4,4,4));h.assertTrue(CanonPaperContent.owned(f.player,pos),"real ladder retains lease");
            f.player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            h.assertTrue(f.player.onClimbable(),"native player movement sees ladder as climbable");
            h.assertTrue(f.player.getMainHandItem().isEmpty(),"ladder consumed paper");f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_gate_is_a_real_player_operated_door(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_patch");
        try {
            h.setBlock(new BlockPos(4,4,6),Blocks.STONE);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_patch"),"real paper support built");
            var state=AbilityStorage.get(f.player);state.learn("paper_gate");state.presets.edit(0,0,"paper_gate",state::learned);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_gate"),"paper gate attached to owned support");
            var pos=h.absolutePos(new BlockPos(4,4,4));h.assertTrue(CanonPaperContent.owned(f.player,pos),"real gate retains lease");
            h.assertFalse(h.getLevel().getBlockState(pos).getValue(BlockStateProperties.OPEN),"gate starts closed");
            var hit=new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),net.minecraft.core.Direction.NORTH,pos,false);
            var result=f.player.gameMode.useItemOn(f.player,h.getLevel(),ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(result.consumesAction()&&h.getLevel().getBlockState(pos).getValue(BlockStateProperties.OPEN),"native player opens actual gate");
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_switch_builds_a_real_player_operated_redstone_button(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_patch");
        try {
            h.setBlock(new BlockPos(4,4,6),Blocks.STONE);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_patch"),"real paper support built");
            var state=AbilityStorage.get(f.player);state.learn("paper_switch");state.presets.edit(0,0,"paper_switch",state::learned);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_switch"),"button constructed on owned support");
            var pos=h.absolutePos(new BlockPos(4,4,4));
            h.assertTrue(CanonPaperContent.owned(f.player,pos)&&f.player.getMainHandItem().isEmpty(),"real button is leased and paid");
            var hit=new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),net.minecraft.core.Direction.NORTH,pos,false);
            var result=f.player.gameMode.useItemOn(f.player,h.getLevel(),ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(result.consumesAction()&&h.getLevel().getBlockState(pos).getValue(BlockStateProperties.POWERED),"native player interaction actually powers the button");
            h.assertTrue(h.getLevel().getSignal(pos,net.minecraft.core.Direction.UP)>0,"button emits real redstone signal");
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_plate_is_real_owned_redstone_trigger(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_patch");
        try {
            h.setBlock(new BlockPos(4,4,6),Blocks.STONE);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_patch"),"real paper support built");
            var state=AbilityStorage.get(f.player);state.learn("paper_plate");state.presets.edit(0,0,"paper_plate",state::learned);
            Vec3 at=h.absoluteVec(new Vec3(4.5,6,3.5));f.player.moveTo(at.x,at.y,at.z,0,60);f.player.setYHeadRot(0);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_plate"),"physical paper pressure plate built atop owned support");
            var plate=h.absolutePos(new BlockPos(4,5,5));
            h.assertTrue(CanonPaperContent.owned(f.player,plate),"trigger retains real owner lease");
            h.assertTrue(f.player.getMainHandItem().isEmpty(),"plate consumes real paper");
            var zombie=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,5,5.5));
            h.runAtTickTime(8,()->{
                var placed=h.getLevel().getBlockState(plate);
                h.assertTrue(placed.is(CanonPaperContent.PLATE.get())&&placed.getValue(BlockStateProperties.POWERED),"real mob powers the paper plate");
                h.assertTrue(h.getLevel().getSignal(plate,net.minecraft.core.Direction.UP)>0,"plate emits actual redstone signal");
                zombie.discard();f.close();h.succeed();
            });
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_walkway_builds_three_real_blocks_and_charges_six_sheets(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_walkway");
        try {
            AbilityStorage.get(f.player).learn("paper_patch");h.setBlock(new BlockPos(4,4,6),Blocks.STONE);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,6));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_walkway"),"six sheets create three block walkway");
            h.assertTrue(f.player.getMainHandItem().isEmpty(),"six real sheets consumed");
            for(int z=3;z<=5;z++) {
                var at=h.absolutePos(new BlockPos(4,4,z));
                h.assertTrue(CanonPaperContent.owned(f.player,at),"each segment is an actual owned block");
                h.assertFalse(h.getLevel().getBlockState(at).getCollisionShape(h.getLevel(),at).isEmpty(),"each segment supports collision");
            }
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    private static void learnPaperPrerequisites(GameTestHelper h,ServerPlayer player,String skill) {
        var state=AbilityStorage.get(player);
        var entry=cn.academy.port.SkillCatalog.find("paper_psychokinesis",skill).orElseThrow();
        for(var requirement:cn.academy.port.SkillCatalog.requirements(entry)) {
            if(state.learned(requirement.id()))continue;
            learnPaperPrerequisites(h,player,requirement.id());
            var parent=cn.academy.port.SkillCatalog.find("paper_psychokinesis",requirement.id()).orElseThrow();
            h.assertTrue(cn.academy.port.SkillCatalog.canLearn(state,parent),"fixture learns each actual paper prerequisite in order");
            state.learn(requirement.id());
        }
    }
    private static void paperStructure(GameTestHelper h,String skill,int sheets,BlockPos... positions) {
        var f=new Fixture(h,"paper_psychokinesis",skill);
        try {
            learnPaperPrerequisites(h,f.player,skill);
            h.assertTrue(AbilityStorage.get(f.player).learned("paper_patch"),"paper construct lease requires the actual learned paper patch");
            h.setBlock(new BlockPos(4,4,6),Blocks.STONE);
            var eye=f.player.getEyePosition();var direction=cn.academy.port.skill.ClassicRaytrace.direction(f.player);
            var hit=h.getLevel().clip(new net.minecraft.world.level.ClipContext(eye,eye.add(direction.scale(SkillTuning.number(skill, "reach"))),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,f.player));
            h.assertTrue(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&hit.getBlockPos().equals(h.absolutePos(new BlockPos(4,4,6)))&&hit.getDirection()==net.minecraft.core.Direction.NORTH,"actual aiming ray hits the backing block");
            for(BlockPos relative:positions){var pos=h.absolutePos(relative);
                h.assertTrue(h.getLevel().hasChunkAt(pos)&&h.getLevel().getBlockState(pos).isAir()&&h.getLevel().getBlockEntity(pos)==null
                        &&h.getLevel().mayInteract(f.player,pos)&&f.player.distanceToSqr(pos.getCenter())<=Math.pow(SkillTuning.number(skill, "reach"),2)
                        &&h.getLevel().noCollision(null,new net.minecraft.world.phys.AABB(pos)),"each intended position is loaded, permitted, within range and physically clear");}
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,sheets));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,skill),"paper structure is built from real sheets");
            h.assertTrue(f.player.getMainHandItem().isEmpty(),"all required sheets are consumed");
            for(BlockPos relative:positions){var pos=h.absolutePos(relative);
                h.assertTrue(CanonPaperContent.owned(f.player,pos),"segment is a real leased construction");
                h.assertFalse(h.getLevel().getBlockState(pos).getCollisionShape(h.getLevel(),pos).isEmpty(),"segment is physically solid");}
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_puzzle_link_builds_a_real_triggered_support(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_puzzle_link");
        try {
            learnPaperPrerequisites(h,f.player,"paper_puzzle_link");
            h.assertTrue(AbilityStorage.get(f.player).learned("paper_patch"),"assembly fixture learned paper patch");
            h.setBlock(new BlockPos(4,4,6),Blocks.STONE);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,4));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_puzzle_link"),"support and plate built together");
            var support=h.absolutePos(new BlockPos(4,4,5));var plate=support.above();
            h.assertTrue(CanonPaperContent.owned(f.player,support)&&CanonPaperContent.owned(f.player,plate)&&f.player.getMainHandItem().isEmpty(),"both physical leased components consume four sheets");
            var target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,5,5.5));
            h.runAtTickTime(8,()->{h.assertTrue(h.getLevel().getBlockState(plate).getValue(BlockStateProperties.POWERED),"real creature pressure powers the plate");target.discard();f.close();h.succeed();});
        } catch(Throwable failure) {f.close();throw failure;}
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_switch_bridge_is_real_redstone_operated_gate(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_switch_bridge");
        try {
            learnPaperPrerequisites(h,f.player,"paper_switch_bridge");
            h.assertTrue(AbilityStorage.get(f.player).learned("paper_patch"),"assembly fixture learned paper patch");
            h.setBlock(new BlockPos(4,4,6),Blocks.STONE);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,6));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_switch_bridge"),"switch bridge built from six sheets");
            var support=h.absolutePos(new BlockPos(4,4,5));var button=support.above();var gate=support.east();
            h.assertTrue(CanonPaperContent.owned(f.player,support)&&CanonPaperContent.owned(f.player,button)&&CanonPaperContent.owned(f.player,gate),"three real leased components");
            h.assertFalse(h.getLevel().getBlockState(gate).getValue(BlockStateProperties.OPEN),"gate begins shut");
            var hit=new net.minecraft.world.phys.BlockHitResult(button.getCenter(),net.minecraft.core.Direction.UP,button,false);
            h.assertTrue(f.player.gameMode.useItemOn(f.player,h.getLevel(),ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,hit).consumesAction(),"native button interaction accepted");
            h.assertTrue(h.getLevel().getBlockState(button).getValue(BlockStateProperties.POWERED)&&h.getLevel().getBlockState(gate).getValue(BlockStateProperties.OPEN),"real redstone signal opens the adjacent gate");
            h.assertTrue(f.player.getMainHandItem().isEmpty(),"all six sheets consumed");f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_ramp_builds_four_solid_rising_steps(GameTestHelper h) {
        paperStructure(h,"paper_ramp",8,new BlockPos(4,4,5),new BlockPos(4,5,4),new BlockPos(4,6,3),new BlockPos(4,7,2));
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_step_builds_three_solid_steps(GameTestHelper h) {
        paperStructure(h,"paper_step",6,new BlockPos(4,4,3),new BlockPos(4,5,4),new BlockPos(4,6,5));
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_safety_rail_builds_three_solid_blocks(GameTestHelper h) {
        paperStructure(h,"paper_safety_rail",6,new BlockPos(4,4,5),new BlockPos(5,4,5),new BlockPos(6,4,5));
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_retract_removes_owned_block_without_refund(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_patch");
        try {
            h.setBlock(new BlockPos(4,4,6),Blocks.STONE);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_patch"),"physical block built");
            var pos=h.absolutePos(new BlockPos(4,4,5));h.assertTrue(CanonPaperContent.owned(f.player,pos),"block is owned");
            var state=AbilityStorage.get(f.player);state.learn("paper_retract");state.presets.edit(0,0,"paper_retract",state::learned);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_retract"),"owner retracts targeted construction");
            h.assertTrue(h.getLevel().getBlockState(pos).isAir()&&f.player.getMainHandItem().isEmpty(),"block removed and no paper duplicated");f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_lift_bridge_builds_solid_raised_path(GameTestHelper h) {
        paperStructure(h,"paper_lift_bridge",8,new BlockPos(4,4,5),new BlockPos(4,4,4),new BlockPos(4,5,3),new BlockPos(4,5,2));
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_cover_builds_real_wide_screen(GameTestHelper h) {
        paperStructure(h,"paper_cover",12,new BlockPos(3,4,5),new BlockPos(4,4,5),new BlockPos(5,4,5),new BlockPos(3,5,5),new BlockPos(4,5,5),new BlockPos(5,5,5));
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_platform_builds_nine_solid_blocks(GameTestHelper h) {
        paperStructure(h,"paper_platform",18,new BlockPos(3,4,3),new BlockPos(4,4,3),new BlockPos(5,4,3),new BlockPos(3,4,4),new BlockPos(4,4,4),new BlockPos(5,4,4),new BlockPos(3,4,5),new BlockPos(4,4,5),new BlockPos(5,4,5));
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_catwalk_builds_five_solid_blocks(GameTestHelper h) {
        paperStructure(h,"paper_catwalk",10,new BlockPos(2,4,5),new BlockPos(3,4,5),new BlockPos(4,4,5),new BlockPos(5,4,5),new BlockPos(6,4,5));
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_emergency_stairs_descend_on_real_blocks(GameTestHelper h) {
        paperStructure(h,"paper_emergency_stairs",8,new BlockPos(4,4,5),new BlockPos(4,3,4),new BlockPos(4,2,3),new BlockPos(4,1,2));
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_screen_is_a_real_three_block_visual_obstruction(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_screen");
        try {
            AbilityStorage.get(f.player).learn("paper_patch");h.setBlock(new BlockPos(4,4,6),Blocks.STONE);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,6));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_screen"),"six sheets produce vertical screen");
            for(int y=4;y<=6;y++)h.assertTrue(CanonPaperContent.owned(f.player,h.absolutePos(new BlockPos(4,y,5))),"screen segment is an actual owned block");
            var ray=h.getLevel().clip(new net.minecraft.world.level.ClipContext(f.player.getEyePosition(),
                    f.player.getEyePosition().add(0,0,5),net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,f.player));
            h.assertTrue(ray.getBlockPos().equals(h.absolutePos(new BlockPos(4,4,5))),"screen physically occludes the view before the backing wall");
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_pad_forms_a_real_four_block_platform(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_pad");
        try {
            AbilityStorage.get(f.player).learn("paper_patch");h.setBlock(new BlockPos(4,4,6),Blocks.STONE);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,8));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_pad"),"eight sheets construct a 2x2 platform");
            for(int x=4;x<=5;x++)for(int z=4;z<=5;z++) {
                var pos=h.absolutePos(new BlockPos(x,4,z));
                h.assertTrue(CanonPaperContent.owned(f.player,pos),"platform has four actual owned blocks");
                h.assertFalse(h.getLevel().getBlockState(pos).getCollisionShape(h.getLevel(),pos).isEmpty(),"each platform block is solid");
            }
            h.assertTrue(f.player.getMainHandItem().isEmpty(),"all eight sheets consumed");f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_walkway_rolls_back_all_segments_after_second_place_veto(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_walkway");var guard=new SecondPaperPlaceGuard(f.player);
        try {
            AbilityStorage.get(f.player).learn("paper_patch");h.setBlock(new BlockPos(4,4,6),Blocks.STONE);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,6));
            NeoForge.EVENT_BUS.register(guard);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"paper_walkway"),"second protected position refuses walkway");
            h.assertTrue(guard.seen==2&&f.player.getMainHandItem().getCount()==6,"two events observed, no sheets consumed");
            for(int z=3;z<=5;z++)h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(4,4,z))).isAir(),"no residual bridge segment");
            h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(guard);f.close();}
    }
    public static final class SecondPaperPlaceGuard {
        final ServerPlayer owner;int seen;SecondPaperPlaceGuard(ServerPlayer owner){this.owner=owner;}
        @SubscribeEvent public void place(BlockEvent.EntityPlaceEvent e){if(e.getEntity()==owner&&++seen==2)e.setCanceled(true);}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_patch_honors_native_place_protection(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_patch");var relative=new BlockPos(4,4,5);
        var guard=new PaperPlacementGuard(f.player);
        try {
            h.setBlock(new BlockPos(4,4,6),Blocks.STONE);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            NeoForge.EVENT_BUS.register(guard);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"paper_patch"),"cancelled real place event denies construction");
            h.assertTrue(guard.seen==1,"native protection hook invoked");
            h.assertTrue(f.player.getMainHandItem().getCount()==2,"vetoed construction cannot consume material");
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(relative)).isAir(),"vetoed construction is rolled back");
            h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(guard);f.close();}
    }
    public static final class PaperPlacementGuard {
        final ServerPlayer owner;int seen;PaperPlacementGuard(ServerPlayer owner){this.owner=owner;}
        @SubscribeEvent public void place(BlockEvent.EntityPlaceEvent event){if(event.getEntity()==owner){seen++;event.setCanceled(true);}}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_wing_consumes_medium_glides_and_stops_on_sneak(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_wing");
        try {
            h.assertTrue(f.acknowledgeTeleports()>0,"login teleport acknowledged");
            Vec3 at=h.absoluteVec(new Vec3(4.5,6,2.5));f.player.moveTo(at.x,at.y,at.z,0,0);f.player.setYHeadRot(0);
            f.player.setOnGround(false);f.player.setDeltaMovement(0,-.8,0);f.player.fallDistance=6;
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,7));
            double cp=HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"paper_wing"),"seven sheets cannot deploy wings");
            h.assertTrue(HighPowerResources.cp(f.player)==cp,"missing paper costs no CP");
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,8));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_wing"),"real paper wings start");
            h.assertTrue(f.player.getMainHandItem().isEmpty()&&HighPowerResources.cp(f.player)<cp,"eight sheets and shared CP consumed");
            h.assertFalse(f.player.getAbilities().mayfly,"glide does not grant unrestricted native flight");
            h.runAtTickTime(3,()->{
                Vec3 speed=f.player.getDeltaMovement();
                h.assertTrue(speed.y>=-.13&&speed.z>.1,"actual server velocity steers and slows descent");
                h.assertTrue(f.player.fallDistance==0,"active wings account for actual fall distance");
                Vec3 before=f.player.position(),destination=before.add(speed);
                f.player.connection.resetPosition();
                f.player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(destination.x,destination.y,destination.z,0,0,false));
                h.assertTrue(f.player.position().z>before.z+.1,"native movement packet accepts glide motion");
                f.player.setShiftKeyDown(true);f.player.setDeltaMovement(0,-.7,0);
                h.runAtTickTime(6,()->{
                    h.assertTrue(f.player.getDeltaMovement().y<-.2,"sneak ends flight control");
                    f.close();h.succeed();
                });
            });
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=240)
    public static void paper_wing_ceiling_and_duration_are_bounded(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_wing");
        try {
            Vec3 at=h.absoluteVec(new Vec3(4.5,6,2.5));f.player.moveTo(at.x,at.y,at.z,0,-45);f.player.setYHeadRot(0);
            f.player.setOnGround(false);f.player.setDeltaMovement(0,0,0);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,8));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_wing"),"paper wings start");
            f.player.setPos(at.add(0,SkillTuning.number("paper_wing", "rise_limit"),0));f.player.setDeltaMovement(0,.5,0);
            int expiry=SkillTuning.integer("paper_wing", "duration_ticks");
            h.runAtTickTime(3,()->h.assertTrue(f.player.getDeltaMovement().y<=0,"no lift at or above the fixed ceiling"));
            h.runAtTickTime(expiry+2,()->{
                f.player.setDeltaMovement(0,-.7,0);f.player.fallDistance=4;
                h.runAtTickTime(expiry+5,()->{
                    h.assertTrue(f.player.getDeltaMovement().y<-.2&&f.player.fallDistance>=4,"expired wings no longer control velocity or fall distance");
                    f.close();h.succeed();
                });
            });
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void water_domain_pushes_a_mob_actually_in_water(GameTestHelper h) {
        var f=new Fixture(h,"hydro_hand","hydro_domain");
        var target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,7.5));
        try {
            h.setBlock(new BlockPos(3,3,2),Blocks.WATER);h.setBlock(new BlockPos(4,3,7),Blocks.WATER);
            h.runAtTickTime(3,()->{
                h.assertTrue(target.isInWaterOrBubble(),"target is genuinely in water");
                h.assertTrue(CanonSkillsCombatB.cast(f.player,"hydro_domain"),"water current starts");
                h.runAtTickTime(6,()->{
                    h.assertTrue(target.getDeltaMovement().z>.05,"server water current moves target");
                    target.discard();f.close();h.succeed();
                });
            });
        } catch(Throwable failure) { target.discard();f.close();throw failure; }
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_body_uses_medium_moves_and_attacks_through_exact_ledger(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true); double cp=HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"no medium refuses body");
            h.assertTrue(HighPowerResources.cp(f.player)==cp,"missing medium costs no CP");
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real medium creates liquid body");
            h.assertTrue(f.player.getMainHandItem().isEmpty(),"one actual medium consumed");
            var bodies=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8));
            h.assertTrue(bodies.size()==1,"single real body present"); var body=bodies.getFirst();
            h.assertTrue(body.controlOwner().filter(f.player.getUUID()::equals).isPresent(),"server synchronizes only actual lease owner");
            h.assertFalse(body.hasObservationModule(),"liquid body has no intrinsic remote sight without optic");
            h.assertFalse(body.shouldBeSaved(),"temporary body cannot become a saved ghost");
            h.assertTrue(body.getEncodeId()==null && !body.save(new CompoundTag()),"entity type and native save both reject serialization");
            double start=body.getZ();
            var target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,8.5));
            var second=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(3,3,7.5));
            h.assertTrue(HighPowerCombat.enroll(target,5)&&HighPowerCombat.enroll(second,5),"targets have exact health ledgers");
            BigDecimal health=new BigDecimal(HighPowerCombat.healthText(target));
            h.runAtTickTime(4,()->{
                h.assertTrue(body.getZ()>start,"actual shadow body follows server-observed aim");
                f.player.setShiftKeyDown(true);
                var state=AbilityStorage.get(f.player);state.learn("liquid_strike");
                state.presets.edit(0,0,"liquid_strike",state::learned);
                h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_strike"),"body performs actual attack");
                h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).compareTo(health)<0,"body attack settles exact damage");
                h.runAtTickTime(18,()->{
                    BigDecimal firstBefore=new BigDecimal(HighPowerCombat.healthText(target));
                    BigDecimal secondBefore=new BigDecimal(HighPowerCombat.healthText(second));
                    state.learn("liquid_shadow_field");state.presets.edit(0,0,"liquid_shadow_field",state::learned);
                    h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_shadow_field"),"body field attacks");
                    h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).compareTo(firstBefore)<0
                            &&new BigDecimal(HighPowerCombat.healthText(second)).compareTo(secondBefore)<0,"field applies exact shared damage to both targets");
                    state.setActivateState(false);
                    h.runAtTickTime(21,()->{
                        h.assertTrue(body.isRemoved(),"deactivation discards body");
                        h.assertTrue(body.controlOwner().isEmpty(),"ending the lease revokes camera ownership");
                        target.discard();second.discard();f.close();h.succeed();
                    });
                });
            });
        } catch(Throwable failure) { f.close(); throw failure; }
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_listen_reports_a_real_nearby_sound_without_remote_sight(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"unassisted body exists");
            var body=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8)).getFirst();
            h.assertFalse(body.hasObservationModule(),"listening does not require artificial sight");
            h.getLevel().playSound(null,h.absolutePos(new BlockPos(4,3,7)),net.minecraft.sounds.SoundEvents.STONE_BREAK,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
            var state=AbilityStorage.get(f.player);state.learn("liquid_listen");state.presets.edit(0,0,"liquid_listen",state::learned);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_listen"),"listen to actual nearby sound");
            boolean heard=false;Object packet;
            while((packet=f.channel.readOutbound())!=null){
                try {if(packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat && chat.overlay()
                        &&chat.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        &&text.getKey().equals("ac.ability.liquid_shadow.heard_front"))heard=true;}
                finally {ReferenceCountUtil.release(packet);}
            }
            h.assertTrue(heard,"real sound in front produces coarse directional hearing feedback");
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_push_moves_real_hostile_without_direct_damage(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real liquid body created");
            var target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,7.5));
            h.assertTrue(HighPowerCombat.enroll(target,5),"target has exact ledger");BigDecimal health=new BigDecimal(HighPowerCombat.healthText(target));
            var state=AbilityStorage.get(f.player);state.learn("liquid_push");state.presets.edit(0,0,"liquid_push",state::learned);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_push"),"body physically pushes enemy");
            h.assertTrue(target.getDeltaMovement().z>.3,"actual enemy velocity changes");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).compareTo(health)==0,"push does not invent direct damage");
            target.discard();f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_guard_deflects_one_real_hostile_arrow_and_charges_shield(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real liquid body created");
            var bodies=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8));
            h.assertTrue(bodies.size()==1,"one body");var body=bodies.getFirst();f.player.setShiftKeyDown(true);
            var state=AbilityStorage.get(f.player);state.learn("liquid_guard");state.presets.edit(0,0,"liquid_guard",state::learned);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_guard"),"body guard armed");
            var shooter=h.spawnWithNoFreeWill(EntityType.SKELETON,new Vec3(9,3,9));var arrow=f.arrow(shooter);
            arrow.setPos(body.getBoundingBox().getCenter().add(0,0,1));arrow.setDeltaMovement(0,0,-.2);
            BigDecimal shield=HighPowerCombat.shield(f.player),cost=new BigDecimal(HighPowerCombat.maxShieldText(f.player)).multiply(new BigDecimal("0.05"));
            h.runAtTickTime(4,()->{
                h.assertTrue(arrow.getDeltaMovement().z>0,"real approaching arrow deflected away from liquid body");
                h.assertTrue(shield.subtract(HighPowerCombat.shield(f.player)).compareTo(cost)==0,"actual shield fraction paid once");
                arrow.discard();shooter.discard();f.close();h.succeed();
            });
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_pulse_moves_multiple_real_hostiles_without_damage(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real body created");
            var a=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,6.5));var b=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(5.5,3,5.5));
            h.assertTrue(HighPowerCombat.enroll(a,5)&&HighPowerCombat.enroll(b,5),"both hostile ledgers enrolled");
            BigDecimal ah=new BigDecimal(HighPowerCombat.healthText(a)),bh=new BigDecimal(HighPowerCombat.healthText(b));
            var state=AbilityStorage.get(f.player);state.learn("liquid_pulse");state.presets.edit(0,0,"liquid_pulse",state::learned);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_pulse"),"body emits a real local impulse");
            h.assertTrue(a.getDeltaMovement().lengthSqr()>.01&&b.getDeltaMovement().lengthSqr()>.01,"both actual enemies move");
            h.assertTrue(ah.compareTo(new BigDecimal(HighPowerCombat.healthText(a)))==0&&bh.compareTo(new BigDecimal(HighPowerCombat.healthText(b)))==0,"impulse adds no damage");
            a.discard();b.discard();f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_screen_intercepts_two_real_hostile_arrows(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real body created");
            var body=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8)).getFirst();
            var shooter=h.spawnWithNoFreeWill(EntityType.SKELETON,new Vec3(9,3,9));var a=f.arrow(shooter);var b=f.arrow(shooter);
            a.setPos(body.getBoundingBox().getCenter().add(0,0,1));a.setDeltaMovement(0,0,-.2);
            b.setPos(body.getBoundingBox().getCenter().add(.3,0,1));b.setDeltaMovement(0,0,-.2);
            var state=AbilityStorage.get(f.player);state.learn("liquid_screen");state.presets.edit(0,0,"liquid_screen",state::learned);
            BigDecimal shield=HighPowerCombat.shield(f.player),cost=new BigDecimal(HighPowerCombat.maxShieldText(f.player)).multiply(new BigDecimal("0.10"));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_screen"),"two-arrow screen succeeds");
            h.assertTrue(a.getDeltaMovement().z>0&&b.getDeltaMovement().z>0&&shield.subtract(HighPowerCombat.shield(f.player)).compareTo(cost)==0,"both real arrows deflected with separate exact shield costs");
            a.discard();b.discard();shooter.discard();f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_dual_sensor_uses_real_sound_and_contact(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real body created");
            var body=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8)).getFirst();
            var target=EntityType.ZOMBIE.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setPos(body.position());h.getLevel().addFreshEntity(target);
            h.getLevel().playSound(null,h.absolutePos(new BlockPos(4,3,7)),net.minecraft.sounds.SoundEvents.STONE_BREAK,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
            var state=AbilityStorage.get(f.player);state.learn("liquid_dual_sensor");state.presets.edit(0,0,"liquid_dual_sensor",state::learned);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_dual_sensor"),"combined actual senses reported");
            boolean both=false;Object packet;while((packet=f.channel.readOutbound())!=null){try {
                if(packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket text&&text.overlay()
                        &&text.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                        &&translated.getKey().equals("ac.ability.liquid_shadow.sensor_contact_sound"))both=true;
            } finally {ReferenceCountUtil.release(packet);}}
            h.assertTrue(both&&!body.hasObservationModule(),"real contact and sound both reported without a camera");target.discard();f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_retreat_moves_body_backwards_without_teleport(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real body created");
            var body=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8)).getFirst();double initial=body.getZ();
            var state=AbilityStorage.get(f.player);state.learn("liquid_retreat");state.presets.edit(0,0,"liquid_retreat",state::learned);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_retreat"),"retreat mode starts");
            h.runAtTickTime(4,()->{h.assertTrue(body.getZ()<initial-.1&&body.getZ()>initial-1,"body walks backwards in bounded real steps");f.close();h.succeed();});
        } catch(Throwable failure) {f.close();throw failure;}
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_route_walks_toward_a_real_visible_floor(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real body created");
            var body=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8)).getFirst();double initial=body.getZ();
            var state=AbilityStorage.get(f.player);state.learn("liquid_route");state.presets.edit(0,0,"liquid_route",state::learned);f.player.setXRot(24);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_route"),"visible floor waypoint is selected");
            h.runAtTickTime(4,()->{h.assertTrue(body.getZ()>initial+.1&&body.getZ()<initial+1,"body follows route by physical bounded steps");f.close();h.succeed();});
        } catch(Throwable failure) {f.close();throw failure;}
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_turn_rotates_the_real_stationary_body(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real body created");
            var body=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8)).getFirst();Vec3 position=body.position();
            var state=AbilityStorage.get(f.player);state.learn("liquid_turn");state.presets.edit(0,0,"liquid_turn",state::learned);f.player.setYHeadRot(90);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_turn"),"body turns in place");
            h.assertTrue(body.getYHeadRot()==90&&body.position().equals(position),"actual head turns without relocation");f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_intercept_deflects_a_real_incoming_arrow(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real body created");
            var body=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8)).getFirst();
            var shooter=h.spawnWithNoFreeWill(EntityType.SKELETON,new Vec3(9,3,9));var arrow=f.arrow(shooter);
            arrow.setPos(body.getBoundingBox().getCenter().add(0,0,1));arrow.setDeltaMovement(0,0,-.2);
            var state=AbilityStorage.get(f.player);state.learn("liquid_intercept");state.presets.edit(0,0,"liquid_intercept",state::learned);
            BigDecimal shield=HighPowerCombat.shield(f.player),cost=new BigDecimal(HighPowerCombat.maxShieldText(f.player)).multiply(new BigDecimal("0.05"));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_intercept"),"manual interception succeeds");
            h.assertTrue(arrow.getDeltaMovement().z>0&&shield.subtract(HighPowerCombat.shield(f.player)).compareTo(cost)==0,"real arrow reversed at exact shield cost");
            arrow.discard();shooter.discard();f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_survey_detects_only_actual_physical_contact(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real body created");
            var body=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8)).getFirst();
            var target=EntityType.ZOMBIE.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setPos(body.position());h.getLevel().addFreshEntity(target);
            var state=AbilityStorage.get(f.player);state.learn("liquid_survey");state.presets.edit(0,0,"liquid_survey",state::learned);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_survey"),"contact survey succeeds without camera");
            boolean contact=false;Object packet;while((packet=f.channel.readOutbound())!=null){try {
                if(packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket text&&text.overlay()
                        &&text.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                        &&translated.getKey().equals("ac.ability.liquid_shadow.touch_contact"))contact=true;
            } finally {ReferenceCountUtil.release(packet);}}
            h.assertTrue(contact&&!body.hasObservationModule(),"only a physically touching creature produces tactile feedback");
            target.discard();f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_path_reports_real_touch_obstacle_without_camera(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"unassisted liquid body created");
            var bodies=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8));
            h.assertTrue(bodies.size()==1&&!bodies.getFirst().hasObservationModule(),"touch works without artificial sight");
            h.setBlock(new BlockPos(4,3,5),Blocks.STONE);
            var state=AbilityStorage.get(f.player);state.learn("liquid_path");state.presets.edit(0,0,"liquid_path",state::learned);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_path"),"paid tactile probe accepted");
            boolean blocked=false;Object packet;
            while((packet=f.channel.readOutbound())!=null) {
                try {if(packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket text && text.overlay()
                        && text.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                        && translated.getKey().equals("ac.ability.liquid_shadow.touch_blocked"))blocked=true;}
                finally {ReferenceCountUtil.release(packet);}
            }
            h.assertTrue(blocked,"real blocking block produces tactile blocked feedback");
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_switch_touches_a_real_lever_within_native_player_reach(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"actual body created");
            h.setBlock(new BlockPos(4,4,7),Blocks.STONE);
            var lever=Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACE,net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                    .setValue(net.minecraft.world.level.block.LeverBlock.FACING,net.minecraft.core.Direction.NORTH);
            var pos=h.absolutePos(new BlockPos(4,4,6));h.getLevel().setBlock(pos,lever,3);
            var state=AbilityStorage.get(f.player);state.learn("liquid_switch");state.presets.edit(0,0,"liquid_switch",state::learned);
            f.player.setXRot(8);f.player.setYHeadRot(0);
            var body=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8)).getFirst();
            var from=body.getEyePosition();var direction=cn.academy.port.skill.ClassicRaytrace.direction(f.player);
            var actual=h.getLevel().clip(new net.minecraft.world.level.ClipContext(from,from.add(direction.scale(2.5)),
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,body));
            h.assertTrue(actual.getBlockPos().equals(pos),"actual shadow touch ray reaches the real lever");
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_switch"),"body physically touches protected native lever");
            h.assertTrue(h.getLevel().getBlockState(pos).getValue(BlockStateProperties.POWERED),"native redstone lever actually switches on");
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    private static final class MoveShadowOnJoin {
        CanonLiquidEntities.Shadow moved;
        @SubscribeEvent public void joining(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
            if(event.getEntity() instanceof CanonLiquidEntities.Shadow body){moved=body;body.setPos(body.position().add(2,0,0));}
        }
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_spawn_rejects_body_moved_by_real_join_event(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");var hook=new MoveShadowOnJoin();
        try {
            for(int x=2;x<=8;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            NeoForge.EVENT_BUS.register(hook);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"join callback moved the body and aborts deployment");
            h.assertTrue(hook.moved!=null&&hook.moved.isRemoved(),"uncommitted native body removed");
            h.assertTrue(f.player.getMainHandItem().is(CanonLiquidEntities.DENSE_MEDIUM.get()),"failed deployment does not consume medium");
            h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(hook);f.close();}
    }
    private static final class MoveShadowOnSwitch {
        final ServerPlayer owner;final CanonLiquidEntities.Shadow body;boolean called;
        MoveShadowOnSwitch(ServerPlayer owner,CanonLiquidEntities.Shadow body){this.owner=owner;this.body=body;}
        @SubscribeEvent public void click(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
            if(event.getEntity()==owner){called=true;body.setPos(body.position().add(2,0,0));}
        }
    }
    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_switch_rejects_body_moved_by_real_interaction_event(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");MoveShadowOnSwitch hook=null;
        try {
            for(int x=2;x<=8;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"actual body created");
            h.setBlock(new BlockPos(4,4,7),Blocks.STONE);
            var lever=Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACE,net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                    .setValue(net.minecraft.world.level.block.LeverBlock.FACING,net.minecraft.core.Direction.NORTH);
            var pos=h.absolutePos(new BlockPos(4,4,6));h.getLevel().setBlock(pos,lever,3);
            var state=AbilityStorage.get(f.player);state.learn("liquid_switch");state.presets.edit(0,0,"liquid_switch",state::learned);
            f.player.setXRot(8);f.player.setYHeadRot(0);
            var body=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8)).getFirst();
            hook=new MoveShadowOnSwitch(f.player,body);NeoForge.EVENT_BUS.register(hook);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"liquid_switch"),"interaction callback invalidates stale touch");
            h.assertTrue(hook.called&&!h.getLevel().getBlockState(pos).getValue(BlockStateProperties.POWERED),"native callback ran and lever remains off");
            h.succeed();
        } finally {if(hook!=null)NeoForge.EVENT_BUS.unregister(hook);f.close();}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_follow_and_recall_move_the_real_body_without_teleport(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=12;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real leased body created");
            var bodies=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8));
            h.assertTrue(bodies.size()==1,"one body");var body=bodies.getFirst();double initial=body.getZ();
            Vec3 owner=h.absoluteVec(new Vec3(4.5,3,10));f.player.moveTo(owner.x,owner.y,owner.z,0,0);
            var state=AbilityStorage.get(f.player);state.learn("liquid_follow");state.learn("liquid_recall");
            state.presets.edit(0,0,"liquid_follow",state::learned);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_follow"),"follow mode starts");
            h.runAtTickTime(4,()->{
                h.assertTrue(body.getZ()>initial+.1&&body.getZ()<initial+1,"follow moves gradually rather than teleporting");
                state.presets.edit(0,0,"liquid_recall",state::learned);
                h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_recall"),"recall mode starts");
                double oldDistance=body.distanceToSqr(f.player);
                h.runAtTickTime(8,()->{
                    h.assertTrue(body.isRemoved()||body.distanceToSqr(f.player)<oldDistance,"real body approaches owner");
                    f.close();h.succeed();
                });
            });
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_pause_and_dismiss_control_the_real_leased_body(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            h.assertTrue(f.acknowledgeTeleports()>0,"client acknowledges login teleport");
            Vec3 origin=h.absoluteVec(new Vec3(4.5,3,2.5));f.player.moveTo(origin.x,origin.y,origin.z,0,0);f.player.setYHeadRot(0);
            for(int tick=1;tick<=16;tick++)h.runAtTickTime(tick,()->f.player.connection.tick());
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"actual medium creates body");
            h.assertTrue(f.player.getInventory().countItem(CanonLiquidEntities.DENSE_MEDIUM.get())==0,"actual medium was consumed before pause");
            var bodies=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8));
            h.assertTrue(bodies.size()==1,"one leased body");var body=bodies.getFirst();var state=AbilityStorage.get(f.player);
            state.learn("liquid_pause");state.learn("liquid_dismiss");state.presets.edit(0,0,"liquid_pause",state::learned);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_pause"),"pause controls real body");double stopped=body.getZ();
            h.runAtTickTime(4,()->{
                h.assertTrue(Math.abs(body.getZ()-stopped)<1e-6,"pause actually prevents movement");
                h.runAtTickTime(12,()->{
                    h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_pause"),"second press resumes body");
                    h.runAtTickTime(16,()->{
                        h.assertTrue(body.getZ()>stopped+.1,"resumed body physically moves");
                        state.presets.edit(0,0,"liquid_dismiss",state::learned);
                        h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_dismiss"),"dismiss releases body");
                        h.assertTrue(body.isRemoved()&&body.controlOwner().isEmpty(),"dismiss destroys the actual lease without refund");
                        h.assertTrue(f.player.getInventory().countItem(CanonLiquidEntities.DENSE_MEDIUM.get())==0,
                                "spent medium is not duplicated in any inventory slot");
                        f.close();h.succeed();
                    });
                });
            });
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_remote_view_requires_consumed_optical_module(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            f.player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(CanonLiquidEntities.OBSERVATION_MODULE.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"medium and physical optic deploy body");
            h.assertTrue(f.player.getMainHandItem().isEmpty()&&f.player.getOffhandItem().isEmpty(),"body medium and optical module consumed");
            var bodies=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8));
            h.assertTrue(bodies.size()==1&&bodies.getFirst().hasObservationModule(),"server marks installed observation module");
            var body=bodies.getFirst();AbilityStorage.get(f.player).setActivateState(false);
            h.runAtTickTime(3,()->{
                h.assertTrue(body.isRemoved()&&!body.hasObservationModule()&&body.controlOwner().isEmpty(),"lease end revokes optic and camera ownership");
                f.close();h.succeed();
            });
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_spyglass_is_not_an_observation_module(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            f.player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(Items.SPYGLASS));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"medium deploys a touch and hearing body");
            var bodies=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8));
            h.assertTrue(bodies.size()==1&&!bodies.getFirst().hasObservationModule(),"ordinary spyglass grants no remote observation");
            h.assertTrue(f.player.getOffhandItem().is(Items.SPYGLASS),"uninstalled spyglass is retained");
            f.close();h.succeed();
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_body_tracks_native_player_look_packet(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        try {
            h.assertTrue(f.acknowledgeTeleports()>0,"client acknowledges actual login teleport before sending movement");
            Vec3 origin=h.absoluteVec(new Vec3(4.5,3,2.5));f.player.moveTo(origin.x,origin.y,origin.z,0,0);f.player.setYHeadRot(0);
            for(int x=1;x<=8;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            f.player.setOnGround(true);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real leased body created");
            var bodies=h.getLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,f.player.getBoundingBox().inflate(8));
            h.assertTrue(bodies.size()==1,"one body");var body=bodies.getFirst();double initialX=body.getX();
            var at=f.player.position();f.player.connection.resetPosition();
            f.player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(at.x,at.y,at.z,90,0,true));
            h.assertTrue(Math.abs(f.player.getYRot()-90)<.01,"native look packet accepted by server");
            // EmbeddedChannel is not in the server connection listener's tick loop. Tick the real
            // listener once so ServerPlayer.doTick updates head yaw exactly as a connected client does.
            f.player.connection.tick();
            h.assertTrue(Math.abs(f.player.getYHeadRot()-90)<.01,"native connection tick advances observed head yaw");
            h.runAtTickTime(4,()->{
                h.assertTrue(body.getX()<initialX-.1,"body steers toward the new server-observed look");
                f.close();h.succeed();
            });
        } catch(Throwable failure) {f.close();throw failure;}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void water_barrier_intercepts_one_arrow_and_pays_exact_shield(GameTestHelper h) {
        var f=new Fixture(h,"hydro_hand","hydro_shield");
        try {
            h.setBlock(new BlockPos(3,3,2),Blocks.WATER);
            BigDecimal before=HighPowerCombat.shield(f.player);
            BigDecimal expectedCost=new BigDecimal(HighPowerCombat.maxShieldText(f.player)).multiply(new BigDecimal("0.05"));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"hydro_shield"),"water barrier starts");
            var shooter=h.spawnWithNoFreeWill(EntityType.SKELETON,new Vec3(9,3,9));
            Arrow arrow=f.arrow(shooter);
            h.runAtTickTime(4,()->{
                h.assertTrue(arrow.getDeltaMovement().z>0,"actual approaching arrow diverted away");
                h.assertTrue(before.subtract(HighPowerCombat.shield(f.player)).compareTo(expectedCost)==0,"one exact shield charge");
                h.assertBlockPresent(Blocks.WATER,new BlockPos(3,3,2));
                arrow.discard(); shooter.discard(); f.close(); h.succeed();
            });
        } catch(Throwable failure) { f.close(); throw failure; }
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_armor_consumes_sheets_and_blocks_hostile_arrow(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_armor");
        try {
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,2));
            double cp=HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"paper_armor"),"two sheets cannot deploy armor");
            h.assertTrue(HighPowerResources.cp(f.player)==cp,"missing paper costs no CP");
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,3));
            BigDecimal before=HighPowerCombat.shield(f.player);
            BigDecimal cost=new BigDecimal(HighPowerCombat.maxShieldText(f.player)).multiply(new BigDecimal("0.05"));
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_armor"),"three-sheet armor deploys");
            h.assertTrue(f.player.getMainHandItem().isEmpty(),"three actual paper consumed");
            var shooter=h.spawnWithNoFreeWill(EntityType.SKELETON,new Vec3(9,3,9)); Arrow arrow=f.arrow(shooter);
            h.runAtTickTime(4,()->{
                h.assertTrue(arrow.getDeltaMovement().z>0,"paper sheet diverts actual arrow");
                h.assertTrue(before.subtract(HighPowerCombat.shield(f.player)).compareTo(cost)==0,"paper pays exact shield on contact");
                arrow.discard(); shooter.discard(); f.close(); h.succeed();
            });
        } catch(Throwable failure) { f.close(); throw failure; }
    }


    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void paper_array_rechecks_each_target_after_damage_callback(GameTestHelper h) {
        var f=new Fixture(h,"paper_psychokinesis","paper_array");
        var a=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,6));
        var b=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,9));
        var hook=new AttackHook(f.player,"paper_psychokinesis.paper_array",a,b,false);
        try {
            h.assertTrue(HighPowerCombat.enroll(a,5)&&HighPowerCombat.enroll(b,5),"exact target ledgers");
            BigDecimal hpA=new BigDecimal(HighPowerCombat.healthText(a)),hpB=new BigDecimal(HighPowerCombat.healthText(b));
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,6));
            NeoForge.EVENT_BUS.register(hook);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"paper_array"),"array begins");
            h.assertTrue(hook.calls==1,"first target callback moved the other out of range");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(a)).compareTo(hpA)<0,"first target hit");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(b)).compareTo(hpB)==0,"moved target receives no damage");
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(hook);a.discard();b.discard();f.close(); }
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void denied_water_and_paper_target_moved_by_own_callback_give_no_experience(GameTestHelper h) {
        var water=new Fixture(h,"hydro_hand","hydro_whip");
        var paper=new Fixture(h,"paper_psychokinesis","paper_blade");
        // Fixtures share the initial coordinates: move paper player to a separate lane.
        paper.player.setPos(paper.player.position().add(3,0,0));
        var a=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,7.5));
        var b=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(7.5,3,7.5));
        var ah=new AttackHook(water.player,"hydro_hand.hydro_whip",a,null,true);
        var bh=new AttackHook(paper.player,"paper_psychokinesis.paper_blade",b,b,false);
        try {
            h.setBlock(new BlockPos(3,3,2),Blocks.WATER);
            h.assertTrue(HighPowerCombat.enroll(a,5)&&HighPowerCombat.enroll(b,5),"exact target ledgers");
            paper.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER));
            var ws=AbilityStorage.get(water.player);var ps=AbilityStorage.get(paper.player);
            double we=ws.exp("hydro_whip"),pe=ps.exp("paper_blade");
            BigDecimal ha=new BigDecimal(HighPowerCombat.healthText(a)),hb=new BigDecimal(HighPowerCombat.healthText(b));
            NeoForge.EVENT_BUS.register(ah);NeoForge.EVENT_BUS.register(bh);
            h.assertTrue(CanonSkillsCombatB.cast(water.player,"hydro_whip"),"water cast reaches callback");
            h.assertTrue(CanonSkillsCombatB.cast(paper.player,"paper_blade"),"paper cast reaches callback");
            h.assertTrue(ah.calls==1&&bh.calls==1,"water callback denied and paper callback moved its own target");
            h.assertTrue(ws.exp("hydro_whip")==we&&ps.exp("paper_blade")==pe,"rejected hits give no skill experience");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(a)).compareTo(ha)==0&&new BigDecimal(HighPowerCombat.healthText(b)).compareTo(hb)==0,"denied hits leave exact health unchanged");
            h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(ah);NeoForge.EVENT_BUS.unregister(bh);a.discard();b.discard();water.close();paper.close();}
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_field_rechecks_target_after_callback_and_denial_awards_no_experience(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
        f.player.setOnGround(true);
        f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
        h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real shadow created");
        var a=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(3,3,7.5));
        var b=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(6,3,7.5));
        h.assertTrue(HighPowerCombat.enroll(a,5)&&HighPowerCombat.enroll(b,5),"exact target ledgers");
        h.runAtTickTime(4,()->{
            f.player.setShiftKeyDown(true);
            var state=AbilityStorage.get(f.player);state.learn("liquid_shadow_field");state.presets.edit(0,0,"liquid_shadow_field",state::learned);
            var first=a.getUUID().compareTo(b.getUUID())<0?a:b;var second=first==a?b:a;
            var hook=new AttackHook(f.player,"liquid_shadow.liquid_shadow_field",first,second,true);
            double exp=state.exp("liquid_shadow_field");BigDecimal hp=new BigDecimal(HighPowerCombat.healthText(second));
            try {
                NeoForge.EVENT_BUS.register(hook);
                h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_shadow_field"),"field begins");
                h.assertTrue(hook.calls==1,"first callback moved second outside body range");
                h.assertTrue(new BigDecimal(HighPowerCombat.healthText(second)).compareTo(hp)==0,"moved target unharmed");
                h.assertTrue(state.exp("liquid_shadow_field")==exp,"denied hit gives no experience");
                h.succeed();
            } finally {NeoForge.EVENT_BUS.unregister(hook);a.discard();b.discard();f.close();}
        });
    }


    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void liquid_target_moved_in_its_own_callback_cannot_be_hit(GameTestHelper h) {
        var f=new Fixture(h,"liquid_shadow","liquid_scout");
        for(int x=2;x<=7;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
        f.player.setOnGround(true);
        f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));
        h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_scout"),"real shadow created");
        var target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,8.5));
        h.assertTrue(HighPowerCombat.enroll(target,5),"exact target ledger");
        h.runAtTickTime(4,()->{
            f.player.setShiftKeyDown(true);
            var state=AbilityStorage.get(f.player);state.learn("liquid_strike");state.presets.edit(0,0,"liquid_strike",state::learned);
            var hook=new AttackHook(f.player,"liquid_shadow.liquid_strike",target,target,false);
            double exp=state.exp("liquid_strike");BigDecimal hp=new BigDecimal(HighPowerCombat.healthText(target));
            try {
                NeoForge.EVENT_BUS.register(hook);
                h.assertTrue(CanonSkillsCombatB.cast(f.player,"liquid_strike"),"strike enters callback");
                h.assertTrue(hook.calls==1,"target moved during its own attack callback");
                h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).compareTo(hp)==0,"out of body range target unharmed");
                h.assertTrue(state.exp("liquid_strike")==exp,"rejected strike grants no experience");
                h.succeed();
            } finally {NeoForge.EVENT_BUS.unregister(hook);target.discard();f.close();}
        });
    }

    @GameTest(template="empty",batch="canon_media",timeoutTicks=100)
    public static void target_policy_only_special_cases_real_calibrator(GameTestHelper h) {
        var boss=UrbanCalibrator.BOSS.get().create(h.getLevel());
        var villager=h.spawnWithNoFreeWill(EntityType.VILLAGER,new Vec3(5,3,5));
        var zombie=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(6,3,5));
        try {
            h.assertTrue(boss!=null&&CanonTargetPolicy.hostile(boss),"registered exact calibrator boss is eligible");
            h.assertFalse(CanonTargetPolicy.hostile(villager),"friendly villager remains ineligible");
            h.assertTrue(CanonTargetPolicy.hostile(zombie),"vanilla hostile remains eligible");
            h.succeed();
        } finally {if(boss!=null)boss.discard();villager.discard();zombie.discard();}
    }

    public static final class AttackHook {
        final ServerPlayer player;final String skill;final LivingEntity first,move;final boolean deny;int calls;
        AttackHook(ServerPlayer player,String skill,LivingEntity first,LivingEntity move,boolean deny){this.player=player;this.skill=skill;this.first=first;this.move=move;this.deny=deny;}
        @SubscribeEvent public void attack(SkillAttackEvent e){
            if(e.player!=player||!e.skill.equals(skill))return;
            calls++;
            if(e.target==first&&move!=null) {
                String id=skill.substring(skill.indexOf('.')+1);
                double distance=switch(id){
                    case "paper_array" -> SkillTuning.number(id, "radius");
                    case "liquid_shadow_field" -> SkillTuning.number("liquid_scout", "control_reach")+SkillTuning.number(id, "radius");
                    default -> SkillTuning.number(id, "reach");
                };
                move.setPos(player.position().add(0,0,distance+2));
            }
            if(deny)e.amount=0;
        }
    }

    private static final class Fixture {
        final GameTestHelper h; final ServerPlayer player; final EmbeddedChannel channel; boolean closed;
        Fixture(GameTestHelper h,String category,String id) {
            this.h=h; var world=h.getLevel(); UUID uuid=UUID.randomUUID();
            var cookie=CommonListenerCookie.createInitial(new GameProfile(uuid,"CM"+uuid.toString().substring(0,8)),false);
            player=new ServerPlayer(world.getServer(),world,cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND); channel=new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); world.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
            player.setGameMode(GameType.SURVIVAL); player.getAbilities().instabuild=false;
            Vec3 position=h.absoluteVec(new Vec3(4.5,3,2.5)); player.moveTo(position.x,position.y,position.z,0,0); player.setYHeadRot(0);
            for(int x=(player.blockPosition().getX()-32)>>4;x<=(player.blockPosition().getX()+32)>>4;x++)
                for(int z=(player.blockPosition().getZ()-32)>>4;z<=(player.blockPosition().getZ()+32)>>4;z++)world.getChunk(x,z);
            h.setBlock(new BlockPos(4,2,2),Blocks.STONE);
            var state=AbilityStorage.get(player); state.changeCategoryClassic(category); state.setLevel(5); state.learn(id); state.setActivateState(true);
            state.presets.edit(0,0,id,state::learned);
            h.assertTrue(HighPowerCombat.enroll(player,5),"real high-power ledger fixture"); HighPowerResources.initializeBudget(player);
            h.testInfo.addListener(new GameTestListener(){
                public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){close();}
                public void testFailed(GameTestInfo i,GameTestRunner r){close();} public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}
            });
        }
        int acknowledgeTeleports() {
            int acknowledged=0;Object packet;
            while((packet=channel.readOutbound())!=null) {
                try {
                    if(packet instanceof ClientboundPlayerPositionPacket position) {
                        player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));
                        acknowledged++;
                    }
                } finally {ReferenceCountUtil.release(packet);}
            }
            return acknowledged;
        }
        Arrow arrow(Skeleton shooter) {
            var arrow=new Arrow(h.getLevel(),shooter,new ItemStack(Items.ARROW),null);
            arrow.setPos(player.getBoundingBox().getCenter().add(0,0,2)); arrow.setDeltaMovement(0,0,-.2);
            h.assertTrue(h.getLevel().addFreshEntity(arrow),"real arrow added to level"); return arrow;
        }
        void close() { if(closed)return;closed=true; try {player.server.getPlayerList().remove(player);} finally {channel.finishAndReleaseAll();} }
    }
}
