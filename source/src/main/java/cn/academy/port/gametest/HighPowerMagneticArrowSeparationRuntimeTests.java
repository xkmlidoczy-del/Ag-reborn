package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.MagneticArrowSeparationRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native identities, Arrow trajectories, real server resources, persisted guards and facade lifecycle. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerMagneticArrowSeparationRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_arrow_separation";
    private HighPowerMagneticArrowSeparationRuntimeTests() {}
    private static void near(GameTestHelper h, double a, double b, String why) { h.assertTrue(Math.abs(a-b)<1e-7, why+": "+a+" != "+b); }
    private static double turn(Arrow arrow) { return arrow.getPersistentData().getCompound(MagneticArrowSeparation.LEASE_KEY).getDouble("turn"); }
    private static CompoundTag tag(Arrow arrow) { return arrow.saveWithoutId(new CompoundTag()); }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void arrow_separation_empty_pulse_is_free_finite_and_one_use(GameTestHelper h) {
        Fixture f=new Fixture(h);
        h.assertFalse(MagneticItemPull.releaseArrows(f.p,f.wire(99)),"release without any preview lease rejected");
        f.arm(1, () -> {
 double cp=f.cp(),exp=AbilityStorage.get(f.p).exp(MagneticItemPull.ID);
        h.assertFalse(MagneticItemPull.releaseArrows(f.p,f.wire(1)),"release replay has no remaining authenticated begin lease");
        
        MagneticArrowSeparation.tick(f.p);
        h.assertTrue(MagneticArrowSeparation.active(f.p)&&!MagneticArrowSeparation.paid(f.p),"empty field is unpaid");
        near(h,f.cp(),cp,"empty arming has no CP cost");
        h.assertFalse(MagneticItemPull.releaseArrows(f.p,f.wire(1)),"release lease consumed exactly once");
        h.assertFalse(MagneticItemPull.beginArrows(f.p,f.wire(1)),"replayed begin cannot resurrect lease");
        h.runAfterDelay(21,()->{h.assertFalse(MagneticArrowSeparation.active(f.p),"original20tick deadline ends empty field");near(h,f.cp(),cp,"empty expiry has no CP cost");near(h,AbilityStorage.get(f.p).exp(MagneticItemPull.ID),exp,"empty field cannot farm experience");h.succeed();});
            });
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void arrow_separation_first_real_turn_one_debit_metadata_and_duplicate_callback(GameTestHelper h) {
        Fixture f=new Fixture(h);
        f.arm(1, () -> {
Arrow a=f.arrow(0,12,new Vec3(0,.12,-.4));a.setBaseDamage(3.5);a.pickup=AbstractArrow.Pickup.CREATIVE_ONLY;a.getPersistentData().putString("fixture_metadata","keep");
        Vec3 before=a.getDeltaMovement(),position=a.position();CompoundTag metadata=tag(a),persistent=a.getPersistentData().copy();double cp=f.cp();
        near(h,f.cp(),cp,"release itself does not debit");MagneticArrowSeparation.tick(f.p);
        h.assertTrue(MagneticArrowSeparation.paid(f.p)&&MagneticArrowSeparation.targetCount(f.p)==1,"first nonzero native turn authorizes payment");near(h,f.cp(),cp-8,"one LV3 CP debit");
        Vec3 changed=a.getDeltaMovement();near(h,changed.y,before.y,"actualY unchanged");near(h,changed.horizontalDistance(),before.horizontalDistance(),"actualXZspeed unchanged");h.assertTrue(Math.abs(changed.x)>.001&&a.position().equals(position),"velocity only, no movement teleport");
        near(h,turn(a),.1,"first bounded .10rad turn");MagneticArrowSeparation.tick(f.p);h.assertTrue(a.getDeltaMovement().equals(changed),"duplicate same-tick callback cannot steer twice");near(h,f.cp(),cp-8,"duplicate callback cannot debit");
        CompoundTag after=tag(a);metadata.remove("Motion");after.remove("Motion");metadata.remove("NeoForgeData");after.remove("NeoForgeData");h.assertTrue(metadata.equals(after),"all serialized native metadata except Motion remains exact");
        var own=a.getPersistentData().copy();own.remove(MagneticArrowSeparation.LEASE_KEY);h.assertTrue(own.equals(persistent),"only this controller's own lease metadata added");
        h.assertTrue(a.getOwner()==f.skeleton&&a.pickup==AbstractArrow.Pickup.CREATIVE_ONLY&&!a.isNoGravity()&&!a.isNoPhysics(),"native owner pickup gravity collision preserved");
        MagneticItemPull.abort(f.p);h.assertFalse(MagneticArrowSeparation.active(f.p),"facade abort owns second-mode context");h.assertTrue(a.getDeltaMovement().equals(changed)&&MagneticArrowSeparation.leased(a),"abort keeps motion and residual shared lease");h.succeed();
            });
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void arrow_separation_paired_native_control_gravity_and_horizontal_curvature(GameTestHelper h) {
        Fixture f=new Fixture(h);
        f.arm(1, () -> {
Arrow curved=f.arrow(0,12,new Vec3(0,.2,-.18)),control=f.arrow(7,12,new Vec3(0,.2,-.18));Vec3 initial=curved.position();
        MagneticArrowSeparation.tick(f.p);
        h.runAfterDelay(8,()->{near(h,curved.getY(),control.getY(),"paired actual native Y trajectory is identical");near(h,curved.getDeltaMovement().y,control.getDeltaMovement().y,"paired gravity/drag Y identical");near(h,curved.getDeltaMovement().horizontalDistance(),control.getDeltaMovement().horizontalDistance(),"paired native horizontal drag magnitude identical");h.assertTrue(Math.abs(curved.getX()-initial.x)>.1&&Math.abs(control.getX()-(initial.x+7))<1e-8,"native movement follows actual bent path while control remains straight");h.assertTrue(turn(curved)<=Math.PI/4+1e-10&&!curved.isNoGravity()&&!curved.isNoPhysics(),"turn bound and native physics retained");for(double base:new double[]{2,3.5,10,1000})h.assertTrue(Math.ceil((float)curved.getDeltaMovement().length()*base)<=Math.ceil((float)control.getDeltaMovement().length()*base),"paired native float-speed ceil damage cannot increase");System.out.println("NEW133_ARROW_NATIVE_CURVE="+curved.position().subtract(initial)+" native_control_y="+control.getY()+" accumulated="+turn(curved));h.succeed();});
            });
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void arrow_separation_ownerless_self_passive_custom_spectral_and_potion_are_refused(GameTestHelper h) {
        Fixture f=new Fixture(h);
        f.arm(1, () -> {
List<AbstractArrow> rejected=new ArrayList<>();
        Arrow ownerless=new Arrow(EntityType.ARROW,h.getLevel());f.place(ownerless,0,10,new Vec3(0,0,-.4));h.assertTrue(ownerless.getOwner()==null,"owner-free native constructor has no cached owner or ownerUUID");rejected.add(ownerless);
        Arrow self=f.arrow(.1,10,new Vec3(0,0,-.4));self.setOwner(f.p);rejected.add(self);
        Mob cow=f.mob(EntityType.COW,1,22);Arrow passive=f.arrow(.2,10,new Vec3(0,0,-.4));passive.setOwner(cow);rejected.add(passive);
        Arrow potion=f.arrow(.3,10,new Vec3(0,0,-.4));potion.addEffect(new MobEffectInstance(MobEffects.POISON,80));h.assertTrue(potion.getPickupItemStackOrigin().is(Items.ARROW),"native addEffect can hide potion on ordinary arrow item");rejected.add(potion);
        Arrow custom=new Arrow(EntityType.ARROW,h.getLevel()){};custom.setOwner(f.skeleton);f.place(custom,.4,10,new Vec3(0,0,-.4));rejected.add(custom);
        SpectralArrow spectral=new SpectralArrow(EntityType.SPECTRAL_ARROW,h.getLevel());spectral.setOwner(f.skeleton);f.place(spectral,.5,10,new Vec3(0,0,-.4));rejected.add(spectral);
        Map<AbstractArrow,Vec3> velocities=new HashMap<>();rejected.forEach(a->velocities.put(a,a.getDeltaMovement()));double cp=f.cp();MagneticArrowSeparation.tick(f.p);
        h.assertTrue(!MagneticArrowSeparation.paid(f.p)&&MagneticArrowSeparation.targetCount(f.p)==0,"positive enemy and exact ordinary-potion-free arrow proof required");rejected.forEach(a->h.assertTrue(a.getDeltaMovement().equals(velocities.get(a)),"unsupported native trajectory untouched"));near(h,f.cp(),cp,"unsupported-only field free");h.succeed();
            });
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void arrow_separation_crit_fire_pierce_no_gravity_enchanted_weapon_protection_refused(GameTestHelper h) {
        Fixture f=new Fixture(h);
        f.arm(1, () -> {
List<Arrow> rejected=new ArrayList<>();
        Arrow critical=f.arrow(0,10,new Vec3(0,0,-.4));critical.setCritArrow(true);rejected.add(critical);
        Arrow fire=f.arrow(.1,10,new Vec3(0,0,-.4));fire.setRemainingFireTicks(100);rejected.add(fire);
        Arrow pierce=f.arrow(.2,10,new Vec3(0,0,-.4));CompoundTag piercing=tag(pierce);piercing.putByte("PierceLevel",(byte)1);pierce.load(piercing);rejected.add(pierce);
        Arrow floating=f.arrow(.3,10,new Vec3(0,0,-.4));floating.setNoGravity(true);rejected.add(floating);
        Arrow protectedArrow=f.arrow(.4,10,new Vec3(0,0,-.4));protectedArrow.getPersistentData().putBoolean("academy:protected",true);rejected.add(protectedArrow);
        ItemStack bow=new ItemStack(Items.BOW);bow.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER),1);
        Arrow enchanted=new Arrow(h.getLevel(),f.skeleton,new ItemStack(Items.ARROW),bow);f.place(enchanted,.5,10,new Vec3(0,0,-.4));rejected.add(enchanted);
        Map<Arrow,Vec3> before=new HashMap<>();rejected.forEach(a->before.put(a,a.getDeltaMovement()));MagneticArrowSeparation.tick(f.p);h.assertFalse(MagneticArrowSeparation.paid(f.p),"unsupported metadata does not trigger transaction");rejected.forEach(a->h.assertTrue(a.getDeltaMovement().equals(before.get(a)),"unsupported metadata arrow untouched"));h.succeed();
            });
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void arrow_separation_near_far_radius_and_receding_boundaries(GameTestHelper h) {
        Fixture f=new Fixture(h);
        f.arm(1, () -> {
Arrow near=f.arrow(.1,1.1,new Vec3(0,0,-.1)),far=f.arrow(0,23.9,new Vec3(0,0,-.1)),outside=f.arrow(0,24.01,new Vec3(0,0,-.1)),side=f.arrow(2.01,12,new Vec3(0,0,-.1)),rear=f.arrow(0,-1,new Vec3(0,0,.1)),receding=f.arrow(.2,12,new Vec3(0,0,.1));
        // Place the reviewed shooter beyond the far arrow so both accepted trajectories are outgoing from it.
        f.skeleton.setPos(f.p.position().add(0,0,27));
        MagneticArrowSeparation.tick(f.p);
        h.assertTrue(MagneticArrowSeparation.leased(near)&&MagneticArrowSeparation.leased(far),"near and within24m incoming ordinary enemy arrows steer");
        for(Arrow rejected:List.of(outside,side,rear,receding))h.assertFalse(MagneticArrowSeparation.leased(rejected),"outside/behind/receding not admitted");near(h,MagneticArrowSeparation.affectedCount(f.p),2,"only exact eligible subset admitted");h.succeed();
            });
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void arrow_separation_authoritative_pose_wall_shortening_and_changed_aim_cancel(GameTestHelper h) {
        Fixture f=new Fixture(h);BlockPos wall=BlockPos.containing(f.p.getEyePosition().add(0,0,6));h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        var pose=MagneticArrowSeparation.capturePose(f.p);h.assertTrue(pose!=null&&pose.range()<6&&pose.range()>4,"server preview terminates before first centerline wall");

        f.arm(1, () -> {
        Arrow hidden=f.arrow(0,12,new Vec3(0,0,-.3));double cp=f.cp();MagneticArrowSeparation.tick(f.p);h.assertFalse(MagneticArrowSeparation.leased(hidden),"arrow behind wall not influenced");near(h,f.cp(),cp,"obstructed field unpaid");MagneticItemPull.abort(f.p);
        h.getLevel().setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());h.assertTrue(MagneticItemPull.beginArrows(f.p,f.wire(2)),"captured server preview lease");
        h.runAfterDelay(1,()->{f.p.setYRot(20);h.assertFalse(MagneticItemPull.releaseArrows(f.p,f.wire(2)),"material aim change cancels instead of quietly redirecting captured corridor");f.p.setYRot(0);
            h.assertTrue(MagneticItemPull.beginArrows(f.p,f.wire(3)),"fresh preview");h.runAfterDelay(1,()->{f.p.setPos(f.p.position().add(.6,0,0));h.assertFalse(MagneticItemPull.releaseArrows(f.p,f.wire(3)),"material position change cancels");h.succeed();});});
            });
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void arrow_separation_zero_cp_and_invalid_next_ray_never_write(GameTestHelper h) {
        Fixture f=new Fixture(h);
        f.arm(1, () -> {
Arrow arrow=f.arrow(0,10,new Vec3(0,0,-.4));f.setCp(0);Vec3 before=arrow.getDeltaMovement();MagneticArrowSeparation.tick(f.p);
        h.assertTrue(!MagneticArrowSeparation.active(f.p)&&arrow.getDeltaMovement().equals(before),"failed first payment retires without write");h.assertFalse(MagneticArrowSeparation.leased(arrow),"failed payment cannot consume projectile lease");near(h,f.cp(),0,"zeroCP never becomes negative");f.setCp(100);
        // The current arrow remains visible; its proposed/native next ray enters the adjacent wall.
        arrow.setPos(f.p.getEyePosition().add(0,0,4.8));arrow.setDeltaMovement(new Vec3(0,0,-1));BlockPos block=BlockPos.containing(f.p.getEyePosition().add(0,0,4));h.getLevel().setBlockAndUpdate(block,Blocks.STONE.defaultBlockState());
        f.arm(2, () -> {double blockedCp=f.cp();MagneticArrowSeparation.tick(f.p);h.assertFalse(MagneticArrowSeparation.leased(arrow),"blocked current/next ray is not rewritten");near(h,f.cp(),blockedCp,"blocked ray has no cost");h.succeed();
            });});
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void arrow_separation_live_owner_team_target_and_death_changes_retire(GameTestHelper h) {
        Fixture f=new Fixture(h);
        f.arm(1, () -> {
Arrow arrow=f.arrow(.1,12,new Vec3(0,.2,-.1));MagneticArrowSeparation.tick(f.p);double turned=turn(arrow);
        var board=h.getLevel().getScoreboard();var team=board.addPlayerTeam("hp_arrow_"+f.p.getId());board.addPlayerToTeam(f.p.getScoreboardName(),team);board.addPlayerToTeam(f.skeleton.getScoreboardName(),team);
        h.runAfterDelay(1,()->{MagneticArrowSeparation.tick(f.p);near(h,turn(arrow),turned,"new bilateral alliance prevents another native steering write");near(h,MagneticArrowSeparation.targetCount(f.p),0,"allied shooter target retires");board.removePlayerTeam(team);MagneticItemPull.abort(f.p);f.resetCooldown();
            Mob cow=f.mob(EntityType.COW,0,22);cow.setTarget(f.p);Arrow hostilePassive=f.arrow(.2,12,new Vec3(0,.2,-.1));hostilePassive.setOwner(cow);f.arm(2, () -> {MagneticArrowSeparation.tick(f.p);h.assertTrue(MagneticArrowSeparation.leased(hostilePassive),"positive Mob target proof admitted");double cowTurn=turn(hostilePassive);cow.setTarget(null);
            h.runAfterDelay(1,()->{MagneticArrowSeparation.tick(f.p);near(h,turn(hostilePassive),cowTurn,"target loss midflight cannot continue steering");MagneticItemPull.abort(f.p);f.resetCooldown();Arrow orphan=f.arrow(.3,12,new Vec3(0,.2,-.1));f.arm(3, () -> {MagneticArrowSeparation.tick(f.p);double last=turn(orphan);f.skeleton.discard();h.runAfterDelay(1,()->{MagneticArrowSeparation.tick(f.p);near(h,turn(orphan),last,"dead or unloaded shooter stops before next write");h.succeed();});});});});});
            });
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void arrow_separation_native_server_player_pvp_revocation_stops_midflight(GameTestHelper h) {
        Fixture f=new Fixture(h),other=new Fixture(h);
        f.arm(1, () -> {
other.p.setPos(f.p.position().add(0,0,22));other.p.connection.resetPosition();Arrow arrow=f.arrow(0,12,new Vec3(0,.2,-.1));arrow.setOwner(other.p);
        boolean old=f.p.server.isPvpAllowed();f.p.server.setPvpAllowed(true);
        try {MagneticArrowSeparation.tick(f.p);h.assertTrue(MagneticArrowSeparation.leased(arrow),"exactServerPlayer plus reciprocal nativePvP/config permits steering");}
        finally {f.p.server.setPvpAllowed(old);}
        double before=turn(arrow);h.runAfterDelay(1,()->{h.assertFalse(f.p.server.isPvpAllowed(),"nativeGameTest serverPvP default restored");MagneticArrowSeparation.tick(f.p);near(h,turn(arrow),before,"serverPvP revocation is revalidated before subsequent steering");h.succeed();});
            });
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void arrow_separation_rolling_four_survives_abort_recast_and_cache_reload(GameTestHelper h) {
        Fixture f=new Fixture(h);
        f.arm(1, () -> {
List<Arrow> arrows=new ArrayList<>();for(int i=0;i<5;i++)arrows.add(f.arrow((i-2)*.15,12,new Vec3(0,.2,-.1)));
        MagneticArrowSeparation.tick(f.p);near(h,MagneticArrowSeparation.affectedCount(f.p),4,"at mostfour unique admissions per20ticks");
        Arrow fifth=arrows.stream().filter(a->!MagneticArrowSeparation.leased(a)).findFirst().orElseThrow();MagneticItemPull.abort(f.p);f.resetCooldown();f.arm(2, () -> {double recastCp=f.cp();MagneticArrowSeparation.tick(f.p);h.assertFalse(MagneticArrowSeparation.leased(fifth),"recast does not reset rolling quota");near(h,f.cp(),recastCp,"full quota cannot trigger new payment");
        MagneticItemPull.abort(f.p);dropCachedState(f);f.resetCooldown();f.arm(3, () -> {MagneticArrowSeparation.tick(f.p);h.assertFalse(MagneticArrowSeparation.leased(fifth),"persistedquota survives controller cache reload");for(Arrow a:arrows)if(a!=fifth)h.assertTrue(MagneticArrowSeparation.leased(a),"persistedarrow leases survive cache reload");h.succeed();
            });});});
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void arrow_separation_lifetime45degree_budget_survives_native_entity_reload(GameTestHelper h) {
        Fixture f=new Fixture(h);
        f.arm(1, () -> {
Arrow arrow=f.arrow(0,12,new Vec3(0,.2,-.08));MagneticArrowSeparation.tick(f.p);
        h.runAfterDelay(9,()->{near(h,turn(arrow),Math.PI/4,"actualcontroller reaches exact45degree lifetime cap");});
        h.runAfterDelay(22,()->{CompoundTag saved=tag(arrow);arrow.discard();Arrow loaded=new Arrow(EntityType.ARROW,h.getLevel());loaded.load(saved);loaded.setPos(f.p.getEyePosition().add(0,0,12));loaded.setDeltaMovement(new Vec3(0,.2,-.08));f.entities.add(loaded);h.getLevel().addFreshEntity(loaded);h.assertFalse(MagneticArrowSeparation.leased(loaded),"20tick residual lease expires normally");f.resetCooldown();f.arm(2, () -> {double cp=f.cp();Vec3 before=loaded.getDeltaMovement();MagneticArrowSeparation.tick(f.p);h.assertTrue(loaded.getDeltaMovement().equals(before),"expired lease and full native entity reload cannot reset lifetime45degree budget");near(h,f.cp(),cp,"already-exhausted angle cannot charge newpulse");near(h,turn(loaded),Math.PI/4,"persisted total stays exact");h.succeed();});});
            });
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void arrow_separation_malformed_future_persistent_quota_and_arrow_lease_fail_closed(GameTestHelper h) {
        Fixture f=new Fixture(h);
        f.arm(1, () -> {
Arrow arrow=f.arrow(0,12,new Vec3(0,.1,-.3));arrow.getPersistentData().putString(MagneticArrowSeparation.LEASE_KEY,"bad");h.assertTrue(MagneticArrowSeparation.leased(arrow),"wrongtyped persistedlease blocks everywriter");MagneticArrowSeparation.tick(f.p);h.assertFalse(MagneticArrowSeparation.paid(f.p),"badarrowlease nevercharges");MagneticItemPull.abort(f.p);arrow.getPersistentData().remove(MagneticArrowSeparation.LEASE_KEY);
        f.p.getPersistentData().putString(MagneticArrowSeparation.QUOTA_KEY,"bad");h.assertTrue(MagneticItemPull.beginArrows(f.p,f.wire(2)),"preview alone does not mutate quota");
        h.runAfterDelay(1,()->{h.assertFalse(MagneticItemPull.releaseArrows(f.p,f.wire(2)),"malformedquota denies controller admission");f.p.getPersistentData().remove(MagneticArrowSeparation.QUOTA_KEY);
            CompoundTag quota=new CompoundTag();quota.putInt("version",1);quota.putLong("observed",h.getLevel().getServer().overworld().getGameTime()+100);quota.put("entries",new ListTag());f.p.getPersistentData().put(MagneticArrowSeparation.QUOTA_KEY,quota);h.assertTrue(MagneticItemPull.beginArrows(f.p,f.wire(3)),"futurequota can preview");
            h.runAfterDelay(1,()->{h.assertFalse(MagneticItemPull.releaseArrows(f.p,f.wire(3)),"futureclock quota failsclosed");h.succeed();});});
            });
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void arrow_separation_shared_leases_exclude_bracer_decoy_and_groundstep_both_directions(GameTestHelper h) {
        Fixture f=new Fixture(h);
        f.arm(1, () -> {
Arrow arrow=f.arrow(0,3,new Vec3(0,0,-.2));
        arrow.getPersistentData().putLong("academy:ground_step_arrow_until",h.getLevel().getServer().overworld().getGameTime()+20);MagneticArrowSeparation.tick(f.p);h.assertFalse(MagneticArrowSeparation.paid(f.p),"groundstepfirst arrow never doublewritten");MagneticItemPull.abort(f.p);arrow.getPersistentData().remove("academy:ground_step_arrow_until");
        f.arm(2, () -> {MagneticArrowSeparation.tick(f.p);h.assertTrue(MagneticArrowSeparation.leased(arrow),"NEW133 residuallease published");
        try {Method bracer=ElectromasterPrototype.class.getDeclaredMethod("eligibleArrow",AbstractArrow.class,ServerPlayer.class,Vec3.class,Vec3.class);bracer.setAccessible(true);h.assertFalse((Boolean)bracer.invoke(null,arrow,f.p,f.p.getEyePosition(),f.p.getLookAngle()),"bracer refuses NEW133firstlease");Method decoy=ElectromasterChargeDecoy.class.getDeclaredMethod("cheapEligible",ServerPlayer.class,Vec3.class,AbstractArrow.class);decoy.setAccessible(true);h.assertFalse((Boolean)decoy.invoke(null,f.p,arrow.position(),arrow),"decoy refuses NEW133firstlease");Method ground=MagneticGroundStep.class.getDeclaredMethod("cheapEligible",ServerPlayer.class,AbstractArrow.class);ground.setAccessible(true);h.assertFalse((Boolean)ground.invoke(null,f.p,arrow),"groundstep refuses NEW133firstlease");}catch(ReflectiveOperationException e){throw new AssertionError(e);}h.succeed();
            });});
}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void arrow_separation_facade_mode_mutual_exclusion_cancel_and_session_lifecycle(GameTestHelper h) {
        Fixture f=new Fixture(h);h.assertTrue(MagneticItemPull.beginArrows(f.p,f.wire(1)),"servermode lease");h.assertFalse(MagneticItemPull.tap(f.p,f.wire(1),List.of()),"arrowlease cannot be released as itemselection");h.assertFalse(MagneticItemPull.releaseArrows(f.p,f.wire(1)),"wrongmode attempt also consumes lease");
        h.assertTrue(MagneticItemPull.beginArrows(f.p,f.wire(2)),"fresh modelease");h.assertTrue(MagneticItemPull.abort(f.p,f.wire(2)),"owned abort retires selectingmode");h.assertFalse(MagneticItemPull.releaseArrows(f.p,f.wire(2)),"canceled lease cannot replay");f.arm(3, () -> {h.assertFalse(MagneticItemPull.begin(f.p,f.wire(4)),"ownitemmode cannot overlap arrowcontroller");
        AbilityStorage.get(f.p).interfering=true;MagneticArrowSeparation.tick(f.p);h.assertFalse(MagneticArrowSeparation.active(f.p),"interference retires even same tick");AbilityStorage.get(f.p).interfering=false;f.arm(5, () -> {LegacySingleKeyOwnerEpoch.forget(f.p);MagneticArrowSeparation.tick(f.p);h.assertFalse(MagneticArrowSeparation.active(f.p),"sessionepoch loss retires exactowner");f.epoch();f.arm(6, () -> {AbilityStorage.get(f.p).selectCategory("meltdowner");MagneticArrowSeparation.tick(f.p);h.assertFalse(MagneticArrowSeparation.active(f.p),"categorychange retires");h.succeed();
    });});});}
    @SuppressWarnings("unchecked")
    private static void dropCachedState(Fixture f) {
        try {
            var quota=MagneticArrowSeparation.class.getDeclaredField("QUOTAS");quota.setAccessible(true);((Map<UUID,?>)quota.get(null)).remove(f.p.getUUID());
            var leases=MagneticArrowSeparation.class.getDeclaredField("LEASES");leases.setAccessible(true);Map<UUID,?> live=(Map<UUID,?>)leases.get(null);for(Entity entity:f.entities)live.remove(entity.getUUID());
        } catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static final class Fixture {
        final GameTestHelper h; final ServerPlayer p; final EmbeddedChannel channel; final Skeleton skeleton; final List<Entity> entities=new ArrayList<>(); boolean closed;
        Fixture(GameTestHelper h) {
            this.h=h;for(int x=3;x<=14;x++)for(int z=3;z<=31;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPA"+UUID.randomUUID().toString().substring(0,10)),false);p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;
            Vec3 at=h.absoluteVec(new Vec3(5.5,2,4.5));p.moveTo(at.x,at.y,at.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();h.assertTrue(HighPowerCombat.enroll(p,3),"real connected LV3 caster");var state=AbilityStorage.get(p);state.selectCategory("electromaster");state.setLevel(3);state.learn("forked_arc");state.learn("magnetic_bracer");state.learn(MagneticItemPull.ID);state.activated=true;state.overloadFine=true;h.assertTrue(state.presets.edit(0,0,MagneticItemPull.ID,id->id.equals(MagneticItemPull.ID)),"learned sameNEW133slot");ElectromasterPrototype.initializeBudget(p);epoch();
            skeleton=(Skeleton)mob(EntityType.SKELETON,0,22);
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Mob mob(EntityType<? extends Mob> type,double x,double z){Mob mob=type.create(h.getLevel());h.assertTrue(mob!=null,"native reviewed shooter created");mob.setPos(p.position().add(x,0,z));mob.setNoAi(true);mob.setNoGravity(true);h.getLevel().addFreshEntity(mob);entities.add(mob);return mob;}
        Arrow arrow(double x,double z,Vec3 velocity){Arrow a=new Arrow(h.getLevel(),skeleton,new ItemStack(Items.ARROW),null);place(a,x,z,velocity);return a;}
        void place(AbstractArrow a,double x,double z,Vec3 velocity){a.setPos(p.getEyePosition().add(x,0,z));a.setDeltaMovement(velocity);h.getLevel().addFreshEntity(a);entities.add(a);}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        void arm(long input,Runnable after){
            h.assertTrue(MagneticItemPull.beginArrows(p,wire(input)),"native server begins immutable preview lease");
            h.runAfterDelay(1,()->{h.assertTrue(MagneticItemPull.releaseArrows(p,wire(input)),"native one-tick preview releases its one-use corridor lease");after.run();});
        }
        double cp(){return p.getPersistentData().getCompound("academy:electromaster_prototype").getDouble("cp");}
        void setCp(double cp){p.getPersistentData().getCompound("academy:electromaster_prototype").putDouble("cp",cp);}
        void resetCooldown(){var state=AbilityStorage.get(p);state.cooldowns.remove(MagneticItemPull.ID);state.cooldownMaxTicks.remove(MagneticItemPull.ID);p.getPersistentData().getCompound("academy:electromaster_prototype").remove("next_"+MagneticItemPull.ID);}
        void close(){if(closed)return;closed=true;MagneticItemPull.remove(p);MagneticArrowSeparation.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}
