package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.develop.*;
import net.minecraft.nbt.CompoundTag;
import cn.academy.port.AcademyCraft;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.DisplacedProjectionRules;
import cn.academy.port.highpower.core.DamageMitigation;
import cn.academy.port.highpower.core.DefenseProfile;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NEW311 controller-to-native-combat proof. Real PlayerList admission uses a declared
 * mocked network; learned flags are setup, not learning evidence. All contacts come
 * from the registered needle's native tick and ProjectileImpactEvent. No test calls
 * onHitEntity or constructs a damage permit. Targets are native hostile monsters only.
 * Explicit native stepping is labeled; one separate test uses ordinary world ticks.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDisplacedProjectionRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_displaced_projection";
    private static final String ID=DisplacedProjection.ID, SKILL="teleporter.displaced_projection";
    private static final TicketType<UUID> TICKET=TicketType.create("academy_displaced_projection_fixture",UUID::compareTo);
    private HighPowerDisplacedProjectionRuntimeTests() {}
    private static BigDecimal n(String value){return new BigDecimal(value);}
    private static BigDecimal hp(LivingEntity e){return HighPowerCombat.enabled(e)?n(HighPowerCombat.healthText(e)):BigDecimal.valueOf(e.getHealth());}
    private static void eq(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+": "+actual+" != "+expected);}
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-7,why+": "+actual+" != "+expected);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void paid_cast_explicit_native_step_has_one_real_medium_one_contact_and_no_mastery(GameTestHelper h){
        try(Fixture f=new Fixture(h,true)){
            BigDecimal before=hp(f.target), damage=DisplacedProjectionRules.damage(HighPowerGrowth.values(f.p).damage());
            double cp=f.cp(); Vec3 caster=f.p.position(); int count=f.needles(); Observation hit=f.observe(f.target);
            h.assertTrue(f.cast(),"genuine authenticated NEW311 cast: "+DisplacedProjection.receipt(f.p));
            DisplacedNeedleEntity medium=f.medium(); Vec3 arrival=medium.position();
            h.assertTrue(medium.getType()==TeleporterNeedleEntities.DISPLACED_NEEDLE.get()&&medium.getOwner()==f.p&&medium.isContactEnabled(),"registered physical medium is admitted and armed");
            h.assertTrue(DisplacedProjection.receipt(f.p).paid()&&DisplacedProjection.receipt(f.p).projected(),"receipt proves paid projection");
            h.assertValueEqual(f.needles(),count-1,"exactly one actual offhand needle removed");
            near(h,f.cp(),cp-DisplacedProjectionRules.cpCost(HighPowerGrowth.values(f.p).position()),"one shared CP debit");
            near(h,arrival.distanceTo(f.target.getBoundingBox().getCenter()),1.5,"fixed side offset is exactly 1.5m");
            h.assertTrue(f.p.getEyePosition().distanceTo(arrival)+arrival.distanceTo(f.target.getBoundingBox().getCenter())<=DisplacedProjectionRules.RANGE,"caster-to-side plus initial native flight fits the cumulative reach bound");
            h.assertTrue(arrival.x<f.target.getX()&&arrival.z==f.target.getZ(),"yaw zero selects exactly caster-right, without alternate side search");
            h.assertTrue(f.p.position().equals(caster),"caster is never relocated");
            medium.tick(); // Explicit stepping of the actual registered AbstractArrow collision path.
            eq(h,hp(f.target),before.subtract(damage),"one frozen 1D budget reaches exact hostile HP");
            h.assertTrue(hit.posts==1&&DisplacedProjection.receipt(f.p).hurt()&&!DisplacedProjection.active(f.p),"one native contact produces one terminal receipt");
            assertSource(h,hit.source,f.p,medium,arrival);
            h.assertTrue(hit.contactPosition!=null&&hit.contactPosition.y>f.target.getY()+.5,"actual native contact intersection differs from target feet");
            BigDecimal after=hp(f.target); medium.tick(); medium.tick(); medium.playerTouch(f.p);
            eq(h,hp(f.target),after,"spent native flight cannot damage a second time");
            h.assertTrue(!medium.isContactEnabled()&&!medium.isRemoved()&&medium.mediumSnapshot().getCount()==1,"spent medium remains one recoverable actual entity");
            h.assertValueEqual(f.needles(),count-1,"airborne playerTouch cannot copy or grant a second item");
            near(h,AbilityStorage.get(f.p).exp(ID),0,"NEW311 prototype awards zero mastery");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void ordinary_world_ticks_deliver_the_same_real_native_contact(GameTestHelper h){
        Fixture f=new Fixture(h,true); Observation hit=f.observe(f.target); BigDecimal before=hp(f.target);
        BigDecimal damage=DisplacedProjectionRules.damage(HighPowerGrowth.values(f.p).damage());
        h.assertTrue(f.cast(),"actual paid cast before ordinary level ticks"); var medium=f.medium(); Vec3 arrival=medium.position();
        h.runAfterDelay(5,()->{
            h.assertTrue(medium.tickCount>0,"actual registered entity ticked in its resident native world");
            eq(h,hp(f.target),before.subtract(damage),"ordinary world ticks spend exactly one frozen budget");
            h.assertTrue(hit.posts==1&&DisplacedProjection.receipt(f.p).hurt()&&!medium.isContactEnabled(),"world tick produces one terminal native hit");
            assertSource(h,hit.source,f.p,medium,arrival); near(h,AbilityStorage.get(f.p).exp(ID),0,"native world contact creates no mastery"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void absent_target_medium_decorated_medium_and_blocked_fixed_side_are_free(GameTestHelper h){
        for(int mode=0;mode<4;mode++)try(Fixture f=new Fixture(h,true)){
            if(mode==0)f.target.discard();
            if(mode==1)f.p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            if(mode==2)f.p.getOffhandItem().set(DataComponents.CUSTOM_NAME,Component.literal("Not a plain needle"));
            if(mode==3)f.block(f.target.getBoundingBox().getCenter().add(-1.5,0,0));
            double cp=f.cp();int count=f.needles();BigDecimal before=hp(f.target);Vec3 pos=f.p.position();
            h.assertFalse(f.cast(),"native preparation refuses mode "+mode);var r=DisplacedProjection.receipt(f.p);
            String reason=mode==0?"no_target":mode==3?"blocked":"no_medium";
            h.assertTrue(r!=null&&!r.paid()&&!r.projected()&&r.reason().equals(reason),"precise free refusal "+reason+": "+r);
            near(h,f.cp(),cp,"free refusal cannot spend CP");h.assertValueEqual(f.needles(),count,"free refusal cannot consume actual inventory");
            eq(h,hp(f.target),before,"free refusal cannot apply damage");
            h.assertTrue(!DisplacedProjection.active(f.p)&&DisplacedNeedleRegistry.existing(f.p).isEmpty()&&f.p.position().equals(pos),"no alternate side, movement or hidden medium");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void cumulative_reach_refuses_a_visible_target_whose_side_detour_is_too_long(GameTestHelper h){
        try(Fixture f=new Fixture(h,true)){
            f.target.setPos(f.p.position().add(0,0,DisplacedProjectionRules.RANGE-1));f.aim(f.target.getBoundingBox().getCenter());double cp=f.cp();int stock=f.needles();
            h.assertTrue(f.p.getEyePosition().distanceTo(f.target.getBoundingBox().getCenter())<DisplacedProjectionRules.RANGE,"visible native target is within direct reach");
            h.assertFalse(f.cast(),"side detour plus initial flight exceeds configured cumulative reach");
            h.assertTrue(DisplacedProjection.receipt(f.p).reason().equals("out_of_range"),"distance refusal has explicit reason");
            near(h,f.cp(),cp,"out-of-range refusal is free");h.assertValueEqual(f.needles(),stock,"out-of-range refusal leaves original medium");h.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_first_other_body_or_new_solid_spends_one_attempt_without_target_damage(GameTestHelper h){
        for(int mode=0;mode<2;mode++)try(Fixture f=new Fixture(h,true)){
            if(mode==1){f.p.setPos(f.p.position().add(.5,0,0));f.target.setPos(f.target.position().add(.5,0,0));f.aim(f.target.getBoundingBox().getCenter());}
            BigDecimal before=hp(f.target);double cp=f.cp();h.assertTrue(f.cast(),"paid projection precedes changed native flight path");var medium=f.medium();
            LivingEntity other=null;
            if(mode==0)other=f.mob(medium.position().add(.7,-f.target.getBbHeight()/2,0),true);
            else f.block(medium.position().add(.6,0,0));
            Vec3 origin=medium.position(),end=origin.add(medium.getDeltaMovement());
            Vec3 selectedContact=f.target.getBoundingBox().inflate((double).3F).clip(origin,end).orElseThrow();
            if(other!=null){var bounds=other.getBoundingBox().inflate((double).3F);h.assertFalse(bounds.contains(origin),"declared first body starts strictly ahead of native segment origin");Vec3 first=bounds.clip(origin,end).orElseThrow();h.assertTrue(origin.distanceToSqr(first)<origin.distanceToSqr(selectedContact),"inserted body's native padded entry precedes selected hostile");}
            else{var first=h.getLevel().clip(new net.minecraft.world.level.ClipContext(origin,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.ANY,medium));h.assertTrue(first.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&origin.distanceToSqr(first.getLocation())>0&&origin.distanceToSqr(first.getLocation())<origin.distanceToSqr(selectedContact),"actual native solid entry is strictly ahead and precedes selected hostile's padded contact");}
            BigDecimal otherBefore=other==null?BigDecimal.ZERO:hp(other);medium.tick();medium.tick();
            eq(h,hp(f.target),before,"first native obstacle cannot be skipped to hit selected target, mode="+mode);
            if(other!=null)eq(h,hp(other),otherBefore,"non-selected first hostile receives no transferred damage authority");
            h.assertTrue(!medium.isContactEnabled()&&!DisplacedProjection.active(f.p),"first actual contact retires its one generation");
            h.assertTrue(f.cp()<cp&&f.needles()==2,"blocked attempt remains paid with one actual medium");h.assertFalse(f.cast(),"same-tick retry cannot charge a new attempt");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void source_overlap_with_nonselected_native_padded_body_cannot_damage_selected_target(GameTestHelper h){
        try(Fixture f=new Fixture(h,true)){
            BigDecimal before=hp(f.target);double cp=f.cp();h.assertTrue(f.cast(),"real paid projection precedes late overlapping hostile");var medium=f.medium();
            Zombie other=f.mob(medium.position().add(.5,-f.target.getBbHeight()/2,0),true);BigDecimal otherBefore=hp(other);
            h.assertTrue(other.getBoundingBox().inflate((double).3F).contains(medium.position()),"declared source-overlap fixture starts inside nonselected native padded body");
            // The pinned ProjectileUtil overload can omit an entry when the origin is
            // already inside its padded box. This is a controller safety regression,
            // not evidence that native collision chose the overlapping body first.
            near(h,f.cp(),cp-DisplacedProjectionRules.cpCost(HighPowerGrowth.values(f.p).position()),"overlapped attempt has exactly one committed fee");double paid=f.cp();
            medium.tick();medium.tick();medium.tick();
            eq(h,hp(f.target),before,"overlapped source cannot send damage through nonselected body to chosen hostile");eq(h,hp(other),otherBefore,"overlap never transfers damage authority to another body");
            h.assertTrue(!medium.isContactEnabled()&&!DisplacedProjection.active(f.p)&&DisplacedProjection.receipt(f.p).paid(),"one paid native contact retires without retry");
            h.assertTrue(f.needles()==2&&medium.mediumSnapshot().getCount()==1,"overlap retains one physical medium without duplication");
            h.assertFalse(f.cast(),"same-tick retry cannot re-arm overlapped attempt");near(h,f.cp(),paid,"overlap and retry cannot add or refund CP");h.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void moving_target_misses_without_tracking_and_retains_the_paid_medium(GameTestHelper h){
        Fixture f=new Fixture(h,true);BigDecimal before=hp(f.target);double cp=f.cp();h.assertTrue(f.cast(),"native paid projection starts with visible hostile");
        var medium=f.medium();near(h,f.cp(),cp-DisplacedProjectionRules.cpCost(HighPowerGrowth.values(f.p).position()),"miss begins with the exact committed CP debit");Vec3 originalMotion=medium.getDeltaMovement();f.target.setPos(f.target.position().add(0,4,0));
        h.assertTrue(medium.getDeltaMovement().equals(originalMotion),"moving target does not retarget actual needle velocity");
        h.runAfterDelay(22,()->{
            eq(h,hp(f.target),before,"moving hostile was missed by actual native world ticks");
            h.assertTrue(!medium.isContactEnabled()&&!DisplacedProjection.active(f.p),"twenty-tick contract is expired");
            h.assertTrue(DisplacedProjection.receipt(f.p).paid()&&f.needles()==2&&medium.mediumSnapshot().getCount()==1,"miss keeps one paid recoverable medium while ordinary CP recovery remains active");
            near(h,AbilityStorage.get(f.p).exp(ID),0,"spent miss awards no mastery");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void skill_zero_denial_and_amplification_cannot_expand_frozen_damage(GameTestHelper h){
        for(int mode=0;mode<2;mode++)try(Fixture f=new Fixture(h,true)){
            int modeNow=mode;boolean[] called={false};BigDecimal before=hp(f.target);double cp=f.cp();
            f.hook(new Object(){@SubscribeEvent public void skill(SkillAttackEvent e){if(e.target!=f.target||!e.skill.equals(SKILL))return;called[0]=true;if(modeNow==0)e.amount=0;else e.amount*=2;}});
            h.assertTrue(f.cast(),"actual paid cast before SkillAttack callback");var m=f.medium();m.tick();m.tick();
            h.assertTrue(called[0],"native SkillAttack callback executes");eq(h,hp(f.target),before,"zeroed or amplified frozen allocation is rejected");
            h.assertTrue(f.cp()<cp&&!m.isContactEnabled()&&!DisplacedProjection.active(f.p),"rejected terminal hit remains spent without retry");
            near(h,AbilityStorage.get(f.p).exp(ID),0,"failed terminal callback creates no mastery");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_skill_incoming_pre_and_late_absorption_revalidate_caster_target_and_owner(GameTestHelper h){
        for(int stage=0;stage<4;stage++)for(int mutation=0;mutation<3;mutation++)for(boolean exact:List.of(true,false))try(Fixture f=new Fixture(h,exact)){
            f.hook(new Discount(f.target));var gate=new Mutation(f,stage,mutation);f.hook(gate);Observation hit=f.observe(f.target);
            if(stage==3){f.target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,0));f.target.setAbsorptionAmount(2);}
            BigDecimal before=hp(f.target);double cp=f.cp();h.assertTrue(f.cast(),"paid native cast for callback stage "+stage+" mutation "+mutation+" exact="+exact);
            f.medium().tick();h.assertTrue(gate.called,"requested native mutable callback really executes stage="+stage);
            eq(h,hp(f.target),before,"mutation cannot leave an exact or ordinary native HP debit");
            h.assertTrue(hit.damage==0&&f.cp()<cp&&!f.medium().isContactEnabled(),"late mutation spends original attempt without positive proxy damage");
            if(stage==3)h.assertTrue(gate.afterPre&&gate.reduction==2&&f.target.getAbsorptionAmount()==2,"positive native ABSORPTION callback invalidation preserves the original pool after Pre");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void projectile_fire_critical_and_pierce_contamination_never_become_ordinary_arrow_damage(GameTestHelper h){
        for(int mode=0;mode<3;mode++)try(Fixture f=new Fixture(h,true)){
            BigDecimal before=hp(f.target);h.assertTrue(f.cast(),"paid projection before contamination");var m=f.medium();
            if(mode==0)m.setRemainingFireTicks(100);else if(mode==1)m.setCritArrow(true);else contaminatePinnedNativePierce(m);
            m.tick();m.tick();eq(h,hp(f.target),before,"contaminated real projectile cannot obtain exact or vanilla arrow damage");
            h.assertTrue(!m.isContactEnabled()&&!f.target.isOnFire(),"contamination never supplies a second hit or fire transfer");
            h.assertValueEqual(f.needles(),2,"contamination cannot duplicate physical inventory");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void canceled_or_throwing_native_projectile_impact_retires_without_retry(GameTestHelper h){
        for(int mode=0;mode<2;mode++)try(Fixture f=new Fixture(h,true)){
            int modeNow=mode;int[] calls={0};BigDecimal before=hp(f.target);h.assertTrue(f.cast(),"real paid projection before native impact gate");var medium=f.medium();
            f.hook(new Object(){@SubscribeEvent public void impact(ProjectileImpactEvent e){if(e.getEntity()!=medium)return;calls[0]++;if(modeNow==0)e.setCanceled(true);else throw new IllegalStateException("Declared NEW311 native ProjectileImpactEvent failure");}});
            medium.tick();medium.tick();medium.tick();eq(h,hp(f.target),before,"native cancellation/exception prevents damage");
            h.assertTrue(calls[0]==1&&!medium.isContactEnabled()&&!DisplacedProjection.active(f.p),"native event gate spends first observation and cannot retry");
            h.assertTrue(medium.mediumSnapshot().getCount()==1&&f.needles()==2,"one recoverable physical medium survives event refusal");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_impact_reentrant_reprojection_and_pickup_have_no_fee_or_movement(GameTestHelper h){
        try(Fixture f=new Fixture(h,true)){
            int[] calls={0},payments={0};BigDecimal before=hp(f.target);h.assertTrue(f.cast(),"native paid medium before hostile callback");var medium=f.medium();double cp=f.cp();
            f.hook(new Object(){@SubscribeEvent public void impact(ProjectileImpactEvent e){if(e.getEntity()!=medium)return;calls[0]++;Vec3 at=medium.position();long generation=medium.identity().generation();
                h.assertTrue(medium.impactBusy(),"impact guard is established before external callbacks");
                h.assertFalse(DisplacedNeedleRegistry.reproject(f.p,medium,at.add(3,0,0),()->true,()->{payments[0]++;return true;}),"actual registry rejects reentrant medium relocation");
                medium.playerTouch(f.p);h.assertTrue(medium.position().equals(at)&&medium.identity().generation()==generation&&!medium.isRemoved(),"reentrant pickup/reproject changes no native identity or pose");
                near(h,f.cp(),cp,"reentrant attempts add no fee");h.assertValueEqual(f.needles(),2,"reentrant pickup grants no inventory");}});
            medium.tick();h.assertTrue(calls[0]==1&&payments[0]==0,"one native event; reentrant payment supplier never executes");
            eq(h,hp(f.target),before.subtract(DisplacedProjectionRules.damage(HighPowerGrowth.values(f.p).damage())),"original authorized native impact alone completes");h.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_projectile_event_target_pose_or_owner_mutation_retires_before_damage(GameTestHelper h){
        for(int mode=0;mode<3;mode++)try(Fixture f=new Fixture(h,true)){
            int modeNow=mode;boolean[] called={false};BigDecimal before=hp(f.target);h.assertTrue(f.cast(),"paid real medium before native event mutation");var m=f.medium();
            f.hook(new Object(){@SubscribeEvent public void impact(ProjectileImpactEvent e){if(e.getEntity()!=m)return;called[0]=true;if(modeNow==0)f.target.setPos(f.target.position().add(0,4,0));else if(modeNow==1)f.target.setBoundingBox(f.target.getBoundingBox().inflate(.05));else m.setOwner(f.target);}});
            m.tick();m.tick();h.assertTrue(called[0],"real native impact mutation callback executed");eq(h,hp(f.target),before,"native event mutation cannot transfer captured contact");
            h.assertTrue(!m.isContactEnabled()&&!DisplacedProjection.active(f.p)&&m.getOwner()==f.p,"mutation retires old authority; owner identity cannot be replaced");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void generic_skill_string_spoof_refuses_and_ordinary_native_combat_still_works(GameTestHelper h){
        try(Fixture f=new Fixture(h,true)){
            BigDecimal before=hp(f.target);double cp=f.cp();h.assertFalse(HighPowerCombat.attack(f.p,SKILL,f.target,n("5")),"generic attack string is not an actual native-contact permit");
            eq(h,hp(f.target),before,"spoof performs no exact damage");near(h,f.cp(),cp,"spoof cannot spend controller CP");
            h.assertTrue(f.target.hurt(f.p.damageSources().playerAttack(f.p),5),"ordinary native combat remains available");
            eq(h,hp(f.target),before.subtract(n("5")),"ordinary native damage retains normal exact adapter units");h.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void armor_protection_resistance_absorption_match_native_thrown_controls(GameTestHelper h){defenseCase(h,true);}
    private static void defenseCase(GameTestHelper h,boolean exact){
        Fixture f=new Fixture(h,exact);Zombie control=f.mob(f.target.position().add(5,0,0),false);
        defenses(h,f.target);defenses(h,control);f.hook(new Discount(f.target));Observation hit=f.observe(f.target),nativeHit=f.observe(control);
        h.runAfterDelay(2,()->{
            BigDecimal before=hp(f.target);h.assertTrue(f.cast(),"actual paid projection reaches native defense pipeline");var m=f.medium();Vec3 arrival=m.position();
            Vec3 nativeOrigin=arrival.add(control.position().subtract(f.target.position()));
            h.assertTrue(control.hurt(new DamageSource(f.p.damageSources().thrown(m,f.p).typeHolder(),m,f.p,nativeOrigin),5),"matching ordinary THROWN control accepts");
            m.tick();h.assertTrue(hit.posts==1&&nativeHit.posts==1&&hit.armor>0&&hit.effects>0&&hit.enchantments>0&&hit.absorption==.5F,"every native defense reducer executes exactly once");
            h.assertTrue(hit.armor==nativeHit.armor&&hit.effects==nativeHit.effects&&hit.enchantments==nativeHit.enchantments&&hit.absorption==nativeHit.absorption&&Math.abs(hit.damage-nativeHit.damage)<.00001,"paired native THROWN and real displaced needle share all reducer observations");
            if(exact){BigDecimal residual=DamageMitigation.mitigate(n("5"),new DefenseProfile(BigDecimal.valueOf(f.target.getArmorValue()),BigDecimal.valueOf(f.target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)),1,4)).subtract(n(".5"));eq(h,hp(f.target),before.subtract(residual),"exact ledger retains only native-mitigated residual");}
            else h.assertTrue(f.target.getHealth()==control.getHealth(),"ordinary hostile retains native float damage");
            h.assertTrue(f.target.getAbsorptionAmount()==0&&control.getAbsorptionAmount()==0,"absorption pool consumed once");assertSource(h,hit.source,f.p,m,arrival);
            f.close();if(exact)defenseCase(h,false);else h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=70)
    public static void held_shield_faces_actual_needle_side_even_when_caster_is_on_opposite_hemisphere(GameTestHelper h){shieldCase(h,0);}
    private static void shieldCase(GameTestHelper h,int mode){
        Fixture f=new Fixture(h,mode<2);Zombie control=f.mob(f.target.position().add(5,0,0),false);float yaw=mode%2==0?45:225;
        shield(f.target,yaw);shield(control,yaw);f.hook(new Discount(f.target));Observation hit=f.observe(f.target),nativeHit=f.observe(control);
        h.runAfterDelay(7,()->{
            face(f.target,yaw);face(control,yaw);h.assertTrue(f.target.isBlocking()&&control.isBlocking(),"native shield-raising delay has completed");
            BigDecimal before=hp(f.target);h.assertTrue(f.cast(),"real paid projection begins shield case "+mode);var m=f.medium();Vec3 arrival=m.position();
            double casterDot=f.target.getLookAngle().dot(f.p.position().subtract(f.target.position()));double needleDot=f.target.getLookAngle().dot(arrival.subtract(f.target.position()));
            h.assertTrue(casterDot*needleDot<0,"actual caster and actual arriving needle lie in opposite shield hemispheres");
            boolean blocked=mode%2==0;Vec3 nativeOrigin=arrival.add(control.position().subtract(f.target.position()));
            boolean ordinary=control.hurt(new DamageSource(f.p.damageSources().thrown(m,f.p).typeHolder(),m,f.p,nativeOrigin),5);m.tick();
            h.assertTrue(ordinary!=blocked&&hit.blocked==blocked&&nativeHit.blocked==blocked,"held shield uses actual final native needle origin");
            eq(h,hp(f.target),blocked?before:before.subtract(n("5")),"shield arrival side controls exact and ordinary HP");
            h.assertTrue(hit.blockedDamage==nativeHit.blockedDamage&&hit.damage==nativeHit.damage,"native control block and residual amounts agree");assertSource(h,hit.source,f.p,m,arrival);
            f.close();if(mode<3)shieldCase(h,mode+1);else h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_same_rank_iframes_preserve_absorption_and_refuse_second_equal_damage(GameTestHelper h){
        for(boolean exact:List.of(true,false))try(Fixture f=new Fixture(h,exact)){
            f.target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,2));f.target.setAbsorptionAmount(10);f.hook(new Discount(f.target));Observation hit=f.observe(f.target);BigDecimal before=hp(f.target);
            h.assertTrue(f.target.hurt(f.p.damageSources().playerAttack(f.p),5),"ordinary native hit establishes real hurt cooldown and spends absorption");
            h.assertTrue(f.target.getAbsorptionAmount()==5,"native first hit spends five absorption");
            h.assertTrue(f.cast(),"independent paid projection still reaches native iframe decision");f.medium().tick();
            eq(h,hp(f.target),before,"absorption plus actual native iframe prevents both HP debits");
            h.assertTrue(f.target.getAbsorptionAmount()==5&&hit.posts==1&&!DisplacedProjection.receipt(f.p).hurt(),"equal projected hit cannot bypass iframe or spend absorption twice");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_death_attribution_death_veto_and_totem_remain_intact(GameTestHelper h){
        for(int mode=0;mode<6;mode++)try(Fixture f=new Fixture(h,mode<3)){
            int outcome=mode%3;f.hook(new Discount(f.target));if(mode<3)f.target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health","1");else f.target.setHealth(1);
            hp(f.target);Observation hit=f.observe(f.target);boolean[] veto={false},controlVeto={false};
            Zombie control=mode>=3&&outcome==1?f.mob(f.target.position().add(5,0,0),false):null;
            if(control!=null){control.setHealth(1);f.hook(new Object(){@SubscribeEvent public void death(LivingDeathEvent e){if(e.getEntity()==control){controlVeto[0]=true;e.setCanceled(true);}}});}
            if(outcome==1)f.hook(new Object(){@SubscribeEvent public void death(LivingDeathEvent e){if(e.getEntity()==f.target){veto[0]=true;e.setCanceled(true);}}});
            if(outcome==2)f.target.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.TOTEM_OF_UNDYING));
            h.assertTrue(f.cast(),"real paid projection before native lethal handling");var m=f.medium();Vec3 arrival=m.position();
            if(control!=null)control.hurt(new DamageSource(f.p.damageSources().thrown(m,f.p).typeHolder(),m,f.p,arrival.add(control.position().subtract(f.target.position()))),5);
            m.tick();assertSource(h,hit.source,f.p,m,arrival);
            if(outcome==0)h.assertTrue(!f.target.isAlive()&&hit.deaths==1&&f.target.getKillCredit()==f.p,"native death occurs once with actual owner kill credit");
            if(outcome==1){h.assertTrue(veto[0],"native death veto callback executes for mode "+mode);if(mode<3){h.assertTrue(f.target.isAlive(),"exact death veto leaves hostile alive");eq(h,hp(f.target),BigDecimal.ONE,"exact death veto restores original HP");}else{h.assertTrue(controlVeto[0]&&f.target.isAlive()==control.isAlive()&&f.target.getHealth()==control.getHealth()&&f.target.deathTime==control.deathTime,"ordinary death veto preserves the same HP, liveness and native death state as matching THROWN control");}}
            if(outcome==2)h.assertTrue(f.target.isAlive()&&f.target.getOffhandItem().isEmpty()&&hp(f.target).signum()>0,"native totem consumes exactly once and rescues hostile");
            h.assertTrue(!m.isContactEnabled(),"lethal handling never restores spent projectile authority");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void stationary_native_flight_expires_at_twenty_ticks_independently_of_range(GameTestHelper h){
        Fixture f=new Fixture(h,true);BigDecimal before=hp(f.target);h.assertTrue(f.cast(),"actual paid flight before declared stationary-motion expiry fixture");var m=f.medium();m.setDeltaMovement(Vec3.ZERO);Vec3 origin=m.position();
        // Declared test-only velocity reset isolates the native twenty-tick timer;
        // the separate ordinary-world-flight test leaves native movement untouched.
        f.hook(new Object(){@SubscribeEvent public void tick(ServerTickEvent.Pre e){if(e.getServer()==f.p.server&&m.isContactEnabled())m.setDeltaMovement(Vec3.ZERO);}});
        h.runAfterDelay(18,()->{h.assertTrue(DisplacedProjection.active(f.p)&&m.isContactEnabled(),"flight remains armed before twenty physical server ticks");h.assertTrue(origin.distanceTo(m.position())<DisplacedProjectionRules.RANGE-f.p.getEyePosition().distanceTo(origin),"declared timer-isolation fixture has not exhausted cumulative remaining range");});
        h.runAfterDelay(22,()->{h.assertTrue(!DisplacedProjection.active(f.p)&&!m.isContactEnabled(),"twenty-tick age independently retires native flight");eq(h,hp(f.target),before,"expiry never substitutes direct target damage");h.assertTrue(m.mediumSnapshot().getCount()==1&&f.needles()==2,"expiry preserves one actual recoverable needle");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void eight_physical_tick_fence_survives_one_tick_lv5_cooldown_and_reprojects_same_uuid(GameTestHelper h){
        Fixture f=new Fixture(h,true,5);BigDecimal before=hp(f.target);h.assertTrue(f.cast(),"LV5 genuine initial paid projection");var medium=f.medium();UUID uuid=medium.getUUID();int nativeId=medium.getId();long generation=medium.identity().generation();medium.setDeltaMovement(Vec3.ZERO);
        h.assertValueEqual(AbilityStorage.get(f.p).cooldowns.getOrDefault(ID,0),1,"LV5 provisional cooldown is one ordinary tick");
        h.runAfterDelay(4,()->{double cp=f.cp();Vec3 pos=medium.position();h.assertFalse(f.cast(),"four real ticks cannot bypass independent eight-tick movement fence");var r=DisplacedProjection.receipt(f.p);h.assertTrue(r!=null&&!r.paid()&&r.reason().equals("cast_fence"),"physical fence refusal remains explicit after cooldown expiry");near(h,f.cp(),cp,"fence rejection costs no CP");h.assertTrue(medium.position().equals(pos)&&medium.identity().generation()==generation,"early request cannot move or re-arm original entity");});
        h.runAfterDelay(9,()->{double cp=f.cp();BigDecimal frozenDamage=DisplacedProjectionRules.damage(HighPowerGrowth.values(f.p).damage());h.assertTrue(frozenDamage.compareTo(n("1e20"))<=0,"actual native LV5 D remains within global ceiling");h.assertTrue(f.cast(),"later authenticated press after physical fence reprojects actual existing medium: "+DisplacedProjection.receipt(f.p));var same=f.medium();
            h.assertTrue(same==medium&&same.getUUID().equals(uuid)&&same.getId()==nativeId&&same.identity().generation()==generation+1,"reprojection retains same native object/UUID/id and advances one generation");
            h.assertValueEqual(f.needles(),2,"reprojection consumes no second physical item");near(h,f.cp(),cp-DisplacedProjectionRules.cpCost(HighPowerGrowth.values(f.p).position()),"reprojection charges exactly one new cast");same.tick();
            eq(h,hp(f.target),before.subtract(frozenDamage),"only replacement generation spends its actual one-D frozen LV5 budget");h.assertFalse(same.isContactEnabled(),"reprojected generation also has exactly one contact");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_saved_medium_rebind_is_harmless_and_does_not_repay_or_restore_flight(GameTestHelper h){
        try(Fixture f=new Fixture(h,true)){
            h.assertTrue(f.cast(),"actual paid flight before declared native NBT reconstruction");var m=f.medium();double cp=f.cp();BigDecimal before=hp(f.target);int stock=f.needles();
            CompoundTag saved=new CompoundTag();h.assertTrue(m.save(saved),"actual native entity save succeeds");UUID uuid=m.getUUID();m.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertFalse(DisplacedProjection.active(f.p),"native removal retires old controller generation");
            var restored=new DisplacedNeedleEntity(TeleporterNeedleEntities.DISPLACED_NEEDLE.get(),h.getLevel());restored.load(saved);f.entities.add(restored);h.assertTrue(h.getLevel().addFreshEntity(restored)&&DisplacedNeedleRegistry.tryBind(restored),"actual saved entity rejoins its authoritative custody record");
            h.assertTrue(restored.getUUID().equals(uuid)&&!restored.isContactEnabled()&&!DisplacedProjection.active(f.p),"same native UUID restores custody only, never transient damage authority");
            restored.tick();restored.tick();eq(h,hp(f.target),before,"saved medium cannot replay old native damage");near(h,f.cp(),cp,"rebind cannot repay or refund cast");h.assertValueEqual(f.needles(),stock,"rebind never creates a second item");h.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void actual_paid_learning_ingress_requires_313_and_preserves_q_Q_HP_CP_and_zero_xp(GameTestHelper h){
        try(Fixture f=new Fixture(h,true)){
            var s=AbilityStorage.get(f.p);s.unlearn(ID);s.unlearn(HighPowerSkills.SAFE_LANDING_PROBE);
            var skill=SkillCatalog.find(HighPowerCategories.TELEPORTER,ID).orElseThrow();
            h.assertTrue(skill.level()==1&&skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.SAFE_LANDING_PROBE,0))),"native catalog has exact LV1 learned NEW313 prerequisite");
            var rejected=portable(f.p,10000);h.assertFalse(learnRequest(f.p,ID),"actual paid learning ingress refuses absent NEW313");h.assertTrue(rejected.energy()==10000,"missing parent costs no IF");
            paidLearn(h,f.p,HighPowerSkills.SAFE_LANDING_PROBE);
            f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health","123.25");f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp",37);HighPowerGrowth.values(f.p);
            CompoundTag combat=f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy(),budget=f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();double xp=s.levelExperience;
            int stimulations=cn.academy.port.core.ClassicRules.learningStimulations(skill.level());int ticks=stimulations*DeveloperType.PORTABLE.ticksPerStimulation();double cost=DeveloperType.PORTABLE.actualConsumption(stimulations);var battery=portable(f.p,cost);
            h.assertTrue(learnRequest(f.p,ID),"genuine sender-bound hp_learn ingress starts portable development");
            for(int tick=0;tick<ticks-1;tick++)DevelopmentController.tick(f.p,ignored->{});
            h.assertTrue(!s.learned(ID)&&battery.energy()>0,"all but last declared accelerated native developer tick cannot grant early");DevelopmentController.tick(f.p,ignored->{});
            h.assertTrue(s.learned(ID)&&battery.energy()==0&&DevelopmentController.process(f.p).state()==DevelopmentProcess.State.DONE,"native portable completion pays every IF tick before granting NEW311");
            h.assertTrue(combat.equals(f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&budget.equals(f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"paid learning preserves depleted HP/CP and all existing q/Q ledger bytes");
            h.assertTrue(s.levelExperience==xp&&s.exp(ID)==0,"paid unlock grants neither level XP nor skill mastery");
            var saved=AbilityStorage.encodeSaved(s);h.assertTrue(AbilityStorage.encodeSaved(AbilityStorage.decodeSaved(saved,s.cpDataConfig())).equals(saved),"new learned bit retains native ability save schema");
            h.assertFalse(learnRequest(f.p,ID),"actual ingress cannot purchase learned skill twice");h.succeed();
        }
    }
    private static DeveloperItemEnergy portable(ServerPlayer p,double energy){var stack=new ItemStack(AcademyCraft.DEVELOPER.get());p.setItemInHand(InteractionHand.MAIN_HAND,stack);var battery=new DeveloperItemEnergy(stack,DeveloperType.PORTABLE);battery.energy(energy);return battery;}
    private static boolean learnRequest(ServerPlayer p,String skill){return AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("hp_learn",new HighPowerLearningProtocol.Request(skill,1).wire()));}
    private static void paidLearn(GameTestHelper h,ServerPlayer p,String skill){int level=SkillCatalog.find(AbilityStorage.get(p).category,skill).orElseThrow().level();int pulses=cn.academy.port.core.ClassicRules.learningStimulations(level);var battery=portable(p,DeveloperType.PORTABLE.actualConsumption(pulses));h.assertTrue(learnRequest(p,skill),"actual prerequisite learning ingress starts");for(int tick=0;tick<pulses*DeveloperType.PORTABLE.ticksPerStimulation();tick++)DevelopmentController.tick(p,ignored->{});h.assertTrue(AbilityStorage.get(p).learned(skill)&&battery.energy()==0,"actual prerequisite pays all portable IF");}
    private static void face(LivingEntity e,float yaw){e.setYRot(yaw);e.setYHeadRot(yaw);e.yBodyRot=yaw;e.setXRot(0);}
    private static void shield(LivingEntity e,float yaw){face(e,yaw);e.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));e.startUsingItem(InteractionHand.OFF_HAND);}
    private static void defenses(GameTestHelper h,LivingEntity e){var helmet=new ItemStack(Items.DIAMOND_HELMET);helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),4);helmet.set(DataComponents.UNBREAKABLE,new Unbreakable(true));e.setItemSlot(EquipmentSlot.HEAD,helmet);e.getAttribute(Attributes.ARMOR).setBaseValue(5);e.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0));e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,0));e.setAbsorptionAmount(.5F);}

    public static final class Mutation{
        final Fixture f;final int stage,mutation;boolean called,sawPre,afterPre;float reduction;
        Mutation(Fixture f,int stage,int mutation){this.f=f;this.stage=stage;this.mutation=mutation;}
        void change(){if(called)return;called=true;if(mutation==0)f.p.setPos(f.p.position().add(.1,0,0));else if(mutation==1)f.target.setPos(f.target.position().add(0,4,0));else f.medium().setOwner(f.target);}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent e){if(stage==0&&e.target==f.target&&e.skill.equals(SKILL))change();}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()!=f.target)return;if(stage==1)change();if(stage==3)e.addReductionModifier(DamageContainer.Reduction.ABSORPTION,(container,amount)->{afterPre=sawPre;reduction=amount;change();return amount;});}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre e){if(e.getEntity()!=f.target)return;sawPre=true;if(stage==2)change();}
    }

    /** Adversarial native-data fixture only: the pinned vanilla setter is private.
     * This writes the actual PIERCE_LEVEL SynchedEntityData field; it neither creates
     * nor bypasses any controller, registry generation or damage permit. */
    @SuppressWarnings("unchecked")
    private static void contaminatePinnedNativePierce(DisplacedNeedleEntity medium){
        try{
            var field=net.minecraft.world.entity.projectile.AbstractArrow.class.getDeclaredField("PIERCE_LEVEL");field.setAccessible(true);
            var accessor=(net.minecraft.network.syncher.EntityDataAccessor<Byte>)field.get(null);
            medium.getEntityData().set(accessor,(byte)1);
            if(medium.getPierceLevel()!=1)throw new AssertionError("Pinned native pierce fixture did not take effect");
        }catch(ReflectiveOperationException invalid){throw new AssertionError("Pinned native pierce fixture is unavailable",invalid);}
    }

    private static void assertSource(GameTestHelper h,DamageSource source,ServerPlayer caster,DisplacedNeedleEntity medium,Vec3 arrival){
        h.assertTrue(source!=null&&source.is(DamageTypes.THROWN)&&source.is(DamageTypeTags.IS_PROJECTILE)
                &&source.getEntity()==caster&&source.getDirectEntity()==medium&&arrival.equals(source.getSourcePosition())
                &&arrival.equals(source.sourcePositionRaw()),"native THROWN attribution uses actual medium and actual incoming segment origin");
        h.assertFalse(source.is(DamageTypeTags.BYPASSES_ARMOR)||source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                ||source.is(DamageTypeTags.BYPASSES_EFFECTS)||source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                ||source.is(DamageTypeTags.BYPASSES_SHIELD)||source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                ||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)||source.is(DamageTypeTags.IS_FIRE)
                ||source.is(DamageTypeTags.IS_EXPLOSION),"projection adds no defense bypass or environmental damage family");
    }
    public static final class Observation{
        final LivingEntity target; DamageSource source; Vec3 contactPosition; int posts,deaths; boolean blocked; float damage,armor,effects,enchantments,absorption,blockedDamage;
        Observation(LivingEntity target){this.target=target;}
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target){source=e.getSource();if(source.getDirectEntity() instanceof DisplacedNeedleEntity m)contactPosition=m.position();}}
        @SubscribeEvent public void shield(LivingShieldBlockEvent e){if(e.getEntity()==target){blocked=e.getBlocked();blockedDamage=e.getBlockedDamage();}}
        @SubscribeEvent public void post(LivingDamageEvent.Post e){if(e.getEntity()!=target)return;posts++;damage=e.getNewDamage();armor=e.getReduction(DamageContainer.Reduction.ARMOR);effects=e.getReduction(DamageContainer.Reduction.MOB_EFFECTS);enchantments=e.getReduction(DamageContainer.Reduction.ENCHANTMENTS);absorption=e.getReduction(DamageContainer.Reduction.ABSORPTION);}
        @SubscribeEvent public void death(LivingDeathEvent e){if(e.getEntity()==target){deaths++;source=e.getSource();}}
    }
    public static final class Discount{
        final LivingEntity target;Discount(LivingEntity target){this.target=target;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void skill(SkillAttackEvent e){if(e.target==target&&e.skill.equals(SKILL))e.amount=5;}
    }
    private static final class Fixture implements AutoCloseable{
        final GameTestHelper h;final ServerPlayer p;final Zombie target;final EmbeddedChannel channel;
        final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();
        final Map<BlockPos,BlockState> changed=new LinkedHashMap<>();final Set<ChunkPos> held=new LinkedHashSet<>();final UUID ticket=UUID.randomUUID();
        long inputs;boolean closed;
        Fixture(GameTestHelper h,boolean exact){this(h,exact,3);}
        Fixture(GameTestHelper h,boolean exact,int level){
            this.h=h;Vec3 source=h.absoluteVec(new Vec3(10.5,3,5.5));
            for(int x=((int)source.x-5)>>4;x<=((int)source.x+5)>>4;x++)for(int z=((int)source.z-3)>>4;z<=((int)source.z+24)>>4;z++){
                ChunkPos chunk=new ChunkPos(x,z);held.add(chunk);h.getLevel().getChunkSource().addRegionTicket(TICKET,chunk,2,ticket);h.getLevel().getChunk(x,z);
            }
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"DP"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);acknowledgeLogin();
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.moveTo(source.x,source.y,source.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();
            h.assertTrue(HighPowerCombat.enroll(p,level),"one genuine caster enrollment");var state=AbilityStorage.get(p);state.selectCategory(HighPowerCategories.TELEPORTER);state.setLevel(level);
            state.learn(HighPowerSkills.SAFE_LANDING_PROBE);state.learn(ID);state.activated=true;state.overloadFine=true;h.assertTrue(state.presets.edit(0,0,ID,state::learned),"real learned input slot zero");HighPowerResources.initializeBudget(p);
            p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AcademyCraft.NEEDLE.get(),3));
            target=mob(source.add(0,0,6),exact);aim(target.getBoundingBox().getCenter());
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p,h.testInfo.getTimeoutTicks()+5));
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Zombie mob(Vec3 at,boolean exact){Zombie m=EntityType.HUSK.create(h.getLevel());m.setNoAi(true);m.setNoGravity(true);m.setSilent(true);m.getAttribute(Attributes.ARMOR).setBaseValue(0);m.setPos(at);h.assertTrue(h.getLevel().addFreshEntity(m),"native hostile admitted");entities.add(m);if(exact)h.assertTrue(HighPowerCombat.enroll(m,5),"genuine exact hostile enrollment");return m;}
        void acknowledgeLogin(){channel.runPendingTasks();channel.flushOutbound();channel.runPendingTasks();Object packet;while((packet=channel.readOutbound())!=null){if(packet instanceof ClientboundPlayerPositionPacket position)p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));ReferenceCountUtil.release(packet);}}
        void aim(Vec3 at){Vec3 d=at.subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
        String wire(){return new HighPowerInput.Input(0,++inputs,p.getId(),LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p))).wire();}
        boolean cast(){return DisplacedProjection.start(p,wire());}
        DisplacedNeedleEntity medium(){var m=DisplacedNeedleRegistry.existing(p).orElseThrow();if(!entities.contains(m))entities.add(m);return m;}
        double cp(){return HighPowerResources.cp(p);}
        int needles(){int count=0;for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack s=p.getInventory().getItem(i);if(s.is(AcademyCraft.NEEDLE.get()))count+=s.getCount();}return count;}
        void block(Vec3 at){BlockPos pos=BlockPos.containing(at);changed.putIfAbsent(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        Observation observe(LivingEntity e){Observation o=new Observation(e);hook(o);return o;}
        public void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);DisplacedProjection.cancelActive(p);DisplacedNeedleRegistry.existing(p).ifPresent(m->{if(!entities.contains(m))entities.add(m);});DevelopmentController.abort(p);HighPowerBracerSession.remove(p);AbilityStorage.remove(p);p.server.getPlayerList().remove(p);entities.forEach(Entity::discard);changed.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));held.forEach(chunk->h.getLevel().getChunkSource().removeRegionTicket(TICKET,chunk,2,ticket));channel.finishAndReleaseAll();}
    }
}
