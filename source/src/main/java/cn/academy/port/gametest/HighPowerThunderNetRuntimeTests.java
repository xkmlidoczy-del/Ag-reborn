package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ThunderNetRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW141 connected-owner input, actual native Arc receipts, fixed locks, real damage and cancellation tests. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerThunderNetRuntimeTests {
    private static final String TEMPLATE="highpower_empty",BATCH="highpower_thunder_net";
    private static int pvpFixtures;private static boolean originalPvp;
    private HighPowerThunderNetRuntimeTests(){}
    private static BigDecimal hp(LivingEntity e){return new BigDecimal(HighPowerCombat.healthText(e));}
    private static void near(GameTestHelper h,double a,double b,String why){h.assertTrue(Math.abs(a-b)<1e-6,why+": "+a+" != "+b);}
    private static void exact(GameTestHelper h,BigDecimal a,BigDecimal b,String why){h.assertTrue(a.compareTo(b)==0,why+": "+a+" != "+b);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void thunder_empty_and_forged_legacy_nbt_are_free(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp(),exp=AbilityStorage.get(f.p).exp(ThunderNet.ID);
        h.assertFalse(ThunderNet.begin(f.p,f.wire(1)),"empty is free");Zombie target=f.target(0,6);
        target.getPersistentData().putLong("academy:conductive_until",h.getLevel().getGameTime()+1200);
        h.assertFalse(ConductiveMarkReceipts.has(f.p,target),"bare administrative/legacy timestamp is not receipt");
        h.assertFalse(ThunderNet.begin(f.p,f.wire(2)),"dry forged mark cannot lock");
        h.assertFalse(ThunderNet.release(f.p,f.wire(2)),"release cannot mint a begin");near(h,f.cp(),cp,"no empty CP");near(h,AbilityStorage.get(f.p).exp(ThunderNet.ID),exp,"no empty XP");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void thunder_tap_six_fixed_targets_one_payment_exact_damage_and_replay(GameTestHelper h){
        Fixture f=new Fixture(h);List<Zombie> targets=new ArrayList<>();for(int i=0;i<8;i++){Zombie t=f.target((i%3-1)*2,6+i/3*2);targets.add(t);f.mark(t);}
        h.runAfterDelay(21,()->{
            h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"native selected set");h.assertTrue(ThunderNet.lockedCount(f.p)==6,"six target cap");Set<UUID> locked=new HashSet<>();ThunderNet.serverNodes(f.p).forEach(n->locked.add(n.uuid()));
            Map<UUID,BigDecimal> before=new HashMap<>();targets.forEach(t->before.put(t.getUUID(),hp(t)));BigDecimal damage=ThunderNetRules.damage(HighPowerGrowth.values(f.p).damage(),false);double cp=f.cp();
            h.assertTrue(ThunderNet.release(f.p,f.wire(1)),"tap committed");near(h,f.cp(),cp-10,"singleLV4tapcost");
            for(Zombie t:targets)exact(h,before.get(t.getUUID()).subtract(hp(t)),locked.contains(t.getUUID())?damage:BigDecimal.ZERO,"per-target exact2.5D only locked subset");
            h.assertFalse(ThunderNet.release(f.p,f.wire(1)),"release replay");h.assertFalse(ThunderNet.begin(f.p,f.wire(1)),"begin replay");near(h,f.cp(),cp-10,"replays cannot double pay");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void thunder_hold_three_native_pulses_twenty_tick_gap_and_final_cooldown(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,7);f.mark(target);Observe observe=new Observe(f.p);f.hook(observe);
        // Embedded transport omits doTick; run the actual native player path, including PlayerTick cooldowns.
        h.onEachTick(()->{if(!f.closed)f.p.doTick();});
        h.runAfterDelay(21,()->{
            h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"held begin");double exp=AbilityStorage.get(f.p).exp(ThunderNet.ID);BigDecimal initial=hp(target),damage=ThunderNetRules.damage(HighPowerGrowth.values(f.p).damage(),true);
            h.runAfterDelay(7,()->{ThunderNet.tick(f.p);h.assertTrue(observe.times.isEmpty(),"seven ticks remains free");near(h,AbilityStorage.get(f.p).exp(ThunderNet.ID),exp,"idle noXP");});
            h.runAfterDelay(8,()->{double cp=f.cp();ThunderNet.tick(f.p);h.assertTrue(observe.times.size()==1,"first pulse atthreshold8");near(h,f.cp(),cp-3,"one productive hold debit");h.assertTrue(target.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThunderNetRestraint.MODIFIER_ID)!=null,"native restraint installed");});
            h.runAfterDelay(48,()->{ThunderNet.tick(f.p);h.assertTrue(observe.times.size()==3,"only three pulses at8/28/48; actual="+observe.times+" active="+ThunderNet.active(f.p)+" pulses="+ThunderNet.pulseCount(f.p)+" classicCooldown="+AbilityStorage.get(f.p).cooldowns+" locks="+ThunderNet.lockedCount(f.p));for(int i=1;i<observe.times.size();i++)h.assertTrue(observe.times.get(i)-observe.times.get(i-1)>=20,"native invulnerability interval preserved");exact(h,initial.subtract(hp(target)),damage.add(ThunderNetRules.damage(HighPowerGrowth.values(f.p).damage(),true).multiply(BigDecimal.valueOf(2))),"three exactquarterD at each productivepulse growthsnapshot");h.assertTrue(observe.playerAttackOrigin,"nativePLAYER_ATTACK origin is actualcaster");});
            h.runAfterDelay(68,()->{ThunderNet.tick(f.p);h.assertFalse(ThunderNet.active(f.p),"60 active ticks hardstop");h.assertTrue(observe.times.size()==3,"noexpiry pulse");h.assertTrue(target.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThunderNetRestraint.MODIFIER_ID)==null,"expiry cleans only ownedmodifier");h.assertFalse(ThunderNet.begin(f.p,f.wire(2)),"termination cooldown cannotreenter");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void thunder_receipt_actual_arc_primary_only_not_branch(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie primary=f.target(0,6),branch=f.target(2,6);branch.getPersistentData().putLong("academy:conductive_until",h.getLevel().getGameTime()+100);
        f.p.setXRot(0);f.p.setYRot(0);h.assertTrue(ElectromasterPrototype.cast(f.p),"actualprimaryArc cast");
        h.assertTrue(ConductiveMarkReceipts.has(f.p,primary),"acceptedprimaryArc issues native proof");h.assertFalse(ConductiveMarkReceipts.has(f.p,branch),"branch damage neverissues primary receipt");
        h.assertTrue(primary.getPersistentData().contains("academy:conductive_until"),"legacy timestamp preserved");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=125)
    public static void thunder_receipt_caster_incarnation_replay_and_exact_expiry(GameTestHelper h){
        Fixture f=new Fixture(h),other=new Fixture(h);other.p.setPos(f.p.position().add(6,0,0));other.p.connection.resetPosition();Zombie target=f.target(0,6);
        var capture=ConductiveMarkReceipts.capture(f.p,target);h.assertTrue(HighPowerCombat.attack(f.p,ConductiveMarkReceipts.SOURCE,target,BigDecimal.ONE),"native acceptedArc");h.assertTrue(ConductiveMarkReceipts.acceptedArc(capture),"oneuse receipt");h.assertFalse(ConductiveMarkReceipts.acceptedArc(capture),"capture replay");
        h.assertTrue(ConductiveMarkReceipts.has(f.p,target),"owned receipt");h.assertFalse(ConductiveMarkReceipts.has(other.p,target),"othercaster cannotborrow");
        h.runAfterDelay(99,()->h.assertTrue(ConductiveMarkReceipts.has(f.p,target),"99tick stilllive"));
        h.runAfterDelay(100,()->{h.assertFalse(ConductiveMarkReceipts.has(f.p,target),"100tick expiredexactly");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void thunder_receipt_state_epoch_and_entity_reload_invalidate(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,6);f.mark(target);String old=f.wire(1);
        LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();h.assertFalse(ConductiveMarkReceipts.has(f.p,target),"sameusername entity staleepoch no proof");h.assertFalse(ThunderNet.begin(f.p,old),"old packet epoch refused");
        h.runAfterDelay(21,()->{f.mark(target);CompoundTag saved=target.saveWithoutId(new CompoundTag());target.discard();Zombie reloaded=EntityType.HUSK.create(h.getLevel());reloaded.load(saved);h.getLevel().addFreshEntity(reloaded);f.entities.add(reloaded);
            h.assertFalse(ConductiveMarkReceipts.has(f.p,reloaded),"sameUUID NBTreload cannot inherit object proof");h.assertFalse(ThunderNet.begin(f.p,f.wire(2)),"replacement remains dry and unmarked");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void thunder_capture_invalidated_inside_native_callback_never_issues(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,6);var capture=ConductiveMarkReceipts.capture(f.p,target);Object hook=new PostAction(target,()->AbilityStorage.get(f.p).presets.edit(0,1,"forked_arc",id->true));f.hook(hook);
        h.assertTrue(HighPowerCombat.attack(f.p,ConductiveMarkReceipts.SOURCE,target,BigDecimal.ONE),"nativeattack completes");h.assertFalse(ConductiveMarkReceipts.acceptedArc(capture),"changedcontext aftercallback deniesreceipt");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=45)
    public static void thunder_fixed_anchor_retirement_never_reacquires(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie primary=f.target(0,6),second=f.target(2,6);f.mark(primary);f.mark(second);
        h.runAfterDelay(21,()->{h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"capture two fixedlocks");Vec3 anchor=ThunderNet.anchor(f.p),old=second.position();h.assertTrue(ThunderNet.lockedCount(f.p)==2,"two locks");second.setPos(anchor.add(13,0,0));ThunderNet.tick(f.p);h.assertTrue(ThunderNet.lockedCount(f.p)==1,"sphere exit retires");second.setPos(old);ThunderNet.tick(f.p);h.assertTrue(ThunderNet.lockedCount(f.p)==1,"return cannotreacquire");h.assertTrue(ThunderNet.anchor(f.p).equals(anchor),"anchor neverfollows primary");double cp=f.cp();BigDecimal secondHp=hp(second);h.assertTrue(ThunderNet.release(f.p,f.wire(1)),"remainingtargettap");exact(h,hp(second),secondHp,"retiredtarget untouched");near(h,f.cp(),cp-10,"onlyonepayment");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=45)
    public static void thunder_native_water_loss_retires_without_legacy_proof(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,6);BlockPos water=target.blockPosition();h.getLevel().setBlockAndUpdate(water,Blocks.WATER.defaultBlockState());h.getLevel().setBlockAndUpdate(water.above(),Blocks.WATER.defaultBlockState());
        h.runAfterDelay(2,()->{h.assertTrue(target.isInWaterOrRain(),"nativewetness reallyupdated");h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"nativewater qualifies withoutreceipt");target.setPos(target.position().add(0,3,0));
            h.runAfterDelay(2,()->{ThunderNet.tick(f.p);h.assertFalse(target.isInWaterOrRain(),"nativewetness gone");h.assertFalse(ThunderNet.active(f.p),"onlylock permanentlyretired");h.assertFalse(ConductiveMarkReceipts.has(f.p,target),"waternevercreates receipt");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void thunder_lost_caster_line_of_sight_is_free_and_wall_unchanged(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,7);f.mark(target);
        h.runAfterDelay(21,()->{h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"visiblebegin");Vec3 middle=f.p.position().lerp(target.getBoundingBox().getCenter(),.5);BlockPos wall=BlockPos.containing(middle);h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());double cp=f.cp();BigDecimal before=hp(target);h.assertFalse(ThunderNet.release(f.p,f.wire(1)),"wallretires beforepay");near(h,f.cp(),cp,"noemptycost");exact(h,hp(target),before,"no damage throughwall");h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE),"skilldidnoteditwall");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void thunder_armor_native_reduction_and_same_tick_one_hit(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,7);f.mark(target);target.getAttribute(Attributes.ARMOR).setBaseValue(20);
        h.runAfterDelay(21,()->{BigDecimal before=hp(target),raw=ThunderNetRules.damage(HighPowerGrowth.values(f.p).damage(),false);Observe observed=new Observe(f.p);f.hook(observed);h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"armoredlock");h.assertTrue(ThunderNet.release(f.p,f.wire(1)),"armoredtap");exact(h,before.subtract(hp(target)),raw.multiply(new BigDecimal(".84")),"native armor20 reduces16percent");h.assertFalse(ThunderNet.release(f.p,f.wire(1)),"noiframe resetreplay");h.assertTrue(observed.times.size()==1,"exactonenativehurt");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=45)
    public static void thunder_partial_native_callback_cancels_remaining_without_refund(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie first=f.target(0,6),other=f.target(2,7);f.mark(first);f.mark(other);
        h.runAfterDelay(21,()->{h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"two locks");UUID firstUuid=ThunderNet.serverNodes(f.p).getFirst().uuid();Zombie a=firstUuid.equals(first.getUUID())?first:other,b=a==first?other:first;BigDecimal ahp=hp(a),bhp=hp(b);double cp=f.cp();Object hook=new PostAction(a,()->ThunderNet.abort(f.p));f.hook(hook);
            h.assertTrue(ThunderNet.release(f.p,f.wire(1)),"paidattemptreturns true afterrealpartial");h.assertTrue(hp(a).compareTo(ahp)<0,"firstnativehitcommitted");exact(h,hp(b),bhp,"callbackabort stopssecond");near(h,f.cp(),cp-10,"neverrollback realpartial");h.assertFalse(ThunderNet.active(f.p),"cleancontroller");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=50)
    public static void thunder_preset_change_category_and_cancel_replay_cleanup(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,6);f.mark(target);
        h.runAfterDelay(21,()->{h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"pendingbegin");double cp=f.cp();AbilityStorage.get(f.p).presets.edit(0,1,"forked_arc",id->true);ThunderNet.tick(f.p);h.assertFalse(ThunderNet.active(f.p),"anypresetrevision cancels");near(h,f.cp(),cp,"unpaidcancel free");
            h.assertTrue(ThunderNet.abort(f.p,f.wire(3))==false,"pendingcancel fencesfuturebegin");h.assertFalse(ThunderNet.begin(f.p,f.wire(3)),"cancelbeforestart cannotrevive");h.assertTrue(ThunderNet.begin(f.p,f.wire(4)),"newerinputaccepted");AbilityStorage.get(f.p).category="meltdowner";ThunderNet.tick(f.p);h.assertFalse(ThunderNet.active(f.p),"categoryswitch removescontroller");h.assertFalse(ConductiveMarkReceipts.has(f.p,target),"categoryswitch removesreceipt authority");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=55)
    public static void thunder_paid_hold_cancel_stamps_persistent_reentry_fence(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,6);f.mark(target);
        h.runAfterDelay(21,()->{h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"holdbegin");h.runAfterDelay(8,()->{ThunderNet.tick(f.p);h.assertTrue(ThunderNet.pulseCount(f.p)==1,"paidhold");double cp=f.cp();ThunderNet.remove(f.p);near(h,f.cp(),cp,"cancelnorefund");h.assertTrue(target.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThunderNetRestraint.MODIFIER_ID)==null,"cancelremovesrestraint");
            long fence=f.p.getPersistentData().getCompound("academy:electromaster_prototype").getLong("thunder_net_recast_until");h.assertTrue(fence>=h.getLevel().getGameTime()+40,"configuredcooldownstartsatpaidholdend");
            // Simulated ordinary state/session renewal cannot use the one-tick continuation debit as a recast window.
            AbilityStorage.get(f.p).cooldowns.remove(ThunderNet.ID);f.p.getPersistentData().getCompound("academy:electromaster_prototype").remove("next_"+ThunderNet.ID);LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();h.assertFalse(ThunderNet.begin(f.p,f.wire(2)),"persistent recast fence survives new epoch even if classic timer absent");h.succeed();});});
    }


    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void thunder_player_lock_refuses_hold_before_any_payment(GameTestHelper h){
        Fixture f=new Fixture(h),other=new Fixture(h);f.enablePvp();other.p.setPos(f.p.position().add(0,0,6));other.p.connection.resetPosition();BlockPos water=other.p.blockPosition();h.getLevel().setBlockAndUpdate(water,Blocks.WATER.defaultBlockState());h.getLevel().setBlockAndUpdate(water.above(),Blocks.WATER.defaultBlockState());
        h.runAfterDelay(2,()->{other.p.doTick();h.assertTrue(other.p.isInWaterOrRain(),"actualnative wetplayer");h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"tap-eligibleplayerlock");double cp=f.cp();h.runAfterDelay(8,()->{ThunderNet.tick(f.p);h.assertFalse(ThunderNet.active(f.p),"playerhold explicitlyrefused");near(h,f.cp(),cp,"zero playerhold charge");h.assertTrue(other.p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThunderNetRestraint.MODIFIER_ID)==null,"no playerrestraint or movement seam");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void thunder_tap_pvp_rechecks_teams_and_server_gate(GameTestHelper h){
        Fixture f=new Fixture(h),other=new Fixture(h);f.enablePvp();other.p.setPos(f.p.position().add(0,0,6));other.p.connection.resetPosition();
        h.runAfterDelay(72,()->{f.mark(other.p);h.runAfterDelay(21,()->{h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"ownedreceipt enables nativePvPtaplock");double cp=f.cp();BigDecimal before=hp(other.p);f.p.server.setPvpAllowed(false);h.assertFalse(ThunderNet.release(f.p,f.wire(1)),"current serverPvP gate recheckedbeforetap");near(h,f.cp(),cp,"gateclosed free");exact(h,hp(other.p),before,"gateclosednodamage");
            f.p.server.setPvpAllowed(true);h.assertTrue(ThunderNet.begin(f.p,f.wire(2)),"freshvalidPvPlock");var scoreboard=h.getLevel().getScoreboard();var team=scoreboard.addPlayerTeam("tn"+UUID.randomUUID().toString().substring(0,10));team.setAllowFriendlyFire(false);scoreboard.addPlayerToTeam(f.p.getScoreboardName(),team);scoreboard.addPlayerToTeam(other.p.getScoreboardName(),team);f.teams.add(team);
            h.assertFalse(ThunderNet.release(f.p,f.wire(2)),"newteam blocksalreadyselectedtap");exact(h,hp(other.p),before,"teamgate preserveshealth");near(h,f.cp(),cp,"teamgate nocharge");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=50)
    public static void thunder_owner_death_and_target_death_clean_paid_restraint(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie first=f.target(0,6),second=f.target(2,7);f.mark(first);f.mark(second);
        h.runAfterDelay(21,()->{h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"holdwithtwo");h.runAfterDelay(8,()->{ThunderNet.tick(f.p);h.assertTrue(ThunderNet.pulseCount(f.p)==1,"paidheldpulse");first.kill();ThunderNet.tick(f.p);h.assertTrue(first.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThunderNetRestraint.MODIFIER_ID)==null,"targetdeathleasegone");double cp=f.cp();f.p.kill();ThunderNet.tick(f.p);h.assertFalse(ThunderNet.active(f.p),"ownerdeathcontrollergone");h.assertTrue(second.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThunderNetRestraint.MODIFIER_ID)==null,"ownerdeathremainingleasegone");near(h,f.cp(),cp,"deathneverrefunds paidpulse");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=45)
    public static void thunder_range_and_aim_gates_do_not_follow_new_targets(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie near=f.target(0,6),offAxis=f.target(10,1);f.mark(near);f.mark(offAxis);
        h.runAfterDelay(21,()->{f.p.setYRot(180);h.assertFalse(ThunderNet.begin(f.p,f.wire(1)),"behindcasterisnotaimed");f.p.setYRot(0);h.assertTrue(ThunderNet.begin(f.p,f.wire(2)),"forwardprimary");Vec3 old=f.p.position();f.p.setPos(old.add(0,0,-ThunderNetRules.RANGE-1));f.p.connection.resetPosition();ThunderNet.tick(f.p);h.assertFalse(ThunderNet.active(f.p),"casterrangeexitpermanentlyends fixedset");f.p.setPos(old);f.p.connection.resetPosition();h.assertFalse(ThunderNet.release(f.p,f.wire(2)),"returncannotrevivecontext");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void thunder_receipts_bound_each_caster_to_sixty_four_native_targets(GameTestHelper h){
        Fixture f=new Fixture(h);List<Zombie> targets=new ArrayList<>();for(int i=0;i<65;i++){Zombie target=f.target((i%8)*.2,6+(i/8)*.2);targets.add(target);f.mark(target);}
        h.assertFalse(ConductiveMarkReceipts.has(f.p,targets.getFirst()),"oldest acceptedreceipt evictedatcap64");for(int i=1;i<65;i++)h.assertTrue(ConductiveMarkReceipts.has(f.p,targets.get(i)),"boundednewestreceipt retained");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void thunder_tap_player_uses_real_native_pvp_without_restraint(GameTestHelper h){
        Fixture f=new Fixture(h),other=new Fixture(h);f.enablePvp();other.p.setPos(f.p.position().add(0,0,6));other.p.connection.resetPosition();
        h.runAfterDelay(72,()->{f.mark(other.p);h.runAfterDelay(21,()->{BigDecimal before=hp(other.p),damage=ThunderNetRules.damage(HighPowerGrowth.values(f.p).damage(),false);double cp=f.cp();h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"nativePvPtap lock");h.assertTrue(ThunderNet.release(f.p,f.wire(1)),"actualnative PvPtap");exact(h,before.subtract(hp(other.p)),damage,"exactper-player2.5D withnativegates");near(h,f.cp(),cp-10,"onePvPtappayment");h.assertTrue(other.p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThunderNetRestraint.MODIFIER_ID)==null,"tapnever appliesrestraint");h.succeed();});});
    }


    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=45)
    public static void thunder_skill_attack_callback_cannot_move_native_damage_origin(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,6);f.mark(target);
        h.runAfterDelay(21,()->{h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"lockedbeforecallback");BigDecimal before=hp(target);double cp=f.cp();f.hook(new SkillMutation(f.p,()->f.p.setPos(f.p.position().add(.25,0,0))));h.assertTrue(ThunderNet.release(f.p,f.wire(1)),"paidattempt retains debit");exact(h,hp(target),before,"SkillAttack originchange refusesbeforehurt");near(h,f.cp(),cp-10,"native callback cancellation neverrollsbackpayment");h.assertFalse(ThunderNet.active(f.p),"invalidatedcontroller cleaned");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=45)
    public static void thunder_incoming_callback_cannot_move_locked_target_before_ledger_apply(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,6);f.mark(target);
        h.runAfterDelay(21,()->{h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"lockedbeforeincoming");BigDecimal before=hp(target);double cp=f.cp();f.hook(new IncomingMutation(f.p,target,()->target.setPos(target.position().add(0,0,.25))));h.assertTrue(ThunderNet.release(f.p,f.wire(1)),"paidattempt remains irreversible");exact(h,hp(target),before,"incoming targetmovement rejected before exactledgerapply");near(h,f.cp(),cp-10,"incoming callback doesnotrefundpayment");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=45)
    public static void thunder_legitimate_native_lethal_hit_is_not_rolled_back_by_dead_lock(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=EntityType.HUSK.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setPos(f.p.position().add(0,0,6));h.getLevel().addFreshEntity(target);f.entities.add(target);h.assertTrue(HighPowerCombat.enroll(target,1),"LV1 native exactlethal fixture");f.mark(target);
        h.runAfterDelay(21,()->{h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"livelethallock");h.assertTrue(ThunderNet.release(f.p,f.wire(1)),"actualacceptedlethal tap");h.assertFalse(target.isAlive(),"realnative death preserved");exact(h,hp(target),BigDecimal.ZERO,"no Postguard restoresdeadtarget");h.assertTrue(AbilityStorage.get(f.p).exp(ThunderNet.ID)>0,"productivekill awards once");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void thunder_native_absorption_then_grounding_shield_exactly_once(GameTestHelper h){
        Fixture f=new Fixture(h),other=new Fixture(h);f.enablePvp();other.p.setPos(f.p.position().add(0,0,6));other.p.connection.resetPosition();
        h.runAfterDelay(72,()->{var state=AbilityStorage.get(other.p);state.learn(GroundingReturn.ID);state.presets.edit(0,0,GroundingReturn.ID,id->true);other.p.setShiftKeyDown(true);other.p.setOnGround(true);other.p.setDeltaMovement(Vec3.ZERO);h.assertTrue(GroundingReturn.start(other.p,other.wire(1)),"real nativegroundeddefense");f.mark(other.p);
            h.runAfterDelay(21,()->{h.assertTrue(GroundingReturn.active(other.p),"captureddefense stillactive");other.p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20);other.p.setAbsorptionAmount(10);BigDecimal health=hp(other.p),shield=HighPowerCombat.shield(other.p),damage=ThunderNetRules.damage(HighPowerGrowth.values(f.p).damage(),false);
                h.assertTrue(ThunderNet.begin(f.p,f.wire(1)),"groundingtarget remainsreviewed");h.assertTrue(ThunderNet.release(f.p,f.wire(1)),"electricalnativehitaccepted");exact(h,hp(other.p),health,"capturedgroundingretains realhealth aftertransientnativezero");near(h,other.p.getAbsorptionAmount(),0,"nativeabsorption consumedonce");exact(h,shield.subtract(HighPowerCombat.shield(other.p)),damage.subtract(BigDecimal.TEN),"sharedshieldonly absorbs exactpostnativeabsorption remainder once");h.succeed();});});
    }

    public static final class SkillMutation {
        final ServerPlayer owner;final Runnable action;boolean done;
        SkillMutation(ServerPlayer owner,Runnable action){this.owner=owner;this.action=action;}
        @SubscribeEvent public void attack(SkillAttackEvent e){if(e.player==owner&&e.skill.equals("electromaster.thunder_net")&&!done){done=true;action.run();}}
    }
    public static final class IncomingMutation {
        final ServerPlayer owner;final LivingEntity target;final Runnable action;boolean done;
        IncomingMutation(ServerPlayer owner,LivingEntity target,Runnable action){this.owner=owner;this.target=target;this.action=action;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target&&e.getSource().getEntity()==owner&&!done){done=true;action.run();}}
    }

    public static final class PostAction {
        final LivingEntity target;final Runnable action;boolean done;
        PostAction(LivingEntity target,Runnable action){this.target=target;this.action=action;}
        @SubscribeEvent public void post(LivingDamageEvent.Post e){if(e.getEntity()==target&&!done){done=true;action.run();}}
    }

    public static final class Observe {
        final ServerPlayer owner;final List<Long> times=new ArrayList<>();boolean playerAttackOrigin=true;
        Observe(ServerPlayer owner){this.owner=owner;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void incoming(LivingIncomingDamageEvent e){if(e.getSource().getEntity()==owner&&e.getSource().getMsgId().equals("player")){times.add(owner.server.overworld().getGameTime());playerAttackOrigin&=e.getSource().is(DamageTypes.PLAYER_ATTACK)&&owner.position().equals(e.getSource().getSourcePosition());}}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final List<net.minecraft.world.scores.PlayerTeam> teams=new ArrayList<>();boolean pvpEnabled,closed;
        Fixture(GameTestHelper h){
            this.h=h;for(int x=2;x<=18;x++)for(int z=2;z<=31;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPT"+UUID.randomUUID().toString().substring(0,10)),false);p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;
            Vec3 at=h.absoluteVec(new Vec3(8.5,2,5.5));p.moveTo(at.x,at.y,at.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();h.assertTrue(HighPowerCombat.enroll(p,4),"realconnectedLV4caster");var state=AbilityStorage.get(p);state.selectCategory("electromaster");state.setLevel(4);state.learn("forked_arc");state.learn("conductive_circuit");state.learn(ThunderNet.ID);state.activated=true;state.overloadFine=true;h.assertTrue(state.presets.edit(0,0,ThunderNet.ID,id->id.equals(ThunderNet.ID)),"learnedboundslot");ElectromasterPrototype.initializeBudget(p);epoch();
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Zombie target(double x,double z){Zombie e=EntityType.HUSK.create(h.getLevel());e.setNoAi(true);e.setNoGravity(true);e.setSilent(true);e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);e.setPos(p.position().add(x,0,z));h.getLevel().addFreshEntity(e);h.assertTrue(HighPowerCombat.enroll(e,5),"exactnativesupportedtarget");entities.add(e);return e;}
        void mark(LivingEntity target){var capture=ConductiveMarkReceipts.capture(p,target);h.assertTrue(capture!=null,"nativeArc capture");h.assertTrue(HighPowerCombat.attack(p,ConductiveMarkReceipts.SOURCE,target,BigDecimal.ONE),"actualacceptednativeArc");h.assertTrue(ConductiveMarkReceipts.acceptedArc(capture),"postcallbackissuedreceipt");}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        double cp(){return ElectromasterPrototype.prototypeCp(p);}
        void enablePvp(){if(pvpEnabled)return;pvpEnabled=true;if(pvpFixtures++==0)originalPvp=p.server.isPvpAllowed();p.server.setPvpAllowed(true);}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);if(pvpEnabled&&--pvpFixtures==0)p.server.setPvpAllowed(originalPvp);ThunderNet.remove(p);ConductiveMarkReceipts.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}
