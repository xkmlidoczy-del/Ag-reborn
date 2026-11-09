package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.IronSandLanceRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real server hold identities, native offhand transaction, finite physical paths, and contact-only Arrow assists. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerIronSandLanceRuntimeTests {
    private static final String TEMPLATE="highpower_empty",BATCH="highpower_iron_sand_lance";
    private HighPowerIronSandLanceRuntimeTests(){}
    private static void near(GameTestHelper h,double a,double b,String why){h.assertTrue(Math.abs(a-b)<1e-7,why+": "+a+" != "+b);}
    private static BigDecimal health(LivingEntity e){return new BigDecimal(HighPowerCombat.healthText(e));}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void lance_unpaid_short_hold_abort_and_replay_are_free(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp(),exp=AbilityStorage.get(f.p).exp(IronSandLance.ID);
        h.assertFalse(IronSandLance.release(f.p,f.wire(1)),"release needs actual begin");
        h.assertTrue(IronSandLance.begin(f.p,f.wire(1),false),"real free server charge");
        h.assertFalse(IronSandLance.release(f.p,f.wire(1)),"same-tick short release refused");
        h.assertFalse(IronSandLance.active(f.p),"early release consumes charge identity");
        h.assertFalse(IronSandLance.begin(f.p,f.wire(1),false),"same begin replay cannot rearm");
        h.assertTrue(IronSandLance.begin(f.p,f.wire(2),false),"fresh charge");h.assertTrue(IronSandLance.abort(f.p,f.wire(2)),"owned abort");
        h.assertFalse(IronSandLance.release(f.p,f.wire(2)),"aborted release cannot commit");near(h,f.cp(),cp,"unpaid CP unchanged");near(h,f.sand(),9,"unpaid offhand unchanged");near(h,AbilityStorage.get(f.p).exp(IronSandLance.ID),exp,"no charge farming");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void lance_twelve_ticks_one_atomic_payment_partial_emission_and_postcommit_abort(GameTestHelper h){
        Fixture f=new Fixture(h);f.charge(1,false,()->{
            double before=f.cp();int count=f.sand();
            h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"release after real12ticks");near(h,f.cp(),before-12,"oneLV3CP12");near(h,f.sand(),count-3,"exactthree medium");
            h.assertTrue(IronSandLance.paid(f.p)&&IronSandLance.emittedCount(f.p)==1,"only first lance emitted on tick0 after transaction");
            var pose=IronSandLance.serverPose(f.p);h.assertTrue(pose.get(0).status()==IronSandLance.FLYING&&pose.get(1).status()==IronSandLance.WAITING&&pose.get(2).status()==IronSandLance.WAITING,"singlecontroller staggered emission");
            Vec3 head=pose.get(0).head();IronSandLance.tick(f.p);h.assertTrue(IronSandLance.serverPose(f.p).get(0).head().equals(head),"same-tick duplicate callback does not move again");
            h.assertFalse(IronSandLance.release(f.p,f.wire(1)),"release replay cannot pay or emit again");
            double paid=f.cp();IronSandLance.abort(f.p);near(h,f.cp(),paid,"partialvolley abort neverrefundsCP");near(h,f.sand(),count-3,"partialvolley abort neverrefundssand");
            h.assertFalse(IronSandLance.active(f.p),"remainingtwo lances never launch");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void lance_offhand_object_count_components_and_mode_are_revalidated(GameTestHelper h){
        Fixture f=new Fixture(h);h.assertTrue(IronSandLance.begin(f.p,f.wire(1),false),"begun charge");
        f.p.setItemSlot(EquipmentSlot.OFFHAND,f.p.getOffhandItem().copy());IronSandLance.tick(f.p);h.assertFalse(IronSandLance.active(f.p),"same-looking replacement object cancels");
        h.assertTrue(IronSandLance.begin(f.p,f.wire(2),false),"fresh object charge");f.p.getOffhandItem().shrink(1);IronSandLance.tick(f.p);h.assertFalse(IronSandLance.active(f.p),"any count change cancels");
        h.assertTrue(IronSandLance.begin(f.p,f.wire(3),false),"fresh count charge");f.p.getOffhandItem().set(DataComponents.CUSTOM_NAME,Component.literal("changed"));IronSandLance.tick(f.p);h.assertFalse(IronSandLance.active(f.p),"component change cancels");
        h.assertTrue(IronSandLance.begin(f.p,f.wire(4),true),"both modes allowed independently of crouch packet timing");IronSandLance.abort(f.p);
        f.charge(5,false,()->{f.p.setShiftKeyDown(true);h.assertTrue(IronSandLance.release(f.p,f.wire(5)),"startmode remains valid when crouch changes");h.assertTrue(IronSandLance.emittedCount(f.p)==1,"capturedattack not converted toorbit");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void lance_zero_cp_invalid_preset_and_unlearned_hold_never_consume_medium(GameTestHelper h){
        Fixture f=new Fixture(h);f.charge(1,false,()->{
            f.setCp(0);int sand=f.sand();h.assertFalse(IronSandLance.release(f.p,f.wire(1)),"noCP commit refused");near(h,f.sand(),sand,"failedcommit neverconsumes sand");near(h,f.cp(),0,"noCP remains zero");f.setCp(100);
            h.assertTrue(IronSandLance.begin(f.p,f.wire(2),false),"newcharge after failed transaction");AbilityStorage.get(f.p).presets.edit(0,0,"",id->true);IronSandLance.tick(f.p);h.assertFalse(IronSandLance.active(f.p),"slot revision change cancels immediately");near(h,f.sand(),sand,"invalidpreset has no inventory cost");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void lance_creative_wrong_medium_and_forged_identity_never_start(GameTestHelper h){
        Fixture f=new Fixture(h);f.p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.IRON_INGOT,9));h.assertFalse(IronSandLance.begin(f.p,f.wire(1),false),"genericiron is not registered ironsand");
        f.p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(IronSandMedium.ITEM.get(),2));h.assertFalse(IronSandLance.begin(f.p,f.wire(2),false),"shortstack refused");f.sand(9);f.p.setGameMode(GameType.CREATIVE);h.assertFalse(IronSandLance.begin(f.p,f.wire(3),false),"creative neverwaives finite payment");f.p.setGameMode(GameType.SURVIVAL);
        String forged=new HighPowerInput.Input(0,4,f.p.getId()+1,f.epoch()).wire();h.assertFalse(IronSandLance.begin(f.p,forged,false),"foreignactor refused");
        String wire=f.wire(5);LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();h.assertFalse(IronSandLance.begin(f.p,wire,false),"oldsession packet refused");near(h,f.sand(),9,"allrefusals leave medium");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=55)
    public static void lance_three_actual_trajectories_speed_stagger_and_range(GameTestHelper h){
        Fixture f=new Fixture(h);f.p.setPos(f.p.position().add(0,16,0));f.p.connection.resetPosition();
        double range=IronSandLanceRules.RANGE;
        int firstRangeEnd=(int)Math.ceil(range/IronSandLanceRules.SPEED)-1;
        Vec3 clearStart=f.p.getEyePosition(),clearEnd=clearStart.add(0,0,range+1);
        h.assertTrue(h.getLevel().clip(new net.minecraft.world.level.ClipContext(clearStart,clearEnd,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,f.p)).getType()==net.minecraft.world.phys.HitResult.Type.MISS,"fixture range lane stays above neighboring nativeGameTest structures");
        f.charge(1,false,()->{
            Vec3 muzzle=f.p.getEyePosition().add(0,0,.2);h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"paidvolley");
            near(h,IronSandLance.serverPose(f.p).get(0).head().distanceTo(muzzle),2,"firstreal path2m");
            h.runAfterDelay(1,()->{IronSandLance.tick(f.p);var poses=IronSandLance.serverPose(f.p);near(h,poses.get(0).head().distanceTo(muzzle),4,"firsttravels4m aftertwosteps");near(h,poses.get(1).head().distanceTo(muzzle),2,"secondstarts tick1");near(h,poses.get(2).head().distanceTo(muzzle),0,"thirdnotyet emitted");});
            h.runAfterDelay(2,()->{IronSandLance.tick(f.p);var poses=IronSandLance.serverPose(f.p);h.assertTrue(IronSandLance.emittedCount(f.p)==3,"threefinite shots bytick2");h.assertTrue(poses.get(0).head().x>muzzle.x&&Math.abs(poses.get(1).head().x-muzzle.x)<1e-9&&poses.get(2).head().x<muzzle.x,"actual yaw−4/0/+4spread");});
            h.runAfterDelay(firstRangeEnd,()->{IronSandLance.tick(f.p);var poses=IronSandLance.serverPose(f.p);h.assertTrue(poses.size()==3,"later two trajectories keep controller present atfirst rangeend");near(h,poses.get(0).head().distanceTo(muzzle),range,"first actual path ends at exact reach");for(var pose:poses)h.assertTrue(pose.head().distanceTo(muzzle)<=range+1e-8,"no head passes configured reach");});
            h.runAfterDelay(firstRangeEnd+3,()->{h.assertFalse(IronSandLance.active(f.p),"maximum reach plus finite stagger retires the volley");near(h,f.sand(),6,"no recoveryitems at rangeend");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void lance_first_solid_stops_all_paths_and_never_edits_blocks(GameTestHelper h){
        Fixture f=new Fixture(h);BlockPos wall=BlockPos.containing(f.p.getEyePosition().add(0,0,5));
        for(int x=-2;x<=2;x++)for(int y=-1;y<=1;y++)h.getLevel().setBlockAndUpdate(wall.offset(x,y,0),Blocks.STONE.defaultBlockState());
        Zombie behind=f.target(0,8);BigDecimal hp=health(behind);f.charge(1,false,()->{
            h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"wallisphysicalhit ratherthan freecancel");
            h.runAfterDelay(7,()->{h.assertTrue(health(behind).compareTo(hp)==0,"no damage throughfirstsolid");h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE),"no blockedit or explosion");h.assertFalse(IronSandLance.active(f.p),"threewallhits endcontroller");near(h,f.sand(),6,"solidimpact consumes committed sand");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void lance_exact_damage_native_iframes_and_no_piercing(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,4),behind=f.target(0,8);BigDecimal targetBefore=health(target),behindBefore=health(behind);
        f.charge(1,false,()->{
            BigDecimal damage=IronSandLanceRules.damage(HighPowerGrowth.values(f.p).damage());double exp=AbilityStorage.get(f.p).exp(IronSandLance.ID);
            h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"launchedphysicalvolley");
            h.runAfterDelay(8,()->{BigDecimal lost=targetBefore.subtract(health(target));h.assertTrue(lost.compareTo(damage)==0,"threeoverlapping shots respect nativeiframes, one exact.6Dhit: "+lost+" / "+damage);h.assertTrue(health(behind).compareTo(behindBefore)==0,"firstentity stops eachlance withoutpiercing; behind_delta="+behindBefore.subtract(health(behind))+" fire="+behind.isOnFire()+" at="+behind.position()+" sky="+h.getLevel().canSeeSky(behind.blockPosition()));near(h,AbilityStorage.get(f.p).exp(IronSandLance.ID),exp+.005,"onepositive mastery award percast");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void lance_allied_target_blocks_physical_path_without_damage_or_mastery(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie ally=f.target(0,4),behind=f.target(0,8);BigDecimal a=health(ally),b=health(behind);
        var board=h.getLevel().getScoreboard();var team=board.addPlayerTeam("hp_lance_"+f.p.getId());board.addPlayerToTeam(f.p.getScoreboardName(),team);board.addPlayerToTeam(ally.getScoreboardName(),team);f.teams.add(team);
        f.charge(1,false,()->{double exp=AbilityStorage.get(f.p).exp(IronSandLance.ID);h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"physicalvolleystarts");h.runAfterDelay(8,()->{h.assertTrue(health(ally).compareTo(a)==0&&health(behind).compareTo(b)==0,"alliedcontact neitherhurtsnor pierces; ally_delta="+a.subtract(health(ally))+" behind_delta="+b.subtract(health(behind))+" allyfire="+ally.isOnFire()+" behindfire="+behind.isOnFire()+" sky="+h.getLevel().canSeeSky(behind.blockPosition()));near(h,AbilityStorage.get(f.p).exp(IronSandLance.ID),exp,"refusedhit doesnotfarm");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void lance_interference_and_epoch_loss_aftercommit_no_refund_or_remaining_shots(GameTestHelper h){
        Fixture f=new Fixture(h);f.charge(1,false,()->{h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"paidfirstshot");double cp=f.cp();AbilityStorage.get(f.p).interfering=true;IronSandLance.tick(f.p);h.assertFalse(IronSandLance.active(f.p),"same-tick invalidation wins duplicate callback");near(h,f.cp(),cp,"interference doesnotrefund");near(h,f.sand(),6,"remainingtwo notrefunded");AbilityStorage.get(f.p).interfering=false;f.resetCooldown();
            h.assertTrue(IronSandLance.begin(f.p,f.wire(2),false),"freshunpaidcharge");String old=f.wire(2);LegacySingleKeyOwnerEpoch.forget(f.p);IronSandLance.tick(f.p);h.assertFalse(IronSandLance.active(f.p),"lostexactownersession cancels");f.epoch();h.assertFalse(IronSandLance.release(f.p,old),"oldrelease cannotresurrect afterrelog");near(h,f.sand(),6,"oldsession leavesremaining medium");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void lance_logout_and_death_cleanup_discard_paid_state_without_items(GameTestHelper h){
        Fixture f=new Fixture(h);f.charge(1,false,()->{h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"paidcommit");IronSandLance.logout(new PlayerEvent.PlayerLoggedOutEvent(f.p));h.assertFalse(IronSandLance.active(f.p),"logoutremoves paidcontroller");near(h,f.sand(),6,"logout doesnotrecover medium");f.resetCooldown();f.epoch();
            h.assertTrue(IronSandLance.begin(f.p,f.wire(2),false),"newunpaid charge");f.p.setHealth(0);IronSandLance.tick(f.p);h.assertFalse(IronSandLance.active(f.p),"deadowner neverkeepscharge");near(h,f.sand(),6,"deathbeforecommit doesnotconsume another3");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void lance_orbit_actual_contact_only_one_deflection_metadata_and_no_damage(GameTestHelper h){
        Fixture f=new Fixture(h);f.skeleton.setPos(f.p.position().add(12,0,0));
        f.charge(1,true,()->{
            Vec3 center=f.p.getEyePosition().add(0,-.4,0);Arrow contact=f.arrow(center.add(1.6,0,.1),new Vec3(-.3,.12,0));Arrow gap=f.arrow(center.add(0,0,.8),new Vec3(0,0,-.2));
            contact.setBaseDamage(3.5);contact.pickup=AbstractArrow.Pickup.CREATIVE_ONLY;contact.getPersistentData().putString("fixture_metadata","preserved");CompoundTag before=contact.saveWithoutId(new CompoundTag());Vec3 velocity=contact.getDeltaMovement(),position=contact.position();float hp=f.p.getHealth();double cp=f.cp(),exp=AbilityStorage.get(f.p).exp(IronSandLance.ID);
            h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"paidorbit");near(h,f.cp(),cp-12,"orbit paysonceatcommit");near(h,f.sand(),6,"orbit exact3sand");h.assertTrue(IronSandLance.deflectedCount(f.p)==1&&IronSandLance.leased(contact),"actualsinglecapsulecontact deflects one");h.assertFalse(IronSandLance.leased(gap),"insidebroadring gap isnot invisible spherecontact");
            Vec3 changed=contact.getDeltaMovement();h.assertTrue(changed.y==velocity.y&&changed.lengthSqr()<=velocity.lengthSqr()&&changed.dot(velocity)>0,"sameY nonincreasingspeed noreflection");h.assertTrue(contact.position().equals(position)&&contact.getOwner()==f.skeleton,"no teleport or ownersteal");near(h,f.p.getHealth(),hp,"orbit adds no customdamage");near(h,AbilityStorage.get(f.p).exp(IronSandLance.ID),exp,"orbit hasno passivemastery");
            CompoundTag after=contact.saveWithoutId(new CompoundTag());for(String key:List.of("Motion","NeoForgeData")){before.remove(key);after.remove(key);}h.assertTrue(before.equals(after),"allnative serialized metadata unchangedexceptmotion/lease");
            IronSandLance.tick(f.p);h.assertTrue(contact.getDeltaMovement().equals(changed)&&IronSandLance.deflectedCount(f.p)==1,"duplicatecallback notseconddeflection");IronSandLance.abort(f.p);h.assertTrue(IronSandLance.leased(contact),"residuallease survives abort");near(h,f.sand(),6,"orbitabort no recoveryitems");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void lance_orbit_plain_hostile_whitelist_and_reciprocal_leases(GameTestHelper h){
        Fixture f=new Fixture(h);f.skeleton.setPos(f.p.position().add(12,0,0));f.charge(1,true,()->{
            Vec3 center=f.p.getEyePosition().add(0,-.4,0);List<Arrow> rejected=new ArrayList<>();
            Arrow ownerless=new Arrow(EntityType.ARROW,h.getLevel());f.place(ownerless,center.add(1.6,0,0),new Vec3(-.2,0,0));rejected.add(ownerless);
            Arrow self=f.arrow(center.add(1.6,0,.05),new Vec3(-.2,0,0));self.setOwner(f.p);rejected.add(self);
            Arrow potion=f.arrow(center.add(1.6,0,.1),new Vec3(-.2,0,0));potion.addEffect(new MobEffectInstance(MobEffects.POISON,60));rejected.add(potion);
            Arrow critical=f.arrow(center.add(1.6,0,.15),new Vec3(-.2,0,0));critical.setCritArrow(true);rejected.add(critical);
            Arrow noGravity=f.arrow(center.add(1.6,0,.2),new Vec3(-.2,0,0));noGravity.setNoGravity(true);rejected.add(noGravity);
            Arrow leased=f.arrow(center.add(1.6,0,.25),new Vec3(-.2,0,0));leased.getPersistentData().putLong("academy:ground_step_arrow_until",h.getLevel().getServer().overworld().getGameTime()+20);rejected.add(leased);
            Map<Arrow,Vec3> before=new HashMap<>();for(Arrow a:rejected)before.put(a,a.getDeltaMovement());
            h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"orbit paidindependentof arrowadmission");h.assertTrue(IronSandLance.deflectedCount(f.p)==0,"unsupportedarrows notrewritten");for(Arrow a:rejected)h.assertTrue(a.getDeltaMovement().equals(before.get(a))&&!IronSandLance.leased(a),"excludedarrow untouched");
            Arrow eligible=f.arrow(center.add(-.8,0,1.385640646),new Vec3(.1,0,-.15));eligible.setOwner(f.skeleton);eligible.getPersistentData().putString(IronSandLance.LEASE_KEY,"bad");h.assertTrue(IronSandLance.leased(eligible),"malformedownlease failclosed");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void lance_orbit_published_lease_blocks_all_other_arrow_writers(GameTestHelper h){
        Fixture f=new Fixture(h);f.skeleton.setPos(f.p.position().add(12,0,0));f.charge(1,true,()->{
            Arrow arrow=f.arrow(f.p.getEyePosition().add(1.6,-.4,.1),new Vec3(-.3,0,0));h.assertTrue(IronSandLance.release(f.p,f.wire(1))&&IronSandLance.leased(arrow),"orbitcontact publishesguard");
            try{Method bracer=ElectromasterPrototype.class.getDeclaredMethod("eligibleArrow",AbstractArrow.class,ServerPlayer.class,Vec3.class,Vec3.class);bracer.setAccessible(true);h.assertFalse((Boolean)bracer.invoke(null,arrow,f.p,f.p.getEyePosition(),f.p.getLookAngle()),"bracerreciprocalguard");Method decoy=ElectromasterChargeDecoy.class.getDeclaredMethod("cheapEligible",ServerPlayer.class,Vec3.class,AbstractArrow.class);decoy.setAccessible(true);h.assertFalse((Boolean)decoy.invoke(null,f.p,arrow.position(),arrow),"decoyreciprocalguard");Method ground=MagneticGroundStep.class.getDeclaredMethod("cheapEligible",ServerPlayer.class,AbstractArrow.class);ground.setAccessible(true);h.assertFalse((Boolean)ground.invoke(null,f.p,arrow),"groundreciprocalguard");Method separation=MagneticArrowSeparation.class.getDeclaredMethod("otherLease",AbstractArrow.class);separation.setAccessible(true);h.assertTrue((Boolean)separation.invoke(null,arrow),"NEW133reciprocalguard");}catch(ReflectiveOperationException e){throw new AssertionError(e);}h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=160)
    public static void lance_orbit_original_five_seconds_no_refresh_and_no_lease_item_recovery(GameTestHelper h){
        Fixture f=new Fixture(h);f.charge(1,true,()->{h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"committed finite orbit");h.assertFalse(IronSandLance.release(f.p,f.wire(1)),"releasecannotextend deadline");h.runAfterDelay(IronSandLanceRules.ORBIT_TICKS-1,()->h.assertTrue(IronSandLance.active(f.p),"original deadline includes its final eligible tick"));h.runAfterDelay(IronSandLanceRules.ORBIT_TICKS+1,()->{h.assertFalse(IronSandLance.active(f.p),"orbit expires at its deadline without recovery");near(h,f.sand(),6,"expiry leavesexactspent3");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void lance_orbit_solid_capsule_refuses_commit(GameTestHelper h){
        Fixture f=new Fixture(h);f.charge(1,true,()->{Vec3 center=f.p.getEyePosition().add(0,-.4,0);BlockPos wall=BlockPos.containing(center.add(1.6,0,0));h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());double cp=f.cp();h.assertFalse(IronSandLance.release(f.p,f.wire(1)),"physicalcapsuleinside wall cancelsbeforecommit");near(h,f.cp(),cp,"invalidorbit isfreeCP");near(h,f.sand(),9,"invalidorbit isfreemedium");h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE),"wallneveredited");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void lance_orbit_observed_native_crossing_contacts_between_tick_endpoints(GameTestHelper h){
        Fixture f=new Fixture(h);f.skeleton.setPos(f.p.position().add(12,0,0));f.charge(1,true,()->{
            Vec3 center=f.p.getEyePosition().add(0,-.4,0);Arrow arrow=f.arrow(center.add(2.4,0,0),new Vec3(-1.6,0,0));
            h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"realorbit beforecrossing");h.assertFalse(IronSandLance.leased(arrow),"upcomingprojectedcontact doesnotdeflect early");
            h.runAfterDelay(1,()->{IronSandLance.tick(f.p);h.assertTrue(arrow.position().distanceTo(center)<1,"actualnative position isnowinside ring gap");h.assertTrue(IronSandLance.leased(arrow)&&IronSandLance.deflectedCount(f.p)==1,"observednative segment crossed physical movinglance betweentickpositions");h.assertTrue(arrow.getDeltaMovement().y<0&&!arrow.isNoGravity(),"native gravity continues");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void lance_orbit_three_lances_each_spent_once_and_candidate_scan_bounded(GameTestHelper h){
        Fixture f=new Fixture(h);f.charge(1,true,()->{
            Vec3 center=f.p.getEyePosition().add(0,-.4,0);List<Arrow> arrows=new ArrayList<>();
            for(int i=0;i<3;i++){
                double angle=i*Math.PI*2/3;Vec3 radial=new Vec3(Math.cos(angle),0,Math.sin(angle));
                Skeleton shooter=EntityType.SKELETON.create(h.getLevel());shooter.setPos(f.p.position().add(radial.scale(12)));shooter.setNoAi(true);shooter.setNoGravity(true);h.getLevel().addFreshEntity(shooter);f.entities.add(shooter);
                Arrow arrow=f.arrow(center.add(radial.scale(1.6)),radial.scale(-.2));arrow.setOwner(shooter);arrows.add(arrow);
            }
            Arrow fourth=f.arrow(center.add(1.6,0,.1),new Vec3(-.2,0,0));fourth.setOwner(arrows.get(0).getOwner());arrows.add(fourth);
            for(int i=0;i<70;i++){Arrow inert=new Arrow(EntityType.ARROW,h.getLevel());f.place(inert,center.add(4+i*.001,0,0),new Vec3(0,0,-.1));}
            h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"threephysical orbitsegments");h.assertTrue(IronSandLance.scannedLastTick(f.p)<=64,"nativecandidate cap64");
            // Native live-index truncation may leave a useful arrow out. It can never exceed physical capacity.
            h.assertTrue(IronSandLance.deflectedCount(f.p)<=3,"three-lance cap regardlesscandidates");h.assertTrue(arrows.stream().filter(IronSandLance::leased).count()==IronSandLance.deflectedCount(f.p),"exactadmitted subsetonly");near(h,f.sand(),6,"capacity notextra inventorydebits");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void lance_native_boat_blocks_path_without_damage_or_vehicle_manipulation(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie behind=f.target(0,8);BigDecimal hp=health(behind);
        var boat=EntityType.BOAT.create(h.getLevel());h.assertTrue(boat!=null&&boat.canBeHitByProjectile(),"nativeboat isordinary projectileblocker");boat.setNoGravity(true);boat.setPos(f.p.getEyePosition().add(0,-.3,4));h.getLevel().addFreshEntity(boat);f.entities.add(boat);
        Vec3 position=boat.position();f.charge(1,false,()->{h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"ordinarypaidattack");h.runAfterDelay(8,()->{
            h.assertTrue(health(behind).compareTo(hp)==0,"nonliving nativecollision stopsbefore supported target");h.assertTrue(!boat.isRemoved()&&boat.position().equals(position),"no boatdamage, destroy, movement or ownerchange");h.assertFalse(IronSandLance.active(f.p),"finite shotsstopatnativeboat");h.succeed();
        });});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=125)
    public static void lance_orbit_paired_actual_native_hit_efficacy(GameTestHelper h){
        Fixture f=new Fixture(h),control=new Fixture(h);control.p.setPos(f.p.position().add(0,0,8));control.p.connection.resetPosition();f.skeleton.setPos(f.p.position().add(12,0,0));
        // Both genuine connected players outlive native spawn immunity; no cooldown/invulnerability field rewrite.
        h.runAfterDelay(72,()->{control.p.setShiftKeyDown(true);f.charge(1,true,()->{
            Vec3 defendedCenter=f.p.getEyePosition().add(0,-.4,0),plainCenter=control.p.getEyePosition().add(0,-.4,0),velocity=new Vec3(-.4,.12,0);
            Arrow defended=f.arrow(defendedCenter.add(3,0,0),velocity),plain=f.arrow(plainCenter.add(3,0,0),velocity);defended.setBaseDamage(2);plain.setBaseDamage(2);
            BigDecimal defendedBefore=health(f.p),plainBefore=health(control.p);
            h.assertTrue(IronSandLance.release(f.p,f.wire(1)),"paid real12tickorbit before incoming native arrows");
            h.runAfterDelay(12,()->{
                BigDecimal defendedLoss=defendedBefore.subtract(health(f.p)),plainLoss=plainBefore.subtract(health(control.p));
                boolean actualContact=defended.getPersistentData().contains(IronSandLance.LEASE_KEY);
                System.out.println("NEW131_NATIVE_ORBIT_EFFICACY turn="+IronSandLanceRules.TURN+" actual_contact="+actualContact+" baseline_loss="+plainLoss+" defended_loss="+defendedLoss+" baseline_at="+plain.position()+" defended_at="+defended.position()+" native_y="+defended.getY()+"/"+plain.getY());
                h.assertTrue(plainLoss.signum()>0,"unprotected control really receives native directhit, not spawnimmune");
                h.assertTrue(actualContact,"orbit really contacts and changes actual incoming arrow");
                h.assertTrue(defendedLoss.signum()==0,"boundeddeflection must avert this directcenter nativehit; control="+plainLoss+" defended="+defendedLoss+" turn="+IronSandLanceRules.TURN);
                h.succeed();
            });
        });});
    }

    private static final class Fixture{
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final Skeleton skeleton;final List<Entity> entities=new ArrayList<>();final List<net.minecraft.world.scores.PlayerTeam> teams=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){
            this.h=h;for(int x=2;x<=18;x++)for(int z=2;z<=31;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPL"+UUID.randomUUID().toString().substring(0,10)),false);p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;
            Vec3 at=h.absoluteVec(new Vec3(8.5,2,5.5));p.moveTo(at.x,at.y,at.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();h.assertTrue(HighPowerCombat.enroll(p,3),"realconnectedLV3caster");var state=AbilityStorage.get(p);state.selectCategory("electromaster");state.setLevel(3);state.learn("forked_arc");state.learn("polarity_tether");state.learn(IronSandLance.ID);state.activated=true;state.overloadFine=true;h.assertTrue(state.presets.edit(0,0,IronSandLance.ID,id->id.equals(IronSandLance.ID)),"learnedsameslot");ElectromasterPrototype.initializeBudget(p);sand(9);epoch();
            skeleton=EntityType.SKELETON.create(h.getLevel());skeleton.setPos(p.position().add(8,0,15));skeleton.setNoAi(true);skeleton.setNoGravity(true);skeleton.setSilent(true);h.getLevel().addFreshEntity(skeleton);entities.add(skeleton);
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Zombie target(double x,double z){Zombie e=EntityType.HUSK.create(h.getLevel());e.setNoAi(true);e.setNoGravity(true);e.setSilent(true);e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);e.setPos(p.position().add(x,0,z));h.getLevel().addFreshEntity(e);h.assertTrue(HighPowerCombat.enroll(e,5),"reviewedexacttarget");entities.add(e);
            // Reviewed native Husk inherits Zombie's ordinary combat without random sunlight damage.
            h.assertTrue(e.getType()==EntityType.HUSK,"native sunlight-safe target, no damage/physics override");return e;}
        Arrow arrow(Vec3 at,Vec3 velocity){Arrow arrow=new Arrow(h.getLevel(),skeleton,new ItemStack(Items.ARROW),null);place(arrow,at,velocity);return arrow;}
        void place(AbstractArrow arrow,Vec3 at,Vec3 velocity){arrow.setPos(at);arrow.setDeltaMovement(velocity);h.getLevel().addFreshEntity(arrow);entities.add(arrow);}
        void charge(long input,boolean orbit,Runnable after){p.setShiftKeyDown(orbit);h.assertTrue(IronSandLance.begin(p,wire(input),orbit),"nativebegin capturesfreecharge andmode");h.runAfterDelay(12,after);}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        int sand(){return p.getOffhandItem().is(IronSandMedium.ITEM.get())?p.getOffhandItem().getCount():0;}
        void sand(int n){p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(IronSandMedium.ITEM.get(),n));}
        double cp(){return p.getPersistentData().getCompound("academy:electromaster_prototype").getDouble("cp");}
        void setCp(double cp){p.getPersistentData().getCompound("academy:electromaster_prototype").putDouble("cp",cp);}
        void resetCooldown(){var state=AbilityStorage.get(p);state.cooldowns.remove(IronSandLance.ID);state.cooldownMaxTicks.remove(IronSandLance.ID);p.getPersistentData().getCompound("academy:electromaster_prototype").remove("next_"+IronSandLance.ID);}
        void close(){if(closed)return;closed=true;IronSandLance.remove(p);teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}
