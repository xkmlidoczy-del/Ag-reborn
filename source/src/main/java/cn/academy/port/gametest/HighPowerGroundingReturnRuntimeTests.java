package cn.academy.port.gametest;

import cn.academy.port.AbilityDamage;
import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW-122 native pipeline acceptance; connected stance owner, no gameplay shortcut or damage probe. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerGroundingReturnRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_grounding_return", ELECTRIC="electromaster.forked_arc";
    private HighPowerGroundingReturnRuntimeTests() {}
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void eq(GameTestHelper h,BigDecimal a,BigDecimal b,String message) { h.assertTrue(a.compareTo(b)==0,message+": "+a+" != "+b); }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void grounding_precise_refund_is_spend_capped_once_and_retains_native_cooldown(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{ BigDecimal shield=f.shield(), hp=f.hp();
        h.assertTrue(f.start(1),"authenticated crouch+tap starts actual support stance");
        h.assertTrue(HighPowerCombat.shieldActive(f.p),"HUD composite includes grounding");
        h.assertFalse(f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getBoolean("bracerActive"),"grounding never enables projectile bracer");
        h.assertTrue(f.hit("4"),"first exact electric hit accepted");eq(h,f.shield(),shield,"precision refunds only four spent shield");eq(h,f.hp(),hp,"full shielding retains HP despite native damage");
        h.assertTrue(HighPowerCombat.earnedGrowthPosition(f.p).compareTo(BigDecimal.TWO)>0,"real grounding XP queued inside native frame reaches earned capacity after unwind");
        h.assertTrue(f.hit("8"),"stronger same-tick raw hit passes existing differential seam");eq(h,f.shield(),shield.subtract(n("4")),"second four-point residual spends shield without another refund");
        h.assertFalse(f.hit("8"),"equal raw cooldown hit refused");eq(h,f.shield(),shield.subtract(n("4")),"refused hit has no defense transaction");
        h.assertFalse(f.start(1),"replayed start rejected");
        h.assertFalse(f.start(2),"fresh redundant tap cannot restart active stance");
        h.assertTrue(GroundingReturn.abort(f.p,f.wire(2)),"latest emitted rejected tap still bounds focus cancellation of existing stance");
        h.assertFalse(HighPowerCombat.shieldActive(f.p),"composite HUD ends with stance");
        h.assertFalse(f.start(3),"fresh input cannot bypass sixty-tick activation cooldown");
        eq(h,f.shield(),shield.subtract(n("4")),"abort/restart never refills");
        h.succeed();
            });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void grounding_exact_armor_resistance_protection_absorption_and_transient_zero_are_preserved(GameTestHelper h) {
        Fixture f=new Fixture(h,5);f.ready(()->{
        f.p.getAttribute(Attributes.ARMOR).setBaseValue(20); f.p.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        f.p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,200,0));
        ItemStack boots=new ItemStack(Items.LEATHER_BOOTS);
        boots.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE,1_000_000); // Fixture keeps Protection equipped through bounded native armor wear.
        boots.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),4);
        f.p.setItemSlot(EquipmentSlot.FEET,boots);
        f.p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20); f.p.setAbsorptionAmount(10);
        BigDecimal before=f.shield(),hp=f.hp(); h.assertTrue(f.start(1),"defended stance starts");
        var defenses=new DefenseProfile(BigDecimal.valueOf(f.p.getArmorValue()),BigDecimal.valueOf(f.p.getAttributeValue(Attributes.ARMOR_TOUGHNESS)),1,4);
        BigDecimal damage=DamageMitigation.mitigate(before.divide(BigDecimal.TWO),defenses).subtract(BigDecimal.TEN);
        Observe hook=new Observe(f.p); f.hook(hook);
        h.assertTrue(f.hit(before.divide(BigDecimal.TWO).toPlainString()),"large exact electric transaction accepted");
        h.assertTrue(hook.sawNativeZero,"native million-point witness transiently reduced adapter HP to zero before exact Post");
        h.assertTrue(f.p.isAlive(),"grounding eligibility survives transient native zero HP");eq(h,f.hp(),hp,"shield absorbs fully mitigated exact remainder");
        eq(h,f.shield(),before.subtract(damage).add(before.multiply(new BigDecimal("0.2"))),"armor, Resistance, Protection then ten native absorption then capped shared refund");
        h.assertTrue(f.p.getAbsorptionAmount()==0,"native absorption consumed exactly once");
        h.assertTrue(boots.getDamageValue()>0,"native armor durability stage still ran");
        h.assertTrue(f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getString("shieldEnabled").equals("false"),"serialized global shield remains disabled");
        h.succeed();
            });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void grounding_zero_veto_native_absorption_and_held_shield_never_award_success(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{ BigDecimal before=f.shield(),hp=f.hp();
        f.p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));f.p.startUsingItem(InteractionHand.OFF_HAND);
        h.runAfterDelay(7,()->{
            // Embedded transport does not invoke ServerPlayer.doTick; advance the real native use-item steps.
            for(int i=0;i<7;i++)f.p.doTick();
            f.place(); f.p.setYRot(0);f.p.setYHeadRot(0);h.assertTrue(f.p.isBlocking(),"real shield raising delay elapsed");
            h.assertTrue(f.start(1),"grounding while actual held shield active");
            h.assertFalse(f.hit("10000000000"),"native held shield blocks first, retaining its ordinary return semantics");
            eq(h,f.shield(),before,"native blocking does not spend grounding pool");eq(h,f.hp(),hp,"held shield protected HP");
            f.p.stopUsingItem();f.p.invulnerableTime=0;f.p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20);f.p.setAbsorptionAmount(10);
            h.assertTrue(f.hit("5"),"native-absorption-only hit accepted");eq(h,f.shield(),before,"native absorption covers hit without shared spending");
            Gate gate=new Gate(f.p);gate.zero=true;f.hook(gate);f.p.invulnerableTime=0;
            h.assertTrue(f.hit("10000000000"),"zero-veto retains accepted native hurt behavior");eq(h,f.shield(),before,"zero pre protection leaves shield unchanged");eq(h,f.hp(),hp,"zero pre protection leaves exact HP unchanged");
            gate.zero=false;gate.cancel=true;f.p.invulnerableTime=0;h.assertFalse(f.hit("10000000000"),"incoming cancellation authoritative");eq(h,f.shield(),before,"canceled hit cannot spend or refund");
            h.succeed();
        });
            });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void grounding_whitelist_excludes_fire_melee_railgun_and_accepts_native_lightning_only(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{h.assertTrue(f.start(1),"stance starts");BigDecimal shield=f.shield(),hp=f.hp();
        for(DamageSource source:List.of(f.p.damageSources().generic(),f.p.damageSources().onFire(),f.p.damageSources().playerAttack(f.attacker),
                new AbilityDamage.SkillDamageSource(f.attacker,"electromaster.railgun",false),new AbilityDamage.SkillDamageSource(f.attacker,"electromaster.arc_gen_suffix",false))) {
            f.p.invulnerableTime=0;h.assertTrue(f.hurt(source,3),"unrelated source still performs ordinary native damage");hp=hp.subtract(n("3"));eq(h,f.hp(),hp,"unrelated source not grounded");eq(h,f.shield(),shield,"unrelated source leaves shield unchanged");
        }
        f.p.invulnerableTime=0;h.assertTrue(f.hurt(f.p.damageSources().lightningBolt(),5),"reviewed vanilla environmental lightning accepted in native units");
        eq(h,f.hp(),hp,"environmental lightning remainder is absorbed; "+f.lightningDiagnostic());eq(h,f.shield(),shield,"first precise five-point spend refunds at most five");
        f.p.invulnerableTime=0;h.assertTrue(f.hurt(new AbilityDamage.SkillDamageSource(f.attacker,"electromaster.arc_gen",false),3),"literal legacy electric identity admitted");
        eq(h,f.shield(),shield.subtract(n("3")),"legacy electricity spends only its native units and cannot repeat refund");
        h.succeed();
            });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void grounding_precision_expires_at_six_ticks_and_knockback_receipt_never_leaks(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{h.assertTrue(f.start(1),"stance starts");BigDecimal shield=f.shield(),hp=f.hp();
        h.runAfterDelay(6,()->{
            f.place();f.p.setDeltaMovement(Vec3.ZERO);h.assertTrue(f.hit("100"),"late electric damage accepted");
            eq(h,f.shield(),shield.subtract(n("100")),"six-tick boundary receives no precision refund");eq(h,f.hp(),hp,"late full absorption still protects HP");
            h.assertTrue(Math.abs(f.p.getDeltaMovement().horizontalDistance()-.2)<1e-6,"same-hit native .4 knockback strength halved");
            f.p.setDeltaMovement(Vec3.ZERO);f.p.knockback(.4,1,0);
            h.assertTrue(Math.abs(f.p.getDeltaMovement().horizontalDistance()-.4)<1e-6,"later unrelated native knockback has no leftover receipt");
            f.p.fallDistance=12;Vec3 velocity=f.p.getDeltaMovement();f.p.setPos(f.p.position().add(.1,0,0));GroundingReturn.tick(f.p);
            h.assertFalse(GroundingReturn.active(f.p),"movement cancels grounded context");
            h.assertTrue(f.p.getDeltaMovement().equals(velocity)&&f.p.fallDistance==12,"cancellation does not erase inertia or fall distance");
            h.succeed();
        });
            });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=155)
    public static void grounding_support_loss_pending_abort_identity_and_expiry_are_fenced(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
        h.assertFalse(GroundingReturn.abort(f.p,f.wire(1)),"cancel-before-start retires an authenticated pending input");
        h.assertFalse(f.start(1),"retired start cannot revive");h.assertTrue(f.start(2),"newer input starts");
        h.assertFalse(GroundingReturn.abort(f.p,f.wire(1)),"old cancel cannot kill new session");
        h.setBlock(f.floor,Blocks.AIR);GroundingReturn.tick(f.p);h.assertFalse(GroundingReturn.active(f.p),"actual support removal beats stale onGround flag");
        h.setBlock(f.floor,Blocks.STONE);
        h.runAfterDelay(60,()->{
            f.place();AbilityStorage.get(f.p).cooldowns.remove(GroundingReturn.ID);
            h.assertTrue(f.start(3),"fresh activation after paid native clock expires");
            AbilityStorage.get(f.p).presets.switchTo(1);GroundingReturn.tick(f.p);
            h.assertFalse(GroundingReturn.active(f.p),"preset change cancels owner context");h.succeed();
        });
            });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void grounding_partial_shield_lethal_remainder_still_uses_native_totem_and_death_veto(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{ f.lowerHealthAndShield();h.assertTrue(f.start(1),"low-resource stance starts");
        f.p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.TOTEM_OF_UNDYING));
        h.assertTrue(f.hit("250"),"partial shield leaves real lethal remainder");
        h.assertTrue(f.p.isAlive()&&f.p.getOffhandItem().isEmpty(),"native totem executes once");
        eq(h,f.shield(),n("40"),"precision refunds forty spent shield only, even on lethal remainder");
        h.assertTrue(f.hp().signum()>0,"exact recovery observes native totem rescue");
        h.assertTrue(HighPowerCombat.earnedGrowthPosition(f.p).compareTo(BigDecimal.TWO)>0,"transient lethal health cannot drop genuine absorption XP before native totem rescue");
        GroundingReturn.abort(f.p);f.p.removeAllEffects();f.p.invulnerableTime=0;
        h.succeed();
            });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void grounding_death_veto_restores_hp_but_not_consumed_shield(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{f.lowerHealthAndShield();h.assertTrue(f.start(1),"low-resource stance starts");
        // Consume the one precise refund with a tiny real hit, then create a stronger lethal residual.
        h.assertTrue(f.hit("1"),"one shield spent and returned");Gate gate=new Gate(f.p);gate.death=true;f.hook(gate);
        h.assertTrue(f.hit("251"),"stronger raw250 residual reaches actual death veto");
        eq(h,f.hp(),n("100"),"native death veto restores pre-hit exact HP");eq(h,f.shield(),BigDecimal.ZERO,"death veto never refills spent grounding shield");
        h.succeed();
            });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void grounding_xp_observer_nested_damage_cannot_steal_receipt_or_restore_old_hp(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{h.assertTrue(f.start(1),"stance starts");BigDecimal hp=f.hp();
        Reentry observer=new Reentry(f.p,f.attacker,false);f.hook(observer);f.p.setDeltaMovement(Vec3.ZERO);
        // The existing exact contract reports nested ordinary reentry as unsupported after native side effects.
        f.hit("100");
        h.assertTrue(observer.called && observer.nestedAccepted,"real committed-XP observer performs an ordinary nested hit");
        eq(h,f.hp(),hp.subtract(n("3")),"outer completion never overwrites nested ordinary HP damage");
        h.assertTrue(Math.abs(observer.nestedHorizontal-.4)<1e-6,"nested unrelated source receives full native knockback");
        h.assertTrue(Math.abs(f.p.getDeltaMovement().horizontalDistance()-.6)<1e-6,"poisoned outer receipt cannot reduce its native knockback after reentry");
        f.p.setDeltaMovement(Vec3.ZERO);f.p.knockback(.4,1,0);
        h.assertTrue(Math.abs(f.p.getDeltaMovement().horizontalDistance()-.4)<1e-6,"completed native frame cannot leak its receipt");h.succeed();
            });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void grounding_xp_observer_position_replacement_invalidates_same_hit_receipt(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{h.assertTrue(f.start(1),"stance starts");Reentry observer=new Reentry(f.p,f.attacker,true);f.hook(observer);
        f.p.setDeltaMovement(Vec3.ZERO);h.assertTrue(f.hit("100"),"actual absorption committed before positional observer");
        h.assertTrue(observer.called&&!GroundingReturn.active(f.p),"observer movement ends captured grounding context");
        h.assertTrue(Math.abs(f.p.getDeltaMovement().horizontalDistance()-.4)<1e-6,"position-replaced context cannot use the old absorption receipt");h.succeed();
            });
    }

    public static final class Reentry {
        final ServerPlayer target,attacker;final boolean move;boolean called,nestedAccepted;double nestedHorizontal;
        Reentry(ServerPlayer target,ServerPlayer attacker,boolean move){this.target=target;this.attacker=attacker;this.move=move;}
        @SubscribeEvent public void experience(cn.academy.port.api.SkillExpAddedEvent event) {
            if(event.getEntity()!=target||!event.skill.equals(GroundingReturn.ID)||called)return;
            called=true;
            if(move){target.setPos(target.position().add(.1,0,0));return;}
            target.invulnerableTime=0;target.setDeltaMovement(Vec3.ZERO);
            nestedAccepted=target.hurt(target.damageSources().playerAttack(attacker),3);
            nestedHorizontal=target.getDeltaMovement().horizontalDistance();
        }
    }

    public static final class Observe {
        final LivingEntity target;boolean sawNativeZero;
        Observe(LivingEntity target){this.target=target;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e){if(e.getEntity()==target&&target.getHealth()==0)sawNativeZero=true;}
    }
    public static final class Gate {
        final LivingEntity target;boolean cancel,zero,death;
        Gate(LivingEntity target){this.target=target;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target&&cancel)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void pre(LivingDamageEvent.Pre e){if(e.getEntity()==target&&zero)e.setNewDamage(0);}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void death(LivingDeathEvent e){if(e.getEntity()==target&&death)e.setCanceled(true);}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p,attacker;final EmbeddedChannel channel;
        final BlockPos floor=new BlockPos(5,1,5);final List<Object> hooks=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this(h,2);}
        Fixture(GameTestHelper h,int level){
            this.h=h;h.setBlock(floor,Blocks.STONE);var server=h.getLevel().getServer();
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPGroundProbe"),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;place();
            h.assertTrue(HighPowerCombat.enroll(p,level),"approved connected defender");
            var state=AbilityStorage.get(p);state.selectCategory("electromaster");state.setLevel(level);state.learn("forked_arc");state.learn("magnetic_bracer");state.learn(GroundingReturn.ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,GroundingReturn.ID,id->id.equals(GroundingReturn.ID)),"genuine learned slot");
            ElectromasterPrototype.initializeBudget(p);epoch();
            attacker=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[HP-Electric]"));attacker.setPos(p.position().add(0,0,3));attacker.setNoGravity(true);attacker.getAbilities().instabuild=false;
            var attackerState=AbilityStorage.get(attacker);attackerState.changeCategoryClassic("electromaster");attackerState.setLevel(5);
            h.assertTrue(HighPowerCombat.enroll(attacker,5),"approved exact source actor");
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        void ready(Runnable assertions){
            // Real connected players retain the native sixty-tick login protection; never bypass its private field.
            h.runAfterDelay(61,()->{
                p.doTick();place();
                h.assertTrue(cn.academy.port.AcademyConfig.ATTACK_PLAYERS.get(),"fixture config permits ability PvP");
                h.assertTrue(withPvp(()->p.canHarmPlayer(attacker)),"scoped ordinary server PvP policy permits the fixture attacker");
                assertions.run();
            });
        }
        void place(){Vec3 at=h.absoluteVec(new Vec3(5.5,2,5.5));p.moveTo(at.x,at.y,at.z,0,0);p.setYHeadRot(0);p.setShiftKeyDown(true);p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        boolean start(long input){return GroundingReturn.start(p,wire(input));}
        /** GameTestServer disables PvP. Scope the public native server setting to one synchronous hit, including its observers. */
        boolean withPvp(java.util.function.BooleanSupplier action){
            boolean original=p.server.isPvpAllowed();p.server.setPvpAllowed(true);
            try{return action.getAsBoolean();}finally{p.server.setPvpAllowed(original);}
        }
        boolean hit(String amount){return withPvp(()->HighPowerCombat.attack(attacker,ELECTRIC,p,n(amount)));}
        boolean hurt(DamageSource source,float amount){return withPvp(()->p.hurt(source,amount));}
        String lightningDiagnostic(){
            var source=p.damageSources().lightningBolt();var resources=p.server.getResourceManager();
            var stacks=new ArrayList<String>();
            for(String path:List.of("damage_type/lightning_bolt.json","tags/damage_type/is_lightning.json","tags/damage_type/no_knockback.json"))
                stacks.add(path+"="+resources.getResourceStack(net.minecraft.resources.ResourceLocation.withDefaultNamespace(path)).stream()
                        .map(r->r.sourcePackId()+":"+r.knownPackInfo()).toList());
            return "active="+GroundingReturn.active(p)+", class="+source.getClass()+", lightning="+source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)
                    +", noKnockback="+source.is(net.minecraft.tags.DamageTypeTags.NO_KNOCKBACK)+", fire="+source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                    +", armorBypass="+source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)+", source="+source.getEntity()+", direct="+source.getDirectEntity()+", resources="+stacks;
        }
        BigDecimal hp(){return n(HighPowerCombat.healthText(p));}BigDecimal shield(){return HighPowerCombat.shield(p);}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void lowerHealthAndShield(){
            h.assertTrue(withPvp(()->HighPowerCombat.attack(attacker,"electromaster.railgun",p,HighPowerLevel.LV2.maxHealth().subtract(n("100")))),"real non-electric bridge lowers HP to100");
            h.assertTrue(HighPowerCombat.spendShield(p,shield().subtract(n("40"))),"fixture explicitly spends to forty shared shield");p.invulnerableTime=0;place();
        }
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);GroundingReturn.remove(p);AbilityStorage.remove(p);AbilityStorage.remove(attacker);attacker.discard();h.getLevel().getServer().getPlayerList().remove(p);channel.finishAndReleaseAll();}
    }
}
