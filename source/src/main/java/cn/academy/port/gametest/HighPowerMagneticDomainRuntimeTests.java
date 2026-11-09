package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW152 native authenticated ownership, closed payment, bounded actual arrows and persistent fences. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerMagneticDomainRuntimeTests {
    static final String TEMPLATE="highpower_empty",BATCH="highpower_magnetic_domain",CP_KEY="academy:electromaster_prototype";
    private HighPowerMagneticDomainRuntimeTests() {}
    static void near(GameTestHelper h,double a,double b,String why){h.assertTrue(Math.abs(a-b)<1e-7,why+": "+a+" != "+b);}
    static void eq(GameTestHelper h,BigDecimal a,BigDecimal b,String why){h.assertTrue(a.compareTo(b)==0,why+": "+a+" != "+b);}
    private static double turn(Arrow a){return a.getPersistentData().getCompound(MagneticDomainArrows.LEASE_KEY).getDouble("turn");}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_zero_cp_replay_pending_abort_and_closed_first_payment(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertFalse(MagneticDomain.abort(f.p,f.wire(1)),"pending abort fences delayed activation");
            h.assertFalse(f.toggle(1),"delayed tap cannot activate");f.initialCp(0);
            CompoundTag budget=f.p.getPersistentData().getCompound(CP_KEY).copy();
            h.assertFalse(f.toggle(2),"zeroCP cannot start domain");h.assertFalse(MagneticDomain.active(f.p),"no zeroCP benefits");
            h.assertFalse(f.p.getPersistentData().contains(MagneticDomain.PAYMENT_KEY),"unpaid activation creates no paid window");
            h.assertTrue(f.p.getPersistentData().getCompound(CP_KEY).equals(budget),"failed payment never mutates budget or deadlines");
            f.initialCp(10);double xp=AbilityStorage.get(f.p).exp(MagneticDomain.ID);
            h.assertTrue(f.toggle(3),"fresh authenticated paid activation");near(h,f.cp(),9,"first20ticks cost exactly1CP");
            h.assertFalse(f.toggle(3),"replayed toggle cannot turn off");h.assertFalse(MagneticDomain.abort(f.p,f.wire(2)),"old cancel cannot end new input");
            MagneticDomain.tick(f.p);MagneticDomain.tick(f.p);near(h,f.cp(),9,"duplicate same-tick tick cannot charge twice");
            near(h,AbilityStorage.get(f.p).exp(MagneticDomain.ID),xp,"payment and idle field create no mastery");
            h.assertTrue(f.toggle(4),"fresh tap turns off");h.assertFalse(MagneticDomain.active(f.p),"off revokes shield immediately");
            h.assertFalse(f.toggle(5),"paid window cannot be borrowed by new activation");near(h,f.cp(),9,"blocked retoggle cannot charge again");
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(MagneticDomain.ID,0)==4,"exit applies4native cooldown ticks");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=120)
    public static void domain_exact_twenty_tick_cost_and_real_lv5_cp_recovery(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(ElectromasterPrototype.trySpend(f.p,HighPowerSkills.FORKED_ARC,5000,1),"declared real shared payment opens CP headroom");
            h.assertTrue(f.toggle(1),"paid activation");long until=MagneticDomain.paidUntil(f.p);double xp=AbilityStorage.get(f.p).exp(MagneticDomain.ID);
            PaymentWitness witness=new PaymentWitness(f.p);f.hook(witness);RecoveryWitness recovery=new RecoveryWitness(f.p);f.hook(recovery);
            // Mock connections have no real client network pump. Invoke the genuine player tick on
            // each later server tick; never edit balances or recovery/deadline stamps to manufacture credit.
            f.hook(new NativePlayerPump(f.p,41));
            h.runAfterDelay(41,()->{
                h.assertTrue(witness.charges>=2,"observed at least two actual20tick payments");
                h.assertTrue(witness.allExact,"every observed native domain payment debits exactly1CP and duplicate tick is inert");
                h.assertTrue(witness.spacingsExact&&MagneticDomain.paidUntil(f.p)>=until+40,"native paid windows retain20tick spacing");
                h.assertTrue(recovery.samples>0&&recovery.exact,"actual LV5 player regeneration credits1250CP/tick while below cap");
                near(h,HighPowerGrowth.values(f.p).cpRecovery(),25000,"native growth helper reports25000CP/s");
                near(h,AbilityStorage.get(f.p).exp(MagneticDomain.ID),xp,"idle maintenance never farms mastery");h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void domain_native_save_reload_cannot_duplicate_charge_or_reuse_paid_window(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(f.toggle(1),"first activation");long until=MagneticDomain.paidUntil(f.p);MagneticDomain.remove(f.p);
            AbilityStorage.save(f.p);CompoundTag saved=f.p.saveWithoutId(new CompoundTag());AbilityStorage.remove(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);
            f.p.load(saved);f.place();f.epoch();double cp=f.cp();
            h.assertFalse(MagneticDomain.active(f.p),"NBT does not restore a live activation");h.assertFalse(f.toggle(1),"new login cannot reuse old paid interval");near(h,f.cp(),cp,"relogin cannot duplicate debit");
            h.runAfterDelay(Math.max(1,until-f.p.server.overworld().getGameTime()),()->{
                f.p.doTick();f.place();double before=f.cp();h.assertTrue(f.toggle(2),"window expiry permits fresh deliberate activation");
                near(h,f.cp(),before-1,"fresh post-relog interval must pay1CP");h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_preset_category_death_and_epoch_invalidation_revoke_same_tick(GameTestHelper h){
        Fixture preset=new Fixture(h),category=new Fixture(h),death=new Fixture(h),epoch=new Fixture(h);
        preset.ready(()->{
            for(Fixture f:List.of(preset,category,death,epoch)){f.p.doTick();f.place();h.assertTrue(f.toggle(1),"each distinct owner activates");}
            AbilityStorage.get(preset.p).presets.edit(0,0,HighPowerSkills.FORKED_ARC,id->id.equals(HighPowerSkills.FORKED_ARC));
            AbilityStorage.get(category.p).selectCategory("meltdowner");
            MagneticDomain.death(new LivingDeathEvent(death.p,death.p.damageSources().generic()));
            LegacySingleKeyOwnerEpoch.forget(epoch.p);
            for(Fixture f:List.of(preset,category,death,epoch))h.assertFalse(MagneticDomain.active(f.p),"same-tick changed ownership cannot retain benefits");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=120)
    public static void domain_real_dimension_change_keeps_global_fence_without_stranding_new_world(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            h.assertTrue(f.toggle(1),"overworld paid");long until=MagneticDomain.paidUntil(f.p);String old=f.wire(1);
            var other=f.p.server.getLevel(Level.NETHER);h.assertTrue(other!=null,"native Nether available");other.getChunk(f.p.blockPosition());
            f.p.teleportTo(other,f.p.getX(),f.p.getY(),f.p.getZ(),Set.of(),0,0);
            h.assertTrue(f.p.serverLevel()==other,"actual native dimension transition");h.assertFalse(MagneticDomain.active(f.p),"captured world loses effects");
            h.assertFalse(MagneticDomain.toggle(f.p,old),"old world identity cannot be replayed");f.epoch();h.assertFalse(f.toggle(2),"global paid fence survives dimension change");
            // A declared stale LOCAL helper deadline models a destination clock behind the prior world.
            // The domain can translate only its own deadline after this real dimension identity change.
            var budget=f.p.getPersistentData().getCompound(CP_KEY);budget.putLong("next_"+MagneticDomain.ID,other.getGameTime()+10000);f.p.getPersistentData().put(CP_KEY,budget);
            long remaining=Math.max(5,until-f.p.server.overworld().getGameTime());
            f.hook(new NativePlayerPump(f.p,(int)remaining));
            h.runAfterDelay(remaining,()->{
                f.epoch();h.assertTrue(AbilityStorage.get(f.p).canUse(MagneticDomain.ID),"genuine native ticks expire post-stop cooldown");double before=f.cp();h.assertTrue(f.toggle(3),"verified dimension change translates only stale owned local clock after global expiry");
                near(h,f.cp(),before-1,"new dimension window pays exactly once");h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_native_arrow_first_turn_debits_one_percent_preserves_metadata_and_shared_lease(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            Arrow arrow=f.arrow(0,8,new Vec3(0,.12,-.2));arrow.setBaseDamage(3.5);arrow.pickup=AbstractArrow.Pickup.CREATIVE_ONLY;arrow.getPersistentData().putString("fixture_metadata","keep");
            Vec3 before=arrow.getDeltaMovement(),position=arrow.position();CompoundTag saved=arrow.saveWithoutId(new CompoundTag());CompoundTag persistent=arrow.getPersistentData().copy();
            h.assertTrue(f.toggle(1),"paid domain");BigDecimal pool=MagneticDomainReserve.total(f.p),fee=MagneticDomainReserve.capacity(f.p).movePointLeft(2);double cp=f.cp();MagneticDomain.tick(f.p);
            h.assertTrue(MagneticDomainArrows.leased(arrow),"actual first turn acquires shared lease");eq(h,MagneticDomainReserve.total(f.p),pool.subtract(fee),"one1%baseS fee");
            near(h,f.cp(),cp,"arrow does not add CP cost");near(h,turn(arrow),.1,"first0.1rad turn");near(h,arrow.getDeltaMovement().y,before.y,"vertical speed bit-preserved");
            near(h,arrow.getDeltaMovement().horizontalDistance(),before.horizontalDistance(),"horizontal magnitude conserved");h.assertTrue(arrow.position().equals(position),"no teleport");
            CompoundTag after=arrow.saveWithoutId(new CompoundTag());saved.remove("Motion");after.remove("Motion");saved.remove("NeoForgeData");after.remove("NeoForgeData");h.assertTrue(saved.equals(after),"all native nonmotion metadata conserved");
            var own=arrow.getPersistentData().copy();own.remove(MagneticDomainArrows.LEASE_KEY);h.assertTrue(own.equals(persistent),"only owned lease metadata added");
            h.assertTrue(arrow.getOwner()==f.skeleton&&!arrow.isNoPhysics()&&!arrow.isNoGravity(),"native owner gravity collision intact");
            Vec3 changed=arrow.getDeltaMovement();MagneticDomain.tick(f.p);h.assertTrue(changed.equals(arrow.getDeltaMovement()),"same-tick duplicate cannot write");
            eq(h,MagneticDomainReserve.total(f.p),pool.subtract(fee),"same-tick duplicate cannot debit");
            MagneticDomain.abort(f.p);h.assertTrue(MagneticDomainArrows.leased(arrow),"residual lease prevents another skill stealing write");h.assertTrue(changed.equals(arrow.getDeltaMovement()),"cancel never resets velocity");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_arrow_admission_filters_four_quota_and_unaffordable_turns_are_inert(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            List<Arrow> valid=new ArrayList<>();for(int i=0;i<5;i++)valid.add(f.arrow((i-2)*.15,7,new Vec3(0,.2,-.1)));
            Arrow rear=f.arrow(0,-4,new Vec3(0,0,.2)),side=f.arrow(6,2,new Vec3(-.2,0,0)),outside=f.arrow(0,MagneticDomain.RADIUS+.01,new Vec3(0,0,-.2));
            Arrow potion=f.arrow(0,6,new Vec3(0,0,-.2));potion.addEffect(new MobEffectInstance(MobEffects.POISON,80));
            Arrow ownerless=new Arrow(EntityType.ARROW,h.getLevel());ownerless.setPos(f.p.position().add(.1,1,6));ownerless.setDeltaMovement(new Vec3(0,0,-.2));h.getLevel().addFreshEntity(ownerless);f.entities.add(ownerless);
            h.assertTrue(ownerless.getOwner()==null,"actual ownerless constructor has no cached UUID");
            h.assertFalse(potion.getPickupItemStackOrigin().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,net.minecraft.world.item.alchemy.PotionContents.EMPTY).equals(net.minecraft.world.item.alchemy.PotionContents.EMPTY),"native-added potion exists in reviewed origin component");
            Arrow critical=f.arrow(.2,6,new Vec3(0,0,-.2));critical.setCritArrow(true);
            Arrow foreign=f.arrow(.3,6,new Vec3(0,0,-.2));foreign.getPersistentData().putString(MagneticArrowSeparation.LEASE_KEY,"malformed-fails-closed");
            h.assertTrue(f.toggle(1),"paid domain");BigDecimal before=MagneticDomainReserve.total(f.p),fee=MagneticDomainReserve.capacity(f.p).movePointLeft(2);MagneticDomain.tick(f.p);
            h.assertTrue(valid.stream().filter(MagneticDomainArrows::leased).count()==4&&MagneticDomain.refractedCount(f.p)==4,"four unique admissions only");
            for(Arrow a:List.of(rear,side,outside,potion,ownerless,critical,foreign))h.assertFalse(a.getPersistentData().contains(MagneticDomainArrows.LEASE_KEY),"unsupported or foreign-leased native arrow untouched");
            eq(h,MagneticDomainReserve.total(f.p),before.subtract(fee.multiply(BigDecimal.valueOf(4))),"only four admitted arrows pay");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_unaffordable_and_wall_hidden_arrows_never_pay_or_receive_a_lease(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            Arrow a=f.arrow(0,8,new Vec3(0,0,-.2));Vec3 before=a.getDeltaMovement();
            h.assertTrue(HighPowerCombat.spendShield(f.p,HighPowerCombat.shield(f.p)),"declared fixture genuinely spends base shield");
            h.assertTrue(f.toggle(1),"domain may run with empty base shield");
            // ready() executes a genuine player tick; its reserve credit must also be genuinely spent.
            int spent=0;while(MagneticDomainReserve.total(f.p).signum()>0&&spent++<100)h.assertTrue(MagneticDomainReserve.spendRefraction(f.p),"declared fixture spends any real reserve credit through the closed domain helper");
            eq(h,MagneticDomainReserve.total(f.p),BigDecimal.ZERO,"both tranches proven empty before arrow attempt");MagneticDomain.tick(f.p);
            h.assertTrue(a.getDeltaMovement().equals(before)&&!a.getPersistentData().contains(MagneticDomainArrows.LEASE_KEY),"unaffordable turn is wholly inert");
            eq(h,MagneticDomainReserve.total(f.p),BigDecimal.ZERO,"no negative shield or reserve");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=120)
    public static void domain_episode_45degree_cap_no_second_fee_and_serialized_arrow_never_readmitted(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            Arrow a=f.arrow(0,10,new Vec3(0,.3,-.08));h.assertTrue(f.toggle(1),"domain active");BigDecimal before=MagneticDomainReserve.total(f.p),fee=MagneticDomainReserve.capacity(f.p).movePointLeft(2);MagneticDomain.tick(f.p);
            h.runAfterDelay(9,()->{h.assertTrue(turn(a)<=Math.PI/4+1e-10&&turn(a)>.1,"native curvature stays below45degrees total");eq(h,MagneticDomainReserve.total(f.p),before.subtract(fee),"later curvature has no second fee");});
            h.runAfterDelay(22,()->{
                CompoundTag saved=a.saveWithoutId(new CompoundTag());a.discard();Arrow restored=new Arrow(EntityType.ARROW,h.getLevel());restored.load(saved);restored.setPos(f.p.position().add(0,1,8));restored.setDeltaMovement(new Vec3(0,0,-.2));h.getLevel().addFreshEntity(restored);f.entities.add(restored);
                h.assertFalse(MagneticDomainArrows.leased(restored),"global20tick write lease expired");double angle=turn(restored);BigDecimal pool=MagneticDomainReserve.total(f.p);Vec3 speed=restored.getDeltaMovement();MagneticDomain.tick(f.p);
                h.assertTrue(speed.equals(restored.getDeltaMovement()),"persisted one-use domain admission rejects reacquisition");near(h,turn(restored),angle,"save/reload cannot reset45degree budget");eq(h,MagneticDomainReserve.total(f.p),pool,"reloaded arrow cannot pay again");h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void domain_new_wall_and_current_owner_revocation_stop_existing_native_curvature(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            Arrow a=f.arrow(0,8,new Vec3(0,.2,-.1));h.assertTrue(f.toggle(1),"active domain");MagneticDomain.tick(f.p);double angle=turn(a);
            BlockPos wall=BlockPos.containing(f.p.position().add(0,1,4));h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
            f.next(()->{near(h,turn(a),angle,"new loaded wall immediately stops curvature");h.getLevel().setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());
                Arrow changed=f.arrow(.2,7,new Vec3(0,.2,-.1));f.next(()->{h.assertTrue(MagneticDomainArrows.leased(changed),"second reviewed arrow admitted");double previous=turn(changed);changed.setOwner(f.p);
                    f.next(()->{near(h,turn(changed),previous,"current owner changed to caster prevents subsequent write");h.succeed();});});});
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void domain_reciprocal_native_writers_refuse_an_owned_arrow(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{
            Arrow a=f.arrow(0,2,new Vec3(0,0,-.1));
            try {
                var bracer=ElectromasterPrototype.class.getDeclaredMethod("eligibleArrow",AbstractArrow.class,ServerPlayer.class,Vec3.class,Vec3.class);bracer.setAccessible(true);
                var decoy=ElectromasterChargeDecoy.class.getDeclaredMethod("cheapEligible",ServerPlayer.class,Vec3.class,AbstractArrow.class);decoy.setAccessible(true);
                var ground=MagneticGroundStep.class.getDeclaredMethod("cheapEligible",ServerPlayer.class,AbstractArrow.class);ground.setAccessible(true);
                var lance=IronSandLance.class.getDeclaredMethod("arrowEligible",ServerPlayer.class,Arrow.class);lance.setAccessible(true);
                var separation=MagneticArrowSeparation.class.getDeclaredMethod("otherLease",AbstractArrow.class);separation.setAccessible(true);
                h.assertTrue((Boolean)bracer.invoke(null,a,f.p,f.p.getEyePosition(),f.p.getLookAngle()),"otherwise-eligible native bracer candidate");
                h.assertFalse((Boolean)separation.invoke(null,a),"candidate starts without any other writer lease");
                h.assertTrue(f.toggle(1),"domain activated");MagneticDomain.tick(f.p);h.assertTrue(MagneticDomainArrows.leased(a),"domain owns actual positive turn");
                h.assertFalse((Boolean)bracer.invoke(null,a,f.p,f.p.getEyePosition(),f.p.getLookAngle()),"bracer refuses domain lease");
                h.assertFalse((Boolean)decoy.invoke(null,f.p,a.position().add(0,0,-1),a),"decoy refuses domain lease");
                h.assertFalse((Boolean)ground.invoke(null,f.p,a),"groundstep refuses domain lease");
                h.assertFalse((Boolean)lance.invoke(null,f.p,a),"lance refuses domain lease");
                h.assertTrue((Boolean)separation.invoke(null,a),"arrow separation recognizes domain lease");h.succeed();
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void domain_current_team_and_native_pvp_permissions_revalidated_each_tick(GameTestHelper h){
        Fixture f=new Fixture(h),other=new Fixture(h);f.ready(()->{
            other.p.doTick();other.p.setPos(f.p.position().add(0,0,15));other.p.connection.resetPosition();
            Arrow playerArrow=f.arrow(.3,8,new Vec3(0,.2,-.1));playerArrow.setOwner(other.p);
            Arrow monsterArrow=f.arrow(-.3,8,new Vec3(0,.2,-.1));h.assertTrue(f.toggle(1),"active domain");
            boolean previous=f.p.server.isPvpAllowed();f.p.server.setPvpAllowed(true);
            try{MagneticDomain.tick(f.p);h.assertTrue(MagneticDomainArrows.leased(playerArrow)&&MagneticDomainArrows.leased(monsterArrow),"reviewed player and monster admitted with native permissions");}
            finally{f.p.server.setPvpAllowed(previous);}
            double playerTurn=turn(playerArrow),monsterTurn=turn(monsterArrow);
            var board=h.getLevel().getScoreboard();var team=board.addPlayerTeam("d"+UUID.randomUUID().toString().substring(0,10));team.setAllowFriendlyFire(false);
            board.addPlayerToTeam(f.p.getScoreboardName(),team);board.addPlayerToTeam(f.skeleton.getScoreboardName(),team);
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){board.removePlayerTeam(team);}public void testFailed(GameTestInfo i,GameTestRunner r){board.removePlayerTeam(team);}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){board.removePlayerTeam(team);}});
            f.next(()->{h.assertFalse(f.p.server.isPvpAllowed(),"native GameTestPvP restored off");near(h,turn(playerArrow),playerTurn,"PvP revocation prevents continued turn");near(h,turn(monsterArrow),monsterTurn,"new team alliance prevents continued turn");h.succeed();});
        });
    }

    /** One bounded native tick pump avoids rehashing GameTestInfo's executing schedule map. */
    public static final class NativePlayerPump {
        final ServerPlayer player;final int started,until;int last;
        public NativePlayerPump(ServerPlayer player,int duration){this.player=player;started=player.server.getTickCount();until=started+duration;last=started;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void tick(ServerTickEvent.Post event){
            if(event.getServer()!=player.server)return;int tick=player.server.getTickCount();
            if(tick<=last||tick>until||player.isRemoved()||player.hasDisconnected())return;
            last=tick;player.doTick();
        }
    }

    public static final class PaymentWitness {
        final ServerPlayer p;long until,tick,lastCharge;double cp;int charges;boolean allExact=true,spacingsExact=true;
        PaymentWitness(ServerPlayer p){this.p=p;until=MagneticDomain.paidUntil(p);lastCharge=p.server.getTickCount();}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(ServerTickEvent.Pre e){if(e.getServer()!=p.server)return;until=MagneticDomain.paidUntil(p);cp=ElectromasterPrototype.prototypeCp(p);tick=p.server.overworld().getGameTime();}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void after(ServerTickEvent.Pre e){if(e.getServer()!=p.server)return;long next=MagneticDomain.paidUntil(p);if(next>until){charges++;allExact&=ElectromasterPrototype.prototypeCp(p)==cp-1;spacingsExact&=next-until==20&&p.server.getTickCount()-lastCharge==20;lastCharge=p.server.getTickCount();double paid=ElectromasterPrototype.prototypeCp(p);MagneticDomain.tick(p);allExact&=ElectromasterPrototype.prototypeCp(p)==paid;}}
    }
    public static final class RecoveryWitness {
        final ServerPlayer p;double before;long last;int samples;boolean exact=true;
        RecoveryWitness(ServerPlayer p){this.p=p;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(PlayerTickEvent.Post e){if(e.getEntity()!=p)return;before=ElectromasterPrototype.prototypeCp(p);last=p.getPersistentData().getCompound(CP_KEY).getLong("last_tick");}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void after(PlayerTickEvent.Post e){if(e.getEntity()!=p||last==p.serverLevel().getGameTime()||before>=98750)return;samples++;exact&=ElectromasterPrototype.prototypeCp(p)==before+1250;}
    }

    /** Initial learned bits and optional CP balance are declared fixtures, never survival-acquisition claims. */
    static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final Skeleton skeleton;
        final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){
            this.h=h;for(int x=2;x<=14;x++)for(int z=2;z<=25;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HMD"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;place();h.assertTrue(HighPowerCombat.enroll(p,5),"genuine connected LV5 fixture");
            var s=AbilityStorage.get(p);s.selectCategory("electromaster");s.setLevel(5);for(String id:List.of(HighPowerSkills.FORKED_ARC,HighPowerSkills.MAGNETIC_BRACER,GroundingReturn.ID,PotentialBuffer.ID,MagneticDomain.ID))s.learn(id);
            s.activated=true;s.overloadFine=true;h.assertTrue(s.presets.edit(0,0,MagneticDomain.ID,id->id.equals(MagneticDomain.ID)),"learned domain assigned to real slot");ElectromasterPrototype.initializeBudget(p);epoch();
            skeleton=EntityType.SKELETON.create(h.getLevel());skeleton.setNoAi(true);skeleton.setNoGravity(true);skeleton.setSilent(true);skeleton.setPos(p.position().add(0,0,15));h.getLevel().addFreshEntity(skeleton);entities.add(skeleton);
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        void ready(Runnable action){h.runAfterDelay(61,()->{p.doTick();place();skeleton.setPos(p.position().add(0,0,15));action.run();});}
        void next(Runnable action){h.runAfterDelay(1,()->{p.doTick();place();action.run();});}
        void place(){Vec3 at=h.absoluteVec(new Vec3(7.5,2,5.5));p.moveTo(at.x,at.y,at.z,0,0);p.setYHeadRot(0);p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();}
        Arrow arrow(double x,double z,Vec3 velocity){Arrow a=new Arrow(h.getLevel(),skeleton,new ItemStack(Items.ARROW),null);a.setPos(p.position().add(x,1,z));a.setDeltaMovement(velocity);h.getLevel().addFreshEntity(a);entities.add(a);return a;}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        boolean toggle(long input){return MagneticDomain.toggle(p,wire(input));}
        double cp(){return ElectromasterPrototype.prototypeCp(p);}
        void initialCp(double value){var t=p.getPersistentData().getCompound(CP_KEY);t.putDouble("cp",value);p.getPersistentData().put(CP_KEY,t);}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);MagneticDomain.remove(p);AbilityStorage.remove(p);p.server.getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}
