package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.api.SkillBlockDestroyEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.DisintegrationSweepRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real connected native owners, physical server clocks, and native input/terrain/damage callbacks. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDisintegrationSweepRuntimeTests {
    private static final String TEMPLATE="highpower_empty",BATCH="highpower_disintegration_sweep";
    private HighPowerDisintegrationSweepRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void sweep_early_release_and_replayed_edges_cannot_manufacture_charge(GameTestHelper h){
        Fixture f=new Fixture(h,5);double cp=f.cp();String wire=f.wire(1);
        h.assertFalse(DisintegrationSweep.start(f.p,new HighPowerInput.Input(0,1,f.p.getId()+1,f.epoch()).wire()),"foreign actor cannot own a charge");
        h.assertTrue(DisintegrationSweep.start(f.p,wire),"real authenticated physical press");
        for(int i=0;i<1000;i++)DisintegrationSweep.tick(f.p);
        h.assertTrue(DisintegrationSweep.release(f.p,wire),"zero-tick release retires harmlessly");
        h.assertFalse(DisintegrationSweep.active(f.p),"duplicate controller calls never synthesize eight real ticks");
        h.assertFalse(DisintegrationSweep.start(f.p,wire),"press replay cannot resurrect charge");
        h.assertFalse(DisintegrationSweep.start(f.p,f.wire(2)),"fresh nonce cannot erase physical same-tick fence");
        near(h,f.cp(),cp,"all invalid/early edges remain free");
        h.runAfterDelay(1,()->{h.assertTrue(f.start(3,false),"new real tick accepts fresh press");});
        h.runAfterDelay(8,()->{
            h.assertTrue(f.release(3),"seven real ticks releases for free");h.assertFalse(DisintegrationSweep.active(f.p),"seven is below required eight");
            near(h,f.cp(),cp,"undercharged cast pays no CP");near(h,AbilityStorage.get(f.p).exp(DisintegrationSweep.ID),0,"idle charge grants no mastery");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void sweep_hold_limit_and_preview_fence_survive_abort_and_owner_epoch(GameTestHelper h){
        Fixture f=new Fixture(h,5);double cp=f.cp();h.assertTrue(f.start(1,false),"charge starts");long initial=previewStamp(f.p);
        for(int i=0;i<100;i++)DisintegrationSweep.tick(f.p);
        h.assertTrue(previewStamp(f.p)==initial,"same-tick input spam does not produce preview work");
        h.runAfterDelay(1,()->{
            DisintegrationSweep.abort(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();
            h.assertTrue(f.start(2,false),"reconnected epoch can own a new physical press");
            h.assertTrue(previewStamp(f.p)==initial,"new context cannot reset persistent four-tick preview fence");
        });
        h.runAfterDelay(4,()->{DisintegrationSweep.tick(f.p);h.assertTrue(previewStamp(f.p)-initial>=4,"preview advances only on a real four-tick boundary");});
        h.runAfterDelay(102,()->{
            DisintegrationSweep.tick(f.p);h.assertFalse(DisintegrationSweep.active(f.p),"hundred-tick hold retires without a release");
            h.assertFalse(f.release(2),"expired charge never fires later");near(h,f.cp(),cp,"holding and expiry remain free");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void sweep_exact_eight_initial_identities_partition_once_and_ninth_stays_out(GameTestHelper h){
        Fixture f=new Fixture(h,5);List<Zombie> targets=new ArrayList<>();Map<Entity,Integer> calls=new IdentityHashMap<>();
        for(int i=0;i<9;i++)targets.add(f.target(-22+i*5.5,18));
        List<BigDecimal> before=targets.stream().map(HighPowerDisintegrationSweepRuntimeTests::hp).toList();
        f.hook(new Object(){@SubscribeEvent public void attack(SkillAttackEvent e){if(e.player==f.p&&e.skill.equals("meltdowner.disintegration_sweep"))calls.merge(e.target,1,Integer::sum);}});
        BigDecimal total=DisintegrationSweepRules.damage(HighPowerGrowth.values(f.p).damage());double cp=f.cp();f.start(1,false);
        h.runAfterDelay(8,()->{h.assertTrue(f.release(1),"charged release freezes nine candidate scan but only eight identities");near(h,f.cp(),cp,"release itself makes no attack payment");});
        h.runAfterDelay(20,()->{
            BigDecimal observed=BigDecimal.ZERO;
            for(int i=0;i<8;i++){BigDecimal loss=before.get(i).subtract(hp(targets.get(i)));exact(h,loss,total.divide(BigDecimal.valueOf(8)),"frozen identity keeps exact eighth allocation "+i);observed=observed.add(loss);h.assertValueEqual(calls.getOrDefault(targets.get(i),0),1,"one native attempt per initial identity");}
            exact(h,observed,total,"whole sweep exactly partitions min(2D,1e20)");exact(h,hp(targets.get(8)),before.get(8),"ninth identity receives no reassigned share");
            near(h,cp-f.cp(),DisintegrationSweepRules.attackCost(HighPowerGrowth.values(f.p).position()),"one committed attack CP debit");
            near(h,AbilityStorage.get(f.p).exp(DisintegrationSweep.ID),.005,"one productive sweep awards mastery once");h.assertFalse(DisintegrationSweep.active(f.p),"finite ten-tick sweep retires");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void sweep_native_level_three_keeps_lower_effective_growth_through_category_transition(GameTestHelper h){
        Fixture f=new Fixture(h,5);var state=AbilityStorage.get(f.p);BigDecimal earned=HighPowerCombat.earnedGrowthPosition(f.p);
        String ownerHp=HighPowerCombat.healthText(f.p),ownerShield=HighPowerCombat.shieldText(f.p);double cp=f.cp();
        // Declared native rollback fixture lowers current output while preserving already earned
        // capacity. Subsequent ordinary native category/level transitions cannot recertify q.
        state.setLevel(1);exact(h,HighPowerGrowth.values(f.p).position(),BigDecimal.ONE,"observed native rollback leaves q1 below earned Q5");
        state.changeCategoryClassic("electromaster");state.setLevel(3);state.changeCategoryClassic("meltdowner");
        for(String id:List.of(DualPoint.ID,FoldedMirrorArray.ID,DisintegrationSweep.ID))state.learn(id);
        state.activated=true;state.overloadFine=true;state.presets.edit(0,0,DisintegrationSweep.ID,id->true);
        BigDecimal q=HighPowerGrowth.values(f.p).position(),amount=DisintegrationSweepRules.damage(HighPowerGrowth.values(f.p).damage());
        exact(h,q,BigDecimal.ONE,"native LV3 unlock retains actual lower q across both category changes");exact(h,HighPowerCombat.earnedGrowthPosition(f.p),earned,"category changes preserve earned capacity");
        near(h,f.cp(),cp,"level/category setup does not refill CP");Zombie target=f.target(0,12);BigDecimal before=hp(target);boolean[] emitted={false};
        f.hook(new Object(){@SubscribeEvent public void attack(SkillAttackEvent event){if(event.player==f.p&&event.target==target&&event.skill.equals("meltdowner.disintegration_sweep")){
            emitted[0]=true;exact(h,HighPowerGrowth.values(f.p).position(),q,"native emission uses the retained release q");exact(h,HighPowerCombat.earnedGrowthPosition(f.p),earned,"emission cannot mint earned Q");}}});
        h.assertTrue(f.start(1,false),"native LV3 and earned capacity admit charge even with q below three");
        h.runAfterDelay(8,()->h.assertTrue(f.release(1),"genuine charged release remains available at retained q"));
        h.runAfterDelay(20,()->{
            h.assertTrue(emitted[0],"lower-q ability reaches actual native emission");exact(h,before.subtract(hp(target)),amount,"whole damage is actual lower-q 2D, without a level-three output jump");
            near(h,cp-f.cp(),DisintegrationSweepRules.attackCost(q),"one cost uses frozen lower q");exact(h,HighPowerCombat.earnedGrowthPosition(f.p),earned,"cast leaves previous earned capacity unchanged");
            h.assertTrue(HighPowerGrowth.values(f.p).position().compareTo(BigDecimal.valueOf(3))<0,"only genuine small productive mastery may advance q");
            h.assertTrue(ownerHp.equals(HighPowerCombat.healthText(f.p))&&ownerShield.equals(HighPowerCombat.shieldText(f.p)),"no transition or cast refills exact owner resources");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void sweep_frozen_shares_never_reassign_to_late_entities_or_through_new_allies(GameTestHelper h){
        Fixture f=new Fixture(h,5);Zombie first=f.target(-15,16),second=f.target(15,16);BigDecimal hp1=hp(first),hp2=hp(second);BigDecimal total=DisintegrationSweepRules.damage(HighPowerGrowth.values(f.p).damage());
        Zombie[] late={null};BigDecimal[] lateHp={null};f.start(1,false);
        h.runAfterDelay(8,()->{
            f.release(1);Zombie ally=f.target(-15,8);f.ally(ally);late[0]=f.target(0,12);lateHp[0]=hp(late[0]);
        });
        h.runAfterDelay(20,()->{
            exact(h,hp(first),hp1,"new allied body blocks frozen target without piercing");exact(h,hp2.subtract(hp(second)),total.divide(BigDecimal.TWO),"surviving target keeps only original half");
            exact(h,hp(late[0]),lateHp[0],"new exposed enemy is never acquired after release");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void sweep_overfull_native_scan_fails_closed_before_payment(GameTestHelper h){
        Fixture f=new Fixture(h,5);List<Zombie> bodies=new ArrayList<>();int[] emissions={0};
        // Sixty-five coincident bodies would legitimately take vanilla six-point cramming
        // damage. Separate every body while keeping the entire resident set inside the fan.
        for(int i=0;i<65;i++){double x=(i%9-4)*1.25,z=12+(i/9)*1.25;bodies.add(f.target(Math.toDegrees(Math.atan2(-x,z)),Math.hypot(x,z)));}
        f.hook(new Object(){@SubscribeEvent public void attack(SkillAttackEvent e){if(e.player==f.p&&e.skill.equals("meltdowner.disintegration_sweep"))emissions[0]++;}});
        List<BigDecimal> before=bodies.stream().map(HighPowerDisintegrationSweepRuntimeTests::hp).toList();double cp=f.cp();f.start(1,false);
        h.runAfterDelay(8,()->f.release(1));h.runAfterDelay(20,()->{
            for(int i=0;i<bodies.size();i++)exact(h,hp(bodies.get(i)),before.get(i),"unbounded resident scan yields no victim");
            h.assertValueEqual(emissions[0],0,"overfull resident scan reaches no native skill emission");near(h,f.cp(),cp,"sixty-five-body scan does not buy attack CP");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void sweep_caster_motion_beyond_release_leash_cancels_remaining_without_payment(GameTestHelper h){
        Fixture f=new Fixture(h,5);Zombie target=f.target(15,16);BigDecimal before=hp(target);double cp=f.cp();f.start(1,false);
        h.runAfterDelay(8,()->{f.release(1);f.p.setPos(f.p.position().add(.25001,0,0));DisintegrationSweep.tick(f.p);});
        h.runAfterDelay(20,()->{h.assertFalse(DisintegrationSweep.active(f.p),"real owner movement past quarter metre retires sweep");exact(h,hp(target),before,"frozen-origin reach cannot be walked");near(h,f.cp(),cp,"cancel before opportunity remains free");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void sweep_release_plane_is_frozen_and_later_aim_cannot_retarget(GameTestHelper h){
        Fixture f=new Fixture(h,5);Zombie original=f.target(8,14),outside=f.target(34,14);BigDecimal hp1=hp(original),hp2=hp(outside);f.start(1,false);
        h.runAfterDelay(8,()->{f.release(1);Vec3 hand=DisintegrationSweep.hand(f.p);f.p.setYRot(5);f.p.setPos(f.p.position().add(hand.subtract(DisintegrationSweep.hand(f.p))));});
        h.runAfterDelay(20,()->{h.assertTrue(hp(original).compareTo(hp1)<0,"changed live aim with unchanged real focusing point still hits original frozen fan");exact(h,hp(outside),hp2,"new aim cannot acquire outside release fan");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void sweep_callback_cannot_replace_paid_target_or_recursive_context(GameTestHelper h){
        Fixture f=new Fixture(h,5);Zombie target=f.target(0,12);BigDecimal before=hp(target);double cp=f.cp();boolean[] called={false};
        f.hook(new Object(){@SubscribeEvent public void attack(SkillAttackEvent event){if(event.player==f.p&&event.target==target&&event.skill.equals("meltdowner.disintegration_sweep")){
            called[0]=true;target.setPos(target.position().add(1,0,0));h.assertFalse(f.start(2,false),"recursive physical press cannot mutate resolving context");}}});
        f.start(1,false);h.runAfterDelay(8,()->f.release(1));h.runAfterDelay(20,()->{
            h.assertTrue(called[0],"native skill callback actually occurred");exact(h,hp(target),before,"mutable body invalidates exact captured ray");h.assertTrue(f.cp()<cp,"already validated opportunity remains paid");near(h,AbilityStorage.get(f.p).exp(DisintegrationSweep.ID),0,"invalidated emission has no productive mastery");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void sweep_native_crouch_at_press_freezes_mining_and_zero_entity_damage(GameTestHelper h){
        Fixture f=new Fixture(h,5);Zombie target=f.target(0,14);BigDecimal before=hp(target);BlockPos dirt=f.cell(0,9,0);f.block(dirt,Blocks.DIRT);
        f.start(1,true);f.p.setShiftKeyDown(false);
        h.runAfterDelay(8,()->{h.assertTrue(f.release(1),"release uncrouch cannot convert mining to attack");h.assertTrue(DisintegrationSweep.marks(f.p).stream().anyMatch(mark->mark.pos().equals(dirt)),"vertical first visible soil cell frozen");});
        h.runAfterDelay(20,()->{h.assertTrue(h.getLevel().getBlockState(dirt).isAir(),"native mining removes admitted dirt");exact(h,hp(target),before,"mining never emits entity damage even through its new hole");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void sweep_mining_has_sixteen_frozen_cells_two_attempts_per_tick_and_no_tunnel_reacquisition(GameTestHelper h){
        Fixture f=new Fixture(h,5);List<BlockPos> fronts=new ArrayList<>(),backs=new ArrayList<>();Map<Long,Integer> attempts=new HashMap<>();
        for(int y=-10;y<=10;y++){BlockPos front=f.cell(y,24,0);fronts.add(front);backs.add(front.south());f.block(front,Blocks.DIRT);f.block(front.south(),Blocks.DIRT);}
        f.hook(new Object(){@SubscribeEvent public void terrain(SkillBlockDestroyEvent event){if(event.player==f.p&&event.skill.equals("meltdowner.disintegration_sweep"))attempts.merge(f.now(),1,Integer::sum);}});
        f.start(1,true);Set<BlockPos> frozen=new HashSet<>();
        h.runAfterDelay(8,()->{f.release(1);DisintegrationSweep.marks(f.p).forEach(mark->frozen.add(mark.pos()));h.assertValueEqual(frozen.size(),16,"only sixteen initially visible supported cells freeze");
            long[] counts=acquisitionCounts(f.p);h.assertTrue(counts[0]<=402L*193&&counts[1]<=201L*192,"release acquisition has a finite measured native-prefix budget");
            System.out.println("NEW231 measured mining release prefixes: outline="+counts[0]+", native="+counts[1]);});
        h.runAfterDelay(20,()->{
            h.assertTrue(!attempts.isEmpty()&&attempts.values().stream().allMatch(n->n<=2),"real native source attempts never exceed two per server tick");
            for(BlockPos back:backs)h.assertTrue(h.getLevel().getBlockState(back).is(Blocks.DIRT),"newly exposed rear cells stay outside queue");
            for(BlockPos front:fronts)if(!frozen.contains(front))h.assertTrue(h.getLevel().getBlockState(front).is(Blocks.DIRT),"unselected front cells cannot be reacquired");
            h.assertTrue(fronts.stream().filter(pos->h.getLevel().getBlockState(pos).isAir()).count()<=16,"no more than fixed sixteen removals");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void sweep_mining_stops_at_first_changed_frozen_cell(GameTestHelper h){
        Fixture f=new Fixture(h,5);for(int y=-3;y<=3;y++)f.block(f.cell(y,10,0),Blocks.DIRT);double cp=f.cp();Set<BlockPos> remaining=new HashSet<>();f.start(1,true);
        h.runAfterDelay(8,()->{
            f.release(1);var marks=DisintegrationSweep.marks(f.p);h.assertTrue(marks.size()>=3,"multiple initial visible cells freeze");marks.forEach(mark->remaining.add(mark.pos()));f.block(marks.getFirst().pos(),Blocks.STONE);
        });
        h.runAfterDelay(20,()->{for(BlockPos pos:remaining)h.assertFalse(h.getLevel().getBlockState(pos).isAir(),"changed first cell prevents every later removal");near(h,f.cp(),cp,"changed target settles no mining CP");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void sweep_menu_and_preset_revision_cancel_without_restore(GameTestHelper h){
        Fixture f=new Fixture(h,5);Zombie target=f.target(15,12);BigDecimal before=hp(target);double cp=f.cp();f.start(1,false);
        h.runAfterDelay(8,()->{f.release(1);var state=AbilityStorage.get(f.p);state.presets.edit(0,1,DualPoint.ID,id->true);DisintegrationSweep.tick(f.p);
            h.assertFalse(DisintegrationSweep.active(f.p),"any preset revision retires captured context");AbilityStorage.save(f.p);DisintegrationSweep.remove(f.p);});
        h.runAfterDelay(20,()->{h.assertFalse(DisintegrationSweep.active(f.p),"saved state never restores a controller");exact(h,hp(target),before,"preset cancellation prevents later native rays");near(h,f.cp(),cp,"pre-opportunity revision remains free");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void sweep_and_beam_etching_share_real_native_attempt_allowance(GameTestHelper h){
        Fixture f=new Fixture(h,5);var ability=AbilityStorage.get(f.p);ability.learn(BeamEtching.ID);ability.presets.edit(0,1,BeamEtching.ID,id->true);
        BlockPos near1=f.cell(0,6,4),near2=f.cell(1,7,4);f.block(near1,Blocks.DIRT);f.block(near2,Blocks.DIRT);
        for(int y=-10;y<=-6;y++)f.block(f.cell(y,26,0),Blocks.DIRT);
        Map<Long,Integer> attempts=new HashMap<>();int[] etching={0},sweep={0};
        f.hook(new Object(){@SubscribeEvent public void terrain(SkillBlockDestroyEvent e){if(e.player==f.p){attempts.merge(f.now(),1,Integer::sum);if(e.skill.equals("meltdowner.beam_etching"))etching[0]++;if(e.skill.equals("meltdowner.disintegration_sweep"))sweep[0]++;}}});
        f.face(Vec3.atCenterOf(near1));String etch=f.wire(1,1);h.assertTrue(BeamEtching.start(f.p,etch),"real NEW213 preparation owns slot one");
        f.start(2,true);
        h.runAfterDelay(2,()->{f.face(Vec3.atCenterOf(near2));BeamEtching.tick(f.p);});
        h.runAfterDelay(7,()->{f.p.setXRot(0);f.p.setYRot(0);});
        h.runAfterDelay(8,()->{f.release(2);h.assertTrue(DisintegrationSweep.marks(f.p).size()>=2,"NEW231 has real initial cells waiting for native quota");});
        h.runAfterDelay(9,()->{h.assertTrue(BeamEtching.release(f.p,etch),"real NEW213 release uses same server tick budget");DisintegrationSweep.tick(f.p);});
        h.runAfterDelay(20,()->{h.assertTrue(etching[0]>0&&sweep[0]>0,"both native skill controllers actually attempted work");h.assertTrue(attempts.values().stream().allMatch(n->n<=2),"combined NEW213 and NEW231 callbacks never exceed two attempts per real tick");h.succeed();});
    }

    private static long[] acquisitionCounts(ServerPlayer p){
        try{var field=DisintegrationSweep.class.getDeclaredField("CONTEXTS");field.setAccessible(true);Object context=((Map<?,?>)field.get(null)).get(p.getUUID());
            var surface=context.getClass().getDeclaredField("acquisitionSurfacePrefixes");var nativeShapes=context.getClass().getDeclaredField("acquisitionNativePrefixes");surface.setAccessible(true);nativeShapes.setAccessible(true);return new long[]{surface.getLong(context),nativeShapes.getLong(context)};
        }catch(ReflectiveOperationException e){throw new AssertionError("Native acquisition diagnostic unavailable",e);}
    }
    private static long previewStamp(ServerPlayer p){return p.getPersistentData().getCompound("academy:disintegration_sweep").getLong("previewAt");}
    private static BigDecimal hp(LivingEntity target){return HighPowerCombat.enabled(target)?new BigDecimal(HighPowerCombat.healthText(target)):BigDecimal.valueOf(target.getHealth());}
    private static void exact(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+": "+actual+" / "+expected);}
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-7,why+": "+actual+" / "+expected);}
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();
        final List<BlockPos> blocks=new ArrayList<>();final List<net.minecraft.world.scores.PlayerTeam> teams=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h,int level){
            this.h=h;var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"DS"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;Vec3 at=h.absoluteVec(new Vec3(15.5,24,4.5));p.moveTo(at.x,at.y,at.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();
            h.assertTrue(HighPowerCombat.enroll(p,level),"connected exact LV caster");var state=AbilityStorage.get(p);state.selectCategory("meltdowner");state.setLevel(level);state.learn(DualPoint.ID);state.learn(FoldedMirrorArray.ID);state.learn(DisintegrationSweep.ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,DisintegrationSweep.ID,id->true),"NEW231 bound after learned predecessor");HighPowerResources.initializeBudget(p);epoch();
            // A native perimeter contains this fixture's 48m query without sharing victims
            // with an adjacent simultaneously running 32m GameTest structure.
            for(int y=21;y<=29;y++)for(int axis=0;axis<=31;axis++){
                block(h.absolutePos(new BlockPos(axis,y,31)),Blocks.STONE);
                block(h.absolutePos(new BlockPos(0,y,axis)),Blocks.STONE);
                block(h.absolutePos(new BlockPos(31,y,axis)),Blocks.STONE);
            }
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Zombie target(double angle,double distance){Vec3 origin=DisintegrationSweep.hand(p);double radians=Math.toRadians(angle);Zombie target=EntityType.HUSK.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            target.setPos(origin.x-Math.sin(radians)*distance,p.getY(),origin.z+Math.cos(radians)*distance);h.getLevel().addFreshEntity(target);h.assertTrue(HighPowerCombat.enroll(target,5),"durable native supported body");entities.add(target);return target;}
        void ally(Entity entity){var board=h.getLevel().getScoreboard();var team=board.addPlayerTeam("dsa"+p.getId()+"_"+teams.size());teams.add(team);board.addPlayerToTeam(p.getScoreboardName(),team);board.addPlayerToTeam(entity.getScoreboardName(),team);}
        BlockPos cell(int y,int z,int x){return BlockPos.containing(DisintegrationSweep.hand(p)).offset(x,y,z);}
        void block(BlockPos pos,net.minecraft.world.level.block.Block block){h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());if(!blocks.contains(pos))blocks.add(pos);}
        void face(Vec3 point){Vec3 delta=point.subtract(p.getEyePosition());p.setYRot((float)-Math.toDegrees(Math.atan2(delta.x,delta.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));}
        boolean start(long input,boolean mining){p.setShiftKeyDown(mining);return DisintegrationSweep.start(p,wire(input));}
        boolean release(long input){return DisintegrationSweep.release(p,wire(input));}
        long now(){return p.server.overworld().getGameTime();}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return wire(0,input);}String wire(int slot,long input){return new HighPowerInput.Input(slot,input,p.getId(),epoch()).wire();}
        double cp(){return HighPowerResources.cp(p);}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);blocks.forEach(pos->h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState()));DisintegrationSweep.remove(p);BeamEtching.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}
