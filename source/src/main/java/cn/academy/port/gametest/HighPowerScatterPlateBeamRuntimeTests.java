package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Actual NEW211 permits and paired native PLAYER_ATTACK controls through NEW212. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerScatterPlateBeamRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_scatter_plate_beam";
    private static BigDecimal n(String amount){return new BigDecimal(amount);}
    private static BigDecimal hp(ServerPlayer p){return n(HighPowerCombat.healthText(p));}
    private static ScatterPlateShares shares(ServerPlayer p){return ScatterPlateStorage.read(p).orElseThrow();}
    private static void eq(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+": "+actual+" != "+expected);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void genuine_hand_beam_exhausts_only_contacted_plate_and_retains_native_player_attack(GameTestHelper h){
        Fixture f=new Fixture(h);ServerPlayer caster=f.player(6,5,true),defender=f.player(6,12,true),control=f.player(16,12,false);
        f.ready(()->{
        f.arm(defender,"6",180);f.aim(caster,defender);Observation hit=f.observe(defender),nativeHit=f.observe(control);
        BigDecimal health=hp(defender);Vec3 origin=DualPoint.hand(caster,0);
        h.assertTrue(f.hurt(control,f.source(caster,origin.add(10,0,0)),5),"paired native control accepts PLAYER_ATTACK");
        h.assertTrue(f.cast(caster),"live NEW211 controller supplies unforgeable permit");
        eq(h,HighPowerCombat.shield(defender),n("4"),"only contacted two-point share is spent");
        eq(h,shares(defender).plate(1).current(),BigDecimal.ZERO,"middle plate breaks");
        eq(h,shares(defender).plate(0).current(),n("2"),"left plate cannot lend resources");eq(h,shares(defender).plate(2).current(),n("2"),"right plate cannot lend resources");
        eq(h,hp(defender),health.subtract(n("3")),"only partial-contact residual reaches exact HP");
        h.assertTrue(control.getHealth()==15&&hit.posts==1&&nativeHit.posts==1&&hit.damage==nativeHit.damage,"one unchanged native damage pipeline for each target");
        h.assertTrue(hit.source.is(DamageTypes.PLAYER_ATTACK)&&hit.source.getDirectEntity()==caster&&origin.equals(hit.source.getSourcePosition()),"real hand origin and player attribution survive contact");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void authenticated_beam_plate_spend_follows_native_armor_resistance_protection_and_absorption(GameTestHelper h){
        Fixture f=new Fixture(h);ServerPlayer caster=f.player(6,5,true),defender=f.player(6,12,true),control=f.player(16,12,false);
        f.ready(()->{
        f.arm(defender,null,180);defenses(h,defender);defenses(h,control);f.aim(caster,defender);
        Observation hit=f.observe(defender),nativeHit=f.observe(control);BigDecimal base=HighPowerCombat.shield(defender),health=hp(defender);
        BigDecimal remainder=DamageMitigation.mitigate(n("5"),new DefenseProfile(BigDecimal.valueOf(defender.getArmorValue()),
                BigDecimal.valueOf(defender.getAttributeValue(Attributes.ARMOR_TOUGHNESS)),1,4)).subtract(n(".5"));
        h.assertTrue(f.hurt(control,f.source(caster,DualPoint.hand(caster,0).add(10,0,0)),5),"ordinary matching native control");h.assertTrue(f.cast(caster),"real hand beam emitted");
        eq(h,HighPowerCombat.shield(defender),base.subtract(remainder),"exact contacted debit is after every native reducer");eq(h,hp(defender),health,"plate preserves HP only for final remainder");
        h.assertTrue(hit.posts==1&&nativeHit.posts==1&&hit.armor>0&&hit.effects>0&&hit.enchantments>0&&hit.absorption==.5F,"all native mitigation stages execute once");
        h.assertTrue(hit.armor==nativeHit.armor&&hit.effects==nativeHit.effects&&hit.enchantments==nativeHit.enchantments&&hit.absorption==nativeHit.absorption
                &&Math.abs(hit.damage-nativeHit.damage)<.00001,"paired native reductions match before plate allocation");
        h.assertTrue(defender.getAbsorptionAmount()==0&&control.getAbsorptionAmount()==0,"native absorption consumed once");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void actual_beam_through_real_gap_or_from_rear_never_debits_any_share(GameTestHelper h){
        Fixture f=new Fixture(h);
        ServerPlayer[] casters=new ServerPlayer[2],defenders=new ServerPlayer[2];
        for(int i=0;i<2;i++){casters[i]=f.player(6+i*14,5,true);defenders[i]=f.player(6+i*14,12,true);}
        f.ready(()->{
        for(int i=0;i<2;i++){
            ServerPlayer caster=casters[i],defender=defenders[i];f.arm(defender,null,i==0?201:0);f.aim(caster,defender);
            BigDecimal base=HighPowerCombat.shield(defender),health=hp(defender);var prior=shares(defender).toDecimalMap();
            h.assertTrue(f.cast(caster),"actual native beam hits body through "+(i==0?"plate gap":"rear"));
            eq(h,hp(defender),health.subtract(n("5")),"body damage unchanged for missed physical plate");eq(h,HighPowerCombat.shield(defender),base,"missed geometry has no shield debit");
            h.assertTrue(shares(defender).toDecimalMap().equals(prior),"no generic forcefield or synthetic source-to-center contact");
        }h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void incoming_cancel_controller_abort_and_invalid_sidecar_preserve_native_outcomes_without_contact_debit(GameTestHelper h){
        Fixture f=new Fixture(h);
        ServerPlayer[] casters=new ServerPlayer[3],defenders=new ServerPlayer[3];
        for(int i=0;i<3;i++){casters[i]=f.player(6+i*14,5,true);defenders[i]=f.player(6+i*14,12,true);}
        f.ready(()->{
        for(int stage=0;stage<3;stage++){
            ServerPlayer caster=casters[stage],defender=defenders[stage];f.arm(defender,null,180);f.aim(caster,defender);
            BigDecimal health=hp(defender),base=HighPowerCombat.shield(defender);var gate=new Gate(defender,stage);f.hook(gate);
            h.assertTrue(f.cast(caster),"paid cast reaches native callback stage "+stage);h.assertTrue(gate.called,"native incoming callback executed");
            eq(h,HighPowerCombat.shield(defender),base,"no stale capture debits after cancel/abort/corruption");
            eq(h,hp(defender),stage==0?health:health.subtract(n("5")),"native cancellation and normal body damage remain authoritative");
        }h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_absorption_zero_and_hurt_cooldown_rejection_do_not_charge_plate(GameTestHelper h){
        Fixture f=new Fixture(h);ServerPlayer caster=f.player(6,5,true),defender=f.player(6,12,true),second=f.player(1,5,true);
        f.ready(()->{
        f.arm(defender,null,180);f.aim(caster,defender);
        defender.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,2));defender.setAbsorptionAmount(10);
        BigDecimal base=HighPowerCombat.shield(defender),health=hp(defender);h.assertTrue(f.cast(caster),"absorbed actual beam emits");
        eq(h,HighPowerCombat.shield(defender),base,"zero post-absorption damage spends no plate");eq(h,hp(defender),health,"native absorption preserves HP");
        h.assertTrue(defender.getAbsorptionAmount()==5,"native absorption pays exactly once");
        Vec3 oldOrigin=caster.position();caster.setPos(caster.position().add(-5,0,0));second.setPos(oldOrigin);f.aim(second,defender);h.assertTrue(f.cast(second),"second independent paid cast reaches native iframe decision");
        eq(h,HighPowerCombat.shield(defender),base,"same-rank native iframe refusal cannot debit shares");h.assertTrue(defender.getAbsorptionAmount()==5,"iframe refusal consumes no second absorption");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void raised_native_player_shield_blocks_before_any_plate_spend_with_paired_control(GameTestHelper h){
        Fixture f=new Fixture(h);ServerPlayer caster=f.player(6,5,true),defender=f.player(6,12,true),control=f.player(16,12,false);
        f.ready(()->{
        f.arm(defender,null,180);for(var p:List.of(defender,control)){face(p,180);p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));p.startUsingItem(InteractionHand.OFF_HAND);}
        Observation hit=f.observe(defender),nativeHit=f.observe(control);BigDecimal base=HighPowerCombat.shield(defender),health=hp(defender);
        f.hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(defender,8));f.hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(control,8));
        h.runAfterDelay(7,()->{
            face(defender,180);face(control,180);h.assertTrue(defender.isBlocking()&&control.isBlocking(),"native raising delay completed");f.aim(caster,defender);
            h.assertFalse(f.hurt(control,f.source(caster,DualPoint.hand(caster,0).add(10,0,0)),5),"native paired shield blocks");h.assertTrue(f.cast(caster),"native beam cast independent of block outcome");
            eq(h,HighPowerCombat.shield(defender),base,"native player shield prevents plate debit");eq(h,hp(defender),health,"blocked native beam does not injure");
            h.assertTrue(hit.blocked&&nativeHit.blocked&&hit.blockedDamage==nativeHit.blockedDamage,"same native shield decision and durability amount");h.succeed();
        });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_death_veto_restores_hp_without_refunding_already_contacted_share(GameTestHelper h){
        Fixture f=new Fixture(h);ServerPlayer caster=f.player(6,5,true),defender=f.player(6,12,true);
        f.ready(()->{
        f.arm(defender,"6",180);defender.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health","1");f.aim(caster,defender);
        var veto=new DeathVeto(defender);f.hook(veto);h.assertTrue(f.cast(caster),"genuine native beam reaches lethal residual");
        h.assertTrue(veto.called,"native death decision and its veto execute after plate settlement");
        eq(h,hp(defender),BigDecimal.ONE,"native death veto restores original exact HP");eq(h,HighPowerCombat.shield(defender),n("4"),"death veto never refunds contacted resources");
        eq(h,shares(defender).plate(1).current(),BigDecimal.ZERO,"broken plate remains broken after veto");h.succeed();
        });
    }

    private static void face(ServerPlayer p,float yaw){p.setYRot(yaw);p.setYHeadRot(yaw);p.yBodyRot=yaw;p.setXRot(0);}
    private static void defenses(GameTestHelper h,LivingEntity p){
        ItemStack helmet=new ItemStack(Items.DIAMOND_HELMET);helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),4);
        helmet.set(DataComponents.UNBREAKABLE,new Unbreakable(true));p.setItemSlot(EquipmentSlot.HEAD,helmet);
        p.getAttribute(Attributes.ARMOR).setBaseValue(5);p.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0));p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,0));p.setAbsorptionAmount(.5F);
    }
    public static final class Gate{
        final ServerPlayer p;final int stage;boolean called;Gate(ServerPlayer p,int stage){this.p=p;this.stage=stage;}
        @SubscribeEvent(priority=EventPriority.NORMAL)public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()!=p)return;called=true;
            if(stage==0)e.setCanceled(true);else if(stage==1)ScatterPlates.abort(p);
            else p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(ScatterPlateStorage.KEY).putString("plate1","0");}
    }
    public static final class Discount{
        @SubscribeEvent(priority=EventPriority.HIGHEST)public void skill(SkillAttackEvent e){if(e.skill.equals("meltdowner.dual_point"))e.amount=5;}
    }
    public static final class DeathVeto{
        final ServerPlayer p;boolean called;DeathVeto(ServerPlayer p){this.p=p;}
        @SubscribeEvent(priority=EventPriority.NORMAL)public void death(LivingDeathEvent e){if(e.getEntity()==p){called=true;e.setCanceled(true);}}
    }
    public static final class Observation{
        final LivingEntity target;DamageSource source;int posts;boolean blocked;float damage,armor,effects,enchantments,absorption,blockedDamage;
        Observation(LivingEntity target){this.target=target;}
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target)source=e.getSource();}
        @SubscribeEvent public void shield(LivingShieldBlockEvent e){if(e.getEntity()==target){blocked=e.getBlocked();blockedDamage=e.getBlockedDamage();}}
        @SubscribeEvent public void post(LivingDamageEvent.Post e){if(e.getEntity()!=target)return;posts++;damage=e.getNewDamage();armor=e.getReduction(DamageContainer.Reduction.ARMOR);effects=e.getReduction(DamageContainer.Reduction.MOB_EFFECTS);enchantments=e.getReduction(DamageContainer.Reduction.ENCHANTMENTS);absorption=e.getReduction(DamageContainer.Reduction.ABSORPTION);}
    }
    private static final class Fixture{
        final GameTestHelper h;final List<ServerPlayer> players=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final Map<ServerPlayer,Vec3> positions=new IdentityHashMap<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;hook(new Discount());h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});}
        ServerPlayer player(double x,double z,boolean enrolled){var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPPlateBeam"),false);
            var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());players.add(p);var connection=new Connection(PacketFlow.SERVERBOUND);channels.add(new EmbeddedChannel(connection));NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setNoGravity(true);p.setGameMode(GameType.SURVIVAL);p.setPos(h.absoluteVec(new Vec3(x,2,z)));p.connection.resetPosition();
            positions.put(p,p.position());
            if(enrolled){var s=AbilityStorage.get(p);s.changeCategoryClassic(HighPowerCategories.MELTDOWNER);s.setLevel(1);s.learn(HighPowerSkills.DUAL_POINT);s.setActivateState(true);s.overloadFine=true;s.presets.edit(0,0,HighPowerSkills.DUAL_POINT,s::learned);
                h.assertTrue(HighPowerCombat.enroll(p,1),"connected native high-power fixture");HighPowerResources.initializeBudget(p);}return p;}
        /** Real native server ticks expire login invulnerability; no protection field or guard is bypassed. */
        void ready(Runnable action){
            for(var p:players)hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p,61));
            h.runAfterDelay(61,()->{for(var p:players){p.doTick();p.setPos(positions.get(p));p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();}action.run();});
        }
        void arm(ServerPlayer p,String existingBase,float yaw){if(existingBase!=null)p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield",existingBase);
            var s=AbilityStorage.get(p);s.learn(HighPowerSkills.SCATTER_PLATES);s.presets.edit(0,0,HighPowerSkills.SCATTER_PLATES,s::learned);ScatterPlateStorage.ensure(p);face(p,yaw);
            long epoch=LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),s);h.assertTrue(ScatterPlates.toggle(p,new HighPowerInput.Input(0,1,p.getId(),epoch).wire()),"paid real NEW212 activation");}
        void aim(ServerPlayer p,LivingEntity target){Vec3 d=target.getBoundingBox().getCenter().subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));p.setShiftKeyDown(false);}
        boolean cast(ServerPlayer p){long epoch=LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));return pvp(()->DualPoint.tap(p,new HighPowerInput.Input(0,1,p.getId(),epoch).wire()));}
        boolean hurt(ServerPlayer p,DamageSource source,float amount){return pvp(()->p.hurt(source,amount));}
        boolean pvp(java.util.function.BooleanSupplier action){boolean old=h.getLevel().getServer().isPvpAllowed();h.getLevel().getServer().setPvpAllowed(true);try{return action.getAsBoolean();}finally{h.getLevel().getServer().setPvpAllowed(old);}}
        DamageSource source(ServerPlayer p,Vec3 origin){return new DamageSource(p.damageSources().playerAttack(p).typeHolder(),p,p,origin);}
        Observation observe(LivingEntity p){var result=new Observation(p);hook(result);return result;}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);for(var p:players){ScatterPlates.remove(p);DualPoint.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);}channels.forEach(EmbeddedChannel::finishAndReleaseAll);}
    }
}
