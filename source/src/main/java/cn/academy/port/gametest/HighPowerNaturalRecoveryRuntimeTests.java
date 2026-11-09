package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.HighPowerLevel;
import com.mojang.authlib.GameProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native FoodData and real heal-event tests; explicit setup writes are isolated fixture controls. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerNaturalRecoveryRuntimeTests {
    private static final String TEMPLATE = "highpower_empty";
    private HighPowerNaturalRecoveryRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void saturated_native_20hp_control_and_large_pool_finish_same_tick(GameTestHelper h) {
        Fixture f = new Fixture(h); var control=f.player(false,5);var large=f.player(true,5);
        control.setHealth(10); f.hp(large,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO));
        f.food(control,20,20);f.food(large,20,20);Counter counter=new Counter(large);f.hook(counter);
        int ticks=0;
        while(control.getHealth()<control.getMaxHealth()&&ticks<150){
            control.getFoodData().tick(control);large.getFoodData().tick(large);ticks++;
            f.sameFood(control,large);h.assertTrue(large.getHealth()==control.getHealth(),"same normalized progress every native tick");
        }
        h.assertTrue(ticks==100&&control.getHealth()==20,"vanilla ten saturated pulses finish at 100 ticks");
        f.exact(large,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"large pool completes on identical native tick");h.assertTrue(counter.calls==10,"one heal event per native pulse");
        h.assertTrue(HighPowerNaturalRecovery.snapshot(large).orElseThrow().exactHpPerSecond().equals("0"),"full pool reports zero scheduled rate");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void slow_food18_control_matches_80tick_pulses_and_exhaustion(GameTestHelper h) {
        Fixture f=new Fixture(h);var control=f.player(false,5);var large=f.player(true,5);
        control.setHealth(10);f.hp(large,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO));f.food(control,18,0);f.food(large,18,0);
        int tick=0;
        while(control.getHealth()<20 && tick<1000){
            tick++;
            // Identical declared feeding input keeps both controls on the ordinary food-18 branch.
            control.getFoodData().setFoodLevel(18);large.getFoodData().setFoodLevel(18);
            control.getFoodData().tick(control);large.getFoodData().tick(large);f.sameFood(control,large);
            h.assertTrue(control.getHealth()==large.getHealth(),"slow branch proportional progress at tick "+tick);
            if(control.getHealth()<20)h.assertTrue(new BigDecimal(HighPowerCombat.healthText(large)).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth())<0,"large pool never finishes before native control");
        }
        f.exact(large,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"slow branch full on identical tick, including ordinary hunger branch interruptions");h.assertTrue(control.getHealth()==20&&tick>=800&&tick<1000,"ordinary control same completion with ten 80tick pulses");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void tiny_exact_deficit_triggers_food_regen_even_when_native_float_is_full(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(true,5);f.hp(p,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(new BigDecimal("0.000001")));f.food(p,20,6);
        h.assertTrue(p.getHealth()==20&&!p.isHurt(),"normalized float cannot represent the tiny exact deficit");
        var before=HighPowerNaturalRecovery.snapshot(p).orElseThrow();h.assertTrue(before.pulseTicks()==10&&before.ticksUntilPulse()==10,"exact deficit starts real native ten tick cycle");
        f.ticks(p,9);f.exact(p,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(new BigDecimal("0.000001")),"no pre-pulse award");f.ticks(p,1);
        f.exact(p,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"native predicate seam reaches exact missing fraction");f.ticks(p,80);f.exact(p,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"full ledger never overflows");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void final_heal_veto_zero_nan_and_tiny_amount_observed_once_with_vanilla_food_cost(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(true,5);f.hp(p,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO));f.food(p,20,6);
        Counter hook=new Counter(p);f.hook(hook);var start=new BigDecimal(HighPowerCombat.healthText(p));
        var control=f.player(false,5);control.setHealth(10);f.food(control,20,6);Counter controlHook=new Counter(control);f.hook(controlHook);
        controlHook.cancel=true;hook.cancel=true;f.ticks(control,10);f.ticks(p,10);f.sameFood(control,p);f.exact(p,start,"late final cancellation wins");h.assertTrue(p.getFoodData().getExhaustionLevel()==6,"canceled recovery retains vanilla food exhaustion");f.food(p,20,6);f.food(control,20,6);
        controlHook.cancel=false;controlHook.rewrite=0F;f.ticks(control,10);hook.cancel=false;hook.rewrite=0F;f.ticks(p,10);f.exact(p,start,"zero final amount wins");f.sameFood(control,p);h.assertTrue(p.getFoodData().getExhaustionLevel()==6,"zero recovery retains vanilla food exhaustion");f.food(p,20,6);
        hook.rewrite=Float.NaN;f.ticks(p,10);f.exact(p,start,"NaN final amount refused");h.assertTrue(Float.isFinite(p.getHealth())&&p.getFoodData().getExhaustionLevel()==6,"NaN cannot poison adapter; ordinary food cost remains");f.food(p,20,6);
        hook.rewrite=Float.MIN_VALUE;f.ticks(p,10);
        f.exact(p,start,"native underflowing finite amount cannot create proportional recovery");h.assertTrue(hook.calls==4,"exactly four native callbacks");h.assertTrue(p.getFoodData().getExhaustionLevel()==6,"successful tiny pulse retains native food charge");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void effects_and_direct_item_style_heals_remain_one_to_one_and_nested_heals_do_not_inherit_source(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(true,5);var half=cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO);f.hp(p,half);
        p.heal(4);f.flush(p);f.exact(p,half.add(BigDecimal.valueOf(4)),"direct item-style native heal stays four exact points");
        MobEffects.REGENERATION.value().applyEffectTick(p,0);f.flush(p);f.exact(p,half.add(BigDecimal.valueOf(5)),"native Regeneration pulse remains one point");
        MobEffects.HEAL.value().applyInstantenousEffect(null,null,p,0,1);f.flush(p);f.exact(p,half.add(BigDecimal.valueOf(9)),"instant health potion keeps native four-point amount");
        f.food(p,20,6);Counter nested=new Counter(p);nested.nested=true;f.hook(nested);f.ticks(p,10);
        f.exact(p,half.add(BigDecimal.valueOf(12)).add(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.valueOf(20))),"nested three-point heal never inherits FoodData scaling");
        h.assertTrue(nested.calls==2,"one ordinary and one natural callback, no replay");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void hunger_gamerule_wrong_category_unlearned_and_inactive_states_do_not_gain_scaled_healing(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(true,5);var half=cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO);f.hp(p,half);f.food(p,17,0);f.ticks(p,100);
        f.exact(p,half,"food17 has no natural recovery");h.assertTrue(HighPowerNaturalRecovery.snapshot(p).orElseThrow().status().equals("food_low"),"authoritative low-food status");
        boolean original=h.getLevel().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        try {h.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,h.getLevel().getServer());f.food(p,20,6);f.ticks(p,100);f.exact(p,half,"gamerule off blocks recovery");
            h.assertTrue(HighPowerNaturalRecovery.snapshot(p).orElseThrow().status().equals("gamerule_off"),"gamerule status current");}
        finally {h.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(original,h.getLevel().getServer());}
        AbilityStorage.get(p).unlearn(HighPowerSkills.FORKED_ARC);f.food(p,20,6);f.ticks(p,10);f.flush(p);f.exact(p,half.add(BigDecimal.ONE),"unlearned player receives ordinary one point only");
        AbilityStorage.get(p).changeCategoryClassic("teleporter");f.food(p,20,6);f.ticks(p,10);f.flush(p);f.exact(p,half.add(BigDecimal.TWO),"other category retains ordinary units");
        var tp=HighPowerNaturalRecovery.snapshot(p).orElseThrow();
        h.assertTrue(tp.status().equals("unlearned")&&tp.exactHpPerSecond().equals("0"),"TP exposes only an unlearned recovery snapshot before313");
        AbilityStorage.get(p).changeCategoryClassic("unsupported_test");
        h.assertTrue(HighPowerNaturalRecovery.snapshot(p).isEmpty(),"unadmitted category has no high-power recovery HUD");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery_pause",timeoutTicks=65)
    public static void offensive_pause_resumes_after_actual_server_ticks_and_has_no_regen_exhaustion(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(true,5);var half=cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO);f.hp(p,half);f.food(p,20,6);
        HighPowerNaturalRecovery.offensiveAction(p);f.ticks(p,100);f.exact(p,half,"repeated native food ticks cannot bypass actual server rest clock");
        h.assertTrue(p.getFoodData().getExhaustionLevel()==0,"paused recovery adds no exhaustion");var paused=HighPowerNaturalRecovery.snapshot(p).orElseThrow();
        h.assertTrue(paused.pauseTicksRemaining()==40&&paused.exactHpPerSecond().equals("0")&&paused.status().equals("paused"),"default40 explicit and bounded");
        h.runAtTickTime(39,()->{f.ticks(p,10);f.exact(p,half,"39 elapsed ticks still paused");});
        h.runAtTickTime(40,()->{f.ticks(p,9);f.exact(p,half,"fresh ordinary ten tick pulse starts after rest pause");f.ticks(p,1);f.exact(p,half.add(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.valueOf(20))),"healing resumes after40plus10");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void zero_pause_configuration_preserves_strict_vanilla_timing(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(true,5);var half=cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO);f.hp(p,half);f.food(p,20,6);
        int previous=HighPowerRecoveryConfig.COMBAT_PAUSE_TICKS.get();
        try {HighPowerRecoveryConfig.COMBAT_PAUSE_TICKS.set(0);HighPowerNaturalRecovery.offensiveAction(p);f.ticks(p,10);f.exact(p,half.add(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.valueOf(20))),"zero setting has no added delay");}
        finally {HighPowerRecoveryConfig.COMBAT_PAUSE_TICKS.set(previous);}h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void save_reload_preserves_food_timer_exact_balance_and_combat_pause_while_promotion_never_refills(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(true,1);var hp=HighPowerLevel.LV1.maxHealth().subtract(BigDecimal.ONE);f.hp(p,hp);f.food(p,20,6);f.ticks(p,7);
        CompoundTag saved=p.saveWithoutId(new CompoundTag());var reload=f.player(false,1);reload.load(saved);
        var state=AbilityStorage.get(reload);state.changeCategoryClassic("electromaster");state.setLevel(1);state.learn(HighPowerSkills.FORKED_ARC);
        f.exact(reload,hp,"actual entity NBT preserves exact balance");h.assertTrue(((NativeFoodRecoveryAccess)reload.getFoodData()).academy$naturalRecoveryTimer()==7,"actual food save timer restored");
        f.ticks(reload,2);f.exact(reload,hp,"no early reload heal");f.ticks(reload,1);f.exact(reload,HighPowerLevel.LV1.maxHealth(),"restored timer reaches exact pulse");
        var before=new BigDecimal(HighPowerCombat.healthText(reload));
        state.addExperience(HighPowerSkills.FORKED_ARC,cn.academy.port.core.ClassicRules.levelThreshold(1,cn.academy.port.SkillCatalog.levelSkillCount(state)));
        // Isolated completion-seam fixture; the paid native developer is covered by growth/progression tests.
        HighPowerCombat.completeEarnedLevel(reload,()->state.setLevel(2));f.exact(reload,before,"earned progress raises capacity only");
        h.assertTrue(new BigDecimal(HighPowerCombat.maxHealthText(reload)).compareTo(HighPowerLevel.LV2.maxHealth())==0,"promoted maximum");
        f.food(reload,20,6);f.ticks(reload,10);f.exact(reload,before.add(HighPowerLevel.LV2.maxHealth().divide(BigDecimal.valueOf(20))),"natural recovery supplies usable promoted HP");
        HighPowerNaturalRecovery.offensiveAction(reload);CompoundTag combatSave=reload.saveWithoutId(new CompoundTag());var paused=f.player(false,2);paused.load(combatSave);
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(paused)==40,"pause persisted across entity reload");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void earlier_highest_priority_nested_heal_cannot_steal_outer_food_source(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(true,5);var half=cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO);f.hp(p,half);f.food(p,20,6);
        EarlyNested early=new EarlyNested(p);
        // Explicitly order the adversarial test hook ahead of the production HIGHEST observer.
        NeoForge.EVENT_BUS.unregister(HighPowerCombat.class);
        NeoForge.EVENT_BUS.register(early);
        NeoForge.EVENT_BUS.register(HighPowerCombat.class);
        try {
            f.ticks(p,10);
            f.exact(p,half.add(BigDecimal.valueOf(3)).add(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.valueOf(20))),
                    "outer FoodData amount alone scales even when nested event precedes our observer");
            h.assertTrue(early.calls==2,"two real native events without callback replay");
        } finally { NeoForge.EVENT_BUS.unregister(early); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void accepted_damage_and_clone_preserve_pause_without_extra_hunger_charge(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.connectedPlayer();var half=cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO);f.hp(p,half);f.food(p,20,6);
        h.assertTrue(p.hurt(p.damageSources().fellOutOfWorld(),1),"real reviewed damage reaches native pipeline without fixture spawn-timer edits");
        f.exact(p,half.subtract(BigDecimal.ONE),"damage remains exactly one ordinary point");
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p)==40,"accepted exact HP damage triggers rest pause");
        p.getFoodData().setExhaustion(6);p.getFoodData().tick(p);
        h.assertTrue(p.getFoodData().getSaturationLevel()==5&&p.getFoodData().getExhaustionLevel()==2,"pause retains ordinary preexisting exhaustion processing");
        var replacement=f.player(false,5);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(replacement,p,false));
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(replacement)==40,"nondeath clone cannot evade pause");f.exact(replacement,half.subtract(BigDecimal.ONE),"clone preserves spent exact balance");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void partial_saturation_native_maximum_and_food_zero_use_original_conditions(GameTestHelper h) {
        Fixture f=new Fixture(h);var control=f.player(false,5);var p=f.player(true,5);var half=cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO);
        control.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40);
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40);
        control.setHealth(20);f.hp(p,half);p.setHealth(20);f.food(control,20,3);f.food(p,20,3);f.ticks(control,10);f.ticks(p,10);
        f.exact(p,half.add(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.valueOf(80))),"half-point native pulse divided by actual forty-point native maximum");
        h.assertTrue(control.getHealth()==20.5F&&p.getHealth()==20.5F,"native maximum is denominator, not hardcoded twenty");f.sameFood(control,p);
        var hp=new BigDecimal(HighPowerCombat.healthText(p));f.food(p,0,0);f.ticks(p,79);f.exact(p,hp,"zero food never enters either natural healing branch");
        h.assertTrue(HighPowerNaturalRecovery.snapshot(p).orElseThrow().exactHpPerSecond().equals("0"),"zero-food scheduled rate finite zero");h.succeed();
    }

    public static final class EarlyNested {
        final ServerPlayer player;boolean nested;int calls;
        EarlyNested(ServerPlayer player){this.player=player;}
        @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public void heal(LivingHealEvent event){
            if(event.getEntity()!=player)return;calls++;
            if(!nested){nested=true;try{player.heal(3);}finally{nested=false;}}
        }
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void fractional_pulses_match_native_float_completion_at_110_ticks(GameTestHelper h) {
        Fixture f=new Fixture(h);var control=f.player(false,5);var p=f.player(true,5);
        control.setHealth(19.5F);f.hp(p,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().multiply(new BigDecimal("0.975")));f.food(control,20,.3F);f.food(p,20,.3F);
        f.ticks(control,100);f.ticks(p,100);h.assertTrue(new BigDecimal(HighPowerCombat.healthText(p)).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth())<0,"exact pool retains attributable native rounding tail after ten pulses");
        h.assertTrue(control.getHealth()<20&&control.getHealth()>19.9999F,"ordinary float accumulation still has rounding tail at100ticks");
        f.ticks(control,10);f.ticks(p,10);h.assertTrue(control.getHealth()==20,"native float control completes its eleventh pulse at110ticks");f.exact(p,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"exact pool finishes on same110tick genuine positive pulse");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void fractional_food_and_injury_matrix_finishes_on_same_real_native_pulse(GameTestHelper h) {
        Fixture f=new Fixture(h);
        for(float initial:new float[]{18F,19.5F,19.9F})for(float saturation:new float[]{.3F,.6F,1.8F,3F,5.7F}){
            var control=f.player(false,5);var p=f.player(true,5);control.setHealth(initial);
            f.hp(p,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().multiply(new BigDecimal(Float.toString(initial))).divide(BigDecimal.valueOf(20)));f.food(control,20,saturation);f.food(p,20,saturation);
            int ticks=0;
            while(control.getHealth()<20&&ticks<800){
                control.getFoodData().setFoodLevel(20);p.getFoodData().setFoodLevel(20);
                control.getFoodData().setSaturation(saturation);p.getFoodData().setSaturation(saturation);
                f.ticks(control,1);f.ticks(p,1);ticks++;f.sameFood(control,p);
                boolean nativeFull=control.getHealth()>=20,exactFull=new BigDecimal(HighPowerCombat.healthText(p)).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth())==0;
                h.assertTrue(nativeFull==exactFull,"identical completion tick for initial="+initial+" saturation="+saturation+" tick="+ticks);
            }
            h.assertTrue(ticks<800,"fractional native benchmark terminates");f.exact(p,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"fractional matrix full");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void fractional_reference_survives_reload_and_never_heals_from_proxyfull_mapping(GameTestHelper h) {
        Fixture f=new Fixture(h);var control=f.player(false,5);var p=f.player(true,5);control.setHealth(19.5F);
        f.hp(p,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().multiply(new BigDecimal(".975")));f.food(control,20,.3F);f.food(p,20,.3F);f.ticks(control,50);f.ticks(p,50);
        var reload=f.player(false,5);reload.load(p.saveWithoutId(new CompoundTag()));var state=AbilityStorage.get(reload);state.changeCategoryClassic("electromaster");state.setLevel(5);state.learn(HighPowerSkills.FORKED_ARC);
        f.ticks(control,50);f.ticks(reload,50);h.assertTrue(control.getHealth()<20&&new BigDecimal(HighPowerCombat.healthText(reload)).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth())<0,"reload retains float rounding tail after100ticks");
        f.ticks(control,10);f.ticks(reload,10);f.exact(reload,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"saved reference finishes with nativecontrol at110ticks");
        f.hp(reload,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(new BigDecimal("0.000001")));f.food(reload,17,0);
        for(int i=0;i<100;i++){HighPowerNaturalRecovery.snapshot(reload);f.ticks(reload,1);}f.exact(reload,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(new BigDecimal("0.000001")),"full proxy mapping and status reads never grant missing HP without valid food pulse");
        f.food(reload,20,6);var snapshot=HighPowerNaturalRecovery.snapshot(reload).orElseThrow();h.assertTrue(snapshot.exactHpPerSecond().equals("0.000002"),"tiny deficit scheduled rate reflects the actual missing fraction per half second");
        Counter cancel=new Counter(reload);cancel.cancel=true;f.hook(cancel);f.ticks(reload,10);f.exact(reload,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(new BigDecimal("0.000001")),"cancelled full-reference pulse grants nothing");
        cancel.cancel=false;cancel.rewrite=0F;f.food(reload,20,6);f.ticks(reload,10);f.exact(reload,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(new BigDecimal("0.000001")),"zero full-reference pulse grants nothing");
        cancel.rewrite=Float.MIN_VALUE;f.food(reload,20,6);f.ticks(reload,10);
        f.exact(reload,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(new BigDecimal("0.000001")).add(HighPowerNaturalRecovery.scaledAmount(Float.MIN_VALUE,20,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth())),"tiny positive full-reference pulse applies only bounded nominal budget, never entire deficit");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void large_nested_ordinary_heal_remaps_reference_without_becoming_natural_gain(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(true,5);var half=cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO);f.hp(p,half);f.food(p,20,.3F);
        Counter nested=new Counter(p);nested.nested=true;nested.nestedAmount=HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TEN).floatValue();f.hook(nested);f.ticks(p,10);nested.nested=false;
        float firstReference=12F+.3F/6F;
        BigDecimal firstGain=HighPowerNaturalRecovery.scaledAmount(firstReference-12F,20,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth());
        BigDecimal baseline=half.add(new BigDecimal(Float.toString(nested.nestedAmount)));
        f.exact(p,baseline.add(firstGain),"nested huge ordinary heal remains exactly its native-unit amount");
        h.assertTrue(p.getPersistentData().getCompound(HighPowerNaturalRecovery.DATA_KEY).getFloat("referenceHealth")==firstReference,"reference is remapped from updated exact baseline before outer natural pulse");
        f.ticks(p,10);float secondReference=firstReference+.3F/6F;
        f.exact(p,baseline.add(firstGain).add(HighPowerNaturalRecovery.scaledAmount(secondReference-firstReference,20,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth())),"next fractional pulse advances the corrected persisted reference");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void one_point_native_maximum_advertises_bounded_exact_rate_above_pool_cap(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(true,5);p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1);
        f.hp(p,BigDecimal.ONE);p.setHealth(1.0e-20F);f.food(p,20,6);
        var snapshot=HighPowerNaturalRecovery.snapshot(p).orElseThrow();
        h.assertTrue(snapshot.exactHpPerSecond().equals(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(BigDecimal.ONE).multiply(BigDecimal.TWO).toPlainString()),"per-second rate uses up to2CAP while HP itself remains capped atCAP");
        f.ticks(p,10);f.exact(p,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"actual half-second native pulse matches advertised missing-HP rate");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void real_bow_and_trident_launch_pause_but_utility_reloads_and_ineligible_owners_do_not(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.connectedPlayer();var bow=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,bow);p.getInventory().items.set(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW,4));
        p.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        net.minecraft.world.item.Items.BOW.releaseUsing(bow,h.getLevel(),p,bow.getUseDuration(p)-20);
        var arrows=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,p.getBoundingBox().inflate(8),arrow->arrow.getOwner()==p);
        h.assertTrue(!arrows.isEmpty()&&HighPowerNaturalRecovery.pauseTicksRemaining(p)==40,"real native bow release starts pause at launch before any hit");f.entities.addAll(arrows);p.stopUsingItem();f.clearPause(p);
        var trident=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,trident);p.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        net.minecraft.world.item.Items.TRIDENT.releaseUsing(trident,h.getLevel(),p,trident.getUseDuration(p)-10);p.stopUsingItem();
        var tridents=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.ThrownTrident.class,p.getBoundingBox().inflate(8),arrow->arrow.getOwner()==p);
        h.assertTrue(!tridents.isEmpty()&&HighPowerNaturalRecovery.pauseTicksRemaining(p)==40,"real native thrown trident is covered");f.entities.addAll(tridents);f.clearPause(p);
        var pearl=new net.minecraft.world.entity.projectile.ThrownEnderpearl(net.minecraft.world.entity.EntityType.ENDER_PEARL,h.getLevel());pearl.setOwner(p);f.entity(pearl);
        var snow=new net.minecraft.world.entity.projectile.Snowball(net.minecraft.world.entity.EntityType.SNOWBALL,h.getLevel());snow.setOwner(p);f.entity(snow);
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p)==0,"utility pearl and snowball launches are not classified as weapon attacks");
        var reloaded=new net.minecraft.world.entity.projectile.Arrow(net.minecraft.world.entity.EntityType.ARROW,h.getLevel());reloaded.setOwner(p);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(reloaded,h.getLevel(),true));
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p)==0,"disk rejoin of an existing arrow is not a new launch");
        var canceled=new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(reloaded,h.getLevel(),false);canceled.setCanceled(true);NeoForge.EVENT_BUS.post(canceled);
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p)==0,"canceled projectile launch does not start pause");
        AbilityStorage.get(p).unlearn(HighPowerSkills.FORKED_ARC);var unlearned=new net.minecraft.world.entity.projectile.Arrow(net.minecraft.world.entity.EntityType.ARROW,h.getLevel());unlearned.setOwner(p);f.entity(unlearned);
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p)==0,"enrollment alone cannot activate passive offense marker");
        AbilityStorage.get(p).changeCategoryClassic("teleporter");var other=new net.minecraft.world.entity.projectile.Arrow(net.minecraft.world.entity.EntityType.ARROW,h.getLevel());other.setOwner(p);f.entity(other);
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p)==0,"other category launch has no EM pause mutation");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery",timeoutTicks=10)
    public static void legacy_em_and_successful_outgoing_native_damage_pause_without_zero_or_category_leaks(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.connectedPlayer();var target=new net.minecraft.world.entity.npc.Villager(net.minecraft.world.entity.EntityType.VILLAGER,h.getLevel());target.setNoAi(true);f.entity(target);
        h.assertTrue(cn.academy.port.AbilityDamage.attack(p,"electromaster.arc_gen",target,1),"real legacy EM AbilityDamage call enters native damage");
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p)==40,"legacy EM attack pauses learned rest passive");f.clearPause(p);
        NeoForge.EVENT_BUS.post(new cn.academy.port.api.SkillAttackEvent(p,"electromaster.arc_gen",target,0));
        NeoForge.EVENT_BUS.post(new cn.academy.port.api.SkillAttackEvent(p,"electromaster.arc_gen",target,Double.NaN));
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p)==0,"zero/nonfinite skill events add no offense marker");
        target.invulnerableTime=0;h.assertTrue(target.hurt(p.damageSources().playerAttack(p),1),"actual ordinary outgoing damage without manual AttackEntityEvent");
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p)==40,"positive native outgoing Post supplies fallback pause");f.clearPause(p);
        AbilityStorage.get(p).changeCategoryClassic("teleporter");target.invulnerableTime=0;cn.academy.port.AbilityDamage.attack(p,"teleporter.threatening_teleport",target,1);
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p)==0,"other ability category keeps its existing behavior");h.succeed();
    }

    private static final class Fixture {
        final GameTestHelper h;final List<ServerPlayer> players=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final List<net.minecraft.world.entity.Entity> entities=new ArrayList<>();final List<io.netty.channel.embedded.EmbeddedChannel> channels=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});}
        ServerPlayer player(boolean enrolled,int level){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[HP-Rest]"));players.add(p);p.setNoGravity(true);p.getAbilities().instabuild=false;if(enrolled){var s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");s.setLevel(level);s.learn(HighPowerSkills.FORKED_ARC);h.assertTrue(HighPowerCombat.enroll(p,level),"reviewed fixture enrollment "+HighPowerCombat.unsupportedReason(p));}return p;}
        ServerPlayer connectedPlayer(){
            var server=h.getLevel().getServer();var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPRestProbe"),false);
            var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());players.add(p);
            var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            channels.add(new io.netty.channel.embedded.EmbeddedChannel(connection));net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection,p,cookie);p.setNoGravity(true);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var position=h.absoluteVec(new net.minecraft.world.phys.Vec3(5,2,5));p.moveTo(position.x,position.y,position.z,0,0);
            var state=AbilityStorage.get(p);state.changeCategoryClassic("electromaster");state.setLevel(5);state.learn(HighPowerSkills.FORKED_ARC);
            h.assertTrue(HighPowerCombat.enroll(p,5),"real player enrolled");return p;
        }
        void entity(net.minecraft.world.entity.Entity entity){entity.setNoGravity(true);entity.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(5,3,7)));h.getLevel().addFreshEntity(entity);entities.add(entity);}
        void clearPause(ServerPlayer p){p.getPersistentData().getCompound(HighPowerNaturalRecovery.DATA_KEY).remove("lastCombatTick");}
        void hp(ServerPlayer p,BigDecimal hp){p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health",CombatAmounts.decimalString(hp));p.setHealth(hp.divide(new BigDecimal(HighPowerCombat.maxHealthText(p)),java.math.MathContext.DECIMAL128).multiply(BigDecimal.valueOf(20)).floatValue());}
        void food(ServerPlayer p,int level,float saturation){var food=p.getFoodData();food.setFoodLevel(level);food.setSaturation(saturation);food.setExhaustion(0);CompoundTag tag=new CompoundTag();food.addAdditionalSaveData(tag);tag.putInt("foodTickTimer",0);food.readAdditionalSaveData(tag);}
        void ticks(ServerPlayer p,int count){for(int i=0;i<count;i++)p.getFoodData().tick(p);}
        void flush(ServerPlayer p){HighPowerCombat.tick(new EntityTickEvent.Post(p));}
        void sameFood(ServerPlayer a,ServerPlayer b){var x=a.getFoodData();var y=b.getFoodData();h.assertTrue(x.getFoodLevel()==y.getFoodLevel()&&x.getSaturationLevel()==y.getSaturationLevel()&&x.getExhaustionLevel()==y.getExhaustionLevel(),"identical native hunger/saturation/exhaustion");}
        void exact(ServerPlayer p,BigDecimal expected,String message){h.assertTrue(new BigDecimal(HighPowerCombat.healthText(p)).compareTo(expected)==0,message+": actual="+HighPowerCombat.healthText(p)+" expected="+expected);}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);players.forEach(p->{AbilityStorage.remove(p);var list=h.getLevel().getServer().getPlayerList();if(list.getPlayer(p.getUUID())==p)list.remove(p);else p.discard();});entities.forEach(net.minecraft.world.entity.Entity::discard);channels.forEach(io.netty.channel.embedded.EmbeddedChannel::finishAndReleaseAll);}
    }
    public static final class Counter {
        final ServerPlayer player;int calls;boolean cancel,nested,inside;float nestedAmount=3;Float rewrite;
        Counter(ServerPlayer player){this.player=player;}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void heal(LivingHealEvent event){if(event.getEntity()!=player)return;calls++;if(cancel)event.setCanceled(true);if(rewrite!=null)event.setAmount(rewrite);if(nested&&!inside){inside=true;try{player.heal(nestedAmount);}finally{inside=false;}}}
    }
}
