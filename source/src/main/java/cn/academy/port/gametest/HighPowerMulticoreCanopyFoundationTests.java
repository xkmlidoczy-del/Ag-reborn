package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.MulticoreCanopyGeometry;
import cn.academy.port.highpower.core.MulticoreCanopyRules;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native foundation coverage through authenticated input; no fabricated paid plans or permits. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerMulticoreCanopyFoundationTests {
    private static final String TEMPLATE="highpower_empty",BATCH="highpower_multicore_foundation";
    private static final TicketType<UUID> FAR_FIXTURE_TICKET=TicketType.create("academy_multicore_foundation_far",UUID::compareTo);
    private HighPowerMulticoreCanopyFoundationTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH+"_formation",timeoutTicks=250)
    public static void multicore_fixed_six_centers_and_first_press_lifetime(GameTestHelper h){
        Fixture f=new Fixture(h);List<Vec3> first=new ArrayList<>();h.assertTrue(MulticoreCanopy.start(f.p,f.wire(1)),"first press accepted");
        first.add(MulticoreCanopy.cores(f.p).getFirst().center());int remaining=MulticoreCanopy.remainingTicks(f.p);h.assertTrue(remaining==240,"lifetime begins first press");
        h.runAtTickTime(10,()->{face(f.p,90);h.assertTrue(MulticoreCanopy.cores(f.p).getFirst().center().equals(first.getFirst()),"turning never moves accepted core");});
        h.runAtTickTime(27,()->{
            h.assertTrue(MulticoreCanopy.cores(f.p).size()==6,"one core every five physical ticks");
            var expected=MulticoreCanopyGeometry.cores(vector(f.eye),new Vector(0,0,1));
            for(var core:MulticoreCanopy.cores(f.p))h.assertTrue(core.center().distanceTo(vec(expected.get(core.id()).center()))<1e-8,"all six use initial eye pose");
            h.assertTrue(MulticoreCanopy.release(f.p,f.wire(1)),"first release arms");h.assertTrue(MulticoreCanopy.remainingTicks(f.p)<240,"release cannot refresh lifetime");
        });
        h.runAtTickTime(243,()->{h.assertFalse(MulticoreCanopy.active(f.p),"immutable240tick expiry");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_edge",timeoutTicks=10)
    public static void multicore_requires_first_release_and_distinct_input(GameTestHelper h){
        Fixture f=new Fixture(h);h.assertTrue(MulticoreCanopy.start(f.p,f.wire(1)),"deploy starts");
        h.assertFalse(MulticoreCanopy.start(f.p,f.wire(2)),"cannot fire before first release");
        h.assertTrue(MulticoreCanopy.release(f.p,f.wire(1)),"first release arms");
        h.runAtTickTime(2,()->{h.assertFalse(MulticoreCanopy.start(f.p,f.wire(1)),"replayed physical input refused");h.assertFalse(MulticoreCanopy.release(f.p,f.wire(1)),"duplicate release cannot emit");h.assertTrue(MulticoreCanopy.receipt(f.p)==null,"no fire receipt");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_solid",timeoutTicks=10)
    public static void multicore_rejected_real_core_volume_is_free(GameTestHelper h){
        Fixture f=new Fixture(h);Vec3 center=vec(MulticoreCanopyGeometry.cores(vector(f.eye),new Vector(0,0,1)).getFirst().center());f.block(BlockPos.containing(center));double cp=f.cp();
        h.assertTrue(MulticoreCanopy.start(f.p,f.wire(1)),"bounded deployment attempt starts");
        h.assertTrue(MulticoreCanopy.cores(f.p).isEmpty(),"real .18 core center inside solid rejected");near(h,f.cp(),cp,"only accepted core pays");
        MulticoreCanopy.release(f.p,f.wire(1));h.assertFalse(MulticoreCanopy.active(f.p),"empty released deployment cleans up");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_recall",timeoutTicks=10)
    public static void multicore_sneak_recall_keeps_exact_placement_fee(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp();f.armOne();
        h.runAtTickTime(6,()->{f.p.setShiftKeyDown(true);h.assertTrue(MulticoreCanopy.start(f.p,f.wire(2)),"sneak tap recalls");h.assertFalse(MulticoreCanopy.active(f.p),"recalled core removed");near(h,f.debit(cp),.01,"no refund and no fire fee");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_zero",timeoutTicks=10)
    public static void multicore_zero_cp_has_no_unpaid_core_or_experience(GameTestHelper h){
        Fixture f=new Fixture(h);f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp",0);double xp=f.xp();
        MulticoreCanopy.start(f.p,f.wire(1));h.assertTrue(MulticoreCanopy.cores(f.p).isEmpty(),"zeroCP never accepts core");
        MulticoreCanopy.release(f.p,f.wire(1));near(h,f.xp(),xp,"unpaid deployment has no mastery");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_empty",timeoutTicks=10)
    public static void multicore_empty_fire_is_free_without_idle_experience(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp(),xp=f.xp();f.armOne();
        h.runAtTickTime(6,()->{h.assertTrue(MulticoreCanopy.start(f.p,f.wire(2)),"empty preparation processed");var receipt=MulticoreCanopy.receipt(f.p);h.assertTrue(receipt!=null&&!receipt.paid()&&receipt.frozenTargets()==0,"empty plan never paid");near(h,f.debit(cp),.01,"placement remains paid");near(h,f.xp(),xp,"no idleXP");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_flood",timeoutTicks=10)
    public static void multicore_raw_sixty_five_item_roster_overflow_refuses_before_filtering(GameTestHelper h){
        Fixture f=new Fixture(h);f.armOne();
        for(int i=0;i<65;i++){var item=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),f.eye.x+5,f.eye.y,f.eye.z+5,new ItemStack(Items.COBBLESTONE));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);h.getLevel().addFreshEntity(item);f.entities.add(item);}
        h.runAtTickTime(6,()->{double cp=f.cp();h.assertFalse(MulticoreCanopy.start(f.p,f.wire(2)),"65 raw nonhostile candidates fail closed");near(h,f.cp(),cp,"overflow buys no fire");h.assertFalse(MulticoreCanopy.active(f.p),"refused bounded work retires plan");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_retire",timeoutTicks=10)
    public static void multicore_owner_eight_meter_limit_retires_original_identity(GameTestHelper h){
        Fixture f=new Fixture(h);f.armOne();int id=MulticoreCanopy.cores(f.p).getFirst().id();Vec3 center=MulticoreCanopy.cores(f.p).getFirst().center();
        h.runAtTickTime(2,()->{f.p.setPos(f.origin.add(0,0,-7));f.p.connection.resetPosition();});
        h.runAtTickTime(4,()->{var core=MulticoreCanopy.cores(f.p).getFirst();h.assertTrue(core.id()==id&&core.center().equals(center)&&!core.available(),"out-of-range core retired with stable ID and no reposition");f.p.setPos(f.origin);f.p.connection.resetPosition();});
        h.runAtTickTime(6,()->{h.assertFalse(MulticoreCanopy.cores(f.p).getFirst().available(),"returning cannot reactivate retired core");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_owner_body",timeoutTicks=10)
    public static void multicore_emitted_ray_keeps_caster_as_first_body(GameTestHelper h){
        Fixture f=new Fixture(h);f.armOne();Vec3 core=MulticoreCanopy.cores(f.p).getFirst().center();Vec3 end=core.add(0,0,12);
        f.p.setPos(core.add(0,-f.p.getEyeHeight(),4));f.p.connection.resetPosition();
        NativeTrace hit=trace(f.p,core,end,true,.035);h.assertTrue(hit.permitted&&hit.target==f.p,"caster walked in front of fixed emitter blocks actual outgoing beam");
        NativeTrace stem=trace(f.p,f.p.getEyePosition(),core,false,.18);h.assertTrue(stem.permitted&&!stem.contact,"only eye-to-core link ignores its owner");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_radius",timeoutTicks=10)
    public static void multicore_closed_core_radius_and_solid_tie_are_native(GameTestHelper h){
        Fixture f=new Fixture(h);BlockPos pos=BlockPos.containing(f.eye.add(0,0,5));f.block(pos);Vec3 origin=new Vec3(pos.getX()-.18,pos.getY()+.5,pos.getZ()+.5);
        NativeTrace core=trace(f.p,origin,origin,true,.18);h.assertTrue(core.permitted&&core.contact&&core.status==MulticoreCanopy.BLOCKED,"closed .18-radius core tangency refused by native solid");
        Vec3 start=new Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ()-1);Vec3 endpoint=new Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ());NativeTrace tie=trace(f.p,start,endpoint,true,0);
        h.assertTrue(tie.permitted&&tie.contact&&tie.point.equals(endpoint)&&tie.status==MulticoreCanopy.BLOCKED,"solid exact terminal contact never passes");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_native_flood",timeoutTicks=10)
    public static void multicore_native_trace_bounds_irrelevant_bodies_before_filter(GameTestHelper h){
        Fixture f=new Fixture(h);Vec3 origin=f.eye.add(0,0,5);
        for(int i=0;i<65;i++){var item=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),origin.x,origin.y,origin.z,new ItemStack(Items.COBBLESTONE));item.setNoGravity(true);h.getLevel().addFreshEntity(item);f.entities.add(item);}
        NativeTrace hit=trace(f.p,origin,origin.add(0,0,1),true,.035);h.assertFalse(hit.permitted,"raw spatial overflow refuses before projectile eligibility");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_hidden",timeoutTicks=35)
    public static void multicore_hidden_target_retains_last_visible_point_then_retires(GameTestHelper h){
        Fixture f=new Fixture(h);f.armOne();List<Zombie> targets=new ArrayList<>();
        for(int i=0;i<4;i++)targets.add(f.target(f.eye.add((i-1.5)*1.6,0,24+i),true));Zombie hidden=targets.getFirst();Vec3 last=hidden.getBoundingBox().getCenter();
        h.runAtTickTime(6,()->{h.assertTrue(MulticoreCanopy.start(f.p,f.wire(2)),"frozen roster starts held warning");h.assertTrue(MulticoreCanopy.targets(f.p).stream().anyMatch(target->target.id()==hidden.getId()),"visible identity acquired");});
        h.runAtTickTime(7,()->{f.block(BlockPos.containing(last));hidden.setPos(hidden.position().add(0,0,2));});
        h.runAtTickTime(10,()->{var marker=MulticoreCanopy.targets(f.p).stream().filter(target->target.id()==hidden.getId()).findFirst().orElseThrow();h.assertTrue(marker.status()==MulticoreCanopy.LOST&&marker.point().equals(last),"hidden movement never reaches client marker");});
        h.runAtTickTime(19,()->{h.assertTrue(MulticoreCanopy.targets(f.p).stream().noneMatch(target->target.id()==hidden.getId()),"ten consecutive invisible server ticks permanently retire identity");f.clearBlocks();});
        h.runAtTickTime(22,()->{h.assertTrue(MulticoreCanopy.targets(f.p).stream().noneMatch(target->target.id()==hidden.getId()),"visibility cannot re-add retired roster identity");h.assertTrue(new BigDecimal(HighPowerCombat.healthText(hidden)).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth())==0,"hidden assigned slot loses damage");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_team",timeoutTicks=10)
    public static void multicore_dead_identity_removed_without_last_point_grace(GameTestHelper h){
        Fixture f=new Fixture(h);f.armOne();Zombie target=f.target(f.eye.add(0,0,16),true);
        h.runAtTickTime(6,()->{h.assertTrue(MulticoreCanopy.start(f.p,f.wire(2)),"visible target acquired");target.discard();});
        h.runAtTickTime(8,()->{h.assertTrue(MulticoreCanopy.targets(f.p).isEmpty(),"removed native identity retires immediately");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_cleanup",timeoutTicks=10)
    public static void multicore_menu_and_preset_changes_destroy_deployment(GameTestHelper h){
        Fixture f=new Fixture(h);f.armOne();AbilityStorage.get(f.p).presets.edit(0,0,ParticleLaminate.ID,id->true);
        h.runAtTickTime(2,()->{h.assertFalse(MulticoreCanopy.active(f.p),"preset revision invalidates accepted cores");h.assertTrue(MulticoreCanopy.cores(f.p).isEmpty(),"cleanup sends no stale core state");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_persist",timeoutTicks=10)
    public static void multicore_corrupt_persistent_fence_and_remove_do_not_reset_it(GameTestHelper h){
        Fixture f=new Fixture(h);var data=new net.minecraft.nbt.CompoundTag();data.putInt("fireRemaining",20);data.putInt("inputRemaining",-1);f.p.getPersistentData().put("academy:multicore_canopy",data);
        MulticoreCanopy.remove(f.p);h.assertFalse(MulticoreCanopy.start(f.p,f.wire(1)),"negative persisted input fence refuses after cleanup");
        h.assertTrue(f.p.getPersistentData().getCompound("academy:multicore_canopy").getInt("fireRemaining")==20,"remove/recast cleanup preserves physical fire debt");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_warning_recall",timeoutTicks=20)
    public static void multicore_sneak_can_recall_during_unpaid_tap_warning(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp();f.armOne();f.target(f.eye.add(0,0,16),true);
        h.runAtTickTime(6,()->{h.assertTrue(MulticoreCanopy.start(f.p,f.wire(2)),"second edge arms warning");h.assertTrue(MulticoreCanopy.release(f.p,f.wire(2)),"short tap frozen");});
        h.runAtTickTime(12,()->{f.p.setShiftKeyDown(true);h.assertTrue(MulticoreCanopy.start(f.p,f.wire(3)),"sneak third edge recalls warning");h.assertFalse(MulticoreCanopy.active(f.p),"no delayed post-recall fire");near(h,f.debit(cp),.01,"recall has no fire debit or refund");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_clock",timeoutTicks=10)
    public static void multicore_world_clock_jumps_never_advance_physical_lifetime_or_debt(GameTestHelper h){
        Fixture f=new Fixture(h);f.armOne();var data=f.p.getPersistentData().getCompound("academy:multicore_canopy");data.putInt("fireRemaining",20);
        int remaining=MulticoreCanopy.remainingTicks(f.p);long before=h.getLevel().getGameTime();
        var clock=(net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData();
        try{clock.setGameTime(before+1_000_000);h.assertTrue(h.getLevel().getGameTime()==before+1_000_000,"native game clock actually advanced");h.assertTrue(MulticoreCanopy.remainingTicks(f.p)==remaining,"worldtime forward jump does not age immutable physical deployment");h.assertTrue(data.getInt("fireRemaining")==20,"worldtime cannot burn persistent fire fence");
            clock.setGameTime(0);h.assertTrue(h.getLevel().getGameTime()==0,"native game clock actually rolled back");h.assertTrue(MulticoreCanopy.remainingTicks(f.p)==remaining,"clock rollback cannot refresh or age field");}
        finally{clock.setGameTime(before);}
        h.runAtTickTime(4,()->{int left=f.p.getPersistentData().getCompound("academy:multicore_canopy").getInt("fireRemaining");h.assertTrue(left>=15&&left<=17,"only four or five physical events reduce debt");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_clone",timeoutTicks=10)
    public static void multicore_clone_and_logout_keep_fence_but_clear_native_context(GameTestHelper h){
        Fixture original=new Fixture(h);original.armOne();var data=original.p.getPersistentData().getCompound("academy:multicore_canopy");data.putInt("fireRemaining",20);
        Fixture replacement=new Fixture(h);MulticoreCanopy.clone(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(replacement.p,original.p,false));
        h.assertFalse(MulticoreCanopy.active(original.p),"clone retires original native identity");h.assertTrue(replacement.p.getPersistentData().getCompound("academy:multicore_canopy").getInt("fireRemaining")==20,"clone copies remaining physical fence");
        MulticoreCanopy.logout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(replacement.p));
        h.assertTrue(replacement.p.getPersistentData().getCompound("academy:multicore_canopy").getInt("fireRemaining")==20,"logout cannot erase debt");h.assertTrue(MulticoreCanopy.cores(replacement.p).isEmpty(),"no transferred core entities");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_range",timeoutTicks=10)
    public static void multicore_native_trace_has_exact_ninety_six_meter_limit(GameTestHelper h){
        Fixture f=new Fixture(h);Vec3 origin=new Vec3(1_421_000.5,120,1_431_000.5),end=origin.add(0,0,96);
        int cx=((int)Math.floor(origin.x))>>4,cz=((int)Math.floor(origin.z))>>4;
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+7;z++)f.holdChunk(x,z);
        NativeTrace accepted=trace(f.p,origin,end,true,.035);h.assertTrue(accepted.permitted&&!accepted.contact&&accepted.point.equals(end),"all384 loaded permitted prefixes end at96m");
        NativeTrace refused=trace(f.p,origin,origin.add(0,0,96.0001),true,.035);h.assertFalse(refused.permitted,"first out-of-bound native segment refused");h.assertTrue(refused.point.equals(origin),"no overrange query prefix observed");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_observed_core",timeoutTicks=45)
    public static void multicore_callback_observed_core_obstruction_cannot_revive_after_removal(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp();f.armOne();Vec3 center=MulticoreCanopy.cores(f.p).getFirst().center();
        List<Zombie> targets=new ArrayList<>();for(int i=0;i<3;i++)targets.add(f.target(f.eye.add((i-1)*1.6,0,20+i),true));
        boolean[] observed={false},removed={false},stillRetired={false};
        f.hook(new Object(){@SubscribeEvent public void skill(SkillAttackEvent e){
            if(e.player!=f.p||!e.skill.equals("meltdowner.multicore_canopy")||observed[0])return;
            observed[0]=true;f.block(BlockPos.containing(center));
            // Remove before the next ServerTick.Post. Only the native callback guard observed it.
            h.runAfterDelay(1,()->{f.clearBlocks();removed[0]=true;});
            h.runAfterDelay(2,()->{stillRetired[0]=!MulticoreCanopy.cores(f.p).isEmpty()&&!MulticoreCanopy.cores(f.p).getFirst().available();});
        }});
        h.runAtTickTime(6,()->h.assertTrue(MulticoreCanopy.start(f.p,f.wire(2)),"held three-slot plan starts"));
        h.runAtTickTime(35,()->{
            h.assertTrue(observed[0]&&removed[0]&&stillRetired[0],"guard-observed obstruction latches retirement despite next-tick removal");
            for(Zombie target:targets)h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth())==0,"later frozen slots never reuse retired core");
            var receipt=MulticoreCanopy.receipt(f.p);h.assertTrue(receipt!=null&&receipt.paid()&&receipt.slots()==3&&receipt.emittedSlots()==3&&receipt.successes()==0,"all three assigned shares discarded without replacement");
            near(h,f.debit(cp),.21,"one placement and one whole fire remain spent");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_observed_team",timeoutTicks=65)
    public static void multicore_callback_observed_alliance_cannot_revive_repeated_tap_target(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie a=f.target(f.eye.add(-1.5,0,20),true),b=f.target(f.eye.add(1.5,0,21),true);
        var scoreboard=h.getLevel().getScoreboard();var team=scoreboard.addPlayerTeam("mc"+UUID.randomUUID().toString().substring(0,10));f.teams.add(team);scoreboard.addPlayerToTeam(f.p.getScoreboardName(),team);
        boolean[] madeAllied={false},cleared={false};
        f.hook(new Object(){@SubscribeEvent public void skill(SkillAttackEvent e){
            if(e.player!=f.p||!e.skill.equals("meltdowner.multicore_canopy"))return;
            if(e.target==a&&!madeAllied[0]){madeAllied[0]=true;scoreboard.addPlayerToTeam(a.getScoreboardName(),team);}
            else if(e.target==b&&madeAllied[0]&&!cleared[0]){cleared[0]=true;scoreboard.removePlayerFromTeam(a.getScoreboardName(),team);}
        }});
        h.assertTrue(MulticoreCanopy.start(f.p,f.wire(1)),"hold first edge for six real cores");
        h.runAtTickTime(27,()->{h.assertTrue(MulticoreCanopy.cores(f.p).size()==6,"six deployed origins");h.assertTrue(MulticoreCanopy.release(f.p,f.wire(1)),"arm six cores");});
        h.runAtTickTime(33,()->{h.assertTrue(MulticoreCanopy.start(f.p,f.wire(2)),"two frozen identities shared round robin");h.assertTrue(MulticoreCanopy.release(f.p,f.wire(2)),"tap plan frozen");});
        h.runAtTickTime(53,()->{
            h.assertTrue(madeAllied[0]&&cleared[0]&&!f.p.isAlliedTo(a),"two native callbacks temporarily invalidated then restored alliance");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(a)).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth())==0,"observed invalid identity cannot receive later tap slots after alliance reversal");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(b)).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth())<0,"independent frozen identity still receives its own native share");
            var receipt=MulticoreCanopy.receipt(f.p);h.assertTrue(receipt!=null&&receipt.paid()&&receipt.slots()==6&&receipt.frozenTargets()==2,"six immutable shares keep original two identities");h.succeed();
        });
    }

    private record NativeTrace(Vec3 point,Entity target,int status,boolean permitted,boolean contact) {}
    /** Read-only native trace seam; never constructs an emission permit or mutates paid controller state. */
    private static NativeTrace trace(ServerPlayer p,Vec3 start,Vec3 end,boolean includeCaster,double radius){
        try{
            var method=MulticoreCanopy.class.getDeclaredMethod("nativeTrace",ServerPlayer.class,Vec3.class,Vec3.class,boolean.class,double.class,MulticoreCanopyRules.Work.class);method.setAccessible(true);Object hit=method.invoke(null,p,start,end,includeCaster,radius,new MulticoreCanopyRules.Work());
            var point=hit.getClass().getDeclaredMethod("point");var target=hit.getClass().getDeclaredMethod("target");var status=hit.getClass().getDeclaredMethod("status");var permitted=hit.getClass().getDeclaredMethod("permitted");var contact=hit.getClass().getDeclaredMethod("contact");
            point.setAccessible(true);target.setAccessible(true);status.setAccessible(true);permitted.setAccessible(true);contact.setAccessible(true);
            return new NativeTrace((Vec3)point.invoke(hit),(Entity)target.invoke(hit),(Integer)status.invoke(hit),(Boolean)permitted.invoke(hit),(Boolean)contact.invoke(hit));
        }catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}
    }
    private static Vector vector(Vec3 v){return new Vector(v.x,v.y,v.z);}private static Vec3 vec(Vector v){return new Vec3(v.x(),v.y(),v.z());}
    private static void face(LivingEntity entity,float yaw){entity.setYRot(yaw);entity.setXRot(0);entity.setYHeadRot(yaw);entity.setYBodyRot(yaw);}
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-7,why+": "+actual+" / "+expected);}
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final Vec3 origin,eye;
        final UUID chunkTicket=UUID.randomUUID();final Set<ChunkPos> heldChunks=new LinkedHashSet<>();
        final List<Object> hooks=new ArrayList<>();final List<net.minecraft.world.scores.PlayerTeam> teams=new ArrayList<>();
        final List<Entity> entities=new ArrayList<>();final Set<BlockPos> blocks=new LinkedHashSet<>();BigDecimal credits=BigDecimal.ZERO;boolean closed;
        Fixture(GameTestHelper h){
            this.h=h;var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"MC"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;origin=h.absoluteVec(new Vec3(12.5,18,5.5));p.moveTo(origin.x,origin.y,origin.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();eye=p.getEyePosition();
            h.assertTrue(HighPowerCombat.enroll(p,5),"native authenticated level5 caster");var state=AbilityStorage.get(p);state.selectCategory("meltdowner");state.setLevel(5);state.learn(DualPoint.ID);state.learn(FoldedMirrorArray.ID);state.learn(DisintegrationSweep.ID);state.learn(ParticleLaminate.ID);state.learn(MulticoreCanopy.ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,MulticoreCanopy.ID,id->true),"bind learned NEW251");HighPowerResources.initializeBudget(p);epoch();
            h.onEachTick(()->{if(!closed){BigDecimal before=new BigDecimal(cp());p.doTick();credits=credits.add(new BigDecimal(cp()).subtract(before));}});
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        void armOne(){h.assertTrue(MulticoreCanopy.start(p,wire(1)),"real first press deploys");h.assertTrue(MulticoreCanopy.cores(p).size()==1,"one accepted core");h.assertTrue(MulticoreCanopy.release(p,wire(1)),"real first release arms");}
        Zombie target(Vec3 center,boolean baby){Zombie target=EntityType.HUSK.create(h.getLevel());target.setBaby(baby);target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setPos(center.add(0,-target.getBbHeight()/2,0));h.getLevel().addFreshEntity(target);h.assertTrue(HighPowerCombat.enroll(target,5),"reviewed native hostile target");target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health",cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().toPlainString());entities.add(target);return target;}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        double cp(){return HighPowerResources.cp(p);}double xp(){return AbilityStorage.get(p).experience.getOrDefault(MulticoreCanopy.ID,0.0);}
        double debit(double initial){return new BigDecimal(initial).add(credits).subtract(new BigDecimal(cp())).doubleValue();}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void holdChunk(int x,int z){ChunkPos chunk=new ChunkPos(x,z);if(heldChunks.add(chunk)){h.getLevel().getChunkSource().addRegionTicket(FAR_FIXTURE_TICKET,chunk,2,chunkTicket);h.getLevel().getChunk(x,z);}}
        void block(BlockPos pos){h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());blocks.add(pos);}
        void clearBlocks(){blocks.forEach(pos->h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState()));blocks.clear();}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);clearBlocks();MulticoreCanopy.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);heldChunks.forEach(chunk->h.getLevel().getChunkSource().removeRegionTicket(FAR_FIXTURE_TICKET,chunk,2,chunkTicket));heldChunks.clear();channel.finishAndReleaseAll();}
    }
}
