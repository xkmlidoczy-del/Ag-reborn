package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.SpatialPincerCorridorRules;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.DamageMitigation;
import cn.academy.port.highpower.core.DefenseProfile;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW341 real short-release surface marks, actual native projectile ticks and native damage callbacks.
 * Resident mocked-network players, learned flags, supported floors and stationary husks are explicit fixtures.
 * No attack permit is manufactured; manual projectile ticks execute the same native first-contact body.
 */
@GameTestHolder("academy_highpower_spatial_corridor")
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid="academy")
public final class HighPowerSpatialPincerCorridorRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_spatial_pincer_corridor";
    private static final String ID=SpatialPincerCorridor.ID, SKILL="teleporter.spatial_pincer_corridor";
    private static final String FENCES="academy:spatial_pincer_corridor_fences";
    private static final TicketType<UUID> TICKET=TicketType.create("academy_spatial_pincer_fixture",UUID::compareTo);
    private HighPowerSpatialPincerCorridorRuntimeTests() {}
    private static BigDecimal n(String value){return new BigDecimal(value);}
    private static BigDecimal hp(LivingEntity entity){return HighPowerCombat.enabled(entity)?n(HighPowerCombat.healthText(entity)):BigDecimal.valueOf(entity.getHealth());}
    private static void eq(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+": "+actual+" != "+expected);}
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-6,why+": "+actual+" != "+expected);}
    private static BigDecimal firstShare(Fixture f){return HighPowerGrowth.values(f.p).damage().multiply(n("2.8")).divide(BigDecimal.valueOf(3),CombatAmounts.MAX_ABSOLUTE_SCALE,java.math.RoundingMode.DOWN);}
    /** Independent oracle for the retained double-valued skill hook's documented exact ratio mapping. */
    private static BigDecimal discountedFirstShare(Fixture f){BigDecimal original=firstShare(f);return original.multiply(n("5")).divide(BigDecimal.valueOf(original.doubleValue()),java.math.MathContext.DECIMAL128);}

    @SubscribeEvent public static void installTemplate(net.neoforged.neoforge.event.level.LevelEvent.Load event){
        if(!net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return;
        CompoundTag tag=new CompoundTag(),cell=new CompoundTag(),air=new CompoundTag();var size=new net.minecraft.nbt.ListTag();var position=new net.minecraft.nbt.ListTag();var blocks=new net.minecraft.nbt.ListTag();var palette=new net.minecraft.nbt.ListTag();
        for(int dimension:new int[]{32,8,32})size.add(net.minecraft.nbt.IntTag.valueOf(dimension));for(int axis=0;axis<3;axis++)position.add(net.minecraft.nbt.IntTag.valueOf(0));
        cell.put("pos",position);cell.putInt("state",0);blocks.add(cell);air.putString("Name","minecraft:air");palette.add(air);
        tag.put("size",size);tag.put("blocks",blocks);tag.put("palette",palette);tag.put("entities",new net.minecraft.nbt.ListTag());
        level.getStructureManager().getOrCreate(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy_highpower_spatial_corridor",TEMPLATE)).load(level.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=24)
    public static void three_real_short_release_marks_are_free_and_keep_first_lifetime(GameTestHelper h){
        Fixture f=new Fixture(h,true);double cp=f.cp();f.ready(()->{
            h.assertTrue(SpatialPincerCorridor.preparedPoints(f.p)==3&&SpatialPincerCorridor.marked(f.p)&&!SpatialPincerCorridor.active(f.p),"three saved physical floors remain preparation only");
            h.assertValueEqual(SpatialPincerCorridor.remainingTicks(f.p),SpatialPincerCorridorRules.PREPARATION_TICKS - 12,"later marks preserve first mark's lifetime");
            h.assertTrue(f.originA.equals(f.surfaceA.add(0,1.2,0))&&f.originB.equals(f.surfaceB.add(0,1.2,0))&&f.originC.equals(f.surfaceC.add(0,1.2,0)),"three emitter origins retain the exact native UP hits plus 1.2m");
            near(h,f.cp(),cp,"free preparation preserves CP");h.assertValueEqual(f.needles(),3,"free marks never escrow media");
            h.assertTrue(f.spawns.media.isEmpty()&&f.p.position().equals(f.source),"preparation creates no needle or movement");
            near(h,AbilityStorage.get(f.p).exp(ID),0,"free marks grant no mastery");
        });f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=16)
    public static void press_alone_and_replayed_release_never_place_an_extra_mark(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.aim(f.pointA);String wire=f.wire(++f.inputs);double cp=f.cp();
        h.assertTrue(SpatialPincerCorridor.press(f.p,wire),"authenticated press records gesture");
        h.assertValueEqual(SpatialPincerCorridor.preparedPoints(f.p),0,"press is not release authority");
        h.assertTrue(SpatialPincerCorridor.release(f.p,wire),"first matching short release captures A");
        h.assertFalse(SpatialPincerCorridor.release(f.p,wire)||SpatialPincerCorridor.press(f.p,wire),"same input cannot release or press again");
        h.assertValueEqual(SpatialPincerCorridor.preparedPoints(f.p),1,"replay never adds B");near(h,f.cp(),cp,"gesture replay remains free");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void six_observed_hold_ticks_clear_only_on_matching_release(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.markA();double cp=f.cp();f.post(4,()->{
            h.assertTrue(f.press(),"fresh held gesture begins after request fence");String wire=f.wire(f.inputs);
            for(int i=0;i<100;i++)SpatialPincerCorridor.tick(f.p);
            h.assertValueEqual(SpatialPincerCorridor.heldTicks(f.p),0,"same-tick calls cannot accelerate hold");
            f.post(5,()->h.assertTrue(SpatialPincerCorridor.heldTicks(f.p)==5&&SpatialPincerCorridor.marked(f.p),"five real ticks keep the mark"));
            f.post(6,()->{h.assertTrue(SpatialPincerCorridor.heldTicks(f.p)==6&&SpatialPincerCorridor.marked(f.p),"six ticks alone do not auto-clear");
                h.assertTrue(SpatialPincerCorridor.release(f.p,wire),"matching long release clears");
                h.assertFalse(SpatialPincerCorridor.marked(f.p)||SpatialPincerCorridor.active(f.p),"long release ends saved preparation");
                near(h,f.cp(),cp,"clear consumes no CP");h.assertValueEqual(f.needles(),3,"clear consumes no media");});
        });f.finish(11);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=515)
    public static void preparation_expires_on_its_real_tick_boundary_without_call_acceleration(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.markA();double cp=f.cp();for(int i=0;i<1000;i++)SpatialPincerCorridor.tick(f.p);
        h.assertValueEqual(SpatialPincerCorridor.remainingTicks(f.p),SpatialPincerCorridorRules.PREPARATION_TICKS,"same physical tick never ages saved points");
        f.post(SpatialPincerCorridorRules.PREPARATION_TICKS - 1,()->h.assertTrue(SpatialPincerCorridor.marked(f.p)&&SpatialPincerCorridor.remainingTicks(f.p)==1,"A survives its final eligible tick"));
        f.post(SpatialPincerCorridorRules.PREPARATION_TICKS,()->{h.assertFalse(SpatialPincerCorridor.marked(f.p)||SpatialPincerCorridor.active(f.p),"the exact number of observed ticks expire A");
            h.assertTrue(SpatialPincerCorridor.receipt(f.p).reason().equals("expired")&&!SpatialPincerCorridor.receipt(f.p).paid(),"expiry records a free terminal outcome");
            near(h,f.cp(),cp,"expiry has no CP cost");h.assertValueEqual(f.needles(),3,"expiry retains every original");});f.finish(SpatialPincerCorridorRules.PREPARATION_TICKS + 1);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=16)
    public static void overlapping_second_standing_body_is_refused_without_new_mark(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.markA();double cp=f.cp();f.post(4,()->{f.aim(f.pointA.add(.25,0,0));
            h.assertFalse(f.tap(),"overlapping full standing destination is rejected");
            h.assertTrue(SpatialPincerCorridor.receipt(f.p).reason().equals("points_overlap")&&!SpatialPincerCorridor.marked(f.p),"overlap terminates unsafe preparation");
            near(h,f.cp(),cp,"overlap is free");h.assertValueEqual(f.needles(),3,"overlap creates no originals");});f.finish(5);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=22)
    public static void two_offhand_needles_refuse_whole_volley_before_any_debit(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AcademyCraft.NEEDLE.get(),2));double cp=f.cp();
        f.preview(()->f.post(4,()->{h.assertFalse(f.tap(),"all three real originals must exist before the first publication");
            h.assertTrue(SpatialPincerCorridor.receipt(f.p).reason().equals("needles_required")&&f.spawns.media.isEmpty(),"insufficient stock never partially starts");
            near(h,f.cp(),cp,"stock refusal has no CP cost");h.assertValueEqual(f.needles(),2,"two originals stay in inventory");}));f.finish(17);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=22)
    public static void decorated_offhand_medium_refuses_before_publication(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.p.getOffhandItem().set(DataComponents.CUSTOM_NAME,Component.literal("declared decorated needle"));double cp=f.cp();
        f.preview(()->f.post(4,()->{h.assertFalse(f.tap(),"component-bearing stock is not plain original medium");
            h.assertTrue(f.spawns.media.isEmpty()&&f.needles()==3,"decorated stack remains intact");near(h,f.cp(),cp,"decorated medium refusal is free");}));f.finish(17);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=34)
    public static void real_A_B_C_publications_keep_zero_four_eight_cadence_and_three_cost_shares(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.hook(new Discount(f.target));double before=f.cp();Observation hit=f.observe(f.target);BigDecimal health=hp(f.target);
        f.begin(()->{h.assertValueEqual(f.spawns.media.size(),1,"release publishes only A");double paid=f.cp();
            for(int i=0;i<64;i++)SpatialPincerCorridor.tick(f.p);
            for(int i=0;i<8;i++){SpatialPincerCorridor.pre(new ServerTickEvent.Pre(()->true,f.p.server));SpatialPincerCorridor.post(new ServerTickEvent.Post(()->true,f.p.server));}
            h.assertTrue(f.spawns.media.size()==1&&f.cp()==paid,"duplicate observations cannot catch up, publish or repay");
            f.post(3,()->h.assertValueEqual(f.spawns.media.size(),1,"B is not early"));
            f.post(4,()->h.assertValueEqual(f.spawns.media.size(),2,"B publishes on the fourth actual tick"));
            f.post(7,()->h.assertValueEqual(f.spawns.media.size(),2,"C is not early"));
            f.post(8,()->{h.assertTrue(f.spawns.ticks.equals(List.of(f.started,f.started+4,f.started+8)),"native joins prove observed cadence 0/4/8");
                h.assertTrue(f.spawns.origins.equals(List.of(f.originA,f.originB,f.originC)),"each native original comes from its own frozen A/B/C emitter");
                h.assertTrue(f.spawns.media.stream().map(Entity::getUUID).distinct().count()==3&&f.needles()==0,"three distinct native media consume exactly three originals");
                h.assertTrue(f.spawns.exactPayments&&SpatialPincerCorridor.receipt(f.p).paidNeedles()==3,"three publications each pay only their fixed CP share");
                h.assertValueEqual(f.budget.debitCount,3,"each publication has one independently witnessed actual debit");
                f.budget.assertConserved("three exact debits and native recovery conserve the represented CP ledger");});
            f.post(12,()->{eq(h,hp(f.target),health.subtract(discountedFirstShare(f)),"same-rank native iframes suppress later equal reduced shares without pooling");
                h.assertTrue(hit.posts==1&&SpatialPincerCorridor.receipt(f.p).contacts()==3&&SpatialPincerCorridor.receipt(f.p).hurts()==1,"three first contacts retain one native accepted hit");
                h.assertFalse(SpatialPincerCorridor.active(f.p),"completed flight sequence closes");near(h,AbilityStorage.get(f.p).exp(ID),0,"no new mastery from the volley");
                h.assertTrue(f.spawns.media.stream().allMatch(m->!m.isRemoved()&&!m.isContactEnabled()&&m.mediumSnapshot().getCount()==1),"spent originals remain harmless recoverable native objects");});
        });f.finish(25);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=32)
    public static void cancel_before_attack_preserves_all_three_stock_and_budget(GameTestHelper h){cancelCase(h,0);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=32)
    public static void cancel_after_A_preserves_two_unused_stock_without_refund(GameTestHelper h){cancelCase(h,1);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=36)
    public static void cancel_after_B_preserves_one_unused_stock_without_refund(GameTestHelper h){cancelCase(h,2);}
    private static void cancelCase(GameTestHelper h,int paid){
        Fixture f=new Fixture(h,true);double before=f.cp();Runnable cancel=()->{double cp=f.cp();SpatialPincerCorridor.cancelActive(f.p);
            h.assertFalse(SpatialPincerCorridor.active(f.p)||SpatialPincerCorridor.marked(f.p),"cancel removes preparation and future publication authority");
            h.assertValueEqual(f.needles(),3-paid,"only actually published originals leave inventory");
            h.assertTrue(f.spawns.media.size()==paid&&f.spawns.media.stream().allMatch(m->!m.isContactEnabled()),"every paid original remains harmless physical custody");
            h.assertTrue(Double.doubleToLongBits(f.cp())==Double.doubleToLongBits(cp),"cancellation itself neither refunds nor repays CP");
            h.assertValueEqual(f.budget.debitCount,paid,"only actual native publications have a witnessed debit");f.budget.assertConserved("cancel preserves all witnessed native recovery and publication debits");
            f.post(9,()->{h.assertValueEqual(f.spawns.media.size(),paid,"cancel cannot replay a queued slot");
                h.assertValueEqual(f.budget.debitCount,paid,"canceled queue never adds another debit");f.budget.assertConserved("later CP changes are exactly witnessed native recovery, without cancellation refund");
                if(paid>0)h.assertTrue(f.budget.positiveRecoveries>0,"real player ticks independently replenish the previously spent budget");});};
        if(paid==0)f.ready(cancel);else f.begin(()->{if(paid==1)cancel.run();else f.post(4,cancel);});f.finish(paid==2?26:22);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void native_contact_keeps_exact_fraction_and_actual_thrown_source(GameTestHelper h){
        Fixture f=new Fixture(h,true);Observation hit=f.observe(f.target);BigDecimal before=hp(f.target),share=firstShare(f);
        f.begin(()->{DisplacedNeedleEntity medium=f.first();Vec3 origin=medium.position();near(h,medium.getDeltaMovement().length(),1.6,"native initial speed is exactly the bounded configured speed");
            h.assertFalse(medium.isNoGravity()||medium.isNoPhysics()||medium.noPhysics,"flight uses ordinary native gravity and collisions");
            medium.tick();eq(h,hp(f.target),before.subtract(share),"one native contact debits the exact allocated third of 2.8D");
            h.assertTrue(hit.incoming==1&&hit.posts==1&&medium.getType()==TeleporterNeedleEntities.CORRIDOR_NEEDLE.get(),"one actual native needle drives one native hurt");
            assertSource(h,hit.source,f.p,medium,origin);SpatialPincerCorridor.cancelActive(f.p);});f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=24)
    public static void generic_skill_string_and_null_permit_cannot_forge_contact(GameTestHelper h){
        Fixture f=new Fixture(h,true);BigDecimal before=hp(f.target);double cp=f.cp();
        h.assertFalse(HighPowerCombat.attack(f.p,SKILL,f.target,n("5"))||HighPowerCombat.attackSpatialPincerCorridor(null),"generic names and null authority never enter the closed native contact path");
        eq(h,hp(f.target),before,"spoof leaves exact HP intact");near(h,f.cp(),cp,"spoof leaves budget intact");
        h.assertTrue(f.target.hurt(f.p.damageSources().playerAttack(f.p),5),"ordinary native combat remains available");eq(h,hp(f.target),before.subtract(n("5")),"ordinary native HP still applies its literal damage");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=24)
    public static void spent_contact_cannot_rearm_advance_generation_or_reenter_tick(GameTestHelper h){
        Fixture f=new Fixture(h,true);Observation hit=f.observe(f.target);f.begin(()->{DisplacedNeedleEntity medium=f.first();var identity=medium.identity();medium.tick();BigDecimal after=hp(f.target);
            h.assertFalse(medium.beginFlight(identity)||medium.advanceGeneration(identity,identity.generation()+1),"spent corridor original cannot acquire new attack permission");
            for(int i=0;i<8;i++)medium.tick();eq(h,hp(f.target),after,"repeated native ticks cannot replay the spent share");
            h.assertValueEqual(hit.incoming,1,"no second native hurt after spent contact");SpatialPincerCorridor.cancelActive(f.p);});f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=24)
    public static void ordinary_unenrolled_hostile_death_preserves_native_source_after_guard_expires(GameTestHelper h){
        Fixture f=new Fixture(h,false);Observation hit=f.observe(f.target);f.begin(()->{DisplacedNeedleEntity medium=f.first();Vec3 origin=medium.position();medium.tick();
            h.assertTrue(!f.target.isAlive()&&hit.incoming==1&&hit.posts==1&&hit.deaths==1,"ordinary target follows one native death decision");
            assertSource(h,hit.deathSource,f.p,medium,origin);h.assertFalse(HighPowerCombat.enabled(f.target),"ordinary hostile was not enrolled as a side effect");
            h.assertFalse(medium.isContactEnabled(),"post-death source classification survives spent mutable contact guard");SpatialPincerCorridor.cancelActive(f.p);});f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void moved_target_keeps_frozen_aim_and_cannot_reassign_to_other_hostile(GameTestHelper h){
        Fixture f=new Fixture(h,true);BigDecimal before=hp(f.target);f.begin(()->{Vec3 frozen=f.target.getBoundingBox().getCenter();
            f.target.setPos(f.target.position().add(0,0,3));Zombie other=f.mob(h.absoluteVec(new Vec3(10.5,5,12.5)),true);BigDecimal otherBefore=hp(other);
            f.first().tick();eq(h,hp(f.target),before,"moving out of fixed path makes A miss");eq(h,hp(other),otherBefore,"replacement hostile is a blocker, never the assigned target");
            other.discard(); // Restore actual visibility and the original B route; the retired A share remains lost.
            h.assertTrue(h.getLevel().clip(new ClipContext(f.p.getEyePosition(),f.target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,f.p)).getType()==net.minecraft.world.phys.HitResult.Type.MISS,"moved original retains actual owner LOS after first blocker departs");
            f.post(4,()->{DisplacedNeedleEntity second=f.spawns.media.get(1);Vec3 expected=frozen.subtract(f.originB).normalize().scale(1.6);
                h.assertTrue(f.spawns.origins.get(1).equals(f.originB),"B retains its original emitter");
                h.assertTrue(second.getOwner()==f.p&&f.spawns.initialMotions.get(1).equals(expected),"B's actual launch velocity aims at the frozen old endpoint despite target movement");
                eq(h,hp(f.target),before,"later emitter does not follow moved target");eq(h,hp(other),otherBefore,"later emitter cannot acquire the replacement body");SpatialPincerCorridor.cancelActive(f.p);});
        });f.finish(17);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=24)
    public static void new_block_on_actual_first_segment_stops_native_contact(GameTestHelper h){
        Fixture f=new Fixture(h,true);BigDecimal before=hp(f.target);Observation hit=f.observe(f.target);f.begin(()->{
            f.block(BlockPos.containing(f.originA.add(.7,0,0)),Blocks.STONE.defaultBlockState());f.first().tick();
            eq(h,hp(f.target),before,"native first solid prevents target damage");h.assertValueEqual(hit.incoming,0,"no body hurt behind first block");
            h.assertTrue(f.first().inGround||!f.first().isContactEnabled(),"native block collision embeds or retires the actual original");SpatialPincerCorridor.cancelActive(f.p);});f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=24)
    public static void entity_inside_mark_origin_refuses_before_first_publication(GameTestHelper h){
        Fixture f=new Fixture(h,true);double cp=f.cp();f.ready(()->{f.mob(f.surfaceA,false);f.aim(f.target.getBoundingBox().getCenter());
            h.assertFalse(f.tap(),"new native occupant prevents publication at saved A");h.assertTrue(f.spawns.media.isEmpty()&&f.needles()==3,"occupied origin creates no escrowed medium");near(h,f.cp(),cp,"origin clearance precedes payment");});f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=24)
    public static void lost_saved_surface_or_eye_line_of_sight_refuses_before_payment(GameTestHelper h){
        Fixture f=new Fixture(h,true);double cp=f.cp();f.ready(()->{f.block(BlockPos.containing(f.surfaceA.add(0,-.1,0)),Blocks.AIR.defaultBlockState());f.aim(f.target.getBoundingBox().getCenter());
            h.assertFalse(f.tap(),"saved exact support is revalidated instead of finding a replacement");h.assertTrue(f.spawns.media.isEmpty()&&f.needles()==3,"changed saved support emits nothing");near(h,f.cp(),cp,"changed point is free");});f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=50)
    public static void armor_protection_resistance_and_absorption_match_native_thrown_control(GameTestHelper h){defenseCase(h,true);}
    private static void defenseCase(GameTestHelper h,boolean exact){
        Fixture f=new Fixture(h,exact);Zombie control=f.mob(f.target.position().add(5,0,0),false);defenses(h,f.target);defenses(h,control);
        f.hook(new Discount(f.target));Observation hit=f.observe(f.target),ordinary=f.observe(control);
        f.begin(()->{DisplacedNeedleEntity medium=f.first();Vec3 origin=medium.position(),nativeOrigin=origin.add(control.position().subtract(f.target.position()));BigDecimal before=hp(f.target);
            h.assertTrue(control.hurt(new DamageSource(f.p.damageSources().thrown(medium,f.p).typeHolder(),medium,f.p,nativeOrigin),5),"paired ordinary native THROWN control is accepted");medium.tick();
            h.assertTrue(hit.posts==1&&ordinary.posts==1&&hit.armor>0&&hit.effects>0&&hit.enchantments>0&&hit.absorption==.5F,"all four native reducers execute on the corridor contact");
            h.assertTrue(hit.armor==ordinary.armor&&hit.effects==ordinary.effects&&hit.enchantments==ordinary.enchantments&&hit.absorption==ordinary.absorption&&Math.abs(hit.damage-ordinary.damage)<.00001,"observed native defenses match independent THROWN control");
            if(exact){BigDecimal loss=DamageMitigation.mitigate(discountedFirstShare(f),new DefenseProfile(BigDecimal.valueOf(f.target.getArmorValue()),new BigDecimal(Float.toString((float)f.target.getAttributeValue(Attributes.ARMOR_TOUGHNESS))),1,4)).subtract(n(".5"));eq(h,hp(f.target),before.subtract(loss),"exact HP uses the native defenses after the independently computed double-hook ratio");}
            else near(h,f.target.getHealth(),control.getHealth(),"ordinary target HP matches the native control");
            h.assertTrue(f.target.getAbsorptionAmount()==0&&control.getAbsorptionAmount()==0,"actual pools consume once");SpatialPincerCorridor.cancelActive(f.p);
        });h.runAfterDelay(17,()->{f.verifyPosts();f.close();if(exact)defenseCase(h,false);else h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void native_shield_direction_uses_needle_segment_origin_for_exact_and_ordinary_targets(GameTestHelper h){shieldCase(h,0);}
    private static void shieldCase(GameTestHelper h,int mode){
        Fixture f=new Fixture(h,mode<2);Zombie control=f.mob(f.target.position().add(5,0,0),false);float yaw=mode%2==0?45:225;
        shield(f.target,yaw);shield(control,yaw);f.hook(new Discount(f.target));Observation hit=f.observe(f.target),ordinary=f.observe(control);
        f.begin(()->{face(f.target,yaw);face(control,yaw);h.assertTrue(f.target.isBlocking()&&control.isBlocking(),"real preparation elapsed native shield raise delay");
            DisplacedNeedleEntity medium=f.first();Vec3 origin=medium.position();BigDecimal before=hp(f.target);boolean blocked=mode%2==0;
            h.assertTrue(f.target.getLookAngle().dot(f.p.position().subtract(f.target.position()))*f.target.getLookAngle().dot(origin.subtract(f.target.position()))<0,"caster and actual emitter are on opposite shield hemispheres");
            Vec3 nativeOrigin=origin.add(control.position().subtract(f.target.position()));
            boolean accepted=control.hurt(new DamageSource(f.p.damageSources().thrown(medium,f.p).typeHolder(),medium,f.p,nativeOrigin),5);medium.tick();
            h.assertTrue(accepted!=blocked&&hit.blocked==blocked&&ordinary.blocked==blocked,"actual native shield hemisphere agrees with paired control");
            eq(h,hp(f.target),blocked?before:before.subtract(mode<2?discountedFirstShare(f):n("5")),"native shield decision controls the one exact hook-ratio or native-float HP debit");
            h.assertTrue(hit.blockedDamage==ordinary.blockedDamage&&hit.damage==ordinary.damage,"native blocked and residual amounts match control");
            assertSource(h,hit.source,f.p,medium,origin);SpatialPincerCorridor.cancelActive(f.p);
        });h.runAfterDelay(17,()->{f.verifyPosts();f.close();if(mode<3)shieldCase(h,mode+1);else h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=50)
    public static void existing_native_stronger_iframes_do_not_respend_real_absorption(GameTestHelper h){iframeCase(h,true);}
    private static void iframeCase(GameTestHelper h,boolean exact){
        Fixture f=new Fixture(h,exact);f.target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,2));f.hook(new Discount(f.target));
        f.begin(()->{f.target.setAbsorptionAmount(10);BigDecimal before=hp(f.target);
            h.assertTrue(f.target.hurt(f.p.damageSources().playerAttack(f.p),6),"ordinary native hit creates a stronger cooldown than the discounted share");
            h.assertTrue(f.target.getAbsorptionAmount()==4,"ordinary hit spends six absorption exactly once");f.first().tick();
            eq(h,hp(f.target),before,"weaker corridor share cannot bypass native iframe despite double-ratio rounding");
            h.assertTrue(f.target.getAbsorptionAmount()==4&&SpatialPincerCorridor.receipt(f.p).hurts()==0,"native iframe spends no second pool amount");SpatialPincerCorridor.cancelActive(f.p);
        });h.runAfterDelay(17,()->{f.verifyPosts();f.close();if(exact)iframeCase(h,false);else h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=220)
    public static void skill_callbacks_revalidate_source_target_owner_and_geometry_before_native_hurt(GameTestHelper h){mutationCase(h,0,0,true);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=220)
    public static void incoming_callbacks_revalidate_source_target_owner_and_geometry_before_commit(GameTestHelper h){mutationCase(h,1,0,true);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=220)
    public static void pre_callbacks_revalidate_source_target_owner_and_geometry_before_commit(GameTestHelper h){mutationCase(h,2,0,true);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=220)
    public static void final_absorption_callbacks_revalidate_source_target_owner_and_geometry_before_hp(GameTestHelper h){mutationCase(h,3,0,true);}
    private static void mutationCase(GameTestHelper h,int stage,int mutation,boolean exact){
        Fixture f=new Fixture(h,exact);f.hook(new Discount(f.target));Mutation gate=new Mutation(f,stage,mutation);f.hook(gate);Observation hit=f.observe(f.target);
        if(stage==3){f.target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,0));f.target.setAbsorptionAmount(2);}
        f.begin(()->{BigDecimal before=hp(f.target);f.first().tick();h.assertTrue(gate.called,"real native callback was reached: stage="+stage+", mutation="+mutation+", exact="+exact);
            eq(h,hp(f.target),before,"late source, target, owner or collision mutation cannot preserve a damage transaction");
            h.assertTrue(hit.damage==0&&!f.first().isContactEnabled(),"veto retires the one contact without positive native HP debit");
            if(stage==3)h.assertTrue(gate.afterPre&&gate.reduction==2&&f.target.getAbsorptionAmount()==2,"post-Pre mutation preserves the original native absorption pool");
            SpatialPincerCorridor.cancelActive(f.p);
        });h.runAfterDelay(17,()->{f.verifyPosts();f.close();if(exact)mutationCase(h,stage,mutation,false);else if(mutation<4)mutationCase(h,stage,mutation+1,true);else h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=50)
    public static void final_absorption_amount_rewrite_is_vetoed_before_native_hp_or_pool_write(GameTestHelper h){absorptionRewrite(h,true);}
    private static void absorptionRewrite(GameTestHelper h,boolean exact){
        Fixture f=new Fixture(h,exact);f.hook(new Discount(f.target));f.target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,0));f.target.setAbsorptionAmount(2);boolean[] called={false};
        f.hook(new Object(){@SubscribeEvent(priority=EventPriority.LOWEST) public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==f.target)e.addReductionModifier(DamageContainer.Reduction.ABSORPTION,(container,amount)->{called[0]=true;container.setNewDamage(container.getNewDamage()+1);return amount;});}});
        f.begin(()->{BigDecimal before=hp(f.target);f.first().tick();h.assertTrue(called[0],"late modifier runs after the closed source's absorption observer");
            eq(h,hp(f.target),before,"positive damage rewrite is refused before native HP subtraction");
            h.assertTrue(f.target.getAbsorptionAmount()==2&&!f.first().isContactEnabled(),"late veto preserves pool and consumes the one impact authority");SpatialPincerCorridor.cancelActive(f.p);
        });h.runAfterDelay(17,()->{f.verifyPosts();f.close();if(exact)absorptionRewrite(h,false);else h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=50)
    public static void skill_zero_and_share_amplification_cannot_redistribute_reserved_damage(GameTestHelper h){amountCase(h,false);}
    private static void amountCase(GameTestHelper h,boolean amplified){
        Fixture f=new Fixture(h,true);boolean[] called={false};f.hook(new Object(){@SubscribeEvent public void skill(SkillAttackEvent e){if(e.target==f.target&&e.skill.equals(SKILL)){called[0]=true;e.amount=amplified?e.amount*2:0;}}});
        f.begin(()->{BigDecimal before=hp(f.target);f.first().tick();h.assertTrue(called[0],"actual native contact reaches skill amount hook");
            eq(h,hp(f.target),before,"zero or amplified share cannot debit the fixed cast budget");
            h.assertTrue(SpatialPincerCorridor.receipt(f.p).paidNeedles()==1&&!f.first().isContactEnabled(),"refused contact spends its one published attempt without pooling");SpatialPincerCorridor.cancelActive(f.p);
        });h.runAfterDelay(17,()->{f.verifyPosts();f.close();if(!amplified)amountCase(h,true);else h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void native_projectile_impact_cancel_and_reentry_never_issue_a_second_permit(GameTestHelper h){
        Fixture f=new Fixture(h,true);BigDecimal before=hp(f.target);Observation hit=f.observe(f.target);int[] calls={0};
        f.hook(new Object(){@SubscribeEvent public void impact(ProjectileImpactEvent e){if(e.getEntity() instanceof DisplacedNeedleEntity medium&&medium.getOwner()==f.p){calls[0]++;medium.tick();e.setCanceled(true);}}});
        f.begin(()->{f.first().tick();eq(h,hp(f.target),before,"canceled first impact cannot damage");
            h.assertTrue(calls[0]==1&&hit.incoming==0&&!f.first().isContactEnabled(),"native impact callback cannot reenter or recover a consumed claim");SpatialPincerCorridor.cancelActive(f.p);});f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=32)
    public static void native_logout_reload_cancels_queue_without_refill_or_custody_reset(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.begin(()->{DisplacedNeedleEntity medium=f.first();var identity=medium.identity();double cp=f.cp();CompoundTag fences=f.p.getPersistentData().getCompound(FENCES).copy();
            var ledger=CorridorNeedleRegistry.get(f.p.serverLevel()).snapshots(f.p.getUUID());AbilityStorage.save(f.p);CompoundTag saved=f.p.saveWithoutId(new CompoundTag());
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));h.assertFalse(SpatialPincerCorridor.active(f.p)||medium.isContactEnabled(),"native logout retires queued publications and current attack authority");
            f.p.load(saved);AbilityStorage.remove(f.p);AbilityStorage.get(f.p);f.p.setOnGround(true);f.p.setDeltaMovement(0,-.0784,0);f.p.connection.resetPosition();f.wire(5);
            h.assertTrue(f.p.getPersistentData().getCompound(FENCES).equals(fences),"native reload retains remaining request, cast and cooldown fences");near(h,f.cp(),cp,"reload neither refunds nor refills CP");
            h.assertTrue(CorridorNeedleRegistry.get(f.p.serverLevel()).snapshots(f.p.getUUID()).equals(ledger)&&identity.equals(medium.identity())&&CorridorNeedleRegistry.authentic(f.p,medium),"native reload keeps exact existing physical custody");
            f.post(9,()->h.assertTrue(f.spawns.media.size()==1&&!SpatialPincerCorridor.active(f.p)&&f.needles()==2,"no queued slot revives after reload"));
        });f.finish(22);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=64,skyAccess=true)
    public static void real_flight_authority_expires_at_forty_observed_ticks_without_destroying_original(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.begin(()->{DisplacedNeedleEntity medium=f.first();
            // GameTest's default barrier roof caused a real tick-3 block hit. This one vertical-flight test explicitly needs native sky access.
            // At vY=1.2, native float .99 drag and .05 gravity leave +5.2375m displacement after40 ticks and <21m actual path.
            h.assertTrue(h.getLevel().clip(new ClipContext(medium.position(),medium.position().add(0,16,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,medium)).getType()==net.minecraft.world.phys.HitResult.Type.MISS,"declared vertical control has actual unobstructed native sky");
            medium.setDeltaMovement(0,1.2,0);for(int i=0;i<100;i++)SpatialPincerCorridor.tick(f.p);
            h.assertTrue(medium.isContactEnabled(),"duplicate controller calls do not consume flight age");
            FlightEvidence evidence=new FlightEvidence(f,medium);f.hook(evidence);
            f.post(39,()->{h.assertTrue(medium.isContactEnabled()&&!medium.isRemoved()&&!medium.inGround,"original remains eligible through its 39th observed flight tick; "+evidence.samples);
                h.assertTrue(evidence.traveled<21&&medium.position().y>f.originA.y+6,"observed native trajectory remains clear and far below the cumulative64m budget");});
            f.post(40,()->{h.assertFalse(medium.isContactEnabled(),"40th actual tick retires attack authority");h.assertTrue(!medium.isRemoved()&&CorridorNeedleRegistry.authentic(f.p,medium)&&medium.mediumSnapshot().getCount()==1,"expiry retains real recoverable original and exact custody");
                h.assertFalse(medium.beginFlight(medium.identity()),"expiry cannot be rearmed");SpatialPincerCorridor.cancelActive(f.p);});
        });f.finish(53);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=50,skyAccess=true)
    public static void cumulative_sixty_four_boundary_uses_current_caster_distance_and_real_contact_segment(GameTestHelper h){rangeCase(h,false);}
    private static void rangeCase(GameTestHelper h,boolean outside){
        Fixture f=new Fixture(h,true);Observation hit=f.observe(f.target);BigDecimal before=hp(f.target),share=firstShare(f);
        f.begin(()->{DisplacedNeedleEntity medium=f.first();Vec3 origin=medium.position();
            Vec3 nativeContact=f.target.getBoundingBox().inflate((double).3F).clip(origin,origin.add(medium.getDeltaMovement())).orElseThrow();
            double segment=origin.distanceTo(nativeContact),distance=(outside?64.01:64)-segment;
            double feet=f.source.y+32,vertical=feet+f.p.getEyeHeight()-origin.y;
            double horizontal=Math.sqrt(distance*distance-vertical*vertical);double x=origin.x+horizontal;
            f.p.setPos(x,feet,origin.z);
            // Native world-coordinate additions round independently. Prove the adjacent representable x straddles the literal boundary.
            for(int rounding=0;rounding<32&&!outside&&f.p.getEyePosition().distanceTo(origin)+segment>64;rounding++){x=Math.nextDown(x);f.p.setPos(x,feet,origin.z);}
            for(int rounding=0;rounding<32&&!outside;rounding++){
                double outward=Math.nextUp(x);Vec3 adjacent=new Vec3(outward,f.p.getEyePosition().y,origin.z);
                if(adjacent.distanceTo(origin)+segment>64)break;x=outward;f.p.setPos(x,feet,origin.z);
            }
            double actual=f.p.getEyePosition().distanceTo(origin)+segment;
            double adjacent=new Vec3(Math.nextUp(x),f.p.getEyePosition().y,origin.z).distanceTo(origin)+segment;
            h.assertTrue(outside?actual>64:actual<=64&&adjacent>64,"adjacent representable native source coordinates straddle64, or source is explicitly outside: current="+actual+", adjacent="+adjacent);
            h.assertTrue(SpatialPincerCorridorRules.path(64,0)&&!SpatialPincerCorridorRules.path(Math.nextUp(64D),0),"literal scalar boundary includes exactly 64 and excludes its next representable successor");
            BlockPos floor=BlockPos.containing(f.p.position().add(0,-1,0));for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)f.block(floor.offset(dx,0,dz),Blocks.STONE.defaultBlockState());
            f.p.setOnGround(true);f.p.setDeltaMovement(0,-.0784,0);f.p.connection.resetPosition();
            for(int step=0;step<=64;step++)f.hold(new ChunkPos(BlockPos.containing(f.p.getEyePosition().lerp(f.target.getBoundingBox().getCenter(),step/64D))));
            h.assertTrue(SafeLandingProbeWorld.sourceRefusal(f.p).isEmpty(),"range control retains a genuinely supported full caster body");
            h.assertTrue(f.p.getEyePosition().distanceTo(f.target.getBoundingBox().getCenter())<64,"target-facing source placement leaves current target center within64");
            h.assertTrue(h.getLevel().clip(new ClipContext(f.p.getEyePosition(),f.target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,f.p)).getType()==net.minecraft.world.phys.HitResult.Type.MISS,"declared sky access and shallower source elevation leave real current owner LOS clear of the full support floor");
            medium.tick();
            eq(h,hp(f.target),outside?before:before.subtract(share),"real native contact obeys current caster-to-original-emitter plus actual flown path");
            h.assertValueEqual(hit.incoming,outside?0:1,"out-of-budget contact is stopped before native body hurt");SpatialPincerCorridor.cancelActive(f.p);
        });h.runAfterDelay(17,()->{f.verifyPosts();f.close();if(!outside)rangeCase(h,true);else h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=24)
    public static void missed_tick_timestamp_observation_never_catches_up_queued_shots_or_flight_age(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.begin(()->{double cp=f.cp();DisplacedNeedleEntity medium=f.first();
            try{var contexts=SpatialPincerCorridor.class.getDeclaredField("CONTEXTS");contexts.setAccessible(true);
                Object context=((Map<?,?>)contexts.get(null)).get(f.p.getUUID());h.assertTrue(context!=null,"fixture uses actual admitted live cast");
                var stamp=context.getClass().getDeclaredField("lastTick");stamp.setAccessible(true);stamp.setLong(context,Integer.toUnsignedLong(f.p.server.getTickCount())-1000);
            }catch(ReflectiveOperationException failure){throw new AssertionError("Pinned missed-observation timestamp fixture unavailable",failure);}
            SpatialPincerCorridor.tick(f.p);for(int i=0;i<100;i++)SpatialPincerCorridor.tick(f.p);
            h.assertTrue(f.spawns.media.size()==1&&medium.isContactEnabled(),"a stale timestamp causes one observed advance, never 1000 ticks of scheduled work");
            near(h,f.cp(),cp,"missed observations cannot catch up CP charges");h.assertValueEqual(f.needles(),2,"missed observations cannot synthesize B or C");SpatialPincerCorridor.cancelActive(f.p);
        });f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=24)
    public static void first_nonhostile_body_blocks_target_without_receiving_damage(GameTestHelper h){
        Fixture f=new Fixture(h,true);BigDecimal before=hp(f.target);Observation hit=f.observe(f.target);f.begin(()->{
            var sheep=EntityType.SHEEP.create(h.getLevel());sheep.setNoAi(true);sheep.setNoGravity(true);sheep.setPos(f.originA.add(.85,-1.2,0));
            h.assertTrue(h.getLevel().addFreshEntity(sheep),"declared native nonhostile first blocker is resident");f.entities.add(sheep);float health=sheep.getHealth();f.first().tick();
            eq(h,hp(f.target),before,"unselected native first body blocks selected hostile");h.assertTrue(sheep.getHealth()==health&&hit.incoming==0,"nonhostile blocker never receives corridor body damage");
            h.assertFalse(f.first().isContactEnabled(),"first-body miss consumes the one real contact authority");SpatialPincerCorridor.cancelActive(f.p);
        });f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=24)
    public static void late_eye_to_emitter_obstruction_prevents_initial_publication(GameTestHelper h){
        Fixture f=new Fixture(h,true);double cp=f.cp();f.ready(()->{Vec3 middle=f.p.getEyePosition().add(f.originA).scale(.5);
            f.block(BlockPos.containing(middle),Blocks.STONE.defaultBlockState());f.aim(f.target.getBoundingBox().getCenter());
            h.assertFalse(f.tap(),"new obstruction of actual caster-to-emitter segment is revalidated");
            h.assertTrue(f.spawns.media.isEmpty()&&f.needles()==3,"blocked source segment cannot publish A");near(h,f.cp(),cp,"source LOS failure precedes all CP payment");});f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=16)
    public static void near_bottom_first_floor_succeeds_before_unused_ray_tail_leaves_build_height(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.aim(f.pointA);double cp=f.cp();Vec3 eye=f.p.getEyePosition();
        h.assertTrue(eye.add(f.p.getLookAngle().scale(64)).y<h.getLevel().getMinBuildHeight(),"unchanged lower-Y fixture puts the unused 64m ray tail below the world");
        Vec3 expected=f.nativeSurface();var assessed=SpatialPincerCorridorWorld.capturePoint(f.p);
        h.assertTrue(assessed.valid()&&assessed.point().destination().equals(expected),"read-only assessment accepts the actual first UP floor before the invalid unused tail: "+assessed.reason());
        h.assertTrue(f.tap()&&SpatialPincerCorridor.preparedPoints(f.p)==1,"real authenticated short release saves the same near-bottom floor");
        h.assertTrue(SpatialPincerCorridor.receipt(f.p).arrival().equals(expected.add(0,1.2,0)),"saved emitter uses actual first contact rather than full-ray endpoint");
        near(h,f.cp(),cp,"first-floor preparation remains free");h.assertTrue(f.needles()==3&&f.spawns.media.isEmpty(),"valid first floor escrows nothing");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=16)
    public static void no_first_floor_before_world_bottom_refuses_at_actual_outside_cell(GameTestHelper h){
        Fixture f=new Fixture(h,true);double cp=f.cp();int x=BlockPos.containing(f.source).getX(),z=BlockPos.containing(f.source).getZ();
        // Keep the caster's own full support at z intact. Clear only the steep ray's later columns, including native bedrock.
        for(int y=h.getLevel().getMinBuildHeight();y<(int)f.source.y;y++)for(int dz=1;dz<=9;dz++)f.block(new BlockPos(x,y,z+dz),Blocks.AIR.defaultBlockState());
        f.aim(f.source.add(0,-3,3));h.assertTrue(SafeLandingProbeWorld.sourceRefusal(f.p).isEmpty(),"source remains genuinely supported beside the cleared ray corridor");
        h.assertTrue(f.p.getEyePosition().add(f.p.getLookAngle().scale(64)).y<h.getLevel().getMinBuildHeight(),"actual no-hit traversal reaches below build height");
        var assessed=SpatialPincerCorridorWorld.capturePoint(f.p);
        h.assertTrue(!assessed.valid()&&assessed.reason().equals("outside_build_height"),"no-first-hit traversal refuses its actually visited below-world cell: "+assessed.reason());
        h.assertFalse(f.tap(),"release cannot manufacture a floor beyond world bounds");
        near(h,f.cp(),cp,"outside-cell refusal is free");h.assertTrue(f.needles()==3&&f.spawns.media.isEmpty()&&!SpatialPincerCorridor.marked(f.p),"outside-world traversal grants no mark or medium");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=16)
    public static void nearer_native_wall_takes_precedence_over_floor_and_invalid_unused_tail(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.aim(f.pointA);Vec3 floor=f.nativeSurface();double cp=f.cp();
        Vec3 middle=f.p.getEyePosition().add(floor).scale(.5);BlockPos wall=BlockPos.containing(middle);
        for(int dy=-1;dy<=1;dy++)f.block(wall.above(dy),Blocks.STONE.defaultBlockState());
        var nativeHit=h.getLevel().clip(new ClipContext(f.p.getEyePosition(),f.p.getEyePosition().add(f.p.getLookAngle().scale(64)),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,f.p));
        h.assertTrue(nativeHit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&nativeHit.getDirection()!=net.minecraft.core.Direction.UP
                &&nativeHit.getLocation().distanceToSqr(f.p.getEyePosition())<floor.distanceToSqr(f.p.getEyePosition()),"independent native ray encounters the nearer vertical wall first");
        var assessed=SpatialPincerCorridorWorld.capturePoint(f.p);
        h.assertTrue(!assessed.valid()&&assessed.reason().equals("first_surface_not_up"),"first wall controls refusal before the floor or unused below-world tail: "+assessed.reason());
        h.assertFalse(f.tap(),"controller cannot search behind first wall");near(h,f.cp(),cp,"first-wall refusal remains free");h.assertTrue(f.needles()==3&&f.spawns.media.isEmpty(),"first wall grants no emission");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=24)
    public static void unloaded_chunk_before_first_hit_is_refused_without_loading_the_chunk(GameTestHelper h){
        // This is a direct World assessment, not an authenticated-controller fixture. Only the source chunk is deliberately loaded.
        Vec3 source=new Vec3(25000008.5,h.getLevel().getMinBuildHeight()+8,25000008.5);ChunkPos center=new ChunkPos(BlockPos.containing(source));
        h.getLevel().getChunk(center.x,center.z);Map<BlockPos,BlockState> changed=new LinkedHashMap<>();
        var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"SCPNoLoad"),false);
        ServerPlayer p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());Connection connection=new Connection(PacketFlow.SERVERBOUND);EmbeddedChannel channel=new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,p,cookie);
        try{
            BlockPos floor=BlockPos.containing(source.add(0,-1,0));for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){BlockPos pos=floor.offset(dx,0,dz);changed.put(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());}
            p.setPose(Pose.STANDING);p.setShiftKeyDown(false);p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);p.setPos(source);p.connection.resetPosition();
            h.assertTrue(!p.isAddedToLevel()&&SafeLandingProbeWorld.sourceRefusal(p).isEmpty(),"unregistered direct-assessment source has real loaded body and full floor support");
            ChunkPos missing=null;int dx=0,dz=0;
            search:for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int step=1;step<=4;step++){
                ChunkPos candidate=new ChunkPos(center.x+direction[0]*step,center.z+direction[1]*step);
                if(h.getLevel().getChunkSource().getChunkNow(candidate.x,candidate.z)==null){missing=candidate;dx=direction[0];dz=direction[1];break search;}}
            h.assertTrue(missing!=null,"declared 64m horizontal path contains an actually absent downstream chunk");
            p.setYRot((float)Math.toDegrees(Math.atan2(-dx,dz)));p.setXRot(0);CompoundTag persistent=p.getPersistentData().copy();
            var assessed=SpatialPincerCorridorWorld.capturePoint(p);
            h.assertTrue(!assessed.valid()&&assessed.reason().equals("unloaded_chunk"),"actual traversal refuses absent chunk before any first hit: "+assessed.reason());
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(missing.x,missing.z)==null,"World assessment never loads the missing chunk");
            h.assertTrue(p.getPersistentData().equals(persistent)&&p.position().equals(source)&&SpatialPincerCorridor.receipt(p)==null,"direct assessment leaves movement, resources and controller authority untouched");h.succeed();
        }finally{changed.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));p.discard();channel.finishAndReleaseAll();}
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=16)
    public static void world_border_before_first_hit_refuses_actual_prefix_and_restores_settings(GameTestHelper h){
        Fixture f=new Fixture(h,true);f.aim(f.pointA);Vec3 expected=f.nativeSurface();double cp=f.cp();
        var border=h.getLevel().getWorldBorder();var original=border.createSettings();
        try{
            border.setCenter(f.source.x,f.source.z);border.setSize(8);
            h.assertTrue(SafeLandingProbeWorld.sourceRefusal(f.p).isEmpty(),"caster body and full floor remain inside the temporary border");
            h.assertFalse(border.isWithinBounds(BlockPos.containing(expected)),"known first floor lies beyond an earlier border crossing");
            var assessed=SpatialPincerCorridorWorld.capturePoint(f.p);
            h.assertTrue(!assessed.valid()&&assessed.reason().equals("outside_world_border"),"visited border crossing is refused before the known first floor: "+assessed.reason());
            h.assertFalse(f.tap(),"actual release cannot mark through the border");near(h,f.cp(),cp,"border refusal precedes all payment");
            h.assertTrue(f.needles()==3&&f.spawns.media.isEmpty()&&!SpatialPincerCorridor.marked(f.p),"border refusal grants no authority or media");
        }finally{border.applySettings(original);}
        var restored=SpatialPincerCorridorWorld.capturePoint(f.p);
        h.assertTrue(restored.valid()&&restored.point().destination().equals(expected),"same callback restores original border and the identical first floor is valid again");h.succeed();
    }

    private static void face(LivingEntity e,float yaw){e.setYRot(yaw);e.setYHeadRot(yaw);e.yBodyRot=yaw;e.setXRot(0);}
    private static void shield(LivingEntity e,float yaw){face(e,yaw);e.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));e.startUsingItem(InteractionHand.OFF_HAND);}
    private static void defenses(GameTestHelper h,LivingEntity e){var helmet=new ItemStack(Items.DIAMOND_HELMET);helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),4);helmet.set(DataComponents.UNBREAKABLE,new Unbreakable(true));e.setItemSlot(EquipmentSlot.HEAD,helmet);e.getAttribute(Attributes.ARMOR).setBaseValue(5);e.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,200,0));e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,0));e.setAbsorptionAmount(.5F);}
    private static void assertSource(GameTestHelper h,DamageSource source,ServerPlayer p,DisplacedNeedleEntity medium,Vec3 origin){
        h.assertTrue(source!=null&&source.is(DamageTypes.THROWN)&&source.is(DamageTypeTags.IS_PROJECTILE)&&source.getEntity()==p
                &&source.getDirectEntity()==medium&&origin.equals(source.getSourcePosition())&&origin.equals(source.sourcePositionRaw()),"native THROWN source retains actual cross needle and incoming origin");
        h.assertFalse(source.is(DamageTypeTags.BYPASSES_ARMOR)||source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)||source.is(DamageTypeTags.BYPASSES_EFFECTS)
                ||source.is(DamageTypeTags.BYPASSES_RESISTANCE)||source.is(DamageTypeTags.BYPASSES_SHIELD)||source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                ||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)||source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypeTags.IS_EXPLOSION),"corridor needle adds no defense bypass or environmental damage family");
    }
    public static final class Discount{
        final LivingEntity target;Discount(LivingEntity target){this.target=target;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void skill(SkillAttackEvent e){if(e.target==target&&e.skill.equals(SKILL))e.amount=5;}
    }
    public static final class Mutation{
        final Fixture f;final int stage,mutation;boolean called,sawPre,afterPre;float reduction;
        Mutation(Fixture f,int stage,int mutation){this.f=f;this.stage=stage;this.mutation=mutation;}
        void change(){if(called)return;called=true;
            if(mutation==0)f.p.setPos(f.p.position().add(.1,0,0));
            else if(mutation==1)f.target.setPos(f.target.position().add(0,4,0));
            else if(mutation==2)f.first().setOwner(f.target);
            else if(mutation==3)f.block(BlockPos.containing(f.originA.add(.7,0,0)),Blocks.STONE.defaultBlockState());
            else f.p.noPhysics=true;
        }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent e){if(stage==0&&e.target==f.target&&e.skill.equals(SKILL))change();}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()!=f.target)return;if(stage==1)change();if(stage==3)e.addReductionModifier(DamageContainer.Reduction.ABSORPTION,(container,amount)->{afterPre=sawPre;reduction=amount;change();return amount;});}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre e){if(e.getEntity()!=f.target)return;sawPre=true;if(stage==2)change();}
    }
    public static final class Observation{
        final LivingEntity target;DamageSource source,deathSource;int incoming,deaths,posts;boolean blocked;float damage,armor,effects,enchantments,absorption,blockedDamage;
        Observation(LivingEntity target){this.target=target;}
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target){incoming++;source=e.getSource();}}
        @SubscribeEvent public void death(LivingDeathEvent e){if(e.getEntity()==target){deaths++;deathSource=e.getSource();}}
        @SubscribeEvent public void shield(LivingShieldBlockEvent e){if(e.getEntity()==target){blocked=e.getBlocked();blockedDamage=e.getBlockedDamage();}}
        @SubscribeEvent public void post(LivingDamageEvent.Post e){if(e.getEntity()!=target)return;posts++;damage=e.getNewDamage();armor=e.getReduction(DamageContainer.Reduction.ARMOR);effects=e.getReduction(DamageContainer.Reduction.MOB_EFFECTS);enchantments=e.getReduction(DamageContainer.Reduction.ENCHANTMENTS);absorption=e.getReduction(DamageContainer.Reduction.ABSORPTION);}
    }
    public static final class Spawns{
        final Fixture f;final List<DisplacedNeedleEntity> media=new ArrayList<>();final List<Integer> ticks=new ArrayList<>(),slots=new ArrayList<>();final List<Vec3> origins=new ArrayList<>(),initialMotions=new ArrayList<>();
        int inspected;double before;boolean exactPayments=true;
        Spawns(Fixture f){this.f=f;}
        @SubscribeEvent public void join(EntityJoinLevelEvent e){
            if(!(e.getEntity() instanceof DisplacedNeedleEntity m)||!m.isCorridorNeedle()||m.getOwner()!=f.p)return;
            media.add(m);f.entities.add(m);ticks.add(f.p.server.getTickCount());origins.add(m.position());before=f.cp();
            var snapshot=CorridorNeedleRegistry.get(f.p.serverLevel()).snapshots(f.p.getUUID());int slot=-1;
            for(int index=0;index<snapshot.size();index++)if(m.getUUID().equals(snapshot.get(index).entityId()))slot=index;
            f.h.assertTrue(slot>=0,"native pending entity already owns one exact physical custody slot");slots.add(slot);
            f.h.assertFalse(m.isAdmitted(),"native join exposes only inert pending entity before CP commit");
            f.h.assertValueEqual(f.p.getOffhandItem().getCount(),f.initialStock-media.size(),"one actual item is escrowed before each native join callback");
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void pre(ServerTickEvent.Pre e){
            if(e.getServer()!=f.p.server||media.size()==inspected)return;
            payment();
        }
        void firstPayment(){payment();}
        private void payment(){double total=SpatialPincerCorridorRules.cpCost(HighPowerGrowth.values(f.p).position()),share=total/f.volleyNeedles,allocated=0;
            for(int i=0;i<f.volleyNeedles-1;i++)allocated+=share;
            if(!media.getLast().isAdmitted()){inspected=media.size();return;}
            double nominal=slots.getLast()==f.volleyNeedles-1?total-allocated:share;double after=f.cp();
            exactPayments&=Double.doubleToLongBits(after)==Double.doubleToLongBits(before-nominal);
            f.budget.debit(before,after,nominal);initialMotions.add(media.getLast().getDeltaMovement());inspected=media.size();}
    }
    /** Observes the two sides of actual native player recovery callbacks, separately from native publication debits. */
    public static final class BudgetLedger{
        final Fixture f;final BigDecimal initial;BigDecimal recovered=BigDecimal.ZERO,debited=BigDecimal.ZERO;
        int debitCount,positiveRecoveries;double before,maximum,recovery;long previous,now;boolean hadPrevious;
        BudgetLedger(Fixture f){this.f=f;initial=BigDecimal.valueOf(f.cp());}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){if(event.getEntity()!=f.p)return;
            before=f.cp();var data=f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);hadPrevious=data.contains("last_tick");previous=data.getLong("last_tick");now=f.p.level().getGameTime();
            var growth=HighPowerGrowth.values(f.p);maximum=growth.cpMaximum();recovery=growth.cpRecovery()/20;
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void after(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){if(event.getEntity()!=f.p)return;
            double after=f.cp(),expected=hadPrevious&&previous==now?before:Math.min(maximum,before+recovery);
            f.h.assertTrue(Double.doubleToLongBits(after)==Double.doubleToLongBits(expected),"native recovery is exactly one capped growth.cpRecovery/20 addition per game tick: before="+before+", after="+after+", expected="+expected+", tick="+now);
            BigDecimal gain=BigDecimal.valueOf(after).subtract(BigDecimal.valueOf(before));recovered=recovered.add(gain);if(gain.signum()>0)positiveRecoveries++;
        }
        void debit(double before,double after,double nominal){f.h.assertTrue(Double.doubleToLongBits(after)==Double.doubleToLongBits(before-nominal),"native publication debits its own exact represented share: before="+before+", after="+after+", nominal="+nominal);
            debited=debited.add(BigDecimal.valueOf(before).subtract(BigDecimal.valueOf(after)));debitCount++;}
        void assertConserved(String why){eq(f.h,BigDecimal.valueOf(f.cp()),initial.add(recovered).subtract(debited),why+"; recovered="+recovered+", debited="+debited);}
    }
    public static final class FlightEvidence{
        final Fixture f;final DisplacedNeedleEntity medium;final List<String> samples=new ArrayList<>();boolean reported;Vec3 previous;double traveled;
        FlightEvidence(Fixture f,DisplacedNeedleEntity medium){this.f=f;this.medium=medium;previous=medium.position();}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post event){if(event.getServer()!=f.p.server||samples.size()>=42)return;
            traveled+=previous.distanceTo(medium.position());previous=medium.position();
            int elapsed=f.p.server.getTickCount()-f.started;var receipt=SpatialPincerCorridor.receipt(f.p);
            String sample="elapsed="+elapsed+", nativeTicks="+medium.tickCount+", pos="+medium.position()+", motion="+medium.getDeltaMovement()+", inGround="+medium.inGround+", contact="+medium.isContactEnabled()+", removed="+medium.isRemoved()+", authentic="+CorridorNeedleRegistry.authentic(f.p,medium)+", receipt="+receipt;
            samples.add(sample);if(!reported&&!medium.isContactEnabled()&&elapsed<40){reported=true;org.slf4j.LoggerFactory.getLogger("academy.spatial_pincer_fixture").warn("NEW341 premature flight observation: {}",samples);}
        }
    }
    public static final class PostCheck{
        final Fixture f;final int tick;final Runnable action;boolean ran;
        PostCheck(Fixture f,int delay,Runnable action){this.f=f;tick=f.p.server.getTickCount()+delay;this.action=action;}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post e){
            if(ran||e.getServer()!=f.p.server||e.getServer().getTickCount()!=tick)return;ran=true;
            try{action.run();}catch(Throwable failure){if(f.postFailure==null)f.postFailure=failure;}
        }
    }
    static final class Fixture implements AutoCloseable{
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final Zombie target;final Vec3 source,pointA,pointB,pointC;
        final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final List<PostCheck> checks=new ArrayList<>();
        final Map<BlockPos,BlockState> changed=new LinkedHashMap<>();final Set<ChunkPos> held=new LinkedHashSet<>();final UUID ticket=UUID.randomUUID();final Spawns spawns;final BudgetLedger budget;
        Vec3 originA,originB,originC,surfaceA,surfaceB,surfaceC;long inputs;int started,initialStock=3,volleyNeedles=3;Throwable postFailure;boolean closed,usesPreview;
        Fixture(GameTestHelper h,boolean exact){this(h,exact,0);}
        Fixture(GameTestHelper h,boolean exact,int offset){this(h,exact,offset,false);}
        Fixture(GameTestHelper h,boolean exact,int offset,boolean wide){
            this.h=h;source=h.absoluteVec(new Vec3(10.5+offset,5,6.5));pointA=h.absoluteVec(new Vec3(8.5+offset,5,wide?20.5:12.5));pointB=h.absoluteVec(new Vec3((wide?10.5:12.5)+offset,5,wide?20.5:12.5));pointC=h.absoluteVec(new Vec3((wide?12.5:14.5)+offset,5,wide?20.5:11.5));
            for(int x=4;x<=18;x++)for(int z=3;z<=(wide?64:17);z++)block(h.absolutePos(new BlockPos(x+offset,4,z)),Blocks.STONE.defaultBlockState());
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"SC"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);acknowledgeLogin();
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=false;p.getAbilities().instabuild=false;p.moveTo(source.x,source.y,source.z,0,0);
            p.setPose(Pose.STANDING);p.setShiftKeyDown(false);p.setNoGravity(false);p.fallDistance=0;p.setOnGround(true);
            p.move(MoverType.SELF,new Vec3(0,-.0784,0));p.setDeltaMovement(0,-.0784,0);p.connection.resetPosition();
            h.assertTrue(p.onGround()&&p.position().equals(source),"native downward floor collision establishes genuine safe source");
            h.assertTrue(HighPowerCombat.enroll(p,4),"declared real caster enrollment");var state=AbilityStorage.get(p);state.selectCategory(HighPowerCategories.TELEPORTER);state.setLevel(4);
            state.learn(SafeLandingProbe.ID);state.learn(DisplacedProjection.ID);state.learn(CrossSubstitution.ID);state.learn(ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,ID,state::learned),"native learned skill occupies mapped slot zero");HighPowerResources.initializeBudget(p);
            p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AcademyCraft.NEEDLE.get(),3));target=mob(h.absoluteVec(new Vec3(10.5+offset,5,wide?28.5:12.5)),exact);
            budget=new BudgetLedger(this);hook(budget);spawns=new Spawns(this);hook(spawns);hook(new HighPowerPhaseSidestepRuntimeTests.NativePlayerPump(p,h.testInfo.getTimeoutTicks()+5));
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Zombie mob(Vec3 at,boolean exact){Zombie m=EntityType.HUSK.create(h.getLevel());m.setNoAi(true);m.setNoGravity(true);m.setSilent(true);m.getAttribute(Attributes.ARMOR).setBaseValue(0);m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);m.setPos(at);h.assertTrue(h.getLevel().addFreshEntity(m),"native stationary hostile admitted");entities.add(m);if(exact)h.assertTrue(HighPowerCombat.enroll(m,5),"declared exact hostile enrollment");return m;}
        void acknowledgeLogin(){channel.runPendingTasks();channel.flushOutbound();channel.runPendingTasks();Object packet;while((packet=channel.readOutbound())!=null){if(packet instanceof ClientboundPlayerPositionPacket position)p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));ReferenceCountUtil.release(packet);}}
        void hold(ChunkPos chunk){if(held.add(chunk)){h.getLevel().getChunkSource().addRegionTicket(TICKET,chunk,2,ticket);h.getLevel().getChunk(chunk.x,chunk.z);}}
        void block(BlockPos pos,BlockState state){hold(new ChunkPos(pos));changed.putIfAbsent(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,state);}
        void aim(Vec3 at){Vec3 d=at.subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p))).wire();}
        boolean press(){return SpatialPincerCorridor.press(p,wire(++inputs));}
        boolean tap(){String wire=wire(++inputs);return SpatialPincerCorridor.press(p,wire)&&SpatialPincerCorridor.release(p,wire);}
        Vec3 nativeSurface(){var hit=h.getLevel().clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(64)),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,p));
            h.assertTrue(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&hit.getDirection()==net.minecraft.core.Direction.UP,
                    "independent native fixture ray confirms actual first-UP surface: "+hit.getLocation());return hit.getLocation();}
        void markA(){aim(pointA);surfaceA=nativeSurface();h.assertTrue(tap(),"genuine authenticated first short release captures native floor A: "+SpatialPincerCorridor.receipt(p));originA=SpatialPincerCorridor.receipt(p).arrival();}
        void ready(Runnable action){markA();post(4,()->{aim(pointB);surfaceB=nativeSurface();h.assertTrue(tap(),"genuine second short release captures B: "+SpatialPincerCorridor.receipt(p));originB=SpatialPincerCorridor.receipt(p).arrival();});post(8,()->{aim(pointC);surfaceC=nativeSurface();h.assertTrue(tap(),"genuine third short release captures C: "+SpatialPincerCorridor.receipt(p));originC=SpatialPincerCorridor.receipt(p).arrival();});post(12,action);}
        void preview(Runnable action){ready(()->{aim(target.getBoundingBox().getCenter());double before=cp();int originals=needles();
            h.assertTrue(tap(),"genuine fourth short release freezes a free target preview: "+SpatialPincerCorridor.receipt(p));
            h.assertTrue(SpatialPincerCorridor.previewed(p)&&!SpatialPincerCorridor.active(p)&&spawns.media.isEmpty(),"preview has no paid publication or active flight");
            h.assertValueEqual(SpatialPincerCorridor.remainingTicks(p),SpatialPincerCorridorRules.PREPARATION_TICKS-12,"preview preserves the first mark's immutable remaining lifetime");
            near(h,cp(),before,"preview consumes no CP");h.assertValueEqual(needles(),originals,"preview reserves no physical originals");action.run();});}
        void begin(Runnable action){begin(()->{},action);}
        void begin(Runnable previewCheck,Runnable action){usesPreview=true;preview(()->{previewCheck.run();post(4,()->{double before=cp();started=p.server.getTickCount();
            h.assertTrue(tap(),"genuine fifth fresh short release confirms the same preview and publishes first original: "+SpatialPincerCorridor.receipt(p));
            near(h,cp(),before-SpatialPincerCorridorRules.cpCost(HighPowerGrowth.values(p).position())/volleyNeedles,"first publication debits its own frozen whole-volley share only");spawns.firstPayment();action.run();});});}
        void stock(int count){initialStock=count;p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AcademyCraft.NEEDLE.get(),count));}
        DisplacedNeedleEntity first(){return spawns.media.getFirst();}
        double cp(){return HighPowerResources.cp(p);}
        int needles(){int count=0;for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);if(stack.is(AcademyCraft.NEEDLE.get()))count+=stack.getCount();}return count;}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        Observation observe(LivingEntity e){Observation o=new Observation(e);hook(o);return o;}
        void post(int delay,Runnable action){PostCheck c=new PostCheck(this,delay,action);checks.add(c);hook(c);}
        void verifyPosts(){if(postFailure instanceof RuntimeException failure)throw failure;if(postFailure instanceof Error failure)throw failure;if(postFailure!=null)throw new AssertionError(postFailure);h.assertTrue(checks.stream().allMatch(c->c.ran),"all requested actual native Post observations executed");}
        void finish(int ticks){h.runAfterDelay(ticks+(usesPreview?4:0),()->{verifyPosts();h.succeed();});}
        public void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);SpatialPincerCorridor.clear(p);HighPowerBracerSession.remove(p);AbilityStorage.remove(p);p.server.getPlayerList().remove(p);entities.forEach(Entity::discard);changed.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));held.forEach(chunk->h.getLevel().getChunkSource().removeRegionTicket(TICKET,chunk,2,ticket));channel.finishAndReleaseAll();}
    }
}
