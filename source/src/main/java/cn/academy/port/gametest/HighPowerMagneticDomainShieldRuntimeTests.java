package cn.academy.port.gametest;

import cn.academy.port.AbilityDamage;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillExpAddedEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW152 native two-tranche ledger, persistence and defense ordering acceptance. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerMagneticDomainShieldRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_magnetic_domain_shield", ELECTRIC="electromaster.forked_arc";
    private static BigDecimal n(String value){return new BigDecimal(value);}
    private static void eq(GameTestHelper h,BigDecimal a,BigDecimal b,String why){h.assertTrue(a.compareTo(b)==0,why+": "+a+" != "+b);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=140)
    public static void domain_reserve_starts_zero_activation_save_reload_and_category_do_not_refill(GameTestHelper h){
        Fixture f=new Fixture(h);
        eq(h,f.reserve(),BigDecimal.ZERO,"learning exposes a zero reserve");
        h.assertFalse(f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).contains(MagneticDomainReserve.KEY),"read-only reserve queries never write migration");
        f.ready(()->{
            h.assertTrue(f.reserve().signum()>0,"real quiet tick earns reserve after base is full");
            BigDecimal reserve=f.reserve(),base=f.shield(),hp=f.hp();
            h.assertTrue(f.toggle(1),"paid domain activation");eq(h,f.reserve(),reserve,"activation does not fill reserve");eq(h,f.shield(),base,"activation keeps base unchanged");
            MagneticDomain.abort(f.p);eq(h,f.reserve(),reserve,"deactivation does not clamp reserve");
            AbilityStorage.save(f.p);CompoundTag saved=new CompoundTag();f.p.saveWithoutId(saved);MagneticDomain.remove(f.p);AbilityStorage.remove(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);f.p.load(saved);f.place();f.epoch();
            eq(h,f.reserve(),reserve,"reload preserves exact reserve");eq(h,f.shield(),base,"reload preserves base");eq(h,f.hp(),hp,"reload preserves HP");
            AbilityStorage.get(f.p).category="meltdowner";eq(h,f.reserve(),BigDecimal.ZERO,"category suspends access");
            AbilityStorage.get(f.p).category="electromaster";eq(h,f.reserve(),reserve,"category return exposes unchanged stored amount");
            h.assertFalse(MagneticDomain.active(f.p),"reload cannot restore activation");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=145)
    public static void domain_quiet_recovery_has_one_budget_base_first_and_active_blocks_both(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            BigDecimal reserve=f.reserve(),cap=n(HighPowerCombat.maxShieldText(f.p)),budget=cap.divide(n("100"));
            h.assertTrue(HighPowerCombat.spendShield(f.p,budget.divide(n("2"))),"ordinary cost consumes base only");
            eq(h,f.reserve(),reserve,"old skills never consume reserve");BigDecimal base=f.shield();
            h.assertTrue(f.toggle(1),"domain active");
            // Connected mocks do not receive a real client network pump. Run genuine player ticks;
            // this also exercises shield recovery before/after the controller's paid-window renewal.
            f.hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(f.p,61));
            h.runAfterDelay(61,()->{
                h.assertTrue(MagneticDomain.active(f.p),"paid native domain remains active throughout recovery witness");
                f.place();HighPowerCombat.tickShield(f.p);eq(h,f.shield(),base,"active domain suppresses base recovery");eq(h,f.reserve(),reserve,"active domain suppresses reserve recovery");
                MagneticDomain.abort(f.p);h.runAfterDelay(1,()->{
                    HighPowerCombat.tickShield(f.p);eq(h,f.shield(),cap,"single budget fills remaining base first");
                    eq(h,f.reserve(),reserve.add(budget.divide(n("2"))),"only unused half budget enters reserve");h.succeed();
                });
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=165)
    public static void domain_recovery_before_due_native_renewal_cannot_award_a_free_tick(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(HighPowerCombat.spendShield(f.p,n("10")),"real base expense starts quiet delay");
            h.assertTrue(f.toggle(1),"domain paid activation");RenewalRecoveryWitness witness=new RenewalRecoveryWitness(f.p);f.hook(witness);
            f.hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(f.p,81));
            h.runAfterDelay(81,()->{
                h.assertTrue(witness.boundaries>0,"native highest-priority observer queried recovery before a due renewal after quiet delay");
                h.assertTrue(witness.exactTiming,"due boundaries align to independently counted20actual server ticks from activation");
                h.assertTrue(witness.expiredBenefitsAbsent,"pending renewal grants no expired paid damage benefit");
                h.assertTrue(witness.noCredit,"unexpired controller ownership blocks both tranches before same-tick renewal");
                h.assertTrue(MagneticDomain.active(f.p),"ordinary renewal keeps paid damage benefits active");h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_malformed_reserve_fails_closed_without_invalidating_base_combat(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            CompoundTag bad=new CompoundTag();bad.putInt("version",999);bad.putString("amount","1");bad.putString("capacity",HighPowerCombat.maxShieldText(f.p));
            f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).put(MagneticDomainReserve.KEY,bad);
            h.assertTrue(HighPowerCombat.enabled(f.p),"healthy base ledger remains enrolled");h.assertFalse(MagneticDomainReserve.available(f.p),"future reserve schema unavailable");
            h.assertFalse(f.toggle(1),"malformed reserve cannot activate domain");BigDecimal hp=f.hp(),base=f.shield();
            h.assertTrue(HighPowerCombat.spendShield(f.p,n("3")),"old shield methods still work");eq(h,f.shield(),base.subtract(n("3")),"ordinary exact cost unchanged");
            h.assertTrue(f.hit("5"),"ordinary exact native damage still accepted");eq(h,f.hp(),hp.subtract(n("5")),"malformed reserve grants no hidden protection");
            h.assertTrue(HighPowerCombat.resetGrowthContract(f.p),"corrupt optional reserve cannot block valid base reset");
            h.assertFalse(MagneticDomainReserve.available(f.p),"reset cannot revive corrupt reserve");eq(h,f.hp(),hp.subtract(n("5")),"base reset preserves injury");
            h.assertTrue(f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(MagneticDomainReserve.KEY).equals(bad),"unknown schema remains untouched");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_rear_damage_is_shielded_but_unknown_beams_bypass_only_this_layer(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(f.toggle(1),"active domain");f.attacker.setPos(f.p.position().add(0,0,-3));BigDecimal hp=f.hp(),base=f.shield(),reserve=f.reserve();
            h.assertTrue(f.hit("7"),"rear reviewed electrical source accepted");eq(h,f.shield(),base.subtract(n("7")),"rear source uses domain base tranche");eq(h,f.hp(),hp,"rear shield protects HP");eq(h,f.reserve(),reserve,"base precedes reserve");
            f.p.invulnerableTime=0;h.assertTrue(f.hurt(new AbilityDamage.SkillDamageSource(f.attacker,"meltdowner.meltdowner",false),3),"unreviewed ACA beam retains ordinary native hit");
            eq(h,f.hp(),hp.subtract(n("3")),"unknown beam receives no universal shield compatibility");eq(h,f.shield(),base.subtract(n("7")),"unknown beam cannot spend base via domain");
            f.p.invulnerableTime=0;h.assertTrue(f.hurt(new AbilityDamage.SkillDamageSource(f.attacker,"electromaster.arc_gen",true),2),"bypass source native hit");eq(h,f.hp(),hp.subtract(n("5")),"bypass armor source never admitted");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_partial_exhaustion_spends_both_once_then_damages_hp(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            BigDecimal reserve=f.reserve(),hp=f.hp();h.assertTrue(reserve.signum()>0,"earned reserve fixture");
            h.assertTrue(HighPowerCombat.spendShield(f.p,f.shield().subtract(n("30"))),"ordinary spend reduces only base");
            h.assertTrue(f.toggle(1),"active domain");h.assertTrue(f.hit(reserve.add(n("45")).toPlainString()),"native exact hit exhausts both tranches");
            eq(h,f.shield(),BigDecimal.ZERO,"base exhausted once");eq(h,f.reserve(),BigDecimal.ZERO,"reserve exhausted once");eq(h,f.hp(),hp.subtract(n("15")),"only remainder reaches HP");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_arrow_fee_is_fixed_base_percentage_atomic_and_cannot_spend_hp(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            BigDecimal fee=n(HighPowerCombat.maxShieldText(f.p)).divide(n("100")),half=fee.divide(n("2"));
            h.assertTrue(HighPowerCombat.spendShield(f.p,f.shield().subtract(half)),"base holds half of one fee");
            h.assertTrue(f.toggle(1),"domain active");BigDecimal reserve=f.reserve(),hp=f.hp();h.assertTrue(reserve.compareTo(half)>=0,"quiet earned reserve can pay other half");
            h.assertFalse(MagneticDomainReserve.debitArrow(f.p,fee,()->false),"failed arrow context validation is free");
            eq(h,f.shield(),half,"failed validation preserves base");eq(h,f.reserve(),reserve,"failed validation preserves reserve");
            h.assertFalse(MagneticDomainReserve.debitArrow(f.p,fee.multiply(n("2")),()->true),"fee cannot use combined capacity percentage");
            h.assertTrue(MagneticDomainReserve.debitArrow(f.p,fee,()->true),"one exact approved arrow charge");
            eq(h,f.shield(),BigDecimal.ZERO,"fee consumes base half once");eq(h,f.reserve(),reserve.subtract(half),"fee consumes reserve half once");eq(h,f.hp(),hp,"fee never costs HP");
            BigDecimal left=f.reserve();h.assertTrue(f.hit(left.add(n("1")).toPlainString()),"native damage exhausts remaining reserve");BigDecimal hurt=f.hp();
            h.assertFalse(MagneticDomainReserve.debitArrow(f.p,fee,()->true),"unaffordable arrow cannot borrow HP");eq(h,f.hp(),hurt,"failed fee leaves injury unchanged");eq(h,f.reserve(),BigDecimal.ZERO,"failed fee cannot create reserve");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_grounding_then_buffer_precedence_never_spends_reserve(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            var state=AbilityStorage.get(f.p);h.assertTrue(state.presets.edit(0,1,PotentialBuffer.ID,id->true),"bind142");h.assertTrue(state.presets.edit(0,2,GroundingReturn.ID,id->true),"bind122");f.epoch();f.p.setShiftKeyDown(true);f.place();
            h.assertTrue(f.toggle(1),"arm152");h.assertTrue(PotentialBuffer.toggle(f.p,new HighPowerInput.Input(1,2,f.p.getId(),f.epoch()).wire()),"arm142");h.assertTrue(GroundingReturn.start(f.p,new HighPowerInput.Input(2,3,f.p.getId(),f.epoch()).wire()),"arm122");
            BigDecimal base=f.shield(),reserve=f.reserve(),hp=f.hp();h.assertTrue(f.hit("5"),"all three active electrical hit");eq(h,f.shield(),base,"precise122 refund unchanged");eq(h,f.reserve(),reserve,"122 never touches reserve");
            GroundingReturn.abort(f.p);f.p.invulnerableTime=0;h.assertTrue(HighPowerCombat.spendShield(f.p,f.shield().subtract(n("3"))),"reduce base for partial142 witness");h.assertTrue(f.hit("5"),"142 precedence over domain");
            eq(h,f.shield(),BigDecimal.ZERO,"142 spends base once");eq(h,f.reserve(),reserve,"142 partial exhaustion cannot fall through to reserve");eq(h,f.hp(),hp.subtract(n("2")),"142 remainder follows its original HP contract");
            PotentialBuffer.abort(f.p);f.p.invulnerableTime=0;h.assertTrue(f.hit("5"),"152 now sole defense");eq(h,f.reserve(),reserve.subtract(n("5")),"domain alone may use reserve");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_lethal_remainder_keeps_native_totem_and_consumed_reserve(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(f.withPvp(()->HighPowerCombat.attack(f.attacker,"electromaster.railgun",f.p,f.hp().subtract(n("100")))),"native unshielded fixture injury");
            h.assertTrue(HighPowerCombat.spendShield(f.p,f.shield().subtract(n("30"))),"base30");f.p.invulnerableTime=0;f.place();BigDecimal reserve=f.reserve();h.assertTrue(f.toggle(1),"arm lowHP domain");
            f.p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.TOTEM_OF_UNDYING));h.assertTrue(f.hit(reserve.add(n("230")).toPlainString()),"lethal remainder accepted");
            h.assertTrue(f.p.isAlive()&&f.p.getOffhandItem().isEmpty(),"native totem consumed once");eq(h,f.shield(),BigDecimal.ZERO,"totem cannot refill base");eq(h,f.reserve(),BigDecimal.ZERO,"totem cannot refill reserve");h.assertTrue(f.hp().signum()>0,"native rescue reconciles exactHP");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_native_death_and_post_callback_reentry_preserve_committed_debits(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(f.toggle(1),"domain armed");BigDecimal hp=f.hp(),base=f.shield(),reserve=f.reserve();
            PostAction hook=new PostAction(f.p,()->{f.p.invulnerableTime=0;f.p.hurt(f.p.damageSources().generic(),3);});f.hook(hook);f.hit("5");
            h.assertTrue(hook.called,"native post callback ran");eq(h,f.hp(),hp.subtract(n("3")),"outer frame does not overwrite nestedHP");eq(h,f.shield(),base.subtract(n("5")),"outer base debit committed before callback");eq(h,f.reserve(),reserve,"no duplicate reserve debit");
            MagneticDomain.abort(f.p);f.p.invulnerableTime=0;f.withPvp(()->HighPowerCombat.attack(f.attacker,"electromaster.railgun",f.p,f.hp()));h.assertFalse(f.p.isAlive(),"ordinary native death still authoritative");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_paired_native_armor_toughness_resistance_protection_absorption_precede_shared_shield(GameTestHelper h) {
        Fixture f=new Fixture(h),control=new Fixture(h);f.ready(()->{
            control.p.doTick();control.place();
            // A true native HP control has no exact ledger and no active defense mode.
            control.p.getPersistentData().remove(HighPowerCombat.DATA_KEY);
            for(ServerPlayer p:List.of(f.p,control.p)) {
                p.getAttribute(Attributes.ARMOR).setBaseValue(20);p.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,200,0));
                ItemStack boots=new ItemStack(Items.LEATHER_BOOTS);boots.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE,1_000_000);
                boots.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),4);
                p.setItemSlot(EquipmentSlot.FEET,boots);p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20);p.setAbsorptionAmount(2);
            }
            Zombie source=f.mob(0,3);MitigationObserve shielded=new MitigationObserve(f.p),nativeOnly=new MitigationObserve(control.p);f.hook(shielded);f.hook(nativeOnly);
            h.assertTrue(f.toggle(1),"only exact owner arms152");h.assertFalse(MagneticDomain.active(control.p)||PotentialBuffer.active(control.p)||GroundingReturn.active(control.p),"native control has all layers off");
            BigDecimal hp=f.hp(),shield=f.shield();float nativeHp=control.p.getHealth();
            h.assertTrue(control.hurt(control.p.damageSources().mobAttack(source),20),"matched actual native unshielded control");
            h.assertTrue(f.hurt(f.p.damageSources().mobAttack(source),20),"matched actual native defended hit");
            h.assertTrue(nativeOnly.posts==1&&shielded.posts==1,"one native pipeline per target");
            h.assertTrue(nativeOnly.armor>0&&nativeOnly.effects>0&&nativeOnly.enchantments>0,"actual armor toughness Resistance and Protection reductions ran");
            h.assertTrue(Float.compare(nativeOnly.damage,shielded.damage)==0,"identical native postabsorption remainder before152");
            h.assertTrue(Float.compare(control.p.getHealth(),nativeHp-nativeOnly.damage)==0,"controlHP follows native float subtraction exactly rather than a decimal tolerance");
            eq(h,f.shield(),shield.subtract(new BigDecimal(Float.toString(nativeOnly.damage))),"152 debits only the observed native postmitigation postabsorption remainder once");
            eq(h,f.hp(),hp,"defended exactHP is preserved");h.assertTrue(f.p.getAbsorptionAmount()==0&&control.p.getAbsorptionAmount()==0,"native absorption consumed once per target");
            h.assertTrue(f.p.getItemBySlot(EquipmentSlot.FEET).getDamageValue()>0&&control.p.getItemBySlot(EquipmentSlot.FEET).getDamageValue()>0,"native armor durability not bypassed");h.succeed();
        });
    }


    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_incoming_cancellation_and_snapshot_abort_cannot_spend_either_tranche(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(f.toggle(1),"armed");BigDecimal hp=f.hp(),base=f.shield(),reserve=f.reserve();
            Gate gate=new Gate(f.p);f.hook(gate);gate.cancel=true;h.assertFalse(f.hit("5"),"native incoming cancellation final");
            eq(h,f.shield(),base,"canceled damage spends no base");eq(h,f.reserve(),reserve,"canceled damage spends no reserve");
            gate.cancel=false;gate.abort=true;f.p.invulnerableTime=0;h.assertTrue(f.hit("5"),"accepted damage after incoming observer abort");
            eq(h,f.shield(),base,"captured activation invalidated before commit");eq(h,f.reserve(),reserve,"stale capture cannot spend reserve");eq(h,f.hp(),hp.subtract(n("5")),"normal HP behavior after context loss");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_clone_copies_reserve_without_refill_and_reset_refuses_lost_capacity(GameTestHelper h){
        Fixture f=new Fixture(h),replacement=new Fixture(h);f.ready(()->{
            BigDecimal reserve=f.reserve();h.assertTrue(reserve.signum()>0,"earned reserve fixture");
            h.assertTrue(HighPowerCombat.spendShield(f.p,n("7")),"existing base injury");f.hit("9");BigDecimal base=f.shield(),hp=f.hp();
            HighPowerCombat.clonePlayer(new PlayerEvent.Clone(replacement.p,f.p,false));
            eq(h,replacement.reserve(),reserve,"nondeath clone retains reserve");eq(h,replacement.shield(),base,"nondeath clone retains base");eq(h,replacement.hp(),hp,"nondeath clone retains injury");
            HighPowerCombat.clonePlayer(new PlayerEvent.Clone(replacement.p,f.p,true));
            eq(h,replacement.reserve(),reserve,"death clone adds no reserve");eq(h,replacement.shield(),n(HighPowerCombat.maxShieldText(replacement.p)),"existing base death policy preserved");
            AbilityStorage.get(f.p).level=1;h.assertFalse(HighPowerCombat.resetGrowthContract(f.p),"reset cannot destroy above-cap balances");AbilityStorage.get(f.p).level=5;
            eq(h,f.reserve(),reserve,"refused reset preserves reserve");h.succeed();
        });
    }

    public static final class Gate {
        final ServerPlayer target;boolean cancel,abort;Gate(ServerPlayer target){this.target=target;}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent event){if(event.getEntity()==target){if(cancel)event.setCanceled(true);if(abort)MagneticDomain.abort(target);}}
    }
    public static final class RenewalRecoveryWitness {
        final ServerPlayer target;final int startedServerTick;int boundaries;boolean noCredit=true,exactTiming=true,expiredBenefitsAbsent=true;
        RenewalRecoveryWitness(ServerPlayer target){this.target=target;startedServerTick=target.server.getTickCount();}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void pre(ServerTickEvent.Pre e){observe(e.getServer());}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(ServerTickEvent.Post e){observe(e.getServer());}
        private void observe(net.minecraft.server.MinecraftServer server){
            if(server!=target.server)return;long now=target.level().getGameTime();
            if(!MagneticDomain.renewalPending(target)||now-target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getLong("lastShieldSpend")<60)return;
            int elapsed=server.getTickCount()-startedServerTick;exactTiming &= elapsed>=60&&elapsed%20==0;
            expiredBenefitsAbsent &= !MagneticDomain.active(target);
            BigDecimal base=HighPowerCombat.shield(target),reserve=MagneticDomainReserve.reserve(target);
            HighPowerCombat.tickShield(target);boundaries++;
            noCredit &= base.compareTo(HighPowerCombat.shield(target))==0&&reserve.compareTo(MagneticDomainReserve.reserve(target))==0;
        }
    }
    public static final class MitigationObserve {
        final LivingEntity target;float armor,effects,enchantments,damage;int posts;MitigationObserve(LivingEntity target){this.target=target;}
        @SubscribeEvent(priority=EventPriority.NORMAL)public void pre(LivingDamageEvent.Pre e){if(e.getEntity()==target){armor=e.getContainer().getReduction(DamageContainer.Reduction.ARMOR);effects=e.getContainer().getReduction(DamageContainer.Reduction.MOB_EFFECTS);enchantments=e.getContainer().getReduction(DamageContainer.Reduction.ENCHANTMENTS);}}
        @SubscribeEvent(priority=EventPriority.HIGHEST)public void post(LivingDamageEvent.Post e){if(e.getEntity()==target){posts++;damage=e.getNewDamage();}}
    }
    public static final class PostAction {
        final LivingEntity target;final Runnable action;boolean called;PostAction(LivingEntity target,Runnable action){this.target=target;this.action=action;}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(LivingDamageEvent.Post event){if(event.getEntity()==target&&!called){called=true;action.run();}}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p,attacker;final EmbeddedChannel channel;final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final List<net.minecraft.world.scores.PlayerTeam> teams=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){
            this.h=h;for(int x=1;x<=13;x++)for(int z=1;z<=15;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPB"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;place();h.assertTrue(HighPowerCombat.enroll(p,5),"approved connected LV5 owner");
            var state=AbilityStorage.get(p);state.selectCategory("electromaster");state.setLevel(5);for(String id:List.of("forked_arc","magnetic_bracer",GroundingReturn.ID,PotentialBuffer.ID,MagneticDomain.ID))state.learn(id);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,MagneticDomain.ID,id->id.equals(MagneticDomain.ID)),"genuine learned152slot");ElectromasterPrototype.initializeBudget(p);epoch();
            attacker=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[HP-Buffer]"));attacker.setPos(p.position().add(0,0,3));attacker.setNoGravity(true);attacker.getAbilities().instabuild=false;
            var s=AbilityStorage.get(attacker);s.changeCategoryClassic("electromaster");s.setLevel(5);h.assertTrue(HighPowerCombat.enroll(attacker,5),"approved exact electrical source actor");
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        void ready(Runnable action){h.runAfterDelay(61,()->{p.doTick();place();attacker.setPos(p.position().add(0,0,3));HighPowerCombat.tickShield(p);action.run();});}
        void next(Runnable action){h.runAfterDelay(1,()->{p.doTick();place();attacker.setPos(p.position().add(0,0,3));action.run();});}
        void place(){Vec3 at=h.absoluteVec(new Vec3(6.5,2,6.5));p.moveTo(at.x,at.y,at.z,0,0);p.setYHeadRot(0);p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();}
        Zombie mob(double x,double z){Zombie e=EntityType.HUSK.create(h.getLevel());e.setNoAi(true);e.setNoGravity(true);e.setSilent(true);e.setOnGround(false);e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);e.setPos(p.position().add(x,0,z));h.getLevel().addFreshEntity(e);entities.add(e);return e;}
        LivingEntity passive(double x,double z){var e=EntityType.COW.create(h.getLevel());e.setNoAi(true);e.setNoGravity(true);e.setPos(p.position().add(x,0,z));h.getLevel().addFreshEntity(e);entities.add(e);return e;}
        Arrow arrow(LivingEntity shooter,ItemStack stack){Arrow arrow=new Arrow(h.getLevel(),shooter,stack,null);arrow.setPos(p.getEyePosition().add(0,0,3));h.getLevel().addFreshEntity(arrow);entities.add(arrow);return arrow;}
        void ally(Entity entity){var board=h.getLevel().getScoreboard();var team=board.addPlayerTeam("pb"+UUID.randomUUID().toString().substring(0,10));team.setAllowFriendlyFire(false);board.addPlayerToTeam(p.getScoreboardName(),team);board.addPlayerToTeam(entity.getScoreboardName(),team);teams.add(team);}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        boolean toggle(long input){return MagneticDomain.toggle(p,wire(input));}
        boolean withPvp(java.util.function.BooleanSupplier action){boolean previous=p.server.isPvpAllowed();p.server.setPvpAllowed(true);try{return action.getAsBoolean();}finally{p.server.setPvpAllowed(previous);}}
        boolean hit(String amount){return withPvp(()->HighPowerCombat.attack(attacker,ELECTRIC,p,n(amount)));}
        boolean hurt(DamageSource source,float amount){return withPvp(()->p.hurt(source,amount));}
        BigDecimal hp(){return n(HighPowerCombat.healthText(p));}BigDecimal shield(){return HighPowerCombat.shield(p);}double cp(){return ElectromasterPrototype.prototypeCp(p);}double xp(){return AbilityStorage.get(p).exp(MagneticDomain.ID);}BigDecimal reserve(){return MagneticDomainReserve.reserve(p);}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);MagneticDomain.remove(p);PotentialBuffer.remove(p);GroundingReturn.remove(p);AbilityStorage.remove(p);AbilityStorage.remove(attacker);attacker.discard();h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}
