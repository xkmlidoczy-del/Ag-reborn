package cn.academy.port.gametest;

import cn.academy.port.highpower.core.PotentialBufferRules;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW142 real native damage/knockback and connected authenticated owner acceptance. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerPotentialBufferRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_potential_buffer", ELECTRIC = "electromaster.forked_arc";
    private HighPowerPotentialBufferRuntimeTests() {}
    private static BigDecimal n(String text) { return new BigDecimal(text); }
    private static void eq(GameTestHelper h, BigDecimal a, BigDecimal b, String why) { h.assertTrue(a.compareTo(b) == 0, why + ": " + a + " != " + b); }
    private static void near(GameTestHelper h, double a, double b, String why) { h.assertTrue(Math.abs(a-b) < 1e-6, why + ": " + a + " != " + b); }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=145)
    public static void potential_authenticated_toggle_pending_cancel_replay_and_idle_have_no_resource_reward(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            h.assertFalse(PotentialBuffer.abort(f.p, f.wire(1)), "pending cancel fences delayed start");
            h.assertFalse(f.toggle(1), "canceled input cannot arm"); h.assertTrue(f.toggle(2), "new input arms");
            h.assertFalse(f.toggle(2), "replayed tap cannot turn off"); h.assertTrue(PotentialBuffer.active(f.p), "replay preserves live controller");
            h.assertFalse(PotentialBuffer.abort(f.p, f.wire(1)), "old cancel cannot end newer activation");
            h.assertFalse(PotentialBuffer.toggle(f.p, new HighPowerInput.Input(0,3,f.p.getId()+1,f.epoch()).wire()), "forged actor rejected");
            h.assertTrue(f.toggle(3), "fresh tap turns off"); h.assertFalse(PotentialBuffer.active(f.p), "toggle off acknowledged");
            h.assertTrue(HighPowerCombat.spendShield(f.p,n("10")),"real sharedshield cost leaves recoverable gap");
            BigDecimal shield=f.shield(),hp=f.hp(); double cp=f.cp(),xp=f.xp();
            h.assertFalse(f.toggle(4), "second activation in the same server tick is bounded");
            f.next(() -> {h.assertTrue(f.toggle(5), "next genuine tick permits free activation");
            h.runAfterDelay(61, () -> {
                HighPowerCombat.tickShield(f.p);
                h.assertTrue(PotentialBuffer.active(f.p), "mode has no synthetic hold expiry");
                near(h,f.cp(),cp,"no idle CP");near(h,f.xp(),xp,"no idle XP");eq(h,f.shield(),shield,"no toggle refill");eq(h,f.hp(),hp,"no HP refill");
                h.assertTrue(HighPowerCombat.shieldActive(f.p),"composite recovery/HUD gate includes active142");
                h.assertFalse(f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getBoolean("bracerActive"),"toggle does not activate projectile bracer");
                h.assertTrue(f.toggle(6),"turn off spent defense");h.runAfterDelay(1,()->{HighPowerCombat.tickShield(f.p);h.assertTrue(f.shield().compareTo(shield)>0,"existing recovery resumes only after defenseoff");h.succeed();});
            });});
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_front_native_melee_electric_and_ordinary_arrow_share_one_pool(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            Zombie melee=f.mob(0,3);h.assertTrue(f.toggle(1),"armed");BigDecimal shield=f.shield(),hp=f.hp();
            h.assertTrue(f.hurt(f.p.damageSources().mobAttack(melee),5),"native reviewed mob melee accepted");eq(h,f.shield(),shield.subtract(n("5")),"melee spends once");eq(h,f.hp(),hp,"melee HP protected");
            f.place();f.p.invulnerableTime=0;h.assertTrue(f.hit("7"),"explicit ACA electricity accepted");eq(h,f.shield(),shield.subtract(n("12")),"electricity shares same pool");
            Arrow arrow=f.arrow(melee,new ItemStack(Items.ARROW));f.p.invulnerableTime=0;
            h.assertTrue(f.hurt(f.p.damageSources().arrow(arrow,melee),3),"reviewed ordinary arrow uses actual native source");
            eq(h,f.shield(),shield.subtract(n("15")),"arrow spends no independent defense pool");eq(h,f.hp(),hp,"three actual native hit types absorbed");
            h.assertFalse(arrow.isRemoved(),"142 does not deflect/delete arrows");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_cone_rejects_rear_side_and_originless_and_snapshots_facing_before_callbacks(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(f.toggle(1),"armed");BigDecimal shield=f.shield(),hp=f.hp();
            for(Vec3 offset:List.of(new Vec3(0,0,-3),new Vec3(3,0,0))) {
                f.attacker.setPos(f.p.position().add(offset));f.p.invulnerableTime=0;h.assertTrue(f.hit("3"),"out of cone remains ordinary accepted damage");hp=hp.subtract(n("3"));
                eq(h,f.hp(),hp,"out-of-cone damages HP");eq(h,f.shield(),shield,"no out-of-cone shield debit");
            }
            f.p.invulnerableTime=0;h.assertTrue(f.hurt(f.p.damageSources().lightningBolt(),3),"originless native lightning still accepted");hp=hp.subtract(n("3"));eq(h,f.hp(),hp,"142 cannot invent environmental origin");eq(h,f.shield(),shield,"originless shield unchanged");
            f.attacker.setPos(f.p.position().add(0,0,3));IncomingAction turn=new IncomingAction(f.p,()->f.p.setYRot(180));f.hook(turn);f.p.invulnerableTime=0;
            h.assertTrue(f.hit("4"),"front hit remains accepted after observer turn");eq(h,f.shield(),shield.subtract(n("4")),"Incoming facing snapshot rather than mutable Post look");eq(h,f.hp(),hp,"turned callback does not erase genuine front capture");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_source_whitelist_refuses_railgun_fire_generic_and_modified_arrows(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(f.toggle(1),"armed");BigDecimal shield=f.shield();
            for(DamageSource source:List.of(f.p.damageSources().generic(),f.p.damageSources().onFire(),f.p.damageSources().fall(),
                    new AbilityDamage.SkillDamageSource(f.attacker,"electromaster.railgun",false),new AbilityDamage.SkillDamageSource(f.attacker,"electromaster.arc_gen_suffix",false))) {
                f.p.invulnerableTime=0;f.hurt(source,3);eq(h,f.shield(),shield,"unreviewed source never spends142shield");
            }
            Zombie shooter=f.mob(0,8);DamageSource spoofed=new DamageSource(f.p.damageSources().mobAttack(shooter).typeHolder(),shooter,shooter,f.p.position().add(0,0,3));
            f.p.invulnerableTime=0;f.hurt(spoofed,3);eq(h,f.shield(),shield,"ordinarysource cannot spoof explicitorigin awayfrom actualdirectentity");
            Arrow critical=f.arrow(shooter,new ItemStack(Items.ARROW));critical.setCritArrow(true);
            Arrow piercing=f.arrow(shooter,new ItemStack(Items.ARROW));CompoundTag piercingData=new CompoundTag();piercing.saveWithoutId(piercingData);piercingData.putByte("PierceLevel",(byte)1);piercing.load(piercingData);
            Arrow burning=f.arrow(shooter,new ItemStack(Items.ARROW));burning.igniteForSeconds(10);
            for(Arrow arrow:List.of(critical,piercing,burning)) {f.p.invulnerableTime=0;f.hurt(f.p.damageSources().arrow(arrow,shooter),3);eq(h,f.shield(),shield,"modified arrows remain native and never142");}
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_partial_exhausted_and_large_native_zero_keep_exact_hp_and_never_refill(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            BigDecimal shield=f.shield(),hp=f.hp();h.assertTrue(f.toggle(1),"armed");ObserveZero observe=new ObserveZero(f.p);f.hook(observe);
            h.assertTrue(f.hit(shield.divide(BigDecimal.TWO).toPlainString()),"large exact hit accepted");h.assertTrue(observe.seen,"real bounded native witness transiently reaches zero HP");
            eq(h,f.hp(),hp,"exact shield preserves living HP after native zero");eq(h,f.shield(),shield.divide(BigDecimal.TWO),"large exact pool debit not float proxy");
            h.assertTrue(HighPowerCombat.spendShield(f.p,f.shield().subtract(n("40"))),"explicit fixture exhausts pool to40");f.p.invulnerableTime=0;
            h.assertTrue(f.hit("55"),"partial hit accepted");eq(h,f.shield(),BigDecimal.ZERO,"pool exactly exhausted");eq(h,f.hp(),hp.subtract(n("15")),"only remainder affects HP");
            f.p.invulnerableTime=0;h.assertTrue(f.hit("5"),"empty pool hit accepted");eq(h,f.hp(),hp.subtract(n("20")),"empty pool cannot protect HP");
            double xp=f.xp();h.assertTrue(f.toggle(2),"empty mode turns off immediately");f.next(()->{h.assertTrue(f.toggle(3),"next native tick arms at zero pool for free");eq(h,f.shield(),BigDecimal.ZERO,"no exhausted recast refill");near(h,f.xp(),xp,"no recast XP");h.succeed();});
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_native_absorption_zero_veto_and_cancel_never_spend_or_pulse(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            Zombie target=f.mob(0,3);h.assertTrue(f.toggle(1),"armed");BigDecimal shield=f.shield(),hp=f.hp();double cp=f.cp(),xp=f.xp();
            f.p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20);f.p.setAbsorptionAmount(10);
            h.assertTrue(f.hit("5"),"native absorption accepts hit");near(h,f.p.getAbsorptionAmount(),5,"native absorption consumed exactly once");
            Gate gate=new Gate(f.p);f.hook(gate);f.p.invulnerableTime=0;gate.zero=true;h.assertTrue(f.hit("20"),"zero pre veto keeps native acceptance semantics");
            f.p.invulnerableTime=0;gate.zero=false;gate.cancel=true;h.assertFalse(f.hit("20"),"incoming cancel authoritative");
            eq(h,f.shield(),shield,"zero/absorption/cancel cannot spend sharedpool");eq(h,f.hp(),hp,"HP conserved");near(h,f.cp(),cp,"no pulse CP");near(h,f.xp(),xp,"no successful XP");
            h.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO)&&PotentialBuffer.pulseCount(f.p)==0,"no ghost pulse");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_paired_native_armor_toughness_resistance_protection_absorption_precede_shared_shield(GameTestHelper h) {
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
            h.assertTrue(f.toggle(1),"only exact owner arms142");h.assertFalse(PotentialBuffer.active(control.p)||GroundingReturn.active(control.p),"native control has all layers off");
            BigDecimal hp=f.hp(),shield=f.shield();float nativeHp=control.p.getHealth();
            h.assertTrue(control.hurt(control.p.damageSources().mobAttack(source),20),"matched actual native unshielded control");
            h.assertTrue(f.hurt(f.p.damageSources().mobAttack(source),20),"matched actual native defended hit");
            h.assertTrue(nativeOnly.posts==1&&shielded.posts==1,"one native pipeline per target");
            h.assertTrue(nativeOnly.armor>0&&nativeOnly.effects>0&&nativeOnly.enchantments>0,"actual armor toughness Resistance and Protection reductions ran");
            h.assertTrue(Float.compare(nativeOnly.damage,shielded.damage)==0,"identical native postabsorption remainder before142");
            h.assertTrue(Float.compare(control.p.getHealth(),nativeHp-nativeOnly.damage)==0,"controlHP follows native float subtraction exactly rather than a decimal tolerance");
            eq(h,f.shield(),shield.subtract(new BigDecimal(Float.toString(nativeOnly.damage))),"142 debits only the observed native postmitigation postabsorption remainder once");
            eq(h,f.hp(),hp,"defended exactHP is preserved");near(h,f.p.getAbsorptionAmount(),0,"shielded native absorption consumed once");near(h,control.p.getAbsorptionAmount(),0,"matched native control absorption consumed once");
            h.assertTrue(f.p.getItemBySlot(EquipmentSlot.FEET).getDamageValue()>0&&control.p.getItemBySlot(EquipmentSlot.FEET).getDamageValue()>0,"native armor durability not bypassed");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_pulse_six_target_cap_one_cp_debit_and_no_reflected_damage(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            List<Zombie> targets=new ArrayList<>();for(int i=0;i<8;i++)targets.add(f.mob((i%4-1.5)*.8,2+i/4));
            h.assertTrue(f.toggle(1),"armed");double cp=f.cp();h.assertTrue(f.hit("5"),"positive absorption");
            long moved=targets.stream().filter(t->t.getDeltaMovement().horizontalDistance()>0).count();h.assertTrue(moved==6,"exactly six targets attempted");
            near(h,f.cp(),cp-3,"one LV4 pulse debit for entire batch");h.assertTrue(PotentialBuffer.pulseCount(f.p)==1,"one owner pulse");
            for(Zombie target:targets){near(h,target.getHealth(),target.getMaxHealth(),"pulse never damages native HP");if(target.getDeltaMovement().horizontalDistance()>0)near(h,target.getDeltaMovement().horizontalDistance(),.35,"native impulse provisional .35");}
            double paid=f.cp();f.p.invulnerableTime=0;h.assertTrue(f.hit("5"),"second shield hit same tick");near(h,f.cp(),paid,"owner20tick fence prevents second cost");h.assertTrue(PotentialBuffer.pulseCount(f.p)==1,"second hit cannot reenter pulse");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_zero_target_and_unrelated_shield_spend_are_free_and_cannot_mint_pulses(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(f.toggle(1),"armed");double cp=f.cp(),xp=f.xp();h.assertTrue(HighPowerCombat.spendShield(f.p,n("5")),"unrelated cost uses sharedpool");
            near(h,f.cp(),cp,"ordinary shield spend cannot charge CP");near(h,f.xp(),xp,"ordinary shield spend is not142absorption");h.assertTrue(PotentialBuffer.pulseCount(f.p)==0,"ordinary shield spend emits nothing");
            h.assertTrue(f.hit("5"),"positive absorption with only player source");near(h,f.cp(),cp,"zero eligible targets are free");h.assertTrue(PotentialBuffer.pulseCount(f.p)==0,"zero target cannot fake pulse");
            h.assertTrue(f.xp()>xp,"real shield absorption can earn bounded mastery without a target");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_allies_players_passives_walls_range_and_zero_offset_are_excluded_before_payment(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            Zombie ally=f.mob(-2,2),behind=f.mob(0,4),outside=f.mob(0,PotentialBufferRules.RANGE+1),zero=f.mob(0,0);f.ally(ally);
            LivingEntity passive=f.passive(2,2);for(int x=-1;x<=1;x++)for(int y=0;y<=3;y++)h.getLevel().setBlockAndUpdate(BlockPos.containing(f.p.position().add(x,y,2)),Blocks.STONE.defaultBlockState());
            h.assertTrue(f.toggle(1),"armed");double cp=f.cp();h.assertTrue(f.hit("5"),"player source accepted for shield but not pulse");
            near(h,f.cp(),cp,"all filtered-out targets means no charge");for(LivingEntity entity:List.of(ally,behind,outside,zero,passive))h.assertTrue(entity.getDeltaMovement().equals(Vec3.ZERO),"excluded entity receives no impulse");
            h.assertTrue(f.attacker.getDeltaMovement().equals(Vec3.ZERO),"players excluded from pulse");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_native_resistance_and_cancellation_keep_paid_attempt_and_fall_state(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            Zombie resistant=f.mob(-1,3),canceled=f.mob(1,3);resistant.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);resistant.fallDistance=7;canceled.fallDistance=9;
            KnockbackAction hook=new KnockbackAction(canceled,()->{},true);f.hook(hook);h.assertTrue(f.toggle(1),"armed");double cp=f.cp();h.assertTrue(f.hit("5"),"positive shielding creates paid attempt");
            near(h,f.cp(),cp-3,"native canceled/resisted attempt is not rolled back");h.assertTrue(hook.called,"native cancellation event genuinely reached");
            h.assertTrue(resistant.getDeltaMovement().equals(Vec3.ZERO)&&canceled.getDeltaMovement().equals(Vec3.ZERO),"native resistance and cancellation authoritative");
            near(h,resistant.fallDistance,7,"no fallreset on resistance");near(h,canceled.fallDistance,9,"no fallreset on cancellation");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_callback_abort_cancels_current_native_impulse_and_remaining_batch_without_rollback(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            Zombie first=f.mob(0,2),later=f.mob(0,4);KnockbackAction hook=new KnockbackAction(first,()->PotentialBuffer.abort(f.p),false);f.hook(hook);
            h.assertTrue(f.toggle(1),"armed");BigDecimal shield=f.shield();double cp=f.cp();h.assertTrue(f.hit("5"),"shield already committed before callback");
            h.assertTrue(hook.called&&!PotentialBuffer.active(f.p),"native observer aborts owned controller");
            h.assertTrue(first.getDeltaMovement().equals(Vec3.ZERO)&&later.getDeltaMovement().equals(Vec3.ZERO),"postcallback guard rejects current stale impulse and rest of batch");
            eq(h,f.shield(),shield.subtract(n("5")),"absorption commit retained");near(h,f.cp(),cp-3,"paid canceled batch never unsafe-refunds");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_incoming_callback_replaces_context_without_stealing_old_capture(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(f.toggle(1),"armed");f.next(()->{BigDecimal shield=f.shield(),hp=f.hp();IncomingAction replace=new IncomingAction(f.p,()->{PotentialBuffer.abort(f.p);h.assertTrue(f.toggle(2),"callback creates distinct new activation");});f.hook(replace);
            h.assertTrue(f.hit("5"),"native hit completes");eq(h,f.shield(),shield,"new controller cannot steal oldIncomingcapture");eq(h,f.hp(),hp.subtract(n("5")),"canceled defense does not erase ordinary HP damage");
            h.assertTrue(PotentialBuffer.active(f.p),"replacement stays active rather than old cleanup clearing newowner");h.succeed();});
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void potential_owner_and_target_twenty_tick_fences_survive_retoggle_and_native_save_reload(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            Zombie target=f.mob(0,3);h.assertTrue(f.toggle(1),"armed");h.assertTrue(f.hit("5"),"first pulse");double xp=f.xp();
            long firstPulse=f.p.server.overworld().getGameTime();h.assertTrue(f.toggle(2),"off remains immediate during one-tick native pulse cooldown");
            h.assertFalse(f.toggle(3),"native one-tick cooldown and activation fence cannot be cleared by fresh input");
            f.next(()->{
                h.assertTrue(f.toggle(4),"rearm after actual player tick");double cp=f.cp();f.p.invulnerableTime=0;h.assertTrue(f.hit("5"),"retoggled positive absorption");near(h,f.cp(),cp,"retoggle cannot resetownerquota");near(h,f.xp(),xp,"retoggle cannot farm mastery");
                PotentialBuffer.remove(f.p);AbilityStorage.save(f.p);CompoundTag saved=new CompoundTag();f.p.saveWithoutId(saved);AbilityStorage.remove(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);f.p.load(saved);f.place();f.epoch();
                h.assertFalse(f.toggle(1),"serialized activation fence survives new epoch in same tick");
                f.next(()->{
                    h.assertTrue(f.toggle(2),"new owner epoch after native NBT roundtrip and real tick can arm");double afterReload=f.cp();f.p.invulnerableTime=0;f.hit("5");near(h,f.cp(),afterReload,"serialized owner fence survives transient controller loss");near(h,f.xp(),xp,"serialized mastery fence survives reload");
                    long remaining=firstPulse+20-f.p.server.overworld().getGameTime();h.assertTrue(remaining>0,"reload test remains inside first owner fence");
                    h.runAfterDelay(remaining,()->{
                        f.p.doTick();f.place();target.setPos(f.p.position().add(0,0,3));target.setDeltaMovement(Vec3.ZERO);f.p.invulnerableTime=0;double before=f.cp(),cost=PotentialBufferRules.pulseCost(HighPowerGrowth.values(f.p).position());
                        h.assertTrue(f.hit("5"),"hit at exact20tick boundary");near(h,f.cp(),before-cost,"next earned-progress cost payable at fence boundary");h.assertTrue(target.getDeltaMovement().horizontalDistance()>0,"target global fence expires at20ticks");h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_two_owners_share_target_rate_fence_and_second_empty_batch_is_free(GameTestHelper h) {
        Fixture f=new Fixture(h),other=new Fixture(h);f.ready(()->{
            other.p.doTick();other.p.setPos(f.p.position().add(.5,0,0));other.p.connection.resetPosition();other.attacker.setPos(other.p.position().add(0,0,3));Zombie target=f.mob(0,3);
            h.assertTrue(f.toggle(1)&&other.toggle(1),"independent owners arm");h.assertTrue(f.hit("5"),"first owner pulse");Vec3 velocity=target.getDeltaMovement();double cp=other.cp();
            h.assertTrue(other.hit("5"),"second owner still absorbs");h.assertTrue(target.getDeltaMovement().equals(velocity),"same native target cannot receive secondowner pulse inside20ticks");near(h,other.cp(),cp,"targetfence leaves zero eligible targets and no second CP cost");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_no_cp_still_absorbs_and_does_not_rollback_or_pulse(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            Zombie target=f.mob(0,3);f.p.getPersistentData().getCompound("academy:electromaster_prototype").putDouble("cp",0);
            h.assertTrue(f.toggle(1),"toggle needs no CP");BigDecimal shield=f.shield(),hp=f.hp();h.assertTrue(f.hit("5"),"shield absorption independent from optional pulse payment");
            eq(h,f.shield(),shield.subtract(n("5")),"committed shield not refunded on CPfailure");eq(h,f.hp(),hp,"HP protected");near(h,f.cp(),0,"no negative or phantom CP");
            h.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO)&&PotentialBuffer.pulseCount(f.p)==0,"unpaid pulse has no native effect");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_grounding_precedence_uses_one_pool_and_does_not_create142pulse(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            f.mob(0,3);var state=AbilityStorage.get(f.p);h.assertTrue(state.presets.edit(0,1,GroundingReturn.ID,id->true),"bind independent grounding slot");f.epoch();f.p.setShiftKeyDown(true);f.p.setOnGround(true);
            h.assertTrue(f.toggle(1),"142arm");h.assertTrue(GroundingReturn.start(f.p,new HighPowerInput.Input(1,2,f.p.getId(),f.epoch()).wire()),"stationary native grounding arm");
            BigDecimal shield=f.shield(),hp=f.hp();double cp=f.cp(),xp=f.xp();h.assertTrue(f.hit("5"),"electric hit accepted");
            eq(h,f.shield(),shield,"grounding precise spend+refund is sole transaction");eq(h,f.hp(),hp,"HP protected");near(h,f.cp(),cp,"no142pulse CP from122absorption");near(h,f.xp(),xp,"no142mastery from122absorption");
            h.assertTrue(PotentialBuffer.active(f.p)&&PotentialBuffer.pulseCount(f.p)==0,"142remains idle concurrently withoutsecondabsorb");GroundingReturn.abort(f.p);h.assertTrue(PotentialBuffer.active(f.p),"122cleanup cannot clear142");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_lethal_remainder_keeps_native_totem_and_consumed_shield(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(f.withPvp(()->HighPowerCombat.attack(f.attacker,"electromaster.railgun",f.p,f.hp().subtract(n("100")))),"real unshielded damage lowers HPto100");
            h.assertTrue(HighPowerCombat.spendShield(f.p,f.shield().subtract(n("40"))),"real cost lowers shieldto40");f.p.invulnerableTime=0;f.place();h.assertTrue(f.toggle(1),"lowresource defense");
            f.p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.TOTEM_OF_UNDYING));h.assertTrue(f.hit("200"),"real partialshield lethal remainder");
            h.assertTrue(f.p.isAlive()&&f.p.getOffhandItem().isEmpty(),"native totem consumed exactly once");eq(h,f.shield(),BigDecimal.ZERO,"totem cannot refill shield");h.assertTrue(f.hp().signum()>0,"exact postnative rescue observed");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_xp_callback_nested_damage_cannot_restore_outer_ledger_or_reenter_pulse(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            f.mob(0,3);h.assertTrue(f.toggle(1),"armed");BigDecimal hp=f.hp(),shield=f.shield();ExperienceAction hook=new ExperienceAction(f.p,()->{f.p.invulnerableTime=0;f.p.hurt(f.p.damageSources().generic(),3);});f.hook(hook);
            f.hit("5");h.assertTrue(hook.called,"actual committed absorption XP observer executed");
            eq(h,f.hp(),hp.subtract(n("3")),"outercompletion never overwrites nested ordinaryHPdamage");eq(h,f.shield(),shield.subtract(n("5")),"outershield commit remains exact");
            h.assertTrue(PotentialBuffer.pulseCount(f.p)==1,"reentrant observer cannot spendanotherpulse");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void potential_cleanup_on_level_preset_config_epoch_dimension_logout_and_category_is_owned(GameTestHelper h) {
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(f.toggle(1),"arm");AbilityStorage.get(f.p).level=3;PotentialBuffer.tick(f.p);h.assertFalse(PotentialBuffer.active(f.p),"level loss ends controller");AbilityStorage.get(f.p).level=4;
            f.next(()->{
                h.assertTrue(f.toggle(2),"rearm");var snapshot=SkillCatalog.configurationSnapshot();try{var changed=snapshot.copy();changed.getCompound("electromaster."+PotentialBuffer.ID).putBoolean("enabled",false);SkillCatalog.applyConfigurationSnapshot(changed);PotentialBuffer.tick(f.p);h.assertFalse(PotentialBuffer.active(f.p),"featuredisable endscontroller");}finally{SkillCatalog.applyConfigurationSnapshot(snapshot);}
                f.next(()->{
                    h.assertTrue(f.toggle(3),"rearm");LegacySingleKeyOwnerEpoch.forget(f.p);PotentialBuffer.tick(f.p);h.assertFalse(PotentialBuffer.active(f.p),"owner epoch loss endscontroller");f.epoch();
                    f.next(()->{
                        h.assertTrue(f.toggle(1),"new epoch rearm");AbilityStorage.get(f.p).presets.switchTo(1);PotentialBuffer.tick(f.p);h.assertFalse(PotentialBuffer.active(f.p),"preset change cleanup");AbilityStorage.get(f.p).presets.switchTo(0);f.epoch();
                        f.next(()->{
                            h.assertTrue(f.toggle(2),"preset rebound");PotentialBuffer.dimension(new PlayerEvent.PlayerChangedDimensionEvent(f.p,f.p.level().dimension(),net.minecraft.world.level.Level.NETHER));h.assertFalse(PotentialBuffer.active(f.p),"dimension cleanup");
                            f.next(()->{
                                h.assertTrue(f.toggle(3),"rearm");PotentialBuffer.logout(new PlayerEvent.PlayerLoggedOutEvent(f.p));h.assertFalse(PotentialBuffer.active(f.p),"logoutcleanup");
                                f.next(()->{h.assertTrue(f.toggle(4),"rearm");AbilityStorage.get(f.p).selectCategory("meltdowner");PotentialBuffer.tick(f.p);h.assertFalse(PotentialBuffer.active(f.p),"category loss cleanup");h.succeed();});
                            });
                        });
                    });
                });
            });
        });
    }

    public static final class Gate {
        final LivingEntity target;boolean cancel,zero;Gate(LivingEntity target){this.target=target;}
        @SubscribeEvent(priority=EventPriority.HIGHEST)public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target&&cancel)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void pre(LivingDamageEvent.Pre e){if(e.getEntity()==target&&zero)e.setNewDamage(0);}
    }
    public static final class MitigationObserve {
        final LivingEntity target;float armor,effects,enchantments,damage;int posts;MitigationObserve(LivingEntity target){this.target=target;}
        @SubscribeEvent(priority=EventPriority.NORMAL)public void pre(LivingDamageEvent.Pre e){if(e.getEntity()==target){armor=e.getContainer().getReduction(DamageContainer.Reduction.ARMOR);effects=e.getContainer().getReduction(DamageContainer.Reduction.MOB_EFFECTS);enchantments=e.getContainer().getReduction(DamageContainer.Reduction.ENCHANTMENTS);}}
        @SubscribeEvent(priority=EventPriority.HIGHEST)public void post(LivingDamageEvent.Post e){if(e.getEntity()==target){posts++;damage=e.getNewDamage();}}
    }
    public static final class ObserveZero {
        final LivingEntity target;boolean seen;ObserveZero(LivingEntity target){this.target=target;}
        @SubscribeEvent(priority=EventPriority.HIGHEST)public void post(LivingDamageEvent.Post e){if(e.getEntity()==target&&target.getHealth()==0)seen=true;}
    }
    public static final class IncomingAction {
        final LivingEntity target;final Runnable action;boolean called;IncomingAction(LivingEntity target,Runnable action){this.target=target;this.action=action;}
        @SubscribeEvent(priority=EventPriority.NORMAL)public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target&&!called){called=true;action.run();}}
    }
    public static final class KnockbackAction {
        final LivingEntity target;final Runnable action;final boolean cancel;boolean called;KnockbackAction(LivingEntity target,Runnable action,boolean cancel){this.target=target;this.action=action;this.cancel=cancel;}
        @SubscribeEvent(priority=EventPriority.NORMAL)public void knockback(LivingKnockBackEvent e){if(e.getEntity()==target&&!called){called=true;action.run();if(cancel)e.setCanceled(true);}}
    }
    public static final class ExperienceAction {
        final ServerPlayer target;final Runnable action;boolean called;ExperienceAction(ServerPlayer target,Runnable action){this.target=target;this.action=action;}
        @SubscribeEvent public void experience(SkillExpAddedEvent e){if(e.getEntity()==target&&e.skill.equals(PotentialBuffer.ID)&&!called){called=true;action.run();}}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p,attacker;final EmbeddedChannel channel;final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final List<net.minecraft.world.scores.PlayerTeam> teams=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){
            this.h=h;for(int x=1;x<=13;x++)for(int z=1;z<=15;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPB"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;place();h.assertTrue(HighPowerCombat.enroll(p,4),"approved connected LV4 owner");
            var state=AbilityStorage.get(p);state.selectCategory("electromaster");state.setLevel(4);for(String id:List.of("forked_arc","magnetic_bracer",GroundingReturn.ID,PotentialBuffer.ID))state.learn(id);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,PotentialBuffer.ID,id->id.equals(PotentialBuffer.ID)),"genuine learned142slot");ElectromasterPrototype.initializeBudget(p);epoch();
            attacker=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[HP-Buffer]"));attacker.setPos(p.position().add(0,0,3));attacker.setNoGravity(true);attacker.getAbilities().instabuild=false;
            var s=AbilityStorage.get(attacker);s.changeCategoryClassic("electromaster");s.setLevel(5);h.assertTrue(HighPowerCombat.enroll(attacker,5),"approved exact electrical source actor");
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        void ready(Runnable action){h.runAfterDelay(61,()->{p.doTick();place();attacker.setPos(p.position().add(0,0,3));action.run();});}
        void next(Runnable action){h.runAfterDelay(1,()->{p.doTick();place();attacker.setPos(p.position().add(0,0,3));action.run();});}
        void place(){Vec3 at=h.absoluteVec(new Vec3(6.5,2,6.5));p.moveTo(at.x,at.y,at.z,0,0);p.setYHeadRot(0);p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();}
        Zombie mob(double x,double z){Zombie e=EntityType.HUSK.create(h.getLevel());e.setNoAi(true);e.setNoGravity(true);e.setSilent(true);e.setOnGround(false);e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);e.setPos(p.position().add(x,0,z));h.getLevel().addFreshEntity(e);entities.add(e);return e;}
        LivingEntity passive(double x,double z){var e=EntityType.COW.create(h.getLevel());e.setNoAi(true);e.setNoGravity(true);e.setPos(p.position().add(x,0,z));h.getLevel().addFreshEntity(e);entities.add(e);return e;}
        Arrow arrow(LivingEntity shooter,ItemStack stack){Arrow arrow=new Arrow(h.getLevel(),shooter,stack,null);arrow.setPos(p.getEyePosition().add(0,0,3));h.getLevel().addFreshEntity(arrow);entities.add(arrow);return arrow;}
        void ally(Entity entity){var board=h.getLevel().getScoreboard();var team=board.addPlayerTeam("pb"+UUID.randomUUID().toString().substring(0,10));team.setAllowFriendlyFire(false);board.addPlayerToTeam(p.getScoreboardName(),team);board.addPlayerToTeam(entity.getScoreboardName(),team);teams.add(team);}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        boolean toggle(long input){return PotentialBuffer.toggle(p,wire(input));}
        boolean withPvp(java.util.function.BooleanSupplier action){boolean previous=p.server.isPvpAllowed();p.server.setPvpAllowed(true);try{return action.getAsBoolean();}finally{p.server.setPvpAllowed(previous);}}
        boolean hit(String amount){return withPvp(()->HighPowerCombat.attack(attacker,ELECTRIC,p,n(amount)));}
        boolean hurt(DamageSource source,float amount){return withPvp(()->p.hurt(source,amount));}
        BigDecimal hp(){return n(HighPowerCombat.healthText(p));}BigDecimal shield(){return HighPowerCombat.shield(p);}double cp(){return ElectromasterPrototype.prototypeCp(p);}double xp(){return AbilityStorage.get(p).exp(PotentialBuffer.ID);}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);PotentialBuffer.remove(p);GroundingReturn.remove(p);AbilityStorage.remove(p);AbilityStorage.remove(attacker);attacker.discard();h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}
